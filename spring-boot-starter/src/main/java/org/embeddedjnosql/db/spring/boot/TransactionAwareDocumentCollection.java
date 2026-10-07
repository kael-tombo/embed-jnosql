package org.embeddedjnosql.db.spring.boot;

import org.embeddedjnosql.db.core.event.EventBus;
import org.embeddedjnosql.db.core.metrics.DatabaseMetrics;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.document.Query;
import org.embeddedjnosql.db.storage.spi.StorageEngine;
import org.embeddedjnosql.db.transaction.mvcc.Transaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A document-collection view whose mutations are staged in the Spring-bound MVCC
 * {@link Transaction} instead of hitting the engine immediately — the H2-in-Spring-Boot
 * experience for a document store: everything a {@code @Transactional} service does through
 * this collection commits atomically with the transaction, and rollback discards it.
 *
 * <p>Semantics, deliberately pinned here:</p>
 * <ul>
 *   <li><b>Read-your-own-writes:</b> id-based reads ({@code findById}, {@code exists})
 *       delegate to {@link Transaction#read}, which consults the staged buffer before the MVCC
 *       snapshot. Query reads ({@code find/findAll/count}) run against committed state and are
 *       then merged with the staged overlay ({@link Transaction#bufferedKeys},
 *       {@link Transaction#bufferedPut}, {@link Transaction#isBufferedDeleted}), so queries
 *       also see uncommitted local writes.</li>
 *   <li><b>Isolation:</b> committed state is read from the transaction's MVCC snapshot; other
 *       transactions' uncommitted work is invisible — snapshot isolation.</li>
 *   <li><b>Update = full-document stage:</b> {@code update}/{@code upsert} stage the complete
 *       document (a document store has no partial writes); {@code insertAll} stages each
 *       document, so batch atomicity is inherited from the transaction itself.</li>
 *   <li><b>Not supported inside a transaction:</b> TTL inserts (a staged write cannot carry an
 *       expiry, so it would silently lose the TTL — this throws instead of lying) and
 *       {@code cleanupExpired} (an immediate maintenance action that cannot be staged).</li>
 *   <li><b>Immediate despite the transaction:</b> index DDL ({@code createIndex},
 *       {@code dropIndex}) and {@code cleanupExpired} act on the engine directly, exactly like
 *       H2's non-transactional DDL in default mode — they survive rollback.</li>
 * </ul>
 *
 * <p>Instances are created per {@code documentCollection()} call while a transaction is
 * active; all durable state lives in the transaction, so instance identity is irrelevant.</p>
 */
public class TransactionAwareDocumentCollection extends DocumentCollection {

    private final Transaction tx;

    TransactionAwareDocumentCollection(String name, StorageEngine engine, EventBus eventBus,
                                       DatabaseMetrics metrics, Transaction tx) {
        super(name, engine, eventBus, metrics);
        this.tx = tx;
    }

    Transaction transaction() {
        return tx;
    }

    // ------------------------------------------------------------------
    // Staged mutations
    // ------------------------------------------------------------------

    @Override
    public Document insert(Document doc) {
        if (doc.id() == null) {
            doc.id(java.util.UUID.randomUUID().toString());
        }
        tx.write(name(), doc.id(), doc.toJson());
        return doc;
    }

    @Override
    public Document insert(Document doc, long ttlSeconds) {
        if (ttlSeconds > 0) {
            throw new UnsupportedOperationException(
                    "TTL inserts are not supported inside a Spring-managed transaction "
                            + "(a staged write cannot carry an expiry; run them outside @Transactional)");
        }
        return insert(doc);
    }

    @Override
    public List<Document> insertAll(List<Document> docs) {
        List<Document> inserted = new ArrayList<>(docs.size());
        for (Document doc : docs) {
            inserted.add(insert(doc));
        }
        return inserted;
    }

    @Override
    public List<Document> insertAll(List<Document> docs, boolean atomic) {
        // The staging buffer IS the atomicity boundary: everything in this list either
        // commits with the transaction or disappears with it.
        return insertAll(docs);
    }

    @Override
    public Document update(Document doc) {
        if (doc.id() == null) {
            throw new IllegalArgumentException("update requires a document id");
        }
        tx.write(name(), doc.id(), doc.toJson());
        return doc;
    }

    @Override
    public Document upsert(Document doc) {
        return insert(doc);
    }

    @Override
    public boolean deleteById(String id) {
        tx.delete(name(), id);
        return true;
    }

    @Override
    public long deleteAll(Query query) {
        List<Document> matches = find(query);
        for (Document doc : matches) {
            tx.delete(name(), doc.id());
        }
        return matches.size();
    }

    @Override
    public long bulkDelete(List<String> ids) {
        long deleted = 0;
        for (String id : ids) {
            if (findById(id) != null) {
                tx.delete(name(), id);
                deleted++;
            }
        }
        return deleted;
    }

    // ------------------------------------------------------------------
    // Reads: staged buffer merged over the MVCC snapshot
    // ------------------------------------------------------------------

    @Override
    public Document findById(String id) {
        String json = tx.read(name(), id);
        return json == null ? null : Document.fromJson(json);
    }

    @Override
    public boolean exists(String id) {
        return tx.read(name(), id) != null;
    }

    @Override
    public List<Document> findByIds(List<String> ids) {
        List<Document> found = new ArrayList<>(ids.size());
        for (String id : ids) {
            Document doc = findById(id);
            if (doc != null) {
                found.add(doc);
            }
        }
        return found;
    }

    @Override
    public List<Document> findAll() {
        return mergeStaged(super.findAll(), null);
    }

    @Override
    public List<Document> find(Query query) {
        return mergeStaged(super.find(query), query);
    }

    @Override
    public Document findOne(Query query) {
        List<Document> matches = find(query);
        return matches.isEmpty() ? null : matches.get(0);
    }

    @Override
    public long count() {
        return findAll().size();
    }

    @Override
    public long count(Query query) {
        return find(query).size();
    }

    /**
     * Applies the staged overlay on top of committed query results: staged deletes remove rows,
     * staged writes re-enter the result set when they match the query predicate (match-all for
     * {@code findAll}).
     */
    private List<Document> mergeStaged(List<Document> committed, Query predicate) {
        Map<String, Document> merged = new LinkedHashMap<>();
        for (Document doc : committed) {
            if (doc.id() != null) {
                merged.put(doc.id(), doc);
            }
        }
        for (String key : tx.bufferedKeys(name())) {
            if (tx.isBufferedDeleted(name(), key)) {
                merged.remove(key);
                continue;
            }
            String json = tx.bufferedPut(name(), key).orElse(null);
            if (json == null) {
                continue;
            }
            Document staged = Document.fromJson(json);
            if (predicate == null || predicate.docPredicate().test(staged)) {
                merged.put(key, staged);
            }
        }
        return new ArrayList<>(merged.values());
    }
}

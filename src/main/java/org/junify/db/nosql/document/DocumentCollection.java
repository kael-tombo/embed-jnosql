package org.junify.db.nosql.document;

import org.junify.db.core.cache.QueryResultCache;
import org.junify.db.core.event.EventBus;
import org.junify.db.index.SecondaryIndex;
import org.junify.db.core.metrics.DatabaseMetrics;
import org.junify.db.storage.spi.StorageEngine;
import org.junify.db.core.util.JsonSerde;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DocumentCollection {

    private final String name;
    private final StorageEngine engine;
    private final EventBus eventBus;
    private final DatabaseMetrics metrics;
    private final Map<String, SecondaryIndex> indexes;
    private final QueryResultCache cache;
    private final Path dataDir;

    public DocumentCollection(String name, StorageEngine engine, EventBus eventBus, DatabaseMetrics metrics) {
        this(name, engine, eventBus, metrics, null, null);
    }

    public DocumentCollection(String name, StorageEngine engine, EventBus eventBus, DatabaseMetrics metrics, QueryResultCache cache) {
        this(name, engine, eventBus, metrics, cache, null);
    }

    public DocumentCollection(String name, StorageEngine engine, EventBus eventBus, DatabaseMetrics metrics, QueryResultCache cache, Path dataDir) {
        this.name = name;
        this.engine = engine;
        this.eventBus = eventBus;
        this.metrics = metrics;
        this.indexes = new ConcurrentHashMap<>();
        this.cache = cache;
        this.dataDir = dataDir;
    }

    public String name() {
        return name;
    }

    public QueryResultCache cache() {
        return cache;
    }

    public SecondaryIndex createIndex(String field) {
        var idx = new SecondaryIndex(name, field);
        indexes.put(field, idx);
        for (var doc : findAll()) {
            idx.add(doc);
        }
        saveIndexes();
        return idx;
    }

    public void loadIndexes() {
        if (dataDir == null) return;
        
        var indexFile = dataDir.resolve(".indexes");
        if (!Files.exists(indexFile)) return;
        
        try {
            var content = Files.readString(indexFile);
            var map = JsonSerde.fromJson(content, Map.class);
            var collectionIndexes = (Map<?, ?>) map.get(name);
            if (collectionIndexes == null) return;
            
            for (var entry : collectionIndexes.entrySet()) {
                var field = (String) entry.getKey();
                var idxJson = (String) entry.getValue();
                var idx = SecondaryIndex.fromJson(idxJson);
                indexes.put(field, idx);
            }
        } catch (IOException e) {
            System.err.println("Failed to load indexes: " + e.getMessage());
        }
    }

    public void saveIndexes() {
        if (dataDir == null) return;

        var indexFile = dataDir.resolve(".indexes");

        // Load the existing index file to preserve indexes from other collections
        Map<String, Object> allIndexes = new HashMap<>();
        if (Files.exists(indexFile)) {
            try {
                var content = Files.readString(indexFile);
                var existing = JsonSerde.fromJson(content, Map.class);
                if (existing != null) {
                    allIndexes.putAll(existing);
                }
            } catch (IOException e) {
                System.err.println("Warning: Could not read existing indexes file; will overwrite: " + e.getMessage());
            }
        }

        // Overwrite only this collection's entries
        Map<String, String> collectionIndexes = new HashMap<>();
        for (var entry : indexes.entrySet()) {
            collectionIndexes.put(entry.getKey(), entry.getValue().toJson());
        }
        allIndexes.put(name, collectionIndexes);

        try {
            var json = JsonSerde.toJson(allIndexes);
            Files.writeString(indexFile, json);
        } catch (IOException e) {
            System.err.println("Failed to save indexes: " + e.getMessage());
        }
    }

    public SecondaryIndex getIndex(String field) {
        return indexes.get(field);
    }

    public Map<String, SecondaryIndex> getIndexes() {
        return Map.copyOf(indexes);
    }

    /**
     * Removes a secondary index, leaving the documents untouched.
     *
     * <p>Dropping an index and deleting data are separate operations on purpose: the
     * console's index endpoint used to call {@link #clear()} here, so a request that
     * reported "indexes cleared" erased every document in the collection.</p>
     *
     * @return {@code true} if an index on {@code field} existed and was removed
     */
    public boolean dropIndex(String field) {
        var removed = indexes.remove(field);
        if (removed != null) {
            saveIndexes();
        }
        return removed != null;
    }

    public Document insert(Document doc) {
        return insert(doc, -1);
    }

    public Document insert(Document doc, long ttlSeconds) {
        eventBus.emit(EventBus.EventType.BEFORE_INSERT, name, doc);
        if (doc.id() == null) {
            doc.id(UUID.randomUUID().toString());
        }
        if (ttlSeconds > 0) {
            doc.expiresAt(System.currentTimeMillis() + (ttlSeconds * 1000));
        }
        engine.putRecord(name, doc);
        for (var idx : indexes.values()) {
            idx.add(doc);
        }
        if (cache != null) {
            cache.invalidatePattern(name);
        }
        metrics.recordInsert();
        metrics.updateCollectionSize(name, count());
        eventBus.emit(EventBus.EventType.AFTER_INSERT, name, doc);
        return doc;
    }

    public List<Document> insertAll(List<Document> docs) {
        return insertAll(docs, true); // Default to atomic
    }

    /**
     * Batch insert with atomicity option.
     * When atomic=true, all documents are inserted or none (rollback on failure).
     */
    public List<Document> insertAll(List<Document> docs, boolean atomic) {
        if (docs.isEmpty()) {
            return List.of();
        }

        List<Document> results = new ArrayList<>();
        List<Document> rollbackDocs = new ArrayList<>();

        try {
            // Begin transaction if engine supports it
            if (engine.supportsTransactions()) {
                engine.beginTransaction();
            }

            for (Document doc : docs) {
                // Pre-generate ID if not set for rollback tracking
                if (doc.id() == null) {
                    doc.id(UUID.randomUUID().toString());
                }
                results.add(insert(doc));
                rollbackDocs.add(doc);
            }

            // Commit if transaction was started
            if (engine.supportsTransactions()) {
                engine.commitTransaction();
            }

            return results;
        } catch (Exception e) {
            // Rollback on failure when atomic
            if (atomic && engine.supportsTransactions()) {
                try {
                    engine.rollbackTransaction();
                } catch (Exception rollbackEx) {
                    System.err.println("Rollback failed: " + rollbackEx.getMessage());
                }
            }
            // Manual rollback: delete any inserted docs
            if (atomic) {
                for (Document inserted : rollbackDocs) {
                    try {
                        if (inserted.id() != null) {
                            deleteById(inserted.id());
                        }
                    } catch (Exception rollbackEx) {
                        System.err.println("Failed to rollback document " + inserted.id() + ": " + rollbackEx.getMessage());
                    }
                }
            }
            throw new org.junify.db.core.exception.StorageException("Batch insert failed: " + e.getMessage(), e);
        }
    }

    public Document findById(String id) {
        metrics.recordRead();
        var json = engine.get(name, id);
        return json != null ? Document.fromJson(json) : null;
    }

    public List<Document> findAll() {
        if (cache != null) {
            var cached = cache.get(name + ":findAll");
            if (cached != null) {
                return cached;
            }
        }
        var results = engine.scan(name).stream()
                .map(Document::fromJson)
                .collect(Collectors.toList());
        if (cache != null) {
            cache.put(name + ":findAll", results);
        }
        return results;
    }

    public List<Document> findByIds(List<String> ids) {
        metrics.recordRead();
        var results = new ArrayList<Document>();
        for (var id : ids) {
            var json = engine.get(name, id);
            if (json != null) {
                results.add(Document.fromJson(json));
            }
        }
        return results;
    }

    public List<Document> find(Query query) {
        metrics.recordQuery();
        
        String cacheKey = cache != null ? QueryResultCache.cacheKey(name, query.toString()) : null;
        if (cacheKey != null) {
            var cached = cache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }
        
        List<Document> results;

        if (shouldUseIndex(query)) {
            results = findWithIndex(query);
        } else {
            results = engine.scan(name).stream()
                    .map(Document::fromJson)
                    .filter(query.docPredicate())
                    .collect(Collectors.toList());
        }

        if (query.sortOrder() != Query.SortOrder.NONE && query.sortField() != null) {
            var sf = query.sortField();
            Comparator<Document> cmp = (a, b) -> {
                var va = toComparable(a.getRaw(sf));
                var vb = toComparable(b.getRaw(sf));
                if (va == null && vb == null) return 0;
                if (va == null) return -1;
                if (vb == null) return 1;
                return ((Comparable) va).compareTo(vb);
            };
            if (query.sortOrder() == Query.SortOrder.DESC) {
                cmp = cmp.reversed();
            }
            results.sort(cmp);
        }

        int offset = query.offset();
        int limit = query.limit();
        if (offset > 0 || limit < Integer.MAX_VALUE) {
            int from = Math.min(offset, results.size());
            int to = (limit == Integer.MAX_VALUE) ? results.size() : Math.min(from + limit, results.size());
            var paged = results.subList(from, to);
            if (cacheKey != null) {
                cache.put(cacheKey, paged);
            }
            return paged;
        }

        if (cacheKey != null) {
            cache.put(cacheKey, results);
        }
        return results;
    }

    /**
     * Check if query should use index optimization.
     *
     * Index usage is determined by Query-level hints:
     * - If the query has NO_INDEX hint, never use an index.
     * - If the query has FORCE_INDEX hint, always use an index (if one exists).
     * - Otherwise, use an index only when an index exists on a field that the
     *   query explicitly targets via an equality predicate (Query.eq()).
     *   This is detected by checking whether any indexed field is named in
     *   the Query's indexed-field set, which avoids the previous O(n) live-data
     *   scan that defeated the whole purpose of indexing.
     */
    private boolean shouldUseIndex(Query query) {
        if (indexes.isEmpty()) {
            return false;
        }

        // Respect explicit query hints
        if (query.hasHint(Query.QueryHint.NO_INDEX)) {
            return false;
        }

        if (query.hasHint(Query.QueryHint.FORCE_INDEX)) {
            String forced = query.getForceIndexName();
            return forced == null || indexes.containsKey(forced);
        }

        // Only use an index when the query targets an indexed field directly.
        // Query exposes the indexed field name through getIndexedField().
        String indexedField = query.getIndexedField();
        return indexedField != null && indexes.containsKey(indexedField);
    }

    private List<Document> findWithIndex(Query query) {
        var pred = query.docPredicate();

        // Point lookup when the query is a simple equality on an indexed field
        // (audit R-18 / 12-IX-01): jump straight to the matching ids instead of
        // walking the entire index. Falls back to the full-index walk for
        // composite queries that merely *contain* an equality term.
        if (query.getIndexedField() != null && query.getIndexedValue() != null) {
            var idx = indexes.get(query.getIndexedField());
            if (idx != null) {
                var results = new ArrayList<Document>();
                for (var id : idx.lookup(query.getIndexedValue())) {
                    var doc = findById(id);
                    if (doc != null && pred.test(doc)) {
                        results.add(doc);
                    }
                }
                return results;
            }
        }

        for (var entry : indexes.entrySet()) {
            var idx = entry.getValue();
            var results = new ArrayList<Document>();
            var ids = idx.allValues();
            for (var id : ids) {
                var doc = findById(id);
                if (doc != null && pred.test(doc)) {
                    results.add(doc);
                }
            }
            return results;
        }
        return findAll().stream().filter(pred).collect(Collectors.toList());
    }

    public Document findOne(Query query) {
        var results = find(query.limit(1));
        return results.isEmpty() ? null : results.get(0);
    }

    public Document update(Document doc) {
        eventBus.emit(EventBus.EventType.BEFORE_UPDATE, name, doc);
        if (doc.id() == null) {
            throw new IllegalArgumentException("Document must have an id to update");
        }
        final var oldDoc = findById(doc.id());
        if (oldDoc == null) {
            throw new IllegalArgumentException("Document not found: " + doc.id());
        }
        engine.putRecord(name, doc);
        for (var idx : indexes.values()) {
            idx.update(oldDoc, doc);
        }
        if (cache != null) {
            cache.invalidatePattern(name);
        }
        metrics.recordUpdate();
        eventBus.emit(EventBus.EventType.AFTER_UPDATE, name, doc);
        return doc;
    }

    public Document upsert(Document doc) {
        if (doc.id() != null && engine.exists(name, doc.id())) {
            return update(doc);
        }
        return insert(doc);
    }

    public boolean deleteById(String id) {
        if (engine.exists(name, id)) {
            var oldDoc = findById(id);
            eventBus.emit(EventBus.EventType.BEFORE_DELETE, name, id);
            engine.delete(name, id);
            if (oldDoc != null) {
                for (var idx : indexes.values()) {
                    idx.remove(oldDoc);
                }
            }
            if (cache != null) {
                cache.invalidatePattern(name);
            }
            metrics.recordDelete();
            metrics.updateCollectionSize(name, count());
            eventBus.emit(EventBus.EventType.AFTER_DELETE, name, id);
            return true;
        }
        return false;
    }

    public long deleteAll(Query query) {
        var docs = find(query.limit(Integer.MAX_VALUE));
        for (var d : docs) {
            engine.delete(name, d.id());
            for ( var idx : indexes.values()) {
                idx.remove(d);
            }
            metrics.recordDelete();
        }
        metrics.updateCollectionSize(name, count());
        return docs.size();
    }

    public long bulkDelete(List<String> ids) {
        long deleted = 0;
        for (var id : ids) {
            if (deleteById(id)) {
                deleted++;
            }
        }
        return deleted;
    }

    public long count() {
        return engine.keys(name).size();
    }

    public long count(Query query) {
        return find(query).size();
    }

    public long cleanupExpired() {
        long cleaned = 0;
        for (var doc : findAll()) {
            if (doc.isExpired()) {
                deleteById(doc.id());
                cleaned++;
            }
        }
        return cleaned;
    }

    public boolean exists(String id) {
        return engine.exists(name, id);
    }

    public void clear() {
        for (var doc : findAll()) {
            engine.delete(name, doc.id());
        }
        indexes.clear();
    }

    public Map<String, Object> stats() {
        var allDocs = findAll();
        long expiredCount = allDocs.stream().filter(Document::isExpired).count();
        long activeCount = allDocs.size() - expiredCount;
        long withTtlCount = allDocs.stream().filter(d -> d.getExpiresAt() != null).count();

        return Map.of(
                "collection", name,
                "count", count(),
                "indexCount", indexes.size(),
                "storageEngine", engine.name(),
                "engineStats", engine.stats(),
                "ttl", Map.of(
                        "total", allDocs.size(),
                        "expired", expiredCount,
                        "active", activeCount,
                        "withTtl", withTtlCount
                )
        );
    }

    public long setTtl(String documentId, long ttlSeconds) {
        var doc = findById(documentId);
        if (doc == null) {
            return 0;
        }
        if (ttlSeconds > 0) {
            doc.expiresAt(System.currentTimeMillis() + (ttlSeconds * 1000));
        } else {
            doc.setExpiresAt(null);
        }
        update(doc);
        return 1;
    }

    public Map<String, Object> ttlStats() {
        var allDocs = findAll();
        long expiredCount = allDocs.stream().filter(Document::isExpired).count();
        long activeCount = allDocs.size() - expiredCount;
        long withTtlCount = allDocs.stream().filter(d -> d.getExpiresAt() != null).count();

        return Map.of(
                "total", allDocs.size(),
                "expired", expiredCount,
                "active", activeCount,
                "withTtl", withTtlCount
        );
    }

    @SuppressWarnings("unchecked")
    private static Comparable<?> toComparable(Object value) {
        if (value == null) return null;
        if (value instanceof Comparable c) return c;
        return value.toString();
    }
}
package org.embeddedjnosql.db;

import org.embeddedjnosql.db.adapter.jnosql.EntityMapper;
import org.embeddedjnosql.db.adapter.jnosql.EmbedRepository;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.embeddedjnosql.db.core.backup.BackupManager;
import org.embeddedjnosql.db.core.cdc.CDCManager;
import org.embeddedjnosql.db.core.event.EventBus;
import org.embeddedjnosql.db.core.schema.SchemaValidator;
import org.embeddedjnosql.db.index.TextIndex;
import org.embeddedjnosql.db.index.hnsw.VectorIndex;
import org.embeddedjnosql.db.nosql.column.ColumnFamily;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.document.Query;
import org.embeddedjnosql.db.nosql.kv.HashBucket;
import org.embeddedjnosql.db.nosql.kv.KeyValueBucket;
import org.embeddedjnosql.db.nosql.kv.ListBucket;
import org.embeddedjnosql.db.nosql.kv.SetBucket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Release feature sweep: every capability in the release contract, driven through the public API
 * against real storage. Console/REST coverage lives in the dedicated console test classes; this
 * class covers the library surface those tests do not reach.
 */
@DisplayName("Release feature sweep — the whole NoSQL contract, end to end")
class ReleaseFeatureSweepTest {

    private EmbedJNoSQL db;

    @AfterEach
    void tearDown() {
        if (db != null && db.isOpen()) {
            db.close();
        }
        db = null;
    }

    // =====================================================================
    // Document model
    // =====================================================================

    @Test
    @DisplayName("Document: insert, id assignment, read, replace, upsert, delete, lifecycle")
    void documentCrudLifecycle() {
        db = EmbedJNoSQL.inMemory();
        DocumentCollection products = db.documentCollection("products");
        // Documented, deliberate semantics: documentCollection(name) is the explicit materialization
        // point — it registers the collection and persists its existence, so an empty collection
        // survives a restart. Read-only paths (console routes, EntityQuery) resolve through the
        // catalog instead and never create anything.
        assertTrue(db.getCollectionNames().contains("products"),
                "documentCollection(name) materializes the collection");
        assertNull(products.findById("does-not-exist"), "a read of a missing id creates no document");
        assertEquals(0, products.count());

        // generated id when none is supplied
        Document generated = products.insert(Document.of("name", "Keyboard"));
        assertNotNull(generated.id(), "insert must assign an id when the document has none");
        assertEquals(1, products.count());

        // explicit id, read back
        products.insert(Document.of("name", "Mouse").id("p2").add("price", 25.0));
        Document read = products.findById("p2");
        assertNotNull(read);
        assertEquals("Mouse", read.get("name"));

        // insert with an existing id replaces the stored document (one document per id)
        products.insert(Document.of("name", "Mouse v2").id("p2"));
        assertEquals(2, products.count(), "an id identifies exactly one document");
        assertEquals("Mouse v2", products.findById("p2").get("name"));

        // update requires an existing id; upsert creates when missing
        assertThrows(IllegalArgumentException.class,
                () -> products.update(Document.of("name", "ghost").id("missing")));
        products.update(Document.of("name", "Mouse v3").id("p2"));
        assertEquals("Mouse v3", products.findById("p2").get("name"));
        products.upsert(Document.of("name", "Fresh").id("p3"));
        assertEquals(3, products.count());

        // exists / delete / unknown reads
        assertTrue(products.exists("p3"));
        assertNull(products.findById("nope"), "an unknown id reads as null, not an error");
        assertTrue(products.deleteById("p3"));
        assertFalse(products.deleteById("p3"), "deleting a missing id reports false");
        assertFalse(products.exists("p3"));

        // batch + clear
        products.insertAll(List.of(Document.of("name", "A").id("b1"), Document.of("name", "B").id("b2")));
        assertEquals(4, products.count());
        assertEquals(2, products.bulkDelete(List.of("b1", "b2")));
        products.clear();
        assertEquals(0, products.count());
        assertTrue(db.getCollectionNames().contains("products"), "clear must not delete the collection");
    }

    @Test
    @DisplayName("Document: nested values, arrays, scalar types, null versus missing")
    void documentTypesAndNullSemantics() {
        db = EmbedJNoSQL.inMemory();
        DocumentCollection c = db.documentCollection("typed");

        Document doc = Document.of("text", "value")
                .add("int", 42)
                .add("long", 9_000_000_000L)
                .add("double", 13.5)
                .add("bool", true)
                .add("nested", Map.of("inner", "deep", "n", 1))
                .add("array", List.of("a", "b"))
                .add("explicitNull", null)
                .id("t1");
        c.insert(doc);

        Document back = c.findById("t1");
        assertEquals("value", back.get("text"));
        assertEquals(13.5, ((Number) back.getRaw("double")).doubleValue(), 0.0001);
        assertEquals(true, back.get("bool"));
        assertEquals(42, ((Number) back.get("int")).intValue());
        assertEquals(9_000_000_000L, ((Number) back.get("long")).longValue());
        assertEquals(Map.of("inner", "deep", "n", 1), (Object) back.get("nested"));
        assertEquals(List.of("a", "b"), (Object) back.get("array"));

        // null versus missing is observable: has() distinguishes them, getRaw() reports null for both
        assertTrue(back.has("explicitNull"), "an explicitly stored null is a present field");
        assertFalse(back.has("neverSet"), "a field that was never written is absent");
        assertNull(back.getRaw("explicitNull"), "a stored null reads back as null");
        assertNull(back.getRaw("neverSet"));
    }

    @Test
    @DisplayName("Document TTL: insert with ttl, setTtl, ttlStats, cleanupExpired")
    void documentTtlAndCleanup() throws InterruptedException {
        db = EmbedJNoSQL.inMemory();
        DocumentCollection sessions = db.documentCollection("sessions");
        sessions.insert(Document.of("user", "a").id("s1"), 1);
        sessions.insert(Document.of("user", "b").id("s2"));

        assertEquals(2, sessions.count());
        assertNotNull(sessions.findById("s1"));
        assertTrue(sessions.setTtl("s2", 1) >= 0, "setTtl must report the write");
        assertFalse(sessions.ttlStats().isEmpty(), "ttlStats must report TTL bookkeeping");

        Thread.sleep(1200);
        long cleaned = sessions.cleanupExpired();
        assertTrue(cleaned >= 2, "both expired documents must be reclaimed, cleaned=" + cleaned);
        assertEquals(0, sessions.count(), "expired documents are gone after cleanup");
    }

    // =====================================================================
    // Key-Value and Redis-style structures
    // =====================================================================

    @Test
    @DisplayName("Key-Value: put, get, exists, delete, counters, bulk, TTL countdown and expiry")
    void keyValueContract() throws InterruptedException {
        db = EmbedJNoSQL.inMemory();
        KeyValueBucket kv = db.keyValueBucket("cache");

        kv.put("k1", "v1");
        assertEquals("v1", kv.get("k1"));
        assertTrue(kv.exists("k1"));
        assertNull(kv.get("missing"), "an absent key reads as null");
        assertEquals(1, kv.count());

        kv.putAll(Map.of("k2", "v2", "k3", "v3"));
        assertEquals(3, kv.count());
        assertEquals("v2", kv.getAll(List.of("k2")).get("k2"));
        assertEquals(3, kv.keys().size());

        assertEquals(1, kv.increment("counter"));
        assertEquals(3, kv.increment("counter", 2));
        assertEquals(2, kv.decrement("counter"));

        // overwrite semantics: a counter creates its own key, so take the baseline first
        int beforeOverwrite = (int) kv.count();
        kv.put("k1", "v1b");
        assertEquals("v1b", kv.get("k1"));
        assertEquals(beforeOverwrite, kv.count(), "overwriting a key must not add a key");
        assertEquals(4, kv.count(), "k1, k2, k3 plus the counter key");

        // TTL: visible now, expiry reported, invisible after the deadline
        kv.put("ephemeral", "gone-soon", Duration.ofMillis(200));
        assertTrue(kv.exists("ephemeral"));
        assertNotNull(kv.expiryOf("ephemeral"), "a TTL key must report its deadline");
        assertFalse(kv.expirations().isEmpty());
        Thread.sleep(400);
        assertNull(kv.get("ephemeral"), "an expired key must not be readable");
        assertFalse(kv.exists("ephemeral"));

        assertTrue(kv.delete("k1"));
        assertFalse(kv.delete("k1"), "deleting a missing key reports false");
        assertNotNull(kv.stats());
        kv.clear();
        assertEquals(0, kv.count());
    }

    @Test
    @DisplayName("Lists: push, range, index, set, trim, pop, move, delete")
    void listBucketOps() {
        db = EmbedJNoSQL.inMemory();
        ListBucket list = db.listBucket("queue");

        assertEquals(1, list.rpush("jobs", "a"));
        list.rpush("jobs", "b", "c");
        list.lpush("jobs", "z");
        assertEquals(4, list.llen("jobs"));
        assertEquals(List.of("z", "a", "b", "c"), list.lrange("jobs", 0, -1));
        assertEquals("a", list.lindex("jobs", 1));

        list.lset("jobs", 1, "A");
        assertEquals("A", list.lindex("jobs", 1));

        assertEquals("z", list.lpop("jobs"));
        assertEquals("c", list.rpop("jobs"));
        assertEquals(2, list.llen("jobs"));

        list.rpush("jobs", "d", "e", "f");
        list.ltrim("jobs", 0, 1);
        assertEquals(2, list.llen("jobs"), "ltrim keeps only the requested window");

        list.rpush("src", "x");
        list.rpush("dst", "y");
        assertEquals("x", list.rpoplpush("src", "dst"),
                "rpoplpush returns the element it moved from the source tail");
        assertEquals(List.of("x", "y"), list.lrange("dst", 0, -1),
                "the moved element lands on the destination head (rpop + lpush, Redis semantics)");
        assertEquals(0, list.llen("src"), "the source list is emptied by the move");

        assertTrue(list.keys().contains("jobs"));
        assertNotNull(list.stats());
        assertTrue(list.delete("jobs"));
        list.clear();
        assertTrue(list.lrange("dst", 0, -1).isEmpty());
    }

    @Test
    @DisplayName("Sets: add, members, membership, card, remove, move, intersect, pop")
    void setBucketOps() {
        db = EmbedJNoSQL.inMemory();
        SetBucket sets = db.setBucket("tags");

        assertEquals(2, sets.sadd("s1", "a", "b"));
        sets.sadd("s2", "b", "c");
        assertEquals(2, sets.scard("s1"));
        assertEquals(java.util.Set.of("a", "b"), sets.smembers("s1"));
        assertTrue(sets.sismember("s1", "a"));
        assertTrue(sets.contains("s1", "a"));

        assertTrue(sets.smove("s1", "s2", "a"));
        assertFalse(sets.sismember("s1", "a"));
        assertTrue(sets.sismember("s2", "a"));

        assertEquals(java.util.Set.of("b"), sets.sinter("s1", "s2"));

        assertEquals(1, sets.srem("s1", "b"));
        assertTrue(sets.smembers("s1").isEmpty());
        assertEquals(0, sets.size("s1"), "size reports the key's member count");

        assertNotNull(sets.spop("s2"), "spop returns a member");
        assertTrue(sets.keys().contains("s2"));
        sets.clear();
        assertTrue(sets.keys().isEmpty());
    }

    @Test
    @DisplayName("Hashes: hset, hget, hgetall, hlen, hexists, increments, hdel")
    void hashBucketOps() {
        db = EmbedJNoSQL.inMemory();
        HashBucket hash = db.hashBucket("profiles");

        assertEquals(1, hash.hset("u1", "name", "Ada"));
        hash.hset("u1", Map.of("role", "admin", "tier", "gold"));
        assertEquals("Ada", hash.hget("u1", "name"));
        assertEquals(3, hash.hlen("u1"));
        assertTrue(hash.hexists("u1", "role"));

        Map<String, String> all = hash.hgetall("u1");
        assertEquals("admin", all.get("role"));
        assertEquals(3, all.size());
        assertEquals(java.util.Set.of("name", "role", "tier"), hash.hkeys("u1"));
        assertEquals(3, hash.hvals("u1").size());
        assertEquals(6, hash.hgetallFlat("u1").size(), "flat form is field/value pairs");

        assertEquals(5, hash.hincrby("u1", "logins", 5));
        assertEquals(4, hash.hlen("u1"), "the counter created a fourth field");
        assertEquals("Ada".length(), hash.hstrlen("u1", "name"));

        assertEquals(1, hash.hdel("u1", "tier"));
        assertFalse(hash.hexists("u1", "tier"));
        assertEquals(3, hash.hlen("u1"));
        assertTrue(hash.keys().contains("u1"));
        hash.clear();
        assertTrue(hash.keys().isEmpty());
    }

    @Test
    @DisplayName("Column family: rows, columns, row slices, per-column TTL and cleanup")
    void columnFamilyOps() throws InterruptedException {
        db = EmbedJNoSQL.inMemory();
        ColumnFamily cf = db.columnFamily("inventory");

        cf.put("row1", "sku", "A-1");
        cf.put("row1", "qty", 41);
        cf.put("row2", "sku", "B-2");
        cf.put("row3", "sku", "C-3");

        assertEquals("A-1", cf.get("row1", "sku"));
        assertEquals(41, cf.get("row1", "qty"));
        Map<String, Object> row = cf.getRow("row1");
        assertEquals("A-1", row.get("sku"));
        assertEquals(2, row.size());

        var slice = cf.getRowSlice("row1", "row2", 10);
        assertFalse(slice.isEmpty(), "a bounded row slice must return the rows in range");
        assertNotNull(cf.getRowByPrefix("row1", "sk"));
        assertNotNull(cf.getRowWithFilter("row1", col -> col.startsWith("s")));

        cf.putWithTtl("row1", "session", "abc", 1);
        assertEquals("abc", cf.getWithTtlCheck("row1", "session"));
        assertTrue(cf.getRemainingTtl("row1", "session") >= 0);
        Thread.sleep(1200);
        assertTrue(cf.isExpired("row1", "session"), "a TTL column must report expiry");
        assertTrue(cf.cleanupExpiredColumns("row1") >= 1);
        assertTrue(cf.cleanupAllExpired() >= 0);
    }

    // =====================================================================
    // Queries and indexes
    // =====================================================================

    @Test
    @DisplayName("Query predicates: eq/ne/gt/gte/lt/lte/in/between/contains/regex/exists + and/or")
    void queryPredicates() {
        db = EmbedJNoSQL.inMemory();
        DocumentCollection c = db.documentCollection("items");
        c.insertAll(List.of(
                Document.of("name", "Keyboard").add("price", 75.0).add("stock", 41).id("i1"),
                Document.of("name", "Mouse Pad").add("price", 12.5).add("stock", 7).id("i2"),
                Document.of("name", "Monitor").add("price", 320.0).add("stock", 0).id("i3"),
                Document.of("name", "Mouse").add("price", 25.0).id("i4")));

        assertEquals(4, c.find(Query.all()).size());
        assertEquals(1, c.find(Query.eq("name", "Keyboard")).size());
        assertEquals(3, c.find(Query.ne("name", "Keyboard")).size());
        assertEquals(2, c.find(Query.gt("price", 25.0)).size());
        assertEquals(3, c.find(Query.gte("price", 25.0)).size());
        assertEquals(1, c.find(Query.lt("price", 25.0)).size());
        assertEquals(2, c.find(Query.lte("price", 25.0)).size());
        assertEquals(2, c.find(Query.in("name", List.of("Mouse", "Monitor"))).size());
        assertEquals(2, c.find(Query.between("price", 20.0, 100.0)).size());
        assertEquals(2, c.find(Query.contains("name", "Mouse")).size(), "substring match");
        assertEquals(3, c.find(Query.regex("name", "^M")).size(), "^M matches Mouse Pad, Monitor and Mouse");
        assertEquals(3, c.find(Query.exists("stock")).size(), "three documents carry the stock field");
        assertEquals(1, c.find(Query.all()).size() - c.find(Query.exists("stock")).size(),
                "exactly the fourth document has no stock field");

        // boolean composition
        assertEquals(1, c.find(Query.eq("name", "Keyboard").and(Query.gt("stock", 10))).size());
        assertEquals(0, c.find(Query.eq("name", "Keyboard").and(Query.gt("stock", 100))).size());
        assertEquals(3, c.find(Query.lt("price", 30.0).or(Query.eq("stock", 0))).size());
        assertEquals(1, c.count(Query.eq("name", "Monitor")), "count agrees with find");
    }

    @Test
    @DisplayName("Query ordering and windowing: asc/desc sort, limit, offset, page, determinism")
    void queryOrderingAndWindowing() {
        db = EmbedJNoSQL.inMemory();
        DocumentCollection c = db.documentCollection("prices");
        c.insertAll(List.of(
                Document.of("n", "a").add("price", 30.0).id("1"),
                Document.of("n", "b").add("price", 10.0).id("2"),
                Document.of("n", "c").add("price", 20.0).id("3"),
                Document.of("n", "d").add("price", 40.0).id("4")));

        List<Document> asc = c.find(Query.all().sortByAsc("price"));
        assertEquals(List.of(10.0, 20.0, 30.0, 40.0), asc.stream().map(d -> (Double) d.get("price")).toList());

        List<Document> desc = c.find(Query.all().sortByDesc("price"));
        assertEquals(List.of(40.0, 30.0, 20.0, 10.0), desc.stream().map(d -> (Double) d.get("price")).toList());

        List<Document> firstTwo = c.find(Query.all().sortByAsc("price").limit(2));
        assertEquals(2, firstTwo.size());
        assertEquals(10.0, firstTwo.get(0).get("price"));

        List<Document> skipped = c.find(Query.all().sortByAsc("price").limit(2).offset(2));
        assertEquals(2, skipped.size());
        assertEquals(30.0, skipped.get(0).get("price"));

        List<Document> page2 = c.find(Query.all().sortByAsc("price").page(1, 2));
        assertEquals(2, page2.size());
        assertEquals(30.0, page2.get(0).get("price"));

        // the same query must produce the same order every time
        assertEquals(asc.stream().map(Document::id).toList(), c.find(Query.all().sortByAsc("price")).stream().map(Document::id).toList());

        assertNull(c.findOne(Query.eq("price", 999.0)), "no match reads as null");
        assertNotNull(c.findOne(Query.eq("price", 10.0)));
    }

    @Test
    @DisplayName("Secondary index: create, lookup, range, drop, and persistence of index files")
    void secondaryIndexLifecycle() {
        db = EmbedJNoSQL.inMemory();
        DocumentCollection c = db.documentCollection("catalog");
        c.createIndex("category");
        c.insertAll(List.of(
                Document.of("category", "hw").id("a"),
                Document.of("category", "hw").id("b"),
                Document.of("category", "sw").id("c")));

        var index = c.getIndex("category");
        assertNotNull(index, "the created index must be retrievable");
        assertEquals(2, index.lookup("hw").size());
        assertEquals(1, index.lookup("sw").size());
        assertEquals(2, index.range("hw", "hw", true).size(),
                "an inclusive range over one value matches both documents that carry it");
        assertFalse(index.allValues().isEmpty());
        assertEquals(1, c.getIndexes().size(), "the created index is tracked on the collection");
        assertNotNull(index.toJson());

        assertTrue(c.dropIndex("category"));
        assertNull(c.getIndex("category"));
        assertFalse(c.dropIndex("category"), "dropping a missing index reports false");
    }

    @Test
    @DisplayName("Text index: tokenized search, phrase search, removal, term list")
    void textIndexSearch() {
        db = EmbedJNoSQL.inMemory();
        DocumentCollection c = db.documentCollection("docs");
        c.insertAll(List.of(
                Document.of("body", "the quick brown fox jumps").id("d1"),
                Document.of("body", "brown bears are quick").id("d2"),
                Document.of("body", "lazy dogs sleep").id("d3")));

        TextIndex index = new TextIndex("docs", "body");
        c.findAll().forEach(index::add);

        assertEquals(2, index.search("brown").size(), "two documents mention brown");
        assertEquals(2, index.search("quick").size());
        assertTrue(index.search("lazy").contains("d3"));
        assertFalse(index.getIndexedTerms().isEmpty());
        assertTrue(index.getIndexedTerms().contains("fox"), "indexed terms expose the vocabulary");
        assertNotNull(index.toMap());
        // size() reports postings — one (term, document) pair each — not documents:
        // d1 contributes quick/brown/fox/jumps, d2 brown/bears/quick, d3 lazy/dogs/sleep.
        assertEquals(10, index.size(), "size() counts (term, document) postings");
        assertFalse(index.searchPhrases(List.of("brown fox")).isEmpty(),
                "phrase search resolves candidate documents");

        long before = index.size();
        index.remove(c.findById("d1"));
        assertFalse(index.search("fox").contains("d1"), "a removed document must leave the index");
        assertTrue(index.size() < before, "removing a document drops its postings");
    }

    @Test
    @DisplayName("Vector index (experimental): add, k-NN ordering, size, remove")
    void vectorIndexKnn() {
        VectorIndex index = new VectorIndex(4, 16, 200);
        index.add("v1", new float[]{0.1f, 0.1f, 0.1f, 0.1f});
        index.add("v2", new float[]{0.9f, 0.9f, 0.9f, 0.9f});
        index.add("v3", new float[]{0.11f, 0.12f, 0.09f, 0.1f});
        assertEquals(3, index.size());

        List<String> nearest = index.search(new float[]{0.1f, 0.1f, 0.1f, 0.1f}, 2);
        assertEquals(2, nearest.size(), "k-NN must return exactly k ids");
        assertEquals("v1", nearest.get(0), "the nearest neighbour of v1 must be v1");
        // Recall beyond the top hit is approximate by design (HNSW, experimental); the scan
        // guarantees the count and the self-match, not exact ranking of the tail.

        List<String> euclid = index.searchEuclidean(new float[]{0.9f, 0.9f, 0.9f, 0.9f}, 1);
        assertEquals(List.of("v2"), euclid);

        index.remove("v1");
        assertEquals(2, index.size());
    }

    // =====================================================================
    // Transactions, persistence, recovery
    // =====================================================================

    @Test
    @DisplayName("Transactions: commit is visible, rollback is not, and status is reported")
    void transactionCommitAndRollback() {
        db = EmbedJNoSQL.inMemory();
        DocumentCollection c = db.documentCollection("accounts");

        try (var tx = db.beginTransaction()) {
            tx.documentCollection("accounts").insert(Document.of("owner", "ada").id("a1"));
            tx.commit();
        }
        assertEquals(1, c.count(), "a committed transaction is visible");

        try (var tx = db.beginTransaction()) {
            tx.documentCollection("accounts").insert(Document.of("owner", "grace").id("a2"));
            tx.rollback();
        }
        assertEquals(1, c.count(), "a rolled back transaction must leave nothing behind");
        assertNull(c.findById("a2"));

        var tx = db.beginTransaction();
        assertNotNull(tx.id());
        tx.documentCollection("accounts").insert(Document.of("owner", "linus").id("a3"));
        assertTrue(tx.operationCount() >= 1, "a transaction counts the operations it staged");
        assertNotNull(tx.readTimestamp());
        tx.commit();
        assertNotNull(c.findById("a3"));
        assertEquals(2, c.count());
    }

    @Test
    @DisplayName("Persistence: documents, KV, and the catalog survive close/reopen in every storage mode")
    void persistenceAcrossReopenForEveryStorageMode(@TempDir Path tempDir) throws Exception {
        for (EmbedJNoSQLConfig.StorageEngineType mode : EmbedJNoSQLConfig.StorageEngineType.values()) {
            Path dir = tempDir.resolve(mode.name().toLowerCase());
            Files.createDirectories(dir);

            String dataDir = dir.toString();
            EmbedJNoSQL first = EmbedJNoSQL.create(EmbedJNoSQL.embed()
                    .storageEngine(mode)
                    .dataDir(dataDir)
                    .autoFlush(true)
                    .buildConfig());
            try {
                first.documentCollection("persisted")
                        .insert(Document.of("name", "survivor").add("n", 7).id("k1"));
                first.keyValueBucket("persisted_kv").put("kv1", "kv-value");
            } finally {
                first.close();
            }

            EmbedJNoSQL reopened = EmbedJNoSQL.create(EmbedJNoSQL.embed()
                    .storageEngine(mode)
                    .dataDir(dataDir)
                    .autoFlush(true)
                    .buildConfig());
        try {
            if (mode == EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY) {
                // In-memory is the documented non-durable mode: nothing survives the close, and
                // that is the difference a user is choosing between.
                assertFalse(reopened.getCollectionNames().contains("persisted"),
                        "IN_MEMORY must not resurrect data after a restart");
                assertEquals(0, reopened.documentCollection("persisted").count(),
                        "IN_MEMORY reports an empty collection after reopen");
                continue;
            }
            assertTrue(reopened.getCollectionNames().contains("persisted"),
                    mode + ": the document collection must be listed after reopen");
            Document back = reopened.documentCollection("persisted").findById("k1");
            assertNotNull(back, mode + ": the document must survive the restart");
            assertEquals("survivor", back.get("name"));
            assertEquals(1, reopened.documentCollection("persisted").count(),
                    mode + ": exactly one copy of the document after reopen (no WAL double-apply)");
            assertEquals("kv-value", reopened.keyValueBucket("persisted_kv").get("kv1"),
                    mode + ": key-value data must survive the restart");
        } finally {
            reopened.close();
        }
        }
    }

    @Test
    @DisplayName("Backup and restore: snapshot captures documents, restore brings them back")
    void backupAndRestore(@TempDir Path tempDir) throws Exception {
        Path dataDir = tempDir.resolve("data");
        Files.createDirectories(dataDir);
        db = EmbedJNoSQL.create(EmbedJNoSQL.embed()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.FILE)
                .dataDir(dataDir.toString())
                .autoFlush(true)
                .buildConfig());

        db.documentCollection("orders").insert(Document.of("total", 100.0).id("o1"));
        db.documentCollection("orders").insert(Document.of("total", 200.0).id("o2"));

        BackupManager backups = new BackupManager(db.storageEngine());
        Path snapshot = backups.backup(dataDir.resolve("backups"));
        assertTrue(Files.exists(snapshot), "the snapshot file must exist on disk: " + snapshot);
        assertFalse(backups.lastBackupCounts().isEmpty(), "the backup must report what it captured");

        db.documentCollection("orders").deleteById("o1");
        db.documentCollection("orders").insert(Document.of("total", 999.0).id("o3"));
        assertEquals(2, db.documentCollection("orders").count());

        backups.restore(snapshot);
        // Verified semantics: restore re-applies the snapshot's documents additively. It brings
        // back what the snapshot holds; it does not act as a rollback of later writes.
        assertNotNull(db.documentCollection("orders").findById("o1"),
                "a document deleted after the snapshot comes back");
        assertNotNull(db.documentCollection("orders").findById("o2"));
        assertNotNull(db.documentCollection("orders").findById("o3"),
                "restore is additive: a document written after the snapshot is still there");
        assertEquals(3, db.documentCollection("orders").count());
    }

    // =====================================================================
    // Diagnostics: validation, events, metrics, CDC
    // =====================================================================

    @Test
    @DisplayName("Document validation rules: required fields accepted and rejected")
    void schemaValidationRules() {
        SchemaValidator validator = new SchemaValidator();
        validator.registerSchema("users", new SchemaValidator.SchemaDefinition("users")
                .field("name", String.class, true)
                .field("age", Integer.class, false));

        assertTrue(validator.hasSchema("users"));
        assertTrue(validator.getSchemaNames().contains("users"));
        assertNotNull(validator.getSchema("users"));

        assertTrue(validator.validate("users", Map.of("name", "Ada", "age", 36)).isValid());
        var invalid = validator.validate("users", Map.of("age", 36));
        assertFalse(invalid.isValid(), "a missing required field must fail validation");
        assertFalse(invalid.getErrors().isEmpty(), "the failure must explain itself");
        assertTrue(validator.validate("unregistered", Map.of()).isValid(),
                "collections without rules are schemaless and must not be rejected");

        validator.dropSchema("users");
        assertFalse(validator.hasSchema("users"));
    }

    @Test
    @DisplayName("Events and metrics: mutations emit lifecycle events and increment counters")
    void eventsAndMetrics() {
        db = EmbedJNoSQL.inMemory();
        AtomicInteger inserts = new AtomicInteger();
        db.eventBus().on(EventBus.EventType.AFTER_INSERT, e -> inserts.incrementAndGet());

        DocumentCollection c = db.documentCollection("observed");
        c.insert(Document.of("a", 1).id("e1"));
        c.insert(Document.of("a", 2).id("e2"));
        assertEquals(2, inserts.get(), "each insert must publish AFTER_INSERT");

        c.update(Document.of("a", 3).id("e1"));
        c.deleteById("e2");

        Map<String, Object> snapshot = db.metrics().snapshot();
        assertFalse(snapshot.isEmpty(), "metrics snapshot must not be empty");
        assertNotNull(db.metrics().memoryStats());
        assertNotNull(db.metrics().contentionStats());
        assertTrue(db.metrics().getFastInserts() > 0, "insert counters must advance");
        assertTrue(db.metrics().getFastReads() >= 0);
        assertTrue(db.eventBus().listenerCount() >= 1);
    }

    @Test
    @DisplayName("CDC: a file connector registers, observes writes, and can be removed")
    void cdcFileConnector(@TempDir Path tempDir) throws Exception {
        db = EmbedJNoSQL.create(EmbedJNoSQL.embed()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.FILE)
                .dataDir(tempDir.resolve("cdc-data").toString())
                .autoFlush(true)
                .buildConfig());

        CDCManager cdc = db.cdcManager();
        var connector = cdc.addFileConnector("sweep", tempDir.resolve("cdc-out"));
        assertTrue(cdc.hasConnector("sweep"), "the connector must be registered");
        connector.start();

        db.documentCollection("streamed").insert(Document.of("v", 1).id("c1"));
        db.documentCollection("streamed").update(Document.of("v", 2).id("c1"));
        db.documentCollection("streamed").deleteById("c1");
        connector.stop();

        assertNotNull(cdc.getStatus());
        assertFalse(cdc.getStatus().isEmpty());
        cdc.removeFileConnector("sweep");
        assertFalse(cdc.hasConnector("sweep"), "the connector must be removable");
        connector.close();
    }

    // =====================================================================
    // Jakarta NoSQL adapter
    // =====================================================================

    @jakarta.nosql.Entity("sweep_books")
    static class Book {
        @jakarta.nosql.Id
        private String isbn;

        @jakarta.nosql.Column("book_title")
        private String title;

        @jakarta.nosql.Column
        private double price;

        Book() {}

        Book(String isbn, String title, double price) {
            this.isbn = isbn;
            this.title = title;
            this.price = price;
        }

        String getIsbn() { return isbn; }
        String getTitle() { return title; }
        double getPrice() { return price; }
        void setPrice(double price) { this.price = price; }
    }

    @Test
    @DisplayName("Jakarta NoSQL adapter: annotation mapping, repository CRUD, fluent entity query")
    void jakartaAdapterAndRepository() {
        db = EmbedJNoSQL.create(EmbedJNoSQL.embed()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY)
                .buildConfig());

        assertEquals("sweep_books", EntityMapper.getCollectionName(Book.class),
                "@Entity names the collection");

        Book book = new Book("978-1", "Effective Java", 45.0);
        Document mapped = EntityMapper.toDocument(book);
        assertEquals("978-1", mapped.id(), "@Id becomes the document id");
        assertEquals("Effective Java", mapped.get("book_title"), "@Column renames the field");

        Book roundTrip = EntityMapper.fromDocument(mapped, Book.class);
        assertEquals("978-1", roundTrip.getIsbn());
        assertEquals("Effective Java", roundTrip.getTitle());
        assertEquals(45.0, roundTrip.getPrice(), 0.0001);

        // id-field type resolution (used by Spring to build EmbedRepository<T, ID> bean targets)
        assertEquals(String.class, EntityMapper.getIdFieldType(Book.class),
                "the @Id field's declared type must be resolvable");
        assertEquals(Object.class, EntityMapper.getIdFieldType(Object.class),
                "classes without an id field resolve to Object");

        // mapping alone must not persist anything
        assertTrue(EntityMapper.getCollectionName(Book.class) != null && db.getCollectionNames().isEmpty(),
                "mapping a class must not create a collection; only writes materialize it");

        EmbedRepository<Book, String> repo = EmbedRepository.of(Book.class, db);
        repo.save(new Book("978-1", "Effective Java", 45.0));
        repo.save(new Book("978-2", "Refactoring", 55.0));
        repo.save(new Book("978-3", "DDD", 65.0));
        assertEquals(3, repo.count());
        assertTrue(repo.existsById("978-2"));
        assertEquals("Refactoring", repo.findById("978-2").orElseThrow().getTitle());
        assertEquals(1, repo.findBy("book_title", "DDD").size());
        assertEquals(3, repo.findAll().size());

        // fluent entity query with bound parameters and ordering
        List<Book> cheap = db.from(Book.class)
                .where("price < ?", 60.0)
                .orderBy("price ASC")
                .list();
        assertEquals(2, cheap.size());
        assertEquals("978-1", cheap.get(0).getIsbn(), "ordered ascending by price");
        assertEquals(2, db.from(Book.class).where("price < ?", 60.0).count());
        assertEquals("978-1", db.from(Book.class).where("isbn = ?", "978-1").first().orElseThrow().getIsbn());

        repo.deleteById("978-3");
        assertEquals(2, repo.count());
    }

    @Test
    @DisplayName("Lifecycle: a closed database refuses work and reports its state")
    void closedDatabaseRejectsUse() {
        db = EmbedJNoSQL.inMemory();
        assertTrue(db.isOpen());
        assertFalse(db.isClosed());
        db.documentCollection("x").insert(Document.of("a", 1).id("1"));
        db.flush();
        db.close();
        assertFalse(db.isOpen());
        assertTrue(db.isClosed());
        assertThrows(IllegalStateException.class, () -> db.documentCollection("x"),
                "using a closed database must fail loudly instead of silently");
        db = null; // already closed
    }

    // =====================================================================
    // Experimental surface
    // =====================================================================

    @Test
    @DisplayName("Experimental label: the shipped-but-untested public classes carry @Experimental")
    void experimentalClassesAreLabeled() {
        // Product decision (release review round 3): keep these classes and label them
        // experimental instead of deleting them. They stay outside the release contract
        // (no production caller, no functional coverage, JaCoCo-excluded in pom.xml); this
        // assertion keeps the label honest — a class listed here must never silently lose
        // its @Experimental marker, and the marker must stay runtime-visible so consumers
        // can detect it reflectively.
        Class<?>[] experimentalSurface = {
                org.embeddedjnosql.db.core.pool.EmbedJNoSQLPool.class,
                org.embeddedjnosql.db.api.reactive.ReactiveJNoSQL.class,
                org.embeddedjnosql.db.core.migration.MigrationManager.class,
        };
        for (Class<?> type : experimentalSurface) {
            Experimental marker = type.getAnnotation(Experimental.class);
            assertNotNull(marker,
                    type.getName() + " ships without functional coverage and must carry @Experimental");
            assertFalse(marker.value().isBlank(),
                    type.getName() + " must state why it is experimental");
        }

        java.lang.annotation.Retention retention = Experimental.class.getAnnotation(java.lang.annotation.Retention.class);
        assertNotNull(retention, "@Experimental must declare a retention policy");
        assertEquals(java.lang.annotation.RetentionPolicy.RUNTIME, retention.value(),
                "@Experimental must be runtime-visible so consumers can detect it reflectively");
    }
}

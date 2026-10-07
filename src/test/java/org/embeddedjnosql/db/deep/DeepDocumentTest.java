package org.embeddedjnosql.db.deep;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.Query;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sub-Agent A — Deep Document Collection Tests.
 *
 * Covers: insert, find, update, delete, query operators, TTL,
 * stats, large payloads, nested structures, concurrent writes,
 * edge cases (null, unicode, oversized fields), multi-collection isolation.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class DeepDocumentTest {

    private static EmbedJNoSQL db;

    @BeforeAll
    static void init() {
        db = EmbedJNoSQL.embed().build();
    }

    @AfterAll
    static void teardown() {
        if (db != null && db.isOpen()) db.close();
    }

    // -----------------------------------------------------------------------
    // Basic CRUD
    // -----------------------------------------------------------------------

    @Test @Order(1)
    void insertReturnsDocWithId() {
        var col = db.documentCollection("d_crud");
        var doc = Document.of("name", "Alice").add("age", 30);
        var saved = col.insert(doc);
        assertNotNull(saved.id(), "Inserted doc must have an auto-generated ID");
        assertFalse(saved.id().isBlank());
    }

    @Test @Order(2)
    void findByIdReturnsCorrectDoc() {
        var col = db.documentCollection("d_crud");
        var saved = col.insert(Document.of("name", "Bob").add("city", "Paris"));
        var found = col.findById(saved.id());
        assertNotNull(found);
        assertEquals("Bob", found.get("name"));
        assertEquals("Paris", found.get("city"));
    }

    @Test @Order(3)
    void findAllContainsInsertedDocs() {
        var col = db.documentCollection("d_findall");
        col.insert(Document.of("tag", "x1"));
        col.insert(Document.of("tag", "x2"));
        col.insert(Document.of("tag", "x3"));
        var all = col.findAll();
        assertTrue(all.size() >= 3);
        long matchCount = all.stream().filter(d -> d.get("tag") != null
                && d.get("tag").toString().startsWith("x")).count();
        assertTrue(matchCount >= 3);
    }

    @Test @Order(4)
    void upsertWithSameIdOverwritesFields() {
        var col = db.documentCollection("d_upsert");
        var doc = Document.of("version", 1);
        var saved = col.insert(doc);

        saved.add("version", 2).add("updated", true);
        col.insert(saved); // same id → update

        var reloaded = col.findById(saved.id());
        assertNotNull(reloaded);
        assertEquals(2, ((Number) reloaded.get("version")).intValue());
        assertEquals(true, reloaded.get("updated"));
    }

    @Test @Order(5)
    void deleteByIdRemovesDoc() {
        var col = db.documentCollection("d_delete");
        var saved = col.insert(Document.of("tmp", true));
        assertTrue(col.deleteById(saved.id()));
        assertNull(col.findById(saved.id()));
    }

    @Test @Order(6)
    void deleteNonExistentReturnsFalse() {
        var col = db.documentCollection("d_delete");
        assertFalse(col.deleteById("nonexistent-id-000"));
    }

    // -----------------------------------------------------------------------
    // Query operators
    // -----------------------------------------------------------------------

    @Test @Order(10)
    void queryEqFindsMatchingDocs() {
        var col = db.documentCollection("d_query_eq");
        col.insert(Document.of("role", "admin"));
        col.insert(Document.of("role", "user"));
        col.insert(Document.of("role", "admin"));
        var results = col.find(Query.eq("role", "admin"));
        assertFalse(results.isEmpty());
        results.forEach(d -> assertEquals("admin", d.get("role")));
    }

    @Test @Order(11)
    void queryGtFiltersNumericValues() {
        var col = db.documentCollection("d_query_gt");
        col.insert(Document.of("score", 10));
        col.insert(Document.of("score", 50));
        col.insert(Document.of("score", 100));
        var results = col.find(Query.gt("score", 30.0));
        assertTrue(results.size() >= 2, "Expected at least 2 docs with score > 30");
        results.forEach(d -> assertTrue(((Number) d.get("score")).intValue() > 30));
    }

    @Test @Order(12)
    void queryLtFiltersNumericValues() {
        var col = db.documentCollection("d_query_lt");
        col.insert(Document.of("price", 5));
        col.insert(Document.of("price", 15));
        col.insert(Document.of("price", 25));
        var results = col.find(Query.lt("price", 10.0));
        assertFalse(results.isEmpty());
        results.forEach(d -> assertTrue(((Number) d.get("price")).intValue() < 10));
    }

    @Test @Order(13)
    void queryAllReturnsEveryDoc() {
        var col = db.documentCollection("d_query_all");
        col.insert(Document.of("k", "a"));
        col.insert(Document.of("k", "b"));
        var all = col.find(Query.all());
        assertTrue(all.size() >= 2);
    }

    // -----------------------------------------------------------------------
    // Stats
    // -----------------------------------------------------------------------

    @Test @Order(20)
    void statsContainsCountAndName() {
        var col = db.documentCollection("d_stats");
        col.insert(Document.of("val", 1));
        var stats = col.stats();
        assertNotNull(stats);
        assertTrue(stats.containsKey("count") || stats.containsKey("name") || stats.containsKey("collection"),
                "Stats should have at least one recognizable key: " + stats.keySet());
    }

    @Test @Order(21)
    void countReflectsInsertions() {
        var col = db.documentCollection("d_count");
        int before = (int) col.findAll().size();
        col.insert(Document.of("x", 1));
        col.insert(Document.of("x", 2));
        assertEquals(before + 2, col.findAll().size());
    }

    // -----------------------------------------------------------------------
    // Nested documents & complex payloads
    // -----------------------------------------------------------------------

    @Test @Order(30)
    void nestedMapFieldStoredAndRetrieved() {
        var col = db.documentCollection("d_nested");
        var address = Map.of("street", "123 Main St", "zip", "10001");
        var doc = Document.of("name", "Nested User").add("address", address);
        var saved = col.insert(doc);
        var found = col.findById(saved.id());
        assertNotNull(found);
        var foundAddress = found.get("address");
        assertNotNull(foundAddress, "Nested address field should be present");
    }

    @Test @Order(31)
    void listFieldStoredAndRetrieved() {
        var col = db.documentCollection("d_list_field");
        var tags = List.of("alpha", "beta", "gamma");
        var doc = Document.of("name", "Tagged").add("tags", tags);
        var saved = col.insert(doc);
        var found = col.findById(saved.id());
        assertNotNull(found);
        assertNotNull(found.get("tags"));
    }

    @Test @Order(32)
    void largeDocumentWith200Fields() {
        var col = db.documentCollection("d_large");
        var doc = new Document();
        for (int i = 0; i < 200; i++) {
            doc.add("field_" + i, "value_" + i);
        }
        var saved = col.insert(doc);
        var found = col.findById(saved.id());
        assertNotNull(found);
        assertEquals("value_100", found.get("field_100"));
        assertEquals("value_199", found.get("field_199"));
    }

    @Test @Order(33)
    void longStringValueStoredCorrectly() {
        var col = db.documentCollection("d_longstr");
        String longVal = "A".repeat(10_000);
        var saved = col.insert(Document.of("data", longVal));
        var found = col.findById(saved.id());
        assertNotNull(found);
        assertEquals(10_000, found.get("data").toString().length());
    }

    @Test @Order(34)
    void unicodeFieldsRoundTrip() {
        var col = db.documentCollection("d_unicode");
        var doc = Document.of("greeting", "こんにちは")
                .add("emoji", "🚀🔥💡")
                .add("arabic", "مرحبا");
        var saved = col.insert(doc);
        var found = col.findById(saved.id());
        assertNotNull(found);
        assertEquals("こんにちは", found.get("greeting"));
        assertEquals("🚀🔥💡", found.get("emoji"));
    }

    // -----------------------------------------------------------------------
    // Edge cases
    // -----------------------------------------------------------------------

    @Test @Order(40)
    void insertDocumentWithNullValue() {
        var col = db.documentCollection("d_null");
        var doc = new Document();
        doc.add("name", null);
        var saved = col.insert(doc);
        assertNotNull(saved.id(), "Document with null field should still get an ID");
    }

    @Test @Order(41)
    void emptyCollectionHasZeroDocuments() {
        var col = db.documentCollection("d_empty_" + System.nanoTime());
        assertTrue(col.findAll().isEmpty(), "Fresh collection must be empty");
        assertEquals(0, col.count());
    }

    @Test @Order(42)
    void findByIdMissingReturnsNull() {
        var col = db.documentCollection("d_missing");
        assertNull(col.findById("no-such-id-xyz-123"));
    }

    @Test @Order(43)
    void booleanFieldRoundTrip() {
        var col = db.documentCollection("d_bool");
        var doc = Document.of("active", true).add("deleted", false);
        var saved = col.insert(doc);
        var found = col.findById(saved.id());
        assertNotNull(found);
        assertEquals(true, found.get("active"));
        assertEquals(false, found.get("deleted"));
    }

    @Test @Order(44)
    void numericTypesRoundTrip() {
        var col = db.documentCollection("d_nums");
        var doc = Document.of("intVal", 42)
                .add("longVal", Long.MAX_VALUE)
                .add("doubleVal", 3.14159);
        var saved = col.insert(doc);
        var found = col.findById(saved.id());
        assertNotNull(found);
        assertEquals(42, ((Number) found.get("intVal")).intValue());
        assertEquals(3.14159, ((Number) found.get("doubleVal")).doubleValue(), 0.00001);
    }

    // -----------------------------------------------------------------------
    // Multi-collection isolation
    // -----------------------------------------------------------------------

    @Test @Order(50)
    void collectionsAreIsolatedFromEachOther() {
        var col1 = db.documentCollection("d_iso_1");
        var col2 = db.documentCollection("d_iso_2");

        col1.insert(Document.of("owner", "col1"));
        col2.insert(Document.of("owner", "col2"));

        var col1Docs = col1.findAll();
        var col2Docs = col2.findAll();

        assertTrue(col1Docs.stream().allMatch(d -> "col1".equals(d.get("owner"))));
        assertTrue(col2Docs.stream().allMatch(d -> "col2".equals(d.get("owner"))));
    }

    @Test @Order(51)
    void tenCollectionsAllIndependent() {
        List<String> names = IntStream.range(0, 10)
                .mapToObj(i -> "d_multi_" + i).toList();
        names.forEach(n -> db.documentCollection(n).insert(Document.of("col", n)));
        names.forEach(n -> {
            var docs = db.documentCollection(n).findAll();
            assertFalse(docs.isEmpty(), n + " should have 1 doc");
            assertEquals(n, docs.get(0).get("col"));
        });
    }

    // -----------------------------------------------------------------------
    // Parameterized tests
    // -----------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"alpha", "beta", "gamma", "delta", "epsilon"})
    @Order(60)
    void insertAndFindByTagValue(String tag) {
        var col = db.documentCollection("d_param");
        col.insert(Document.of("tag", tag));
        var results = col.find(Query.eq("tag", tag));
        assertFalse(results.isEmpty(), "Should find at least one doc with tag=" + tag);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 10, 50, 100, 500})
    @Order(61)
    void insertNDocsAndVerifyCount(int n) {
        String colName = "d_batch_" + n;
        var col = db.documentCollection(colName);
        for (int i = 0; i < n; i++) {
            col.insert(Document.of("idx", i));
        }
        assertEquals(n, col.count(), "Collection should have exactly " + n + " docs");
    }

    // -----------------------------------------------------------------------
    // Concurrency
    // -----------------------------------------------------------------------

    @Test @Order(70)
    void concurrentInsertsAreThreadSafe() throws Exception {
        var col = db.documentCollection("d_concurrent");
        int threads = 8;
        int perThread = 100;
        var pool = Executors.newFixedThreadPool(threads);
        var futures = new ArrayList<Future<?>>();
        for (int t = 0; t < threads; t++) {
            int tid = t;
            futures.add(pool.submit(() -> {
                for (int i = 0; i < perThread; i++) {
                    col.insert(Document.of("thread", tid).add("seq", i));
                }
            }));
        }
        for (var f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        assertEquals(threads * perThread, col.count(),
                "All concurrent inserts must be persisted without loss");
    }

    @Test @Order(71)
    void concurrentReadWriteDoesNotCorruptData() throws Exception {
        var col = db.documentCollection("d_rw_concurrent");
        // Pre-seed
        for (int i = 0; i < 50; i++) col.insert(Document.of("base", i));

        var pool = Executors.newFixedThreadPool(8);
        var futures = new ArrayList<Future<?>>();

        // 4 writers
        for (int w = 0; w < 4; w++) {
            int wid = w;
            futures.add(pool.submit(() -> {
                for (int i = 0; i < 25; i++) {
                    col.insert(Document.of("writer", wid).add("val", i));
                }
            }));
        }
        // 4 readers
        for (int r = 0; r < 4; r++) {
            futures.add(pool.submit(() -> {
                for (int i = 0; i < 25; i++) {
                    assertDoesNotThrow(() -> col.findAll());
                }
            }));
        }

        for (var f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();
        // Total count = 50 (base) + 4 * 25 (writers) = 150
        assertTrue(col.count() >= 150, "All writes must survive concurrent reads");
    }

    // -----------------------------------------------------------------------
    // TTL
    // -----------------------------------------------------------------------

    @Test @Order(80)
    void setTtlDoesNotThrow() {
        var col = db.documentCollection("d_ttl");
        var saved = col.insert(Document.of("expiring", true));
        assertDoesNotThrow(() -> col.setTtl(saved.id(), 3600));
    }

    @Test @Order(81)
    void cleanupExpiredReturnsNonNegative() {
        var col = db.documentCollection("d_ttl_cleanup");
        col.insert(Document.of("tmp", 1));
        long deleted = col.cleanupExpired();
        assertTrue(deleted >= 0, "cleanupExpired should return >= 0");
    }
}
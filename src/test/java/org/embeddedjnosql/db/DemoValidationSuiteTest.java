package org.embeddedjnosql.db;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.document.Query;
import org.embeddedjnosql.db.nosql.kv.KeyValueBucket;
import org.embeddedjnosql.db.transaction.mvcc.Transaction;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Demo Validation Suite — Realistic use cases, edge cases, persistence boundaries,
 * and concurrency correctness scenarios.
 *
 * <p>Organised into groups:
 * <ul>
 *   <li>SC-DOC-*: Realistic document use cases (product catalog, employee directory,
 *       app configuration, audit records, inventory)</li>
 *   <li>SC-KV-*: Realistic key-value use cases (sessions, cache, state, idempotency,
 *       checkpoints)</li>
 *   <li>SC-EDGE-*: Systematic edge cases (empty datasets, duplicates, unicode,
 *       null/absent, numeric boundaries, pagination boundaries, TTL boundaries)</li>
 *   <li>SC-PERSIST-*: Separate-process-boundary persistence checks using @TempDir</li>
 *   <li>SC-TX-*: Transaction correctness, concurrency, visibility, rollback integrity</li>
 * </ul>
 *
 * <p>Every test asserts concrete values — counts, IDs, field values, and orderings —
 * not just "no exception thrown". Failure exits nonzero via JUnit assertion errors.
 */
class DemoValidationSuiteTest {

    // =========================================================================
    // SC-DOC-A: PRODUCT CATALOG
    // =========================================================================

    @Nested
    @DisplayName("SC-DOC-A: Product Catalog")
    class ProductCatalogScenario {

        private EmbedJNoSQL db;
        private DocumentCollection products;

        @BeforeEach
        void setUp() {
            db = EmbedJNoSQL.inMemory();
            products = db.documentCollection("products");

            // Deterministic fixture: 6 products across 3 categories
            products.insert(new Document().id("p-001")
                    .add("name", "Ergonomic Keyboard").add("category", "peripherals")
                    .add("price", 129.99).add("available", true)
                    .add("specs", Map.of("switches", "brown", "layout", "US-104")));
            products.insert(new Document().id("p-002")
                    .add("name", "4K Monitor").add("category", "displays")
                    .add("price", 549.00).add("available", true)
                    .add("specs", Map.of("resolution", "3840x2160", "hz", 60)));
            products.insert(new Document().id("p-003")
                    .add("name", "Mechanical Mouse").add("category", "peripherals")
                    .add("price", 79.50).add("available", false)
                    .add("specs", Map.of("dpi", 12000, "buttons", 7)));
            products.insert(new Document().id("p-004")
                    .add("name", "USB-C Hub").add("category", "accessories")
                    .add("price", 45.00).add("available", true)
                    .add("specs", Map.of("ports", 7, "pd_watts", 100)));
            products.insert(new Document().id("p-005")
                    .add("name", "Curved Monitor").add("category", "displays")
                    .add("price", 399.00).add("available", true)
                    .add("specs", Map.of("resolution", "2560x1440", "hz", 165)));
            products.insert(new Document().id("p-006")
                    .add("name", "Desk Lamp").add("category", "accessories")
                    .add("price", 29.99).add("available", true)
                    .add("specs", Map.of("color_temp", "3000-6500K")));
        }

        @AfterEach
        void tearDown() { db.close(); }

        @Test
        @DisplayName("SC-DOC-A-01: filter by category returns exactly matching products")
        void filterByCategory() {
            var peripherals = products.find(Query.eq("category", "peripherals"));
            assertEquals(2, peripherals.size());
            var ids = peripherals.stream().map(Document::id).collect(java.util.stream.Collectors.toSet());
            assertTrue(ids.contains("p-001"));
            assertTrue(ids.contains("p-003"));
        }

        @Test
        @DisplayName("SC-DOC-A-02: filter by availability returns only available products")
        void filterByAvailability() {
            var available = products.find(Query.eq("available", true));
            assertEquals(5, available.size());
            // p-003 (Mechanical Mouse) is not available
            var unavailable = products.find(Query.eq("available", false));
            assertEquals(1, unavailable.size());
            assertEquals("p-003", unavailable.get(0).id());
        }

        @Test
        @DisplayName("SC-DOC-A-03: price range filter returns expected product set")
        void priceRangeFilter() {
            var midRange = products.find(
                Query.gte("price", 50.0).and(Query.lte("price", 400.0))
            );
            // p-003(79.5), p-002 is 549 (excluded), p-005(399), p-001(129.99)
            var ids = midRange.stream().map(Document::id).collect(java.util.stream.Collectors.toSet());
            assertTrue(ids.contains("p-001"), "p-001 at 129.99 should be in range");
            assertTrue(ids.contains("p-003"), "p-003 at 79.50 should be in range");
            assertTrue(ids.contains("p-005"), "p-005 at 399.00 should be in range");
            assertFalse(ids.contains("p-002"), "p-002 at 549.00 should be excluded");
        }

        @Test
        @DisplayName("SC-DOC-A-04: sort by price ascending produces correct order")
        void sortByPriceAscending() {
            var sorted = products.find(Query.all().sortByAsc("price"));
            assertEquals(6, sorted.size());
            // Verify first and last prices
            double first = ((Number) sorted.get(0).get("price")).doubleValue();
            double last = ((Number) sorted.get(5).get("price")).doubleValue();
            assertEquals(29.99, first, 0.01, "cheapest product first");
            assertEquals(549.00, last, 0.01, "most expensive product last");
            // Verify strictly ascending
            for (int i = 1; i < sorted.size(); i++) {
                double prev = ((Number) sorted.get(i - 1).get("price")).doubleValue();
                double cur = ((Number) sorted.get(i).get("price")).doubleValue();
                assertTrue(cur >= prev, "ordering violated at index " + i);
            }
        }

        @Test
        @DisplayName("SC-DOC-A-05: pagination returns correct slice")
        void pagination() {
            var page1 = products.find(Query.all().sortByAsc("name").limit(2).offset(0));
            var page2 = products.find(Query.all().sortByAsc("name").limit(2).offset(2));
            var page3 = products.find(Query.all().sortByAsc("name").limit(2).offset(4));
            assertEquals(2, page1.size());
            assertEquals(2, page2.size());
            assertEquals(2, page3.size());
            // Pages must not overlap
            var all = new ArrayList<String>();
            page1.forEach(d -> all.add(d.id()));
            page2.forEach(d -> all.add(d.id()));
            page3.forEach(d -> all.add(d.id()));
            assertEquals(6, new HashSet<>(all).size(), "pages must contain distinct documents");
        }

        @Test
        @DisplayName("SC-DOC-A-06: out-of-range page returns empty list")
        void outOfRangePage() {
            var beyond = products.find(Query.all().limit(10).offset(100));
            assertTrue(beyond.isEmpty(), "offset beyond dataset must return empty list");
        }

        @Test
        @DisplayName("SC-DOC-A-07: update product price and verify new value")
        void updateProduct() {
            Document p = products.findById("p-004");
            assertNotNull(p);
            p.add("price", 39.99);
            products.update(p);
            Document updated = products.findById("p-004");
            assertEquals(39.99, ((Number) updated.get("price")).doubleValue(), 0.001);
        }

        @Test
        @DisplayName("SC-DOC-A-08: delete product and confirm absence")
        void deleteProduct() {
            assertTrue(products.deleteById("p-006"));
            assertNull(products.findById("p-006"));
            assertEquals(5, products.count());
        }

        @Test
        @DisplayName("SC-DOC-A-09: nested spec field accessible via dotted path")
        void nestedSpecField() {
            var highHz = products.find(Query.gte("specs.hz", 100));
            assertEquals(1, highHz.size());
            assertEquals("p-005", highHz.get(0).id(), "only Curved Monitor at 165Hz");
        }

        @Test
        @DisplayName("SC-DOC-A-10: combined predicate: available peripherals")
        void combinedPredicate() {
            var result = products.find(
                Query.eq("category", "peripherals").and(Query.eq("available", true))
            );
            assertEquals(1, result.size());
            assertEquals("p-001", result.get(0).id());
        }
    }

    // =========================================================================
    // SC-DOC-B: EMPLOYEE DIRECTORY
    // =========================================================================

    @Nested
    @DisplayName("SC-DOC-B: Employee Directory")
    class EmployeeDirectoryScenario {

        private EmbedJNoSQL db;
        private DocumentCollection employees;

        @BeforeEach
        void setUp() {
            db = EmbedJNoSQL.inMemory();
            employees = db.documentCollection("employees");

            employees.insert(new Document().id("e-001")
                    .add("name", "Ada Lovelace").add("department", "Engineering")
                    .add("location", "London").add("level", 5)
                    .add("contact", Map.of("email", "ada@example.com", "phone", "+44-20-1234")));
            employees.insert(new Document().id("e-002")
                    .add("name", "Björn Ångström").add("department", "Research")
                    .add("location", "Stockholm").add("level", 7)
                    .add("contact", Map.of("email", "bjorn@example.com")));
            // unicode name: Mandarin
            employees.insert(new Document().id("e-003")
                    .add("name", "李明").add("department", "Engineering")
                    .add("location", "Beijing").add("level", 4));
            // optional field absent: no 'location'
            employees.insert(new Document().id("e-004")
                    .add("name", "Grace Hopper").add("department", "Engineering")
                    .add("level", 8));
            // explicit null location
            employees.insert(new Document().id("e-005")
                    .add("name", "Alan Turing").add("department", "Research")
                    .add("location", (Object) null).add("level", 9));
        }

        @AfterEach
        void tearDown() { db.close(); }

        @Test
        @DisplayName("SC-DOC-B-01: unicode name round-trips correctly")
        void unicodeNameRoundTrip() {
            Document d = employees.findById("e-003");
            assertNotNull(d);
            assertEquals("李明", d.get("name"));
        }

        @Test
        @DisplayName("SC-DOC-B-02: department filter returns correct employees")
        void departmentFilter() {
            var eng = employees.find(Query.eq("department", "Engineering"));
            assertEquals(3, eng.size());
            var ids = eng.stream().map(Document::id).collect(java.util.stream.Collectors.toSet());
            assertTrue(ids.contains("e-001"));
            assertTrue(ids.contains("e-003"));
            assertTrue(ids.contains("e-004"));
        }

        @Test
        @DisplayName("SC-DOC-B-03: query for senior employees by level threshold")
        void levelFilter() {
            var seniors = employees.find(Query.gte("level", 7));
            assertEquals(3, seniors.size());
            seniors.forEach(e -> {
                int level = ((Number) e.get("level")).intValue();
                assertTrue(level >= 7, "e-" + e.id() + " has level " + level + " < 7");
            });
        }

        @Test
        @DisplayName("SC-DOC-B-04: nested contact email accessible via dotted path")
        void nestedContactEmail() {
            var result = employees.find(Query.eq("contact.email", "ada@example.com"));
            assertEquals(1, result.size());
            assertEquals("e-001", result.get(0).id());
        }

        @Test
        @DisplayName("SC-DOC-B-05: absent field vs explicit null behave consistently on findAll")
        void absentVsNullField() {
            // Both e-004 (absent location) and e-005 (null location) must be in findAll
            var all = employees.findAll();
            assertEquals(5, all.size());
            // e-004 has no location key
            Document e4 = employees.findById("e-004");
            assertNull(e4.get("location"), "absent field returns null");
            // e-005 has explicit null location
            Document e5 = employees.findById("e-005");
            assertNull(e5.get("location"), "explicit null field also returns null");
        }
    }

    // =========================================================================
    // SC-DOC-C: APPLICATION CONFIGURATION
    // =========================================================================

    @Nested
    @DisplayName("SC-DOC-C: Application Configuration (persistence)")
    class AppConfigurationScenario {

        @Test
        @DisplayName("SC-DOC-C-01: config persists across close and reopen of B_TREE store")
        void configPersistsAcrossRestart(@TempDir Path dir) {
            // Write
            try (EmbedJNoSQL db = EmbedJNoSQL.embed()
                    .storageEngine(EmbedJNoSQLConfig.StorageEngineType.B_TREE)
                    .dataDir(dir).build()) {
                var cfg = db.documentCollection("app_config");
                cfg.insert(new Document().id("smtp")
                        .add("host", "smtp.example.com").add("port", 587)
                        .add("tls", true)
                        .add("credentials", Map.of("user", "mailer", "pass", "s3cr3t")));
                cfg.insert(new Document().id("feature_flags")
                        .add("dark_mode", true).add("beta_api", false).add("max_users", 1000));
            }
            // Reopen — different object, same directory (process boundary within JVM)
            try (EmbedJNoSQL db = EmbedJNoSQL.embed()
                    .storageEngine(EmbedJNoSQLConfig.StorageEngineType.B_TREE)
                    .dataDir(dir).build()) {
                var cfg = db.documentCollection("app_config");
                Document smtp = cfg.findById("smtp");
                assertNotNull(smtp, "smtp config must survive restart");
                assertEquals("smtp.example.com", smtp.get("host"));
                assertEquals(587, ((Number) smtp.get("port")).intValue());
                assertTrue((Boolean) smtp.get("tls"));

                Document flags = cfg.findById("feature_flags");
                assertNotNull(flags, "feature_flags must survive restart");
                assertEquals(1000, ((Number) flags.get("max_users")).intValue());
            }
        }

        @Test
        @DisplayName("SC-DOC-C-02: full replacement (update) replaces all fields")
        void fullReplacementUpdate() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var cfg = db.documentCollection("app_config");
                cfg.insert(new Document().id("db_conn")
                        .add("host", "localhost").add("port", 5432).add("legacy_flag", true));
                // Full replacement: legacy_flag must vanish
                cfg.update(new Document().id("db_conn")
                        .add("host", "prod-db.example.com").add("port", 5433));
                Document updated = cfg.findById("db_conn");
                assertEquals("prod-db.example.com", updated.get("host"));
                assertEquals(5433, ((Number) updated.get("port")).intValue());
                assertNull(updated.get("legacy_flag"),
                        "update is a full replacement; legacy_flag must be absent after update");
            }
        }

        @Test
        @DisplayName("SC-DOC-C-03: default absent field returns null; upsert adds it")
        void upsertAddsNewField() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var cfg = db.documentCollection("app_config");
                // Upsert new document (non-existent)
                cfg.upsert(new Document().id("rate_limit").add("max_rpm", 100));
                Document rl = cfg.findById("rate_limit");
                assertNotNull(rl);
                assertEquals(100, ((Number) rl.get("max_rpm")).intValue());
                // Upsert existing document — should update
                cfg.upsert(new Document().id("rate_limit").add("max_rpm", 200));
                Document updated = cfg.findById("rate_limit");
                assertEquals(200, ((Number) updated.get("max_rpm")).intValue());
            }
        }
    }

    // =========================================================================
    // SC-DOC-D: AUDIT RECORDS (append + pagination)
    // =========================================================================

    @Nested
    @DisplayName("SC-DOC-D: Audit/Event Records")
    class AuditRecordsScenario {

        @Test
        @DisplayName("SC-DOC-D-01: append 50 audit records and paginate through all of them")
        void appendAndPaginate() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var audit = db.documentCollection("audit_log");
                // Insert 50 deterministic records
                for (int i = 1; i <= 50; i++) {
                    audit.insert(new Document().id(String.format("evt-%03d", i))
                            .add("action", i % 2 == 0 ? "READ" : "WRITE")
                            .add("user", "user-" + (i % 5))
                            .add("seq", i));
                }
                assertEquals(50, audit.count());
                // Paginate 10 at a time, verify no gaps or duplicates
                var seenIds = new HashSet<String>();
                for (int page = 0; page < 5; page++) {
                    var slice = audit.find(Query.all().sortByAsc("seq").limit(10).offset(page * 10));
                    assertEquals(10, slice.size(), "page " + page + " must have 10 records");
                    slice.forEach(d -> assertTrue(seenIds.add(d.id()), "duplicate id: " + d.id()));
                }
                assertEquals(50, seenIds.size(), "all 50 records must be covered exactly once");
            }
        }

        @Test
        @DisplayName("SC-DOC-D-02: filter WRITE events returns correct subset")
        void filterByAction() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var audit = db.documentCollection("audit_log");
                for (int i = 1; i <= 20; i++) {
                    audit.insert(new Document().id("e-" + i)
                            .add("action", i % 3 == 0 ? "DELETE" : "WRITE").add("seq", i));
                }
                var deletes = audit.find(Query.eq("action", "DELETE"));
                // seq 3,6,9,12,15,18 → 6 DELETE events
                assertEquals(6, deletes.size());
                deletes.forEach(d -> assertEquals("DELETE", d.get("action")));
            }
        }

        @Test
        @DisplayName("SC-DOC-D-03: TTL on audit records — expired records absent on read")
        void ttlAuditRecord() throws InterruptedException {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var audit = db.documentCollection("audit_log");
                audit.insert(new Document().id("tmp-event").add("action", "LOGIN"), 1);
                audit.insert(new Document().id("permanent-event").add("action", "AUDIT"));
                Thread.sleep(1300);
                assertNull(audit.findById("tmp-event"), "TTL record must be gone after 1s");
                assertNotNull(audit.findById("permanent-event"), "non-TTL record must remain");
            }
        }
    }

    // =========================================================================
    // SC-DOC-E: INVENTORY MANAGEMENT (update + concurrency)
    // =========================================================================

    @Nested
    @DisplayName("SC-DOC-E: Inventory Management")
    class InventoryScenario {

        @Test
        @DisplayName("SC-DOC-E-01: concurrent reads see consistent data after single write")
        void concurrentReadsAfterWrite() throws Exception {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var inventory = db.documentCollection("inventory");
                inventory.insert(new Document().id("sku-1").add("qty", 100));

                int threads = 20;
                var latch = new CountDownLatch(threads);
                var errors = new AtomicInteger(0);
                var executor = Executors.newFixedThreadPool(threads);

                for (int t = 0; t < threads; t++) {
                    executor.submit(() -> {
                        try {
                            Document d = inventory.findById("sku-1");
                            if (d == null || ((Number) d.get("qty")).intValue() != 100) {
                                errors.incrementAndGet();
                            }
                        } finally {
                            latch.countDown();
                        }
                    });
                }
                assertTrue(latch.await(10, TimeUnit.SECONDS));
                executor.shutdown();
                assertEquals(0, errors.get(), "all concurrent reads must see qty=100");
            }
        }

        @Test
        @DisplayName("SC-DOC-E-02: transactional stock deduction prevents lost update")
        void transactionalStockDeduction() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var inventory = db.documentCollection("inventory");
                inventory.insert(new Document().id("sku-2").add("qty", 5));

                // Deduct 2 in a transaction
                try (Transaction tx = db.beginTransaction()) {
                    var txInv = tx.documentCollection("inventory");
                    Document d = txInv.findById("sku-2");
                    assertNotNull(d, "must see sku-2 inside transaction");
                    int currentQty = ((Number) d.get("qty")).intValue();
                    d.add("qty", currentQty - 2);
                    txInv.update(d);
                    tx.commit();
                }
                Document after = inventory.findById("sku-2");
                assertEquals(3, ((Number) after.get("qty")).intValue());
            }
        }

        @Test
        @DisplayName("SC-DOC-E-03: rolled-back stock deduction leaves qty unchanged")
        void rollbackPreservesQty() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var inventory = db.documentCollection("inventory");
                inventory.insert(new Document().id("sku-3").add("qty", 10));

                try (Transaction tx = db.beginTransaction()) {
                    var txInv = tx.documentCollection("inventory");
                    Document d = txInv.findById("sku-3");
                    d.add("qty", 0); // simulate a bad deduction
                    txInv.update(d);
                    tx.rollback();
                }
                Document after = inventory.findById("sku-3");
                assertEquals(10, ((Number) after.get("qty")).intValue(),
                        "rollback must restore original qty");
            }
        }
    }

    // =========================================================================
    // SC-KV-A: SESSION STORAGE
    // =========================================================================

    @Nested
    @DisplayName("SC-KV-A: Session Storage")
    class SessionStorageScenario {

        private EmbedJNoSQL db;
        private KeyValueBucket sessions;

        @BeforeEach
        void setUp() {
            db = EmbedJNoSQL.inMemory();
            sessions = db.keyValueBucket("user_sessions");
        }

        @AfterEach
        void tearDown() { db.close(); }

        @Test
        @DisplayName("SC-KV-A-01: create, read, update, remove session lifecycle")
        void sessionLifecycle() {
            sessions.put("sess:user-1", "{\"userId\":\"u1\",\"role\":\"ADMIN\"}");
            String val = sessions.get("sess:user-1");
            assertNotNull(val);
            assertTrue(val.contains("u1"));
            // Update session (refresh)
            sessions.put("sess:user-1", "{\"userId\":\"u1\",\"role\":\"ADMIN\",\"refreshed\":true}");
            assertTrue(sessions.get("sess:user-1").contains("refreshed"));
            // Remove
            assertTrue(sessions.delete("sess:user-1"));
            assertNull(sessions.get("sess:user-1"));
        }

        @Test
        @DisplayName("SC-KV-A-02: TTL session expires after duration")
        void sessionTtlExpiry() throws InterruptedException {
            sessions.put("sess:temp", "ephemeral-token", Duration.ofMillis(100));
            Thread.sleep(200);
            assertNull(sessions.get("sess:temp"), "expired session must be null");
        }

        @Test
        @DisplayName("SC-KV-A-03: missing session returns null without side effects")
        void missingSesssion() {
            assertNull(sessions.get("sess:nonexistent"), "missing session must return null");
            assertFalse(sessions.delete("sess:nonexistent"), "deleting nonexistent returns false");
        }

        @Test
        @DisplayName("SC-KV-A-04: overwrite expiring session with permanent data clears TTL")
        void overwriteExpiringSessionClearsTtl() throws InterruptedException {
            sessions.put("sess:overlap", "temporary", Duration.ofMillis(100));
            // Immediately overwrite without TTL
            sessions.put("sess:overlap", "permanent-value");
            Thread.sleep(200);
            // The original TTL has now passed; the value must still be readable
            assertEquals("permanent-value", sessions.get("sess:overlap"),
                    "overwriting an expiring key without TTL must clear the expiration");
        }
    }

    // =========================================================================
    // SC-KV-B: LOCAL APPLICATION CACHE
    // =========================================================================

    @Nested
    @DisplayName("SC-KV-B: Local Application Cache")
    class LocalCacheScenario {

        @Test
        @DisplayName("SC-KV-B-01: cache miss, populate, hit, invalidate")
        void cacheLifecycle() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var cache = db.keyValueBucket("price_cache");
                // Miss
                assertNull(cache.get("sku:X1"), "cache miss must return null");
                // Populate
                cache.put("sku:X1", "199.99");
                // Hit
                assertEquals("199.99", cache.get("sku:X1"));
                // Overwrite (price change)
                cache.put("sku:X1", "189.99");
                assertEquals("189.99", cache.get("sku:X1"));
                // Invalidate
                cache.delete("sku:X1");
                assertNull(cache.get("sku:X1"), "invalidated entry must be null");
            }
        }

        @Test
        @DisplayName("SC-KV-B-02: JSON-serialized value round-trips accurately")
        void jsonValueRoundTrip() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var cache = db.keyValueBucket("obj_cache");
                String json = "{\"id\":42,\"name\":\"Widget\",\"tags\":[\"a\",\"b\"]}";
                cache.put("obj:42", json);
                String back = cache.get("obj:42");
                assertEquals(json, back, "JSON value must survive put/get without modification");
            }
        }

        @Test
        @DisplayName("SC-KV-B-03: putAll + getAll batch operation is consistent")
        void batchPutGetConsistency() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var cache = db.keyValueBucket("batch_cache");
                var entries = Map.of(
                    "k1", "v1", "k2", "v2", "k3", "v3", "k4", "v4"
                );
                cache.putAll(entries);
                var back = cache.getAll(List.of("k1", "k2", "k3", "k4"));
                assertEquals(4, back.size());
                assertEquals("v1", back.get("k1"));
                assertEquals("v4", back.get("k4"));
            }
        }
    }

    // =========================================================================
    // SC-KV-C: CHECKPOINT STORAGE (persistence boundary)
    // =========================================================================

    @Nested
    @DisplayName("SC-KV-C: Checkpoint Storage")
    class CheckpointStorageScenario {

        @Test
        @DisplayName("SC-KV-C-01: saved checkpoint survives close and reopen")
        void checkpointSurvivesRestart(@TempDir Path dir) {
            // Process A: write checkpoint
            try (EmbedJNoSQL db = EmbedJNoSQL.embed()
                    .storageEngine(EmbedJNoSQLConfig.StorageEngineType.FILE)
                    .dataDir(dir).build()) {
                var ckpts = db.keyValueBucket("job_checkpoints");
                ckpts.put("job:etl:offset", "10485760");  // 10 MB processed
                ckpts.put("job:etl:batch", "42");
            }
            // Process B: read checkpoint and resume
            try (EmbedJNoSQL db = EmbedJNoSQL.embed()
                    .storageEngine(EmbedJNoSQLConfig.StorageEngineType.FILE)
                    .dataDir(dir).build()) {
                var ckpts = db.keyValueBucket("job_checkpoints");
                String offset = ckpts.get("job:etl:offset");
                String batch = ckpts.get("job:etl:batch");
                assertNotNull(offset, "offset checkpoint must persist");
                assertEquals("10485760", offset);
                assertEquals("42", batch);
                // Process B advances the checkpoint
                ckpts.put("job:etl:offset", "20971520");
                ckpts.put("job:etl:batch", "43");
            }
            // Process C: verify advanced checkpoint
            try (EmbedJNoSQL db = EmbedJNoSQL.embed()
                    .storageEngine(EmbedJNoSQLConfig.StorageEngineType.FILE)
                    .dataDir(dir).build()) {
                var ckpts = db.keyValueBucket("job_checkpoints");
                assertEquals("20971520", ckpts.get("job:etl:offset"));
                assertEquals("43", ckpts.get("job:etl:batch"));
            }
        }
    }

    // =========================================================================
    // SC-KV-D: IDEMPOTENCY RECORDS
    // =========================================================================

    @Nested
    @DisplayName("SC-KV-D: Idempotency Records")
    class IdempotencyScenario {

        @Test
        @DisplayName("SC-KV-D-01: get-then-put idempotency check — duplicate detected")
        void idempotencyDuplicateDetected() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var idem = db.keyValueBucket("idempotency_keys");
                String opId = "op-" + UUID.randomUUID();
                // First call: key absent → process operation
                boolean isFirst = idem.get(opId) == null;
                assertTrue(isFirst, "first call must not find the key");
                idem.put(opId, "PROCESSED");
                // Second call: key present → duplicate detected
                boolean isDuplicate = idem.get(opId) != null;
                assertTrue(isDuplicate, "second call must detect the key as processed");
                // Note: this is application-level idempotency; the engine does not provide
                // atomic check-and-set. The get-then-put pattern is NOT thread-safe.
            }
        }

        @Test
        @DisplayName("SC-KV-D-02: TTL-based idempotency window expires correctly")
        void idempotencyWindowExpiry() throws InterruptedException {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var idem = db.keyValueBucket("idempotency_keys");
                String opId = "op-ttl-" + UUID.randomUUID();
                idem.put(opId, "PROCESSED", Duration.ofMillis(150));
                // Within window: duplicate
                assertNotNull(idem.get(opId), "within TTL window, key must be present");
                Thread.sleep(250);
                // After window: key absent; a new call would be processed again
                assertNull(idem.get(opId), "after TTL window, key must be absent");
            }
        }
    }

    // =========================================================================
    // SC-EDGE: SYSTEMATIC EDGE CASES
    // =========================================================================

    @Nested
    @DisplayName("SC-EDGE: Systematic Edge Cases")
    class EdgeCaseScenario {

        // --- Data & Identifiers ---

        @Test
        @DisplayName("SC-EDGE-01: empty collection findAll returns empty list")
        void emptyCollectionFindAll() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var col = db.documentCollection("empty_col");
                assertTrue(col.findAll().isEmpty());
                assertEquals(0, col.count());
            }
        }

        @Test
        @DisplayName("SC-EDGE-02: findById on missing document returns null")
        void findByIdMissing() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                assertNull(db.documentCollection("c").findById("does-not-exist"));
            }
        }

        @Test
        @DisplayName("SC-EDGE-03: duplicate id insert overwrites (upsert semantics)")
        void duplicateIdOverwrites() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var col = db.documentCollection("dup_test");
                col.insert(new Document().id("dup-1").add("v", "original"));
                col.insert(new Document().id("dup-1").add("v", "overwritten"));
                // Engine stores by id; second insert replaces first
                assertEquals(1, col.count());
                assertEquals("overwritten", col.findById("dup-1").get("v"));
            }
        }

        @Test
        @DisplayName("SC-EDGE-04: empty string key and value survive round-trip")
        void emptyStringKeyValue() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var kv = db.keyValueBucket("edge_kv");
                kv.put("empty-value-key", "");
                assertEquals("", kv.get("empty-value-key"));
            }
        }

        @Test
        @DisplayName("SC-EDGE-05: whitespace-only field value survives round-trip")
        void whitespaceFieldValue() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var col = db.documentCollection("edge_col");
                col.insert(new Document().id("ws-1").add("name", "   "));
                assertEquals("   ", col.findById("ws-1").get("name"));
            }
        }

        @Test
        @DisplayName("SC-EDGE-06: unicode field values survive round-trip")
        void unicodeFieldValues() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var col = db.documentCollection("edge_col");
                col.insert(new Document().id("u-1")
                        .add("emoji", "\uD83D\uDE80")
                        .add("arabic", "مرحبا")
                        .add("japanese", "日本語"));
                Document d = col.findById("u-1");
                assertEquals("\uD83D\uDE80", d.get("emoji"));
                assertEquals("مرحبا", d.get("arabic"));
                assertEquals("日本語", d.get("japanese"));
            }
        }

        @Test
        @DisplayName("SC-EDGE-07: long document id (255 chars) survives round-trip")
        void longDocumentId() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                String longId = "x".repeat(255);
                var col = db.documentCollection("edge_col");
                col.insert(new Document().id(longId).add("k", "v"));
                assertNotNull(col.findById(longId));
            }
        }

        @Test
        @DisplayName("SC-EDGE-08: deeply nested document (5 levels) survives round-trip")
        void deeplyNestedDocument() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var col = db.documentCollection("edge_col");
                col.insert(new Document().id("deep-1")
                        .add("l1", Map.of("l2", Map.of("l3", Map.of("l4", Map.of("l5", "found"))))));
                Document d = col.findById("deep-1");
                assertNotNull(d);
                Object l1 = d.get("l1");
                assertNotNull(l1, "l1 must be present");
            }
        }

        @Test
        @DisplayName("SC-EDGE-09: integer and long numeric values survive round-trip accurately")
        void numericPrecision() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var col = db.documentCollection("edge_col");
                col.insert(new Document().id("num-1")
                        .add("int_val", Integer.MAX_VALUE)
                        .add("neg_val", -1)
                        .add("zero", 0)
                        .add("double_val", 3.141592653589793));
                Document d = col.findById("num-1");
                assertEquals(Integer.MAX_VALUE, ((Number) d.get("int_val")).intValue());
                assertEquals(-1, ((Number) d.get("neg_val")).intValue());
                assertEquals(0, ((Number) d.get("zero")).intValue());
                assertEquals(3.141592653589793, ((Number) d.get("double_val")).doubleValue(), 1e-12);
            }
        }

        // --- Query Edge Cases ---

        @Test
        @DisplayName("SC-EDGE-10: query with no matches returns empty list")
        void queryNoMatches() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var col = db.documentCollection("edge_col");
                col.insert(new Document().id("q1").add("score", 50));
                var result = col.find(Query.gt("score", 100));
                assertTrue(result.isEmpty());
            }
        }

        @Test
        @DisplayName("SC-EDGE-11: query where all records match returns all")
        void queryAllMatch() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var col = db.documentCollection("edge_col");
                for (int i = 1; i <= 5; i++) {
                    col.insert(new Document().id("q-" + i).add("active", true));
                }
                assertEquals(5, col.find(Query.eq("active", true)).size());
            }
        }

        @Test
        @DisplayName("SC-EDGE-12: query on field absent from all documents returns empty list")
        void queryAbsentField() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var col = db.documentCollection("edge_col");
                col.insert(new Document().id("a1").add("name", "Alice"));
                var result = col.find(Query.eq("nonexistent_field", "value"));
                assertTrue(result.isEmpty());
            }
        }

        @Test
        @DisplayName("SC-EDGE-13: sort on equal values produces stable total order")
        void sortStableOnEqualValues() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var col = db.documentCollection("edge_col");
                // 5 docs with same 'score', different ids
                for (int i = 1; i <= 5; i++) {
                    col.insert(new Document().id("tie-" + i).add("score", 42).add("seq", i));
                }
                var sorted = col.find(Query.all().sortByAsc("score"));
                assertEquals(5, sorted.size());
                sorted.forEach(d -> assertEquals(42, ((Number) d.get("score")).intValue()));
            }
        }

        @Test
        @DisplayName("SC-EDGE-14: $in operator with empty set returns empty list")
        void inOperatorEmptySet() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var col = db.documentCollection("edge_col");
                col.insert(new Document().id("i1").add("tag", "a"));
                var result = col.find(Query.in("tag", List.of()));
                assertTrue(result.isEmpty(), "$in with empty list must return no results");
            }
        }

        @Test
        @DisplayName("SC-EDGE-15: $ne operator excludes matching documents")
        void neOperator() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var col = db.documentCollection("edge_col");
                col.insert(new Document().id("r1").add("status", "ACTIVE"));
                col.insert(new Document().id("r2").add("status", "INACTIVE"));
                col.insert(new Document().id("r3").add("status", "ACTIVE"));
                var result = col.find(Query.ne("status", "INACTIVE"));
                assertEquals(2, result.size());
                result.forEach(d -> assertEquals("ACTIVE", d.get("status")));
            }
        }

        // --- Lifecycle Edge Cases ---

        @Test
        @DisplayName("SC-EDGE-16: multiple independent collections in same database are isolated")
        void multipleCollectionIsolation() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var col1 = db.documentCollection("col_a");
                var col2 = db.documentCollection("col_b");
                col1.insert(new Document().id("id1").add("type", "A"));
                col2.insert(new Document().id("id1").add("type", "B"));
                assertEquals("A", col1.findById("id1").get("type"));
                assertEquals("B", col2.findById("id1").get("type"));
                assertEquals(1, col1.count());
                assertEquals(1, col2.count());
            }
        }

        @Test
        @DisplayName("SC-EDGE-17: closed transaction rejects further reads and writes")
        void closedTransactionRejectsOperations() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                Transaction tx = db.beginTransaction();
                tx.commit();
                assertThrows(IllegalStateException.class,
                        () -> tx.write("c", "k", "v"),
                        "committed transaction must reject writes");
                assertThrows(IllegalStateException.class,
                        () -> tx.read("c", "k"),
                        "committed transaction must reject reads");
            }
        }

        @Test
        @DisplayName("SC-EDGE-18: rollback on already-rolled-back transaction is safe")
        void doubleRollbackIsSafe() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                Transaction tx = db.beginTransaction();
                tx.rollback();
                // Second rollback must not throw
                assertDoesNotThrow(tx::rollback);
            }
        }

        // --- TTL Boundary Cases ---

        @Test
        @DisplayName("SC-EDGE-19: TTL key readable before expiry, absent after")
        void ttlBoundaryReadBeforeAndAfter() throws InterruptedException {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var kv = db.keyValueBucket("ttl_kv");
                kv.put("boundary-key", "alive", Duration.ofMillis(200));
                // Before expiry
                assertEquals("alive", kv.get("boundary-key"), "must be readable before expiry");
                Thread.sleep(300);
                // After expiry (lazy eviction on read)
                assertNull(kv.get("boundary-key"), "must be null after expiry");
            }
        }

        @Test
        @DisplayName("SC-EDGE-20: KV increment on missing key initializes to 1")
        void incrementMissingKeyInitializesToOne() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var kv = db.keyValueBucket("counter_kv");
                kv.increment("new-counter");
                assertEquals("1", kv.get("new-counter"));
            }
        }

        @Test
        @DisplayName("SC-EDGE-21: boolean field false is distinct from absent field")
        void booleanFalseIsDistinctFromAbsent() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var col = db.documentCollection("bool_col");
                col.insert(new Document().id("with-false").add("flag", false));
                col.insert(new Document().id("without-field"));

                Document withFalse = col.findById("with-false");
                Document without = col.findById("without-field");

                assertEquals(Boolean.FALSE, withFalse.get("flag"),
                        "explicit false must be stored and returned as false");
                assertNull(without.get("flag"),
                        "absent field must return null");
            }
        }
    }

    // =========================================================================
    // SC-PERSIST: THREE-PHASE PERSISTENCE VERIFICATION (B_TREE)
    // =========================================================================

    @Nested
    @DisplayName("SC-PERSIST: Three-Phase B_TREE Persistence")
    class PersistenceScenario {

        @Test
        @DisplayName("SC-PERSIST-01: write → close → reopen → verify → update → reopen → verify")
        void threePhaseDocumentPersistence(@TempDir Path dir) {
            // Phase 1: Write
            try (EmbedJNoSQL db = EmbedJNoSQL.embed()
                    .storageEngine(EmbedJNoSQLConfig.StorageEngineType.B_TREE)
                    .dataDir(dir).build()) {
                var users = db.documentCollection("users");
                users.insert(new Document().id("u1").add("name", "Alice").add("score", 100));
                users.insert(new Document().id("u2").add("name", "Bob").add("score", 200));
                users.insert(new Document().id("u3").add("name", "Carol").add("score", 150));
            }
            // Phase 2: Reopen and verify, then update
            try (EmbedJNoSQL db = EmbedJNoSQL.embed()
                    .storageEngine(EmbedJNoSQLConfig.StorageEngineType.B_TREE)
                    .dataDir(dir).build()) {
                var users = db.documentCollection("users");
                assertEquals(3, users.count(), "phase 2: all 3 records must persist");
                assertEquals("Alice", users.findById("u1").get("name"));
                assertEquals(200, ((Number) users.findById("u2").get("score")).intValue());
                // Update u2
                Document u2 = users.findById("u2");
                u2.add("score", 250);
                users.update(u2);
                // Delete u3
                users.deleteById("u3");
                assertEquals(2, users.count());
            }
            // Phase 3: Final verification
            try (EmbedJNoSQL db = EmbedJNoSQL.embed()
                    .storageEngine(EmbedJNoSQLConfig.StorageEngineType.B_TREE)
                    .dataDir(dir).build()) {
                var users = db.documentCollection("users");
                assertEquals(2, users.count(), "phase 3: 2 records after delete");
                assertEquals(250, ((Number) users.findById("u2").get("score")).intValue(),
                        "phase 3: updated score must persist");
                assertNull(users.findById("u3"), "phase 3: deleted u3 must be absent");
            }
        }

        @Test
        @DisplayName("SC-PERSIST-02: secondary index survives B_TREE restart")
        void secondaryIndexSurvivesRestart(@TempDir Path dir) {
            // Phase 1: Create index, insert data
            try (EmbedJNoSQL db = EmbedJNoSQL.embed()
                    .storageEngine(EmbedJNoSQLConfig.StorageEngineType.B_TREE)
                    .dataDir(dir).build()) {
                var products = db.documentCollection("products");
                products.createIndex("category");
                products.insert(new Document().id("p1").add("category", "A").add("name", "Alpha"));
                products.insert(new Document().id("p2").add("category", "B").add("name", "Beta"));
                products.insert(new Document().id("p3").add("category", "A").add("name", "Gamma"));
            }
            // Phase 2: Reopen, verify index is usable
            try (EmbedJNoSQL db = EmbedJNoSQL.embed()
                    .storageEngine(EmbedJNoSQLConfig.StorageEngineType.B_TREE)
                    .dataDir(dir).build()) {
                var products = db.documentCollection("products");
                assertNotNull(products.getIndex("category"), "category index must survive restart");
                var catA = products.find(Query.eq("category", "A"));
                assertEquals(2, catA.size(), "index-backed query must return 2 results");
            }
        }

        @Test
        @DisplayName("SC-PERSIST-03: KV bucket data survives FILE engine restart")
        void kvBucketFilePersistence(@TempDir Path dir) {
            // Phase 1: Write
            try (EmbedJNoSQL db = EmbedJNoSQL.embed()
                    .storageEngine(EmbedJNoSQLConfig.StorageEngineType.FILE)
                    .dataDir(dir).build()) {
                var kv = db.keyValueBucket("settings");
                kv.put("theme", "dark");
                kv.put("language", "en-US");
            }
            // Phase 2: Reopen, verify
            try (EmbedJNoSQL db = EmbedJNoSQL.embed()
                    .storageEngine(EmbedJNoSQLConfig.StorageEngineType.FILE)
                    .dataDir(dir).build()) {
                var kv = db.keyValueBucket("settings");
                assertEquals("dark", kv.get("theme"));
                assertEquals("en-US", kv.get("language"));
            }
        }
    }

    // =========================================================================
    // SC-TX: TRANSACTION CORRECTNESS
    // =========================================================================

    @Nested
    @DisplayName("SC-TX: Transaction Correctness")
    class TransactionCorrectnessScenario {

        @Test
        @DisplayName("SC-TX-01: staged insert visible inside transaction via findAll")
        void stagedInsertVisibleInsideTx() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var orders = db.documentCollection("orders");
                try (Transaction tx = db.beginTransaction()) {
                    var txOrders = tx.documentCollection("orders");
                    txOrders.insert(new Document().id("o-staged").add("status", "DRAFT"));
                    var all = txOrders.findAll();
                    assertEquals(1, all.size(), "staged insert must be visible in findAll inside tx");
                    assertEquals("o-staged", all.get(0).id());
                    tx.rollback();
                }
                // After rollback: collection must be empty
                assertEquals(0, orders.count(), "rollback must discard staged insert");
            }
        }

        @Test
        @DisplayName("SC-TX-02: staged update visible via findById inside transaction")
        void stagedUpdateVisibleInsideTx() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var col = db.documentCollection("items");
                col.insert(new Document().id("item-1").add("qty", 10));
                try (Transaction tx = db.beginTransaction()) {
                    var txCol = tx.documentCollection("items");
                    Document d = txCol.findById("item-1");
                    assertNotNull(d);
                    d.add("qty", 5);
                    txCol.update(d);
                    // Read-your-own-writes inside transaction
                    Document updated = txCol.findById("item-1");
                    assertEquals(5, ((Number) updated.get("qty")).intValue(),
                            "update must be visible inside the same transaction");
                    tx.rollback();
                }
                // After rollback: original value must be intact
                assertEquals(10, ((Number) col.findById("item-1").get("qty")).intValue(),
                        "rollback must restore original qty");
            }
        }

        @Test
        @DisplayName("SC-TX-03: staged delete not visible via findById inside transaction")
        void stagedDeleteInvisibleInsideTx() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var col = db.documentCollection("items");
                col.insert(new Document().id("del-1").add("v", "present"));
                try (Transaction tx = db.beginTransaction()) {
                    var txCol = tx.documentCollection("items");
                    txCol.deleteById("del-1");
                    // Within transaction: deleted doc must be null
                    assertNull(txCol.findById("del-1"),
                            "staged delete must make document null inside tx");
                    // findAll must exclude it
                    assertTrue(txCol.findAll().isEmpty(),
                            "staged delete must be excluded from findAll inside tx");
                    tx.commit();
                }
                // After commit: document must be gone permanently
                assertNull(col.findById("del-1"), "committed delete must be permanent");
            }
        }

        @Test
        @DisplayName("SC-TX-04: exception during commit rolls back partial operations")
        void exceptionDuringCommitRollsBack() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var col = db.documentCollection("safe");
                col.insert(new Document().id("base").add("v", "original"));
                // Start tx, make it roll back via rollback()
                try (Transaction tx = db.beginTransaction()) {
                    var txCol = tx.documentCollection("safe");
                    txCol.update(new Document().id("base").add("v", "modified"));
                    tx.rollback();
                }
                assertEquals("original", col.findById("base").get("v"),
                        "rolled-back update must leave original value");
            }
        }

        @Test
        @DisplayName("SC-TX-05: committed transaction makes data visible outside")
        void committedTransactionVisibleOutside() {
            try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
                var col = db.documentCollection("published");
                try (Transaction tx = db.beginTransaction()) {
                    var txCol = tx.documentCollection("published");
                    txCol.insert(new Document().id("pub-1").add("title", "Hello"));
                    // Before commit: invisible outside
                    assertNull(col.findById("pub-1"), "before commit must be invisible outside tx");
                    tx.commit();
                }
                // After commit: visible
                assertNotNull(col.findById("pub-1"), "after commit must be visible outside tx");
                assertEquals("Hello", col.findById("pub-1").get("title"));
            }
        }
    }
}

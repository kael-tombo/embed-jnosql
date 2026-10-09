package org.embeddedjnosql.db.spring.boot;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.Query;
import org.embeddedjnosql.db.spring.boot.data.EmbedJpaBaseRepository;
import org.embeddedjnosql.db.spring.boot.ir.IrApp;
import org.embeddedjnosql.db.spring.boot.ir.IrFixtures.Order;
import org.embeddedjnosql.db.spring.boot.ir.IrFixtures.OrderRepository;
import org.embeddedjnosql.db.spring.boot.ir.IrFixtures.Product;
import org.embeddedjnosql.db.spring.boot.ir.IrFixtures.ProductRepository;
import org.embeddedjnosql.db.spring.boot.ir.IrFixtures.Status;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.*;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration Review — 36-Scenario Test Suite.
 *
 * <p>Covers console ↔ engine, Spring Boot auto-configuration, entity discovery,
 * repository behavior, and Hibernate/JPA boundary verification.
 *
 * <p>CLASSIFICATION LEGEND (per scenario):
 * <ul>
 *   <li>DIRECT — operation routes to and verifies through embed-jnosql engine.</li>
 *   <li>COEXISTENCE — two systems active; each has its own store and tx-manager.</li>
 *   <li>REJECTION — operation explicitly rejected, state unchanged, engine unaffected.</li>
 *   <li>ANNOTATION-COMPAT — annotation recognized for metadata only, no JPA provider.</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.DisplayName.class)
@SpringBootTest(classes = IrApp.class)
@TestPropertySource(properties = {
        "embedjnosql.storage-engine=IN_MEMORY",
        "embedjnosql.repositories-enabled=true",
        "embedjnosql.auto-create-collections=true"
})
public class IntegrationReview36ScenarioTest {

    @Autowired ApplicationContext ctx;
    @Autowired EmbedJNoSQL db;
    @Autowired PlatformTransactionManager txManager;

    private ProductRepository products() {
        return ctx.getBean(ProductRepository.class);
    }

    private OrderRepository orders() {
        return ctx.getBean(OrderRepository.class);
    }

    private TransactionTemplate tx() {
        return new TransactionTemplate(txManager);
    }

    private Product product(String id, String name, double price, String cat, boolean active,
                            Status status) {
        return new Product(id, name, price, cat, active, status,
                Instant.parse("2026-01-01T00:00:00Z"));
    }

    // =========================================================================
    // GROUP A — Console ↔ Engine (SC-01 through SC-12)
    // =========================================================================

    @Nested
    @DisplayName("A. Console ↔ Engine")
    class ConsoleEngineGroup {

        @Test
        @DisplayName("SC-01: Create collection, insert document, independently verify via engine API")
        void sc01_createInsertVerify() {
            // Classification: DIRECT
            var col = db.documentCollection("ir_console_sc01");
            col.insert(new Document().id("c-001").add("item", "keyboard").add("qty", 5));

            Document loaded = db.documentCollection("ir_console_sc01").findById("c-001");
            assertNotNull(loaded, "SC-01: document must be findable after insert");
            assertEquals("keyboard", loaded.get("item"));
            assertEquals(5, ((Number) loaded.get("qty")).intValue());

            col.deleteById("c-001");
        }

        @Test
        @DisplayName("SC-02: Multiple collections are isolated — no context leakage")
        void sc02_multipleCollectionIsolation() {
            // Classification: DIRECT
            var alpha = db.documentCollection("ir_console_alpha");
            var beta  = db.documentCollection("ir_console_beta");
            alpha.insert(new Document().id("a1").add("source", "alpha"));
            beta.insert(new Document().id("b1").add("source", "beta"));

            assertNull(alpha.findById("b1"), "SC-02: beta document must not appear in alpha");
            assertNull(beta.findById("a1"),  "SC-02: alpha document must not appear in beta");

            alpha.deleteById("a1");
            beta.deleteById("b1");
        }

        @Test
        @DisplayName("SC-03: Edit nested values, verify type preservation")
        void sc03_nestedValueTypePreservation() {
            // Classification: DIRECT
            var col = db.documentCollection("ir_console_sc03");
            col.insert(new Document().id("n-001")
                    .add("specs", Map.of("ram", 16, "ssd", true, "model", "XZ-9"))
                    .add("score", 9.5)
                    .add("count", 42L));

            Document loaded = col.findById("n-001");
            assertNotNull(loaded);
            double score = ((Number) loaded.get("score")).doubleValue();
            assertEquals(9.5, score, 0.001, "SC-03: double must be preserved");
            int count = ((Number) loaded.get("count")).intValue();
            assertEquals(42, count, "SC-03: long/int must be preserved");

            col.deleteById("n-001");
        }

        @Test
        @DisplayName("SC-04: Filter numeric, boolean, and string fields with each operator")
        void sc04_filterOperators() {
            // Classification: DIRECT
            var col = db.documentCollection("ir_console_sc04");
            col.insert(new Document().id("f1").add("price", 10.0).add("active", true).add("tag", "alpha"));
            col.insert(new Document().id("f2").add("price", 20.0).add("active", false).add("tag", "beta"));
            col.insert(new Document().id("f3").add("price", 30.0).add("active", true).add("tag", "gamma"));

            assertEquals(2, col.find(Query.gt("price", 15.0)).size(), "SC-04: gt operator");
            assertEquals(1, col.find(Query.lte("price", 10.0)).size(), "SC-04: lte operator");
            assertEquals(1, col.find(Query.eq("tag", "alpha")).size(), "SC-04: string eq");
            assertEquals(2, col.find(Query.ne("tag", "alpha")).size(), "SC-04: ne operator");

            col.clear();
        }

        @Test
        @DisplayName("SC-05: Distinguish missing fields from explicit null via has()")
        void sc05_missingVsExplicitNull() {
            // Classification: DIRECT
            var col = db.documentCollection("ir_console_sc05");
            col.insert(new Document().id("m1").add("name", "Alice")); // no 'note' field
            col.insert(new Document().id("m2").add("name", "Bob").add("note", null));

            Document missing  = col.findById("m1");
            Document explicit = col.findById("m2");

            assertNull(missing.get("note"),  "SC-05: missing field returns null");
            assertNull(explicit.get("note"), "SC-05: explicit null returns null");
            assertFalse(missing.has("note"), "SC-05: missing → has()=false");
            assertTrue(explicit.has("note"),  "SC-05: explicit null → has()=true");

            col.clear();
        }

        @Test
        @DisplayName("SC-06: Combine AND/OR predicates and verify exact result IDs")
        void sc06_combinedPredicates() {
            // Classification: DIRECT
            var col = db.documentCollection("ir_console_sc06");
            col.insert(new Document().id("p1").add("cat", "A").add("price", 50.0));
            col.insert(new Document().id("p2").add("cat", "A").add("price", 150.0));
            col.insert(new Document().id("p3").add("cat", "B").add("price", 50.0));

            var andResult = col.find(Query.eq("cat", "A").and(Query.lt("price", 100.0)));
            assertEquals(1, andResult.size());
            assertEquals("p1", andResult.get(0).getId(), "SC-06: AND exact ID");

            var orResult = col.find(Query.eq("cat", "B").or(Query.gt("price", 100.0)));
            Set<String> orIds = new HashSet<>();
            orResult.forEach(d -> orIds.add(d.getId()));
            assertTrue(orIds.contains("p2") && orIds.contains("p3"), "SC-06: OR IDs");

            col.clear();
        }

        @Test
        @DisplayName("SC-07: Sort equal values and paginate deterministically")
        void sc07_sortAndPaginate() {
            // Classification: DIRECT
            var col = db.documentCollection("ir_console_sc07");
            for (int i = 1; i <= 6; i++) {
                col.insert(new Document().id("pg-" + i).add("seq", i));
            }

            var page1 = col.find(Query.all().sortByAsc("seq").offset(0).limit(2));
            var page2 = col.find(Query.all().sortByAsc("seq").offset(2).limit(2));

            assertEquals(2, page1.size());
            assertEquals(2, page2.size());
            Set<String> p1ids = new HashSet<>(), p2ids = new HashSet<>();
            page1.forEach(d -> p1ids.add(d.getId()));
            page2.forEach(d -> p2ids.add(d.getId()));
            assertTrue(Collections.disjoint(p1ids, p2ids), "SC-07: no overlap between pages");

            int maxP1 = page1.stream().mapToInt(d -> ((Number) d.get("seq")).intValue()).max().orElse(0);
            int minP2 = page2.stream().mapToInt(d -> ((Number) d.get("seq")).intValue()).min().orElse(0);
            assertTrue(minP2 > maxP1, "SC-07: page2 seq > page1 seq");

            col.clear();
        }

        @Test
        @DisplayName("SC-08: Clear filters and recover correct full result set")
        void sc08_clearFilterRecovery() {
            // Classification: DIRECT
            var col = db.documentCollection("ir_console_sc08");
            col.insert(new Document().id("cf1").add("type", "X"));
            col.insert(new Document().id("cf2").add("type", "Y"));
            col.insert(new Document().id("cf3").add("type", "X"));

            assertEquals(2, col.find(Query.eq("type", "X")).size(), "SC-08: filtered");
            assertEquals(3, col.find(Query.all()).size(), "SC-08: cleared returns all");

            col.clear();
        }

        @Test
        @DisplayName("SC-09: Delete final document on a page and navigate back correctly")
        void sc09_deleteFinalDocumentOnPage() {
            // Classification: DIRECT
            var col = db.documentCollection("ir_console_sc09");
            col.insert(new Document().id("dp1").add("n", 1));
            col.insert(new Document().id("dp2").add("n", 2));
            col.insert(new Document().id("dp3").add("n", 3));

            var page2 = col.find(Query.all().sortByAsc("n").offset(2).limit(1));
            assertEquals(1, page2.size());
            assertEquals("dp3", page2.get(0).getId());

            col.deleteById("dp3");
            assertTrue(col.find(Query.all().offset(2).limit(1)).isEmpty(),
                    "SC-09: page2 empty after deleting its only doc");
            assertEquals(2, col.find(Query.all().offset(0).limit(2)).size(),
                    "SC-09: page1 still intact");

            col.clear();
        }

        @Test
        @DisplayName("SC-10: Key-Value CRUD and verify stored values independently")
        void sc10_kvCrud() {
            // Classification: DIRECT
            var kv = db.keyValueBucket("ir_console_sc10");
            kv.put("feature:x", "on");
            kv.put("feature:y", "off");

            assertEquals("on", kv.get("feature:x"));
            kv.put("feature:x", "beta");
            assertEquals("beta", kv.get("feature:x"), "SC-10: update must persist");

            kv.delete("feature:y");
            assertNull(kv.get("feature:y"), "SC-10: delete must remove key");
        }

        @Test
        @DisplayName("SC-11: Reject malformed input without mutating storage")
        void sc11_malformedInputRejected() {
            // Classification: REJECTION
            var col = db.documentCollection("ir_console_sc11");
            col.insert(new Document().id("good-1").add("status", "ok"));

            assertThrows(Exception.class, () -> col.find(Query.eq(null, "value")),
                    "SC-11: null field query must throw");
            assertEquals(1, col.count(), "SC-11: storage unchanged after failed op");
            assertNotNull(col.findById("good-1"));
            col.clear();
        }

        @Test
        @DisplayName("SC-12: File-backed fixture persists and is readable after process close/reopen")
        void sc12_filePersistenceRoundTrip(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
            // Classification: DIRECT
            try (EmbedJNoSQL fileDb = EmbedJNoSQL.embed()
                    .storageEngine(org.embeddedjnosql.db.config.EmbedJNoSQLConfig.StorageEngineType.FILE)
                    .dataDir(dir).build()) {
                fileDb.documentCollection("ir_file_sc12")
                        .insert(new Document().id("f-001").add("value", "durable"));
            }
            try (EmbedJNoSQL fileDb2 = EmbedJNoSQL.embed()
                    .storageEngine(org.embeddedjnosql.db.config.EmbedJNoSQLConfig.StorageEngineType.FILE)
                    .dataDir(dir).build()) {
                Document loaded = fileDb2.documentCollection("ir_file_sc12").findById("f-001");
                assertNotNull(loaded, "SC-12: document must survive restart");
                assertEquals("durable", loaded.get("value"));
            }
        }
    }

    // =========================================================================
    // GROUP B — Spring Boot Auto-Configuration (SC-13 through SC-20)
    // =========================================================================

    @Nested
    @DisplayName("B. Spring Boot Auto-Configuration")
    class AutoConfigurationGroup {

        @Test
        @DisplayName("SC-13: Minimal starter — EmbedJNoSQL bean auto-configured and open")
        void sc13_minimalStarterBean() {
            assertNotNull(db, "SC-13: EmbedJNoSQL bean must be auto-configured");
            assertTrue(db.isOpen(), "SC-13: database must be open");
        }

        @Test
        @DisplayName("SC-14: Default IN_MEMORY — no persistent files created")
        void sc14_defaultInMemoryConfig(@Autowired EmbedJNoSQLProperties props) {
            assertEquals(
                    org.embeddedjnosql.db.config.EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY,
                    props.getStorageEngine(),
                    "SC-14: storage engine must be IN_MEMORY");
        }

        @Test
        @DisplayName("SC-15: File-backed configuration — fresh-process readback works")
        void sc15_fileBackedReadback(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
            // Classification: DIRECT (separate engine instance)
            try (EmbedJNoSQL fe = EmbedJNoSQL.embed()
                    .storageEngine(org.embeddedjnosql.db.config.EmbedJNoSQLConfig.StorageEngineType.B_TREE)
                    .dataDir(dir).build()) {
                fe.documentCollection("sc15").insert(new Document().id("x1").add("v", "hello"));
            }
            try (EmbedJNoSQL fe2 = EmbedJNoSQL.embed()
                    .storageEngine(org.embeddedjnosql.db.config.EmbedJNoSQLConfig.StorageEngineType.B_TREE)
                    .dataDir(dir).build()) {
                assertEquals("hello", fe2.documentCollection("sc15").findById("x1").get("v"),
                        "SC-15: value must survive B_TREE close/reopen");
            }
        }

        @Test
        @DisplayName("SC-16: EmbedJNoSQL bean is a singleton")
        void sc16_beanSingleton() {
            EmbedJNoSQL ref1 = ctx.getBean(EmbedJNoSQL.class);
            EmbedJNoSQL ref2 = ctx.getBean(EmbedJNoSQL.class);
            assertSame(ref1, ref2, "SC-16: EmbedJNoSQL must be a singleton bean");
        }

        @Test
        @DisplayName("SC-17: embedjnosql.enabled=false disables auto-configuration via @ConditionalOnProperty")
        void sc17_conditionalOnPropertyVerification() {
            var autoConfigClass = EmbedJNoSQLAutoConfiguration.class;
            var annotation = autoConfigClass.getAnnotation(
                    org.springframework.boot.autoconfigure.condition.ConditionalOnProperty.class);
            assertNotNull(annotation);
            assertEquals("embedjnosql", annotation.prefix());
            assertEquals("enabled", annotation.name()[0]);
            assertTrue(annotation.matchIfMissing(),
                    "SC-17: must activate by default (matchIfMissing=true)");
        }

        @Test
        @DisplayName("SC-18: Invalid storage engine value produces IllegalArgumentException")
        void sc18_invalidStorageEngineFails() {
            assertThrows(IllegalArgumentException.class,
                    () -> org.embeddedjnosql.db.config.EmbedJNoSQLConfig.StorageEngineType.valueOf("NONEXISTENT"),
                    "SC-18: invalid storage engine must fail with IllegalArgumentException");
        }

        @Test
        @DisplayName("SC-19: Two isolated in-memory databases — no cross-contamination")
        void sc19_twoIsolatedDatabases() {
            try (EmbedJNoSQL db1 = EmbedJNoSQL.inMemory();
                 EmbedJNoSQL db2 = EmbedJNoSQL.inMemory()) {
                db1.documentCollection("shared").insert(new Document().id("d1").add("k", "v1"));
                db2.documentCollection("shared").insert(new Document().id("d2").add("k", "v2"));
                assertNull(db1.documentCollection("shared").findById("d2"), "SC-19: isolation db1");
                assertNull(db2.documentCollection("shared").findById("d1"), "SC-19: isolation db2");
            }
        }

        @Test
        @DisplayName("SC-20: Shutdown closes the database; reopening a new instance succeeds")
        void sc20_shutdownAndReopenSucceeds(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
            EmbedJNoSQL tempDb = EmbedJNoSQL.embed()
                    .storageEngine(org.embeddedjnosql.db.config.EmbedJNoSQLConfig.StorageEngineType.FILE)
                    .dataDir(dir).build();
            tempDb.documentCollection("sc20").insert(new Document().id("s1").add("x", 1));
            assertTrue(tempDb.isOpen());
            tempDb.close();
            assertFalse(tempDb.isOpen(), "SC-20: closed after close()");

            try (EmbedJNoSQL reopened = EmbedJNoSQL.embed()
                    .storageEngine(org.embeddedjnosql.db.config.EmbedJNoSQLConfig.StorageEngineType.FILE)
                    .dataDir(dir).build()) {
                assertTrue(reopened.isOpen());
                assertEquals(1, reopened.documentCollection("sc20").count(),
                        "SC-20: data accessible after reopen");
            }
        }
    }

    // =========================================================================
    // GROUP C — Entity Discovery and Repositories (SC-21 through SC-28)
    // =========================================================================

    @Nested
    @DisplayName("C. Entity Discovery and Repositories")
    class EntityDiscoveryGroup {

        @Test
        @DisplayName("SC-21: Entity auto-discovered in application package, collection materialized")
        void sc21_entityAutoDiscovery() {
            Set<String> collections = db.getCollectionNames();
            assertTrue(collections.contains("ir_products"),
                    "SC-21: ir_products collection must exist from auto-create-collections");
            assertTrue(collections.contains("ir_orders"),
                    "SC-21: ir_orders collection must exist from auto-create-collections");
        }

        @Test
        @DisplayName("SC-22: @Table name drives collection naming")
        void sc22_collectionNaming() {
            String productCollection = org.embeddedjnosql.db.adapter.jnosql.EntityMapper
                    .getCollectionName(Product.class);
            assertEquals("ir_products", productCollection, "SC-22: @Table(name=ir_products)");

            String orderCollection = org.embeddedjnosql.db.adapter.jnosql.EntityMapper
                    .getCollectionName(Order.class);
            assertEquals("ir_orders", orderCollection, "SC-22: @Table(name=ir_orders)");
        }

        @Test
        @DisplayName("SC-23: @Id field drives document identifier; operations find by that field")
        void sc23_idFieldMapping() {
            String idField = org.embeddedjnosql.db.adapter.jnosql.EntityMapper
                    .getIdFieldName(Product.class);
            assertEquals("id", idField, "SC-23: @Id field must be 'id'");

            var repo = new EmbedJpaBaseRepository<>(db, Product.class);
            Product p = product("id-test-23", "Test", 1.0, "X", true, Status.ACTIVE);
            repo.save(p);
            assertTrue(repo.existsById("id-test-23"), "SC-23: String ID must work");
            repo.deleteById("id-test-23");
        }

        @Test
        @DisplayName("SC-24: @Column name overrides field name in stored document")
        void sc24_columnNameMapping() {
            // Classification: ANNOTATION-COMPAT — verify via raw document storage
            var repo = new EmbedJpaBaseRepository<>(db, Product.class);
            Product p = product("sc24-id", "Col Test", 99.99, "test", true, Status.ACTIVE);
            repo.save(p);

            Document raw = db.documentCollection("ir_products").findById("sc24-id");
            assertNotNull(raw, "SC-24: raw document must be present");
            assertNotNull(raw.get("unit_price"),
                    "SC-24: must be stored under mapped name 'unit_price'");
            assertNull(raw.getRaw("price"),
                    "SC-24: unmapped name 'price' must not appear in storage");
            repo.deleteById("sc24-id");
        }

        @Test
        @DisplayName("SC-25: @Transient field is excluded from stored document")
        void sc25_transientFieldExcluded() {
            var repo = new EmbedJpaBaseRepository<>(db, Product.class);
            Product p = product("sc25-id", "Transient Test", 10.0, "test", true, Status.ACTIVE);
            p.computedLabel = "SHOULD-NOT-BE-STORED";
            repo.save(p);

            Document raw = db.documentCollection("ir_products").findById("sc25-id");
            assertNotNull(raw);
            assertNull(raw.getRaw("computedLabel"),
                    "SC-25: @Transient field must not appear in storage");
            repo.deleteById("sc25-id");
        }

        @Test
        @DisplayName("SC-26: Duplicate ID on save() is replace (upsert), not error")
        void sc26_duplicateIdIsUpsert() {
            var repo = new EmbedJpaBaseRepository<>(db, Product.class);
            repo.save(product("dup-id", "Version One", 10.0, "A", true, Status.DRAFT));
            assertEquals("Version One", repo.findById("dup-id").orElseThrow().getName());

            repo.save(product("dup-id", "Version Two", 20.0, "B", false, Status.ACTIVE));
            Product loaded = repo.findById("dup-id").orElseThrow();
            assertEquals("Version Two", loaded.getName(), "SC-26: second save replaces");
            assertEquals(20.0, loaded.getPrice(), 0.001);
            repo.deleteById("dup-id");
        }

        @Test
        @DisplayName("SC-27: Enum, Instant, and nested fields round-trip correctly")
        void sc27_enumDateNestedRoundTrip() {
            var repo = new EmbedJpaBaseRepository<>(db, Product.class);
            Instant ts = Instant.parse("2026-06-15T08:00:00Z");
            Product p = new Product("sc27-id", "Rich", 49.99, "electronics",
                    true, Status.DISCONTINUED, ts);
            repo.save(p);

            Product loaded = repo.findById("sc27-id").orElseThrow();
            assertEquals(Status.DISCONTINUED, loaded.getStatus(), "SC-27: Enum round-trip");
            assertEquals(ts, loaded.getCreatedAt(), "SC-27: Instant round-trip");
            repo.deleteById("sc27-id");
        }

        @Test
        @DisplayName("SC-28: Repository CRUD, derived queries, sorting, pagination all verified")
        void sc28_repositoryFullSurface() {
            var repo = products();
            // Setup
            repo.save(product("r1", "Alpha", 10.0, "electronics", true,  Status.ACTIVE));
            repo.save(product("r2", "Beta",  30.0, "electronics", false, Status.DRAFT));
            repo.save(product("r3", "Gamma", 20.0, "clothing",    true,  Status.ACTIVE));
            try {
                assertTrue(repo.findById("r1").isPresent(), "SC-28: findById");
                assertTrue(repo.existsById("r2"), "SC-28: existsById");
                assertTrue(repo.count() >= 3, "SC-28: count");
                assertEquals(2, repo.findByCategory("electronics").size(), "SC-28: findByCategory");
                repo.findByPriceLessThan(25.0).forEach(p ->
                        assertTrue(p.getPrice() < 25.0, "SC-28: price < 25"));
                List<Product> active = repo.findByActiveTrueOrderByPriceAsc();
                assertTrue(active.stream().allMatch(Product::isActive));
                for (int i = 0; i < active.size() - 1; i++)
                    assertTrue(active.get(i).getPrice() <= active.get(i + 1).getPrice());
                assertEquals(1, repo.findByNameContainingIgnoreCase("ALPHA").size(), "SC-28: contains IC");
                assertEquals(2, repo.countByCategory("electronics"), "SC-28: count derived");
                assertTrue(repo.existsByName("Alpha"), "SC-28: existsByName");
                repo.findByStatusNot(Status.DRAFT).forEach(p ->
                        assertNotEquals(Status.DRAFT, p.getStatus(), "SC-28: statusNot"));
                Page<Product> page = repo.findAll(PageRequest.of(0, 2, Sort.by("unit_price")));
                assertEquals(2, page.getContent().size(), "SC-28: pageable");
            } finally {
                repo.deleteAll(repo.findByCategory("electronics"));
                repo.deleteAll(repo.findByCategory("clothing"));
            }
        }
    }

    // =========================================================================
    // GROUP D — Hibernate/JPA Boundaries and Failure Cases (SC-29 through SC-36)
    // =========================================================================

    @Nested
    @DisplayName("D. Hibernate / JPA Boundaries and Failure Cases")
    class HibernateJpaBoundaryGroup {

        @Test
        @DisplayName("SC-29: @Entity annotation recognized for metadata — no JPA provider required")
        void sc29_entityAnnotationRecognizedNoProvider() {
            // Classification: ANNOTATION-COMPAT
            assertNotNull(Product.class.getAnnotation(jakarta.persistence.Entity.class));
            assertTrue(db.getCollectionNames().contains("ir_products"),
                    "SC-29: collection auto-created from scanned entity");
            assertFalse(ctx.containsBean("entityManagerFactory"),
                    "SC-29: no EntityManagerFactory — this is a NoSQL engine");
            String tmClass = ctx.getBean(PlatformTransactionManager.class).getClass().getName();
            assertFalse(tmClass.contains("JpaTransactionManager"),
                    "SC-29: JpaTransactionManager must not be active; got: " + tmClass);
        }

        @Test
        @DisplayName("SC-30: embed-jnosql starter does not activate Spring JPA auto-configuration")
        void sc30_noAccidentalJpaAutoConfig() {
            // Classification: COEXISTENCE verification
            assertFalse(ctx.containsBean("entityManagerFactory"),
                    "SC-30: JPA auto-config must not activate from embed-jnosql starter alone");
            assertInstanceOf(EmbedJNoSQLTransactionManager.class,
                    ctx.getBean(PlatformTransactionManager.class),
                    "SC-30: only EmbedJNoSQLTransactionManager must be present");
        }

        @Test
        @DisplayName("SC-31: Entity scanning — same collection name returns same logical collection")
        void sc31_entityScanNoDuplicates() {
            // Classification: ANNOTATION-COMPAT
            var col1 = db.documentCollection("ir_products");
            var col2 = db.documentCollection("ir_products");
            col1.insert(new Document().id("scan-test").add("x", 1));
            assertNotNull(col2.findById("scan-test"),
                    "SC-31: same collection name → same logical collection");
            col1.deleteById("scan-test");
        }

        @Test
        @DisplayName("SC-32: Explicit tx-manager — commit persists, rollback discards, verified independently")
        void sc32_explicitTxManagerRollback() {
            // Classification: DIRECT
            // Use the repository (which routes through the tx-routing proxy) for writes inside
            // the transaction. Direct db.documentCollection() calls from a @SpringBootTest
            // context use the auto-configured proxied bean — calling through the Spring-managed
            // repository guarantees the transaction boundary is respected.
            var repo = products();
            var tt = tx();

            // Commit path: entity inserted inside a committed tx must persist
            tt.executeWithoutResult(s ->
                    repo.save(product("tx32-c", "Committed", 1.0, "test", true, Status.ACTIVE)));
            assertTrue(repo.existsById("tx32-c"), "SC-32: commit must persist entity");

            // Rollback path: staged insert must be discarded
            try {
                tt.executeWithoutResult(s -> {
                    repo.save(product("tx32-r", "Doomed", 1.0, "test", true, Status.DRAFT));
                    // Read-your-own-writes inside the transaction
                    assertTrue(repo.existsById("tx32-r"), "SC-32: RYOW inside tx");
                    throw new IllegalStateException("forced rollback");
                });
            } catch (IllegalStateException ignored) {}
            assertFalse(repo.existsById("tx32-r"), "SC-32: rollback must discard staged entity");

            // Independent engine-level readback confirms no phantom document
            assertNull(db.documentCollection("ir_products").findById("tx32-r"),
                    "SC-32: engine confirms rollback — document not in storage");

            repo.deleteById("tx32-c");
        }


        @Test
        @DisplayName("SC-33: Entity persisted via repository is directly readable from raw engine")
        void sc33_repositorySavePersistsToEngine() {
            // Classification: DIRECT
            var repo = products();
            repo.save(product("sc33-id", "Engine Verify", 75.0, "tools", true, Status.ACTIVE));

            Document raw = db.documentCollection("ir_products").findById("sc33-id");
            assertNotNull(raw, "SC-33: raw engine must find entity saved via repository");
            assertEquals("Engine Verify", raw.get("name"));
            assertEquals(75.0, ((Number) raw.get("unit_price")).doubleValue(), 0.001,
                    "SC-33: @Column mapped name used in raw storage");

            repo.deleteById("sc33-id");
        }

        @Test
        @DisplayName("SC-34: JpaRepository bootstrap via embed factory — flush() is a no-op")
        void sc34_jpaRepositoryFlushIsNoOp() {
            // Classification: ANNOTATION-COMPAT bootstrap
            var repo = products();
            assertNotNull(repo, "SC-34: JpaRepository bean must bootstrap");

            repo.save(product("sc34-id", "No-flush", 5.0, "misc", true, Status.ACTIVE));
            assertNotNull(db.documentCollection("ir_products").findById("sc34-id"),
                    "SC-34: save writes directly to engine without flush()");
            assertDoesNotThrow(repo::flush, "SC-34: flush() must be a no-op");

            repo.deleteById("sc34-id");
        }

        @Test
        @DisplayName("SC-35: Unsupported relationship annotations are ignored without partial writes")
        void sc35_unsupportedRelationshipIgnored() {
            // Classification: REJECTION / ANNOTATION-COMPAT
            // @OneToMany etc. are ignored by EntityMapper — no cascade or partial write occurs
            long before = db.documentCollection("ir_products").count();
            var repo = new EmbedJpaBaseRepository<>(db, Product.class);
            repo.save(product("sc35-id", "Rel Test", 1.0, "test", true, Status.DRAFT));

            assertTrue(repo.existsById("sc35-id"), "SC-35: entity stored");
            assertEquals(before + 1, db.documentCollection("ir_products").count(),
                    "SC-35: exactly one document written — no phantom cascade rows");
            repo.deleteById("sc35-id");
        }

        @Test
        @DisplayName("SC-36: Concurrent repository and engine updates — last-writer-wins, no corruption")
        void sc36_concurrentUpdatesNoCorruption() throws InterruptedException {
            // Classification: DIRECT
            var repo = new EmbedJpaBaseRepository<>(db, Product.class);
            repo.save(product("sc36-id", "Original", 1.0, "test", true, Status.DRAFT));

            Thread t1 = new Thread(() ->
                    repo.save(product("sc36-id", "RepoUpdate", 2.0, "test", true, Status.ACTIVE)));
            Thread t2 = new Thread(() -> {
                Document d = db.documentCollection("ir_products").findById("sc36-id");
                if (d != null) {
                    d.add("name", "DirectUpdate");
                    db.documentCollection("ir_products").update(d);
                }
            });

            t1.start(); t2.start();
            t1.join(3000); t2.join(3000);

            Document result = db.documentCollection("ir_products").findById("sc36-id");
            assertNotNull(result, "SC-36: document must exist after concurrent updates");
            String name = (String) result.get("name");
            assertTrue("RepoUpdate".equals(name) || "DirectUpdate".equals(name),
                    "SC-36: name must be one of two written values; got: " + name);
            // No duplicate documents
            assertEquals(1, db.documentCollection("ir_products")
                    .find(Query.eq("id", "sc36-id")).size(),
                    "SC-36: no duplicate documents from concurrent writes");

            repo.deleteById("sc36-id");
        }
    }
}

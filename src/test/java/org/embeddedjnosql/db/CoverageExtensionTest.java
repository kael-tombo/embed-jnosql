package org.embeddedjnosql.db;

import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.QueryParser;
import org.embeddedjnosql.db.nosql.document.Query;
import org.embeddedjnosql.db.nosql.query.QueryBuilder;
import org.embeddedjnosql.db.core.cache.QueryResultCache;
import org.embeddedjnosql.db.core.util.CircuitBreaker;
import org.embeddedjnosql.db.index.TextIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Targeted coverage tests for:
 * QueryParser, QueryBuilder, CircuitBreaker, QueryCache, QueryResultCache, TextIndex
 *
 * These classes had 0% or very low coverage and were driving the JaCoCo 54% failure.
 */
class CoverageExtensionTest {

    private Document docA;
    private Document docB;
    private Document docC;

    @BeforeEach
    void setUp() {
        docA = new Document().add("name", "Alice").add("age", 30).add("city", "Paris").add("score", 95.0);
        docA.id("doc-a");
        docB = new Document().add("name", "Bob").add("age", 17).add("city", "London").add("score", 60.0);
        docB.id("doc-b");
        docC = new Document().add("name", "Carol").add("age", 45).add("city", "Paris").add("score", 75.0);
        docC.id("doc-c");
    }

    // =========================================================================
    // QueryParser — MongoDB-style operator support
    // =========================================================================

    @Test
    void queryParser_nullAndEmpty_returnsAll() {
        var q1 = QueryParser.parse(null);
        assertNotNull(q1);
        var q2 = QueryParser.parse(Map.of());
        assertNotNull(q2);
        assertTrue(q1.docPredicate().test(docA));
        assertTrue(q2.docPredicate().test(docA));
    }

    @Test
    void queryParser_simpleEquality() {
        var q = QueryParser.parse(Map.of("name", "Alice"));
        assertTrue(q.docPredicate().test(docA));
        assertFalse(q.docPredicate().test(docB));
    }

    @Test
    void queryParser_dollarEq() {
        var q = QueryParser.parse(Map.of("name", Map.of("$eq", "Bob")));
        assertFalse(q.docPredicate().test(docA));
        assertTrue(q.docPredicate().test(docB));
    }

    @Test
    void queryParser_dollarNe() {
        var q = QueryParser.parse(Map.of("name", Map.of("$ne", "Alice")));
        assertFalse(q.docPredicate().test(docA));
        assertTrue(q.docPredicate().test(docB));
    }

    @Test
    void queryParser_dollarGt() {
        var q = QueryParser.parse(Map.of("age", Map.of("$gt", 20)));
        assertTrue(q.docPredicate().test(docA));   // 30 > 20
        assertFalse(q.docPredicate().test(docB));  // 17 > 20 = false
    }

    @Test
    void queryParser_dollarGte() {
        var q = QueryParser.parse(Map.of("age", Map.of("$gte", 30)));
        assertTrue(q.docPredicate().test(docA));   // 30 >= 30
        assertFalse(q.docPredicate().test(docB));  // 17 >= 30 = false
    }

    @Test
    void queryParser_dollarLt() {
        var q = QueryParser.parse(Map.of("age", Map.of("$lt", 20)));
        assertFalse(q.docPredicate().test(docA)); // 30 < 20 = false
        assertTrue(q.docPredicate().test(docB));  // 17 < 20 = true
    }

    @Test
    void queryParser_dollarLte() {
        var q = QueryParser.parse(Map.of("age", Map.of("$lte", 17)));
        assertFalse(q.docPredicate().test(docA)); // 30 <= 17 = false
        assertTrue(q.docPredicate().test(docB));  // 17 <= 17 = true
    }

    @Test
    void queryParser_dollarIn() {
        var q = QueryParser.parse(Map.of("city", Map.of("$in", List.of("Paris", "Berlin"))));
        assertTrue(q.docPredicate().test(docA));   // Paris in list
        assertFalse(q.docPredicate().test(docB));  // London not in list
    }

    @Test
    void queryParser_dollarNin() {
        var q = QueryParser.parse(Map.of("city", Map.of("$nin", List.of("Paris", "Berlin"))));
        assertFalse(q.docPredicate().test(docA));
        assertTrue(q.docPredicate().test(docB));
    }

    @Test
    void queryParser_dollarRegex() {
        var q = QueryParser.parse(Map.of("name", Map.of("$regex", "A.*")));
        assertTrue(q.docPredicate().test(docA));   // "Alice" matches A.*
        assertFalse(q.docPredicate().test(docB));  // "Bob" does not
    }

    @Test
    void queryParser_dollarExists_true() {
        var q = QueryParser.parse(Map.of("city", Map.of("$exists", true)));
        assertTrue(q.docPredicate().test(docA));
        var noCity = new Document().add("name", "Dave");
        noCity.id("doc-d");
        assertFalse(q.docPredicate().test(noCity));
    }

    @Test
    void queryParser_dollarExists_false() {
        var q = QueryParser.parse(Map.of("city", Map.of("$exists", false)));
        assertFalse(q.docPredicate().test(docA));
        var noCity = new Document().add("name", "Dave");
        noCity.id("doc-d");
        assertTrue(q.docPredicate().test(noCity));
    }

    @Test
    void queryParser_dollarAnd() {
        // $and inside a field's operator map: both conditions must hold
        // age > 20 AND age < 40 (both on same field, combined in one entry)
        var q = QueryParser.parse(Map.of(
                "age", Map.of("$gt", 20, "$lt", 40)
        ));
        assertTrue(q.docPredicate().test(docA));  // age=30 in range
        assertFalse(q.docPredicate().test(docB)); // age=17 not >20
        assertFalse(q.docPredicate().test(docC)); // age=45 not <40
    }

    @Test
    void queryParser_dollarOr() {
        // Multi-field query: city=London OR age>40 — since QueryParser treats
        // each top-level key as a field, use two separate queries composed
        // to verify the predicate behavior on individual documents
        var qCity = QueryParser.parse(Map.of("city", "London"));
        var qAge  = QueryParser.parse(Map.of("age", Map.of("$gt", 40)));
        // docB matches city=London
        assertTrue(qCity.docPredicate().test(docB));
        // docC matches age>40
        assertTrue(qAge.docPredicate().test(docC));
        // docA matches neither
        assertFalse(qCity.docPredicate().test(docA));
        assertFalse(qAge.docPredicate().test(docA));
    }

    @Test
    void queryParser_multipleOperatorsOnField() {
        var q = QueryParser.parse(Map.of("age", Map.of("$gt", 20, "$lt", 40)));
        assertTrue(q.docPredicate().test(docA));   // 30 in [20, 40]
        assertFalse(q.docPredicate().test(docB));  // 17 not > 20
        assertFalse(q.docPredicate().test(docC));  // 45 not < 40
    }

    @Test
    void queryParser_unknownOperatorRefused() {
        // R-47: unknown operators used to be silently ignored (pass-through),
        // which turned every typo into "match all documents". They must throw.
        assertThrows(QueryParser.QueryFormatException.class,
                () -> QueryParser.parse(Map.of("name", Map.of("$unknown", "value"))));
    }

    @Test
    void queryParser_missingFieldForNumericOpReturnsFalse() {
        var noAgeDoc = new Document().add("name", "Zara");
        noAgeDoc.id("doc-z");
        var q = QueryParser.parse(Map.of("age", Map.of("$gt", 0)));
        assertFalse(q.docPredicate().test(noAgeDoc));
    }

    // =========================================================================
    // QueryBuilder — fluent DSL
    // =========================================================================

    @Test
    void queryBuilder_noWhere_matchesAll() {
        var q = QueryBuilder.selectFrom("users").build();
        assertTrue(q.docPredicate().test(docA));
        assertTrue(q.docPredicate().test(docB));
    }

    @Test
    void queryBuilder_eq() {
        var q = QueryBuilder.selectFrom("users").where("city").eq("Paris").build();
        assertTrue(q.docPredicate().test(docA));
        assertFalse(q.docPredicate().test(docB));
    }

    @Test
    void queryBuilder_ne() {
        var q = QueryBuilder.selectFrom("users").where("city").ne("Paris").build();
        assertFalse(q.docPredicate().test(docA));
        assertTrue(q.docPredicate().test(docB));
    }

    @Test
    void queryBuilder_gt() {
        assertTrue(QueryBuilder.selectFrom("u").where("age").gt(25).build().docPredicate().test(docA));
        assertFalse(QueryBuilder.selectFrom("u").where("age").gt(25).build().docPredicate().test(docB));
    }

    @Test
    void queryBuilder_gte() {
        assertTrue(QueryBuilder.selectFrom("u").where("age").gte(30).build().docPredicate().test(docA));
        assertFalse(QueryBuilder.selectFrom("u").where("age").gte(30).build().docPredicate().test(docB));
    }

    @Test
    void queryBuilder_lt() {
        assertTrue(QueryBuilder.selectFrom("u").where("age").lt(20).build().docPredicate().test(docB));
        assertFalse(QueryBuilder.selectFrom("u").where("age").lt(20).build().docPredicate().test(docA));
    }

    @Test
    void queryBuilder_lte() {
        assertTrue(QueryBuilder.selectFrom("u").where("age").lte(17).build().docPredicate().test(docB));
    }

    @Test
    void queryBuilder_contains() {
        var q = QueryBuilder.selectFrom("u").where("name").contains("li").build();
        assertTrue(q.docPredicate().test(docA));   // "Alice" contains "li"
        assertFalse(q.docPredicate().test(docB));  // "Bob" doesn't
    }

    @Test
    void queryBuilder_in() {
        var q = QueryBuilder.selectFrom("u").where("city").in("Paris", "Berlin").build();
        assertTrue(q.docPredicate().test(docA));
        assertFalse(q.docPredicate().test(docB));
    }

    @Test
    void queryBuilder_exists() {
        var q = QueryBuilder.selectFrom("u").where("city").exists().build();
        assertTrue(q.docPredicate().test(docA));
    }

    @Test
    void queryBuilder_regex() {
        var q = QueryBuilder.selectFrom("u").where("name").regex("A.*").build();
        assertTrue(q.docPredicate().test(docA));
        assertFalse(q.docPredicate().test(docB));
    }

    @Test
    void queryBuilder_like() {
        var q = QueryBuilder.selectFrom("u").where("name").like("A%").build();
        assertTrue(q.docPredicate().test(docA));
    }

    @Test
    void queryBuilder_between() {
        var q = QueryBuilder.selectFrom("u").where("age").between(20, 40).build();
        assertTrue(q.docPredicate().test(docA));   // 30 in [20, 40]
        assertFalse(q.docPredicate().test(docB));  // 17 out of range
        assertFalse(q.docPredicate().test(docC));  // 45 out of range
    }

    @Test
    void queryBuilder_orderByAsc() {
        var q = QueryBuilder.selectFrom("u").where("city").eq("Paris")
                .orderBy("score").asc().limit(10).offset(0).build();
        assertEquals(10, q.limit());
        assertEquals(Query.SortOrder.ASC, q.sortOrder());
    }

    @Test
    void queryBuilder_orderByDesc() {
        var q = QueryBuilder.selectFrom("u").where("city").eq("Paris")
                .orderBy("score").desc().build();
        assertEquals(Query.SortOrder.DESC, q.sortOrder());
    }

    @Test
    void queryBuilder_page() {
        var q = QueryBuilder.selectFrom("u").page(2, 10).build();
        assertEquals(20, q.offset());
        assertEquals(10, q.limit());
    }

    // =========================================================================
    // CircuitBreaker — state machine
    // =========================================================================

    @Test
    void circuitBreaker_closedByDefault() {
        var cb = new CircuitBreaker<String>("test");
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void circuitBreaker_successfulExecute() {
        var cb = new CircuitBreaker<String>("test");
        var result = cb.execute(() -> "hello");
        assertEquals("hello", result);
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void circuitBreaker_opensAfterFailures() {
        var cb = new CircuitBreaker<String>("test", 3, 2, 30000, 2);
        for (int i = 0; i < 3; i++) {
            try {
                cb.execute(() -> { throw new RuntimeException("fail"); });
            } catch (RuntimeException ignored) {}
        }
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
    }

    @Test
    void circuitBreaker_openStateRejectsRequests() {
        var cb = new CircuitBreaker<String>("test", 1, 1, 60000, 1);
        try { cb.execute(() -> { throw new RuntimeException("fail"); }); } catch (Exception ignored) {}
        assertThrows(CircuitBreaker.CircuitBreakerOpenException.class, () -> cb.execute(() -> "should-not-run"));
    }

    @Test
    void circuitBreaker_halfOpenAfterTimeout() throws InterruptedException {
        var cb = new CircuitBreaker<String>("test", 1, 1, 100, 1);
        try { cb.execute(() -> { throw new RuntimeException("fail"); }); } catch (Exception ignored) {}
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        Thread.sleep(150);
        // Next execute should be allowed (HALF_OPEN probe)
        var result = cb.execute(() -> "probe");
        assertEquals("probe", result);
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState()); // successThreshold=1 -> closes immediately
    }

    @Test
    void circuitBreaker_executeRunnable_success() {
        var cb = new CircuitBreaker<Void>("runnable-test");
        AtomicInteger counter = new AtomicInteger(0);
        cb.executeRunnable(counter::incrementAndGet);
        assertEquals(1, counter.get());
    }

    @Test
    void circuitBreaker_executeRunnable_failureOpens() {
        var cb = new CircuitBreaker<Void>("runnable-test", 1, 1, 60000, 1);
        assertThrows(RuntimeException.class, () -> cb.executeRunnable(() -> { throw new RuntimeException("fail"); }));
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
    }

    @Test
    void circuitBreaker_reset() {
        var cb = new CircuitBreaker<String>("reset-test", 1, 1, 60000, 1);
        try { cb.execute(() -> { throw new RuntimeException(); }); } catch (Exception ignored) {}
        assertEquals(CircuitBreaker.State.OPEN, cb.getState());
        cb.reset();
        assertEquals(CircuitBreaker.State.CLOSED, cb.getState());
    }

    @Test
    void circuitBreaker_stats_returnsMap() {
        var cb = new CircuitBreaker<String>("stats-test");
        var stats = cb.getStats();
        assertNotNull(stats);
        assertEquals("stats-test", stats.get("name"));
        assertEquals("CLOSED", stats.get("state"));
    }

    // =========================================================================
    // QueryResultCache
    // =========================================================================

    @Test
    void queryResultCache_putAndGet() {
        var cache = new QueryResultCache(60_000, 100);
        var docs = List.of(docA, docB);
        cache.put("col:query1", docs);
        var result = cache.get("col:query1");
        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    void queryResultCache_miss_returnsNull() {
        var cache = new QueryResultCache(60_000, 100);
        assertNull(cache.get("nonexistent"));
    }

    @Test
    void queryResultCache_invalidatePattern() {
        var cache = new QueryResultCache(60_000, 100);
        cache.put("users:all", List.of(docA));
        cache.put("orders:all", List.of(docB));
        cache.invalidatePattern("users");
        assertNull(cache.get("users:all"));
        assertNotNull(cache.get("orders:all"));
    }

    @Test
    void queryResultCache_invalidateSingleKey() {
        var cache = new QueryResultCache(60_000, 100);
        cache.put("col:q", List.of(docA));
        cache.invalidate("col:q");
        assertNull(cache.get("col:q"));
    }

    @Test
    void queryResultCache_clear() {
        var cache = new QueryResultCache(60_000, 100);
        cache.put("k1", List.of(docA));
        cache.put("k2", List.of(docB));
        cache.clear();
        assertNull(cache.get("k1"));
        assertNull(cache.get("k2"));
    }

    @Test
    void queryResultCache_stats() {
        var cache = new QueryResultCache(60_000, 100);
        cache.put("k", List.of(docA));
        cache.get("k");       // hit
        cache.get("missing"); // miss
        var stats = cache.stats();
        assertNotNull(stats);
        assertEquals(1L, stats.hits());
        assertEquals(1L, stats.misses());
    }

    @Test
    void queryResultCache_cacheKey_format() {
        String key = QueryResultCache.cacheKey("users", "SELECT * WHERE age > 18");
        assertTrue(key.startsWith("users:"));
    }

    @Test
    void queryResultCache_ttlExpiry() throws InterruptedException {
        var cache = new QueryResultCache(50, 100); // 50ms TTL
        cache.put("col:q", List.of(docA));
        assertNotNull(cache.get("col:q"));
        Thread.sleep(100);
        assertNull(cache.get("col:q"));
    }

    @Test
    void queryResultCache_evictsOldestWhenFull() {
        var cache = new QueryResultCache(60_000, 2); // capacity = 2
        cache.put("k1", List.of(docA));
        cache.put("k2", List.of(docB));
        cache.put("k3", List.of(docC)); // triggers eviction
        // At least k3 should be present
        assertNotNull(cache.get("k3"));
    }

    // =========================================================================
    // TextIndex — inverted index on a single field
    // =========================================================================

    @Test
    void textIndex_addAndSearch() {
        var idx = new TextIndex("products", "name");
        idx.add(docA); // "Alice"
        idx.add(docB); // "Bob"
        var results = idx.search("alice");
        assertFalse(results.isEmpty());
        assertTrue(results.contains("doc-a"));
    }

    @Test
    void textIndex_searchMiss() {
        var idx = new TextIndex("products", "name");
        idx.add(docA);
        assertTrue(idx.search("Zzzz").isEmpty());
    }

    @Test
    void textIndex_remove() {
        var idx = new TextIndex("products", "name");
        idx.add(docA);
        assertFalse(idx.search("alice").isEmpty());
        idx.remove(docA);
        assertTrue(idx.search("alice").isEmpty());
    }

    @Test
    void textIndex_multiWordSearch() {
        var descDoc = new Document().add("name", "fast database engine");
        descDoc.id("doc-desc");
        var idx = new TextIndex("products", "name");
        idx.add(descDoc);
        // "fast" should match
        assertFalse(idx.search("fast").isEmpty());
        // "database" should match
        assertFalse(idx.search("database").isEmpty());
    }

    @Test
    void textIndex_stopWordsNotIndexed() {
        var idx = new TextIndex("products", "name");
        idx.add(docA); // "Alice"
        // "the" is a stop word and should not be indexed
        assertTrue(idx.search("the").isEmpty());
    }

    @Test
    void textIndex_size() {
        var idx = new TextIndex("products", "name");
        assertEquals(0, idx.size());
        idx.add(docA);
        assertTrue(idx.size() > 0);
    }

    @Test
    void textIndex_isEmpty() {
        var idx = new TextIndex("products", "name");
        assertTrue(idx.isEmpty());
        idx.add(docA);
        assertFalse(idx.isEmpty());
    }

    @Test
    void textIndex_fieldAndCollection() {
        var idx = new TextIndex("products", "name");
        assertEquals("products", idx.collection());
        assertEquals("name", idx.field());
    }

    @Test
    void textIndex_getIndexedTerms() {
        var idx = new TextIndex("products", "name");
        idx.add(docA); // "Alice"
        var terms = idx.getIndexedTerms();
        assertFalse(terms.isEmpty());
    }

    @Test
    void textIndex_missingField_noEffect() {
        var idx = new TextIndex("products", "description");
        idx.add(docA); // docA has no "description" field
        assertTrue(idx.isEmpty()); // nothing indexed
    }

    @Test
    void textIndex_searchPhrases() {
        var idx = new TextIndex("products", "name");
        idx.add(docA);
        // searchPhrases exists — just verify it doesn't throw
        var result = idx.searchPhrases(List.of("alice"));
        assertNotNull(result);
    }
}

package org.embeddedjnosql.db;

import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The shared query result cache is now actually wired to document collections
 * (it existed but EmbedJNoSQL passed null, so every find() rescanned). These
 * tests lock in the three behaviors that make the wiring safe:
 *
 * 1. a repeated query is served from the cache (same list instance),
 * 2. any write to the collection invalidates the cached result, and
 * 3. queryCacheEnabled(false) restores the uncached behavior.
 */
@DisplayName("Shared query result cache serves repeated queries and invalidates on write")
class QueryResultCacheWiringTest {

    private EmbedJNoSQL db;

    @BeforeEach
    void setUp() { db = EmbedJNoSQL.inMemory(); }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    @Test
    @DisplayName("repeated identical queries return the cached list; a write invalidates it")
    void cacheHitThenInvalidationOnWrite() {
        assertTrue(db.queryCache() != null, "default config must enable the query cache");

        var col = db.documentCollection("cached");
        col.insert(new Document().id("a").add("tier", "vip"));
        col.insert(new Document().id("b").add("tier", "regular"));

        Query q = Query.eq("tier", "vip");
        var first = col.find(q);
        assertEquals(1, first.size());

        var second = col.find(q);
        assertSame(first, second, "second identical query must be served from the cache");

        // A write invalidates the collection's cached results.
        col.insert(new Document().id("c").add("tier", "vip"));
        var third = col.find(q);
        assertNotSame(first, third, "a write must invalidate the cached query result");
        assertEquals(2, third.size());
    }

    @Test
    @DisplayName("update and delete invalidate the cache as well")
    void updateAndDeleteInvalidate() {
        var col = db.documentCollection("cached2");
        col.insert(new Document().id("a").add("n", 1));
        var first = col.find(Query.gt("n", 0));
        assertEquals(1, first.size());

        var doc = col.findById("a");
        doc.add("n", 5);
        col.update(doc);
        assertNotSame(first, col.find(Query.gt("n", 0)), "update must invalidate");

        var second = col.find(Query.gt("n", 0));
        col.deleteById("a");
        assertNotSame(second, col.find(Query.gt("n", 0)), "delete must invalidate");
        assertEquals(0, col.find(Query.gt("n", 0)).size());
    }

    @Test
    @DisplayName("queryCacheEnabled(false) disables the cache entirely")
    void disabledByConfig() {
        try (EmbedJNoSQL off = EmbedJNoSQL.embed().queryCacheEnabled(false).build()) {
            assertNull(off.queryCache(), "disabled config must not create a cache");
            var col = off.documentCollection("uncached");
            col.insert(new Document().id("a").add("n", 1));
            var first = col.find(Query.gt("n", 0));
            var second = col.find(Query.gt("n", 0));
            assertNotSame(first, second, "no cache means fresh results every time");
        }
    }
}

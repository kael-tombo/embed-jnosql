package org.embeddedjnosql.db.demo.demos.keyvaluebuckets;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KeyValueDemoTest {

    private EmbedJNoSQL db;

    @BeforeEach
    void setUp() { db = EmbedJNoSQL.inMemory(); }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    @Test
    @DisplayName("put/get, counters, atomic batch and TTL all behave")
    void runDemo() {
        var r = KeyValueDemo.runDemo(db);
        assertEquals("alice", r.sessionUser());
        assertEquals(5, r.pageViews());
        assertEquals(8, r.inventoryLeft());
        assertEquals(2, r.batchResult().size());
        assertTrue(r.expiredKeyGone());
    }

    @Test
    @DisplayName("decrement below zero and missing keys behave like Redis-style counters")
    void counterEdges() {
        var kv = db.keyValueBucket("counter_edges");
        assertEquals(3, kv.increment("hits", 3));
        assertEquals(2, kv.decrement("hits"));
        // A missing key starts at 0
        assertEquals(-2, kv.increment("ghost", -2));
    }
}

package org.embeddedjnosql.db.demo.demos.ttlexpiry;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TTL expiry across the document, KV, and wide-column models. The engine's
 * expiry semantics: expired entries read as absent, a document with no TTL
 * survives, and {@code cleanupExpired()} sweeps raw storage.
 */
class TtlExpiryDemoTest {

    private EmbedJNoSQL db;

    @BeforeEach
    void setUp() { db = EmbedJNoSQL.inMemory(); }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    @Test
    @DisplayName("expired documents, KV entries and columns read as absent; durable data survives")
    void ttlExpiryAcrossModels() throws Exception {
        var result = TtlExpiryDemo.runDemo(db);
        assertTrue(result.docGone());
        assertTrue(result.kvGone());
        assertTrue(result.colGone());
        assertTrue(result.durableSurvived());
    }

    @Test
    @DisplayName("setTtl can attach and remove TTL on an existing document")
    void setTtlRoundTrip() {
        var col = db.documentCollection("ttl_demo");
        col.insert(new org.embeddedjnosql.db.nosql.document.Document().id("t1").add("v", 1));
        assertEquals(1, col.setTtl("t1", 60));
        assertNotNull(col.findById("t1"));
        // Removing the TTL (ttlSeconds <= 0) keeps the document alive forever
        assertEquals(1, col.setTtl("t1", -1));
        assertNotNull(col.findById("t1"));
    }

    @Test
    @DisplayName("cleanupExpired removes expired documents and returns the count")
    void cleanupSweep() throws Exception {
        var col = db.documentCollection("ttl_sweep");
        col.insert(new org.embeddedjnosql.db.nosql.document.Document().id("gone").add("v", 1), 1);
        col.insert(new org.embeddedjnosql.db.nosql.document.Document().id("stay").add("v", 2));
        Thread.sleep(1200);
        assertEquals(1, col.cleanupExpired());
        assertFalse(col.exists("gone"));
        assertTrue(col.exists("stay"));
    }
}

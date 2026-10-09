package org.embeddedjnosql.db.demo.demos.metrics;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MetricsDemoTest {

    private EmbedJNoSQL db;

    @BeforeEach
    void setUp() { db = EmbedJNoSQL.inMemory(); }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    @Test
    @DisplayName("snapshot counters reflect the traffic the demo drives")
    void runDemo() {
        var r = MetricsDemo.runDemo(db);
        assertTrue(r.inserts() >= 3, "expected >=3 inserts, got " + r.inserts());
        assertTrue(r.reads() >= 2);
        assertTrue(r.queries() >= 1);
        assertTrue(r.deletes() >= 1);
        assertNotNull(r.snapshot());
    }

    @Test
    @DisplayName("reset() zeroes the counters")
    void reset() {
        var col = db.documentCollection("metric_reset");
        col.insert(new org.embeddedjnosql.db.nosql.document.Document().id("a"));
        assertTrue(((Number) db.metrics().snapshot().getOrDefault("inserts", 0)).longValue() >= 1);
        db.metrics().reset();
        assertEquals(0, ((Number) db.metrics().snapshot().getOrDefault("inserts", 0)).longValue());
    }
}

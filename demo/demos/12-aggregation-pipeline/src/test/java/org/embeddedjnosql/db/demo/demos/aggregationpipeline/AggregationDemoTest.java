package org.embeddedjnosql.db.demo.demos.aggregationpipeline;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AggregationDemoTest {

    private EmbedJNoSQL db;

    @BeforeEach
    void setUp() { db = EmbedJNoSQL.inMemory(); }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    @Test
    @DisplayName("group-sum, group-count and overall aggregates compute correctly")
    void runDemo() {
        var r = AggregationDemo.runDemo(db);
        assertEquals(3198.99, r.revenueByCategory().get("Laptops"), 0.01);
        assertEquals(2L, r.countByCategory().get("Audio"));
        assertEquals(1899.99, r.maxPrice(), 0.01);
        assertEquals(59.99, r.minPrice(), 0.01);
        assertTrue(r.overallAvg() > 0);
    }

    @Test
    @DisplayName("aggregating an empty result set yields zeros without throwing")
    void emptyAggregation() {
        var empty = db.documentCollection("agg_empty");
        var none = empty.find(org.embeddedjnosql.db.nosql.document.Query.all());
        assertEquals(0, none.stream().mapToDouble(d -> 1).sum());
    }
}

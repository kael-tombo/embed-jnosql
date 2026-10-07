package org.embeddedjnosql.db;

import org.embeddedjnosql.db.nosql.document.AggregationPipeline;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.document.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AggregationPipelineTest {

    private EmbedJNoSQL db;
    private DocumentCollection orders;

    @BeforeEach
    void setUp() {
        db = EmbedJNoSQL.embed().build();
        orders = db.documentCollection("orders");
        
        orders.insertAll(List.of(
                Document.of("customer", "Alice").add("product", "Widget").add("amount", 100.0).add("quantity", 2),
                Document.of("customer", "Bob").add("product", "Widget").add("amount", 50.0).add("quantity", 1),
                Document.of("customer", "Alice").add("product", "Gadget").add("amount", 200.0).add("quantity", 1),
                Document.of("customer", "Bob").add("product", "Gadget").add("amount", 150.0).add("quantity", 3),
                Document.of("customer", "Charlie").add("product", "Widget").add("amount", 75.0).add("quantity", 1)
        ));
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void testCount() {
        long count = AggregationPipeline.create(orders).count();
        assertEquals(5, count);
    }

    @Test
    void testSum() {
        var result = AggregationPipeline.create(orders)
                .sum("amount", "totalAmount")
                .execute();
        
        assertEquals(1, result.size());
        assertEquals(575.0, result.get(0).get("totalAmount"));
    }

    @Test
    void testAvg() {
        var result = AggregationPipeline.create(orders)
                .avg("amount", "avgAmount")
                .execute();
        
        assertEquals(1, result.size());
        assertEquals(115.0, result.get(0).get("avgAmount"));
    }

    @Test
    void testMinMax() {
        var minResult = AggregationPipeline.create(orders)
                .min("amount", "minAmount")
                .execute();
        assertEquals(50.0, minResult.get(0).get("minAmount"));
        
        var maxResult = AggregationPipeline.create(orders)
                .max("amount", "maxAmount")
                .execute();
        assertEquals(200.0, maxResult.get(0).get("maxAmount"));
    }

    @Test
    void testGroupBy() {
        var result = AggregationPipeline.create(orders)
                .group("product", AggregationPipeline.GroupOperation.COUNT)
                .execute();
        
        assertEquals(2, result.size());
        
        for (var doc : result) {
            String product = (String) doc.get("_id");
            int count = ((Number) doc.get("count")).intValue();
            if ("Widget".equals(product)) {
                assertEquals(3, count);
            } else if ("Gadget".equals(product)) {
                assertEquals(2, count);
            }
        }
    }

    @Test
    void testMatchAndGroup() {
        var result = AggregationPipeline.create(orders)
                .match(Query.eq("product", "Widget"))
                .group("customer", AggregationPipeline.GroupOperation.SUM)
                .execute();
        
        assertEquals(3, result.size());
    }

    @Test
    void testSortAndLimit() {
        var result = AggregationPipeline.create(orders)
                .sortByDesc("amount")
                .limit(3)
                .execute();
        
        assertEquals(3, result.size());
        assertEquals(200.0, result.get(0).get("amount"));
        assertEquals(150.0, result.get(1).get("amount"));
        assertEquals(100.0, result.get(2).get("amount"));
    }

    @Test
    void testSkip() {
        var result = AggregationPipeline.create(orders)
                .sortByDesc("amount")
                .skip(2)
                .execute();
        
        assertEquals(3, result.size());
        assertEquals(100.0, result.get(0).get("amount"));
    }

    @Test
    void testProject() {
        var result = AggregationPipeline.create(orders)
                .project("customer", "product")
                .limit(2)
                .execute();
        
        assertEquals(2, result.size());
        for (var doc : result) {
            assertFalse(doc.has("amount"));
            assertFalse(doc.has("quantity"));
        }
    }

    @Test
    void testComplexPipeline() {
        var result = AggregationPipeline.create(orders)
                .match(Query.gte("amount", 100.0))
                .group("product", AggregationPipeline.GroupOperation.COUNT)
                .sortByDesc("count")
                .limit(5)
                .execute();
        
        assertEquals(2, result.size());
        int count0 = ((Number) result.get(0).get("count")).intValue();
        int count1 = ((Number) result.get(1).get("count")).intValue();
        assertTrue(count0 >= count1);
    }
}

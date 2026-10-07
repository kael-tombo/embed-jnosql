package org.embeddedjnosql.db;

import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.document.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AdvancedQueryTest {

    private EmbedJNoSQL db;
    private DocumentCollection products;

    @BeforeEach
    void setUp() {
        db = EmbedJNoSQL.embed().build();
        products = db.documentCollection("products");
        products.insertAll(List.of(
                Document.of("name", "Apple").add("price", 1.5).add("category", "fruit"),
                Document.of("name", "Banana").add("price", 0.8).add("category", "fruit"),
                Document.of("name", "Carrot").add("price", 1.2).add("category", "vegetable"),
                Document.of("name", "Date").add("price", 3.0).add("category", "fruit"),
                Document.of("name", "Eggplant").add("price", 2.5).add("category", "vegetable")
        ));
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void sortByAsc() {
        var results = products.find(Query.all().sortByAsc("price"));
        assertEquals("Banana", results.get(0).get("name"));
        assertEquals("Carrot", results.get(1).get("name"));
        assertEquals("Apple", results.get(2).get("name"));
        assertEquals("Eggplant", results.get(3).get("name"));
        assertEquals("Date", results.get(4).get("name"));
    }

    @Test
    void sortByDesc() {
        var results = products.find(Query.all().sortByDesc("price"));
        assertEquals("Date", results.get(0).get("name"));
        assertEquals("Eggplant", results.get(1).get("name"));
        assertEquals("Apple", results.get(2).get("name"));
        assertEquals("Carrot", results.get(3).get("name"));
        assertEquals("Banana", results.get(4).get("name"));
    }

    @Test
    void limit() {
        var results = products.find(Query.all().limit(2));
        assertEquals(2, results.size());
    }

    @Test
    void offset() {
        var results = products.find(Query.all().sortByAsc("price").offset(2));
        assertEquals(3, results.size());
        assertEquals("Apple", results.get(0).get("name"));
    }

    @Test
    void pagination() {
        var page0 = products.find(Query.all().sortByAsc("price").page(0, 2));
        var page1 = products.find(Query.all().sortByAsc("price").page(1, 2));
        var page2 = products.find(Query.all().sortByAsc("price").page(2, 2));

        assertEquals(2, page0.size());
        assertEquals(2, page1.size());
        assertEquals(1, page2.size());
        assertEquals("Banana", page0.get(0).get("name"));
        assertEquals("Apple", page1.get(0).get("name"));
        assertEquals("Date", page2.get(0).get("name"));
    }

    @Test
    void findOne() {
        var result = products.findOne(Query.eq("name", "Banana"));
        assertNotNull(result);
        assertEquals("Banana", result.get("name"));
    }

    @Test
    void findOneNotFound() {
        var result = products.findOne(Query.eq("name", "Zucchini"));
        assertNull(result);
    }

    @Test
    void gteQuery() {
        var results = products.find(Query.gte("price", 1.5));
        assertEquals(3, results.size());
    }

    @Test
    void lteQuery() {
        var results = products.find(Query.lte("price", 1.2));
        assertEquals(2, results.size());
    }

    @Test
    void inQuery() {
        var results = products.find(Query.in("name", List.of("Apple", "Date")));
        assertEquals(2, results.size());
    }

    @Test
    void neQuery() {
        var results = products.find(Query.ne("category", "fruit"));
        assertEquals(2, results.size());
    }

    @Test
    void existsQuery() {
        var results = products.find(Query.exists("price"));
        assertEquals(5, results.size());
    }

    @Test
    void upsert() {
        var existing = products.findOne(Query.eq("name", "Apple"));
        existing.add("price", 2.0);
        var updated = products.upsert(existing);
        assertEquals(2.0, (double) updated.get("price"), 0.01);

        var newDoc = Document.of("name", "Fig").add("price", 4.0);
        var inserted = products.upsert(newDoc);
        assertNotNull(inserted.id());
        assertEquals(6, products.count());
    }

    @Test
    void countWithQuery() {
        assertEquals(3, products.count(Query.eq("category", "fruit")));
        assertEquals(2, products.count(Query.eq("category", "vegetable")));
    }

    @Test
    void stats() {
        var stats = products.stats();
        assertEquals("products", stats.get("collection"));
        assertEquals(5L, stats.get("count"));
    }

    @Test
    void combinedSortAndFilter() {
        var results = products.find(
                Query.eq("category", "fruit").sortByDesc("price")
        );
        assertEquals(3, results.size());
        assertEquals("Date", results.get(0).get("name"));
        assertEquals("Banana", results.get(2).get("name"));
    }
}

package org.embeddedjnosql.db;

import org.embeddedjnosql.db.index.SecondaryIndex;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SecondaryIndexTest {

    private EmbedJNoSQL db;

    @BeforeEach
    void setUp() {
        db = EmbedJNoSQL.embed().build();
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void secondaryIndexDirectNumericCrossTypeLookup() {
        var idx = new SecondaryIndex("products", "price");
        var doc1 = Document.of("price", 100).id("prod-1"); // Stored as Integer 100
        var doc2 = Document.of("price", 250L).id("prod-2"); // Stored as Long 250
        idx.add(doc1);
        idx.add(doc2);

        // Lookup with exact same type
        assertEquals(Set.of("prod-1"), idx.lookup(100));

        // Lookup with cross-type Long -> matches Integer 100
        assertEquals(Set.of("prod-1"), idx.lookup(100L));

        // Lookup with cross-type Double -> matches Long 250
        assertEquals(Set.of("prod-2"), idx.lookup(250.0));

        // Lookup non-existent
        assertTrue(idx.lookup(999).isEmpty());
    }

    @Test
    void secondaryIndexNumericRangeWithMismatchedTypes() {
        var idx = new SecondaryIndex("products", "score");
        // Insert Integer keys
        idx.add(Document.of("score", 10).id("d1"));
        idx.add(Document.of("score", 20).id("d2"));
        idx.add(Document.of("score", 30).id("d3"));

        // Query with Long and Double bounds without ClassCastException
        var inRange = idx.range(15L, 35.0, true);
        assertEquals(Set.of("d2", "d3"), inRange);

        var gt = idx.greaterThan(15L, true);
        assertEquals(Set.of("d2", "d3"), gt);

        var lt = idx.lessThan(25.0, true);
        assertEquals(Set.of("d1", "d2"), lt);
    }

    @Test
    void secondaryIndexStringRange() {
        var idx = new SecondaryIndex("users", "name");
        idx.add(Document.of("name", "Alice").id("u1"));
        idx.add(Document.of("name", "Bob").id("u2"));
        idx.add(Document.of("name", "Charlie").id("u3"));

        var inRange = idx.range("A", "C", true);
        assertEquals(Set.of("u1", "u2"), inRange);
    }

    @Test
    void documentCollectionIndexedQueryNumericCoercion() {
        var col = db.documentCollection("items");
        col.createIndex("price");

        // Insert documents with Integer price
        col.insert(Document.of("name", "WidgetA").add("price", 50));
        col.insert(Document.of("name", "WidgetB").add("price", 100));

        // Query with Long target
        var foundLong = col.find(Query.eq("price", 100L));
        assertEquals(1, foundLong.size());
        assertEquals("WidgetB", foundLong.get(0).get("name"));

        // Query with Double target
        var foundDouble = col.find(Query.eq("price", 50.0));
        assertEquals(1, foundDouble.size());
        assertEquals("WidgetA", foundDouble.get(0).get("name"));

        // Query ne with Double
        var foundNe = col.find(Query.ne("price", 50.0));
        assertEquals(1, foundNe.size());
        assertEquals("WidgetB", foundNe.get(0).get("name"));
    }
}

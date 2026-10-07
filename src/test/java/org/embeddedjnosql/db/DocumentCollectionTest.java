package org.embeddedjnosql.db;

import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.document.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DocumentCollectionTest {

    private EmbedJNoSQL db;
    private DocumentCollection users;

    @BeforeEach
    void setUp() {
        db = EmbedJNoSQL.embed().build();
        users = db.documentCollection("users");
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void insertAndFind() {
        var doc = Document.of("name", "Alice").add("age", 30);
        var saved = users.insert(doc);

        assertNotNull(saved.id());
        var found = users.findById(saved.id());

        assertNotNull(found);
        assertEquals("Alice", found.get("name"));
        assertEquals(30, (int) found.get("age"));
    }

    @Test
    void insertAll() {
        var docs = List.of(
                Document.of("name", "Alice").add("age", 30),
                Document.of("name", "Bob").add("age", 25),
                Document.of("name", "Charlie").add("age", 35)
        );
        var saved = users.insertAll(docs);

        assertEquals(3, saved.size());
        assertEquals(3, users.count());
    }

    @Test
    void findAll() {
        users.insertAll(List.of(
                Document.of("name", "Alice"),
                Document.of("name", "Bob")
        ));

        var all = users.findAll();
        assertEquals(2, all.size());
    }

    @Test
    void findByEquality() {
        users.insertAll(List.of(
                Document.of("name", "Alice").add("age", 30),
                Document.of("name", "Bob").add("age", 25),
                Document.of("name", "Alice").add("age", 40)
        ));

        var results = users.find(Query.eq("name", "Alice"));
        assertEquals(2, results.size());
    }

    @Test
    void findByGreaterThan() {
        users.insertAll(List.of(
                Document.of("name", "Alice").add("age", 30),
                Document.of("name", "Bob").add("age", 25),
                Document.of("name", "Charlie").add("age", 35)
        ));

        var results = users.find(Query.gt("age", 28));
        assertEquals(2, results.size());
    }

    @Test
    void findByLessThan() {
        users.insertAll(List.of(
                Document.of("name", "Alice").add("age", 30),
                Document.of("name", "Bob").add("age", 25),
                Document.of("name", "Charlie").add("age", 35)
        ));

        var results = users.find(Query.lt("age", 30));
        assertEquals(1, results.size());
        assertEquals("Bob", results.get(0).get("name"));
    }

    @Test
    void update() {
        var doc = users.insert(Document.of("name", "Alice").add("age", 30));
        doc.add("age", 31);

        var updated = users.update(doc);
        var found = users.findById(doc.id());

        assertEquals(31, (int) found.get("age"));
    }

    @Test
    void deleteById() {
        var doc = users.insert(Document.of("name", "Alice"));
        assertTrue(users.deleteById(doc.id()));
        assertNull(users.findById(doc.id()));
        assertEquals(0, users.count());
    }

    @Test
    void deleteByQuery() {
        users.insertAll(List.of(
                Document.of("name", "Alice").add("active", true),
                Document.of("name", "Bob").add("active", false)
        ));

        var deleted = users.deleteAll(Query.eq("active", false));
        assertEquals(1, deleted);
        assertEquals(1, users.count());
    }

    @Test
    void count() {
        assertEquals(0, users.count());
        users.insert(Document.of("name", "Alice"));
        assertEquals(1, users.count());
    }

    @Test
    void exists() {
        var doc = users.insert(Document.of("name", "Alice"));
        assertTrue(users.exists(doc.id()));
        assertFalse(users.exists("nonexistent"));
    }

    @Test
    void clear() {
        users.insertAll(List.of(
                Document.of("name", "Alice"),
                Document.of("name", "Bob")
        ));
        users.clear();
        assertEquals(0, users.count());
    }

    @Test
    void updateNonExistentThrows() {
        var doc = Document.of("name", "Alice").id("nonexistent");
        assertThrows(IllegalArgumentException.class, () -> users.update(doc));
    }

    @Test
    void andQuery() {
        users.insertAll(List.of(
                Document.of("name", "Alice").add("age", 30),
                Document.of("name", "Alice").add("age", 20),
                Document.of("name", "Bob").add("age", 30)
        ));

        var results = users.find(Query.eq("name", "Alice").and(Query.gt("age", 25)));
        assertEquals(1, results.size());
    }

    @Test
    void orQuery() {
        users.insertAll(List.of(
                Document.of("name", "Alice").add("age", 30),
                Document.of("name", "Bob").add("age", 25),
                Document.of("name", "Charlie").add("age", 35)
        ));

        var results = users.find(Query.eq("name", "Alice").or(Query.eq("name", "Bob")));
        assertEquals(2, results.size());
    }
}

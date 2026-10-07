package org.embeddedjnosql.db;

import org.embeddedjnosql.db.nosql.column.ColumnFamily;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ColumnFamilyTest {

    private EmbedJNoSQL db;
    private ColumnFamily cf;

    @BeforeEach
    void setUp() {
        db = EmbedJNoSQL.embed().build();
        cf = db.columnFamily("users");
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void putAndGet() {
        cf.put("user:1", "name", "Alice");
        cf.put("user:1", "age", 30);
        cf.put("user:1", "email", "alice@example.com");

        assertEquals("Alice", cf.get("user:1", "name"));
        assertEquals(30, cf.get("user:1", "age"));
        assertEquals("alice@example.com", cf.get("user:1", "email"));
    }

    @Test
    void getNonExistentColumn() {
        assertNull(cf.get("user:1", "missing"));
    }

    @Test
    void getNonExistentRow() {
        assertNull(cf.get("nonexistent", "name"));
    }

    @Test
    void deleteColumn() {
        cf.put("user:1", "name", "Alice");
        cf.put("user:1", "age", 30);
        cf.deleteColumn("user:1", "age");

        assertEquals("Alice", cf.get("user:1", "name"));
        assertNull(cf.get("user:1", "age"));
    }

    @Test
    void deleteRow() {
        cf.put("user:1", "name", "Alice");
        cf.put("user:2", "name", "Bob");
        cf.deleteRow("user:1");

        assertNull(cf.get("user:1", "name"));
        assertEquals("Bob", cf.get("user:2", "name"));
    }

    @Test
    void getRowKeys() {
        cf.put("user:1", "name", "Alice");
        cf.put("user:2", "name", "Bob");
        cf.put("user:3", "name", "Charlie");

        var keys = cf.getRowKeys();
        assertEquals(3, keys.size());
        assertTrue(keys.contains("user:1"));
        assertTrue(keys.contains("user:2"));
        assertTrue(keys.contains("user:3"));
    }

    @Test
    void countRows() {
        assertEquals(0, cf.countRows());
        cf.put("user:1", "name", "Alice");
        cf.put("user:2", "name", "Bob");
        assertEquals(2, cf.countRows());
    }

    @Test
    void updateColumn() {
        cf.put("user:1", "name", "Alice");
        cf.put("user:1", "name", "Alice Updated");
        assertEquals("Alice Updated", cf.get("user:1", "name"));
    }
}

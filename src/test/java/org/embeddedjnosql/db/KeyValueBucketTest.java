package org.embeddedjnosql.db;

import org.embeddedjnosql.db.nosql.kv.KeyValueBucket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class KeyValueBucketTest {

    private EmbedJNoSQL db;
    private KeyValueBucket bucket;

    @BeforeEach
    void setUp() {
        db = EmbedJNoSQL.embed().build();
        bucket = db.keyValueBucket("cache");
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void putAndGet() {
        bucket.put("key1", "value1");
        assertEquals("value1", bucket.get("key1"));
    }

    @Test
    void getNonExistent() {
        assertNull(bucket.get("nonexistent"));
    }

    @Test
    void delete() {
        bucket.put("key1", "value1");
        assertTrue(bucket.delete("key1"));
        assertNull(bucket.get("key1"));
    }

    @Test
    void deleteNonExistent() {
        assertFalse(bucket.delete("nonexistent"));
    }

    @Test
    void exists() {
        bucket.put("key1", "value1");
        assertTrue(bucket.exists("key1"));
        assertFalse(bucket.exists("nonexistent"));
    }

    @Test
    void increment() {
        assertEquals(1, bucket.increment("counter"));
        assertEquals(2, bucket.increment("counter"));
        assertEquals(5, bucket.increment("counter", 3));
    }

    @Test
    void decrement() {
        bucket.put("counter", "10");
        assertEquals(9, bucket.decrement("counter"));
        assertEquals(8, bucket.decrement("counter"));
    }

    @Test
    void incrementFromZero() {
        assertEquals(1, bucket.increment("new-counter"));
        assertEquals("1", bucket.get("new-counter"));
    }

    @Test
    void overwrite() {
        bucket.put("key1", "value1");
        bucket.put("key1", "value2");
        assertEquals("value2", bucket.get("key1"));
    }
}

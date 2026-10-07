package org.embeddedjnosql.db;

import org.embeddedjnosql.db.nosql.kv.HashBucket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HashBucketTest {

    private EmbedJNoSQL db;
    private HashBucket bucket;

    @BeforeEach
    void setUp() {
        db = EmbedJNoSQL.embed().build();
        bucket = db.hashBucket("myhashes");
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void hset_single() {
        assertEquals(1, bucket.hset("myhash", "field1", "value1"));
        assertEquals(0, bucket.hset("myhash", "field1", "value1_updated")); // Update
        
        assertEquals("value1_updated", bucket.hget("myhash", "field1"));
    }

    @Test
    void hset_multiple() {
        var fields = Map.of(
            "name", "John",
            "age", "30",
            "city", "NYC"
        );
        
        int added = bucket.hset("user:1", fields);
        assertEquals(3, added);
        
        assertEquals("John", bucket.hget("user:1", "name"));
        assertEquals("30", bucket.hget("user:1", "age"));
        assertEquals("NYC", bucket.hget("user:1", "city"));
    }

    @Test
    void hget() {
        bucket.hset("myhash", "field1", "value1");
        bucket.hset("myhash", "field2", "value2");
        
        assertEquals("value1", bucket.hget("myhash", "field1"));
        assertEquals("value2", bucket.hget("myhash", "field2"));
        assertNull(bucket.hget("myhash", "nonexistent"));
        assertNull(bucket.hget("nonexistent", "field"));
    }

    @Test
    void hmget() {
        bucket.hset("myhash", "a", "1");
        bucket.hset("myhash", "b", "2");
        bucket.hset("myhash", "c", "3");
        
        var result = bucket.hmget("myhash", "a", "c", "nonexistent");
        assertEquals(2, result.size());
        assertEquals("1", result.get("a"));
        assertEquals("3", result.get("c"));
    }

    @Test
    void hgetall() {
        assertTrue(bucket.hgetall("nonexistent").isEmpty());
        
        bucket.hset("myhash", "field1", "value1");
        bucket.hset("myhash", "field2", "value2");
        
        var all = bucket.hgetall("myhash");
        assertEquals(2, all.size());
        assertEquals("value1", all.get("field1"));
        assertEquals("value2", all.get("field2"));
    }

    @Test
    void hdel() {
        bucket.hset("myhash", "a", "1");
        bucket.hset("myhash", "b", "2");
        bucket.hset("myhash", "c", "3");
        
        assertEquals(2, bucket.hdel("myhash", "a", "c"));
        assertEquals(0, bucket.hdel("myhash", "a")); // Already deleted
        
        assertEquals(1, bucket.hlen("myhash"));
        assertEquals("2", bucket.hget("myhash", "b"));
    }

    @Test
    void hlen() {
        assertEquals(0, bucket.hlen("nonexistent"));
        
        bucket.hset("myhash", "a", "1");
        bucket.hset("myhash", "b", "2");
        bucket.hset("myhash", "c", "3");
        
        assertEquals(3, bucket.hlen("myhash"));
        
        bucket.hdel("myhash", "b");
        assertEquals(2, bucket.hlen("myhash"));
    }

    @Test
    void hexists() {
        bucket.hset("myhash", "field1", "value1");
        
        assertTrue(bucket.hexists("myhash", "field1"));
        assertFalse(bucket.hexists("myhash", "nonexistent"));
        assertFalse(bucket.hexists("nonexistent", "field"));
    }

    @Test
    void hkeys() {
        bucket.hset("myhash", "a", "1");
        bucket.hset("myhash", "b", "2");
        bucket.hset("myhash", "c", "3");
        
        var keys = bucket.hkeys("myhash");
        assertEquals(3, keys.size());
        assertTrue(keys.contains("a"));
        assertTrue(keys.contains("c"));
    }

    @Test
    void hvals() {
        bucket.hset("myhash", "a", "1");
        bucket.hset("myhash", "b", "2");
        bucket.hset("myhash", "c", "3");
        
        var vals = bucket.hvals("myhash");
        assertEquals(3, vals.size());
        assertTrue(vals.contains("1"));
        assertTrue(vals.contains("3"));
    }

    @Test
    void hincrby() {
        assertEquals(5, bucket.hincrby("myhash", "counter", 5));
        assertEquals(8, bucket.hincrby("myhash", "counter", 3));
        assertEquals(3, bucket.hincrby("myhash", "counter", -5));
        
        bucket.hset("myhash", "existing", "10");
        assertEquals(15, bucket.hincrby("myhash", "existing", 5));
    }

    @Test
    void hincrbyfloat() {
        assertEquals("3.14", bucket.hincrbyfloat("myhash", "pi", 3.14));
        String result = bucket.hincrbyfloat("myhash", "pi", 1.0);
        assertTrue(result.startsWith("4.14"), "Expected result starting with 4.14, got: " + result);
        result = bucket.hincrbyfloat("myhash", "pi", -2.0);
        assertTrue(result.startsWith("2.14"), "Expected result starting with 2.14, got: " + result);
    }

    @Test
    void hsetnx() {
        assertEquals(1, bucket.hsetnx("myhash", "field", "value1"));
        assertEquals(0, bucket.hsetnx("myhash", "field", "value2")); // Already exists
        
        assertEquals("value1", bucket.hget("myhash", "field"));
    }

    @Test
    void hmset() {
        var fields = Map.of("a", "1", "b", "2", "c", "3");
        assertEquals("OK", bucket.hmset("myhash", fields));
        
        var retrieved = bucket.hgetall("myhash");
        assertEquals(3, retrieved.size());
    }

    @Test
    void hstrlen() {
        bucket.hset("myhash", "short", "abc");
        bucket.hset("myhash", "long", "hello world");
        
        assertEquals(3, bucket.hstrlen("myhash", "short"));
        assertEquals(11, bucket.hstrlen("myhash", "long"));
        assertEquals(0, bucket.hstrlen("myhash", "nonexistent"));
    }

    @Test
    void hgetallFlat() {
        bucket.hset("myhash", "a", "1");
        bucket.hset("myhash", "b", "2");
        
        var flat = bucket.hgetallFlat("myhash");
        assertEquals(4, flat.size());
        assertTrue(flat.contains("a"));
        assertTrue(flat.contains("1"));
        assertTrue(flat.contains("b"));
        assertTrue(flat.contains("2"));
    }

    @Test
    void delete() {
        bucket.hset("myhash", "a", "1");
        bucket.hset("myhash", "b", "2");
        
        assertTrue(bucket.delete("myhash"));
        assertEquals(0, bucket.hlen("myhash"));
        assertFalse(bucket.delete("nonexistent"));
    }

    @Test
    void clear() {
        bucket.hset("hash1", "a", "1");
        bucket.hset("hash2", "b", "2");
        bucket.hset("hash3", "c", "3");
        
        bucket.clear();
        assertEquals(0, bucket.hlen("hash1"));
        assertEquals(0, bucket.hlen("hash2"));
    }

    @Test
    void stats() {
        bucket.hset("hash1", "a", "1");
        bucket.hset("hash1", "b", "2");
        bucket.hset("hash2", "x", "10");
        bucket.hset("hash2", "y", "20");
        bucket.hset("hash2", "z", "30");
        
        var stats = bucket.stats();
        assertEquals("myhashes", stats.get("name"));
        assertEquals(2L, stats.get("totalHashes"));
        assertEquals(5L, stats.get("totalFields"));
    }

    @Test
    void empty_hash_operations() {
        assertTrue(bucket.hgetall("nonexistent").isEmpty());
        assertTrue(bucket.hkeys("nonexistent").isEmpty());
        assertTrue(bucket.hvals("nonexistent").isEmpty());
        assertEquals(0, bucket.hlen("nonexistent"));
        assertNull(bucket.hget("nonexistent", "field"));
        assertFalse(bucket.hexists("nonexistent", "field"));
    }
}

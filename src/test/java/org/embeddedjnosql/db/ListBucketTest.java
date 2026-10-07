package org.embeddedjnosql.db;

import org.embeddedjnosql.db.nosql.kv.ListBucket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ListBucketTest {

    private EmbedJNoSQL db;
    private ListBucket bucket;

    @BeforeEach
    void setUp() {
        db = EmbedJNoSQL.embed().build();
        bucket = db.listBucket("mylists");
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void lpush_and_rpush() {
        assertEquals(1, bucket.lpush("mylist", "a"));
        assertEquals(3, bucket.lpush("mylist", "b", "c"));
        assertEquals(6, bucket.rpush("mylist", "d", "e", "f"));
        
        var all = bucket.lrange("mylist", 0, -1);
        assertEquals(List.of("c", "b", "a", "d", "e", "f"), all);
    }

    @Test
    void lpop() {
        bucket.rpush("mylist", "first", "middle", "last");
        assertEquals("first", bucket.lpop("mylist"));
        assertEquals("middle", bucket.lpop("mylist"));
        assertEquals(1, bucket.llen("mylist"));
    }

    @Test
    void rpop() {
        bucket.rpush("mylist", "first", "middle", "last");
        assertEquals("last", bucket.rpop("mylist"));
        assertEquals("middle", bucket.rpop("mylist"));
        assertEquals(1, bucket.llen("mylist"));
    }

    @Test
    void lrange() {
        bucket.rpush("mylist", "a", "b", "c", "d", "e");
        
        assertEquals(List.of("a", "b", "c"), bucket.lrange("mylist", 0, 2));
        assertEquals(List.of("c", "d", "e"), bucket.lrange("mylist", 2, -1));
        assertEquals(List.of("b", "c", "d"), bucket.lrange("mylist", 1, 3));
    }

    @Test
    void llen() {
        assertEquals(0, bucket.llen("nonexistent"));
        bucket.rpush("mylist", "a", "b", "c");
        assertEquals(3, bucket.llen("mylist"));
    }

    @Test
    void lrem() {
        bucket.rpush("mylist", "a", "b", "a", "c", "a", "b", "a");
        
        // Remove first 2 occurrences from head
        assertEquals(2, bucket.lrem("mylist", 2, "a"));
        assertEquals(List.of("b", "c", "a", "b", "a"), bucket.lrange("mylist", 0, -1));
        
        // Remove 1 occurrence from tail
        bucket.rpush("mylist2", "x", "y", "x", "z", "x");
        assertEquals(1, bucket.lrem("mylist2", -1, "x"));
        assertEquals(List.of("x", "y", "x", "z"), bucket.lrange("mylist2", 0, -1));
        
        // Remove all occurrences
        bucket.rpush("mylist3", "a", "b", "a", "a");
        assertEquals(3, bucket.lrem("mylist3", 0, "a"));
        assertEquals(List.of("b"), bucket.lrange("mylist3", 0, -1));
    }

    @Test
    void lindex() {
        bucket.rpush("mylist", "a", "b", "c", "d");
        
        assertEquals("a", bucket.lindex("mylist", 0));
        assertEquals("c", bucket.lindex("mylist", 2));
        assertEquals("d", bucket.lindex("mylist", -1));
        assertEquals("b", bucket.lindex("mylist", -3));
        assertNull(bucket.lindex("mylist", 10));
        assertNull(bucket.lindex("mylist", -10));
    }

    @Test
    void lset() {
        bucket.rpush("mylist", "a", "b", "c");
        
        bucket.lset("mylist", 1, "X");
        assertEquals(List.of("a", "X", "c"), bucket.lrange("mylist", 0, -1));
        
        bucket.lset("mylist", -1, "Z");
        assertEquals(List.of("a", "X", "Z"), bucket.lrange("mylist", 0, -1));
        
        assertThrows(IndexOutOfBoundsException.class, () -> {
            bucket.lset("mylist", 10, "invalid");
        });
    }

    @Test
    void ltrim() {
        bucket.rpush("mylist", "a", "b", "c", "d", "e");
        
        bucket.ltrim("mylist", 1, 3);
        assertEquals(List.of("b", "c", "d"), bucket.lrange("mylist", 0, -1));
        
        bucket.ltrim("mylist", -2, -1);
        assertEquals(List.of("c", "d"), bucket.lrange("mylist", 0, -1));
    }

    @Test
    void linsert() {
        bucket.rpush("mylist", "a", "c", "d");
        
        assertEquals(4, bucket.linsert("mylist", "BEFORE", "c", "b"));
        assertEquals(List.of("a", "b", "c", "d"), bucket.lrange("mylist", 0, -1));
        
        assertEquals(5, bucket.linsert("mylist", "AFTER", "c", "X"));
        assertEquals(List.of("a", "b", "c", "X", "d"), bucket.lrange("mylist", 0, -1));
        
        // Pivot not found
        assertEquals(-1, bucket.linsert("mylist", "BEFORE", "nonexistent", "Y"));
    }

    @Test
    void rpoplpush() {
        bucket.rpush("src", "a", "b", "c");
        bucket.rpush("dest", "x", "y");
        
        String moved = bucket.rpoplpush("src", "dest");
        assertEquals("c", moved);
        assertEquals(List.of("c", "x", "y"), bucket.lrange("dest", 0, -1));
        assertEquals(List.of("a", "b"), bucket.lrange("src", 0, -1));
    }

    @Test
    void empty_list_operations() {
        assertNull(bucket.lpop("nonexistent"));
        assertNull(bucket.rpop("nonexistent"));
        assertEquals(0, bucket.llen("nonexistent"));
        assertEquals(List.of(), bucket.lrange("nonexistent", 0, -1));
        assertEquals(0, bucket.lrem("nonexistent", 0, "value"));
        assertNull(bucket.lindex("nonexistent", 0));
    }

    @Test
    void delete() {
        bucket.rpush("mylist", "a", "b", "c");
        assertTrue(bucket.delete("mylist"));
        assertEquals(0, bucket.llen("mylist"));
        assertFalse(bucket.delete("nonexistent"));
    }

    @Test
    void stats() {
        bucket.rpush("list1", "a", "b");
        bucket.rpush("list2", "x", "y", "z");
        
        var stats = bucket.stats();
        assertEquals("mylists", stats.get("name"));
        assertEquals(2L, stats.get("totalLists"));
        assertEquals(5L, stats.get("totalElements"));
    }
}

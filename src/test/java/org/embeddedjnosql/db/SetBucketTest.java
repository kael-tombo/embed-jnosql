package org.embeddedjnosql.db;

import org.embeddedjnosql.db.nosql.kv.SetBucket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SetBucketTest {

    private EmbedJNoSQL db;
    private SetBucket bucket;

    @BeforeEach
    void setUp() {
        db = EmbedJNoSQL.embed().build();
        bucket = db.setBucket("mysets");
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void sadd() {
        assertEquals(3, bucket.sadd("myset", "a", "b", "c"));
        assertEquals(0, bucket.sadd("myset", "a", "b")); // Already exist
        assertEquals(2, bucket.sadd("myset", "d", "e"));
        
        var members = bucket.smembers("myset");
        assertEquals(5, members.size());
        assertTrue(members.contains("a"));
        assertTrue(members.contains("e"));
    }

    @Test
    void srem() {
        bucket.sadd("myset", "a", "b", "c", "d");
        
        assertEquals(2, bucket.srem("myset", "a", "c"));
        assertEquals(1, bucket.srem("myset", "b"));
        assertEquals(0, bucket.srem("myset", "nonexistent"));
        
        assertEquals(Set.of("d"), bucket.smembers("myset"));
    }

    @Test
    void smembers() {
        assertTrue(bucket.smembers("nonexistent").isEmpty());
        
        bucket.sadd("myset", "x", "y", "z");
        var members = bucket.smembers("myset");
        assertEquals(3, members.size());
        assertTrue(members.contains("x"));
        assertTrue(members.contains("y"));
        assertTrue(members.contains("z"));
    }

    @Test
    void sismember() {
        bucket.sadd("myset", "a", "b", "c");
        
        assertTrue(bucket.sismember("myset", "a"));
        assertTrue(bucket.sismember("myset", "b"));
        assertFalse(bucket.sismember("myset", "x"));
        assertFalse(bucket.sismember("nonexistent", "a"));
    }

    @Test
    void scard() {
        assertEquals(0, bucket.scard("nonexistent"));
        
        bucket.sadd("myset", "a", "b", "c", "d");
        assertEquals(4, bucket.scard("myset"));
        
        bucket.srem("myset", "a", "b");
        assertEquals(2, bucket.scard("myset"));
    }

    @Test
    void spop_single() {
        bucket.sadd("myset", "a", "b", "c");
        
        String popped = bucket.spop("myset");
        assertNotNull(popped);
        assertTrue(Set.of("a", "b", "c").contains(popped));
        assertEquals(2, bucket.scard("myset"));
        
        // Pop until empty
        bucket.spop("myset");
        bucket.spop("myset");
        assertNull(bucket.spop("myset"));
    }

    @Test
    void spop_multiple() {
        bucket.sadd("myset", "a", "b", "c", "d", "e");
        
        var popped = bucket.spop("myset", 3);
        assertEquals(3, popped.size());
        assertEquals(2, bucket.scard("myset"));
        
        // Pop more than available
        var remaining = bucket.spop("myset", 10);
        assertEquals(2, remaining.size());
        assertEquals(0, bucket.scard("myset"));
    }

    @Test
    void srandmember() {
        bucket.sadd("myset", "a", "b", "c", "d");
        
        String member = bucket.srandmember("myset");
        assertNotNull(member);
        assertTrue(Set.of("a", "b", "c", "d").contains(member));
        assertEquals(4, bucket.scard("myset")); // Not removed
        
        var members = bucket.srandmember("myset", 3);
        assertEquals(3, members.size());
        assertEquals(4, bucket.scard("myset")); // Still not removed
    }

    @Test
    void smove() {
        bucket.sadd("src", "a", "b", "c");
        bucket.sadd("dest", "x", "y");
        
        assertTrue(bucket.smove("src", "dest", "b"));
        assertFalse(bucket.smove("src", "dest", "b")); // Already moved
        
        assertEquals(Set.of("a", "c"), bucket.smembers("src"));
        assertEquals(Set.of("x", "y", "b"), bucket.smembers("dest"));
    }

    @Test
    void sinter() {
        bucket.sadd("set1", "a", "b", "c", "d");
        bucket.sadd("set2", "b", "c", "e", "f");
        bucket.sadd("set3", "c", "g", "h");
        
        var inter2 = bucket.sinter("set1", "set2");
        assertEquals(Set.of("b", "c"), inter2);
        
        var inter3 = bucket.sinter("set1", "set2", "set3");
        assertEquals(Set.of("c"), inter3);
    }

    @Test
    void sunion() {
        bucket.sadd("set1", "a", "b", "c");
        bucket.sadd("set2", "c", "d", "e");
        
        var union = bucket.sunion("set1", "set2");
        assertEquals(5, union.size());
        assertTrue(union.contains("a"));
        assertTrue(union.contains("e"));
    }

    @Test
    void sdiff() {
        bucket.sadd("set1", "a", "b", "c", "d");
        bucket.sadd("set2", "c", "d", "e");
        
        var diff = bucket.sdiff("set1", "set2");
        assertEquals(Set.of("a", "b"), diff);
    }

    @Test
    void delete() {
        bucket.sadd("myset", "a", "b", "c");
        assertTrue(bucket.delete("myset"));
        assertEquals(0, bucket.scard("myset"));
        assertFalse(bucket.delete("nonexistent"));
    }

    @Test
    void clear() {
        bucket.sadd("set1", "a", "b");
        bucket.sadd("set2", "x", "y", "z");
        
        bucket.clear();
        assertEquals(0, bucket.scard("set1"));
        assertEquals(0, bucket.scard("set2"));
    }

    @Test
    void stats() {
        bucket.sadd("set1", "a", "b");
        bucket.sadd("set2", "x", "y", "z");
        
        var stats = bucket.stats();
        assertEquals("mysets", stats.get("name"));
        assertEquals(2L, stats.get("totalSets"));
        assertEquals(5L, stats.get("totalMembers"));
    }

    @Test
    void contains_alias() {
        bucket.sadd("myset", "a", "b");
        assertTrue(bucket.contains("myset", "a"));
        assertFalse(bucket.contains("myset", "x"));
    }

    @Test
    void size_alias() {
        bucket.sadd("myset", "a", "b", "c");
        assertEquals(3, bucket.size("myset"));
    }
}

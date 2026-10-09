package org.embeddedjnosql.db.demo.demos.redisstylehashes;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.kv.HashBucket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RedisHashDemoTest {

    private EmbedJNoSQL db;

    @BeforeEach
    void setUp() { db = EmbedJNoSQL.inMemory(); }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    @Test
    @DisplayName("hset/hgetall/hdel round-trip an object")
    void runDemo() {
        var r = RedisHashDemo.runDemo(db);
        assertEquals(3, r.newFields());
        assertTrue(r.cartCleared());
        assertEquals("platinum", r.profile().get("tier"));
        assertFalse(r.afterDelete().containsKey("email"));
    }

    @Test
    @DisplayName("hset returns 1 for new fields and 0 for updates; hexists/hlen behave")
    void fieldLevelSemantics() {
        var hashes = db.hashBucket("hash_sem");
        assertEquals(1, hashes.hset("h", "f", "v1"));
        assertEquals(0, hashes.hset("h", "f", "v2"));
        assertEquals("v2", hashes.hget("h", "f"));
        assertTrue(hashes.hexists("h", "f"));
        assertEquals(1, hashes.hlen("h"));
        assertTrue(hashes.hkeys("h").contains("f"));
        assertTrue(hashes.hvals("h").contains("v2"));
    }
}

package org.embeddedjnosql.db.demo.demos.redisstylelists;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.kv.ListBucket;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RedisListDemoTest {

    private EmbedJNoSQL db;

    @BeforeEach
    void setUp() { db = EmbedJNoSQL.inMemory(); }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    @Test
    @DisplayName("FIFO queue, retry hand-off, negative indexing and lrem all behave")
    void runDemo() {
        var r = RedisListDemo.runDemo(db);
        assertEquals(List.of("job-a", "job-b"), r.firstTwo());
        assertEquals(1, r.queueDepth());
        assertEquals("job-d", r.handoffItem());
        assertEquals(2, r.remaining());
        assertEquals("job-c", r.peekedTail());
    }

    @Test
    @DisplayName("lpush builds a stack (LIFO) and lrange supports negative ranges")
    void stackAndRanges() {
        var lists = db.listBucket("stack_demo");
        lists.lpush("undo", "op1");
        lists.lpush("undo", "op2");
        lists.lpush("undo", "op3");
        // Last pushed pops first
        assertEquals("op3", lists.lpop("undo"));
        // lrange with negative end: last two elements
        assertEquals(List.of("op2", "op1"), lists.lrange("undo", 0, -1));
        assertEquals(2, lists.llen("undo"));
    }
}

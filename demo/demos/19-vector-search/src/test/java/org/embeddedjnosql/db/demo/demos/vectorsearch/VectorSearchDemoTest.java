package org.embeddedjnosql.db.demo.demos.vectorsearch;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.index.hnsw.VectorIndex;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VectorSearchDemoTest {

    private EmbedJNoSQL db;

    @BeforeEach
    void setUp() { db = EmbedJNoSQL.inMemory(); }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    @Test
    @DisplayName("nearest neighbor is correct and removal shrinks results")
    void runDemo() {
        var r = VectorSearchDemo.runDemo(db);
        assertEquals("a", r.topMatch());
        assertEquals(2, r.indexSize());
        assertTrue(r.removedFromIndex());
        assertTrue(r.secondMatchSane());
    }

    @Test
    @DisplayName("dimension mismatch is rejected")
    void dimensionMismatch() {
        var index = new VectorIndex(4, 4, 8);
        index.add("x", new float[]{1, 0, 0, 0});
        assertThrows(Exception.class, () -> index.add("y", new float[]{1, 0}));
    }
}

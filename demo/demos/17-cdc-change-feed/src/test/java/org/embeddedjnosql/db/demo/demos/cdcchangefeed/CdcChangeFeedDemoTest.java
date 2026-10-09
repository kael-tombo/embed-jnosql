package org.embeddedjnosql.db.demo.demos.cdcchangefeed;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.core.cdc.CDCEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CdcChangeFeedDemoTest {

    private EmbedJNoSQL db;

    @BeforeEach
    void setUp() { db = EmbedJNoSQL.inMemory(); }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    @Test
    @DisplayName("insert, update and delete each produce one CDC event")
    void runDemo() {
        var r = CdcChangeFeedDemo.runDemo(db);
        assertEquals(1, r.inserts());
        assertEquals(1, r.updates());
        assertEquals(1, r.deletes());
        assertTrue(r.logSize() >= 3);
        assertNotNull(r.lastJson());
        assertTrue(r.lastJson().contains("DELETE") || r.lastJson().contains("delete"));
    }

    @Test
    @DisplayName("CDC survives eventBus.clear() because it rides the system channel")
    void cdcSurvivesUserClear() {
        var cdc = db.cdcManager();
        AtomicInteger hits = new AtomicInteger();
        cdc.processor().subscribe(e -> hits.incrementAndGet());
        db.eventBus().clear(); // user listener cleanup
        var col = db.documentCollection("cdc_survives");
        col.insert(new org.embeddedjnosql.db.nosql.document.Document().id("s1"));
        assertTrue(hits.get() >= 1, "CDC must still receive events after eventBus.clear()");
        assertTrue(cdc.processor().isEnabled());
    }
}

package org.embeddedjnosql.db.demo.demos.widecolumnfamilies;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.column.ColumnFamily;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WideColumnDemoTest {

    private EmbedJNoSQL db;

    @BeforeEach
    void setUp() { db = EmbedJNoSQL.inMemory(); }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    @Test
    @DisplayName("sparse rows, pagination, prefix scans and column TTL behave")
    void runDemo() {
        var r = WideColumnDemo.runDemo(db);
        assertEquals(1, r.firstPage().size());
        assertTrue(r.pageCount() >= 3);
        assertTrue(r.cleanedUp() >= 0);
        assertTrue(r.tempColumns().containsKey("temp_max"));
    }

    @Test
    @DisplayName("row slices and per-row stats expose family shape")
    void slicesAndStats() {
        var fam = db.columnFamily("slice_demo");
        fam.put("row-a", "c1", 1);
        fam.put("row-b", "c1", 2);
        fam.put("row-c", "c1", 3);
        var slices = fam.getRowSlice("row-a", "row-c", 10);
        assertEquals(3, slices.size());
        var stats = fam.getColumnStats();
        assertEquals(3, ((Number) stats.get("rowCount")).intValue());
    }
}

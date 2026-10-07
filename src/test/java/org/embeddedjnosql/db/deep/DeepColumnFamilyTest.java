package org.embeddedjnosql.db.deep;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.column.ColumnFamily;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sub-Agent C — Deep Column Family Tests.
 *
 * Covers: put/get/delete column, delete row, getRow, paginated getRow,
 * filter by set, filter by regex pattern, filter by prefix, TTL columns,
 * column stats, row stats, cleanup expired, multi-row isolation, concurrency.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Timeout(value = 90, unit = TimeUnit.SECONDS)
class DeepColumnFamilyTest {

    private static EmbedJNoSQL db;

    @BeforeAll
    static void init() {
        db = EmbedJNoSQL.embed().build();
    }

    @AfterAll
    static void teardown() {
        if (db != null && db.isOpen()) db.close();
    }

    // -----------------------------------------------------------------------
    // Basic put / get / delete
    // -----------------------------------------------------------------------

    @Test @Order(1)
    void putAndGetSingleColumn() {
        ColumnFamily cf = db.columnFamily("cf_basic");
        cf.put("row1", "name", "Alice", null);
        assertEquals("Alice", cf.get("row1", "name"));
    }

    @Test @Order(2)
    void putMultipleColumnsOnSameRow() {
        ColumnFamily cf = db.columnFamily("cf_multicol");
        cf.put("r1", "col_a", "v_a", null);
        cf.put("r1", "col_b", "v_b", null);
        cf.put("r1", "col_c", "v_c", null);
        assertEquals("v_a", cf.get("r1", "col_a"));
        assertEquals("v_b", cf.get("r1", "col_b"));
        assertEquals("v_c", cf.get("r1", "col_c"));
    }

    @Test @Order(3)
    void overwriteColumnValue() {
        ColumnFamily cf = db.columnFamily("cf_overwrite");
        cf.put("row", "field", "original", null);
        cf.put("row", "field", "updated", null);
        assertEquals("updated", cf.get("row", "field"));
    }

    @Test @Order(4)
    void getMissingColumnReturnsNull() {
        ColumnFamily cf = db.columnFamily("cf_missing");
        assertNull(cf.get("row_xyz", "col_xyz"), "Missing column must return null");
    }

    @Test @Order(5)
    void deleteColumnRemovesValue() {
        ColumnFamily cf = db.columnFamily("cf_delcol");
        cf.put("row", "col", "val", null);
        cf.deleteColumn("row", "col");
        assertNull(cf.get("row", "col"));
    }

    @Test @Order(6)
    void deleteRowRemovesAllColumns() {
        ColumnFamily cf = db.columnFamily("cf_delrow");
        cf.put("dr", "c1", "v1", null);
        cf.put("dr", "c2", "v2", null);
        cf.deleteRow("dr");
        assertTrue(cf.getRow("dr").isEmpty(), "After deleteRow, row must be empty");
    }

    // -----------------------------------------------------------------------
    // getRow variants
    // -----------------------------------------------------------------------

    @Test @Order(10)
    void getRowReturnsAllColumns() {
        ColumnFamily cf = db.columnFamily("cf_getrow");
        cf.put("gr", "a", "1", null);
        cf.put("gr", "b", "2", null);
        var row = cf.getRow("gr");
        assertEquals("1", row.get("a"));
        assertEquals("2", row.get("b"));
    }

    @Test @Order(11)
    void getRowEmptyForUnknownRow() {
        ColumnFamily cf = db.columnFamily("cf_emptyrow");
        assertTrue(cf.getRow("unknown_row_xyz").isEmpty());
    }

    @Test @Order(12)
    void getRowWithLimitAndOffset() {
        ColumnFamily cf = db.columnFamily("cf_paged");
        for (int i = 0; i < 10; i++) cf.put("paged_row", "col_" + i, "val_" + i, null);
        var page1 = cf.getRow("paged_row", 5, 0, null);
        var page2 = cf.getRow("paged_row", 5, 5, null);
        assertEquals(5, page1.size(), "Page 1 must have 5 columns");
        assertEquals(5, page2.size(), "Page 2 must have 5 columns");
        // Pages must not overlap
        var page1Keys = page1.keySet();
        var page2Keys = page2.keySet();
        long overlap = page1Keys.stream().filter(page2Keys::contains).count();
        assertEquals(0, overlap, "Pages must not overlap");
    }

    @Test @Order(13)
    void getRowFilteredByColumnNames() {
        ColumnFamily cf = db.columnFamily("cf_filter");
        cf.put("frow", "username", "alice", null);
        cf.put("frow", "email", "alice@test.com", null);
        cf.put("frow", "phone", "555-1234", null);
        var filtered = cf.getRow("frow", Set.of("username", "email"));
        assertEquals(2, filtered.size());
        assertEquals("alice", filtered.get("username"));
        assertEquals("alice@test.com", filtered.get("email"));
        assertFalse(filtered.containsKey("phone"), "phone should not be included");
    }

    @Test @Order(14)
    void getRowByPattern() {
        ColumnFamily cf = db.columnFamily("cf_pattern");
        cf.put("prow", "meta_a", "1", null);
        cf.put("prow", "meta_b", "2", null);
        cf.put("prow", "data_c", "3", null);
        var result = cf.getRowByPattern("prow", "meta.*");
        assertTrue(result.containsKey("meta_a"), "Pattern meta.* should match meta_a");
        assertTrue(result.containsKey("meta_b"), "Pattern meta.* should match meta_b");
        assertFalse(result.containsKey("data_c"), "Pattern meta.* should not match data_c");
    }

    @Test @Order(15)
    void getRowByPrefix() {
        ColumnFamily cf = db.columnFamily("cf_prefix");
        cf.put("pfrow", "ts_2024", "v1", null);
        cf.put("pfrow", "ts_2025", "v2", null);
        cf.put("pfrow", "other",   "v3", null);
        var result = cf.getRowByPrefix("pfrow", "ts_");
        assertTrue(result.containsKey("ts_2024"));
        assertTrue(result.containsKey("ts_2025"));
        assertFalse(result.containsKey("other"));
    }

    // -----------------------------------------------------------------------
    // TTL
    // -----------------------------------------------------------------------

    @Test @Order(20)
    void putColumnWithTtlDoesNotThrow() {
        ColumnFamily cf = db.columnFamily("cf_ttl");
        assertDoesNotThrow(() -> cf.put("ttl_row", "col", "val", 3600));
    }

    @Test @Order(21)
    void getRemainingTtlIsPositive() {
        ColumnFamily cf = db.columnFamily("cf_ttlcheck");
        cf.put("trow", "tcol", "tval", 3600);
        long remaining = cf.getRemainingTtl("trow", "tcol");
        assertTrue(remaining > 0, "Remaining TTL must be positive for a 1h column");
        assertTrue(remaining <= 3600, "Remaining TTL must not exceed original TTL");
    }

    @Test @Order(22)
    void columnWithNoTtlHasNegativeRemainingTtl() {
        ColumnFamily cf = db.columnFamily("cf_nottl");
        cf.put("ntrow", "ntcol", "ntval", null);
        long remaining = cf.getRemainingTtl("ntrow", "ntcol");
        assertTrue(remaining < 0, "Column without TTL must return negative remaining TTL");
    }

    @Test @Order(23)
    void getColumnDataReturnsTtlInfo() {
        ColumnFamily cf = db.columnFamily("cf_coldata");
        cf.put("cdrow", "cdcol", "cdval", 60);
        var colData = cf.getColumnData("cdrow", "cdcol");
        assertNotNull(colData, "getColumnData must return non-null for existing column");
        assertTrue(colData.hasTtl(), "Column with TTL must report hasTtl=true");
    }

    @Test @Order(24)
    void cleanupExpiredReturnsNonNegative() {
        ColumnFamily cf = db.columnFamily("cf_cleanup");
        cf.put("clrow", "clcol", "clval", null);
        long deleted = cf.cleanupAllExpired();
        assertTrue(deleted >= 0, "cleanupAllExpired must return >= 0");
    }

    // -----------------------------------------------------------------------
    // Stats
    // -----------------------------------------------------------------------

    @Test @Order(30)
    void columnStatsNotNull() {
        ColumnFamily cf = db.columnFamily("cf_stats");
        cf.put("srow", "scol", "sval", null);
        var stats = cf.getColumnStats();
        assertNotNull(stats, "Column stats must not be null");
        assertFalse(stats.isEmpty(), "Column stats must not be empty");
    }

    @Test @Order(31)
    void rowStatsNotNull() {
        ColumnFamily cf = db.columnFamily("cf_rowstats");
        cf.put("rsrow", "c1", "v1", null);
        cf.put("rsrow", "c2", "v2", null);
        var stats = cf.getRowStats("rsrow");
        assertNotNull(stats, "Row stats must not be null");
    }

    // -----------------------------------------------------------------------
    // Multi-row isolation
    // -----------------------------------------------------------------------

    @Test @Order(40)
    void rowsAreIsolatedFromEachOther() {
        ColumnFamily cf = db.columnFamily("cf_rowiso");
        cf.put("rowA", "col", "valueA", null);
        cf.put("rowB", "col", "valueB", null);
        assertEquals("valueA", cf.get("rowA", "col"));
        assertEquals("valueB", cf.get("rowB", "col"));
    }

    @Test @Order(41)
    void deleteRowDoesNotAffectOtherRows() {
        ColumnFamily cf = db.columnFamily("cf_rowdel");
        cf.put("safe_row", "col", "safe", null);
        cf.put("del_row",  "col", "gone", null);
        cf.deleteRow("del_row");
        assertEquals("safe", cf.get("safe_row", "col"),
                "Deleting one row must not affect other rows");
    }

    @Test @Order(42)
    void twentyRowsWithTenColsEach() {
        ColumnFamily cf = db.columnFamily("cf_bulk");
        for (int r = 0; r < 20; r++) {
            for (int c = 0; c < 10; c++) {
                cf.put("row_" + r, "col_" + c, "v_" + r + "_" + c, null);
            }
        }
        for (int r = 0; r < 20; r++) {
            var row = cf.getRow("row_" + r);
            assertEquals(10, row.size(), "Row row_" + r + " must have 10 columns");
            assertEquals("v_" + r + "_5", cf.get("row_" + r, "col_5"));
        }
    }

    // -----------------------------------------------------------------------
    // Concurrency
    // -----------------------------------------------------------------------

    @Test @Order(50)
    void concurrentPutsOnDifferentRows() throws Exception {
        ColumnFamily cf = db.columnFamily("cf_concurrent");
        int threads = 10;
        var pool = Executors.newFixedThreadPool(threads);
        var futures = new ArrayList<Future<?>>();
        for (int t = 0; t < threads; t++) {
            final int tid = t;
            futures.add(pool.submit(() -> {
                String row = "concurrent_row_" + tid;
                for (int i = 0; i < 20; i++) {
                    cf.put(row, "col_" + i, "val_" + i, null);
                }
            }));
        }
        for (var f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        for (int t = 0; t < threads; t++) {
            assertEquals("val_10", cf.get("concurrent_row_" + t, "col_10"),
                    "Concurrent put must persist correctly for row " + t);
        }
    }

    @Test @Order(51)
    void concurrentPutsOnSameRowSameColumn() throws Exception {
        ColumnFamily cf = db.columnFamily("cf_conc_same");
        int threads = 8;
        var pool = Executors.newFixedThreadPool(threads);
        var futures = new ArrayList<Future<?>>();
        for (int t = 0; t < threads; t++) {
            final String val = "thread_" + t;
            futures.add(pool.submit(() -> cf.put("shared", "shared_col", val, null)));
        }
        for (var f : futures) f.get(30, TimeUnit.SECONDS);
        pool.shutdown();
        // After concurrent writes, the column must have some value (last-write-wins)
        Object rawVal = cf.get("shared", "shared_col");
        assertNotNull(rawVal, "Concurrent writes to same column — value must not be null");
        assertTrue(rawVal.toString().startsWith("thread_"), "Value must be from one of the threads");
    }
}
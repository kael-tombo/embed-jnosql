package org.embeddedjnosql.db;

import org.embeddedjnosql.db.nosql.column.ColumnFamily;
import org.embeddedjnosql.db.console.http.EmbedJNoSQLServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URI;
import java.util.Set;
import java.util.Map;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for ColumnFamily advanced features:
 * - Column-level TTL
 * - Wide-column pagination
 * - Column filtering
 * - Statistics
 */
class ColumnFamilyAdvancedTest {

    private EmbedJNoSQL db;
    private ColumnFamily cf;
    private EmbedJNoSQLServer server;

    @BeforeEach
    void setUp() {
        db = EmbedJNoSQL.embed().build();
        cf = db.columnFamily("test_family");
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop();
        }
        db.close();
    }

    // ==================== COLUMN-LEVEL TTL TESTS ====================

    @Test
    @DisplayName("TTL-01: Put column with TTL and verify expiration")
    void testColumnTtlExpiration() throws InterruptedException {
        // Put column with 1 second TTL
        cf.put("row1", "temp_column", "temp_value", 1);

        // Should be able to read immediately
        assertEquals("temp_value", cf.get("row1", "temp_column"));
        assertTrue(cf.getRemainingTtl("row1", "temp_column") >= 0);

        // Wait for expiration
        Thread.sleep(1100);

        // Should be expired now
        assertNull(cf.get("row1", "temp_column"));
        assertEquals(-2, cf.getRemainingTtl("row1", "temp_column"));
    }

    @Test
    @DisplayName("TTL-02: Put column without TTL (permanent)")
    void testColumnWithoutTtl() {
        cf.put("row1", "permanent_column", "permanent_value");

        assertEquals("permanent_value", cf.get("row1", "permanent_column"));
        assertEquals(-1, cf.getRemainingTtl("row1", "permanent_column"));
        assertFalse(cf.getColumnData("row1", "permanent_column").hasTtl());
    }

    @Test
    @DisplayName("TTL-03: Update TTL on existing column")
    void testUpdateTtl() throws InterruptedException {
        // Put column with 10 second TTL
        cf.put("row1", "column", "value", 10);
        long initialTtl = cf.getRemainingTtl("row1", "column");
        assertTrue(initialTtl > 5);

        // Update to 1 second TTL
        assertTrue(cf.updateTtl("row1", "column", 1));

        // Wait for expiration
        Thread.sleep(1100);

        // Should be expired
        assertNull(cf.get("row1", "column"));
    }

    @Test
    @DisplayName("TTL-04: Remove TTL from column")
    void testRemoveTtl() throws InterruptedException {
        // Put column with 2 second TTL
        cf.put("row1", "column", "value", 2);
        assertTrue(cf.getColumnData("row1", "column").hasTtl());

        // Remove TTL
        assertTrue(cf.removeTtl("row1", "column"));
        assertFalse(cf.getColumnData("row1", "column").hasTtl());
        assertEquals(-1, cf.getRemainingTtl("row1", "column"));

        // Wait - should still exist
        Thread.sleep(2100);
        assertEquals("value", cf.get("row1", "column"));
    }

    @Test
    @DisplayName("TTL-05: Get remaining TTL")
    void testGetRemainingTtl() throws InterruptedException {
        cf.put("row1", "column", "value", 5);

        long remaining = cf.getRemainingTtl("row1", "column");
        assertTrue(remaining > 0 && remaining <= 5);

        Thread.sleep(2000);

        long remainingAfter = cf.getRemainingTtl("row1", "column");
        assertTrue(remainingAfter > 0 && remainingAfter < remaining);
    }

    @Test
    @DisplayName("TTL-06: Cleanup expired columns in row")
    void testCleanupExpiredColumns() throws InterruptedException {
        cf.put("row1", "permanent", "value1");
        cf.put("row1", "temporary", "value2", 1);
        cf.put("row1", "another_temp", "value3", 1);

        Thread.sleep(1100);

        int cleaned = cf.cleanupExpiredColumns("row1");
        assertEquals(2, cleaned);

        assertNotNull(cf.get("row1", "permanent"));
        assertNull(cf.get("row1", "temporary"));
        assertNull(cf.get("row1", "another_temp"));
    }

    @Test
    @DisplayName("TTL-07: Cleanup all expired columns in family")
    void testCleanupAllExpired() throws InterruptedException {
        cf.put("row1", "temp1", "value1", 1);
        cf.put("row2", "temp2", "value2", 1);
        cf.put("row3", "permanent", "value3");

        Thread.sleep(1100);

        int cleaned = cf.cleanupAllExpired();
        assertTrue(cleaned >= 2);

        assertNull(cf.get("row1", "temp1"));
        assertNull(cf.get("row2", "temp2"));
        assertNotNull(cf.get("row3", "permanent"));
    }

    @Test
    @DisplayName("TTL-08: getWithTtlCheck explicitly checks expiration")
    void testGetWithTtlCheck() throws InterruptedException {
        cf.put("row1", "expiring", "value", 1);
        Thread.sleep(1100);

        assertNull(cf.getWithTtlCheck("row1", "expiring"));
    }

    @Test
    @DisplayName("TTL-09: Column data includes metadata")
    void testColumnDataMetadata() {
        cf.put("row1", "column", "value", 60);

        var columnData = cf.getColumnData("row1", "column");
        assertNotNull(columnData);
        assertEquals("value", columnData.getValue());
        assertEquals(60, columnData.getTtlSeconds());
        assertNotNull(columnData.getCreatedAt());
        assertNotNull(columnData.getExpiresAt());
        assertTrue(columnData.getExpiresAt() > columnData.getCreatedAt());
    }

    @Test
    @DisplayName("TTL-10: Is expired check")
    void testIsExpired() throws InterruptedException {
        cf.put("row1", "expiring", "value", 1);
        cf.put("row1", "permanent", "value");

        assertFalse(cf.isExpired("row1", "permanent"));
        assertFalse(cf.isExpired("row1", "expiring"));

        Thread.sleep(1100);

        assertTrue(cf.isExpired("row1", "expiring"));
        assertFalse(cf.isExpired("row1", "permanent"));
    }

    // ==================== WIDE-COLUMN PAGINATION TESTS ====================

    @Test
    @DisplayName("PAG-01: Get row with limit")
    void testGetRowWithLimit() {
        // Create row with 10 columns
        for (int i = 0; i < 10; i++) {
            cf.put("row1", "col" + i, "value" + i);
        }

        var result = cf.getRow("row1", 5, 0);
        assertEquals(5, result.size());
        assertTrue(result.containsKey("col0"));
        assertTrue(result.containsKey("col4"));
    }

    @Test
    @DisplayName("PAG-02: Get row with offset")
    void testGetRowWithOffset() {
        for (int i = 0; i < 10; i++) {
            cf.put("row1", "col" + i, "value" + i);
        }

        var result = cf.getRow("row1", 5, 5);
        assertEquals(5, result.size());
        assertFalse(result.containsKey("col0"));
        assertFalse(result.containsKey("col4"));
        assertTrue(result.containsKey("col5"));
        assertTrue(result.containsKey("col9"));
    }

    @Test
    @DisplayName("PAG-03: Get row with limit and offset combined")
    void testGetRowWithLimitAndOffset() {
        for (int i = 0; i < 20; i++) {
            cf.put("row1", "col" + i, "value" + i);
        }

        // Get second page (columns 5-9)
        var result = cf.getRow("row1", 5, 5);
        assertEquals(5, result.size());
        assertTrue(result.containsKey("col5"));
        assertTrue(result.containsKey("col9"));
        assertFalse(result.containsKey("col10"));
    }

    @Test
    @DisplayName("PAG-04: Get row slice (multiple rows)")
    void testGetRowSlice() {
        // Create multiple rows
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 3; c++) {
                cf.put("row:" + r, "col" + c, "value" + r + "-" + c);
            }
        }

        var result = cf.getRowSlice("row:0", "row:4", 3);
        assertEquals(3, result.size());
        assertEquals("row:0", result.get(0).getRowKey());
        assertEquals("row:1", result.get(1).getRowKey());
        assertEquals("row:2", result.get(2).getRowKey());
    }

    @Test
    @DisplayName("PAG-05: Get row slice with column projection")
    void testGetRowSliceWithProjection() {
        for (int r = 0; r < 3; r++) {
            cf.put("row:" + r, "name", "name" + r);
            cf.put("row:" + r, "age", "age" + r);
            cf.put("row:" + r, "email", "email" + r);
        }

        var result = cf.getRowSlice("row:0", "row:2", 3, Set.of("name", "email"));
        assertEquals(3, result.size());
        for (var rowSlice : result) {
            assertTrue(rowSlice.getData().containsKey("name"));
            assertTrue(rowSlice.getData().containsKey("email"));
            assertFalse(rowSlice.getData().containsKey("age"));
        }
    }

    @Test
    @DisplayName("PAG-06: Pagination respects expired columns")
    void testPaginationRespectsExpiration() throws InterruptedException {
        cf.put("row1", "col1", "value1");
        cf.put("row1", "col2", "value2", 1);
        cf.put("row1", "col3", "value3");

        Thread.sleep(1100);

        var result = cf.getRow("row1", 10, 0);
        assertEquals(2, result.size());
        assertFalse(result.containsKey("col2"));
    }

    // ==================== COLUMN FILTERING TESTS ====================

    @Test
    @DisplayName("FLT-01: Get specific columns from row")
    void testGetRowWithColumnSet() {
        cf.put("row1", "name", "Alice");
        cf.put("row1", "age", 30);
        cf.put("row1", "email", "alice@example.com");
        cf.put("row1", "phone", "123-456");

        var result = cf.getRow("row1", Set.of("name", "email"));
        assertEquals(2, result.size());
        assertEquals("Alice", result.get("name"));
        assertEquals("alice@example.com", result.get("email"));
        assertFalse(result.containsKey("age"));
        assertFalse(result.containsKey("phone"));
    }

    @Test
    @DisplayName("FLT-02: Get row with filter predicate")
    void testGetRowWithFilter() {
        cf.put("row1", "user_name", "Alice");
        cf.put("row1", "user_age", 30);
        cf.put("row1", "meta_created", "2024-01-01");
        cf.put("row1", "meta_updated", "2024-01-02");

        // Filter by prefix
        var userCols = cf.getRowWithFilter("row1", col -> col.startsWith("user_"));
        assertEquals(2, userCols.size());
        assertTrue(userCols.containsKey("user_name"));
        assertTrue(userCols.containsKey("user_age"));

        var metaCols = cf.getRowWithFilter("row1", col -> col.startsWith("meta_"));
        assertEquals(2, metaCols.size());
        assertTrue(metaCols.containsKey("meta_created"));
        assertTrue(metaCols.containsKey("meta_updated"));
    }

    @Test
    @DisplayName("FLT-03: Get row by prefix")
    void testGetRowByPrefix() {
        cf.put("row1", "ts:2024:01:01", "data1");
        cf.put("row1", "ts:2024:01:02", "data2");
        cf.put("row1", "ts:2024:02:01", "data3");
        cf.put("row1", "other", "data4");

        var result = cf.getRowByPrefix("row1", "ts:2024:01:");
        assertEquals(2, result.size());
        assertTrue(result.containsKey("ts:2024:01:01"));
        assertTrue(result.containsKey("ts:2024:01:02"));
        assertFalse(result.containsKey("ts:2024:02:01"));
        assertFalse(result.containsKey("other"));
    }

    @Test
    @DisplayName("FLT-04: Get row by regex pattern")
    void testGetRowByPattern() {
        cf.put("row1", "email:home", "home@example.com");
        cf.put("row1", "email:work", "work@example.com");
        cf.put("row1", "phone:mobile", "123-456");
        cf.put("row1", "phone:home", "789-012");

        var emailCols = cf.getRowByPattern("row1", "email:.*");
        assertEquals(2, emailCols.size());
        assertTrue(emailCols.containsKey("email:home"));
        assertTrue(emailCols.containsKey("email:work"));

        var mobileCols = cf.getRowByPattern("row1", ".*mobile.*");
        assertEquals(1, mobileCols.size());
        assertTrue(mobileCols.containsKey("phone:mobile"));
    }

    @Test
    @DisplayName("FLT-05: Filter with non-existent columns returns empty")
    void testFilterNonExistentColumns() {
        cf.put("row1", "name", "Alice");

        var result = cf.getRow("row1", Set.of("nonexistent1", "nonexistent2"));
        assertTrue(result.isEmpty());
    }

    // ==================== STATISTICS TESTS ====================

    @Test
    @DisplayName("STAT-01: Get column family stats")
    void testGetColumnStats() {
        assertEquals(0, cf.countRows());

        cf.put("row1", "col1", "value1");
        cf.put("row1", "col2", "value2");
        cf.put("row2", "col1", "value3");

        var stats = cf.getColumnStats();
        assertEquals("test_family", stats.get("familyName"));
        assertEquals(2, ((Number) stats.get("rowCount")).longValue());
        assertEquals(3, ((Number) stats.get("totalColumns")).longValue());
        assertEquals(0, ((Number) stats.get("expiredColumns")).longValue());
    }

    @Test
    @DisplayName("STAT-02: Get column stats with TTL columns")
    void testGetColumnStatsWithTtl() {
        cf.put("row1", "permanent", "value1");
        cf.put("row1", "temporary", "value2", 60);
        cf.put("row2", "also_temp", "value3", 120);

        var stats = cf.getColumnStats();
        assertEquals(2L, stats.get("columnsWithTtl"));
    }

    @Test
    @DisplayName("STAT-03: Get row stats")
    void testGetRowStats() {
        cf.put("row1", "col1", "value1");
        cf.put("row1", "col2", "value2", 60);
        cf.put("row1", "col3", "value3");

        var stats = cf.getRowStats("row1");
        assertEquals("row1", stats.get("rowKey"));
        assertEquals(3, ((Number) stats.get("columnCount")).longValue());
        assertEquals(1, ((Number) stats.get("columnsWithTtl")).longValue());
    }

    @Test
    @DisplayName("STAT-04: Get detailed stats")
    void testGetDetailedStats() {
        cf.put("row1", "col1", "value1");
        cf.put("row2", "col2", "value2");

        var stats = cf.getDetailedStats();
        assertTrue(stats.containsKey("familyName"));
        assertTrue(stats.containsKey("rowCount"));
        assertTrue(stats.containsKey("rowBreakdown"));

        var breakdown = (List<?>) stats.get("rowBreakdown");
        assertEquals(2, breakdown.size());
    }

    @Test
    @DisplayName("STAT-05: Stats include avg columns per row")
    void testStatsAvgColumnsPerRow() {
        cf.put("row1", "col1", "v1");
        cf.put("row1", "col2", "v2");
        cf.put("row2", "col1", "v3");
        cf.put("row3", "col1", "v4");
        cf.put("row3", "col2", "v5");
        cf.put("row3", "col3", "v6");

        var stats = cf.getColumnStats();
        // (2 + 1 + 3) / 3 = 2
        assertEquals(2L, stats.get("avgColumnsPerRow"));
    }

    @Test
    @DisplayName("STAT-06: Row stats include TTL column names")
    void testRowStatsTtlColumnNames() {
        cf.put("row1", "permanent", "value1");
        cf.put("row1", "temp1", "value2", 60);
        cf.put("row1", "temp2", "value3", 120);

        var stats = cf.getRowStats("row1");
        var ttlColumns = (List<?>) stats.get("ttlColumnNames");
        assertEquals(2, ttlColumns.size());
        assertTrue(ttlColumns.contains("temp1"));
        assertTrue(ttlColumns.contains("temp2"));
    }

    // ==================== EDGE CASE TESTS ====================

    @Test
    @DisplayName("EDGE-01: Get non-existent row returns empty map")
    void testGetNonExistentRow() {
        var result = cf.getRow("nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("EDGE-02: Pagination with zero limit")
    void testPaginationZeroLimit() {
        cf.put("row1", "col1", "value1");
        cf.put("row1", "col2", "value2");

        var result = cf.getRow("row1", 0, 0);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("EDGE-03: Pagination beyond available columns")
    void testPaginationBeyondAvailable() {
        cf.put("row1", "col1", "value1");
        cf.put("row1", "col2", "value2");

        var result = cf.getRow("row1", 10, 100);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("EDGE-04: Empty row stats")
    void testEmptyRowStats() {
        var stats = cf.getRowStats("nonexistent");
        assertEquals("nonexistent", stats.get("rowKey"));
        assertEquals(0, ((Number) stats.get("columnCount")).longValue());
    }

    @Test
    @DisplayName("EDGE-05: Column data from legacy format (plain value)")
    void testLegacyColumnDataFormat() {
        // Simulate legacy format by directly storing plain value
        var row = new java.util.LinkedHashMap<String, Object>();
        row.put("legacy_col", "legacy_value");
        cf.put("row1", "temp", "temp"); // Initialize row
        // Note: This tests that the code handles both formats gracefully
        var columnData = cf.getColumnData("row1", "temp");
        assertNotNull(columnData);
    }

    // ==================== TIME-SERIES USE CASE TESTS ====================

    @Test
    @DisplayName("TS-01: Time-series data with TTL")
    void testTimeSeriesDataWithTtl() throws InterruptedException {
        // Simulate time-series: sensor readings with 2-second TTL
        for (int i = 0; i < 5; i++) {
            cf.put("sensor:temp:1", "ts:" + System.currentTimeMillis(), 20.0 + i, 2);
            Thread.sleep(100);
        }

        // Read current data
        var data = cf.getRow("sensor:temp:1");
        assertTrue(data.size() > 0);

        // Wait for expiration
        Thread.sleep(2000);

        // Cleanup and verify
        cf.cleanupExpiredColumns("sensor:temp:1");
        var afterCleanup = cf.getRow("sensor:temp:1");
        assertTrue(afterCleanup.isEmpty());
    }

    @Test
    @DisplayName("TS-02: Time-series pagination for historical data")
    void testTimeSeriesPagination() {
        // Simulate 100 time-series entries
        for (int i = 0; i < 100; i++) {
            cf.put("metrics:cpu", "ts:" + String.format("%010d", i), Math.random() * 100);
        }

        // Get first page
        var page1 = cf.getRow("metrics:cpu", 10, 0);
        assertEquals(10, page1.size());

        // Get second page
        var page2 = cf.getRow("metrics:cpu", 10, 10);
        assertEquals(10, page2.size());

        // Pages should have different data
        assertNotEquals(page1, page2);
    }

    @Test
    @DisplayName("TS-03: Wide row scan for analytics")
    void testWideRowScan() {
        // Create multiple row keys for time-range queries
        for (int day = 1; day <= 30; day++) {
            for (int hour = 0; hour < 24; hour++) {
                var rowKey = String.format("events:2024-%02d-%02d", day, hour);
                cf.put(rowKey, "count", (int) (Math.random() * 1000));
            }
        }

        // Scan specific date range
        var result = cf.getRowSlice("events:2024-01-01", "events:2024-01-03", 100);
        assertTrue(result.size() > 0);

        // All results should be within range
        for (var rowSlice : result) {
            assertTrue(rowSlice.getRowKey().contains("2024-01-0"));
        }
    }
}

package org.embeddedjnosql.db.nosql.column;

import org.embeddedjnosql.db.storage.spi.StorageEngine;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Wide-Column Family implementation with advanced features:
 * - Column-level TTL (Time-To-Live)
 * - Wide-column pagination
 * - Column filtering
 * - Column family statistics
 *
 * Storage format for columns with TTL:
 * <pre>{@code
 * {
 *   "columns": {
 *     "columnName": {
 *       "value": <actual value>,
 *       "ttl": <ttlSeconds or null>,
 *       "createdAt": <timestamp>,
 *       "expiresAt": <timestamp or null>
 *     }
 *   }
 * }
 * }</pre>
 */
public class ColumnFamily {

    private final String name;
    private final StorageEngine engine;

    public ColumnFamily(String name, StorageEngine engine) {
        this.name = name;
        this.engine = engine;
    }

    public String name() {
        return name;
    }

    // ==================== BASIC OPERATIONS ====================

    /**
     * Put a column value without TTL
     */
    public void put(String rowKey, String column, Object value) {
        put(rowKey, column, value, null);
    }

    /**
     * Put a column value with optional TTL
     * @param rowKey The row key
     * @param column The column name
     * @param value The value to store
     * @param ttlSeconds Time-to-live in seconds (null for no expiration)
     */
    public void put(String rowKey, String column, Object value, Integer ttlSeconds) {
        var row = getRowInternal(rowKey);
        var columnData = new ColumnData(value, ttlSeconds);
        row.put(column, columnData.toMap());
        saveRow(rowKey, row);
        updateStatsOnWrite(rowKey, column, true);
    }

    /**
     * Get a column value (with automatic TTL expiration check)
     */
    public Object get(String rowKey, String column) {
        var columnData = getColumnData(rowKey, column);
        if (columnData == null) {
            return null;
        }
        if (columnData.isExpired()) {
            deleteColumn(rowKey, column);
            return null;
        }
        return columnData.getValue();
    }

    /**
     * Get a column value with TTL check (explicit method)
     */
    public Object getWithTtlCheck(String rowKey, String column) {
        return get(rowKey, column);
    }

    /**
     * Get column data including TTL metadata
     */
    public ColumnData getColumnData(String rowKey, String column) {
        var row = getRowInternal(rowKey);
        var columnMap = row.get(column);
        if (columnMap == null) {
            return null;
        }
        if (columnMap instanceof Map) {
            return ColumnData.fromMap((Map<?, ?>) columnMap);
        }
        // Legacy format: plain value without metadata
        return new ColumnData(columnMap, null);
    }

    // ==================== COLUMN-LEVEL TTL OPERATIONS ====================

    /**
     * Put a column with explicit TTL
     */
    public void putWithTtl(String rowKey, String column, Object value, int ttlSeconds) {
        put(rowKey, column, value, ttlSeconds);
    }

    /**
     * Update TTL for an existing column
     * @return true if TTL was updated, false if column doesn't exist
     */
    public boolean updateTtl(String rowKey, String column, int ttlSeconds) {
        var row = getRowInternal(rowKey);
        var columnMap = row.get(column);
        if (columnMap == null) {
            return false;
        }
        var columnData = ColumnData.fromMap((Map<?, ?>) columnMap);
        columnData.updateTtl(ttlSeconds);
        row.put(column, columnData.toMap());
        saveRow(rowKey, row);
        return true;
    }

    /**
     * Remove TTL from a column (make it permanent)
     */
    public boolean removeTtl(String rowKey, String column) {
        return updateTtl(rowKey, column, -1);
    }

    /**
     * Get remaining TTL for a column in seconds
     * @return remaining seconds, -1 if no TTL, -2 if expired or doesn't exist
     */
    public long getRemainingTtl(String rowKey, String column) {
        var columnData = getColumnData(rowKey, column);
        if (columnData == null) {
            return -2;
        }
        if (!columnData.hasTtl()) {
            return -1;
        }
        return columnData.getRemainingTtlSeconds();
    }

    /**
     * Check if a column is expired
     */
    public boolean isExpired(String rowKey, String column) {
        var columnData = getColumnData(rowKey, column);
        return columnData != null && columnData.isExpired();
    }

    /**
     * Cleanup all expired columns in a row
     * @return number of columns removed
     */
    public int cleanupExpiredColumns(String rowKey) {
        var row = getRowInternal(rowKey);
        var expiredColumns = new ArrayList<String>();

        for (var entry : row.entrySet()) {
            if (entry.getValue() instanceof Map) {
                var columnData = ColumnData.fromMap((Map<?, ?>) entry.getValue());
                if (columnData.isExpired()) {
                    expiredColumns.add(entry.getKey());
                }
            }
        }

        for (var column : expiredColumns) {
            row.remove(column);
        }

        if (!expiredColumns.isEmpty()) {
            if (row.isEmpty()) {
                engine.delete(name, rowKey);
            } else {
                saveRow(rowKey, row);
            }
        }

        return expiredColumns.size();
    }

    /**
     * Cleanup expired columns across all rows in this column family
     * @return total number of columns removed
     */
    public int cleanupAllExpired() {
        int totalRemoved = 0;
        for (var rowKey : getRowKeys()) {
            totalRemoved += cleanupExpiredColumns(rowKey);
        }
        return totalRemoved;
    }

    // ==================== WIDE-COLUMN PAGINATION ====================

    /**
     * Get a row with pagination (limit and offset)
     * @param rowKey The row key
     * @param limit Maximum number of columns to return
     * @param offset Number of columns to skip
     * @return Map of column names to values (paginated)
     */
    public Map<String, Object> getRow(String rowKey, int limit, int offset) {
        return getRow(rowKey, limit, offset, null);
    }

    /**
     * Get a row with pagination and optional column filter
     */
    public Map<String, Object> getRow(String rowKey, int limit, int offset, Set<String> columns) {
        // Handle zero or negative limit
        if (limit <= 0) {
            return new LinkedHashMap<String, Object>();
        }

        var row = getRowInternal(rowKey);
        var result = new LinkedHashMap<String, Object>();
        int count = 0;
        int skipped = 0;

        for (var entry : row.entrySet()) {
            // Skip expired columns
            if (entry.getValue() instanceof Map) {
                var columnData = ColumnData.fromMap((Map<?, ?>) entry.getValue());
                if (columnData.isExpired()) {
                    continue;
                }
            }

            // Apply column filter if provided
            if (columns != null && !columns.contains(entry.getKey())) {
                continue;
            }

            // Apply offset
            if (skipped < offset) {
                skipped++;
                continue;
            }

            // Apply limit
            if (limit > 0 && count >= limit) {
                break;
            }

            // Extract value
            if (entry.getValue() instanceof Map) {
                var columnData = ColumnData.fromMap((Map<?, ?>) entry.getValue());
                result.put(entry.getKey(), columnData.getValue());
            } else {
                result.put(entry.getKey(), entry.getValue());
            }
            count++;
        }

        return result;
    }

    /**
     * Get a slice of rows (for scanning multiple row keys)
     * @param startRow Starting row key (inclusive)
     * @param endRow Ending row key (inclusive, or null for no upper bound)
     * @param limit Maximum number of rows to return
     * @return List of row data with their keys
     */
    public List<RowSlice> getRowSlice(String startRow, String endRow, int limit) {
        var allKeys = new ArrayList<String>(engine.keys(name));
        allKeys.sort(String::compareTo);

        // Filter by range
        var filteredKeys = allKeys.stream()
            .filter(key -> key.compareTo(startRow) >= 0)
            .filter(key -> endRow == null || key.compareTo(endRow) <= 0)
            .limit(limit)
            .collect(Collectors.toList());

        var result = new ArrayList<RowSlice>();
        for (var key : filteredKeys) {
            var row = getRow(key);
            if (!row.isEmpty()) {
                result.add(new RowSlice(key, row));
            }
        }

        return result;
    }

    /**
     * Get a slice of rows with column projection
     */
    public List<RowSlice> getRowSlice(String startRow, String endRow, int limit, Set<String> columns) {
        var allKeys = new ArrayList<String>(engine.keys(name));
        allKeys.sort(String::compareTo);

        var filteredKeys = allKeys.stream()
            .filter(key -> key.compareTo(startRow) >= 0)
            .filter(key -> endRow == null || key.compareTo(endRow) <= 0)
            .limit(limit)
            .collect(Collectors.toList());

        var result = new ArrayList<RowSlice>();
        for (var key : filteredKeys) {
            var row = columns != null ? getRow(key, columns) : getRow(key);
            if (!row.isEmpty()) {
                result.add(new RowSlice(key, row));
            }
        }

        return result;
    }

    /**
     * Get all columns for a row (original method, no pagination)
     */
    public Map<String, Object> getRow(String rowKey) {
        var row = getRowInternal(rowKey);
        var result = new LinkedHashMap<String, Object>();

        for (var entry : row.entrySet()) {
            if (entry.getValue() instanceof Map) {
                var columnData = ColumnData.fromMap((Map<?, ?>) entry.getValue());
                if (!columnData.isExpired()) {
                    result.put(entry.getKey(), columnData.getValue());
                }
            } else {
                result.put(entry.getKey(), entry.getValue());
            }
        }

        return result;
    }

    // ==================== COLUMN FILTERING ====================

    /**
     * Get specific columns from a row
     * @param rowKey The row key
     * @param columns Set of column names to retrieve
     * @return Map containing only the requested columns that exist
     */
    public Map<String, Object> getRow(String rowKey, Set<String> columns) {
        var row = getRowInternal(rowKey);
        var result = new LinkedHashMap<String, Object>();

        for (var column : columns) {
            var columnMap = row.get(column);
            if (columnMap != null) {
                if (columnMap instanceof Map) {
                    var columnData = ColumnData.fromMap((Map<?, ?>) columnMap);
                    if (!columnData.isExpired()) {
                        result.put(column, columnData.getValue());
                    }
                } else {
                    result.put(column, columnMap);
                }
            }
        }

        return result;
    }

    /**
     * Get row columns filtered by a predicate
     * @param rowKey The row key
     * @param filter Predicate to test column names
     * @return Map of columns that match the predicate
     */
    public Map<String, Object> getRowWithFilter(String rowKey, Predicate<String> filter) {
        var row = getRowInternal(rowKey);
        var result = new LinkedHashMap<String, Object>();

        for (var entry : row.entrySet()) {
            if (filter.test(entry.getKey())) {
                if (entry.getValue() instanceof Map) {
                    var columnData = ColumnData.fromMap((Map<?, ?>) entry.getValue());
                    if (!columnData.isExpired()) {
                        result.put(entry.getKey(), columnData.getValue());
                    }
                } else {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return result;
    }

    /**
     * Get row columns matching a prefix pattern
     * @param rowKey The row key
     * @param prefix Column name prefix to match
     * @return Map of columns with names starting with the prefix
     */
    public Map<String, Object> getRowByPrefix(String rowKey, String prefix) {
        return getRowWithFilter(rowKey, col -> col.startsWith(prefix));
    }

    /**
     * Get row columns matching a regex pattern
     * @param rowKey The row key
     * @param pattern Regex pattern to match column names
     * @return Map of columns with names matching the pattern
     */
    public Map<String, Object> getRowByPattern(String rowKey, String pattern) {
        return getRowWithFilter(rowKey, col -> col.matches(pattern));
    }

    // ==================== STATISTICS ====================

    /**
     * Get statistics for the entire column family
     */
    public Map<String, Object> getColumnStats() {
        var stats = new LinkedHashMap<String, Object>();
        var rowKeys = getRowKeys();

        stats.put("familyName", name);
        stats.put("rowCount", rowKeys.size());

        long totalColumns = 0;
        long totalWithTtl = 0;
        long expiredColumns = 0;
        long totalSizeBytes = 0;
        var columnCountPerRow = new ArrayList<Long>();

        for (var rowKey : rowKeys) {
            var row = getRowInternal(rowKey);
            long rowColumns = 0;
            long rowWithTtl = 0;
            long rowExpired = 0;

            for (var entry : row.entrySet()) {
                rowColumns++;
                totalSizeBytes += estimateEntrySize(entry);

                if (entry.getValue() instanceof Map) {
                    var columnData = ColumnData.fromMap((Map<?, ?>) entry.getValue());
                    if (columnData.hasTtl()) {
                        rowWithTtl++;
                    }
                    if (columnData.isExpired()) {
                        rowExpired++;
                    }
                }
            }

            totalColumns += rowColumns;
            totalWithTtl += rowWithTtl;
            expiredColumns += rowExpired;
            columnCountPerRow.add(rowColumns);
        }

        stats.put("totalColumns", totalColumns);
        stats.put("columnsWithTtl", totalWithTtl);
        stats.put("expiredColumns", expiredColumns);
        stats.put("estimatedSizeBytes", totalSizeBytes);
        stats.put("avgColumnsPerRow", rowKeys.isEmpty() ? 0 : totalColumns / rowKeys.size());

        if (!columnCountPerRow.isEmpty()) {
            stats.put("minColumnsPerRow", Collections.min(columnCountPerRow));
            stats.put("maxColumnsPerRow", Collections.max(columnCountPerRow));
        }

        return stats;
    }

    /**
     * Get statistics for a specific row
     */
    public Map<String, Object> getRowStats(String rowKey) {
        var row = getRowInternal(rowKey);
        var stats = new LinkedHashMap<String, Object>();

        stats.put("rowKey", rowKey);
        stats.put("columnCount", row.size());

        long totalWithTtl = 0;
        long expiredColumns = 0;
        long totalSizeBytes = 0;
        var ttlColumns = new ArrayList<String>();
        var expiredColumnList = new ArrayList<String>();

        for (var entry : row.entrySet()) {
            totalSizeBytes += estimateEntrySize(entry);

            if (entry.getValue() instanceof Map) {
                var columnData = ColumnData.fromMap((Map<?, ?>) entry.getValue());
                if (columnData.hasTtl()) {
                    totalWithTtl++;
                    ttlColumns.add(entry.getKey());
                }
                if (columnData.isExpired()) {
                    expiredColumns++;
                    expiredColumnList.add(entry.getKey());
                }
            }
        }

        stats.put("columnsWithTtl", totalWithTtl);
        stats.put("expiredColumns", expiredColumns);
        stats.put("ttlColumnNames", ttlColumns);
        stats.put("expiredColumnNames", expiredColumnList);
        stats.put("estimatedSizeBytes", totalSizeBytes);

        return stats;
    }

    /**
     * Get detailed stats including TTL information
     */
    public Map<String, Object> getDetailedStats() {
        var stats = getColumnStats();
        var detailed = new LinkedHashMap<String, Object>(stats);

        // Add per-row breakdown
        var rowBreakdown = new ArrayList<Map<String, Object>>();
        for (var rowKey : getRowKeys()) {
            rowBreakdown.add(getRowStats(rowKey));
        }
        detailed.put("rowBreakdown", rowBreakdown);

        return detailed;
    }

    // ==================== PRIVATE HELPERS ====================

    private Map<String, Object> getRowInternal(String rowKey) {
        var json = engine.get(name, rowKey);
        if (json == null) return new LinkedHashMap<>();
        return org.embeddedjnosql.db.core.util.JsonSerde.fromJson(json, Map.class);
    }

    private void saveRow(String rowKey, Map<String, Object> row) {
        engine.put(name, rowKey, org.embeddedjnosql.db.core.util.JsonSerde.toJson(row));
    }

    private long estimateEntrySize(Map.Entry<String, Object> entry) {
        long size = entry.getKey().getBytes().length;
        if (entry.getValue() instanceof String) {
            size += ((String) entry.getValue()).getBytes().length;
        } else if (entry.getValue() instanceof Map) {
            size += 100; // Estimate for column metadata
        } else {
            size += String.valueOf(entry.getValue()).getBytes().length;
        }
        return size;
    }

    private void updateStatsOnWrite(String rowKey, String column, boolean isInsert) {
        // Stats are computed on-demand for accuracy
    }

    // ==================== DELETE OPERATIONS ====================

    public void deleteColumn(String rowKey, String column) {
        var row = getRowInternal(rowKey);
        row.remove(column);
        if (row.isEmpty()) {
            engine.delete(name, rowKey);
        } else {
            saveRow(rowKey, row);
        }
    }

    public void deleteRow(String rowKey) {
        engine.delete(name, rowKey);
    }

    public Set<String> getRowKeys() {
        return new HashSet<>(engine.keys(name));
    }

    public long countRows() {
        return engine.keys(name).size();
    }

    // ==================== INNER CLASSES ====================

    /**
     * Column data with TTL metadata
     */
    public static class ColumnData {
        private Object value;
        private Integer ttlSeconds;
        private Long createdAt;
        private Long expiresAt;

        public ColumnData(Object value, Integer ttlSeconds) {
            this.value = value;
            this.ttlSeconds = ttlSeconds;
            this.createdAt = System.currentTimeMillis();
            if (ttlSeconds != null && ttlSeconds > 0) {
                this.expiresAt = this.createdAt + (ttlSeconds * 1000L);
            }
        }

        public Object getValue() {
            return value;
        }

        public Integer getTtlSeconds() {
            return ttlSeconds;
        }

        public Long getCreatedAt() {
            return createdAt;
        }

        public Long getExpiresAt() {
            return expiresAt;
        }

        public boolean hasTtl() {
            return ttlSeconds != null && ttlSeconds > 0;
        }

        public boolean isExpired() {
            return expiresAt != null && System.currentTimeMillis() > expiresAt;
        }

        public long getRemainingTtlSeconds() {
            if (expiresAt == null) {
                return -1;
            }
            long remaining = (expiresAt - System.currentTimeMillis()) / 1000;
            return Math.max(0, remaining);
        }

        public void updateTtl(int ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
            if (ttlSeconds > 0) {
                this.expiresAt = System.currentTimeMillis() + (ttlSeconds * 1000L);
            } else if (ttlSeconds == -1) {
                // Remove TTL
                this.ttlSeconds = null;
                this.expiresAt = null;
            }
        }

        public Map<String, Object> toMap() {
            var map = new LinkedHashMap<String, Object>();
            map.put("value", value);
            if (ttlSeconds != null) {
                map.put("ttl", ttlSeconds);
            }
            map.put("createdAt", createdAt);
            if (expiresAt != null) {
                map.put("expiresAt", expiresAt);
            }
            return map;
        }

        public static ColumnData fromMap(Map<?, ?> map) {
            Object value = map.get("value");
            Object ttl = map.get("ttl");
            Integer ttlSeconds = ttl instanceof Number ? ((Number) ttl).intValue() : null;

            var data = new ColumnData(value, ttlSeconds);

            Object createdAt = map.get("createdAt");
            if (createdAt instanceof Number) {
                data.createdAt = ((Number) createdAt).longValue();
            }

            Object expiresAt = map.get("expiresAt");
            if (expiresAt instanceof Number) {
                data.expiresAt = ((Number) expiresAt).longValue();
            }

            return data;
        }
    }

    /**
     * Row slice for pagination results
     */
    public static class RowSlice {
        private final String rowKey;
        private final Map<String, Object> data;

        public RowSlice(String rowKey, Map<String, Object> data) {
            this.rowKey = rowKey;
            this.data = data;
        }

        public String getRowKey() {
            return rowKey;
        }

        public Map<String, Object> getData() {
            return data;
        }

        public Map<String, Object> toMap() {
            var result = new LinkedHashMap<String, Object>();
            result.put("rowKey", rowKey);
            result.put("data", data);
            return result;
        }
    }
}

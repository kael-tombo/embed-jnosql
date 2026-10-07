package org.embeddedjnosql.db.nosql.kv;

import org.embeddedjnosql.db.core.event.EventBus;
import org.embeddedjnosql.db.core.metrics.DatabaseMetrics;
import org.embeddedjnosql.db.storage.spi.StorageEngine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Redis-style List data structure for EmbedJNoSQL KV engine.
 * Implements a doubly-linked list abstraction using the underlying storage engine.
 * 
 * Operations: lpush, rpush, lpop, rpop, lrange, llen, lrem
 */
public class ListBucket {

    private final String name;
    private final StorageEngine engine;
    private final EventBus eventBus;
    private final DatabaseMetrics metrics;
    private final Map<String, Instant> expirations;
    private final Map<String, List<String>> listCache;

    public ListBucket(String name, StorageEngine engine, EventBus eventBus, DatabaseMetrics metrics) {
        this.name = name;
        this.engine = engine;
        this.eventBus = eventBus;
        this.metrics = metrics;
        this.expirations = new ConcurrentHashMap<>();
        this.listCache = new ConcurrentHashMap<>();
    }

    public String name() {
        return name;
    }

    /**
     * LPUSH - Insert all values at the head (left side) of the list.
     * @param key The list key
     * @param values Values to prepend
     * @return Length of the list after the push operation
     */
    public long lpush(String key, String... values) {
        if (values == null || values.length == 0) {
            return llen(key);
        }
        var list = getList(key);
        for (String value : values) {
            list.add(0, value);
        }
        saveList(key, list);
        metrics.recordInsert();
        return list.size();
    }

    /**
     * RPUSH - Insert all values at the tail (right side) of the list.
     * @param key The list key
     * @param values Values to append
     * @return Length of the list after the push operation
     */
    public long rpush(String key, String... values) {
        if (values == null || values.length == 0) {
            return llen(key);
        }
        var list = getList(key);
        for (String value : values) {
            list.add(value);
        }
        saveList(key, list);
        metrics.recordInsert();
        return list.size();
    }

    /**
     * LPOP - Remove and return the first element from the left side of the list.
     * @param key The list key
     * @return The popped element, or null if list does not exist or is empty
     */
    public String lpop(String key) {
        var list = getList(key);
        if (list.isEmpty()) {
            return null;
        }
        String value = list.remove(0);
        saveList(key, list);
        metrics.recordRead();
        return value;
    }

    /**
     * RPOP - Remove and return the last element from the right side of the list.
     * @param key The list key
     * @return The popped element, or null if list does not exist or is empty
     */
    public String rpop(String key) {
        var list = getList(key);
        if (list.isEmpty()) {
            return null;
        }
        String value = list.remove(list.size() - 1);
        saveList(key, list);
        metrics.recordRead();
        return value;
    }

    /**
     * LRANGE - Get elements in the range [start, end] (inclusive).
     * Supports negative indices: -1 = last element, -2 = second to last, etc.
     * @param key The list key
     * @param start Start index (inclusive)
     * @param end End index (inclusive)
     * @return List of elements in the range
     */
    public List<String> lrange(String key, int start, int end) {
        var list = getList(key);
        if (list.isEmpty()) {
            return List.of();
        }
        
        int size = list.size();
        
        // Handle negative indices
        if (start < 0) {
            start = Math.max(0, size + start);
        }
        if (end < 0) {
            end = Math.max(0, size + end);
        }
        
        // Clamp to valid range
        start = Math.max(0, Math.min(start, size - 1));
        end = Math.max(0, Math.min(end, size - 1));
        
        if (start > end) {
            return List.of();
        }
        
        return new ArrayList<>(list.subList(start, end + 1));
    }

    /**
     * LLEN - Get the length of the list.
     * @param key The list key
     * @return Number of elements in the list, 0 if list does not exist
     */
    public long llen(String key) {
        if (isExpired(key)) {
            deleteList(key);
            return 0;
        }
        var list = getList(key);
        return list.size();
    }

    /**
     * LREM - Remove occurrences of value from the list.
     * @param key The list key
     * @param count If positive: remove first 'count' occurrences from head
     *              If negative: remove first |count| occurrences from tail
     *              If zero: remove all occurrences
     * @param value The value to remove
     * @return Number of elements removed
     */
    public long lrem(String key, long count, String value) {
        var list = getList(key);
        if (list.isEmpty()) {
            return 0;
        }
        
        long removed = 0;
        
        if (count == 0) {
            // Remove all occurrences
            for (int i = list.size() - 1; i >= 0; i--) {
                if (value.equals(list.get(i))) {
                    list.remove(i);
                    removed++;
                }
            }
        } else if (count > 0) {
            // Remove from head
            int toRemove = (int) count;
            for (int i = 0; i < list.size() && toRemove > 0; i++) {
                if (value.equals(list.get(i))) {
                    list.remove(i);
                    i--; // Adjust index after removal
                    toRemove--;
                    removed++;
                }
            }
        } else {
            // Remove from tail (count is negative)
            int toRemove = (int) Math.abs(count);
            for (int i = list.size() - 1; i >= 0 && toRemove > 0; i--) {
                if (value.equals(list.get(i))) {
                    list.remove(i);
                    toRemove--;
                    removed++;
                }
            }
        }
        
        if (removed > 0) {
            saveList(key, list);
            metrics.recordDelete();
        }
        
        return removed;
    }

    /**
     * LINDEX - Get element at index.
     * @param key The list key
     * @param index Index (supports negative: -1 = last)
     * @return Element at index, or null if out of range
     */
    public String lindex(String key, int index) {
        var list = getList(key);
        if (list.isEmpty()) {
            return null;
        }
        
        int size = list.size();
        if (index < 0) {
            index = size + index;
        }
        
        if (index < 0 || index >= size) {
            return null;
        }
        
        metrics.recordRead();
        return list.get(index);
    }

    /**
     * LSET - Set value at index.
     * @param key The list key
     * @param index Index to set (supports negative)
     * @param value New value
     * @throws IndexOutOfBoundsException if index is out of range
     */
    public void lset(String key, int index, String value) {
        var list = getList(key);
        if (list.isEmpty()) {
            throw new IndexOutOfBoundsException("Cannot set index in empty list");
        }
        
        int size = list.size();
        if (index < 0) {
            index = size + index;
        }
        
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException("Index out of range: " + index);
        }
        
        list.set(index, value);
        saveList(key, list);
    }

    /**
     * LTRIM - Trim list to range [start, end].
     * @param key The list key
     * @param start Start index (inclusive)
     * @param end End index (inclusive)
     */
    public void ltrim(String key, int start, int end) {
        var list = getList(key);
        if (list.isEmpty()) {
            return;
        }
        
        int size = list.size();
        
        // Handle negative indices
        if (start < 0) {
            start = Math.max(0, size + start);
        }
        if (end < 0) {
            end = Math.max(0, size + end);
        }
        
        // Clamp to valid range
        start = Math.max(0, Math.min(start, size));
        end = Math.max(0, Math.min(end, size - 1));
        
        if (start > end) {
            list.clear();
        } else {
            // Keep only elements in range [start, end]
            var trimmed = new ArrayList<>(list.subList(start, end + 1));
            list.clear();
            list.addAll(trimmed);
        }
        
        saveList(key, list);
    }

    /**
     * LINSERT - Insert value before or after pivot element.
     * @param key The list key
     * @param where "BEFORE" or "AFTER"
     * @param pivot The pivot element
     * @param value Value to insert
     * @return Length of list after insertion, or -1 if pivot not found
     */
    public long linsert(String key, String where, String pivot, String value) {
        var list = getList(key);
        if (list.isEmpty()) {
            return -1;
        }
        
        int pivotIndex = -1;
        for (int i = 0; i < list.size(); i++) {
            if (pivot.equals(list.get(i))) {
                pivotIndex = i;
                break;
            }
        }
        
        if (pivotIndex == -1) {
            return -1;
        }
        
        int insertIndex = "BEFORE".equalsIgnoreCase(where) ? pivotIndex : pivotIndex + 1;
        list.add(insertIndex, value);
        saveList(key, list);
        metrics.recordInsert();
        
        return list.size();
    }

    /**
     * RPOPLPUSH - Pop from right of source list and push to left of destination.
     * @param sourceKey Source list key
     * @param destKey Destination list key
     * @return The element moved, or null if source is empty
     */
    public String rpoplpush(String sourceKey, String destKey) {
        String value = rpop(sourceKey);
        if (value == null) {
            return null;
        }
        lpush(destKey, value);
        return value;
    }

    /**
     * Set TTL for the list.
     * @param key The list key
     * @param ttl Time to live duration
     */
    public void expire(String key, Duration ttl) {
        expirations.put(key, Instant.now().plus(ttl));
    }

    /**
     * Delete the list.
     * @param key The list key
     * @return true if deleted, false if didn't exist
     */
    public boolean delete(String key) {
        expirations.remove(key);
        listCache.remove(key);
        if (engine.exists(name, key)) {
            engine.delete(name, key);
            metrics.recordDelete();
            return true;
        }
        return false;
    }

    /**
     * Get all list keys.
     */
    public Set<String> keys() {
        return engine.keys(name);
    }

    /**
     * Clear all lists in the bucket.
     */
    public void clear() {
        for (String key : keys()) {
            delete(key);
        }
        expirations.clear();
        listCache.clear();
    }

    /**
     * Get bucket statistics.
     */
    public Map<String, Object> stats() {
        long total = keys().size();
        long expired = expirations.entrySet().stream()
            .filter(e -> e.getValue().isBefore(Instant.now()))
            .count();
        long totalElements = keys().stream().mapToLong(this::llen).sum();
        
        return Map.of(
            "name", name,
            "totalLists", total,
            "expiredLists", expired,
            "activeLists", total - expired,
            "totalElements", totalElements,
            "memoryEntries", expirations.size()
        );
    }

    private List<String> getList(String key) {
        if (isExpired(key)) {
            deleteList(key);
            return new CopyOnWriteArrayList<>();
        }
        
        return listCache.computeIfAbsent(key, k -> {
            String stored = engine.get(name, key);
            if (stored == null) {
                return new CopyOnWriteArrayList<>();
            }
            // Deserialize from JSON-like format: ["val1","val2",...]
            return deserializeList(stored);
        });
    }

    private void saveList(String key, List<String> list) {
        String serialized = serializeList(list);
        engine.put(name, key, serialized);
    }

    private void deleteList(String key) {
        listCache.remove(key);
        engine.delete(name, key);
    }

    private boolean isExpired(String key) {
        var expiry = expirations.get(key);
        return expiry != null && Instant.now().isAfter(expiry);
    }

    private String serializeList(List<String> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(escapeJson(list.get(i))).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }

    private List<String> deserializeList(String json) {
        List<String> result = new CopyOnWriteArrayList<>();
        if (json == null || json.length() < 2) {
            return result;
        }
        
        // Simple JSON array parsing: ["val1","val2",...]
        String content = json.trim();
        if (!content.startsWith("[") || !content.endsWith("]")) {
            return result;
        }
        
        content = content.substring(1, content.length() - 1);
        if (content.isEmpty()) {
            return result;
        }
        
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        
        for (char c : content.toCharArray()) {
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                if (inString) {
                    result.add(current.toString());
                    current.setLength(0);
                }
                inString = !inString;
            } else if (inString) {
                current.append(c);
            }
            // Skip commas and whitespace outside strings
        }
        
        return result;
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}

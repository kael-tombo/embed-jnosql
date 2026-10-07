package org.embeddedjnosql.db.nosql.kv;

import org.embeddedjnosql.db.core.event.EventBus;
import org.embeddedjnosql.db.core.metrics.DatabaseMetrics;
import org.embeddedjnosql.db.storage.spi.StorageEngine;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis-style Hash data structure for EmbedJNoSQL KV engine.
 * Implements a hash map of field-value pairs.
 * 
 * Operations: hset, hget, hgetall, hdel, hlen, hexists, hkeys, hvals
 */
public class HashBucket {

    private final String name;
    private final StorageEngine engine;
    private final EventBus eventBus;
    private final DatabaseMetrics metrics;
    private final Map<String, Instant> expirations;
    private final Map<String, Map<String, String>> hashCache;

    public HashBucket(String name, StorageEngine engine, EventBus eventBus, DatabaseMetrics metrics) {
        this.name = name;
        this.engine = engine;
        this.eventBus = eventBus;
        this.metrics = metrics;
        this.expirations = new ConcurrentHashMap<>();
        this.hashCache = new ConcurrentHashMap<>();
    }

    public String name() {
        return name;
    }

    /**
     * HSET - Set field to value in the hash.
     * @param key The hash key
     * @param field The field name
     * @param value The field value
     * @return 1 if field is new, 0 if field was updated
     */
    public synchronized int hset(String key, String field, String value) {
        var hash = getHash(key);
        boolean isNew = !hash.containsKey(field);
        hash.put(field, value);
        saveHash(key, hash);
        metrics.recordInsert();
        return isNew ? 1 : 0;
    }

    /**
     * HSET - Set multiple field-value pairs in the hash.
     * @param key The hash key
     * @param fieldValues Field-value pairs (must be even number of arguments)
     * @return Number of fields that were added (not updated)
     */
    public synchronized int hset(String key, Map<String, String> fieldValues) {
        var hash = getHash(key);
        int added = 0;
        for (var entry : fieldValues.entrySet()) {
            if (!hash.containsKey(entry.getKey())) {
                added++;
            }
            hash.put(entry.getKey(), entry.getValue());
        }
        saveHash(key, hash);
        metrics.recordInsert();
        return added;
    }

    /**
     * HGET - Get value of field in the hash.
     * @param key The hash key
     * @param field The field name
     * @return The field value, or null if field doesn't exist
     */
    public String hget(String key, String field) {
        var hash = getHash(key);
        metrics.recordRead();
        return hash.get(field);
    }

    /**
     * HMGET - Get values of multiple fields in the hash.
     * @param key The hash key
     * @param fields The field names
     * @return Map of field-value pairs (only existing fields)
     */
    public Map<String, String> hmget(String key, String... fields) {
        var hash = getHash(key);
        Map<String, String> result = new LinkedHashMap<>();
        for (String field : fields) {
            if (hash.containsKey(field)) {
                result.put(field, hash.get(field));
            }
        }
        metrics.recordRead();
        return result;
    }

    /**
     * HGETALL - Get all field-value pairs in the hash.
     * @param key The hash key
     * @return Map of all field-value pairs
     */
    public Map<String, String> hgetall(String key) {
        var hash = getHash(key);
        metrics.recordRead();
        return new LinkedHashMap<>(hash);
    }

    /**
     * HDEL - Delete one or more fields from the hash.
     * @param key The hash key
     * @param fields The field names to delete
     * @return Number of fields that were deleted
     */
    public synchronized int hdel(String key, String... fields) {
        if (fields == null || fields.length == 0) {
            return 0;
        }
        var hash = getHash(key);
        int deleted = 0;
        for (String field : fields) {
            if (hash.remove(field) != null) {
                deleted++;
            }
        }
        if (deleted > 0) {
            saveHash(key, hash);
            metrics.recordDelete();
        }
        return deleted;
    }

    /**
     * HLEN - Get number of fields in the hash.
     * @param key The hash key
     * @return Number of fields, 0 if hash doesn't exist
     */
    public long hlen(String key) {
        if (isExpired(key)) {
            deleteHash(key);
            return 0;
        }
        var hash = getHash(key);
        return hash.size();
    }

    /**
     * HEXISTS - Check if field exists in the hash.
     * @param key The hash key
     * @param field The field name
     * @return true if field exists, false otherwise
     */
    public boolean hexists(String key, String field) {
        var hash = getHash(key);
        metrics.recordRead();
        return hash.containsKey(field);
    }

    /**
     * HKEYS - Get all field names in the hash.
     * @param key The hash key
     * @return Set of all field names
     */
    public Set<String> hkeys(String key) {
        var hash = getHash(key);
        metrics.recordRead();
        return hash.keySet();
    }

    /**
     * HVALS - Get all values in the hash.
     * @param key The hash key
     * @return List of all values
     */
    public List<String> hvals(String key) {
        var hash = getHash(key);
        metrics.recordRead();
        return new ArrayList<>(hash.values());
    }

    /**
     * HINCRBY - Increment integer value of field by delta.
     * @param key The hash key
     * @param field The field name
     * @param delta The amount to increment (can be negative)
     * @return New value after increment
     */
    public synchronized long hincrby(String key, String field, long delta) {
        var hash = getHash(key);
        String currentValue = hash.get(field);
        long value = currentValue != null ? Long.parseLong(currentValue) : 0;
        long newValue = value + delta;
        hash.put(field, String.valueOf(newValue));
        saveHash(key, hash);
        metrics.recordInsert();
        return newValue;
    }

    /**
     * HINCRBYFLOAT - Increment float value of field by delta.
     * @param key The hash key
     * @param field The field name
     * @param delta The amount to increment
     * @return New value as string
     */
    public synchronized String hincrbyfloat(String key, String field, double delta) {
        var hash = getHash(key);
        String currentValue = hash.get(field);
        double value = currentValue != null ? Double.parseDouble(currentValue) : 0.0;
        double newValue = value + delta;
        String result = String.valueOf(newValue);
        hash.put(field, result);
        saveHash(key, hash);
        metrics.recordInsert();
        return result;
    }

    /**
     * HSETNX - Set field to value only if field does not exist.
     * @param key The hash key
     * @param field The field name
     * @param value The field value
     * @return 1 if field was set, 0 if field already existed
     */
    public synchronized int hsetnx(String key, String field, String value) {
        var hash = getHash(key);
        if (hash.containsKey(field)) {
            return 0;
        }
        hash.put(field, value);
        saveHash(key, hash);
        metrics.recordInsert();
        return 1;
    }

    /**
     * HMSET - Set multiple field-value pairs (alias for hset with map).
     * @param key The hash key
     * @param fieldValues Field-value pairs
     * @return "OK"
     */
    public String hmset(String key, Map<String, String> fieldValues) {
        hset(key, fieldValues);
        return "OK";
    }

    /**
     * HSTRLEN - Get length of value at field.
     * @param key The hash key
     * @param field The field name
     * @return Length of value, 0 if field doesn't exist
     */
    public long hstrlen(String key, String field) {
        var hash = getHash(key);
        String value = hash.get(field);
        return value != null ? value.length() : 0;
    }

    /**
     * HGETALL - Get all field-value pairs as alternating list [field1, value1, field2, value2, ...].
     * @param key The hash key
     * @return List of alternating field-value pairs
     */
    public List<String> hgetallFlat(String key) {
        var hash = getHash(key);
        List<String> result = new ArrayList<>(hash.size() * 2);
        for (var entry : hash.entrySet()) {
            result.add(entry.getKey());
            result.add(entry.getValue());
        }
        metrics.recordRead();
        return result;
    }

    /**
     * Set TTL for the hash.
     * @param key The hash key
     * @param ttl Time to live duration
     */
    public void expire(String key, Duration ttl) {
        expirations.put(key, Instant.now().plus(ttl));
    }

    /**
     * Delete the hash.
     * @param key The hash key
     * @return true if deleted, false if didn't exist
     */
    public boolean delete(String key) {
        expirations.remove(key);
        hashCache.remove(key);
        if (engine.exists(name, key)) {
            engine.delete(name, key);
            metrics.recordDelete();
            return true;
        }
        return false;
    }

    /**
     * Get all hash keys.
     */
    public Set<String> keys() {
        return engine.keys(name);
    }

    /**
     * Clear all hashes in the bucket.
     */
    public void clear() {
        for (String key : keys()) {
            delete(key);
        }
        expirations.clear();
        hashCache.clear();
    }

    /**
     * Get bucket statistics.
     */
    public Map<String, Object> stats() {
        long total = keys().size();
        long expired = expirations.entrySet().stream()
            .filter(e -> e.getValue().isBefore(Instant.now()))
            .count();
        long totalFields = keys().stream().mapToLong(this::hlen).sum();
        
        return Map.of(
            "name", name,
            "totalHashes", total,
            "expiredHashes", expired,
            "activeHashes", total - expired,
            "totalFields", totalFields,
            "memoryEntries", expirations.size()
        );
    }

    private Map<String, String> getHash(String key) {
        if (isExpired(key)) {
            deleteHash(key);
            return new ConcurrentHashMap<>();
        }
        
        return hashCache.computeIfAbsent(key, k -> {
            String stored = engine.get(name, key);
            if (stored == null) {
                return new ConcurrentHashMap<>();
            }
            return deserializeHash(stored);
        });
    }

    private void saveHash(String key, Map<String, String> hash) {
        String serialized = serializeHash(hash);
        engine.put(name, key, serialized);
    }

    private void deleteHash(String key) {
        hashCache.remove(key);
        engine.delete(name, key);
    }

    private boolean isExpired(String key) {
        var expiry = expirations.get(key);
        return expiry != null && Instant.now().isAfter(expiry);
    }

    private String serializeHash(Map<String, String> hash) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : hash.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(entry.getKey())).append("\"");
            sb.append(":");
            sb.append("\"").append(escapeJson(entry.getValue())).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private Map<String, String> deserializeHash(String json) {
        Map<String, String> result = new ConcurrentHashMap<>();
        if (json == null || json.length() < 2) {
            return result;
        }
        
        String content = json.trim();
        if (!content.startsWith("{") || !content.endsWith("}")) {
            return result;
        }
        
        content = content.substring(1, content.length() - 1);
        if (content.isEmpty()) {
            return result;
        }
        
        String key = null;
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        boolean isKey = true;
        
        for (char c : content.toCharArray()) {
            if (escaped) {
                current.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                if (inString) {
                    if (isKey) {
                        key = current.toString();
                    } else {
                        if (key != null) {
                            result.put(key, current.toString());
                        }
                    }
                    current.setLength(0);
                }
                inString = !inString;
            } else if (c == ':' && !inString) {
                isKey = false;
            } else if (inString) {
                current.append(c);
            }
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

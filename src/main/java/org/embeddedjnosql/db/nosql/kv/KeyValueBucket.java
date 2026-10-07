package org.embeddedjnosql.db.nosql.kv;

import org.embeddedjnosql.db.core.event.EventBus;
import org.embeddedjnosql.db.core.metrics.DatabaseMetrics;
import org.embeddedjnosql.db.storage.spi.StorageEngine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class KeyValueBucket {

    private final String name;
    private final StorageEngine engine;
    private final EventBus eventBus;
    private final DatabaseMetrics metrics;
    private final Map<String, Instant> expirations;

    public KeyValueBucket(String name, StorageEngine engine, EventBus eventBus, DatabaseMetrics metrics) {
        this.name = name;
        this.engine = engine;
        this.eventBus = eventBus;
        this.metrics = metrics;
        this.expirations = new ConcurrentHashMap<>();
        loadExpirations();
    }

    public String name() {
        return name;
    }

    /**
     * Load persisted expirations from storage engine meta_store.
     */
    @SuppressWarnings("unchecked")
    private void loadExpirations() {
        try {
            var json = engine.get("meta_store", "kv_expirations_" + name);
            if (json != null) {
                var map = org.embeddedjnosql.db.core.util.JsonSerde.fromJson(json, Map.class);
                for (var entryObj : map.entrySet()) {
                    var entry = (java.util.Map.Entry<String, Object>) entryObj;
                    var key = entry.getKey();
                    var timestamp = ((Number) entry.getValue()).longValue();
                    expirations.put(key, Instant.ofEpochMilli(timestamp));
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to load expirations: " + e.getMessage());
        }
    }

    /**
     * Persist expirations map to storage engine meta_store.
     */
    private void saveExpirations() {
        try {
            var json = org.embeddedjnosql.db.core.util.JsonSerde.toJson(
                expirations.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().toEpochMilli()
                    ))
            );
            engine.put("meta_store", "kv_expirations_" + name, json);
        } catch (Exception e) {
            System.err.println("Failed to save expirations: " + e.getMessage());
        }
    }

    public void put(String key, String value) {
        engine.put(name, key, value);
        metrics.recordInsert();
    }

    public void put(String key, String value, Duration ttl) {
        put(key, value);
        expirations.put(key, Instant.now().plus(ttl));
        saveExpirations();
    }

    public String get(String key) {
        metrics.recordRead();
        if (isExpired(key)) {
            engine.delete(name, key);
            expirations.remove(key);
            return null;
        }
        return engine.get(name, key);
    }

    public boolean delete(String key) {
        expirations.remove(key);
        saveExpirations();
        if (engine.exists(name, key)) {
            engine.delete(name, key);
            metrics.recordDelete();
            return true;
        }
        return false;
    }

    public boolean exists(String key) {
        if (isExpired(key)) {
            engine.delete(name, key);
            expirations.remove(key);
            return false;
        }
        return engine.exists(name, key);
    }

    public long increment(String key) {
        return increment(key, 1);
    }

    public long increment(String key, long delta) {
        var val = get(key);
        long current = val != null ? Long.parseLong(val) : 0;
        long next = current + delta;
        put(key, String.valueOf(next));
        return next;
    }

    public long decrement(String key) {
        return increment(key, -1);
    }

    /**
     * PHASE 2: Batch put multiple key-value pairs.
     * Atomic operation: all entries are written or none (rollback on failure).
     */
    public void putAll(Map<String, String> entries) {
        putAll(entries, true); // Default to atomic
    }

    /**
     * Batch put with atomicity option.
     * When atomic=true, all entries are written or none (rollback on failure).
     */
    public void putAll(Map<String, String> entries, boolean atomic) {
        if (entries.isEmpty()) {
            return;
        }

        // Capture existing values for rollback
        Map<String, String> previousValues = new LinkedHashMap<>();
        List<String> newKeys = new ArrayList<>();

        try {
            // Begin transaction if engine supports it
            if (engine.supportsTransactions()) {
                engine.beginTransaction();
            }

            // Save existing values for rollback
            for (String key : entries.keySet()) {
                String existing = engine.get(name, key);
                if (existing != null) {
                    previousValues.put(key, existing);
                } else {
                    newKeys.add(key);
                }
            }

            // Perform batch write
            engine.putAll(name, entries);
            entries.forEach((k, v) -> metrics.recordInsert());

            // Commit if transaction was started
            if (engine.supportsTransactions()) {
                engine.commitTransaction();
            }
        } catch (Exception e) {
            // Rollback on failure when atomic
            if (atomic && engine.supportsTransactions()) {
                try {
                    engine.rollbackTransaction();
                } catch (Exception rollbackEx) {
                    System.err.println("Transaction rollback failed: " + rollbackEx.getMessage());
                }
            }
            // Manual rollback: restore previous values or delete new keys
            if (atomic) {
                for (Map.Entry<String, String> entry : previousValues.entrySet()) {
                    engine.put(name, entry.getKey(), entry.getValue());
                }
                for (String key : newKeys) {
                    engine.delete(name, key);
                }
            }
            throw new org.embeddedjnosql.db.core.exception.StorageException("Batch put failed: " + e.getMessage(), e);
        }
    }

    /**
     * PHASE 2: Batch get multiple values.
     */
    public Map<String, String> getAll(Iterable<String> keys) {
        var result = new LinkedHashMap<String, String>();
        for (String key : keys) {
            var value = get(key);
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    /**
     * PHASE 2: Get all keys in the bucket.
     */
    public Set<String> keys() {
        return engine.keys(name);
    }

    /**
     * PHASE 2: Get the count of non-expired keys.
     */
    public long count() {
        return keys().stream().filter(k -> !isExpired(k)).count();
    }

    /**
     * PHASE 2: Clear all keys in the bucket.
     */
    public void clear() {
        for (String key : keys()) {
            delete(key);
        }
        expirations.clear();
        saveExpirations();
    }

    /**
     * PHASE 2: Get bucket statistics.
     */
    public Map<String, Object> stats() {
        long total = keys().size();
        long expired = expirations.entrySet().stream()
            .filter(e -> e.getValue().isBefore(Instant.now()))
            .count();
        return Map.of(
            "name", name,
            "totalKeys", total,
            "expiredKeys", expired,
            "activeKeys", total - expired,
            "memoryEntries", expirations.size()
        );
    }

    private boolean isExpired(String key) {
        var expiry = expirations.get(key);
        return expiry != null && Instant.now().isAfter(expiry);
    }

    /**
     * Expiry instant per key for keys that carry a TTL (unmodifiable snapshot).
     * Lets the admin surface show expiration without exposing the internal map.
     */
    public Map<String, Instant> expirations() {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(expirations));
    }

    /**
     * Expiry instant for one key, or {@code null} when the key carries no TTL (or
     * already expired). Read-only; does not evict — callers decide what to show.
     */
    public Instant expiryOf(String key) {
        var expiry = expirations.get(key);
        return expiry != null && expiry.isAfter(Instant.now()) ? expiry : null;
    }
}

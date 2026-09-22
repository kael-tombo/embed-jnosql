package org.junify.db.storage.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Blazing-fast zero-overhead in-memory storage engine backed by {@link ConcurrentHashMap}.
 * Suitable for unit tests, ephemeral caches, and embedded workloads.
 */
public class InMemoryEngine implements StorageEngine {

    private final ConcurrentMap<String, ConcurrentMap<String, String>> store;

    public InMemoryEngine() {
        this.store = new ConcurrentHashMap<>();
    }

    @Override
    public String name() {
        return "IN_MEMORY";
    }

    @Override
    public void put(String collection, String key, String value) {
        store.computeIfAbsent(collection, k -> new ConcurrentHashMap<>()).put(key, value);
    }

    @Override
    public void putAll(String collection, Map<String, String> entries) {
        var col = store.computeIfAbsent(collection, k -> new ConcurrentHashMap<>());
        col.putAll(entries);
    }

    @Override
    public String get(String collection, String key) {
        var col = store.get(collection);
        return col != null ? col.get(key) : null;
    }

    @Override
    public List<String> getAll(String collection, List<String> keys) {
        var col = store.get(collection);
        if (col == null) return List.of();
        
        var results = new ArrayList<String>();
        for (var key : keys) {
            results.add(col.get(key));
        }
        return results;
    }

    @Override
    public void delete(String collection, String key) {
        var col = store.get(collection);
        if (col != null) {
            col.remove(key);
        }
    }

    @Override
    public void deleteAll(String collection, List<String> keys) {
        var col = store.get(collection);
        if (col != null) {
            for (var key : keys) {
                col.remove(key);
            }
        }
    }

    @Override
    public boolean exists(String collection, String key) {
        var col = store.get(collection);
        return col != null && col.containsKey(key);
    }

    @Override
    public List<String> scan(String collection) {
        var col = store.get(collection);
        return col != null ? List.copyOf(col.values()) : List.of();
    }

    @Override
    public List<String> scan(String collection, Predicate<String> filter) {
        return scan(collection).stream().filter(filter).collect(Collectors.toList());
    }

    @Override
    public Set<String> keys(String collection) {
        var col = store.get(collection);
        return col != null ? Set.copyOf(col.keySet()) : Set.of();
    }

    @Override
    public Set<String> collections() {
        return Set.copyOf(store.keySet());
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
        store.clear();
    }

    @Override
    public int size() {
        return store.values().stream().mapToInt(ConcurrentMap::size).sum();
    }

    @Override
    public Map<String, Object> stats() {
        return Map.of(
            "engine", name(),
            "collections", store.size(),
            "totalEntries", size(),
            "type", "in-memory"
        );
    }

    public ConcurrentMap<String, ConcurrentMap<String, String>> rawStore() {
        return store;
    }
}

package org.embeddedjnosql.db.core.cache;

import org.embeddedjnosql.db.nosql.document.Document;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

public class QueryResultCache {

    private final Map<String, CacheEntry> cache;
    private final ReadWriteLock lock;
    private final long defaultTtlMs;
    private final int maxSize;
    private long hits;
    private long misses;

    public QueryResultCache() {
        this(60_000, 1000);
    }

    public QueryResultCache(long defaultTtlMs, int maxSize) {
        this.cache = new ConcurrentHashMap<>();
        this.lock = new ReentrantReadWriteLock();
        this.defaultTtlMs = defaultTtlMs;
        this.maxSize = maxSize;
    }

    public List<Document> get(String key) {
        lock.readLock().lock();
        try {
            var entry = cache.get(key);
            if (entry != null && !entry.isExpired()) {
                hits++;
                entry.touch();
                return entry.documents();
            }
            misses++;
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void put(String key, List<Document> documents) {
        put(key, documents, defaultTtlMs);
    }

    public void put(String key, List<Document> documents, Duration ttl) {
        put(key, documents, ttl.toMillis());
    }

    public void put(String key, List<Document> documents, long ttlMs) {
        lock.writeLock().lock();
        try {
            if (cache.size() >= maxSize) {
                evictOldest();
            }
            cache.put(key, new CacheEntry(documents, ttlMs));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void invalidate(String key) {
        lock.writeLock().lock();
        try {
            cache.remove(key);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void invalidatePattern(String pattern) {
        lock.writeLock().lock();
        try {
            var keys = new ArrayList<>(cache.keySet());
            for (var key : keys) {
                if (key.contains(pattern)) {
                    cache.remove(key);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void clear() {
        lock.writeLock().lock();
        try {
            cache.clear();
            hits = 0;
            misses = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public CacheStats stats() {
        lock.readLock().lock();
        try {
            long total = hits + misses;
            double hitRate = total > 0 ? (double) hits / total : 0;
            return new CacheStats(cache.size(), maxSize, hits, misses, hitRate);
        } finally {
            lock.readLock().unlock();
        }
    }

    private void evictOldest() {
        var oldest = cache.entrySet().stream()
                .min(Comparator.comparingLong(e -> e.getValue().createdAt()))
                .map(Map.Entry::getKey);
        oldest.ifPresent(cache::remove);
    }

    public static String cacheKey(String collection, String query) {
        return collection + ":" + query;
    }

    public static <T> Function<String, List<Document>> cachedQuery(
            QueryResultCache cache, 
            Function<String, List<Document>> queryFunction) {
        return key -> {
            var cached = cache.get(key);
            if (cached != null) {
                return cached;
            }
            var result = queryFunction.apply(key);
            cache.put(key, result);
            return result;
        };
    }

    private class CacheEntry {
        private final List<Document> documents;
        private final long ttlMs;
        private final long createdAt;
        private long lastAccessed;

        CacheEntry(List<Document> documents, long ttlMs) {
            this.documents = documents;
            this.ttlMs = ttlMs;
            this.createdAt = Instant.now().toEpochMilli();
            this.lastAccessed = createdAt;
        }

        boolean isExpired() {
            return Instant.now().toEpochMilli() - createdAt > ttlMs;
        }

        void touch() {
            lastAccessed = Instant.now().toEpochMilli();
        }

        List<Document> documents() { return documents; }
        long createdAt() { return createdAt; }
    }

    public static class CacheStats {
        private final int size;
        private final int maxSize;
        private final long hits;
        private final long misses;
        private final double hitRate;

        CacheStats(int size, int maxSize, long hits, long misses, double hitRate) {
            this.size = size;
            this.maxSize = maxSize;
            this.hits = hits;
            this.misses = misses;
            this.hitRate = hitRate;
        }

        public int size() { return size; }
        public int maxSize() { return maxSize; }
        public long hits() { return hits; }
        public long misses() { return misses; }
        public double hitRate() { return hitRate; }

        @Override
        public String toString() {
            return String.format("CacheStats{size=%d, maxSize=%d, hits=%d, misses=%d, hitRate=%.2f%%}",
                size, maxSize, hits, misses, hitRate * 100);
        }
    }
}

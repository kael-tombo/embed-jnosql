package org.embeddedjnosql.db.nosql.kv;

import org.embeddedjnosql.db.core.event.EventBus;
import org.embeddedjnosql.db.core.metrics.DatabaseMetrics;
import org.embeddedjnosql.db.storage.spi.StorageEngine;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Redis-style Set data structure for EmbedJNoSQL KV engine.
 * Implements an unordered collection of unique strings.
 * 
 * Operations: sadd, srem, smembers, sismember, scard, spop
 */
public class SetBucket {

    private final String name;
    private final StorageEngine engine;
    private final EventBus eventBus;
    private final DatabaseMetrics metrics;
    private final Map<String, Instant> expirations;
    private final Map<String, Set<String>> setCache;

    public SetBucket(String name, StorageEngine engine, EventBus eventBus, DatabaseMetrics metrics) {
        this.name = name;
        this.engine = engine;
        this.eventBus = eventBus;
        this.metrics = metrics;
        this.expirations = new ConcurrentHashMap<>();
        this.setCache = new ConcurrentHashMap<>();
    }

    public String name() {
        return name;
    }

    /**
     * SADD - Add members to the set.
     * @param key The set key
     * @param members Members to add
     * @return Number of members that were added (excludes already existing)
     */
    public long sadd(String key, String... members) {
        if (members == null || members.length == 0) {
            return 0;
        }
        var set = getSet(key);
        long added = 0;
        for (String member : members) {
            if (set.add(member)) {
                added++;
            }
        }
        if (added > 0) {
            saveSet(key, set);
            metrics.recordInsert();
        }
        return added;
    }

    /**
     * SREM - Remove members from the set.
     * @param key The set key
     * @param members Members to remove
     * @return Number of members that were removed
     */
    public long srem(String key, String... members) {
        if (members == null || members.length == 0) {
            return 0;
        }
        var set = getSet(key);
        long removed = 0;
        for (String member : members) {
            if (set.remove(member)) {
                removed++;
            }
        }
        if (removed > 0) {
            saveSet(key, set);
            metrics.recordDelete();
        }
        return removed;
    }

    /**
     * SMEMBERS - Get all members in the set.
     * @param key The set key
     * @return Set of all members, empty set if key doesn't exist
     */
    public Set<String> smembers(String key) {
        var set = getSet(key);
        metrics.recordRead();
        return new LinkedHashSet<>(set);
    }

    /**
     * SISMEMBER - Check if member exists in the set.
     * @param key The set key
     * @param member The member to check
     * @return true if member exists, false otherwise
     */
    public boolean sismember(String key, String member) {
        var set = getSet(key);
        metrics.recordRead();
        return set.contains(member);
    }

    /**
     * SCARD - Get the cardinality (number of elements) of the set.
     * @param key The set key
     * @return Number of elements in the set
     */
    public long scard(String key) {
        if (isExpired(key)) {
            deleteSet(key);
            return 0;
        }
        var set = getSet(key);
        return set.size();
    }

    /**
     * SPOP - Remove and return random member(s) from the set.
     * @param key The set key
     * @return A random member, or null if set is empty
     */
    public String spop(String key) {
        var set = getSet(key);
        if (set.isEmpty()) {
            return null;
        }
        String member = set.iterator().next();
        set.remove(member);
        saveSet(key, set);
        metrics.recordRead();
        metrics.recordDelete();
        return member;
    }

    /**
     * SPOP - Remove and return multiple random members from the set.
     * @param key The set key
     * @param count Number of members to pop
     * @return List of popped members
     */
    public List<String> spop(String key, int count) {
        var set = getSet(key);
        List<String> result = new ArrayList<>();
        
        if (count <= 0 || set.isEmpty()) {
            return result;
        }
        
        int toPop = Math.min(count, set.size());
        var iterator = set.iterator();
        
        for (int i = 0; i < toPop && iterator.hasNext(); i++) {
            String member = iterator.next();
            result.add(member);
            iterator.remove();
        }
        
        saveSet(key, set);
        metrics.recordRead();
        metrics.recordDelete();
        
        return result;
    }

    /**
     * SRANDMEMBER - Get random member(s) without removing them.
     * @param key The set key
     * @return A random member, or null if set is empty
     */
    public String srandmember(String key) {
        var set = getSet(key);
        if (set.isEmpty()) {
            return null;
        }
        metrics.recordRead();
        return set.iterator().next();
    }

    /**
     * SRANDMEMBER - Get multiple random members without removing them.
     * @param key The set key
     * @param count Number of members to return
     * @return List of random members
     */
    public List<String> srandmember(String key, int count) {
        var set = getSet(key);
        List<String> result = new ArrayList<>();
        
        if (count <= 0 || set.isEmpty()) {
            return result;
        }
        
        // Convert to list for random access
        var members = new ArrayList<>(set);
        Random rand = new Random();
        int toGet = Math.min(count, members.size());
        
        for (int i = 0; i < toGet; i++) {
            int index = rand.nextInt(members.size());
            result.add(members.get(index));
        }
        
        metrics.recordRead();
        return result;
    }

    /**
     * SISMEMBER - Check if member exists (alias for sismember).
     */
    public boolean contains(String key, String member) {
        return sismember(key, member);
    }

    /**
     * SMOVE - Move member from source to destination set.
     * @param sourceKey Source set key
     * @param destKey Destination set key
     * @param member Member to move
     * @return true if member was moved, false if not in source
     */
    public boolean smove(String sourceKey, String destKey, String member) {
        var sourceSet = getSet(sourceKey);
        if (!sourceSet.contains(member)) {
            return false;
        }
        sourceSet.remove(member);
        saveSet(sourceKey, sourceSet);
        
        var destSet = getSet(destKey);
        destSet.add(member);
        saveSet(destKey, destSet);
        
        metrics.recordInsert();
        return true;
    }

    /**
     * SCARD - Get cardinality (alias for scard).
     */
    public long size(String key) {
        return scard(key);
    }

    /**
     * Set TTL for the set.
     * @param key The set key
     * @param ttl Time to live duration
     */
    public void expire(String key, Duration ttl) {
        expirations.put(key, Instant.now().plus(ttl));
    }

    /**
     * Delete the set.
     * @param key The set key
     * @return true if deleted, false if didn't exist
     */
    public boolean delete(String key) {
        expirations.remove(key);
        setCache.remove(key);
        if (engine.exists(name, key)) {
            engine.delete(name, key);
            metrics.recordDelete();
            return true;
        }
        return false;
    }

    /**
     * Get all set keys.
     */
    public Set<String> keys() {
        return engine.keys(name);
    }

    /**
     * Clear all sets in the bucket.
     */
    public void clear() {
        for (String key : keys()) {
            delete(key);
        }
        expirations.clear();
        setCache.clear();
    }

    /**
     * Get bucket statistics.
     */
    public Map<String, Object> stats() {
        long total = keys().size();
        long expired = expirations.entrySet().stream()
            .filter(e -> e.getValue().isBefore(Instant.now()))
            .count();
        long totalMembers = keys().stream().mapToLong(this::scard).sum();
        
        return Map.of(
            "name", name,
            "totalSets", total,
            "expiredSets", expired,
            "activeSets", total - expired,
            "totalMembers", totalMembers,
            "memoryEntries", expirations.size()
        );
    }

    /**
     * SINTER - Get intersection of multiple sets.
     * @param keys Set keys to intersect
     * @return Set of members in all sets
     */
    public Set<String> sinter(String... keys) {
        if (keys == null || keys.length == 0) {
            return Set.of();
        }
        
        Set<String> result = new LinkedHashSet<>(getSet(keys[0]));
        for (int i = 1; i < keys.length; i++) {
            result.retainAll(getSet(keys[i]));
        }
        
        return result;
    }

    /**
     * SUNION - Get union of multiple sets.
     * @param keys Set keys to union
     * @return Set of members in any set
     */
    public Set<String> sunion(String... keys) {
        Set<String> result = new LinkedHashSet<>();
        if (keys == null || keys.length == 0) {
            return result;
        }
        
        for (String key : keys) {
            result.addAll(getSet(key));
        }
        
        return result;
    }

    /**
     * SDIFF - Get difference between first set and others.
     * @param keys Set keys (first minus rest)
     * @return Set of members in first set but not in others
     */
    public Set<String> sdiff(String... keys) {
        if (keys == null || keys.length == 0) {
            return Set.of();
        }
        
        Set<String> result = new LinkedHashSet<>(getSet(keys[0]));
        for (int i = 1; i < keys.length; i++) {
            result.removeAll(getSet(keys[i]));
        }
        
        return result;
    }

    private Set<String> getSet(String key) {
        if (isExpired(key)) {
            deleteSet(key);
            return ConcurrentHashMap.newKeySet();
        }
        
        return setCache.computeIfAbsent(key, k -> {
            String stored = engine.get(name, key);
            if (stored == null) {
                return ConcurrentHashMap.newKeySet();
            }
            return deserializeSet(stored);
        });
    }

    private void saveSet(String key, Set<String> set) {
        String serialized = serializeSet(set);
        engine.put(name, key, serialized);
    }

    private void deleteSet(String key) {
        setCache.remove(key);
        engine.delete(name, key);
    }

    private boolean isExpired(String key) {
        var expiry = expirations.get(key);
        return expiry != null && Instant.now().isAfter(expiry);
    }

    private String serializeSet(Set<String> set) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (String member : set) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(member)).append("\"");
            first = false;
        }
        sb.append("]");
        return sb.toString();
    }

    private Set<String> deserializeSet(String json) {
        Set<String> result = ConcurrentHashMap.newKeySet();
        if (json == null || json.length() < 2) {
            return result;
        }
        
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

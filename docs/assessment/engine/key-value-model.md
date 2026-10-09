# Sub-Assessment: Key-Value Model and Data Structures

## 1. Purpose and User Outcome
Provide high-performance, embedded Key-Value buckets and Redis-compatible data structures (Lists, Sets, Hashes) with predictable time-to-live (TTL) expiration and atomic operations.

## 2. Relevant Code & Configuration
- `org.embeddedjnosql.db.nosql.kv.KeyValueBucket`
- `org.embeddedjnosql.db.nosql.kv.ListBucket`
- `org.embeddedjnosql.db.nosql.kv.SetBucket`
- `org.embeddedjnosql.db.nosql.kv.HashBucket`

## 3. Current Implementation
- `KeyValueBucket`: Stores values directly in `StorageEngine`, with expiration instants maintained in `expirations` map and persisted to `meta_store` (`kv_expirations_{name}`).
- Expirations checked lazily on `get()`, `exists()`, and counted in `count()`.

## 4. Expected Behavior and Invariants
1. Setting a key with TTL (`put(k, v, ttl)`) schedules the key to expire at `now + ttl`.
2. Overwriting an existing key *without* TTL (`put(k, v)`) must make the key persistent (clear any previously scheduled expiration).
3. Deleting a key (`delete(k)`) must remove its value and its expiration metadata.
4. Calling `get()` or `exists()` on an expired key must return `null` / `false` and evict the expired entry.

## 5. Reproduction Procedure
1. Insert a key with a TTL:
   ```java
   bucket.put("session_1", "temp_token", Duration.ofSeconds(10));
   ```
2. Immediately overwrite the key with permanent data (no TTL):
   ```java
   bucket.put("session_1", "permanent_token");
   ```
3. Sleep or advance clock past 10 seconds.
4. Attempt `bucket.get("session_1")`:
   ```java
   // Fails: returns null because expirations still holds the old 10s timestamp!
   assertNotNull(bucket.get("session_1"));
   ```

## 6. Findings and Supporting Evidence
### Finding F-03 (Severity: P1 - Data Loss via Phantom Expiration)
- **Symptom**: Keys overwritten without a TTL prematurely vanish when their former TTL expires.
- **Root Cause**: `KeyValueBucket.put(String key, String value)` calls `engine.put(name, key, value)` but never touches the `expirations` map! The prior expiration entry remains in `expirations` and in `meta_store`. When the original deadline arrives, `get()` / `exists()` observes `isExpired(key) == true` and deletes the new permanent value.

## 7. Severity & Practical Impact
- **Severity**: P1
- **Impact**: Any cache or KV workflow that refreshes or updates an expiring key into a permanent or refreshed state suffers unexpected data eviction and data loss.

## 8. Proposed Correction
In `KeyValueBucket.put(String key, String value)`:
```java
public void put(String key, String value) {
    engine.put(name, key, value);
    if (expirations.remove(key) != null) {
        saveExpirations();
    }
    metrics.recordInsert();
}
```
If an entry had a TTL, overwriting it removes the expiration and persists the updated metadata.

## 9. Dependencies & Compatibility Implications
- Zero breaking changes; aligns behavior with Redis and standard database specifications.

## 10. Implementation Steps
1. Update `KeyValueBucket.java`.
2. Add regression test in `KeyValueBucketTest.java` verifying that `put(k, v)` removes prior expiration.
3. Verify all KV tests pass.

## 11. Acceptance Criteria
- Overwriting a key with `put(k, v)` removes the key from `expirations()` and `stats().expiredKeys`.
- Reading the key after the initial TTL duration returns the overwritten value.

## 12. Remaining Limitations & Status
- Planned for implementation under task `TASK-P1-02`.

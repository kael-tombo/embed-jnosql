# JNOSQL-EMBED: Storage Engine Design & SPI

Detailed architecture of the `StorageEngine` SPI and its four concrete implementations.

---

## 1. The `StorageEngine` SPI Contract

Every storage backend in JNOSQL-EMBED implements `org.embeddedjnosql.db.storage.spi.StorageEngine`:

```java
public interface StorageEngine extends AutoCloseable {
    String name();
    void put(String collection, String key, String value);
    void putAll(String collection, Map<String, String> entries);
    String get(String collection, String key);
    List<String> getAll(String collection, List<String> keys);
    void delete(String collection, String key);
    void deleteAll(String collection, List<String> keys);
    boolean exists(String collection, String key);
    List<String> scan(String collection);
    List<String> scan(String collection, Predicate<String> filter);
    Set<String> keys(String collection);
    void flush();
    void close();
    int size();
    Map<String, Object> stats();
}
```

---

## 2. Concrete Engine Implementations

### 2.1 `InMemoryEngine`
- **Internal Storage**: `ConcurrentMap<String, ConcurrentMap<String, String>>`.
- **Characteristics**:
  - Blazing-fast sub-microsecond latency.
  - Zero filesystem I/O.
  - No CRC32 hashing overhead on read/write.
  - Ideal for unit testing, integration tests, and ephemeral session caches.

### 2.2 `FileEngine`
- **Internal Storage**: Disk-based log files coordinated by `WALManager`.
- **Characteristics**:
  - Appends operations to an active Write-Ahead Log (WAL) file.
  - Background auto-flush thread flushes memory writes to disk based on `flushIntervalMs` (default 1000ms).
  - Automatically replays the WAL on startup to restore in-memory hash indexes to the latest committed state.

### 2.3 `BTreeEngine`
- **Internal Storage**: Fixed-size disk blocks organized into a self-balancing B-Tree.
- **Characteristics**:
  - Structured for datasets that exceed available RAM.
  - Guarantees $O(\log N)$ search, insertion, and deletion times.
  - Efficient range queries and key-prefix scans.

### 2.4 `LSMTreeEngine`
- **Internal Storage**:
  - **MemTable**: In-memory write buffer (`ConcurrentSkipListMap`).
  - **WAL**: On-disk append log for MemTable durability.
  - **SSTables**: Immutable sorted string tables stored on disk.
- **Characteristics**:
  - Optimized for high-frequency write workloads (e.g. telemetry, event logs, time-series).
  - Background compaction thread merges older SSTables and prunes deleted keys.

---

## 3. Storage Engine Selection Matrix

| Engine Type | Primary Strength | Disk Persistence | Write Speed | Read Speed | Memory Overhead |
|---|---|---|---|---|---|
| `IN_MEMORY` | Sub-microsecond latency | No | Maximum (RAM) | Maximum (RAM) | Low |
| `FILE` | Simple WAL log + fast startup | Yes | High (Append) | Fast (Cached) | Medium |
| `B_TREE` | Predictable $O(\log N)$ on disk | Yes | Moderate (Disk block) | High (Page cache) | Low |
| `LSM_TREE` | High-throughput write streaming | Yes | Very High (MemTable) | Moderate (Multi-SSTable)| Medium |

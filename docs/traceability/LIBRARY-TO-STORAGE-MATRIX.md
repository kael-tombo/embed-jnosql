# Library-to-Storage Engine Traceability Matrix

**Audit Date**: September 9, 2026  
**Auditor**: Database Storage Systems Specialist  
**Scope**: Verification that domain operations reach the underlying storage engines.

---

## 1. Domain Object to Storage Engine Delegation

All EmbedJNoSQL domain abstractions (`DocumentCollection`, `KeyValueBucket`, `ColumnFamily`) encapsulate a shared `StorageEngine` SPI implementation:

```text
DocumentCollection  ──┐
KeyValueBucket      ──┼──> StorageEngine (SPI) ──> [ InMemoryEngine | FileStorageEngine | LSMTreeEngine | BTreeEngine ]
ColumnFamily        ──┘
```

| Domain Class | StorageEngine SPI Call | InMemory Implementation | FileStorageEngine Implementation | LSM-Tree Implementation | B-Tree Implementation |
|---|---|---|---|---|---|
| `DocumentCollection.insert` | `engine.put(key, bytes)` | `ConcurrentHashMap.put` | Memory write + append WAL | Write MemTable + WAL | In-memory Page Split + WAL |
| `DocumentCollection.findById` | `engine.get(key)` | `ConcurrentHashMap.get` | In-memory cache / disk read | MemTable / SSTable scan | B-Tree Node traversal |
| `DocumentCollection.deleteById`| `engine.delete(key)` | `ConcurrentHashMap.remove` | Tombstone mark + WAL | Write Tombstone to MemTable | B-Tree Key deletion |
| `KeyValueBucket.put` | `engine.put(key, bytes)` | Concurrent hash store | Key-value disk serialization | MemTable insert | B-Tree insert |
| `ColumnFamily.put` | `engine.put(key, bytes)` | Multi-version cell map | Multi-version disk record | Multi-version cell record | Multi-version cell record |
| `Transaction.commit` | `engine.flush()` | No-op (memory volatile) | Force fsync on WAL | Flush MemTable to SSTable | Flush dirty pages to disk |
| `Transaction.rollback` | `engine.rollback()` | Revert undo list | Revert undo list from WAL | Revert uncommitted batch | Revert page modifications |

---

## 2. Storage Engine Invariant Verification

Contract tests in `src/test/java/org/embeddedjnosql/db/` enforce that all four storage engines conform to the same lifecycle rules:
- Reading a non-existent key returns `null` or empty byte array.
- Writing a key and immediately reading it returns the exact byte array.
- Overwriting updates the value deterministically.
- Deleting removes the key and subsequent reads return `null`.
- Flushing ensures changes survive across process restarts (for persistent engines).

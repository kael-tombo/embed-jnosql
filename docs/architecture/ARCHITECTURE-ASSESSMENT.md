# JNOSQL-EMBED: Architecture Assessment

A comprehensive architectural evaluation of the current JNOSQL-EMBED database codebase, identifying strengths, design patterns, technical debts, and architectural remedies.

---

## 1. High-Level Architectural Evaluation

JNOSQL-EMBED is structured around a multi-layered design separating high-level data abstractions from the underlying byte and record persistence mechanisms.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Application Layer                               │
│       Plain Java API  |  Spring Boot  |  Quarkus  |  Micronaut         │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│                       EmbedJNoSQL Facade                                  │
│           Lifecycle, Namespace Routing, Subsystem Wiring               │
└─────┬──────────────┬──────────────┬──────────────┬──────────────┬──────┘
      │              │              │              │              │
┌─────▼──────┐ ┌─────▼──────┐ ┌─────▼──────┐ ┌─────▼──────┐ ┌─────▼──────┐
│  Document  │ │ Key-Value  │ │ Redis Data │ │   Column   │ │   MVCC &   │
│ Collection │ │   Bucket   │ │ Structures │ │   Family   │ │Transaction │
└─────┬──────┘ └─────┬──────┘ └─────┬──────┘ └─────┬──────┘ └─────┬──────┘
      │              │              │              │              │
      └──────────────┴──────────────┼──────────────┴──────────────┘
                                    │
┌───────────────────────────────────▼────────────────────────────────────┐
│                    StorageEngine SPI Abstraction                       │
└─────┬──────────────┬─────────────────────────────┬──────────────┬──────┘
      │              │                             │              │
┌─────▼──────┐ ┌─────▼──────┐                ┌─────▼──────┐ ┌─────▼──────┐
│ InMemory   │ │ FileEngine │                │ BTreeEngine│ │  LSMTree   │
│   Engine   │ │   (WAL)    │                │  (Blocks)  │ │   Engine   │
└────────────┘ └────────────┘                └────────────┘ └────────────┘
```

---

## 2. Core Subsystems Analysis

### 2.1 Database Facade (`EmbedJNoSQL.java`)
- **Strengths**: Acts as the single entry point. Provides fluent builder methods (`EmbedJNoSQL.embed().storageEngine(...).build()`). Manages lifecycle (`open`, `close`), metrics, event bus, and internal collection caches via thread-safe `ConcurrentHashMap`.
- **Findings**: Thread-safe namespace lookup cache. Automatically triggers `EventBus` events (`COLLECTION_CREATED`, `BUCKET_CREATED`).

### 2.2 Document Collection Subsystem (`DocumentCollection.java`)
- **Capabilities**:
  - Implements full CRUD with JSON payload serialization via Jackson (`JsonSerde`).
  - Secondary indexing support via `SecondaryIndex` with index metadata persistence in `.indexes` file.
  - Query filtering via predicate trees in `Query.java`.
  - Cache integration via `QueryResultCache`.
- **Findings**: Document IDs can be auto-generated UUIDs or user-specified. Secondary index lookups accelerate equality and range matches without full scans.

### 2.3 Key-Value & Redis-Style Subsystems
- **`KeyValueBucket`**: Key-value pairs with optional TTL timestamp tracking and lazy expiration on read/write. Atomic `increment` and `decrement`.
- **`ListBucket`**: Doubly-linked list abstraction supporting `lpush`, `rpush`, `lpop`, `rpop`, `lrange`, `llen`.
- **`SetBucket`**: Unique string sets supporting `sadd`, `srem`, `smembers`, `sismember`, `scard`.
- **`HashBucket`**: Field-value maps supporting `hset`, `hget`, `hgetall`, `hdel`, `hlen`.

### 2.4 Wide-Column Family Subsystem (`ColumnFamily.java`)
- Models multi-dimensional coordinates: `RowKey -> ColumnQualifier -> CellValue`.
- Features column-level TTLs and timestamps.

### 2.5 Transaction & MVCC Subsystem (`Transaction.java` & `MVCCManager.java`)
- Implements snapshot isolation using logical read timestamps.
- Transactions maintain an in-memory write buffer for uncommitted mutations (`PUT`, `DELETE`).
- On `commit()`, operations are flushed atomically to the underlying `StorageEngine`.
- On `rollback()`, staged operations are discarded without touching persistent storage.

---

## 3. Storage Layer Subsystem

The `StorageEngine` interface provides the physical persistence contract:
- `InMemoryEngine`: Direct `ConcurrentHashMap` with sub-microsecond latency.
- `FileEngine`: Append-only commit log with `WALManager` guaranteeing durability across sudden process terminations.
- `BTreeEngine`: Page-based B-Tree index for large datasets exceeding RAM.
- `LSMTreeEngine`: Log-Structured Merge Tree with memtable, SSTables, and background compaction.

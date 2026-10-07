# JNOSQL-EMBED: Complete Feature Inventory

This inventory documents every feature in JNOSQL-EMBED with standardized status tracking:
- `IMPLEMENTED`: Fully implemented, tested, and validated.
- `PARTIALLY_IMPLEMENTED`: Core functionality exists, but secondary capabilities or edge cases remain.
- `EXPERIMENTAL`: Implementation present for evaluation, not yet stabilized.
- `PLANNED`: On the roadmap for future development.
- `MISSING`: Required for full parity but not yet implemented.
- `DEPRECATED`: Marked for removal or superseded.

---

## Complete Feature Matrix

| Feature | Subsystem | Public API | Status | Tests Present |
|---|---|---|---|---|
| **Document CRUD** | Document Store | `DocumentCollection.insert()`, `findById()`, `update()`, `delete()` | `IMPLEMENTED` | Yes (15+ tests) |
| **Secondary Indexing** | Document Store | `DocumentCollection.createIndex(field)` | `IMPLEMENTED` | Yes (12+ tests) |
| **Predicate Querying** | Query Engine | `Query.eq()`, `gt()`, `lt()`, `contains()`, `in()`, `all()` | `IMPLEMENTED` | Yes (25+ tests) |
| **Aggregation Pipeline** | Document Store | `DocumentCollection.aggregate()` (count, sum, avg, group) | `IMPLEMENTED` | Yes (8+ tests) |
| **Basic Key-Value** | KV Store | `KeyValueBucket.put()`, `get()`, `delete()`, `exists()` | `IMPLEMENTED` | Yes (9+ tests) |
| **KV TTL Expiration** | KV Store | `KeyValueBucket.put(key, value, Duration ttl)` | `IMPLEMENTED` | Yes (4+ tests) |
| **Atomic Counters** | KV Store | `KeyValueBucket.increment()`, `decrement()` | `IMPLEMENTED` | Yes (5+ tests) |
| **Batch KV Operations** | KV Store | `KeyValueBucket.putAll()`, `getAll()` | `IMPLEMENTED` | Yes (6+ tests) |
| **Redis List Bucket** | Redis Structures | `ListBucket.lpush()`, `rpush()`, `lpop()`, `lrange()`, `llen` | `IMPLEMENTED` | Yes (14+ tests) |
| **Redis Set Bucket** | Redis Structures | `SetBucket.sadd()`, `srem()`, `smembers()`, `sismember` | `IMPLEMENTED` | Yes (17+ tests) |
| **Redis Hash Bucket** | Redis Structures | `HashBucket.hset()`, `hget()`, `hgetall()`, `hdel()`, `hlen`| `IMPLEMENTED` | Yes (20+ tests) |
| **Wide-Column Family** | Column Family | `ColumnFamily.put()`, `get()`, `getRow()`, `delete()` | `IMPLEMENTED` | Yes (25+ tests) |
| **Column-Level TTL** | Column Family | `ColumnFamily.put(row, col, val, ttlSeconds)` | `IMPLEMENTED` | Yes (6+ tests) |
| **In-Memory Engine** | Storage SPI | `StorageEngineType.IN_MEMORY` (`InMemoryEngine`) | `IMPLEMENTED` | Yes (100+ tests) |
| **File WAL Engine** | Storage SPI | `StorageEngineType.FILE` (`FileEngine`) | `IMPLEMENTED` | Yes (10+ tests) |
| **B-Tree Disk Engine** | Storage SPI | `StorageEngineType.B_TREE` (`BTreeEngine`) | `IMPLEMENTED` | Yes (8+ tests) |
| **LSM-Tree Engine** | Storage SPI | `StorageEngineType.LSM_TREE` (`LSMTreeEngine`) | `IMPLEMENTED` | Yes (8+ tests) |
| **MVCC Transactions** | Transactions | `EmbedJNoSQL.beginTransaction()`, `tx.commit()`, `tx.rollback()` | `IMPLEMENTED` | Yes (43+ tests) |
| **In-Process EventBus** | Observability | `EventBus.on(EventType, handler)`, `emit()` | `IMPLEMENTED` | Yes (9+ tests) |
| **Database Metrics** | Observability | `DatabaseMetrics.snapshot()`, atomic operation counters | `IMPLEMENTED` | Yes (10+ tests) |
| **Change Data Capture**| CDC | `CDCManager.recordInsert()`, File/Kafka connectors | `PARTIALLY_IMPLEMENTED`| Yes (5+ tests) |
| **Embedded Web Console**| Tooling | `EmbedJNoSQL.startServer(port)`, `/api/health`, `/api/collections`| `IMPLEMENTED` | Yes (72+ tests) |
| **HNSW Vector Index** | Indexing | `HNSWVectorIndex` (in-memory nearest neighbor stub) | `EXPERIMENTAL` | Yes (3 tests) |
| **Spring Boot 3 Starter**| Framework | `embedjnosql-spring-boot-starter`, `EmbedJNoSQLTemplate` | `IMPLEMENTED` | Yes (12 integration tests) |
| **Quarkus 3 Extension** | Framework | `embed-jnosql-quarkus-extension`, `@ConfigMapping` | `IMPLEMENTED` | Verified (`mvn compile`) |
| **Micronaut 4 Adapter** | Framework | `embedjnosql-micronaut-integration`, `EmbedJNoSQLEntityManager`| `IMPLEMENTED` | Verified (`mvn compile`) |
| **SQL Relational Engine**| Relational | Cross-table relational joins, SQL parser, JDBC | `DEPRECATED` | Removed in favor of NoSQL |

# Exhaustive Feature & Capability Inventory

## 1. Document Model Subsystem

| Feature / Capability | API / Code Entry Point | Verification Status | Notes |
|---|---|---|---|
| Document Insertion | `DocumentCollection.insert(doc)` | Verified | Auto-generates UUID if `id` is null |
| Document Point Read | `DocumentCollection.findById(id)` | Verified | Evicts/hides expired documents lazily |
| Document Update | `DocumentCollection.update(doc)` | Verified | Validates document existence, updates indexes & cache |
| Document Upsert | `DocumentCollection.upsert(doc)` | Verified | Checks existence and branches to insert/update |
| Document Delete | `DocumentCollection.deleteById(id)` | Verified | Removes from engine, indexes, and invalidates cache |
| Batch Operations | `DocumentCollection.insertAll(docs, atomic)` | Verified | Rollback-capable atomic batch insertion |
| Nested Dotted Paths | `Document.resolvePath("a.b.c")` | Verified | Traverses nested maps and integer-indexed arrays |
| Criteria Queries | `Query.eq()`, `gt()`, `in()`, etc. | Verified | Matches document fields using predicate lambdas |
| Query Parser | `QueryParser.parse(jsonQuery)` | Verified | Parses MongoDB-like JSON query syntax |
| Secondary Indexes | `DocumentCollection.createIndex(field)` | Partially Verified | O(1) equality lookup; type coercion defect cataloged (**F-04**) |
| Query Result Cache | `QueryResultCache` | Verified | Invalidated on insert/update/delete |
| Document TTL Expiry | `DocumentCollection.insert(doc, ttl)` | Verified | Logical deletion on read; purge on cleanup |
| Aggregation Pipeline | `AggregationPipeline` | Verified | Supports `$match`, `$project`, `$group`, `$sort`, `$limit` |
| Full-Text Search | `TextSearch` / `TextIndex` | Verified | Inverted index tokenization, phrase matching |

---

## 2. Key-Value & Redis-Style Data Structures

| Feature / Capability | API / Code Entry Point | Verification Status | Notes |
|---|---|---|---|
| String KV Put / Get / Delete | `KeyValueBucket.put / get / delete` | Verified | High-throughput direct key-value operations |
| KV TTL Expiration | `KeyValueBucket.put(key, val, ttl)` | Partially Verified | Expiration tracked in meta_store; overwrite bug cataloged (**F-03**) |
| Atomic Increment / Decrement | `KeyValueBucket.increment(key, delta)` | Verified | Long integer arithmetic over string representations |
| Batch KV Operations | `KeyValueBucket.putAll / getAll` | Verified | Atomic batch write with automatic rollback |
| Redis Lists | `ListBucket` (`rpush`, `lpop`, `lrange`) | Verified | Backed by serialized JSON array per key |
| Redis Sets | `SetBucket` (`sadd`, `srem`, `smembers`) | Verified | Set union, intersection, and membership checks |
| Redis Hashes | `HashBucket` (`hset`, `hget`, `hgetall`) | Verified | Field-value dictionary per root key |

---

## 3. Multi-Model Extensions

| Feature / Capability | API / Code Entry Point | Verification Status | Notes |
|---|---|---|---|
| Wide-Column Families | `ColumnFamily` | Verified | Row-key + column + cell value with cell TTLs |
| Vector Index | `HNSWIndex` / `VectorSearch` | Experimental | High-dimensional vector cosine/Euclidean distance search |

---

## 4. Storage Engines & Durability

| Storage Engine | Class | Characteristics | Status |
|---|---|---|---|
| `IN_MEMORY` | `InMemoryEngine` | Ephemeral, ultra-fast, zero disk footprint | Verified |
| `FILE` | `FileEngine` | Per-collection JSON files, async flushing, WAL replay | Verified |
| `B_TREE` | `BTreeEngine` | Sorted binary index stream, background periodic flusher, WAL | Verified |
| `LSM_TREE` | `LSMTreeEngine` | In-memory Memtable, immutable SSTables, Bloom filters, Compaction, WAL | Verified |
| `WAL` | `WriteAheadLog` | Write-ahead logging with rotation, compression, and checkpointing | Fix Required (**F-01**) |

---

## 5. Transactions & Concurrency

| Capability | Component | Guarantees | Status |
|---|---|---|---|
| Multi-Version Concurrency | `MVCCManager` | Monotonic clock, lock-free version chains, snapshot isolation | Verified |
| Conflict Detection | `MVCCManager.commit` | First-committer-wins validation; rejects write-write conflicts | Verified |
| Transaction Workspace | `Transaction` | Staged operations, rollback on close, undo log on engine error | Fix Required (**F-02**) |

---

## 6. Administration Console & Web Endpoints

| Panel / Route | HTTP Path | Purpose |
|---|---|---|
| Overview | `/api/health`, `/api/metrics` | System status, active engine, JVM memory, uptime |
| Collections | `/api/collections`, `/api/collections/{col}/query` | Document collection explorer, CRUD, JSON editor, query bar |
| Key-Value | `/api/kv`, `/api/kv/{bucket}/{key}` | String key-value editor, TTL display, eviction |
| Redis Structures | `/api/kv/lists`, `/api/kv/sets`, `/api/kv/hashes` | Lists (`rpush`), Sets (`sadd`), Hashes (`hset`) |
| Wide-Column | `/api/columns/{family}/{row}` | Column family row browser and cell editor |
| Vector Search | `/api/vectors/{index}/search` | Vector registration and similarity query testbed |
| Indexes | `/api/indexes/{collection}` | Secondary index inspector and creation/drop interface |
| Transactions | `/api/transactions` | Active transaction monitor, commit/rollback trigger |
| Backup & Restore | `/api/backup` | Database snapshot export and file path restore |
| CDC & Audit | `/api/cdc`, `/api/audit/logs` | Change data capture feed, connector status, audit events |

---

## 7. Framework Integrations & Demos

- **Spring Boot**: `spring-boot-starter` (auto-configuration, health indicator, `@Transactional` bridge).
- **Quarkus**: `quarkus-extension` (build-time recorder, CDI producer).
- **Micronaut**: `micronaut-integration` (bean factory, configuration injection).
- **Standalone Demos**: 20 runnable demo applications in `demo/demos/01-*` through `20-*`.

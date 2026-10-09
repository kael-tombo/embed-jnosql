# Product Scope & Contract Verification

## 1. NoSQL-Only Product Identity

**EmbedJNoSQL** is strictly an embedded NoSQL database for JVM applications.

### In-Scope Core Capabilities:
1. **Document Database**:
   - JSON-style document storage with arbitrary schema.
   - Dotted nested path resolution (`user.address.city`, `items.0.sku`).
   - MongoDB-style criteria filters (`$eq`, `$ne`, `$gt`, `$gte`, `$lt`, `$lte`, `$in`, `$nin`, `$regex`, `$exists`, `$and`, `$or`).
   - Secondary field indexing for O(1) equality lookup and range queries.
   - Per-document TTL expiration with automatic lazy and background cleanup.
2. **Key-Value Store**:
   - High-throughput string key-value buckets with atomic increments.
   - Redis-style data structures: Lists (`RPUSH`, `LPOP`, `LRANGE`), Sets (`SADD`, `SREM`, `SMEMBERS`), Hashes (`HSET`, `HGET`, `HGETALL`).
   - TTL assignment, expiry inspection, and eviction.
3. **Multi-Model Extensions**:
   - Wide-column families (row-key + column-name + cell value).
   - Vector index with HNSW similarity search (experimental).
4. **Pluggable Storage SPI**:
   - `IN_MEMORY`: volatile, zero file I/O, ideal for unit testing.
   - `FILE`: human-readable JSON per collection with WAL recovery.
   - `B_TREE`: binary node format for ordered scanning and point queries.
   - `LSM_TREE`: append-only SSTables with Bloom filters and background compaction.
5. **Embedded Operations**:
   - Zero daemon, zero external processes, zero Docker containers required.
   - Embedded administration console serving single-page application directly over lightweight HTTP.

### Strictly Out-of-Scope (Removed Relational Remnants):
- Relational SQL engines, SQL parsers, and SQL execution engines.
- SQL editors / "SQL Studio" in the console.
- JDBC drivers and JDBC connection pools.
- Relational tables, foreign keys, `JOIN` queries, and relational DDL (`CREATE TABLE ...`, `ALTER TABLE ...`).
- Dual-engine marketing, positioning, or runtime modes.

### Verification of Relational Removal:
1. `FullFeatureTest.java` and `ConsoleWorkspaceEndpointsTest.java` enforce that old relational routes (`/api/tables`, `/api/sql`, `/api/constraints`) return HTTP 404.
2. Web console (`static/index.html` and `static/js/console.js`) has 0 references to SQL or dual-engine selectors.
3. Login interface (`static/login.html`) has been cleansed of dual-engine badges.
4. Legacy documentation files in `docs/` containing pre-refactor dual-engine designs have been explicitly marked with `SUPERSEDED` headers or retired.

---

## 2. Product Claims vs. Observed Implementation Contracts

| Claim | Marketing Claim | Actual Engine Guarantee | Assessment & Verification Status |
|---|---|---|---|
| **ACID Transactions** | "Full ACID Transactions" | **Snapshot Isolation (MVCC) + Single-Thread Commit Undo Log** | **Partially Verified with Defects**. Transactions provide snapshot isolation via `MVCCManager` for point operations. However, `TransactionalCollection` bypassed staging for updates, breaking atomicity and isolation (**F-02**). Distributed ACID is not supported (single-node embedded only). |
| **Durability & Crash Safety** | "Crash-safe write-ahead logging across all durable engines" | **Synchronous WAL append with checkpointing; file snapshot replay** | **Partially Verified with Defects**. When `fsync` is called, data is committed. However, `fsync()` was syncing an unwritten `FileOutputStream` while `logWriter` used a separate `FileWriter` (**F-01**). Windows file locking also triggered stream closure on checkpoint. |
| **Thread Safety** | "Fully concurrent, lock-free reads and synchronized writes" | **Concurrent collections + ReentrantReadWriteLock** | **Verified**. `DocumentCollection` uses `ConcurrentHashMap` for indexes, `MVCCManager` uses atomic timestamps and immutable version nodes. Engine flushes synchronize on write barriers. |
| **Zero Configuration** | "Zero configuration, works in one line of code" | **`EmbedJNoSQL.inMemory()` & `EmbedJNoSQL.openTemp()`** | **Verified**. Requires 0 external setup or configuration files; starts in milliseconds. |
| **Compact Distribution** | "<5 MB total footprint" | **2.94 MB shaded uber-jar** | **Verified**. Shaded JAR measures 2.94 MB, core unshaded JAR is 0.56 MB. Console assets measure 218 KB. |
| **Jakarta NoSQL Compatibility** | "Jakarta NoSQL & JPA annotation support" | **Reflection-based mapping for `@Entity`, `@Id`, `@Column`** | **Verified**. Maps annotations directly onto documents without requiring a full CDI container runtime. |
| **High Performance** | "Sub-millisecond reads and writes" | **Microsecond in-memory operations; bounded file I/O** | **Verified**. In-memory CRUD executes in <1 ms across unit and integration tests. |

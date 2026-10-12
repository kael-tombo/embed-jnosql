# Product Contract & Architecture Boundaries

**Document ID**: PRD-CONTRACT-1.0.1  
**Status**: APPROVED & BINDING  

---

## 1. Core Identity & Philosophy

**EmbedJNoSQL** is an embedded, pure-JVM NoSQL database engine designed to run inside application processes and automated testing pipelines.

### What EmbedJNoSQL Is:
- **Embedded Document & Key-Value Database**: In-process execution with zero separate daemons and zero external network ports required.
- **Multi-Model on Single Substrate**: Document collections, Key-Value buckets, Redis-style lists/sets/hashes, and Wide-Column families share the same storage engine, transactions, and WAL.
- **Testcontainers & Docker Alternative**: Instant JVM cold-start (<50 ms in-memory) eliminating container overhead in CI/CD environments.
- **Jakarta NoSQL & Eclipse JNoSQL Native**: Implements document templates and repository patterns adhering to Jakarta Data / NoSQL specifications.

### What EmbedJNoSQL Is NOT (Explicit Boundaries):
- **Not an RDBMS**: No SQL parser, no joins, no tables, no JDBC driver.
- **No Foreign Key Enforcement**: References between documents are application-level keys; the engine does not perform referential cascade checks.
- **No Distributed Clustering**: Single JVM process database; it does not implement Raft, Paxos, or sharding across nodes.

---

## 2. Capability Matrix & Contract Guarantees

| Capability | Supported Contract | Underlying Mechanism | Durability Guarantee |
|---|---|---|---|
| **Document Storage** | Schemaless JSON-like documents with optional schema validation | `DocumentCollection` | In-Memory or fsync WAL |
| **Document Queries** | Dotted-path filter predicates (`$eq`, `$ne`, `$gt`, `$gte`, `$lt`, `$lte`, `$in`, `$nin`, `$regex`, `$exists`), compound `$and`/`$or`, sorting, pagination | `QueryParser` & in-memory stream evaluation | Point-in-time snapshot |
| **Secondary Indexes** | Per-field hash indexes for O(1) point lookups | `SecondaryIndex` | Maintained synchronously on insert/update |
| **Key-Value Store** | String keys and values with per-key TTL | `KeyValueBucket` | In-Memory or fsync WAL |
| **Lists, Sets, Hashes** | Redis-equivalent data structures | `ListBucket`, `SetBucket`, `HashBucket` | In-Memory or fsync WAL |
| **Wide-Column** | Sparse row-column families with cell timestamps | `ColumnFamily` | In-Memory or fsync WAL |
| **ACID Transactions** | Read Committed & Snapshot Isolation via MVCC | `TransactionManager` & Version chains | Atomic commit / rollback |
| **Durability & WAL** | Crash recovery after abrupt process termination | Append-only WAL with CRC32 checksums & checkpointing | Process kill verified; Graceful restart verified |
| **Change Data Capture** | Async event stream of mutations | `EventBus` + File CDC sink | Logged to target path |
| **Vector Search (Experimental)** | Fixed 128-dimensional Cosine similarity | `VectorIndex` (HNSW) | Excluded from stability guarantees |

---

## 3. Footprint & Packaging Invariants

1. **Artifact Target**: Shaded Core JAR <= 3.5 MB (Measured: **3.11 MB**).
2. **Runtime Dependencies**: Strictly 3 external dependencies:
   - `com.fasterxml.jackson.core:jackson-databind`
   - `com.fasterxml.jackson.datatype:jackson-datatype-jsr310`
   - `org.slf4j:slf4j-api`
3. **Java Baseline**: Java 17 minimum; builds and runs on Java 17, 21, and 25.

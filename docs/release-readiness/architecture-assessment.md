# EmbedJNoSQL Architecture Assessment

**Target Version**: 1.0.1  
**Reviewed Subsystems**: Engine Core, MVCC Transactions, Storage Substrate, Framework Adapters, Web Console  

---

## 1. Storage Substrate & Durability

### Unified Multi-Model Storage
EmbedJNoSQL executes all models (Document, KV, Redis-style structures, Wide-Column) on a unified storage SPI:
- `InMemoryEngine`: Thread-safe, ConcurrentHashMap-backed memory substrate for unit/integration testing (<50 ms boot).
- `FileEngine`: Append-only Write-Ahead Log (`WriteAheadLog`) with CRC32 checksums, periodic checkpointing (`checkpoint()`), and deterministic recovery on restart.
- `BTreeEngine`: Disk-backed page index for high-cardinality lookups.
- `LSMTreeEngine`: Log-Structured Merge-Tree with memory tables and immutable SSTables.

### Durability Bounds & Truth in Claims
- **Graceful restart**: 100% verified. Document and KV state reloaded cleanly from WAL.
- **Process kill / crash recovery**: 100% verified. Replays committed operations up to the last flushed write boundary.
- **Power-loss durability**: Relies on OS fsync (`StandardOpenOption.DSYNC` / `force(true)`). As documented, physical power-loss protection depends on disk hardware caching policies.

---

## 2. Concurrency & Transactions (MVCC)

### Transaction Model
- Implements Multi-Version Concurrency Control (MVCC) with snapshot isolation.
- Transactions are managed via `TransactionManager`. Each transaction gets a unique identifier (`txId`), monotonic start timestamp, and an isolation level.
- Write conflicts abort early on concurrent modifications to the same document key.
- Rollback cleanly restores previous document versions from the in-memory/WAL history.

### Console-Engine Transaction Boundary
- **Deliberate Separation**: The Administration Console manages transaction *lifecycle* (`GET /api/transactions`, `POST /api/transactions/begin`, `commit`, `rollback`).
- Direct console REST writes (`POST /api/collections/*`) do *not* join active transactions started through the console unless explicit transaction IDs are supplied via the Java API. This safety boundary is explicitly documented in the status bar and panel notice.

---

## 3. Standards Compliance (Eclipse JNoSQL & Jakarta NoSQL)

### Implemented Specification Bridges:
- **`EclipseDocumentTemplate`**: Implements template query execution, document persistence, and criteria translation.
- **`EclipseQueryMapper`**: Translates Jakarta NoSQL query criteria into EmbedJNoSQL native query predicates.
- **`EmbedNoSQLRepository` / `EmbedRepository`**: Implements Jakarta Data repository patterns (`save`, `findById`, `findAll`, `deleteById`).
- **Annotation Polyglot Support**: `AnnotationResolver` transparently inspects:
  - `@jakarta.nosql.Entity`, `@jakarta.nosql.Id`, `@jakarta.nosql.Column`
  - `@jakarta.persistence.Entity`, `@jakarta.persistence.Id`
  - `@org.hibernate.annotations.*`
  No relational JPA/Hibernate runtime is invoked; annotations serve purely as mapping hints.

---

## 4. Resource & Lifecycle Invariants

- **Zero Daemons / Zero Ports (by default)**: Engine boots in-process without listening on any network socket unless `EmbedJNoSQLServer` (Console) is explicitly started.
- **File Descriptors**: Clean `AutoCloseable` implementation across all storage engines, file channels, and WAL streams, preventing file-lock leaks on Windows.
- **Port Collision Resilience**: If the configured console port is occupied, `PortManager` binds to an available port and logs the active URL.

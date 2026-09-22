# Changelog

All notable changes to **JunifyDB** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-09-09

### Added
- **Multi-Model NoSQL Engine**:
  - Document Collections supporting nested JSON, schema-free storage, and TTL.
  - Secondary Inverted Indexes supporting fast exact-match lookup.
  - Aggregation Pipelines supporting `sum`, `avg`, `min`, `max`, and `groupBy`.
  - Redis-compatible Key-Value data structures: Simple KV, Hash Bucket, List Bucket, and Set Bucket.
  - Wide-Column Family store supporting column-level TTL, range queries, and pagination.
- **Pluggable Storage Engine Architecture**:
  - `IN_MEMORY`: Ultra-fast `ConcurrentHashMap` with CRC32 removal for maximum throughput.
  - `FILE`: Human-readable JSON disk persistence with background flush.
  - `B_TREE`: Clustered binary index with fast range scans.
  - `LSM_TREE`: Log-Structured Merge Tree with MemTable, Bloom Filter, immutable SSTables, and compaction.
- **ACID Transaction Manager**:
  - MVCC multi-version concurrency control with snapshot isolation.
  - Atomic commit, rollback, and conflict detection.
  - Write-Ahead Log (WAL) with deterministic fsync crash durability.
- **JVM Framework Integrations**:
  - `junify-db-spring-boot-starter`: Spring Boot auto-configuration and `JunifyDBTemplate`.
  - `junify-db-quarkus-extension`: Quarkus SmallRye config and CDI `@DefaultBean` producers.
  - `junifydb-micronaut-integration`: Micronaut `@Factory` and Serde reflection-free serialization.
  - Eclipse Vert.x: Non-blocking reactive verticle examples with event loop protection.
- **Developer Experience & Administration**:
  - Built-in HTTP Admin Server and Developer Console (`JunifyDBServer`).
  - API Key authentication and audit logging.
  - Production Demonstration Suite with canonical E-Commerce domain (`demo/`).
  - Comprehensive documentation covering Vision, Architecture, Features, Testing, and Runbooks.

### Fixed
- Fixed Windows file-locking defect in `WriteAheadLog` by ensuring `logFileOutputStream` closes cleanly on database shutdown.
- **Release-audit fixes (September 2026):**
  - MVCC write-write conflict detection was unreachable; commits now validate the write set against the transaction's snapshot timestamp (first-writer-wins). `MVCCManager.commit(txId, commitTs, readTimestamp)` added; the 2-arg overload is retained.
  - `FileEngine` now replays its write-ahead log on startup (entries newer than the last checkpoint), so writes not yet flushed to JSON snapshots are no longer lost on an unclean shutdown.
  - `LSMTreeEngine` now replays its WAL even when SSTables exist, adds recovered keys to the bloom filter, and initializes its WAL writer after recovery; compaction and SSTable ordering are consistently oldest-to-newest with newest-first reads.
  - Collections persisted by a previous run are re-exposed after restart via the new `StorageEngine.collectionNames()` SPI (`JunifyDB.getCollectionNames()` now reflects on-disk state for the FILE engine).
- Resolved LSM-Tree bloom filter cold-restart bug by populating filter directly from loaded SSTables.
- Fixed B-Tree engine shutdown check-open order to guarantee dirty keys flush before marking engine closed.
- **Improvement round 1 (September 2026):**
  - CDC change feed is now wired to the write path (inserts, updates, deletes and upserts emit events on a dedicated system channel that survives user listener `clear()`).
  - Corrupt collection snapshots are quarantined to `.quarantine/` at startup instead of blocking boot; snapshot writes are atomic (temp file + `ATOMIC_MOVE`).
  - WAL entries larger than 64 MB are rejected up front instead of risking memory exhaustion.
  - CORS: wildcard origins are no longer combined with `Allow-Credentials: true`.
  - CI: 70% coverage gate enforced, starter/CLI module job, demo suites job, OWASP dependency scan; canonical `mvnw` wrapper.
- **Improvement round 2 (September 2026):**
  - Typed exception hierarchy (`JunifyDBException` → `StorageException` / `SerializationException`) replaces bare `RuntimeException` wraps.
  - Transaction commits are serialized under a commit lock, staged deletes are now conflict-checked, and the apply phase uses an undo log so engine failure rolls back partial effects.
  - Secondary-index queries perform true point lookups (`lookup(value)`) instead of scanning the entire index.
  - Vector indexes derive dimensionality from the first stored vector (or an explicit `dims` request) instead of a hardcoded 128.
  - Audit trail is persisted as JSONL to `<dataDir>/audit.log` for disk-backed engines (fail-open, closed on shutdown).
  - The standalone CLI shell module was recovered (buildable, runnable, CI-integrated).
- **Final validation round (September 2026):**
  - Core runtime footprint reduced from 6.9 MB to **2.89 MB** by excluding the unused byte-buddy transitive; a CI size gate enforces the 5 MB limit.
  - Console rebranded to the canonical yellow/amber Volt identity (accessible contrast pairings, shared Volt favicon/logo with the website).
  - The SQL console (`POST /api/sql`) audits mutation statements the same way as REST CRUD (`SqlAuditTrailTest`); reads are not audited.
  - Public website corrected to claim only reproducible facts (built-in SQL dialect, indicative performance figures, real Maven coordinates `org.junify.db:junify-db-core`).

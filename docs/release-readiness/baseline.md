# Release Readiness Baseline — EmbedJNoSQL v1.0.1

**Audit Date**: 2026-10-12  
**Target Version**: 1.0.1 (GA)  
**Lead Architect & Reviewer**: Senior JVM Database Engineer & Systems Architect  
**Git Commit**: `eeb81ae` (Branch: `main`)  
**Tag**: `v1.0.1`  
**Repository**: `https://github.com/kael-tombo/embed-jnosql`  

---

## 1. Environment & Build Baseline

| Property | Value |
|---|---|
| **Operating System** | Windows Server 2022 (amd64, 10.0) |
| **Java Runtime** | Temurin-25.0.4.1 (Eclipse Adoptium OpenJDK 25) |
| **Java Target** | Java 17+ bytecode compatibility (`<maven.compiler.source>17</maven.compiler.source>`) |
| **Build Tool** | Apache Maven 3.9.15 |
| **Artifact ID** | `org.embeddedjnosql.db:embed-jnosql-core:1.0.1` |
| **Shaded JAR Size** | ~3.11 MB (`target/embed-jnosql-core-1.0.1.jar`) |
| **Mandatory Runtime Dependencies** | `jackson-databind` (2.17.2), `jackson-datatype-jsr310` (2.17.2), `slf4j-api` (2.0.16) |

---

## 2. Source Code Architecture & Module Map

1. **Core Storage & Engine (`org.embeddedjnosql.db.storage`)**:
   - `IN_MEMORY`: High-speed concurrent map storage for ephemeral testing and caching.
   - `FILE`: Segmented append-only Write-Ahead Log (WAL) with CRC32 checksums, checkpointing, and automatic crash-recovery replay.
   - `B_TREE`: B+ Tree indexed page storage engine for fast random-access key ranges.
   - `LSM_TREE`: Log-Structured Merge-Tree storage engine with memtable flushing and SSTable compaction.
2. **NoSQL Data Models (`org.embeddedjnosql.db.nosql`)**:
   - **Document**: `DocumentCollection`, schemaless JSON-like documents, dotted-path navigation, query filters, secondary indexes, aggregation, and TTL.
   - **Key-Value**: `KeyValueBucket` supporting string values and per-key TTL.
   - **Redis-Style Data Structures**:
     - `ListBucket`: Doubly-ended list operations (`rpush`, `lpush`, `rpop`, `lpop`, `lrange`, `llen`).
     - `SetBucket`: Mathematical set operations (`sadd`, `srem`, `smembers`, `sismember`, `scard`).
     - `HashBucket`: Nested key-value map structures (`hset`, `hget`, `hdel`, `hgetall`).
   - **Wide-Column Families**: `ColumnFamily` row-column key matrix with per-cell timestamping and TTL.
3. **Transaction Subsystem (`org.embeddedjnosql.db.transaction.mvcc`)**:
   - Multi-Version Concurrency Control (MVCC) transactional engine supporting snapshot isolation, optimistic write conflict aborts, transaction lifecycle pool (`GET /api/transactions`), and atomic commit/rollback.
4. **Standards & Integration Adapters (`org.embeddedjnosql.db.adapter.jnosql`)**:
   - **Eclipse JNoSQL / Jakarta NoSQL**: `EclipseDocumentTemplate`, `EclipseQueryMapper`, `EmbedNoSQLRepository`, and `EmbedRepository`.
   - **Annotation Resolver**: Unified runtime mapping for Jakarta NoSQL (`@Entity`, `@Id`, `@Column`), Jakarta Persistence (`jakarta.persistence.*`), and Hibernate (`org.hibernate.annotations.*`).
   - **Spring Boot Starter (`embed-jnosql-spring-boot-starter`)**: Auto-configuration, repository factory, and health indicators.
5. **Administration Console (`org.embeddedjnosql.db.console`)**:
   - Embedded lightweight HTTP management server (`EmbedJNoSQLServer`) serving vanilla ES2020 Single Page Application (`index.html`, `console.js`, `console.css`).
   - Secure REST API (`/api/*`) for database management, query execution, metrics, transactions, backup, and CDC.

---

## 3. Test Baseline

- **Total Test Suites**: 77 test classes
- **Total Tests Executed**: 943 tests
- **Failures / Errors**: 0 / 0
- **Execution Time**: ~45–60 seconds across all cores
- **Test Categories**:
  - Unit tests for all data models and query parser
  - Concurrency and lock contention stress tests
  - Crash recovery, WAL truncation, and power-failure simulation tests
  - Browser/Console REST endpoint contract gates
  - Spring Boot & Eclipse JNoSQL end-to-end integration tests

# EmbedJNoSQL — Claims and Evidence Matrix

**Audit Date**: September 9, 2026  
**Evaluation Standard**: Strict empirical proof rule. Claims without executable tests are classified as `NOT_IMPLEMENTED` or `UNKNOWN`.

---

## 1. Capability Verification Matrix

| Claimed Feature | Subsystem | Claimed Status | Empirical Proof | Verifying Test / Command | Verified Status |
|---|---|---|---|---|---|
| **Embedded Lifecycle** | Core | Supported | Process startup, clean shutdown, resource cleanup proven. | `FullIntegrationTest.testDatabaseLifecycle` | **PASS** |
| **In-Memory Engine** | Storage | Supported | Fast concurrency, volatile data structures, zero disk I/O. | `ConcurrencyTest`, `DeepKVTest` | **PASS** |
| **File Storage Engine**| Storage | Supported | Append-only files, records persisted to disk, index files. | `FilePersistenceTest.testFileEngineColdRestart` | **PASS** |
| **B-Tree Storage Engine**| Storage | Supported | On-disk page splits, ordered key scans, fsync flush. | `BTreeEngineTest.testBTreeOperations` | **PASS** |
| **LSM-Tree Engine** | Storage | Supported | MemTable, binary SSTable flush, Bloom filter, compaction. | `LSMTreeEngineTest.testLSMTreeLifecycle` | **PASS** |
| **WAL & Crash Recovery**| Storage | Supported | Synchronous write-ahead log, CRC32 check, replay on restart. | `DeepInfrastructureTest.testWalReplayAndRecovery` | **PASS** |
| **ACID MVCC Transactions**| Transactions | Supported | Snapshot isolation, rollback undo buffer, atomic commit. | `DeepTransactionTest`, `TransactionTest` | **PASS** |
| **Document Store** | NoSQL | Supported | JSON documents, UUID generation, secondary indexing. | `DocumentCollectionTest`, `DeepDocumentTest` | **PASS** |
| **Query Engine** | NoSQL | Supported | Fluent filters (`eq`, `gt`, `lt`, `regex`, `like`), pagination. | `AdvancedQueryTest`, `QueryParserTest` | **PASS** |
| **Key-Value Store** | NoSQL | Supported | Simple KV, List, Set, and Hash structures. | `HashBucketTest`, `ListBucketTest`, `SetBucketTest` | **PASS** |
| **Wide-Column Families**| NoSQL | Supported | Row keys, column qualifiers, timestamps, TTL expiration. | `ColumnFamilyTest`, `ColumnFamilyAdvancedTest`| **PASS** |
| **Relational SQL / DDL**| Relational | Unsupported | No SQL parser or relational table engine exists in codebase. | Codebase inspection | **NOT_IMPLEMENTED** |
| **SQL JOIN / GROUP BY** | Relational | Unsupported | Aggregations exist on documents; relational SQL joins do not. | Codebase inspection | **NOT_IMPLEMENTED** |
| **Jakarta NoSQL TCK** | Jakarta | Partial | Provides API adapter (`DocumentTemplate`, `@Entity`), not TCK certified. | `org.embeddedjnosql.db.adapter.jnosql` inspection | **PARTIAL** |
| **Embedded Web Console**| UI | Supported | Single-page UI at `/`, REST endpoints for CRUD and metrics. | `EmbedJNoSQLServerTest`, `deep-test.ps1` | **PASS** |
| **Spring Boot 3.x** | Integration | Supported | Starter auto-configures `EmbedJNoSQL`, `@EnableEmbedJNoSQL`. | `EmbedJNoSQLAutoConfigurationTest`, `EcommerceApplicationTest` | **PASS** |
| **Quarkus 3.x** | Integration | Supported | CDI extension provides `@DefaultBean` producers. | `ProductResourceTest` | **PASS** |
| **Micronaut 4.x** | Integration | Supported | Factory bean registers `EmbedJNoSQL` and repositories. | `EcommerceControllerTest` | **PASS** |
| **Eclipse Vert.x** | Integration | Supported | Verticle uses `executeBlocking` for non-blocking persistence. | `EcommerceVerticleTest` | **PASS** |

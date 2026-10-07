# JunifyDB (JNoSQL-EMBED) — Product Constitution, Release Scope, Capability Inventory

## 1. Product constitution

JunifyDB is a lightweight, embedded, multi-model **NoSQL** database for the JVM. A developer
adds one dependency and gets a database inside their own process — no server, no daemon, no
Docker, no network, no account.

Principles (architectural constraints, not marketing):

1. **Embedded first.** `JunifyDB.inMemory()` starts a working database in one call and stops on
   `close()`. The database lifecycle is the application lifecycle. Starting the database never
   starts an HTTP server unless the console is explicitly enabled.
2. **Document and Key-Value are models of one product.** In-memory vs file-backed is a *storage
   mode*, not an engine split. There is no SQL-vs-NoSQL selector.
3. **Zero configuration by default; explicit for persistence.**
4. **Small.** Core runtime dependencies stay minimal (`jackson-databind`,
   `jackson-datatype-jsr310`, `slf4j-api`); framework/DI/console-adjacent dependencies are
   optional.
5. **Honest guarantees.** Durability, transaction, TTL, and adapter claims must each have a test
   or a stated limitation. A passing test count is not evidence of database correctness.
6. **First read/write is a runnable quickstart** using real coordinates and the real API.

## 2. Release scope

### RELEASE REQUIRED (must work and be verified)

- Document operations: insert, get by id, replace, update field, upsert, delete, collection
  lifecycle, TTL on documents.
- Key-Value operations: put/get/delete/exists, TTL with documented expiry-on-read semantics,
  list/set/hash bucket structures.
- Document queries: `Query` factories (`eq/ne/gt/gte/lt/lte/in/between/regex/contains/exists`),
  `and`/`or` composition, sorting, pagination (`limit`/`offset`), secondary field indexes.
- Lifecycle: open/close, flush, repeated open/close, predictable error after close.
- Transactions (MVCC) with documented scope and rollback.
- Persistence: `IN_MEMORY`, `FILE`, `B_TREE`, `LSM_TREE` storage modes; restart survival.
- Eclipse JNoSQL-style document adapter subset (repository, template, entity mapping).
- Console: Overview, Documents, Key-Value, Column Family, Vectors, Schema, Transactions,
  Indexes, Backup, CDC, Audit, Server — over the real backend.
- Reproducible build; green test suite.
- Documentation and website claims that match verified behavior.

### PRESERVE IF WORKING (kept, maintainable)

- NoSQL indexes (secondary field, text, experimental HNSW vector).
- Document schema validation (`SchemaValidator`, `/api/schema`).
- Backup/restore, CDC change feed, audit trail, metrics, health.
- Encryption/crypto utilities, connection pool, migration helper.
- Framework integrations: Spring Boot starter, Quarkus extension, Micronaut, Vert.x.

### DEFER (out of scope for this release; recorded, not implemented)

- Distributed clustering / replication, embedded Raft.
- Full Jakarta NoSQL 1.0 TCK certification.
- GraalVM native-image packaging.
- Enterprise RBAC console, new identity platform.
- Advanced aggregation/window functions beyond the existing document aggregation helpers.

### REMOVED (this change)

- Relational execution engine, SQL parser/lexer/AST, table catalogs, relational constraints
  (PK/FK/UNIQUE/CHECK/NOT NULL), JOINs, DDL/DML.
- JDBC driver.
- Jakarta Persistence `EntityManager`/JPQL provider.
- SQL console routes, SQL Studio UI, SQL examples/tests/claims.
- See [`SQL-REMOVAL-MANIFEST.md`](SQL-REMOVAL-MANIFEST.md).

## 3. Capability inventory

Status vocabulary: IMPLEMENTED · PARTIAL · BROKEN · ABSENT · DEFERRED · REMOVED.
Evidence paths are relative to the repository root.

| Capability | Status | Evidence |
|---|---|---|
| Document CRUD + TTL | IMPLEMENTED | `DocumentCollectionTest`, `DocumentTtlPersistenceTest`, `deep/DeepDocumentTest` |
| Key-Value + TTL + list/set/hash | IMPLEMENTED | `KeyValueBucketTest`, `KvTtlPersistenceTest`, `ListBucketTest`, `SetBucketTest`, `HashBucketTest` |
| Document query model (factories, composition, sort, page) | IMPLEMENTED | `AdvancedQueryTest`, `AggregationPipelineTest`, `CoverageExtensionTest` |
| Secondary field indexes | IMPLEMENTED | `ConsoleIndexAndCdcEndpointTest`, `SecondImprovementRoundTest` |
| Fluent entity query `db.from()` | IMPLEMENTED (rewired onto `Query`) | `JunifyRepositoryTest`, `TransactionalCatalogVisibilityTest` |
| Broad aggregation (JOIN/GROUP BY windows) | REMOVED | SQL removal manifest |
| Transactions (MVCC) | IMPLEMENTED | `TransactionTest`, `deep/DeepTransactionTest`, `ConsoleTransactionAndBulkTest` |
| Persistence across restart (FILE/LSM/B_TREE) | IMPLEMENTED | `FilePersistenceTest`, `LSMReadResolutionTest`, `BTreeAutoFlushPersistenceTest`, `WalRotationAndCheckpointTest`, `CollectionExistenceDurabilityTest` |
| Crash/power-loss safety | PARTIAL — WAL replay + checkpoint rotation tested; no power-loss test | `CheckpointRaceDurabilityTest`, `WalRotationAndCheckpointTest` |
| Eclipse JNoSQL-style adapter (repository/template/mapping) | PARTIAL — document model only, no Key-Value adapter, no TCK | `jnosql/JunifyRepositoryTest`, `jpa/JpaAnnotationTest`, `jpa/AnnotationDualSupportTest`, `jpa/HibernateAnnotationTest` |
| Wide-column families | PARTIAL | `ColumnFamilyTest`, `ColumnFamilyAdvancedTest`, `ColumnFamilyTtlPersistenceTest` |
| Vector (HNSW) search | PARTIAL / EXPERIMENTAL | `VectorPersistenceTest`, `VectorSearchAndTtlReadTest`, `ConsoleVectorSearchTest` |
| Console (NoSQL IA) | IMPLEMENTED | `ConsoleComprehensiveFeatureProofTest`, `ConsoleTaskSuccessTest`, `ConsoleWorkspaceEndpointsTest` |
| Backup / CDC / audit | IMPLEMENTED | `BackupIntegrityTest`, `ConsoleBackupEndpointTest`, `ConsoleIndexAndCdcEndpointTest` |
| Document schema validation | IMPLEMENTED | `SchemaNumericTypeTest`, `ConsoleComprehensiveFeatureProofTest` step 15 |
| Relational SQL engine | REMOVED | this change |
| JDBC driver | REMOVED | this change |
| JPA `EntityManager`/JPQL | REMOVED | this change |
| Distributed clustering / replication | DEFERRED | ROADMAP |
| Jakarta NoSQL 1.0 TCK certification | DEFERRED | ROADMAP |

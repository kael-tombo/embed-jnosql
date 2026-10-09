# EmbedJNoSQL Demo Inventory

**Phase**: Demo Validation — Lead Integration & Database Quality Engineer  
**Date**: October 9, 2026  
**Branch**: `feature/rigorous-assessment-and-improvements`

---

## Existing Demo Ecosystem

### A. 20 Single-Feature Demos (`demo/demos/`)

Each is a standalone Maven project with a `main()` runnable class and a JUnit companion test suite.

| ID | Directory | Demo Class | Engine Feature | Runtime | Assertions | Status |
|---|---|---|---|---|---|---|
| D-01 | `01-document-crud` | `DocumentCrudDemo` | Document insert/findById/update/deleteById | `IN_MEMORY` | `AssertionError` on failure | **PASS** |
| D-02 | `02-native-query-predicates` | `NativeQueryDemo` | `$eq $ne $gt $gte $lt $lte $in $nin $regex $exists $and $or`, sort, page | `IN_MEMORY` | `AssertionError` on failure | **PASS** (warning: `data\.indexes` path) |
| D-03 | `03-nested-field-queries` | `NestedFieldDemo` | Dotted-path queries across all operators | `IN_MEMORY` | `AssertionError` on failure | **PASS** |
| D-04 | `04-secondary-indexes` | `SecondaryIndexDemo` | createIndex, point lookups, dropIndex | `IN_MEMORY` | `AssertionError` on failure | **PASS** (warning: `data\.indexes` path) |
| D-05 | `05-ttl-expiry` | `TtlExpiryDemo` | Document/KV/Column TTL; expired reads absent | `IN_MEMORY` | `AssertionError` on failure; `Thread.sleep` for time passing | **PASS** |
| D-06 | `06-key-value-buckets` | `KeyValueDemo` | put/get, TTL, atomic counters, batch | `IN_MEMORY` | `AssertionError` on failure | **PASS** |
| D-07 | `07-redis-style-lists` | `RedisListDemo` | FIFO queues, stacks, RPOPLPUSH, LREM | `IN_MEMORY` | `AssertionError` on failure | **PASS** |
| D-08 | `08-redis-style-sets` | `RedisSetDemo` | SADD/SREM/SMEMBERS/SMOVE/SCARD | `IN_MEMORY` | `AssertionError` on failure | **PASS** |
| D-09 | `09-redis-style-hashes` | `RedisHashDemo` | HSET/HGET/HGETALL/HDEL | `IN_MEMORY` | `AssertionError` on failure | **PASS** |
| D-10 | `10-wide-column-families` | `WideColumnDemo` | Sparse rows, column-slice pagination, prefix scans | `IN_MEMORY` | `AssertionError` on failure | **PASS** |
| D-11 | `11-mvcc-transactions` | `MvccTransactionDemo` | Staged writes, read-your-own-writes, commit/rollback | `IN_MEMORY` | `AssertionError` on failure | **PASS** |
| D-12 | `12-aggregation-pipeline` | `AggregationDemo` | JVM-side group/count/sum/avg/min/max | `IN_MEMORY` | `AssertionError` on failure | **PASS** |
| D-13 | `13-sql-to-nosql-migration` | `SqlMigrationDemo` | Denormalize join tables into document aggregates | `IN_MEMORY` | `AssertionError` on failure | **PASS** |
| D-14 | `14-event-bus` | `EventBusDemo` | BEFORE/AFTER lifecycle listeners | `IN_MEMORY` | `AssertionError` on failure | **PASS** |
| D-15 | `15-metrics` | `MetricsDemo` | Operation counters, snapshot(), reset() | `IN_MEMORY` | `AssertionError` on failure | **PASS** |
| D-16 | `16-backup-restore` | `BackupDemo` | gzip JSON snapshot; restore into fresh db | `IN_MEMORY` | `AssertionError` on failure | **PASS** |
| D-17 | `17-cdc-change-feed` | `CdcChangeFeedDemo` | Typed CDC events (INSERT/UPDATE/DELETE) | `IN_MEMORY` | `AssertionError` on failure | **PASS** |
| D-18 | `18-text-search` | `TextSearchDemo` | Scored term + fuzzy Levenshtein matching | `IN_MEMORY` | `AssertionError` on failure | **PASS** |
| D-19 | `19-vector-search` | `VectorSearchDemo` | HNSW k-NN float vectors (experimental) | `IN_MEMORY` | `AssertionError` on failure | **PASS** |
| D-20 | `20-durability-recovery` | `DurabilityDemo` | B_TREE persist + WAL recovery across restart | `B_TREE` | `AssertionError` on failure; `@TempDir` isolates state | **PASS** |

### B. Framework Integration Demos

| ID | Directory | Framework | Version | Key Workflow | Status |
|---|---|---|---|---|---|
| F-SB | `spring-boot-demo` | Spring Boot | 3.2.0 | Auto-config, MVC REST, JPA-style repos, Hibernate annotations | **PASS** |
| F-QK | `quarkus-demo` | Quarkus | 3.8.0 | CDI injection, RESTEasy Reactive, MVCC transactions | **PASS** |
| F-MN | `micronaut-demo` | Micronaut | 4.2.0 | Compile-time DI, non-blocking HTTP, serde introspection | **PASS** |
| F-VX | `vertx-demo` | Vert.x | 4.5.4 | Reactive verticle, `executeBlocking`, Netty WebClient tests | **PASS** |
| F-E2E | `end-to-end-validation` | Plain Java | 17 | All 4 storage engines, lifecycle, persistence recovery | **PASS** |
| F-AN | `annotation-showcase-demo` | Plain Java | 17 | JNoSQL, JPA, Hibernate annotation interop | **PASS** |

---

## Identified Issues

### ISSUE-01: `data\.indexes` path warning in in-memory demos

- **Demos affected**: D-02, D-04
- **Symptom**: `Failed to save indexes: data\.indexes` logged to stderr during in-memory test runs
- **Root cause**: `DocumentCollection` index persistence writes to a relative `data/` path when the database is `IN_MEMORY`. On in-memory engines, no data directory is configured, so index serialization fails silently.
- **Impact**: Tests pass (index persistence is irrelevant for in-memory operation), but the warning is misleading and pollutes test output.
- **Status**: **TRACKED** — see findings.md.

### ISSUE-02: `Query.sortField()` package-private (compile break)

- **Impact**: `Transaction.TransactionalCollection.find()` in `org.embeddedjnosql.db.transaction.mvcc` tried to call `sortField()` from `org.embeddedjnosql.db.nosql.document.Query`. Cross-package access failed compilation.
- **Fix**: Made `sortField()` `public` in `Query.java`.
- **Status**: **FIXED**.

---

## Coverage Gaps (Addressed by New Scenarios)

1. No realistic business-domain document demos (product catalog, employee dir, config store).
2. No systematic edge-case collection (empty data, duplicate IDs, unicode, null vs absent, boundary values).
3. No separate-process persistence boundary test (demo 20 uses single-JVM boundary via `@TempDir`).
4. No KV session/cache/checkpoint realistic workflows.
5. No concurrency correctness demo with threads and barriers.
6. No console-linked workflow documentation.

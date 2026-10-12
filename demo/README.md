# EmbedJNoSQL Demonstration Ecosystem

This directory contains the demonstration applications showcasing **EmbedJNoSQL** embedded multi-model NoSQL database across the Java/JVM ecosystem. Every demo is a standalone Maven project; see `RUNBOOK.md` for the exact run procedure (install core, install `demo-common`, then `mvn test` each demo).

---

## Architecture & Submodules

```
demo/
├── demo-common/              # Shared E-Commerce domain model & fixtures (Records)
├── demos/                    # 20 standalone single-feature demos (see table below)
├── spring-boot-demo/         # Spring Boot 4.0.0 (3.4+ compatible) + EmbedJNoSQL Auto-Configuration starter demo
├── quarkus-demo/             # Quarkus 3.8.0 + EmbedJNoSQL CDI Extension demo
├── micronaut-demo/           # Micronaut 4.2.0 + EmbedJNoSQL DI Integration demo
├── vertx-demo/               # Eclipse Vert.x 4.5.4 Reactive non-blocking demo
├── end-to-end-validation/    # Multi-engine lifecycle and durability verification
├── advanced-queries-demo/    # Native document queries, filtering, and JVM-side aggregation
├── batch-processing-demo/    # Atomic batch writes, rollback, throughput patterns
├── load-and-stress-demo/     # Concurrency harness and indicative performance figures
└── annotation-showcase-demo/ # Multi-standard annotations (JNoSQL, JPA, Hibernate) over documents
```

---

## Single-Feature Demos (`demos/`)

Twenty standalone Maven projects, each demonstrating one engine capability with a runnable main class and a JUnit test suite. Every demo compiles against `embed-jnosql-core` only — no framework dependencies.

| # | Directory | Demo | What it shows |
|---|-----------|------|---------------|
| 01 | [01-document-crud](demos/01-document-crud) | `DocumentCrudDemo` | Insert, findById, update, deleteById round-trip |
| 02 | [02-native-query-predicates](demos/02-native-query-predicates) | `NativeQueryDemo` | `$eq $ne $gt $gte $lt $lte $in $nin $regex $exists $and $or`, between, contains, sort, paging |
| 03 | [03-nested-field-queries](demos/03-nested-field-queries) | `NestedFieldDemo` | Dotted-path queries (`address.city`, `tags.0`) across all operators and sort |
| 04 | [04-secondary-indexes](demos/04-secondary-indexes) | `SecondaryIndexDemo` | createIndex, index-backed point lookups, dropIndex |
| 05 | [05-ttl-expiry](demos/05-ttl-expiry) | `TtlExpiryDemo` | Document, KV-bucket, and column-level TTL; expired reads as absent; cleanup sweep |
| 06 | [06-key-value-buckets](demos/06-key-value-buckets) | `KeyValueDemo` | put/get, TTL, atomic counters, atomic batch put/get |
| 07 | [07-redis-style-lists](demos/07-redis-style-lists) | `RedisListDemo` | FIFO queues, stacks, RPOPLPUSH hand-off, LREM, negative indexing |
| 08 | [08-redis-style-sets](demos/08-redis-style-sets) | `RedisSetDemo` | SADD/SREM/SMEMBERS/SMOVE/SCARD, random sampling |
| 09 | [09-redis-style-hashes](demos/09-redis-style-hashes) | `RedisHashDemo` | HSET/HGET/HGETALL/HDEL, field-level semantics |
| 10 | [10-wide-column-families](demos/10-wide-column-families) | `WideColumnDemo` | Sparse rows, column-slice pagination, prefix scans, column TTL |
| 11 | [11-mvcc-transactions](demos/11-mvcc-transactions) | `MvccTransactionDemo` | Staged writes, read-your-own-writes, commit vs rollback |
| 12 | [12-aggregation-pipeline](demos/12-aggregation-pipeline) | `AggregationDemo` | group/count/sum/avg/min/max in the JVM via streams |
| 13 | [13-sql-to-nosql-migration](demos/13-sql-to-nosql-migration) | `SqlMigrationDemo` | Denormalizing join tables into document aggregates; nested-field queries replace JOINs |
| 14 | [14-event-bus](demos/14-event-bus) | `EventBusDemo` | BEFORE/AFTER insert-update-delete lifecycle listeners |
| 15 | [15-metrics](demos/15-metrics) | `MetricsDemo` | Operation counters, snapshot(), reset() |
| 16 | [16-backup-restore](demos/16-backup-restore) | `BackupDemo` | gzip JSON snapshot of every collection, restore into a fresh database |
| 17 | [17-cdc-change-feed](demos/17-cdc-change-feed) | `CdcChangeFeedDemo` | Typed CDC events (INSERT/UPDATE/DELETE), event log, system-listener channel |
| 18 | [18-text-search](demos/18-text-search) | `TextSearchDemo` | Scored term search + Levenshtein fuzzy matching |
| 19 | [19-vector-search](demos/19-vector-search) | `VectorSearchDemo` | HNSW k-NN over float vectors, add/remove/search (experimental) |
| 20 | [20-durability-recovery](demos/20-durability-recovery) | `DurabilityDemo` | B_TREE persistence + WAL recovery across a restart, index survival |

### Building and running the demos

```bash
# from demo/
mvn -B install -f demo-common/pom.xml   # not required by demos/, but keeps the workspace consistent

# run every demo's test suite
for d in demos/*/; do mvn -B test -f "$d/pom.xml"; done

# run one demo as a program (example: nested-field queries)
mvn -B compile exec:java -f demos/03-nested-field-queries/pom.xml \
  -Dexec.mainClass=org.embeddedjnosql.db.demo.demos.nestedfieldqueries.NestedFieldDemo
```

The 20 projects are generated from `gen-demos.sh` (templates in `mainNN.txt` / `testNN.txt`); edit those and re-run the script to regenerate.

---

## Demonstration Applications Overview

### 1. [demo-common](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/demo/demo-common)
- Implements the unified **E-Commerce & Order Management** domain model using modern Java 17 records:
  - `Product`: Document model with nested tags and flexible attribute maps.
  - `Order` & `OrderItem`: Transactional order placements.
  - `Customer`: Customer entity profile.
  - `InventoryItem`: Warehouse inventory tracker.
  - `SampleData`: Deterministic test fixtures.

### 2. [spring-boot-demo](spring-boot-demo)
- **Framework**: Spring Boot 4.0.0 (Spring Boot 3.4+ compatible)
- **Integration**: `embed-jnosql-spring-boot-starter` (`EmbedJNoSQLTemplate`)
- **Key Highlights**:
  - Auto-configuration of `EmbedJNoSQL` and `EmbedJNoSQLTemplate`.
  - Spring MVC REST controller (`EcommerceController`).
  - Document indexing on `category` and fast queries.
  - Key-Value price caching bucket (`price_cache`).
  - ColumnFamily inventory management with atomic updates.

### 3. [quarkus-demo](quarkus-demo)
- **Framework**: Quarkus 3.8.0 (Jakarta EE 10 / CDI)
- **Integration**: `embed-jnosql-quarkus-extension-runtime`
- **Key Highlights**:
  - `@ApplicationScoped` and `@Singleton` CDI injection of `EmbedJNoSQL`.
  - Quarkus RESTEasy Reactive endpoints (`ProductResource`, `OrderResource`).
  - ACID MVCC Transactions (`db.beginTransaction()`) verifying stock deduction.
  - Hot reloading and small-footprint execution.

### 4. [micronaut-demo](micronaut-demo)
- **Framework**: Micronaut 4.2.0
- **Integration**: `embedjnosql-micronaut-integration` (`@Factory`, `@Singleton`)
- **Key Highlights**:
  - Compile-time dependency injection and Serde introspection.
  - Non-blocking HTTP endpoints (`ProductController`, `OrderController`).
  - Stock validation and rollback handling.

### 5. [vertx-demo](vertx-demo)
- **Framework**: Eclipse Vert.x 4.5.4
- **Integration**: `embed-jnosql-core` embedded directly.
- **Key Highlights**:
  - Fully reactive `EcommerceVerticle` utilizing `vertx.executeBlocking(...)` to protect the event loop from blocking disk/MVCC operations.
  - Full CRUD REST API with Netty WebClient integration tests.

### 6. [end-to-end-validation](end-to-end-validation)
- **Scope**: Comprehensive verification across all 4 storage engines (`IN_MEMORY`, `FILE`, `B_TREE`, `LSM_TREE`).
- **Validation**:
  - Documents, Key-Value, and Column-Family co-existence.
  - Transaction commit vs. rollback isolation.
  - Cold engine shutdown and persistence recovery verification on disk.

### 7. [annotation-showcase-demo](annotation-showcase-demo)
- **Scope**: Multi-standard annotation interoperability over one document engine.
- **Key Highlights**:
  - **Eclipse JNoSQL Standard**: Entity mapping with `@Entity`, `@Id`, `@Column` and type-safe `EmbedRepository`.
  - **Jakarta Persistence annotations as mapping hints**: `@Entity`, `@Table`, `@Id`, `@Column`, `@Transient` are resolved onto documents. The JPA `EntityManager`/`TypedQuery`/`EntityTransaction` surface is not part of the product.
  - **Hibernate Annotations**: Automated primary key generation (`@UuidGenerator`), audit timestamps (`@CreationTimestamp`, `@UpdateTimestamp`), computed values (`@Formula`), and enum mappings (`@Enumerated`).
  - **Aggregation in application code**: grouping and aggregates (`count`, `sum`, `avg`, `min`, `max`) are computed from documents in the JVM — there is no SQL engine, no `GROUP BY`, and no `JOIN`.

---

## Running the Demonstrations

To build and run tests for all demo applications:

```bash
# Build shared domain
cd demo/demo-common && mvn clean install

# Run Spring Boot Demo
cd ../spring-boot-demo && mvn test

# Run Quarkus Demo
cd ../quarkus-demo && mvn test

# Run Micronaut Demo
cd ../micronaut-demo && mvn test

# Run Vert.x Demo
cd ../vertx-demo && mvn test

# Run Multi-Engine E2E Validation
cd ../end-to-end-validation && mvn test

# Run Annotation & Dual-Engine Showcase Demo
cd ../annotation-showcase-demo && mvn test && mvn compile exec:java
```

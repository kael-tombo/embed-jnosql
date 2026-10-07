# EmbedJNoSQL (JNOSQL-EMBED) — Repository Inventory

**Audit Date**: September 9, 2026  
**Environment**: Windows Server 2022 (amd64), Java 25.0.2 (Eclipse Adoptium OpenJDK), Maven 3.9.15  
**Git Branch**: `feature/nosql-embedded`  
**Latest Commit**: `33d9ab437073b337578196f47d9c6cf976b19f36`  

---

## 1. Module Structure

The project is structured into a core engine, enterprise starters/extensions, and a multi-framework demonstration suite:

| Module Path | Maven Artifact ID | Packaging | Description |
|---|---|---|---|
| `/` (root) | `embed-jnosql-core` | `jar` | Core multi-model NoSQL embedded database engine, storage engines, WAL, MVCC, HTTP/REST console, web UI. |
| `spring-boot-starter/` | `embed-jnosql-spring-boot-starter` | `jar` | Spring Boot 3.x AutoConfiguration starter providing `EmbedJNoSQL` bean and `EmbedJNoSQLTemplate`. |
| `quarkus-extension/` | `embed-jnosql-quarkus-extension` | `pom` | Parent aggregator for Quarkus 3.x extension. |
| `quarkus-extension/runtime/` | `embed-jnosql-quarkus-extension-runtime` | `jar` | Runtime CDI `@DefaultBean` producers for database and data structures. |
| `quarkus-extension/deployment/` | `embed-jnosql-quarkus-extension-deployment` | `jar` | Quarkus build-step deployment processor for native and JVM image generation. |
| `micronaut-integration/` | `embedjnosql-micronaut-integration` | `jar` | Micronaut 4.x factory and lifecycle integration with reflection-free serde. |
| `demo/demo-common/` | `demo-common` | `jar` | Shared domain entities (`Product`, `Order`, `Customer`, etc.) modeled as Java 17 records. |
| `demo/spring-boot-demo/` | `spring-boot-demo` | `jar` | Executable Spring Boot 3.2.5 REST service demonstrating catalog and order transactions. |
| `demo/quarkus-demo/` | `quarkus-demo` | `jar` | Executable Quarkus 3.8.0 reactive application with CDI injection and REST endpoints. |
| `demo/micronaut-demo/` | `micronaut-demo` | `jar` | Executable Micronaut 4.2.0 application using declarative controllers. |
| `demo/vertx-demo/` | `vertx-demo` | `jar` | Executable Eclipse Vert.x 4.5.4 verticle using `executeBlocking` worker thread isolation. |
| `demo/end-to-end-validation/` | `end-to-end-validation` | `jar` | Comprehensive multi-engine e2e test suite validating all 4 storage engines. |

---

## 2. Core Package Breakdown (`src/main/java/org/embeddedjnosql/db`)

| Package | Purpose & Key Classes |
|---|---|
| `org.embeddedjnosql.db` | Top-level entrypoint `EmbedJNoSQL.java` with builder pattern and component registry. |
| `org.embeddedjnosql.db.config` | `EmbedJNoSQLConfig.java`, engine selection (`IN_MEMORY`, `FILE`, `B_TREE`, `LSM_TREE`), buffer sizes, flush intervals. |
| `org.embeddedjnosql.db.storage.spi` | `StorageEngine.java` interface defining standard key-value storage contracts. |
| `org.embeddedjnosql.db.storage.memory` | `InMemoryEngine.java` (concurrent skiplist/hash map, zero-allocation). |
| `org.embeddedjnosql.db.storage.file` | `FileEngine.java` with append-only data files, indexing, and compaction. |
| `org.embeddedjnosql.db.storage.btree` | `BTreeEngine.java` on-disk B+ Tree storage provider. |
| `org.embeddedjnosql.db.storage.lsmtree` | `LSMTreeEngine.java` Log-Structured Merge Tree with MemTable, SSTables, Bloom Filters, and compaction. |
| `org.embeddedjnosql.db.storage.wal` | `WriteAheadLog.java` synchronous/asynchronous durable WAL with fsync. |
| `org.embeddedjnosql.db.transaction.mvcc` | `MVCCManager.java`, `Transaction.java`, snapshot isolation, read-views, commit/rollback tracking. |
| `org.embeddedjnosql.db.nosql.document` | `Document.java`, `DocumentCollection.java`, `Query.java`, `QueryParser.java`. |
| `org.embeddedjnosql.db.nosql.kv` | `KeyValueBucket.java`, `ListBucket.java`, `SetBucket.java`, `HashBucket.java`. |
| `org.embeddedjnosql.db.nosql.column` | `ColumnFamily.java` wide-column family with timestamps, TTL, and slice queries. |
| `org.embeddedjnosql.db.nosql.aggregation`| `AggregationPipeline.java`, match, project, group, sort, limit stages. |
| `org.embeddedjnosql.db.index` | Secondary indexing (`HashIndex.java`, `BTreeIndex.java`, compound indexing). |
| `org.embeddedjnosql.db.core.event` | `EventBus.java` asynchronous pub/sub event bus for collection/bucket mutations. |
| `org.embeddedjnosql.db.core.cdc` | `CDCManager.java` Change Data Capture stream tracking inserts, updates, and deletes. |
| `org.embeddedjnosql.db.core.metrics` | `DatabaseMetrics.java` operation counters, latency histograms, size tracking. |
| `org.embeddedjnosql.db.console.http` | `EmbedJNoSQLServer.java` embedded HTTP/HTTPS server with static asset hosting and REST API. |
| `org.embeddedjnosql.db.adapter.jnosql` | `DocumentTemplate.java`, `CrudRepository.java`, CDI producer, and Eclipse JNoSQL compatible annotations. |

---

## 3. Web Console & Static Assets (`src/main/resources/static`)

The core engine embeds an administrative single-page console served at `/` and `/index.html`:
- `index.html` (166 KB) — Responsive dashboard, collection browser, KV viewer, query workbench, and stats inspector.
- `css/enhancements.css` (31 KB) — Glassmorphic styling, dark theme, modal dialogs, and responsive grid layouts.
- `js/enhancements.js` (39 KB) — REST client logic, dynamic rendering, query runner, auth token handling.
- `favicon.svg` & `logo.svg` — Brand assets.

---

## 4. Dependencies & Security Stance

- **Core Dependencies**:
  - `com.fasterxml.jackson.core:jackson-databind:2.17.0` (JSON serialization)
  - `com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0` (Java 8 date/time)
  - `org.slf4j:slf4j-api:2.0.12` (Logging abstraction)
  - `org.slf4j:slf4j-simple:2.0.12` (Optional/runtime only)
  - `jakarta.enterprise:jakarta.enterprise.cdi-api:4.0.1` (Provided / optional)
- **Zero Heavy Native Dependencies**:
  - Pure Java 17+ implementation — zero JNI/C++ wrappers (unlike RocksDB or SQLite JNI).
  - Cross-platform portability verified on Windows Server 2022 and Linux.

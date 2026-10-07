# JNOSQL-EMBED: Module Boundaries & Package Governance

This document establishes the official module boundaries, dependency flow rules, and visibility constraints across the JNOSQL-EMBED project.

---

## 1. Maven Module Hierarchy

```
JNOSQL-EMBED (Parent Project)
├── embed-jnosql-core                     [Core Library: Storage, Models, Engine, MVCC]
├── spring-boot-starter                [Spring Boot 3.x Starter & AutoConfiguration]
├── quarkus-extension                  [Quarkus 3.x Extension Multi-Module]
│   ├── runtime                        [Quarkus Runtime Module & SmallRye Config]
│   └── deployment                     [Quarkus BuildStep Processor & ArC Beans]
├── micronaut-integration              [Micronaut 4.x CDI Factory & Repositories]
└── demo                               [Top-Level Demonstration & Real-World Apps]
    ├── demo-common                    [Shared Domain Models & Test Scenarios]
    ├── spring-boot-demo               [Real Spring Boot E-Commerce REST App]
    ├── quarkus-demo                   [Real Quarkus CDI & REST Microservice]
    ├── micronaut-demo                 [Real Micronaut HTTP Service]
    ├── vertx-demo                     [Real Reactive Vert.x Verticle Service]
    └── end-to-end-validation          [Automated Multi-Engine Validation Harness]
```

---

## 2. Dependency Direction Rules

Dependencies must strictly flow in one direction:

```
Applications / Framework Demos
          │
          ▼
Framework Adapters (Spring Boot Starter / Quarkus Extension / Micronaut)
          │
          ▼
Core Library (`embed-jnosql-core`)
          │
          ▼
Pure JDK 17+ APIs (Standard Library + Minimal Essential Dependencies)
```

### Prohibited Dependencies
- `embed-jnosql-core` must **NEVER** depend on Spring, Quarkus, Micronaut, Vert.x, or any application container.
- `embed-jnosql-core` must **NEVER** package or shade `slf4j-simple` into its compile classpath (it must remain runtime/optional so consumers can supply Logback, Log4j2, or JBoss Logging).
- Framework adapters must not cross-depend on each other (e.g., `quarkus-extension` cannot depend on `spring-boot-starter`).

---

## 3. Package Namespace Governance

| Package | Purpose | Consumer Visibility |
|---|---|---|
| `org.embeddedjnosql.db` | Main facade (`EmbedJNoSQL`) and high-level lifecycle | **Public API** |
| `org.embeddedjnosql.db.config` | Database configuration builder (`EmbedJNoSQLConfig`) | **Public API** |
| `org.embeddedjnosql.db.nosql.document` | Document collections, queries, records (`Document`, `Query`) | **Public API** |
| `org.embeddedjnosql.db.nosql.kv` | Key-value and Redis-style buckets (`KeyValueBucket`, `ListBucket`) | **Public API** |
| `org.embeddedjnosql.db.nosql.column` | Cassandra-style wide-column families (`ColumnFamily`) | **Public API** |
| `org.embeddedjnosql.db.transaction.mvcc` | Transactions and snapshot isolation (`Transaction`) | **Public API** |
| `org.embeddedjnosql.db.storage.spi` | Storage engine interface (`StorageEngine`) | **SPI (Extenders only)** |
| `org.embeddedjnosql.db.core.*` | Internal serialization, event bus, metrics | **Internal** |
| `org.embeddedjnosql.db.spring.boot.*` | Spring Boot auto-configuration and template | **Framework Public** |
| `org.embeddedjnosql.db.quarkus.*` | Quarkus SmallRye config, producers, and processors | **Framework Public** |
| `org.embeddedjnosql.db.micronaut.*` | Micronaut factories and entity managers | **Framework Public** |

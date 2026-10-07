# EmbedJNoSQL — Module Inventory

**Audit Date**: September 9, 2026  
**Auditor**: Repository Architect  

---

## 1. Project Modules Overview

The EmbedJNoSQL codebase is structured into core engine components, framework starters/extensions, and demonstration applications:

```text
JNoSQL-EMBED/
├── pom.xml                               # Core Engine root POM (org.embeddedjnosql.db:embed-jnosql-core)
├── src/main/java/org/embeddedjnosql/db/          # Core Database & Engine Implementation
│   ├── nosql/                            # Document, Key-Value, Column models
│   ├── storage/                          # Storage SPI (InMemory, File, BTree, LSMTree, WAL)
│   ├── transaction/                      # MVCC, UndoLog, TransactionManager
│   ├── console/http/                     # Embedded HttpServer, Handlers, SessionManager
│   ├── cdc/                              # Change Data Capture & Connectors
│   ├── event/                            # EventBus & Metrics
│   └── annotation/                       # Jakarta NoSQL style annotations (@Entity, @Id, @Column)
├── demo/                                 # Demo Ecosystem
│   ├── demo-common/                      # Shared domain records (Product, Order, Customer)
│   ├── spring-boot-demo/                 # Spring Boot 3.2.0 integration application
│   ├── quarkus-demo/                     # Quarkus 3.8.0 reactive application
│   ├── micronaut-demo/                   # Micronaut 4.2.0 application
│   ├── vertx-demo/                       # Eclipse Vert.x 4.5.4 verticle application
│   └── end-to-end-validation/            # Multi-engine durability test suite
└── docs/                                 # Complete Engineering & Audit Documentation
```

## 2. Module Specifications

| Module | Artifact ID | Type | Java Target | Primary Purpose |
|---|---|---|---|---|
| Core Engine | `embed-jnosql-core` | JAR | Java 17 | Multi-model database, storage SPI, WAL, transactions, web console |
| Demo Common | `demo-common` | JAR | Java 17 | Domain model records (`Product`, `Order`, `InventoryItem`) |
| Spring Boot Demo | `spring-boot-demo` | App | Java 17 | Spring Boot 3.2.0 REST application using `EmbedJNoSQLTemplate` |
| Quarkus Demo | `quarkus-demo` | App | Java 17 | Quarkus 3.8.0 CDI-based application |
| Micronaut Demo | `micronaut-demo` | App | Java 17 | Micronaut 4.2.0 Serde-based application |
| Vert.x Demo | `vertx-demo` | App | Java 17 | Eclipse Vert.x 4.5.4 non-blocking event-loop application |
| E2E Validation | `end-to-end-validation` | Test | Java 17 | Cold restart and multi-engine durability assertions |

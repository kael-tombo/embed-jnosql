# EmbedJNoSQL Demonstration Ecosystem

This directory contains the demonstration applications showcasing **EmbedJNoSQL** embedded multi-model NoSQL database across the Java/JVM ecosystem. Every demo is a standalone Maven project; see `RUNBOOK.md` for the exact run procedure (install core, install `demo-common`, then `mvn test` each demo).

---

## Architecture & Submodules

```
demo/
├── demo-common/              # Shared E-Commerce domain model & fixtures (Records)
├── spring-boot-demo/         # Spring Boot 3.2.5 + EmbedJNoSQL Auto-Configuration starter demo
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

## Demonstration Applications Overview

### 1. [demo-common](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/demo/demo-common)
- Implements the unified **E-Commerce & Order Management** domain model using modern Java 17 records:
  - `Product`: Document model with nested tags and flexible attribute maps.
  - `Order` & `OrderItem`: Transactional order placements.
  - `Customer`: Customer entity profile.
  - `InventoryItem`: Warehouse inventory tracker.
  - `SampleData`: Deterministic test fixtures.

### 2. [spring-boot-demo](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/demo/spring-boot-demo)
- **Framework**: Spring Boot 3.2.0
- **Integration**: `embed-jnosql-spring-boot-starter` (`EmbedJNoSQLTemplate`)
- **Key Highlights**:
  - Auto-configuration of `EmbedJNoSQL` and `EmbedJNoSQLTemplate`.
  - Spring MVC REST controller (`EcommerceController`).
  - Document indexing on `category` and fast queries.
  - Key-Value price caching bucket (`price_cache`).
  - ColumnFamily inventory management with atomic updates.

### 3. [quarkus-demo](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/demo/quarkus-demo)
- **Framework**: Quarkus 3.8.0 (Jakarta EE 10 / CDI)
- **Integration**: `embed-jnosql-quarkus-extension-runtime`
- **Key Highlights**:
  - `@ApplicationScoped` and `@Singleton` CDI injection of `EmbedJNoSQL`.
  - Quarkus RESTEasy Reactive endpoints (`ProductResource`, `OrderResource`).
  - ACID MVCC Transactions (`db.beginTransaction()`) verifying stock deduction.
  - Hot reloading and small-footprint execution.

### 4. [micronaut-demo](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/demo/micronaut-demo)
- **Framework**: Micronaut 4.2.0
- **Integration**: `embedjnosql-micronaut-integration` (`@Factory`, `@Singleton`)
- **Key Highlights**:
  - Compile-time dependency injection and Serde introspection.
  - Non-blocking HTTP endpoints (`ProductController`, `OrderController`).
  - Stock validation and rollback handling.

### 5. [vertx-demo](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/demo/vertx-demo)
- **Framework**: Eclipse Vert.x 4.5.4
- **Integration**: `embed-jnosql-core` embedded directly.
- **Key Highlights**:
  - Fully reactive `EcommerceVerticle` utilizing `vertx.executeBlocking(...)` to protect the event loop from blocking disk/MVCC operations.
  - Full CRUD REST API with Netty WebClient integration tests.

### 6. [end-to-end-validation](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/demo/end-to-end-validation)
- **Scope**: Comprehensive verification across all 4 storage engines (`IN_MEMORY`, `FILE`, `B_TREE`, `LSM_TREE`).
- **Validation**:
  - Documents, Key-Value, and Column-Family co-existence.
  - Transaction commit vs. rollback isolation.
  - Cold engine shutdown and persistence recovery verification on disk.

### 7. [annotation-showcase-demo](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/demo/annotation-showcase-demo)
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

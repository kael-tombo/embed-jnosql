# Integration Review — Baseline

**Date**: October 9, 2026  
**Branch**: `feature/rigorous-assessment-and-improvements`  
**Spring Boot**: 3.2.5  
**Spring Data Commons/JPA**: 3.3.0 (pinned above Boot BOM)  
**Jakarta Persistence API**: 3.1.0  
**Hibernate ORM**: 6.4.4.Final (test-scope, annotation-only, all transitive exclusions applied)  
**Java**: 17  
**EmbedJNoSQL Core**: 1.0.0  

---

## Modules Inventoried

| Module | Artifact | Purpose |
|---|---|---|
| `embed-jnosql-core` | `embed-jnosql-core-1.0.0.jar` | Document/KV/Column engine, storage engines, MVCC |
| `embed-jnosql-spring-boot-starter` | `embed-jnosql-spring-boot-starter-1.0.0.jar` | Spring Boot auto-configuration, repositories |
| `quarkus-extension` | `embed-jnosql-quarkus-extension-1.0.0.jar` | Quarkus CDI integration |
| `micronaut-integration` | `embed-jnosql-micronaut-1.0.0.jar` | Micronaut bean integration |
| `demo/spring-boot-demo` | Demo app | Spring Boot e-commerce scenario |
| `demo/quarkus-demo` | Demo app | Quarkus product catalog scenario |
| `demo/micronaut-demo` | Demo app | Micronaut integration |
| `demo/vertx-demo` | Demo app | Vert.x reactive integration |

---

## Engine APIs

| API | Interface | Storage Paths |
|---|---|---|
| Document collections | `DocumentCollection` | IN_MEMORY, FILE, LSM_TREE, B_TREE |
| Key-value buckets | `KeyValueBucket` | All engines |
| Wide-column families | `ColumnFamily` | All engines |
| Redis-style lists/sets/hashes | `RedisList`, `RedisSet`, `RedisHash` | All engines |
| MVCC transactions | `Transaction` | All engines |
| Secondary indexes | `SecondaryIndex` | All engines (persistent for FILE/B_TREE/LSM) |
| Text search | `TextIndex` | In-memory overlay |
| Vector search | `VectorIndex` | In-memory overlay |
| Administration console | `EmbedJNoSQLServer` | HTTP, auth-optional |

---

## Spring Boot Integration — Auto-Configuration Surface

### Auto-configuration entry point
File: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

Content: `org.embeddedjnosql.db.spring.boot.EmbedJNoSQLAutoConfiguration`

✅ Correct Boot 3.x registration mechanism.

### Beans auto-configured

| Bean | Type | Condition |
|---|---|---|
| `embedJNoSQL` | `EmbedJNoSQL` | `@ConditionalOnMissingBean` + `embedjnosql.enabled=true` |
| `embedJNoSQLTxRoutingPostProcessor` | `EmbedJNoSQLTxRoutingPostProcessor` | Always when enabled |
| `embedJNoSQLTemplate` | `EmbedJNoSQLTemplate` | `@ConditionalOnMissingBean` |
| `embedJNoSQLServer` | `EmbedJNoSQLServer` | `@ConditionalOnMissingBean` |
| `embedJNoSQLTransactionManager` | `EmbedJNoSQLTransactionManager` | `@ConditionalOnMissingBean(PlatformTransactionManager)` |
| `embedEntityCollectionsInitializer` | `EmbedEntityCollectionsInitializer` | repositories enabled |

### Properties prefix: `embedjnosql.*`

| Property | Default | Notes |
|---|---|---|
| `enabled` | `true` | Master switch |
| `storage-engine` | `IN_MEMORY` | FILE, LSM_TREE, B_TREE also supported |
| `data-dir` | `"data"` | Used only for persistent engines |
| `auto-flush` | `true` | Background flush |
| `flush-interval-ms` | `1000` | Background flush interval |
| `transactions-enabled` | `true` | MVCC tx manager |
| `repositories-enabled` | `true` | Auto-register EmbedRepository per @Entity |
| `auto-create-collections` | `true` | Materialize entity collections at startup |
| `console.enabled` | `false` | Admin console |
| `console.port` | `9090` | Console port |
| `console.intelligent-port` | `true` | Port auto-scan |
| `security.auth-enabled` | `false` | Console auth |

---

## Entity Discovery

**Annotations recognized** (string-based, no classpath requirement):
1. `jakarta.persistence.Entity`
2. `jakarta.nosql.Entity`
3. `org.embeddedjnosql.db.adapter.jnosql.Entity`

**Scan scope**: Spring Boot's `AutoConfigurationPackages` (application base package).

**Collection naming**: Derived from `@Table(name=...)` → `EntityMapper.getCollectionName()`.

**ID field selection**: `@Id` (Jakarta Persistence or JNoSQL) → `EntityMapper.getIdFieldName()`.

---

## Spring Data Repository Support

**Mechanism**: `EmbedJpaRepositoryFactory extends RepositoryFactorySupport` — Spring Data's proxy machinery creates typed repository proxies backed by `EmbedJpaBaseRepository` which routes all operations to the embed-jnosql document engine.

**Enabled via**: `@EnableEmbedJpaRepositories` on the application class.

**Interfaces implemented by** `EmbedJpaBaseRepository`:
- `CrudRepository<T,ID>`
- `PagingAndSortingRepository<T,ID>`
- `JpaRepository<T,ID>` (Spring Data JPA interface — **not** a JPA provider)
- `QueryByExampleExecutor<T>`

**Derived query support**: `EmbedJpaPartTreeQueryCreator` translates Spring Data property path expressions (`findByTitle`, `findByPriceGreaterThan`, etc.) to `Query` predicates against the document engine.

---

## Hibernate / JPA Support Classification

### What is actually implemented

| Capability | Classification | Evidence |
|---|---|---|
| Recognize `@Entity` annotation | **Annotation compatibility** | `EmbedEntityScanner` detects annotation string |
| Recognize `@Id` annotation | **Annotation compatibility** | `EntityMapper.getIdFieldName()` reads annotation |
| Recognize `@Table` annotation | **Annotation compatibility** | `EntityMapper.getCollectionName()` reads annotation |
| Recognize `@Column` annotation | **Annotation compatibility** | `MappingColumns.column()` reads annotation |
| `CrudRepository` via document engine | **Direct NoSQL adapter support** | `EmbedJpaBaseRepository` → `DocumentCollection` |
| `JpaRepository` interface | **Interface compatibility only** | No JPA provider, no EntityManager |
| `@Transactional` | **MVCC transaction support** | `EmbedJNoSQLTransactionManager` → MVCC layer |
| Derived query methods | **Direct NoSQL adapter support** | `EmbedJpaPartTreeQueryCreator` → Query predicates |
| `EntityManagerFactory` | **NOT SUPPORTED** | No JPA provider configured |
| `EntityManager.persist/merge` | **NOT SUPPORTED** | No JPA provider |
| Hibernate SessionFactory | **NOT SUPPORTED** | No Hibernate runtime |
| Dirty checking | **NOT SUPPORTED** | No persistence context |
| Lazy loading | **NOT SUPPORTED** | No session |
| JPA relationships | **NOT SUPPORTED** | No foreign key concept |
| Optimistic locking (`@Version`) | **NOT SUPPORTED** | Engine uses MVCC; no row-version |
| Spring Data `JpaRepository` bootstrap | **Annotation-compatible bootstrap** via embed factory | No JPA EntityManager |
| Coexistence with real JPA | **Supported** (isolated tx manager) | Demo: `spring-boot-demo` |

### What `EmbedJNoSQLHibernateParityTest` actually tests
The "Hibernate parity" test demonstrates **annotation-compatibility + NoSQL CrudRepository parity**:
- `@Entity`, `@Id`, `@Table` annotations on `ParityOrder` → discovered by scanner → `EmbedRepository<ParityOrder,String>` auto-registered
- Operations go through `EmbedRepository` → `DocumentCollection` → in-memory engine
- **Hibernate ORM is not involved in any persistence operation**
- Hibernate is in `test` scope, annotation-only, with all transitive dependencies excluded

This is correctly classified as **H2-style annotation-compatibility** — the developer experience mimics Spring Data JPA without using JPA infrastructure.

---

## Baseline Test Results

| Suite | Tests | Failures | Result |
|---|---|---|---|
| Spring Boot Starter | 42 | 0 | ✅ PASS |
| Core Suite | 928 | 0 | ✅ PASS |
| Spring Boot Demo | 41 | 0 | ✅ PASS |
| Quarkus Demo | 13 | 0 | ✅ PASS |
| Micronaut Demo | 11 | 0 | ✅ PASS |
| Vert.x Demo | 11 | 0 | ✅ PASS |

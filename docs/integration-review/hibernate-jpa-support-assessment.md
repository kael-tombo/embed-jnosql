# Hibernate / JPA Support Assessment

**Assessed against**: Spring Boot 3.2.5, Spring Data JPA 3.3.0, Jakarta Persistence 3.1.0, Hibernate ORM 6.4.4.Final  
**Date**: October 9, 2026

---

## Support Matrix

| Capability | Claimed Behavior | Actual Implementation | Verification | Status | Limitations |
|---|---|---|---|---|---|
| `@Entity` recognition | Entity classes discovered and mapped | `EmbedEntityScanner` reads annotation metadata string | Scanner test + parity test | ✅ **Annotation compatibility** | Only annotation detection; no JPA schema generation |
| `@Id` recognition | ID field used as document identifier | `EntityMapper.getIdFieldName()` reads `@Id` (jakarta.persistence or jnosql) | Parity CRUD test | ✅ **Annotation compatibility** | Composite IDs not supported |
| `@Table` recognition | Collection named from `@Table(name=...)` | `EntityMapper.getCollectionName()` | Parity collection test | ✅ **Annotation compatibility** | No schema/catalog attributes used |
| `@Column` recognition | Field stored under column name | `MappingColumns.column()` | Spring Data sort/filter tests | ✅ **Annotation compatibility** | No column constraints enforced |
| `@Transient` recognition | Field excluded from storage | `EntityMapper.toDocument()` skips transient fields | Mapping test | ✅ **Annotation compatibility** | |
| `CrudRepository` operations | save/find/delete route to document engine | `EmbedJpaBaseRepository` → `DocumentCollection` | Spring Data integration test | ✅ **Direct NoSQL adapter** | |
| `JpaRepository` interface | Full JpaRepository API available | `EmbedJpaBaseRepository implements JpaRepository` | Spring Data integration test | ⚠️ **Interface compatibility only** | No JPA provider; flush is no-op; no batch hints |
| Derived query methods | `findByField`, `findByPriceLessThan` etc. | `EmbedJpaPartTreeQueryCreator` → Query predicates | Derived query test | ✅ **Direct NoSQL adapter** | No JPQL, no @Query |
| `@Transactional` (NoSQL) | Commit/rollback via MVCC layer | `EmbedJNoSQLTransactionManager` | Tx staging/rollback test | ✅ **Direct MVCC support** | |
| `REQUIRES_NEW` propagation | Inner tx independent of outer | `EmbedJNoSQLTxRoutingPostProcessor` + MVCC | Parity propagation test | ✅ **Direct MVCC support** | |
| Query-by-Example | `findAll(Example)`, `count(Example)` | `QueryByExampleBuilder` → document predicates | QBE test | ✅ **Direct NoSQL adapter** | `.as(DTO)` and `.project()` not supported |
| `EntityManagerFactory` | Hibernate/JPA entity manager | **NOT IMPLEMENTED** | Code inspection | ❌ **Unsupported** | No JPA provider configured |
| `EntityManager.persist/merge` | Persist through JPA | **NOT IMPLEMENTED** | Code inspection | ❌ **Unsupported** | No JPA provider |
| Hibernate `SessionFactory` | Hibernate-native session | **NOT IMPLEMENTED** | Code inspection | ❌ **Unsupported** | No Hibernate runtime linked |
| Dirty checking | JPA first-level cache | **NOT IMPLEMENTED** | Code inspection | ❌ **Unsupported** | No persistence context |
| Lazy loading | JPA proxy for relations | **NOT IMPLEMENTED** | Code inspection | ❌ **Unsupported** | No session |
| `@OneToMany`/`@ManyToOne` | JPA relationships | **NOT IMPLEMENTED** | Code inspection | ❌ **Unsupported** | NoSQL: embed or application-managed references |
| Optimistic locking `@Version` | Conflict detection on update | **NOT IMPLEMENTED** | Code inspection | ❌ **Unsupported** | MVCC provides isolation, not row-version |
| JPQL / `@Query` | Named or inline JPQL | **NOT IMPLEMENTED** | Code inspection | ❌ **Unsupported** | Derived query syntax only |
| Schema generation (`ddl-auto`) | Table create/update | **Not applicable** | Code inspection | **N/A** — document store auto-creates collections | |
| `JpaRepository` bootstrap via embed factory | Interface extends JpaRepository → backed by embed | `EmbedJpaRepositoryFactory` + Spring Data proxy | SD integration test | ✅ **Annotation-compatible bootstrap** | Requires `@EnableEmbedJpaRepositories` |
| Coexistence with real JPA | Both JPA (relational) and embed-jnosql active | Separate `DataSource`/`EntityManagerFactory` (app-managed) | Spring Boot demo | ✅ **Coexistence** | Explicit tx-manager selection required per @Transactional |

---

## Honest Classification

### ✅ Genuine Direct Support
- MVCC `@Transactional` with commit, rollback, `REQUIRES_NEW` propagation
- `CrudRepository` + `PagingAndSortingRepository` + derived queries over the document engine
- Query-by-Example with string matchers and case handling
- Auto-entity discovery, auto-collection materialization, auto-repository registration

### ⚠️ Explicitly Limited Adapter Support
- `JpaRepository<T,ID>` interface implemented — all methods delegated to document engine
- `flush()` and `saveAndFlush()` are no-ops by design (writes are immediate)
- `getReferenceById()` is eager (no lazy proxy)
- `deleteAllInBatch()` operates document-by-document (no JPA batch hints)

### 📋 Annotation Compatibility Only
- `@Entity`, `@Id`, `@Table`, `@Column`, `@Transient` recognized for metadata extraction
- Recognition does not imply JPA persistence semantics
- No JPA constraint enforcement, schema generation, or DDL

### 🤝 Coexistence Only
- Applications can run a real JPA stack (DataSource + EntityManagerFactory) alongside embed-jnosql
- Each must use its own dedicated `PlatformTransactionManager`
- No global transaction spanning both stores
- This is documented as coexistence, not direct JPA support

### ❌ Unsupported (Not Claimed, Not Implemented)
- `EntityManagerFactory`, `EntityManager`, Hibernate `Session`
- JPQL, HQL, `@Query`
- JPA relationships (`@OneToMany`, `@ManyToOne`, `@ManyToMany`)
- Dirty checking, lazy loading, cascade operations
- Optimistic locking (`@Version`)
- Second-level caches

---

## Defects and Gaps Found

### GAP-01: Documentation uses "Hibernate/JPA" terminology ambiguously
- The class `EmbedJpaBaseRepository` and annotation `@EnableEmbedJpaRepositories` use "Jpa" in their name
- The parity test is called `EmbedJNoSQLHibernateParityTest`
- A developer reading these names may believe Hibernate or JPA is actually used
- **Recommendation**: Add a prominent contract statement to Javadoc and README clarifying "Jpa" in these names means "JPA developer experience over the NoSQL engine, not a JPA provider"

### GAP-02: No `@Query` / custom query support
- Derived queries work; declared `@Query` annotations are silently ignored
- A `USE_DECLARED_QUERY` lookup strategy returns null (falls through to derived)
- **Recommendation**: Throw `UnsupportedOperationException` with a clear message when `@Query` is detected

### GAP-03: Duplicate ID on `save()` is replace, not error
- `save(entity)` where entity ID already exists performs an upsert (replaces)
- JPA semantics: `merge` replaces; `persist` with existing managed entity is idempotent
- This is correct NoSQL behavior but must be documented

### GAP-04: No `@GeneratedValue` strategy implementation
- `@GeneratedValue` annotation is recognized (scanner) but ID generation is caller-responsibility or UUID-generated internally
- **Recommendation**: Document supported ID generation strategies explicitly

---

## Feasibility Assessment: Direct JPA Provider Support

To genuinely support `EntityManager.persist()` routing data into embed-jnosql would require:
1. Implementing `javax.persistence.spi.PersistenceProvider` / `jakarta.persistence.spi.PersistenceProvider`
2. Implementing `EntityManagerFactory` and `EntityManager`
3. Implementing a persistence context (identity map, dirty tracking)
4. Providing flush/transaction integration with the engine
5. Handling lazy proxies and relationships

**Conclusion**: This is a substantial standalone project. The current approach (annotation-compatibility + document repository) is the correct scope for an embedded document store. Direct JPA provider support is **out of scope for 1.0** and should not be claimed.

# EmbedJNoSQL: Eclipse JNoSQL & Jakarta NoSQL Specification Support

**Subsystem**: `org.embeddedjnosql.db.adapter.jnosql`  
**Components**: `EclipseDocumentTemplate`, `EmbedNoSQLRepository`, `EmbedRepository`, `EclipseQueryMapper`, `EntityMapper`, `AnnotationResolver`, `EmbedDBProducer`, `EmbedJNoSQLConfiguration`  
**Status**: Verified Embedded Eclipse JNoSQL & Jakarta NoSQL Provider  

---

## 1. Specification Architecture

[Eclipse JNoSQL](https://github.com/eclipse-jnosql/jnosql) is the official framework helping Java developers build Jakarta EE and MicroProfile applications with NoSQL databases, serving as the reference implementation of Jakarta NoSQL and Jakarta Data specifications.

EmbedJNoSQL provides complete embedded provider support for Eclipse JNoSQL:

```
┌────────────────────────────────────────────────────────┐
│               Enterprise Application                   │
│  (Jakarta EE / CDI / Quarkus / MicroProfile / SE)      │
└───────────────────────────┬────────────────────────────┘
                            │
            ┌───────────────┴───────────────┐
            ▼                               ▼
┌───────────────────────────────┐ ┌───────────────────────────────┐
│     Eclipse JNoSQL / Data     │ │     Jakarta NoSQL Template    │
│       NoSQLRepository<T, K>   │ │         Template API          │
│     (EmbedNoSQLRepository)    │ │   (EclipseDocumentTemplate)   │
└───────────────┬───────────────┘ └───────────────┬───────────────┘
                │                                 │
                ▼                                 ▼
┌────────────────────────────────────────────────────────┐
│                  EclipseQueryMapper                    │
│      (Fluent criteria query builder & execution)       │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│                     EntityMapper                       │
│    (Jakarta NoSQL / JPA / Hibernate metadata engine)   │
└───────────────────────────┬────────────────────────────┘
                            │
                            ▼
┌────────────────────────────────────────────────────────┐
│             EmbedJNoSQL Storage Engine                 │
│         (DocumentCollection / KeyValueBucket)          │
└────────────────────────────────────────────────────────┘
```

---

## 2. Implemented Capabilities

### A. Standard Template Specification (`jakarta.nosql.Template`)
`EclipseDocumentTemplate` implements `jakarta.nosql.Template` and `org.embeddedjnosql.db.adapter.jnosql.DocumentTemplate`:
- `insert(entity)` and `insert(entity, Duration ttl)` with TTL time-to-live expiration.
- `insert(Iterable<T> entities)` and batch insert with TTL.
- `update(entity)` and `update(Iterable<T> entities)`.
- `find(Class<T> entityClass, K id)` returning `Optional<T>`.
- `delete(Class<T> entityClass, K id)`.
- Fluent query builder via `select(Class<T> entityClass)`:
  - Conditions: `.where(field).eq(val)`, `.gt(val)`, `.gte(val)`, `.lt(val)`, `.lte(val)`, `.between(v1, v2)`, `.like(pattern)`, `.in(iter)`, `.not()`
  - Logical operators: `.and(field)`, `.or(field)`
  - Sorting and pagination: `.orderBy(field).asc() / .desc()`, `.skip(n)`, `.limit(n)`
  - Execution: `.result()`, `.stream()`, `.singleResult()`
- Fluent criteria deletion via `delete(Class<T> entityClass).where(...).execute()`.

### B. Standard Repository Specification (`org.eclipse.jnosql.mapping.NoSQLRepository`)
`EmbedNoSQLRepository<T, K>` implements `NoSQLRepository<T, K>` (extending `BasicRepository` and `CrudRepository` from Jakarta Data):
- `save(S entity)` (transparent insert-or-update based on identity existence).
- `saveAll(List<S> entities)`.
- `insert(S entity)` and `insertAll(List<S> entities)`.
- `update(S entity)` and `updateAll(List<S> entities)`.
- `findById(K id)` and `existsById(K id)`.
- `findAll()` streaming and `findByIdIn(Iterable<K> ids)`.
- `count()` and `countBy()`.
- `deleteById(K id)`, `delete(T entity)`, `deleteByIdIn(Iterable<K> ids)`, `deleteAll()`.
- **Jakarta Data pagination**: `findAll(PageRequest pageRequest, Order<T> order)` returning standard `Page<T>` (via `PageRecord`) with total elements and sorting support.

### C. Jakarta NoSQL Annotation Ecosystem
`AnnotationResolver` and `EntityMapper` provide first-class support for Jakarta NoSQL annotations:
- **`jakarta.nosql.Entity`**: Custom collection/table name mapping via `@Entity("collection_name")`.
- **`jakarta.nosql.Id`**: Entity identifier resolution.
- **`jakarta.nosql.Column`**: Custom column/property name mapping via `@Column("col_name")`.
- **`jakarta.nosql.Embeddable`**: Complex embedded value objects/subdocuments mapped to nested structures.
- **`jakarta.nosql.Convert`**: Custom attribute conversions via user-defined `AttributeConverter<X, Y>`.
- **`jakarta.nosql.Inheritance`, `jakarta.nosql.DiscriminatorColumn`, `jakarta.nosql.DiscriminatorValue`**: Polymorphic entity hierarchies with discriminator-based subclass resolution.
- **`jakarta.nosql.MappedSuperclass`**: Transitive inheritance of persistent fields from abstract base classes.

### D. CDI Integration & Qualifiers
`EmbedDBProducer` exposes CDI beans qualified with Eclipse JNoSQL qualifiers:
- `@Produces @Database(DatabaseType.DOCUMENT) EclipseDocumentTemplate`
- `@Produces @Database(DatabaseType.DOCUMENT) Template`
- `@Produces @Database(DatabaseType.DOCUMENT) DocumentCollection`
- `@Produces @Database(DatabaseType.KEY_VALUE) KeyValueBucket`
- Default un-qualified producers for zero-configuration injection.

### E. Configuration Bridge (`org.eclipse.jnosql.communication.Settings`)
`EmbedJNoSQLConfiguration` allows booting and configuring EmbedJNoSQL from Eclipse JNoSQL `Settings`:
- Storage engine selection: `jnosql.embed.engine` (`IN_MEMORY`, `FILE`, `B_TREE`, `LSM_TREE`).
- Storage path: `jnosql.embed.data.dir`.
- Engine auto-flush and flush interval: `jnosql.embed.auto.flush`, `jnosql.embed.flush.interval`.
- Query cache toggle: `jnosql.embed.query.cache`.

---

## 3. Verification & Evidence

All capabilities are verified end-to-end in `org.embeddedjnosql.db.jnosql.EclipseJNoSQLFullIntegrationTest`:
1. `testJakartaTemplateCrud`: CRUD & Duration TTL validation.
2. `testQueryMapperSelect`: Fluent where, eq, gt, between, like, sort, skip, limit.
3. `testQueryMapperDelete`: Criteria-based deletion.
4. `testNoSqlRepository`: Standard NoSQLRepository CRUD and bulk operations.
5. `testNoSqlRepositoryPagination`: Jakarta Data PageRequest & Order pagination.
6. `testEmbeddableAndConvert`: Embeddable subdocument and AttributeConverter mapping.
7. `testInheritancePolymorphism`: Inheritance & Discriminator polymorphism.
8. `testMappedSuperclass`: Superclass field inheritance.
9. `testSettingsConfiguration`: Eclipse JNoSQL Settings configuration bootstrapping.
10. `testCdiProducerWiring`: CDI producer wiring with `@Database` qualifiers.

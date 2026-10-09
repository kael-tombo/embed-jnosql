# Repository Semantics Contract

**Module**: `embed-jnosql-spring-boot-starter`  
**Implementation**: `EmbedJpaBaseRepository<T,ID>`  
**Factory**: `EmbedJpaRepositoryFactory extends RepositoryFactorySupport`  
**Registration**: `@EnableEmbedJpaRepositories` → `EmbedJpaRepositoryRegistrar`

---

## Supported Repository Interfaces

`EmbedJpaBaseRepository` implements:
- `CrudRepository<T,ID>`
- `PagingAndSortingRepository<T,ID>`
- `JpaRepository<T,ID>` _(Spring Data JPA interface, backed by document engine)_
- `QueryByExampleExecutor<T>`

**Important**: `JpaRepository` is an **interface compatibility** choice. There is no JPA `EntityManager` or Hibernate `Session` underneath — all operations route to `DocumentCollection`.

---

## Operation Semantics

| Spring Data Method | embed-jnosql Implementation | Notes |
|---|---|---|
| `save(entity)` | If ID null: `insert()`. If ID present and exists: `update()`. Else: `insert()` | Upsert-by-existence |
| `saveAll(entities)` | Calls `save()` for each | |
| `findById(id)` | `DocumentCollection.findById(idString(id))` → mapped to entity | |
| `existsById(id)` | `DocumentCollection.exists(idString(id))` | |
| `findAll()` | `DocumentCollection.findAll()` → mapped | |
| `findAll(Sort)` | `findAll()` + JVM-side comparator sort | Sort applied in memory |
| `findAll(Pageable)` | Query with offset+limit, then sort | |
| `count()` | `DocumentCollection.count()` | |
| `deleteById(id)` | `DocumentCollection.deleteById(idString(id))` | |
| `delete(entity)` | Extracts ID, calls `deleteById()` | Throws if ID is null |
| `deleteAll()` | Iterates all documents, deletes by ID | |
| `flush()` | **No-op** — writes are immediate; no persistence context | |
| `saveAndFlush(entity)` | Delegates to `save()` | flush() is no-op |
| `getReferenceById(id)` | Eager read; throws `NoSuchElementException` if absent | No lazy proxy |

---

## Derived Query Translation

Method name → `PartTree` → `EmbedJpaPartTreeQueryCreator` → `Query` predicate

| Spring Data Keyword | Document Query |
|---|---|
| `findByField(value)` | `Query.eq(column, value)` |
| `findByFieldNot(value)` | `Query.ne(column, value)` |
| `findByFieldGreaterThan(n)` | `Query.gt(column, n)` |
| `findByFieldLessThan(n)` | `Query.lt(column, n)` |
| `findByFieldContaining(s)` | `Query.contains(column, s)` (case-sensitive) |
| `findByFieldContainingIgnoreCase(s)` | Lambda: `v.toLowerCase().contains(s.toLowerCase())` |
| `findByFieldStartingWith(s)` | Lambda: `v.startsWith(s)` |
| `findByFieldEndingWith(s)` | Lambda: `v.endsWith(s)` |
| `findByFieldTrue` | `Query.eq(column, true)` |
| `findByFieldFalse` | `Query.eq(column, false)` |
| `findByFieldIsNull` | `Query.matching(d -> !d.has(column))` |
| `findByFieldIsNotNull` | `Query.exists(column)` |
| `findByFieldIn(list)` | `Query.in(column, list)` |
| `findByFieldBetween(lo, hi)` | `Query.gte(lo).and(Query.lte(hi))` |
| `findByFieldLike(pattern)` | `%x%` → contains, `x%` → starts, `%x` → ends, `x` → eq |
| `findByOrderByFieldAsc` | `Query.sortByAsc(column)` |
| `findByOrderByFieldDesc` | `Query.sortByDesc(column)` |
| `findFirst3By...` | `.limit(3)` applied to result |
| `countByField(v)` | Returns `long` count |
| `existsByField(v)` | Returns `boolean` |
| `deleteByField(v)` | Deletes matching documents, returns deleted list |

**Unsupported**: `@Query` annotation (no JPQL/HQL engine). Throws `UnsupportedOperationException` via `USE_DECLARED_QUERY` strategy if no named query is declared.

---

## Query-by-Example

`Example<T>` + `ExampleMatcher` are translated by `QueryByExampleBuilder`:
- Null values: ignored when `NullHandler.IGNORE` (default)
- String matchers: `EXACT`, `STARTING`, `ENDING`, `CONTAINING`, `REGEX`
- `ignoreCase`: lowercases both stored and probe value
- ALL vs ANY matching: AND vs OR of all non-null probe fields

---

## Transaction Semantics

When `embedjnosql.transactions-enabled=true` (default):
- `EmbedJNoSQLTransactionManager` is the `PlatformTransactionManager`
- The `EmbedJNoSQL` bean is wrapped by `EmbedJNoSQLTxRoutingPostProcessor`
- `documentCollection(name)` inside a `@Transactional` method returns a `TransactionAwareDocumentCollection`
- Writes stage into the MVCC transaction
- Commit: staged writes flushed to the collection
- Rollback: staged writes discarded

**`REQUIRES_NEW` propagation**: inner transaction commits independently of the outer rollback — implemented via MVCC transaction stacking.

---

## Findings

| # | Finding | Severity | Fix |
|---|---|---|---|
| RS-01 | `findByFieldContainingIgnoreCase` was case-sensitive (lowercased needle, not value) | HIGH | Fixed in `EmbedJpaPartTreeQueryCreator.contains()` |
| RS-02 | `notContains` delegated to `contains()` with `negated` flag but the flag was ignored | MEDIUM | Fixed as part of RS-01 |
| RS-03 | `deleteAll()` iterates documents then deletes — not atomic under concurrent writes | LOW | Documented limitation |
| RS-04 | No `@Query` detection — silently falls through to derived | LOW | Would require Spring Data `@Query` annotation scanning |

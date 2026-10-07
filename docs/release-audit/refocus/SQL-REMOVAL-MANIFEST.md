# SQL / Relational Removal Manifest

Scope: remove the relational database product, the SQL engine, and the dual-engine
architecture from EmbedJNoSQL, leaving one embedded multi-model **NoSQL** product
(Document + Key-Value, plus the existing wide-column and experimental vector surfaces).

Baseline before this change (recorded, pre-change): `mvn test` → **886 tests, 0 failures**
(`docs/release-audit/refocus/baseline-test.log`).
After this change: `mvn test` → **819 tests, 0 failures**
(`docs/release-audit/refocus/post-removal-test2.log`).

## 1. Removed components

| Component / path | Actual purpose | Consumers found | Classification | Action | Retained replacement | Compatibility impact |
|---|---|---|---|---|---|---|
| `org/embeddedjnosql/db/sql/**` (11 classes: `engine/SqlEngine`, `parser/SqlLexer`, `parser/SqlParser`, `ast/SqlStatement`, `ast/Expression`, `SqlSchemaCatalog`, `SqlTableSchema`, `SqlResultSet`, `SqlRow`, `SqlConstraintViolationException`, `SqlUnknownTableException`) | SQL text parsing, relational execution (SELECT/INSERT/UPDATE/DELETE/JOIN/GROUP BY), table constraints | `EmbedJNoSQL.sql()`, `JembedEntityManager`, `JembedTypedQuery`, console `SqlHandler`, `SqlSchemaHandler`, tests | SQL-exclusive | **Removed** | `DocumentCollection.find(Query)` / `insert` / `update` / `deleteById` | `db.sql(...)` no longer exists. SQL-created tables were document collections, so their data is still readable through the document API (see §3). |
| `org/embeddedjnosql/db/jdbc/**` (7 classes) + `META-INF/services/java.sql.Driver` | JDBC 4 driver over the SQL engine | only its own tests | SQL-exclusive | **Removed** | none | `jdbc:embedjnosql:` URLs are gone. |
| `org/embeddedjnosql/db/jpa/**` (5 classes: `JembedPersistence`, `JembedEntityManager`, `JembedEntityManagerFactory`, `JembedEntityTransaction`, `JembedTypedQuery`) | Jakarta Persistence `EntityManager` / JPQL provider over the document store | `OrderInvoiceService` in `demo/annotation-showcase-demo` | Relational persistence API (JPQL is a SQL-like query language) | **Removed** | `EclipseDocumentTemplate` / `CrudRepository` (JNoSQL-style) and `db.from(Entity.class)` | `JembedPersistence.createEntityManager(...)` gone. **Annotation mapping is retained**: `@jakarta.persistence.Entity/@Id/@Column/@Transient` and Hibernate annotations are still resolved by `EntityMapper`/`AnnotationResolver` onto documents. |
| `EmbedJNoSQL.sql(String, Object...)`, `EmbedJNoSQL.sql(String, Class, Object...)`, `EmbedJNoSQL.sqlEngine()` | public SQL surface | repository, entity query, console, tests | SQL-exclusive | **Removed** | `db.from()` + document collection API | source-incompatible; migration note in README. |
| Console `/api/sql`, `/api/sql/schema` routes + `SqlHandler`, `SqlSchemaHandler`, `splitSqlStatements`, `extractSqlTarget` | SQL execution + constraint catalog over HTTP | console UI, tests | SQL-exclusive | **Removed** | `/api/collections/{name}/query` (document filters), `/api/storage/status` | HTTP clients calling `/api/sql` get 404. |
| Console "SQL Studio" panel, `sql` navigation entry, `engine-tag` dual-engine grouping | SQL editor, autocomplete, saved queries, export | console UI | SQL-exclusive | **Removed** | Collections / Key-Value panels | deep link `#sql` falls back to a valid panel (hash is validated against the panel list at boot). |
| SQL-only tests | `sql/SqlEngineTest`, `sql/SqlConstraintTest`, `sql/SqlReferentialConstraintTest`, `sql/SqlUnknownTableTest`, `jdbc/JdbcDriverTest`, `jpa/JpaEntityManagerTest`, `SqlAuditTrailTest`, and the `sql/schema` case in `ConsoleWorkspaceEndpointsTest` | — | SQL-exclusive | **Removed** (67 tests) | native equivalents in the surviving suites | — |

## 2. Preserved shared infrastructure (verified still in use)

These were audited because the SQL engine referenced them; none are relational, and all are
retained:

- `storage/spi/**` — `StorageEngine`, `InMemoryEngine`, `FileEngine`, `BTreeEngine`,
  `LSMTreeEngine`, `WriteAheadLog`, `CollectionRegistry`, `DatabaseCompactor`, `FileEnginePool`,
  `BloomFilter`.
- `transaction/mvcc/**` (`MVCCManager`, `Transaction`) — transactions are legitimate NoSQL
  functionality and are untouched.
- `index/**` (`SecondaryIndex`, `TextIndex`, `HNSWIndex`), `core/cache`, `core/cdc`,
  `core/crypto`, `core/schema` (`SchemaValidator` — document validation, not relational DDL),
  `core/backup`, `console/http/**` (minus the SQL handlers).
- `adapter/jnosql/**` — the Eclipse JNoSQL-style document adapter and repositories.
- `api/EntityQuery` — **rewired** (was a SQL string builder; now compiles to `Query` predicates).

## 3. Data compatibility

`SqlEngine` wrote every table into the same storage as documents
(`db.documentCollection(tableName)`), and `SqlResultSet.mapTo` used `EntityMapper`. Therefore
**no data migration is required**: rows written by the old SQL engine are ordinary documents and
remain readable through `documentCollection(name).findAll()` / `find(Query)`.

New non-creating read semantics are preserved: a query against a collection that does not exist
matches nothing and does not create it (`EntityQuery.resolveCollection`).
`CollectionExistenceDurabilityTest` and `TransactionalCatalogVisibilityTest` pin this.

## 4. Semantic sweep — deliberately NOT treated as SQL

Inspected and kept (search hits for `sql`/`select`/`join` that are **not** relational):

- `QueryBuilder.selectFrom(...)` — a fluent *document* query DSL (`.where("field").eq(...)`);
  it builds a `Query`, not SQL text.
- Java `stream().collect(...)`, `Thread.join`, `String.join` — language constructs.
- `SchemaValidator` and the `/api/schema` route — insert-time **document** validation rules.
- `ENTITY` mapping (`@Entity`, `@Column`, `@Id`) — annotation-based document mapping.
- Identifier `id` uniqueness on documents — document identity, not a relational primary key.
- Comments/strings containing "NoSQL" (substring "SQL") — brand/product wording.

## 5. Website and documentation

Deployed site `docs/index.html` (published by `.github/workflows/pages.yml`, which uploads the whole
`docs/` directory) was aligned with the NoSQL-only product: title/description/OG/Twitter metadata, hero
headline and sample code (now the real API), the playground (SQL tab and SQL branch removed; a single
document-query tab remains), the dual-engine pillar, the "One Relational SQL Engine" section, the
positioning badge, the storage-section wording, and the footer tagline.

`docs/website/index.html` — a stale duplicate of the site (last touched in `96ebc8d`, superseded by
`docs/index.html`) that was still published at `/website/` with dual-engine claims — was **deleted**;
git history retains it.

Active API documentation: the `POST /api/sql`, `GET /api/sql/schema`, `POST /api/sql/execute` entries in
`docs/api/REST-API.md` were replaced by a "Removed routes" table stating the 404 behavior and the NoSQL
replacement for each.

Pre-refactor documents that still describe the removed product were marked with a consistent
`SUPERSEDED — pre-refactor document (SQL / dual-engine)` banner (20 files under `docs/vision/`,
`docs/product/`, `docs/engines-and-standards/`, `docs/admin-console/`, `docs/features/`). Six files that a
coarse text search flagged but which contain no SQL claim (only H2/SQLite comparisons or `JNoSQL`
substrings) were verified and left byte-identical.

## 6. Historical evidence (retained, not rewritten)

`docs/browser-testing/` (network traces, screenshots), `docs/release-audit/` (117 audit documents) and
`docs/audit/` describe the product as it was before this refactor, including SQL Studio journeys. They are
kept as the record of what was measured at the time and are labeled by the banner or by this manifest.
See `docs/browser-testing/PRE-REFACTOR-NOTICE.md`.

## 7. Dead code removed in the final sweep

Found by re-reading the console assets and caches after the feature removal:

- `src/main/resources/static/css/console.css` — 81 lines of SQL-Studio CSS removed
  (`.sql-layout`, `.sql-explorer`, `.tbl-node`/`.tbl-detail`/`.tbl-col`/`.col-flags`/`.tbl-check`/
  `.tbl-select` schema-explorer tree, `.sql-editor-wrap`, `.sql-highlight`, `.sql-suggest`, `.tok-*`
  highlighter tokens, `.saved-row` saved-query rows) and the `.engine-tag.sql` variant. Verified by
  counting class-name occurrences in `index.html` + `console.js`: every removed selector had zero users.
- `QueryCache` (`core/cache/QueryCache.java`) — the SQL result cache; its only remaining consumer was
  its own unit test. NoSQL reads use `QueryResultCache` (owned by `DocumentCollection`). Class and its
  10 test methods in `CoverageExtensionTest` removed together, so no assertion was weakened.
- `EmbedJNoSQLServer.buildContext` no longer publishes `relationalEngine=EMBEDJNOSQL-RDBMS`; the console reads
  `engine` + `nosqlEngine` only.
- Stale wording in comments/javadoc that described removed behavior (`EmbedJNoSQL`, `StorageEngine`,
  `Transaction`, `ConsoleInteractiveTestServer`, the backup-restore response note).

## 8. Retained API that *resembles* SQL (deliberate, documented)

- `EntityQuery.where("field OP ? AND field2 OP ?", params...)` — a **document field filter with bound
  positional parameters**, split on `AND` and compiled into `Query.eq/ne/gt/...` predicates. No statement
  text is parsed, there is no grammar, and non-`?` values are rejected with `IllegalArgumentException`
  (see `EntityQuery.buildPredicate`). Kept because framework repositories and demos build on it.
- `QueryParser` `{"$gt": 18, "$regex": "^A"}` filter documents — the MongoDB-style filter form the console
  sends to `POST /api/collections/{name}/query`. This is the documented NoSQL query contract, not SQL.
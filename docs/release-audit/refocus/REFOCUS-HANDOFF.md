# JunifyDB Refocus Handoff — NoSQL-only product

**Date**: 2026-10-06 · **Scope**: remove the relational SQL product and the dual-engine architecture,
leave one embedded NoSQL database (Document + Key-Value) that builds, tests, and ships.

**Verdict: READY for a source release / local publication — NOT READY for Maven Central until the
release credentials step is done** (see §7).

Companion documents:

- Product constitution, release scope, capability inventory: [PRODUCT-CONSTITUTION.md](PRODUCT-CONSTITUTION.md)
- Removal manifest with per-surface replacements: [SQL-REMOVAL-MANIFEST.md](SQL-REMOVAL-MANIFEST.md)
- Raw final build log: [verify-run.log](verify-run.log)

---

## 1. Concrete changes

### Backend (`src/main`)

| Area | Change |
|---|---|
| SQL product | Deleted `sql/` (engine, parser, lexer, AST, catalog, schema, row/result types, exceptions). |
| JDBC | Deleted `jdbc/` (7 handlers + driver) and `META-INF/services/java.sql.Driver`. |
| JPA | Deleted `jpa/` (`EntityManager`, `EntityManagerFactory`, `EntityTransaction`, `EntityQuery` impl, `Persistence`). |
| Public API | `JunifyDB.sql(...)`, `sqlSchema()`, `createEntityManager()`, `from(...)`-as-SQL removed; `from(Class)` now returns the native document-backed `EntityQuery`. |
| Console server | `/api/sql`, `/api/sql/schema`, `/api/sql/execute` handlers and routes removed (now 404); `relationalEngine` context key removed; malformed query bodies answer **400** instead of 500; backup-restore note no longer references SQL Studio. |
| Dead code | `core/cache/QueryCache` (the SQL result cache, no remaining consumer) deleted. |
| Retained | Storage engines (memory/file/B-tree/LSM), WAL + recovery, MVCC transactions, secondary/text/HNSW indexes, document validation, TTL, backup, CDC, audit, metrics, event bus, security, console server, Jakarta NoSQL adapter, framework adapters. |

### Console UI

- SQL Studio screen, its navigation entry, its runtime code, and every SQL-specific asset removed.
- Dead CSS removed (81 lines: editor overlay, schema-explorer tree, highlighter tokens, saved-query
  rows, `.engine-tag.sql`); verified with a class-name usage count against `index.html` + `console.js`.
- Remaining IA is Documents / Key-Value / Indexes / Storage / CDC / Audit / Backup / Metrics /
  Transactions, with the yellow/white identity and mascot preserved.

### Website (`docs/index.html`, published via `.github/workflows/pages.yml`)

- Metadata (title/description/keywords/OG/Twitter), hero headline + hero code sample, playground
  (SQL tab and SQL branch removed; sample rewritten to the real API), dual-engine pillar, the
  "One Relational SQL Engine" section, the positioning badge, the storage-section copy, and the
  footer tagline all now describe the NoSQL product only.
- `docs/website/index.html` — a stale duplicate that was still published at `/website/` with
  dual-engine claims — deleted.

### Documentation

- `README.md` and `ROADMAP.md` rewritten for the NoSQL-only contract (including an explicit
  "SQL: intentionally removed" row and the 404 behavior of the removed routes).
- `docs/api/REST-API.md`: SQL endpoints replaced by a **Removed routes** table with the NoSQL
  replacement for each.
- 20 pre-refactor documents carry a `SUPERSEDED — pre-refactor document (SQL / dual-engine)` banner
  linking the constitution and the manifest. Six files that a coarse text search flagged but which
  contain no SQL claim were verified and left byte-identical.
- `docs/browser-testing/PRE-REFACTOR-NOTICE.md` explains that those traces/screenshots predate the
  refactor; `docs/release-audit/` and `docs/audit/` remain as historical record.
- Demo docs rewritten to the shipped code: `demo/README.md`, `demo/advanced-queries-demo/README.md`,
  `demo/annotation-showcase-demo/README.md`.

### Release configuration and tooling

- `scripts/console-contract-gate.sh` retargeted from `/api/sql` to document/KV assertions
  (read-must-not-create, unknown collection 404, delete-must-not-create, restart survival exactly
  once). `scripts/console-auth-gate.sh` no longer probes `/api/sql`.
- `deep-test.ps1` section 10 replaced by a document-query smoke section that also asserts
  `POST /api/sql → 404`. In review round 3 the whole sweep was ported to
  `scripts/deep-test.sh` (PowerShell is not available on this machine) with every expectation
  re-verified against the actual handlers in `JunifyDBServer` — it executes the full 20-section
  sweep end to end.
- `.github/workflows/pages.yml` unchanged (documented as publishing the whole `docs/` directory).

---

## 2. Removed components and retained shared infrastructure

**Removed (33 tracked files):** 24 main Java sources (`sql/**` 11, `jdbc/**` 7, `jpa/**` 5,
`core/cache/QueryCache` 1), 7 test classes (`SqlEngineTest`, `SqlConstraintTest`,
`SqlReferentialConstraintTest`, `SqlUnknownTableTest`, `SqlAuditTrailTest`, `JdbcDriverTest`,
`JpaEntityManagerTest`), `src/main/resources/META-INF/services/java.sql.Driver`,
`docs/website/index.html`.

**Shared infrastructure that was extracted/preserved and is still exercised:** storage SPI, WAL and
crash recovery, checksums, serialization (`JsonSerde`), MVCC transaction coordination, file I/O and
locks, secondary/text/vector indexes, configuration, error types, lifecycle management, event bus,
metrics, CDC, backup, audit trail.

**Deliberately not treated as SQL** (semantic sweep): `QueryBuilder.selectFrom(...)`,
`QueryParser` `$eq/$gt/$in/$regex/$and/$or` filter documents, `EntityQuery.where("field OP ?")`
(document field filter with bound parameters, compiled to `Query` predicates — not parsed statement
text), `SchemaValidator` document rules, `@Entity/@Id/@Column` mapping, `Thread.join`/`String.join`,
and the substring "SQL" inside "NoSQL"/"JNoSQL".

---

## 3. Compatibility and migration notes

**Breaking (intentional):**

- No SQL. `db.sql("…")` does not exist, `POST /api/sql` answers 404, and a former SQL payload is
  never re-interpreted as a NoSQL operation.
- No JDBC driver, no `jdbc:junifydb:` URL, no `java.sql.Driver` service entry.
- No JPA `EntityManager`/`TypedQuery`/`EntityTransaction`. Jakarta Persistence **annotations** are
  still read as mapping hints (`@Entity`, `@Table`, `@Id`, `@Column`, `@Transient`, `@EmbeddedId`).
- Removed packages: `org.junify.db.sql.**`, `org.junify.db.jdbc.**`, `org.junify.db.jpa.**`.
- Console: SQL Studio screen, engine selector, SQL autocomplete/history, saved queries, explain
  views are gone.

**Compatible:**

- **Data requires no migration.** `SqlEngine` wrote tables as documents in
  `documentCollection(tableName)`, so rows written by the old SQL engine are ordinary documents and
  remain readable via `findAll()`, `find(Query)`, `EntityMapper`, and the console.
- Document/Key-Value APIs are unchanged in shape: `documentCollection`, `keyValueBucket`,
  `listBucket`, `setBucket`, `hashBucket`, `columnFamily`, transactions, TTL, indexes, backup.
- Maven coordinates and the Java 17 target are unchanged.
- **Experimental surface (round-3 product decision):** `JunifyDBPool`, `ReactiveJNoSQL`, and
  `MigrationManager` are **kept**, not deleted, and are now marked with the runtime-visible
  `org.junify.db.Experimental` annotation plus class javadoc stating they have no production
  caller, no functional coverage, and may change or be removed without notice. They sit outside
  the compatibility contract and the JaCoCo gate (exclusions retained in `pom.xml`); the label is
  enforced by `ReleaseFeatureSweepTest.experimentalClassesAreLabeled`.

**Migration recipe for a former SQL user:** replace `db.sql("SELECT … WHERE x = ?")` with
`db.documentCollection("t").find(Query.eq("x", value))`; replace JDBC connectivity with the console
REST API (`POST /api/collections/{name}/query`) or the Jakarta NoSQL adapter; move reporting that
genuinely needs `JOIN`/`GROUP BY` to a relational database.

---

## 4. Verification

| Check | Command | Result |
|---|---|---|
| Baseline (pre-change) | `mvn -B clean verify` | 886 tests, 0 failures |
| **Final full build + suite** | `mvn -B clean verify` | **BUILD SUCCESS, 847 tests, 0 failures, 0 errors, 0 skipped** — log: [verify-run.log](verify-run.log) |
| **Release feature sweep** (every advertised capability, driven through the public API/HTTP) | `ReleaseFeatureSweepTest` (22 tests) + `ConsoleWorkspaceEndpointsTest` (10) | PASS — per-feature verdicts in [FEATURE-SWEEP.md](FEATURE-SWEEP.md) |
| Text-index phrase search is real positional matching | `TextIndexPhraseTest` (11 tests) | PASS — a phrase requires its tokens on consecutive positions of the indexable token stream (same pipeline both sides); covers adjacency vs. first-token-only regression, transparent stop words/punctuation, stop-word-initial phrases (the old stub matched nothing there), repeated tokens, OR across phrases, removal clearing positions, re-add replacement |
| Retired relational routes stay retired over HTTP | `FullFeatureTest.http_relationalTableRoutesAreGone`, `http_constraintsRouteIsGone` | PASS — `POST/DELETE /api/tables/...` and `GET /api/constraints/...` answer 404 (previously asserted as “any of 200/400/404/503”, i.e. vacuously) |
| Console is opt-in (no socket without configuration) | `AdminConsoleConfigTest.testNoHttpServerByDefault` | PASS — `db.config().consoleConfig().enabled()` is false, `consoleServer()`/`consoleUrl()` are null, `consolePort()` is -1, and document read/write works with no server running |
| Old SQL deep link (`#sql-studio` etc.) | `console.js` `boot()` + `PANELS` inspection | PASS — the panel list contains no SQL id, and an unknown hash resolves to the `overview` panel; `hashchange` ignores ids that are not panels |
| Console SQL surfaces gone | `ConsoleTaskSuccessTest` (shipped-asset assertions) | PASS — no `/api/sql` in `index.html`/`console.js`, no SQL Studio control, no SQL destructive-statement detection |
| Console error contract | `ConsoleTaskSuccessTest`, `ConsoleQueryEndpointTest` | PASS — malformed query body → 400, create → 201, unknown collection → 404 |
| Document/KV/TTL/index/persistence | `DocumentCollectionTest`, `KeyValueBucketTest`, `*TtlPersistenceTest`, `SecondaryIndex*`, `LSMReadResolutionTest`, `BTreeAutoFlushPersistenceTest` | PASS |
| Transactions | `TransactionTest`, `deep/DeepTransactionTest`, `TransactionalCatalogVisibilityTest` | PASS |
| Durability across restart | `EngineRestartDiscoveryTest`, `CheckpointRaceDurabilityTest`, `WalRotationAndCheckpointTest`, `CollectionExistenceDurabilityTest` | PASS |
| Jakarta NoSQL adapter | `jnosql/JunifyRepositoryTest` + annotation demo suite | PASS |
| Demos and adapters | `mvn -B -o test` per module after `mvn install` of the core | PASS — spring-boot-demo 28 (6 app + **22 Hibernate-integration**), advanced-queries-demo 5, annotation-showcase-demo 5, spring-boot-starter 12, cli 4 (0 failures, 0 errors each); quarkus runtime/deployment and micronaut-integration built green |
| Hibernate integration inside Spring Boot (deep) | `HibernateIntegrationTest` (22 tests, real `@SpringBootTest`) | PASS — log: [demo-spring-hibernate.log](demo-spring-hibernate.log). Hibernate/JPA-annotated entities drive a template-backed `@Service` through auto-configured beans: `@Table` collection naming (and the lowercase-simple-name fallback), `@UuidGenerator` id backfill (36-char UUID) vs. explicit `@Id`, `@PrePersist`/`@PostLoad`, `@CreationTimestamp`/`@UpdateTimestamp` columns, derived `@Formula` recomputed on load and tracking inputs after update, `@Column(name=…)` alias as the stored key, `@Enumerated(STRING)` stored as names and queryable via `Query.eq`, `@Transient` excluded from documents, `java.util.Date`↔ISO-string write/read symmetry, raw `EntityMapper` remap agreeing with the template, batch `insertAll`, `sortByDesc`+`limit(offset)` windowing, storage-level enum-name grouping, `@Immutable` update refusal on an audit entity, `@NaturalId` recognition, and a whole-stack column audit. First run surfaced and fixed a real core bug: `EntityMapper.convertValue` wrote `java.util.Date` as an ISO string but threw on read; the read now parses symmetrically 
| Website JS syntax | `node --check` on the extracted inline script | PASS |
| Website DOM (local preview) | `preview_evaluate` on the registered page | PASS — title `JunifyDB — The Embedded NoSQL Database for Java`, H1 "One Embedded Engine. Zero Infrastructure.", one playground tab, real-API sample, two data-model cards + four storage modes |
| Gate scripts | `bash -n scripts/console-contract-gate.sh`, `bash -n scripts/console-auth-gate.sh` | PASS (syntax), and **executed end-to-end**: contract gate **95/95 checks PASS** ([contract-gate-run.log](contract-gate-run.log)) incl. restart and kill -9 WAL recovery and 4 shipped-jar picker checks; auth gate **36/36 PASS** ([auth-gate-run.log](auth-gate-run.log)); deep test **90/90 PASS** ([deep-test-run.log](deep-test-run.log)) |
| Live browser verification (real server, real Chromium page) | DOM/network assertions via the preview browser against `java -jar junify-db-core-1.0.0.jar --engine IN_MEMORY` | PASS — 12 panels (`overview, collections, kv, columns, vectors, schema, tx, indexes, backup, cdc, audit, server`), zero SQL panels, old `#sql-studio` deep link resolves to `overview`, no SQL headings (the only `sql`-substring hits are `.engine-tag.nosql` “NoSQL” badges), all fetches 200 with no `/api/sql` traffic, and the real user path works: refresh ⟳ → collection chip `gate_ui_demo 1` → click → document table renders the inserted doc (screenshot export still impossible, see §6) |

The 39-test core delta from the baseline (886 → 847) is accounted for: 7 deleted SQL/JDBC/JPA test
classes, 10 `QueryCache` tests deleted with the orphan class they covered, the rest from
migrated/merged console and durability tests, then **+37 core tests added by this verification
round and the round-3 follow-up**: `ReleaseFeatureSweepTest` (22 — one per advertised capability
family plus the experimental-label contract assertion for
`JunifyDBPool`/`ReactiveJNoSQL`/`MigrationManager`), four new HTTP cases in
`ConsoleWorkspaceEndpointsTest` (Redis-style list/set/hash routes, `/api/stats`, and the
retired-route 404s), and `TextIndexPhraseTest` (11 — the positional phrase-search contract after
the sweep's `searchPhrases` finding was fixed). The demo layer adds another 22: the
Spring Boot demo's `HibernateIntegrationTest` (see the deep row above). One further core test, `testNoHttpServerByDefault`, pins the console-opt-in guarantee above.
Three assertions in `FullFeatureTest` that accepted “200, 400, 404 or 503” on removed relational
routes were **tightened** to require 404, and `GET /api/schema/collections` (a path that never
existed) was replaced with a real `GET /api/schema` listing check. No assertion was weakened and no
valid test was deleted to make the suite green.

**NoSQL replacements for the removed gates:** the contract gate now asserts that a query on an
unknown collection answers 404 and creates nothing, that a delete of an unknown collection creates
nothing, and that a document query returns the same count after a restart (exactly-once survival).

---

## 5. Measured footprint (reproducible with `mvn -B clean verify`)

| Artifact | Size |
|---|---|
| `target/junify-db-core-1.0.0.jar` (shaded, published artifact) | **3,076,575 B = 2.93 MiB = 3.08 MB** ✅ under the 5 MB goal |
| — of which compiled classes | 85.1 % |
| — of which console assets (`static/`, 11 entries) | 100 KB compressed / 220 KB raw (3.3 %) |
| — of which documentation/logo SVGs | 2.0 % |
| Required runtime dependencies (bundled in the shaded jar) | jackson-databind 1.65 MB, jackson-core 0.58 MB, jackson-datatype-jsr310 0.13 MB, jackson-annotations 0.08 MB, slf4j-api 0.07 MB |
| Optional logging backend (not bundled by design) | slf4j-simple 15.7 KB |
| Provided + optional (never on a consumer's classpath) | `jnosql-mapping-api-core`, `jakarta.persistence-api`, `hibernate-core`, `jakarta.enterprise.cdi-api`, micrometer, JMH |
| Optional framework adapters | spring-boot-starter 11 KB, quarkus-extension runtime 8 KB, micronaut-integration 34 KB |

Interpretation: the 5 MB goal describes the published core JAR, which meets it at 3.08 MB including
console assets and the bundled JSON/`slf4j-api` dependencies. It is **not** a claim about a consumer's
total distribution — an application that also adds a logging backend and a framework adapter is
adding its own artifacts on top.

---

## 6. Blockers, gaps, and checks not run

| Item | Status | Note |
|---|---|---|
| `deep-test.ps1` (and its execution) | **PASS** | No PowerShell on this machine, so the sweep was ported to bash as `scripts/deep-test.sh` with expectations re-verified against the handlers, and executed: **90 checks, 0 failures** ([deep-test-run.log](deep-test-run.log)) |
| Browser screenshot/video export | **NOT RUN** | The preview webview produces no frames ("not compositing") in this environment — retried twice on a live server and it still captures nothing. All browser verification is therefore DOM/network-level (see the live-browser row in §4), which is weaker only in that it proves the DOM, not the pixels. |
| `docs/assets/junifydb-banner.png` (OG/Twitter social banner) | **NOT VERIFIED** | Raster asset last generated 2026-09-21; its rendered text could not be inspected here. Regenerate before a public announcement if it shows the old dual-engine wording. The README logo and its `alt` text are NoSQL-correct. |
| Maven Central publication | **BLOCKED** | Needs release credentials/authorization; no publish step was run. |
| GitHub Pages deployment | **BLOCKED (by policy)** | `pages.yml` publishes `docs/`; nothing was pushed or deployed from this session. |
| `scripts/console-contract-gate.sh` / `console-auth-gate.sh` / `deep-test.sh` end-to-end | **PASS** | Executed against the shaded jar from the green build: contract gate 95 checks 0 failures (seeding, console GETs, destructive-route safety, transactions, bulk, query operators, malformed-query 400s, no-create-on-read, TTL, vector persistence, CORS policy, restore round trip, restart, kill -9 WAL recovery, shipped-jar picker wiring); auth gate 36 checks 0 failures (anonymous 401 everywhere, wrong key 401, real key 200 everywhere, authenticated write + readback, login/logout session lifecycle); deep test 90 checks 0 failures (every feature family plus retired-SQL-route 404s and anonymous static files) |
| Pre-refactor SQL evidence under `docs/release-audit/`, `docs/audit/`, `docs/browser-testing/` | **RETAINED** | Historical record, labeled by banner or notice; not rewritten. Review round 2 added the `SUPERSEDED` banner to the 20 remaining unlabeled pre-refactor console docs (`docs/release-audit/console/*.md`, `docs/release-audit/evidence/console/*.md`) and a directory-level [PRE-REFACTOR-NOTICE](../PRE-REFACTOR-NOTICE.md) for the numbered `00-…74-` audit series; the sweep also confirmed the live UI's only `sql`-substring matches are the “NoSQL” engine badges |

---

## 7. Release assessment

**READY** for: a source release / local Maven `install` / internal use — the product is coherent
(no relational engine, no SQL API, no SQL UI, no SQL claims), the build is reproducible from a clean
tree, the full core suite is green (847/847) with the Spring Boot demo at 28/28 including the deep
Hibernate-integration suite, and the published core JAR is 3.08 MB.

**NOT READY** for an announced Maven Central release until: release credentials and the publication
step are authorized and executed, and the social banner asset is regenerated/verified.

**Smallest remaining milestone to ship**: sign and publish `junify-db-core-1.0.0` (plus the three
optional adapters) to the chosen repository, then re-run `mvn -B clean verify` on the tagged commit
and refresh the banner image — everything else required for the NoSQL-only release is in place.

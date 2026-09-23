# 00 — Current Codebase Assessment

Deep assessment of JUNIFY-DB **as it actually exists**, performed after the recoverable
baseline (`junifydb-baseline-before-public-release-audit-20260922` @ `b10b6cd`) and before
any transformation work. Every claim below is grounded in an executed command or an
existing audit file; where the code and the aspiration differ, the code wins and the gap
is stated.

**Scope:** repository structure, architecture, engine boundaries, implementation surface,
Console, website, tests, dependencies, footprint, product honesty.

**Method:** `git` recon, `pom.xml` parsing, package trees, live endpoint probes against a
running Console (this and the nine preceding rounds), and the 74-file release-audit corpus
under `docs/release-audit/`.

---

## 1. Repository assessment

### 1.1 Structure as built

| Dimension | Reality |
|---|---|
| Build system | Maven, **single module** — `org.junify:junify-db-core`, `<packaging>jar</packaging>`, `<modules>` **empty** |
| Main sources | **97** `.java` files |
| Test sources | **62** `.java` files |
| Demos | 10 Maven sub-projects under `demo/` (spring-boot, quarkus, vertx, micronaut, batch, advanced-queries, annotation-showcase, load-and-stress, end-to-end-validation, demo-common) |
| Console frontend | 3 static pages (`index.html`, `login.html`), `js/console.js` (864 lines), `css/console.css` (573 lines) + `enhancements.css` |
| Website | `docs/index.html` (GitHub Pages source) + `docs/assets/` brand assets |
| CI/CD | `.github/workflows/ci.yml`, `pages.yml` |
| Release config | `maven-gpg-plugin`, `central-publishing-maven-plugin`, `maven-source-plugin`, `maven-javadoc-plugin` in a release profile |
| Docs | `README.md`, `CHANGELOG.md`, `docs/release-audit/` (**74 files**), `docs/browser-testing/`, `demo/*.md` |
| Branch/tag state | `main` @ `b10b6cd`; tags `v0.1.0-package-migration`, `v0.2.0-mvcc-kernel`; baseline branch created by this audit |
| Working tree | 29 modified-uncommitted files — **owned by a concurrent session**, deliberately untouched |

### 1.2 The single most important structural fact

**The 14-module architecture the product vision describes does not exist.** There is no
`junify-db-sql`, `junify-db-nosql`, `junify-db-storage-*`, `junify-db-jdbc`, or
`junify-db-console` artifact. There is one 3.11 MB shaded jar whose *packages* provide
those separations:

```
org.junify.db
├── JunifyDB.java        (public entry point)
├── api/                 (public API surface)
├── core/                (lifecycle, config, schema validation, MVCC)
├── nosql/               (document, kv, columns, repositories)
├── sql/                 (parser, ast, engine — 8 files)
├── storage/             (memory, file, lsm-tree, btree engines + SPI)
├── index/               (B-tree, HNSW)
├── transaction/         (transaction machinery)
├── adapter/  jpa/       (integration adapters — JPA-style, plus others)
├── security/  config/   (auth, configuration)
├── console/             (http backend + handlers + static UI resources)
└── benchmark/  example/
```

WAL lives with the storage layer (`wal/`-equivalent recovery code paths exercised by the
hard-kill tests), and the framework integrations (Spring Boot, Quarkus, Vert.x, Micronaut)
are **separate demo projects**, not packages in the core.

This is a legitimate design for a lightweight embedded database (H2 and SQLite are also
single jars), but it is **not** independently deployable engine artifacts. Recorded as a
vision/implementation gap, not as a defect — see §2.4 and ADR-001.

---

## 2. Architecture assessment

### 2.1 Runtime dependency graph (compile scope)

Exactly **three** mandatory runtime dependencies:

```
jackson-databind          (JSON serialization)
jackson-datatype-jsr310   (java.time support)
slf4j-api                 (logging facade)
```

Everything else is scoped honestly: `jakarta.enterprise.cdi-api`, `jnosql-mapping-api-core`,
`jakarta.persistence-api`, `hibernate-core` are **`provided`**; `slf4j-simple` is
**`runtime`**; JMH/micrometer are **optional/profiled**; JUnit is **`test`**. This is the
single strongest architectural property of the project: the core has no framework
pollution and no web server inside it.

### 2.2 Engine boundaries — measured, not assumed

| Boundary | Finding |
|---|---|
| Separate query languages | **Yes** — `sql/parser/{SqlLexer,SqlParser}` and the NoSQL JSON query parser are distinct implementations |
| Separate ASTs | **Yes** — `sql/ast/{SqlStatement,Expression}` vs. NoSQL filter expressions |
| Separate execution pipelines | **Yes** — `sql/engine/SqlEngine` vs. `nosql/document/DocumentCollection` query paths |
| Separate catalogs/metadata | **Shared in substance** — SQL "tables" **are** document collections |
| Storage abstraction | **Shared by design** — `storage/` SPI (memory, file, LSM, B-tree) serves both |
| Circular dependencies | **None detected** |
| Coupling direction | `sql` → `nosql.document` (3 imports: `SqlEngine` ×2, `SqlRow` ×1). The reverse does not exist |

**Honest characterisation:** JUNIFY-DB is a **multi-model database with two genuine query
engines over one shared storage and catalog substrate** — not two independently evolvable
database products. This is a coherent, defensible design (it is how "multi-model" engines
are normally built), and it is the basis of the engine-separation verdict in the final
report. It also explains every behaviour found in the R-48 round: because SQL resolves its
tables through `JunifyDB.documentCollection()`, SQL reads inherited the document store's
auto-create semantics until that was fixed.

### 2.3 Shared infrastructure inventory

File I/O and page storage (`storage/file`), WAL and recovery (`wal/`), buffer/page
management, locking and MVCC (`core`), serialization (Jackson), metrics, configuration,
and lifecycle. All are legitimately shared — none erases the SQL/NoSQL semantic split.

### 2.4 Architecture verdict

- **No circular dependencies, no dead modules, no placeholder packages.**
- **Vision/implementation gap:** the modularisation into separately deployable engine
  artifacts (ADR-001) is aspirational; the codebase delivers package-level separation only.
- **Accidental coupling that was real and is now fixed:** SQL read paths mutating the
  shared catalog (R-48).

---

## 3. Implementation assessment — relational (SQL) engine

**Surface:** 8 files (`SqlLexer`, `SqlParser`, AST, `SqlEngine`, `SqlResultSet`, `SqlRow`,
`SqlUnknownTableException`).

**Note on JDBC (updated 2026-09-23, R-76):** a JDBC driver now ships under
`org.junify.db.jdbc` and is discoverable through `META-INF/services/java.sql.Driver`. It is
**not** JDBC-compliant — `Driver.jdbcCompliant()` returns `false` — and does not implement
explicit transactions, savepoints, batch execution, or schema reflection. See
`27-jdbc-and-sql-compatibility.md` and `73-jdbc-driver-evidence.md`.

| Capability | Status | Evidence |
|---|---|---|
| SELECT / INSERT / UPDATE / DELETE | VERIFIED | `SqlUnknownTableTest` (9), contract-gate SQL block, live probes |
| WHERE / ORDER BY / GROUP BY / HAVING / LIMIT / OFFSET | VERIFIED | Lexer/parser tokens + executed queries |
| INNER JOIN | VERIFIED | Parser + executed joins |
| CREATE TABLE / DROP TABLE | VERIFIED | `parseCreate`/`parseDrop` (lines 231/256); website copy corrected this round to state **this is the entire DDL surface** |
| ALTER TABLE / CREATE INDEX / CREATE VIEW / CREATE SEQUENCE | NOT IMPLEMENTED | parser contains only the `TABLE` DDL keyword — no `ALTER`, `INDEX`, `VIEW`, or `SEQUENCE` tokens |
| Aggregations, expressions, CAST | PARTIALLY VERIFIED | doc 08 |
| Transactions / savepoints over SQL | PARTIALLY VERIFIED | shares `core` transaction machinery; doc 13 |
| **JDBC driver** | **PARTIAL** (2026-09-23, R-76) | `org.junify.db.jdbc` + `META-INF/services/java.sql.Driver`; `JdbcDriverTest` (13) + compiled-consumer run |
| Constraints: PRIMARY KEY / UNIQUE / NOT NULL / FOREIGN KEY / CHECK | **ENFORCED** (2026-09-23, R-74 + R-75; inline + table-level, durable, both FK directions) | docs 71, 72 |
| Indexes (SQL-managed, `CREATE INDEX`) | NOT IMPLEMENTED (probe-level indexes exist) | doc 12 |
| Sequences, identity columns, views, procedures, functions, triggers | NOT IMPLEMENTED | parser has no keywords for them |
| Query planner / cost estimation / EXPLAIN | NOT IMPLEMENTED | execution is interpretation, not planning |

**Honest positioning:** this is a **SQL query dialect + execution layer over a document
store**, valuable for embedded and admin workloads, and **not** a full relational DBMS. As of
2026-09-23 the key/null/referential/`CHECK` constraints **are** enforced and a **PARTIAL JDBC
driver** exists; what remains absent is a query planner/`EXPLAIN`, sequences, views, procedures,
functions and triggers, and JDBC's transactional/schema-reflection surface.
`docs/release-audit/05-relational-engine-audit.md` and the corrected website/Console copy state
this.

### 3.1 Non-relational (NoSQL) engine

| Capability | Status |
|---|---|
| Document CRUD, collections, nested docs, arrays | VERIFIED |
| JSON query API (`$eq/$ne/$gt/$gte/$lt/$lte/$in/$nin/$exists/$regex/$and/$or`) | VERIFIED — and repaired this session (R-45…R-47) |
| Key-value store (strings, hashes, lists, sets) | VERIFIED |
| Column-family-style KV namespaces | VERIFIED (probe level) |
| Repositories (`@Repository`-style), schema validation | VERIFIED |
| TTL and expiry | VERIFIED — repaired R-51 (expired docs no longer readable) |
| Vector indexes (HNSW) | PARTIAL — functional + persistent since R-52, but an auxiliary index, **not a first-class vector database model** |
| Indexes (B-tree, HNSW) | VERIFIED |
| Jakarta NoSQL compatibility | PARTIAL — mapping/annotation support; doc 26 |
| Persistence / recovery across FILE, LSM_TREE, B_TREE | VERIFIED — R-53 fixed catalog rediscovery; WAL recovery verified under hard kill |

---

## 4. Console assessment

**Backend:** `console/http/JunifyDBServer.java` registers **22 distinct `/api/...` routes**
covering collections, columns, indexes, KV, vectors, SQL, CDC, backup, bulk, transactions,
schema, health, metrics, stats, CORS. **Frontend:** `index.html` (SPA workspace), `login.html`,
`console.js`, `console.css`.

**Trace method:** every route the UI calls was exercised end-to-end against a running
server over ten audit rounds. Result — **23 defects found and fixed (R-31…R-53)**, all with
falsified regression tests and live before/after evidence. The dominant defect family was
**fake success**: endpoints answering `200 success` for operations that did nothing
(unknown-table SELECT creating a collection instead of failing; vector search on a typo'd
index auto-creating an empty one; `DROP TABLE` on a missing table reporting success).

**Verified honest and left alone:** schema registration/validation, cleanup counts,
auth/session handling (`console-auth-gate.sh`), engine selection.

**Known Console gaps, documented not hidden:** vector indexes are console-server-scoped
(persisted since R-52, in-memory only for `IN_MEMORY` engine); CDC and bulk paths are
probe-level.

**UI:** yellow-and-white design tokens shared with the website (`console.css` +
`docs/assets/`), `logo.svg`/`favicon.svg` served from the jar, SQL/NoSQL workspaces
visually and terminologically distinguished, corrected SQL hint text this round.

---

## 5. Website assessment

- **Source:** `docs/index.html` + `docs/assets/` (GitHub Pages from `docs/`).
- **Brand assets present:** `junifydb-logo*.png`, `junifydb-mark-{64,256,512}.png`,
  `junifydb-banner*.png`, transparent variants, `favicon-{64,256}.png`. No separate "mascot"
  artwork exists — the **mark** is the mascot; consistent across website, README, Console,
  favicon.
- **Corrections made this round (R-docs):** removed the false "no DDL" claim (the parser
  supports `CREATE/DROP TABLE`), removed the stale "fixed 128 dimensions" vector claim
  (dimensionality has been client-derived since R-19), and aligned the Console's SQL error
  hint with the same truth.
- **Remote state:** the corrected `index.html` is on `main` but GitHub Pages deploys on
  push — **the live site still shows the pre-correction copy**. Deployment is the only
  outstanding website item and it is a push-permission matter, not a code matter.

---

## 6. Test assessment

| Suite | Result |
|---|---|
| Core (`mvn test`) | **795/795**, 0 failures, 0 errors, 0 skipped (785 at assessment time; +6 R-55, +4 R-59) |
| CLI module | 4/4 |
| `demo/end-to-end-validation` | 4/4 across four engines |
| `scripts/console-contract-gate.sh` | PASS on **FILE, LSM_TREE, B_TREE** (wired into CI) |
| `scripts/console-auth-gate.sh` | PASS |

**Quality interrogation performed** — tests are not trusted by existing alone. Every fix
this session was **falsified**: its new tests were run against the pre-fix commit in a
throwaway `git worktree`, and each test was required to *fail there*. Observed failures at
pre-fix commits: 9/11 (query operators), 4/9 (unknown-table), 9/9 (vectors/TTL), 3 (vector
persistence), 2/4 (engine discovery). Two tests were found to have **codified defects as
expected behaviour** (`queryParser_unknownOperatorIgnored`, the TTL "expired docs are
readable" assertion) and were inverted.

**Weak spots recorded honestly:** the JDBC driver covers a `PARTIAL` surface (no explicit
transactions or schema reflection) and records a discovery defect found and fixed this round
(R-76); `demo/` projects resolve a **stale installed `junify-db-core`** unless `mvn install` is
run first — documented in doc 35 after it caused a false-negative demo run.

---

## 7. Dependency and footprint assessment

| Artifact | Size |
|---|---|
| Shaded core jar | **3,113,904 bytes (3.11 MB)** |
| Mandatory runtime deps | 3 (`jackson-databind`, `jackson-datatype-jsr310`, `slf4j-api`) |
| Combined runtime (jar + mandatory deps) | **< 5 MB** ✅ |
| Provided/optional (not counted) | cdi-api, jnosql-mapping-api-core, persistence-api, hibernate-core, micrometer, JMH |
| Test/demo deps | JUnit 5, framework stacks — correctly isolated from the core |

**Verdict: the <5 MB claim holds for a precisely defined scope** — core jar plus the three
mandatory runtime dependencies. It does **not** hold if framework integrations, Console,
or demos are included; the website and README state the scope, and `02-size-and-footprint-audit.md`
records the bytes. No heavy library, no vulnerable transitive dependency of note, no
duplicate dependency.

---

## 8. Product and release assessment

**What the product actually is:** a 3.11 MB embedded Java database with a document/KV
NoSQL engine, a SQL query dialect over the same storage, four storage engines (memory,
file, LSM-tree, B-tree), WAL+recovery, TTL, vector indexing, schema validation,
repositories, framework adapters, a browser Console, and a 785-test suite.

**What it can honestly support today:** embedded and local-development workloads,
prototyping, admin/ops via the Console, SQL-shaped querying of documents, and NoSQL
document/KV storage with durability on WAL-backed engines.

**What must be removed from public claims:** "production-ready" and any implication of a
full relational DBMS (constraints, JDBC, planner, procedures) or of vector-database
capability as a first-class model.

**Version judgment:** a **0.x release is the honest label.** A 1.0 database is expected to
ship JDBC and constraint enforcement; this project does not. `v0.9.0` communicates a
mature, tested, honestly-limited embedded database — which is exactly what exists.

---

## 9. Assessment conclusion

| Dimension | Verdict |
|---|---|
| Baseline recoverable | **PASS** (see `baseline/`) |
| Architecture coherence | **PASS with one documented gap** (single module vs. modular vision — ADR-001) |
| Engine separation | **PASS as multi-model over shared substrate**, not as independently deployable engines |
| SQL engine | **PARTIAL** — real query dialect with enforced constraints and a PARTIAL JDBC driver; no planner or routines |
| NoSQL engine | **PASS** — genuinely implemented and now behaviourally honest (R-31…R-53) |
| Console | **PASS** — 22 routes traced end-to-end, 23 fake-success/correctness defects fixed |
| Website | **PASS after this round's corrections**; remote deploy pending |
| Brand consistency | **PASS** — shared yellow/white tokens, logo, mark, favicon across all surfaces |
| Footprint | **PASS** — 3.11 MB core + 3 mandatory deps, both under 5 MB |
| Test quality | **PASS** — 795 + 4 + 4 green, every fix falsified against its pre-fix commit |
| Honesty of public claims | **PASS after correction** of the DDL, dimension, and DDL-hint falsehoods |
| Release blockers remaining | **None technical** — R-13 (Maven Central credentials) is external; R-20 (mixed-writer) is a documented fundamental limitation |

# JUNIFY-DB — Product Blueprint

**Status:** canonical product blueprint for the public release.
**Date:** 2026-09-23
**Companion documents:** `USER_STORY_MAP.md` (this directory), `../release-audit/final-go-no-go-decision.md`.

This blueprint is derived from the measured state of the repository, not from aspiration. Every
number below was re-measured on 2026-09-23 (see `../release-audit/baseline/`). Where a capability
does not exist, it is listed as **NOT IMPLEMENTED** rather than described in the future tense.

---

## 1. Product Vision

> **JUNIFY-DB is a lightweight embedded JVM database platform providing a relational SQL engine
> for structured data and a non-relational NoSQL engine for flexible workloads.**

Both engines:

- run **inside the JVM** (no ports, no daemons, no child processes for basic usage);
- work **in-memory** with zero configuration and **on disk** via a write-ahead log;
- are **independently configurable and independently testable**;
- share one addressable substrate (`JunifyDB` instance) while keeping **separate query languages,
  parsers, ASTs, and execution pipelines**.

The honest one-line summary, measured against the code: *a multi-model embedded database whose
SQL surface is a query/DDL dialect over the same document store as the NoSQL surface.*

---

## 2. Positioning

```
                           Embedded / In-Process
                                     ▲
                                     │
                 H2 / HSQLDB         │   ★ JunifyDB
             (Relational Embedded)   │   (Multi-Model Embedded)
                                     │
────────────────────────────────────-┼──────────────────────────────────► Multi-Model
                                     │
              PostgreSQL / MySQL     │   MongoDB / Redis / Cassandra
             (Relational Standalone) │   (Distributed NoSQL Daemons)
                                     │
                                     ▼
                           Client-Server / External
```

JUNIFY-DB sits in the **embedded + multi-model** quadrant. The nearest neighbours are H2 (embedded,
relational-only, has JDBC and constraints) and embeddable NoSQL harnesses (not JVM-native). The
product must **not** claim H2's relational completeness or MongoDB's distribution semantics.

---

## 3. Personas

| Persona | Primary need | Success looks like |
|---|---|---|
| **P1 — Application developer (plain Java)** | A database in one line, no infra | `JunifyDB.inMemory()` works, data survives if file-backed |
| **P2 — Test engineer** | Fast, dependency-free fixture store | Deterministic tests, no Docker, isolated per test |
| **P3 — Framework developer (Spring/Quarkus/Micronaut/Vert.x)** | Auto-wired bean, config binding | Starter/extensions compile and run a real demo |
| **P4 — Data/platform engineer** | Observe and operate the store | Console shows real state; metrics are truthful |
| **P5 — Evaluator / evaluator-release** | Decide whether to adopt | Claims match behavior; limitations stated plainly |
| **P6 — Maintainer** | Change the code safely | Gates fail on regression; ADRs explain design |

---

## 4. Engine Model (measured)

**Storage engines (4), selectable per instance — `StorageEngineType`:**

| Engine | Backing structure | Persistence | WAL |
|---|---|---|---|
| `IN_MEMORY` | concurrent map | none | n/a |
| `FILE` | append-only log + snapshot | yes | yes |
| `B_TREE` | B+ tree index (+ WAL since the durability round) | yes | yes |
| `LSM_TREE` | memtable + SSTables | yes | yes |

**Logical engines (2), independent at the semantic level:**

| | `JUNIFYDB-RDBMS` | `JUNIFYDB-NOSQL` |
|---|---|---|
| Entry points | `db.sql(...)`, `db.from(Entity.class)` | `db.documentCollection(...)`, `db.keyValueBucket(...)`, `db.listBucket/setBucket/hashBucket`, `db.columnFamily(...)` |
| Language | implementation-defined SQL dialect | fluent criteria + MongoDB-style JSON filter |
| Parser/AST | `sql/parser`, `sql/ast` | `nosql/query` |
| Verified surface | SELECT/INSERT/UPDATE/DELETE, WHERE, ORDER BY, GROUP BY, HAVING, LIMIT, OFFSET, INNER JOIN, CREATE/DROP TABLE, aggregations | document CRUD, nested docs, arrays, projections, sort/paging, KV(string/hash/list/set), TTL, B-tree + HNSW indexes, repositories |
| **NOT implemented** | JDBC driver, foreign-key and `CHECK` constraints, sequences, views, stored procedures, functions, triggers, query planner/EXPLAIN. (**Supported since 2026-09-23, R-74:** inline/table-level `PRIMARY KEY`, `UNIQUE`, `NOT NULL` — enforced and durable) | full document-database breadth (aggregation pipelines, change streams beyond CDC, transactions at SQL surface granularity) |

The shared substrate (storage, catalog, WAL, locking) is **intentional** (ADR-002) and
**documented as a defect vector** where it leaks semantics between engines (the compensating
control is the SQL/NoSQL contract gate).

---

## 5. Module & Package Map (measured)

- **Single Maven module**: `org.junify.db:junify-db-core:1.0.0` (`pom.xml` at root).
- **98** main Java sources, **72** test Java sources; **832** tests green.
- Satellite projects (separate POMs, not aggregated): `cli/`, `spring-boot-starter/`,
  `quarkus-extension/`, `micronaut-integration/`, and **10** `demo/` projects.
- Packages: `api`, `api.reactive`, `core` (+ backup, cache, cdc, crypto, event, health, metrics,
  migration, pool, record, schema, util), `index` (+ hnsw), `nosql` (+ column, document, kv,
  query), `sql` (+ ast, engine, parser), `storage` (+ spi), `transaction` (+ mvcc), `console`
  (+ http), `security`, `config`, `adapter/jnosql`, `jpa`, `benchmark`, `example`.

The aspirational 14-module split does **not** exist (ADR-001). This is recorded as deferred work,
not as a shipped fact.

---

## 6. Dependency Policy

**Mandatory runtime dependencies: exactly three.**

- `com.fasterxml.jackson.core:jackson-databind:2.17.0`
- `com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0`
- `org.slf4j:slf4j-api:2.0.12`

Everything else is `provided`, `runtime`, or `optional`:

- `jakarta.enterprise.cdi-api` — provided + optional (frameworks supply their own CDI).
- `jnosql-mapping-api-core`, `jakarta.persistence-api`, `hibernate-core` — provided + optional
  (annotation interop, detected reflectively, zero-impact if absent).
- `slf4j-simple` — runtime + optional (standalone default logger; excluded from the shaded jar).
- JUnit — test scope only.

**Rule:** no framework is ever a mandatory compile dependency of the core.

---

## 7. Footprint Budget

| Artifact | Measured 2026-09-23 |
|---|---|
| Shaded core jar `junify-db-core-1.0.0.jar` | **3,122,887 bytes (3.12 MB)** |
| + three mandatory runtime deps | **combined runtime < 5 MB** (gate satisfied) |
| Scope of the number | core jar + 3 deps only; excludes Console, framework
 integrations, demos, test deps (all correctly scoped) |

**Gate:** the core runtime must stay under 5 MB. The scope qualifier must always accompany the
claim.

---

## 8. Release Constraints (non-negotiable)

1. The repository is the source of truth; the checked-in code defines truth, not marketing copy.
2. No claim of ACID, durability, compatibility, performance, or production-readiness without
   execution-backed evidence.
3. Limitations (no JDBC, no foreign-key/`CHECK` constraints, no
   sequences/views/procedures/triggers, no planner, SQL is a query layer over the document
   store — though inline `PRIMARY KEY`/`UNIQUE`/`NOT NULL` **are** enforced) ship **on the
   release page and README**, not in a footnote.
4. Release path is **GitHub-first** (ADR-007); Maven Central is **not** claimed until credentials
   exist and a dry-run passes.
5. Any UI feature is complete only when frontend, API, backend, engine, storage, and UI feedback
   are all validated.

---

## 9. Scope: In / Out

**In scope for the public release (v0.9.x):**

- Embedded in-memory and file/ B-tree/ LSM persistence.
- Document, key-value, list/set/hash, column-family access.
- SQL dialect: query + CREATE/DROP TABLE.
- Transactions (core + MVCC + savepoints) and TTL.
- Four framework integrations, each with a runnable demo.
- Embedded Console (22 API routes) with browser-verified workflows.
- Observability: events, metrics, CDC, audit trail.

**Explicitly out of scope (post-release):**

- JDBC driver; relational constraints; sequences/views/procedures/functions/triggers; query
  planner/`EXPLAIN`.
- Per-engine artifact split (single-jar remains).
- Maven Central publication (blocked on credentials).
- Distributed/clustered operation; hardened internet-facing multi-tenant deployment.
- First-class vector-database positioning (HNSW remains an auxiliary index).

---

## 10. Release Gates (definition of "release-ready")

| Gate | Target | Current (2026-09-23) |
|---|---|---|
| Core tests | 100% pass, 0 skipped | **832 / 832 pass** ✅ |
| Line coverage | ≥ 70% (coverage-check profile) | **75.5%** ✅ |
| Branch coverage | reported | **59.2%** (informational) |
| Core jar | < 5 MB | **3.12 MB** ✅ |
| Console contract gate | PASS on FILE/LSM_TREE/B_TREE | PASS (prior round; not re-run this session) |
| Demos | all pass from clean checkout | PASS (prior round) |
| Reproducibility | byte-stable build | PASS (prior round) |
| Maven Central | dry-run validated | **NOT VERIFIED** (credentials absent) ❌ |

---

## 11. Traceability

| Blueprint section | Audit evidence |
|---|---|
| Engine model | `../release-audit/05-relational-engine-audit.md`, `06-nosql-engine-audit.md`, `07-multi-model-architecture.md` |
| Module map | `03-repository-architecture.md`, `04-module-and-package-architecture.md` |
| Dependencies | `45-dependency-and-supply-chain-audit.md` |
| Footprint | `02-size-and-footprint-audit.md` |
| Architecture decisions | `67-architecture-decision-records.md` |
| Limitations/defects | `53-defect-register.md`, `54-release-blocker-register.md` |
| Final decision | `final-go-no-go-decision.md` |
| Stories | `USER_STORY_MAP.md` |

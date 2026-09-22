# 01 — Release Scope

## Scope
What is in scope for the first public release, and what is explicitly out of scope.

> **Version note (2026-09-22).** The canonical decision is `final-go-no-go-decision.md`:
> recommended version is **`v0.9.0`**, published from **GitHub Releases** with the explicit
> limitation list (ADR-007/ADR-008). The `1.0.0` framing below is the round-1 scope and is kept
> as history; the in/out-of-scope *content* still holds, with the corrections marked inline.
> Sequencing of remaining work: `68-implementation-triage.md`.

## In Scope (verified present in repository)
| Area | Evidence |
|---|---|
| Core engine `junify-db-core` (single Maven module; artifact version 1.0.0, release tag `v0.9.0`) | root `pom.xml` |
| Storage engines: IN_MEMORY, FILE, B_TREE, LSM_TREE | `src/main/java/org/junify/db/storage/spi/` |
| Multi-model API: Document, KV/List/Set/Hash buckets, ColumnFamily | `src/main/java/org/junify/db/nosql/`, `column/` |
| Built-in SQL engine (SELECT/INSERT/UPDATE/DELETE, JOIN, GROUP BY, `CREATE/DROP TABLE`) | `src/main/java/org/junify/db/sql/` (8 files: lexer, parser, AST, engine, results; exercised by `SqlEngineTest`, `SqlUnknownTableTest`, the contract gate's SQL block, and the console SQL Studio) |
| MVCC transactions (snapshot isolation, first-writer-wins) | `src/main/java/org/junify/db/transaction/mvcc/` |
| WAL + crash recovery (File/LSM engines) | `WriteAheadLog.java`, fixed per audit (see 15) |
| Embedded HTTP console (UI + REST API + API-key auth) | `console/http/JunifyDBServer.java`, `static/` |
| Framework integration projects (separate builds) | `spring-boot-starter/`, `quarkus-extension/`, `micronaut-integration/`, `demo/` (vertx) |
| Demos (8+ standalone Maven projects) | `demo/` |
| CLI shell | `cli/` |

## Out of Scope (explicitly not claimed)
- Full ANSI:92 SQL grammar. **DDL is limited to `CREATE TABLE` / `DROP TABLE`** — no `ALTER`, no `CREATE INDEX`, no constraints, views, sequences, stored procedures, or full type system. (Corrected 2026-09-22: this line previously said "no DDL", which was false — the parser implements `parseCreate`/`parseDrop`; see R-54.)
- JDBC driver. `27-jdbc-and-sql-compatibility.md` documents the absence.
- SQL-file B-Tree page store; `BTreeEngine` is heap-resident with snapshot persistence only.
- Cryptographically tamper-evident audit log; the audit trail is an in-memory ring buffer.
- Cluster/distributed operation; single-process embedded only.
- Kafka CDC connector as a supported integration (code exists; it is optional, excluded from coverage, and not demo-verified in this audit).
- Testcontainers integration (none exists; `34-` records this honestly).

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| S-01 | CONFIRMED | Medium | Root POM does not aggregate the starter/demo modules, so "the build" and "the integrations" are different things; CI builds only the core (see 44). |
| S-02 | ACCEPTABLE | Low | Out-of-scope list above must be reflected in README claims (done in this audit; see 40). |

## Improvement Plan
Add an aggregator/flatten POM strategy in a later minor release so `mvn verify` at root covers starters; publish a SUPPORTED-MATRIX page. The `maven-central` profile already supplies the source/javadoc/GPG plugins (doc 46, MC-01 fixed); Central publication itself remains gated on credentials (R-13).

## Current verification state (2026-09-22)
**795/795** core tests + 4/4 CLI + 4/4 cross-engine demo; contract gate PASS on FILE,
LSM_TREE and B_TREE; auth gate PASS; core jar **3,114,172 bytes** with three mandatory
runtime dependencies — inside the <5 MB requirement. Baseline comparison and delta:
`baseline/BASELINE-COMPARISON.md`.

## Acceptance Criteria
README scope claims map 1:1 to items in the In-Scope table with a link to evidence; every out-of-scope item is absent from README marketing.

## Final Status
**CONDITIONAL PASS** (scope is honest after README corrections; module aggregation deferred post-release).

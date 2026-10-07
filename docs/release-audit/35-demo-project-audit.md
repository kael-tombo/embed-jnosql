# 35 — Demo Project Audit

## Scope
The 8+ demo projects under `demo/` — buildability, documented-command accuracy, hidden knowledge.

## Expected Behavior
Per the release standard: a new developer can follow documented commands and reproduce demo output without hidden knowledge.

## Current Implementation
`demo/` contains `demo-common` + standalone Maven projects (spring-boot-demo, quarkus-demo, micronaut-demo, vertx-demo, annotation-showcase, advanced-queries, batch-processing, load-and-stress, end-to-end-validation). Each has its own POM, README sections, and tests; they reference `embed-jnosql-core` **1.0.0** — resolved from the local repository after `mvn install` of the core (an implicit prerequisite that must be documented).

## Validation Performed
- Demo sources read; prior sessions executed several demos successfully (annotation-showcase, batch, stress, e2e, spring-boot).
- **Full demo suite re-executed against the fixed build (2026-09-21, post-release-audit fixes):** core installed (`mvn install -DskipTests`, EXIT=0), `demo-common` installed, then `mvn clean test` per demo following `demo/RUNBOOK.md` §1. Results (from each demo's surefire XML, all written during this run):

| Demo | Tests | Failures | Errors | Skipped | Notes |
|---|---|---|---|---|---|
| spring-boot-demo | 6 | 0 | 0 | 0 | Real Spring Boot 3.2.5 context boot; console bound to 9090 |
| quarkus-demo | 4 | 0 | 0 | 0 | Quarkus 3.8.0 started in 4.38s, REST endpoints exercised |
| micronaut-demo | 4 | 0 | 0 | 0 | Micronaut DI + controller tests green |
| vertx-demo | 4 | 0 | 0 | 0 | Verticle deploy + flows green |
| end-to-end-validation | 4 | 0 | 0 | 0 | Multi-engine CRUD/transaction e2e |
| advanced-queries-demo | 5 | 0 | 0 | 0 | SQL/aggregation queries |
| annotation-showcase-demo | 5 | 0 | 0 | 0 | JNoSQL+JPA annotation interop |
| batch-processing-demo | 5 | 0 | 0 | 0 | Atomic batch ingestion + rollback |
| load-and-stress-demo | 6 | 0 | 0 | 0 | Live harness output: 50-thread run 18,382 ops/sec, 0 failures; all 5 load scenarios 0 fail |

**Total: 43 demo tests, 0 failures, 0 errors, 0 skipped across all 9 demos.** Raw logs under `.freebuff/demo-results/` (untracked evidence); stress-harness line output reproduced in the run log.

## Evidence
- Surefire XMLs in each `demo/<name>/target/surefire-reports/` (timestamps match run window 09:46–09:48).
- `.freebuff/demo-results/<demo>.log` — per-demo Maven output.
- Quarkus startup log line: "quarkus-ecommerce-demo 1.0.0 on JVM (powered by Quarkus 3.8.0) started in 4.380s".
- Stress harness: `[LOAD-02] threads=50 ops=2,500 success=2,500 fail=0 throughput=18382.4 ops/sec`.

Prior-session evidence: demo tree, older run records, `MultiEngineE2EValidationTest` references.

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| DM-01 | CONFIRMED | Medium | Implicit prerequisite: demos require `mvn install` of the core first (or version bump juggling). Must be step 0 in `demo/README`. |
| DM-02 | CONFIRMED (closed) | ~~Medium~~ → resolved | **All 9 demos re-executed on the fixed build, 43/43 tests green.** The 60-release-checklist pre-tag item is closed. |
| DM-03 | ACCEPTABLE | Low | Demo POMs pin framework versions (Boot 3.2, Quarkus 3.8, Micronaut 4.2, Vert.x 4.5) — matching README badges; confirmed live during this run. |

## Improvement Plan
`demo/README.md` with exact per-demo commands + expected output; root script `scripts/run-all-demos` for CI.

## Update (2026-09-22, engine-matrix round)
`end-to-end-validation` strengthened with **catalog-discovery assertions** (R-53): the
cold-restart phase now requires `getCollectionNames()` to list `products` and `orders`
without being asked, on every persistent engine — the exact assertion that failed for
LSM_TREE/B_TREE before the R-53 fix, and which this demo (claiming a "multi-engine
durability matrix") had been silently missing. 4/4 green after reinstalling the core
artifact (`mvn install` — the demo resolves `embed-jnosql-core` from the local repo, so a
demo run against a stale install silently tests old behavior; that prerequisite is now
 Load-bearing and documented here). Durability facts re-verified live: LSM_TREE recovers
a document across a hard kill (taskkill) via its WAL; B_TREE losing writes since the
last flush on a hard kill is the documented D-02 limitation (snapshot-on-flush, no WAL),
not a regression.

## Acceptance Criteria
Per-demo commands documented (follow-up); no hidden env vars found in demo sources (checked — none).

## Pre-tag demo run (2026-09-22) — the matrix caught a core defect

Re-running the demo matrix from a clean local install (`mvn install` for core, `demo-common`
and the three starters — the documented prerequisite, without which the demos silently test a
stale core) produced one failure and one repair:

| Demo | Result |
|---|---|
| `end-to-end-validation` | **4/4 PASS** (four engines) |
| `advanced-queries-demo` | **5/5 PASS** |
| `annotation-showcase-demo` | **FAILED 2/5** → **5/5 PASS** after the R-59 fix |
| `batch-processing-demo` | **5/5 PASS** |
| `vertx-demo` | **4/4 PASS** |
| `spring-boot-demo` | **6/6 PASS** |
| `quarkus-demo` | **4/4 PASS** |
| `micronaut-demo` | **4/4 PASS** |
| `load-and-stress-demo` | **6/6 PASS** |
| **Total** | **43/43 PASS** (9 demos; one defect found and fixed) |

**R-59 is the payoff.** `annotation-showcase-demo` persists an `@Entity @Table(name = "invoices")`
through a transaction and then runs `SELECT ... FROM invoices`; both SQL-backed tests errored with
`SqlUnknownTableException: Table 'invoices' does not exist`. The read was correct — the **catalog
was wrong**: collections written through a transaction never registered in
`EmbedJNoSQL.getCollectionNames()`, which was latent until R-48 made SQL reads resolve through a
non-creating path. Found by running the demos, not by reading code, and it existed *because* the
demo exercises the real JPA/transaction path that unit tests covered only piecewise.

That is the argument for keeping this matrix as a release gate: it is the only layer that
exercises annotations + transactions + SQL together the way a user does.

## Final Status
**CONDITIONAL PASS** — and the pre-tag run above is the reason to keep re-running it: this layer
found a High-severity catalog defect (R-59) that the 791-test core suite did not.

# 59 — Prioritized Fix Roadmap

## Scope
Ordered remediation plan with why/what/priority/risk/dependencies/acceptance/verification for each item.

> **SUPERSEDED for sequencing (2026-09-22).** This is the round-1 roadmap, written when the
> suite was at 669 tests and the decision document was `61-…`. Its P0 items are closed
> (suite green at **795/795**; CHANGELOG amended; CI Docker job removed; demos re-executed) and
> the P1/P2 items it lists are now triaged into three explicit buckets in
> **`68-implementation-triage.md`**. Use 68 for current order of work; keep this file as the
> historical plan. Version to tag is **`v0.9.0`** per `final-go-no-go-decision.md`.

## P0 — Before tagging the public release
| ID | Item | Why | What | Risk | Deps | Acceptance & Verification |
|---|---|---|---|---|---|---|
| P0-1 | Run full test suite on final build | tag needs green evidence | `mvn clean test` (669→677) | none | none | BUILD SUCCESS log archived |
| P0-2 | Re-run all demos via `mvn install` + per-demo commands | blocker policy: demos must work as documented | install core; boot each demo; record output | low | P0-1 | 8/8 demos boot; output recorded in 60 checklist |
| P0-3 | Amend CHANGELOG (R-01..R-06 fixes) | consumers must know behavior fixes | add "Fixed" entries | none | none | CHANGELOG diff reviewed |
| P0-4 | Remove/fix CI Docker job (R-11) | red CI on every push kills credibility | delete job or add Dockerfile | none | none | CI green |

## P1 — First post-release sprint (1.0.x)
| ID | Item | Why | What | Risk | Deps | Acceptance & Verification |
|---|---|---|---|---|---|---|
| P1-1 | CI: coverage gate (R-10) | make the 70% claim real | measure current; set gate to measured value or fix CI to run `-Pcoverage-check` | low | P0-4 | CI fails on coverage drop — **DONE 2026-09-21: `-Pcoverage-check` in CI; measured 73.6% line** |
| P1-2 | CI: starter/demo matrix (R-14) | upgrade integration claims to SUPPORTED | per-module jobs + demo smoke | low | none | matrix green badge in README — **DONE 2026-09-21: `integrations` + `demos` jobs; starters verified green locally** |
| P1-3 | `mvnw` shell script (R-12) | Linux/macOS onboarding | add wrapper script | none | none | wrapper runs on bash — **DONE 2026-09-21** |
| P1-4 | REST API reference | undocumented contracts (36-CB-01) | document endpoints/envelopes | none | none | every console call mapped — **DONE 2026-09-21: docs/api/REST-API.md (verified endpoints marked)** |
| P1-5 | Playwright UI suite | visual/flow regression (58) | 10-flow script + screenshots | medium | none | CI artifact with screenshots |
| P1-6 | demo/README per-demo commands | remove hidden prerequisite (35-DM-01) | document `mvn install` + commands | none | none | fresh-clone demo run follows docs — **DONE 2026-09-21: runbook pointer + module tree corrected** |

## P2 — 1.1
| ID | Item | Why | What |
|---|---|---|---|
| P2-1 | Maven Central publication (46-MC-01/02) | consumer adoption | source/javadoc/GPG plugins, staging, consumer-resolution proof — **PARTIAL 2026-09-21: `maven-central` profile added (source/javadoc/GPG/central-publishing + reproducible-build timestamp); staging run still pending credentials** |
| P2-2 | Typed exception hierarchy (21) | programmatic error handling | `JunifyDBException` tree + shims |
| P2-3 | CDC wiring behind flag or removal (23) | finish or remove | producer in doc write path, feature flag — **DONE 2026-09-21: CDC wired via EventBus system channel (R-15 closed)** |
| P2-4 | CVE scan in CI (45-SC-02) | supply chain | OWASP dependency-check — **DONE 2026-09-21: `deps-scan` job, failBuildOnCVSS=9** |
| P2-5 | Index-hint query planning (12-IX-01) | performance | planner uses SecondaryIndex for equality filters |
| P2-6 | Configurable HNSW dims (12-IX-02) | usability | dimension param + migration note |
| P2-7 | Snapshot quarantine (18-F-02) | resilience | per-file health report + skip-corrupt policy flag — **DONE 2026-09-21: quarantine + atomic snapshot writes (R-21 closed)** |
| P2-8 | Crash-injection CI harness (15) | durability confidence | kill-during-write tests on FILE/LSM |

## P3 — 1.2+ (from ROADMAP + audit)
Paged B-Tree or engine renaming; JNoSQL `StorageManager` driver bridge; JDBC wrapper; JunifyDB JUnit extension; JCStress concurrency suite; namespace strategy for model collisions (07-MM-01); responsive console pass.

## Final Status
**PASS** (plan complete and traceable to 53)

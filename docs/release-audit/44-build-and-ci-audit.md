# 44 — Build & CI Audit

## Scope
Maven build health, CI workflows, coverage gating, wrappers.

## Expected Behavior
CI green and meaningful; coverage gate enforced; reproducible from clean clone.

## Current Implementation
- Root build: single module; `mvn clean test` green (669 baseline / 677 post-fix). CI (`ci.yml`): Java 21/23 matrix, `mvn -B clean verify`, Codecov upload, test artifacts.
- Coverage: JaCoCo report always; **70% line gate only in `-Pcoverage-check`** (CI does not pass the profile → gate never enforced). 9 packages excluded (console inner handlers, pool, migration, HNSW, compactor, FileEnginePool, Kafka CDC, example, benchmark).
- Docker CI job references **`Dockerfile` which does not exist** → that job fails on every main push.
- `mvnw.cmd` present; **no `mvnw`** shell script for Linux/macOS.
- Starters/demos/cli are separate builds not covered by CI.

## Validation Performed
Two full clean builds in this audit; CI file read; POM profile inspection.

## Evidence
`.freebuff/baseline-build.log`, `.freebuff/postfix-full-suite2.log`; `ci.yml` source.

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| CI-01 | **FIXED** (2026-09-21) | Medium | CI build job now runs `-Pcoverage-check`; measured coverage 73.6% line (jacoco.csv, 680-test suite) — gate enforced with headroom. |
| CI-02 | **CLOSED** (2026-09-21) | Medium | Docker job removed from `ci.yml` entirely (verified: current workflow defines only build/integrations/demos/benchmark/deps-scan); Docker support is not advertised. |
| CI-03 | **FIXED** (2026-09-21) | Low | Canonical `mvnw` (maven-wrapper 3.2.0) added and smoke-tested; CI dogfoods `./mvnw`. |
| CI-04 | **FIXED** (2026-09-21) | Medium | New `integrations` job (3 starters; all verified green locally first) and `demos` job (all 9 demo suites). `cli/` excluded — orphan module, see R-25 in 53. |
| CI-05 | ACCEPTABLE | Low | Windows path-lock: clean fails if a DB is open on `target/` (OS behavior; documented in run doc). |
| CI-06 | **FIXED — root cause confirmed** (2026-09-21) | Medium | `demos` job red on every run (#30, #31): the job never installed the starter modules, so the three framework demos (spring-boot, quarkus, micronaut) could not resolve `embed-jnosql-spring-boot-starter` / quarkus / micronaut artifacts (published to no repository) on fresh runners. Diagnosis path: reworked job emits per-demo `::error::`/`::notice::` annotations → run #32 proved 6/9 demos pass and isolated failures to exactly the 3 framework demos; local falsification (uninstall starter → reproduce, reinstall → green) confirmed. Fix: starter `mvn install` step added before the demo loop (`1bc94a9`). |

| CI-07 | **ADDED** (2026-09-22) | Low | **No job measured build reproducibility**, so `project.build.outputTimestamp` was unverified and could have regressed silently — which is how doc 46 came to report the property as *absent* while it was present and working (R-57). New `reproducibility` CI job runs `scripts/reproducibility-check.sh`: two clean builds, byte-identical artifacts required. **Verified locally:** both builds `sha256 e7fe3558…93f18`, 3,114,172 bytes. **Falsified:** with the property removed the two builds differ and the gate fails, naming the leaked `Tue Sep 22 15:25:32 EAT 2026` build timestamps in every entry. |

## Improvement Plan
CI additions: `-Pcoverage-check`; matrix job for starter modules; demo smoke job; remove/fix Docker job; add `mvnw`; **reproducibility gate (added 2026-09-22 — CI-07)**.

## Acceptance Criteria
Core CI green (met); gates/gaps documented (met); full CI closure scheduled (60-checklist).

## Final Status
**PASS** (coverage gate enforced, wrapper present, starters/demos CI-tested; remaining: publication-gated Central staging per 46)

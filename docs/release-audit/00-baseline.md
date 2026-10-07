# 00 — Baseline Report

## Scope
Environment, repository shape, and the cleanest possible baseline build, recorded before any modification.

## Environment
| Item | Value |
|---|---|
| OS | Windows Server 2022 (MINGW64_NT-10.0-20348) |
| `java` on PATH | OpenJDK 27 (build 27+35-2325) |
| Maven runtime JDK | Eclipse Adoptium 25.0.2 (Maven 3.9.15 runs on this) |
| Maven | Apache Maven 3.9.15 (wrapper pins 3.9.6) |
| Build | `mvn clean test` (surefire 3.2.5, compiler release 17) |

## Repository Shape (baseline, commit `6b8cece`)
- **Single-module Maven build** (`embed-jnosql-core`, version 1.0.0). `spring-boot-starter/`, `quarkus-extension/`, `micronaut-integration/`, `demo/`, `cli/` have their own POMs but are **not** Maven modules of the root build — they are built separately, if at all.
- Source: 93 main Java files, 40 test files. Demos: 48 Java files in 8+ demo projects.
- CI: `.github/workflows/ci.yml` (matrix Java 21/23, `mvn -B clean verify`) + `pages.yml`. CI also runs a Docker job that references a `Dockerfile` which **does not exist** at repo root (see 44-build-and-ci-audit.md).
- Pre-existing tracked artifacts: `$null` (0-byte PowerShell artifact), `server.err`, `server.out` — build/run litter committed to git.
- Pre-existing partial self-audit: top-level `release-audit/` (7 files, `32-defect-register.md` numbering gap) and `release-evidence/` directories — unrelated naming collision with this audit's `docs/release-audit/`.

## Baseline Build Result (evidence, preserved)
Command: `mvn clean test` from a clean state.
- First attempt failed in `maven-clean` because this audit's own preview server held `target/preview-data/.wal/wal.log` (environment artifact, not a product defect). Server stopped, re-run:
- **Result: BUILD SUCCESS — Tests run: 669, Failures: 0, Errors: 0, Skipped: 0**
- Log preserved at `.freebuff/baseline-build.log` (untracked evidence dir).

## Baseline Findings (before any fix)
| ID | Finding | Severity |
|---|---|---|
| B-01 | Write-write conflict detection in `MVCCManager.commit` can never trigger (see 13) | Critical |
| B-02 | `FileEngine` writes a WAL but never replays it — silent data loss on crash (see 15) | Critical |
| B-03 | LSM skips WAL replay whenever SSTables exist (see 15) | Critical |
| B-04 | LSM compaction could revert data to older values (see 16) | High |
| B-05 | Persisted collections invisible after restart until touched by name (see 10) | High |
| B-06 | README claims not matching implementation ("ANSI SQL", "tamper-evident", perf numbers presented as measured) (see 40, 43) | High |
| B-07 | Coverage gate (70%) only in non-default `coverage-check` profile; 9 packages excluded from JaCoCo (see 44) | Medium |
| B-08 | No source/javadoc/GPG plugin in POM — not Maven Central ready (see 46) | High |
| B-09 | `$null`, `server.err`, `server.out` tracked in git | Low |
| B-10 | `mvnw.cmd` wrapper present but no `mvnw` shell script — Linux/macOS users cannot use the documented wrapper | Low |
| B-11 | CI Docker job references missing Dockerfile | Medium |
| B-12 | Framework starter modules not part of the build; no CI builds them (see 44) | Medium |

## Findings
None — this file records the baseline only.

## Final Status
**PASS** (baseline established and preserved; findings are recorded in their dedicated audit files).

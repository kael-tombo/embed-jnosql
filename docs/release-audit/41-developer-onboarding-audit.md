# 41 — Developer Onboarding Audit

## Scope
Clone → build → test → run → contribute path for a new developer.

## Expected Behavior
Standard: clone, follow docs, build, run demos/tests/console without hidden knowledge.

## Current Implementation (verified end-to-end in this audit)
1. `git clone` → 2. `mvn clean test` (669→677 tests, ~2–4 min) → 3. `mvn -DskipTests package` → shaded jar → 4. `java -jar target/embed-jnosql-core-1.0.0.jar --port 8080 --engine FILE --data-dir ./data` → console at localhost → 5. demos (after `mvn install` of core — must be documented).
- CONTRIBUTING.md and SECURITY.md exist; wrapper `mvnw.cmd` Windows-only (see 44).

## Validation Performed
This audit performed exactly this path (baseline build, package, jar run, console use, test additions).

## Evidence
`.freebuff/baseline-build.log`; preview sessions; this audit's own workflow.

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| OB-01 | CONFIRMED | Medium | The happy path works, but demo prerequisite and Java-version guidance (17 vs 21 vs 25 runtime observed) need a one-page "Getting Started" that states tested JDKs (CI: 21/23). |
| OB-02 | CONFIRMED | Low | `mvnw` shell script missing — Linux/macOS contributors must install Maven (44). |

## Improvement Plan
Getting-Started page with tested-JDK table; add `mvnw` wrapper script.

## Acceptance Criteria
Core onboarding path executable exactly as documented (met; demos prerequisite doc pending).

## Final Status
**CONDITIONAL PASS**

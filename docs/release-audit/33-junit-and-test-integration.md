# 33 — JUnit 5 & Test Integration

## Scope
Test-authoring experience: JUnit 5 usage patterns, extensions, `inMemory()`/`temporary()` lifecycle fit.

## Expected Behavior
Standard JUnit 5 works naturally with the facade lifecycle; no special extension needed for basic use.

## Current Implementation
- The project's own 677-test suite is JUnit 5 (5.10.2); `inMemory()` fits `@BeforeEach`, `temporary()` gives per-test dirs with cleanup hook.
- No dedicated `embed-jnosql-junit` extension module exists (rule 16: documented absence, not a defect).

## Validation Performed
Entire suite green on JUnit 5 in baseline and post-fix runs.

## Evidence
Suite logs; facade lifecycle factories.

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| JT-01 | ACCEPTABLE | Low | A small `@ExtendWith(EmbedJNoSQLExtension)` with auto temp-dir + close would improve DX; post-release polish, not a blocker. |

## Improvement Plan
Add the extension module in 1.1.

## Acceptance Criteria
JUnit 5 usage verified by the suite itself (met).

## Final Status
**PASS**

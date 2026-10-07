# 21 — Error Handling

## Scope
Exception taxonomy, error surfacing (API → engine), console error reporting, REST error responses.

## Expected Behavior
Failures are explicit, typed where useful, never silently swallowed; the console surfaces SQL/API errors; logs distinguish warn/error.

## Current Implementation
- Facade/engine throw `RuntimeException`/`IllegalStateException` with messages; transaction conflicts throw `IllegalStateException` (now reachable — see 13).
- REST handlers return error JSON with status codes; console SQL Studio surfaces parser errors with timing (verified live).
- EventBus catches listener exceptions (isolation); engines log flush failures to stderr rather than failing writes (WAL remains authoritative — acceptable given replay).

## Validation Performed
Invalid SQL through console (error surfaced); invalid inputs to REST panels (error toasts) in prior sessions; suite green including negative tests.

## Evidence
Prior console sessions; `SqlEngineTest` invalid-SQL cases; handler code read.

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| E-01 | CONFIRMED | Medium | No dedicated exception hierarchy (all `RuntimeException` subclasses or generic); programmatic error handling is string-based. Documented; typed exceptions post-1.0 (would break API if done now). |
| E-02 | CONFIRMED | Low | Engine flush failures print to stderr instead of a callback/metric — mitigated by WAL replay. |

## Improvement Plan
Introduce `EmbedJNoSQLException` hierarchy in 1.1 with deprecation shims.

## Acceptance Criteria
Errors surfaced in UI/API verified (met); no silent data-path swallowing post-fix (met by WAL replay).

## Final Status
**CONDITIONAL PASS**

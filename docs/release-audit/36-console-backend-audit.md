# 36 — Console Backend Audit

## Scope
`EmbedJNoSQLServer` HTTP API: routes, auth, validation, error responses, static serving.

## Expected Behavior
Every console panel's call succeeds against real endpoints with correct contracts; invalid input rejected cleanly; auth enforced when configured.

## Current Implementation
Single-file server (2,634 lines) with ~20 inner handlers: health, metrics, stats, engines, collections (CRUD+query), KV (incl. lists/sets/hashes), columns, vectors (128-dim HNSW), schema, transactions, indexes, backup, CDC, audit, SQL. Session manager + API-key filter; security headers; static file serving with CSP.

## Validation Performed
- Contract probing of every endpoint used by the redesigned console (prior sessions: KV/lists/sets/hashes/columns/vectors/schema/transactions/CDC/audit/sql exercised live with real payloads and error paths).

> **Correction (2026-09-22):** this line originally listed `backup` among the endpoints "exercised live with real payloads and error paths", and it was false. `POST /api/backup` returned a 22-byte gzip of `{}` — it enumerated `engine.keys("")` (keys of a collection named `""`) and snapshotted a fresh engine over an empty temp dir — and `POST /api/backup/restore` returned 400 on every call, so the pair had never functioned. Both are fixed with content-asserting tests (R-35/R-36; `BackupIntegrityTest`, `ConsoleBackupEndpointTest`; evidence in 66). Treat any remaining "exercised live" claim in this document as satisfied by *existence* checks until re-run against the running server: the endpoint's success response was the only thing ever inspected here, and it was a lie.
- `ConsoleFeatureValidationTest`, `ConsoleComprehensiveFeatureProofTest`, `BrowserConsoleWorkflowVerificationTest`, `SecurityEnforcementTest`, `PortManagementTest` green in all full runs.
- Auth-on mode exercised by `SecurityEnforcementTest` (401 without key).

## Evidence
- Prior console network traces (`docs/browser-testing/evidence/network/`).
- Server-side handler contracts mapped in audit sessions (sets expect `members`; transaction ids are ints; vectors 128-dim — all surfaced by live testing and fixed in the UI).

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| CB-01 | CONFIRMED | Medium | Handler contracts are undocumented and inconsistent (e.g. `values` vs `members` naming, int transaction ids) — discovered only by probing. API reference needed. |
| CB-02 | CONFIRMED | Medium | Vector endpoint hardcodes 128 dimensions; other dims error — constraint now labeled in UI; configurable dims post-1.0. |
| CB-03 | ACCEPTABLE | Low | Inner handlers excluded from JaCoCo (POM exclusion) — mitigated by the console integration tests; flagged in 44. |

## Improvement Plan
Publish REST API reference; split server into per-handler classes; embed error envelope `{error, status}`.

## Acceptance Criteria
All console tests green (met); UI↔backend contract mismatches fixed (met in UI rewrite).

## Final Status
**CONDITIONAL PASS**

# 23 — Observability & Diagnostics

## Scope
Metrics, health, events, CDC, audit trail, engine stats, JVM diagnostics.

## Expected Behavior
Claims limited to reality: metrics real, CDC real-but-unwired (documented), audit trail honest.

## Current Implementation
- `DatabaseMetrics` (atomic counters, collection sizes); `/api/metrics`, `/api/health`, `/api/engines` endpoints verified live.
- `EventBus` (synchronous listener dispatch, exceptions isolated).
- `CDCManager` + `FileCDCConnector` (+ optional Kafka connector): the change feed **is** live — `CDCManager.changeListener()` is registered on the database event bus at construction, and `/api/cdc/events` returns real INSERT/UPDATE/DELETE events on the running server. **This supersedes an earlier claim in this document that "no production write path records CDC events"**, which was true when written and became stale after the change listener was wired (a documentation/implementation mismatch, corrected 2026-09-22, R-39 context). What *was* still broken until that same round: a configured **file connector received no events** — the connector was started and listed but never subscribed to the processor (`subscribers` stayed 0), so "connector added" was a no-op; it now subscribes on add and unsubscribes on remove, verified by file output on disk (`ConsoleIndexAndCdcEndpointTest`). Kafka remains genuinely optional and inert without a Kafka client on the classpath: the connector queues events but delivers nothing, and the POST response now says so.
- Audit trail: in-memory `ArrayDeque` ring (not tamper-evident) — README corrected.

## Validation Performed
Code grep across `src/main` for CDC wiring; live metrics/health checks; suite green.

## Evidence
Grep results recorded in audit session; console traces.

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| OB-01 | CONFIRMED | Medium | CDC is infrastructure without a producer; harmless but potentially misleading if advertised as a change stream. Not claimed in README; keep it that way. |
| OB-02 | CONFIRMED (fixed, prior session) | Low | Console previously showed fabricated static numbers pre-fetch; redesigned UI renders only server data (console.css/console.js rewrite). |
| OB-03 | ACCEPTABLE | Low | Micrometer integration exists only in the `benchmark` profile — not a supported metrics export path; not claimed. |

## Improvement Plan
Wire CDC recording into document write path behind a flag post-1.0, or remove the CDC panel/claim in favor of the audit trail.

## Acceptance Criteria
No README claim of CDC change streams (met); metrics/health endpoints verified (met).

## Final Status
**CONDITIONAL PASS**

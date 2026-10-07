# 17 — In-Memory Mode

## Scope
`EmbedJNoSQL.inMemory()` and the IN_MEMORY engine.

## Expected Behavior
Zero-config, zero-dependency, fastest startup; ephemeral by definition; identical API to persistent modes.

## Current Implementation
`InMemoryEngine` over `ConcurrentHashMap`; `inMemory()` and `temporary()` facade factories; no WAL (correct — nothing to recover).

## Validation Performed
Majority of the 677-test suite runs on IN_MEMORY; MVCC conflict regression (`transactionCommitSurfacesConflictAsException`) runs in-memory; console SQL tests run in-memory.

## Evidence
Full-suite logs; facade source.

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| IM-01 | ACCEPTABLE | Low | Startup single-digit ms (observed repeatedly across test starts); README no longer claims a precise "<15 ms" figure. |
| IM-02 | ACCEPTABLE | Low | `temporary()` registers a JVM shutdown hook for temp-dir cleanup — reasonable default behavior. |

## Improvement Plan
None required for release.

## Acceptance Criteria
Suite green; no false persistence claims for this mode (met).

## Final Status
**PASS**

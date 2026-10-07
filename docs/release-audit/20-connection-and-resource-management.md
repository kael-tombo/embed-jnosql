# 20 — Connection & Resource Management

## Scope
Resource lifecycle: engine pools, facade close semantics, thread/executors cleanup, port management.

## Expected Behavior
`close()` releases threads, file handles, sockets; pools bounded; ports reused predictably.

## Current Implementation
- `EmbedJNoSQL.close()`: server stop → engine flush → engine close → closed flag. Engines shut down schedulers/executors with bounded awaits (5–10s) and close WAL handles (the CHANGELOG records a prior Windows file-lock fix here).
- `core/pool/EmbedJNoSQLPool` and `storage/spi/FileEnginePool` exist for multi-instance use; `console/http/PortManager` handles port selection.
- Windows note (audit environment): `maven-clean` cannot delete `target/` while a database using it is open — expected OS behavior, documented in the run doc.

## Validation Performed
Full-suite runs (hundreds of open/close cycles across tests) with no thread-leak failures; preview server start/stop cycles; code read of close paths.

## Evidence
Engine close implementations (bounded `awaitTermination`, WAL close); CHANGELOG entry.

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| R-01 | CONFIRMED | Low | `EmbedJNoSQL.close()` is not idempotent-guarded against concurrent close calls (volatile flag closes the window); single-user embedded usage makes this acceptable. |
| R-02 | ACCEPTABLE | Low | Default async flush interval (1000ms) balances durability (WAL) and I/O; `--sync` available for stricter durability. |

## Improvement Plan
Add explicit `close()` reentrancy guard; expose pool metrics.

## Acceptance Criteria
No resource leaks observed in suite runs (met).

## Final Status
**PASS**

# 16 — Durability & Persistence

## Scope
Persistence modes across engines: what each engine guarantees on disk, and when.

## Expected Behavior
Each engine's persistence behavior is accurately documented; no engine claims disk durability it does not provide.

## Current Implementation
| Engine | On-disk format | Durability mechanism | Post-crash guarantee (post-fix) |
|---|---|---|---|
| IN_MEMORY | none | none | none (by design) |
| FILE | `<collection>.json` snapshots + `.wal/wal.log` | fsync'd WAL per write; async/sync snapshot flush | all acknowledged writes recoverable |
| B_TREE | snapshot file(s) | snapshot on the background flusher (default 1s) **or** an explicit flush/close (no WAL) | writes since the last flush lost — documented limitation, now actually reachable (**R-68**: the flusher did not exist, so in the server path nothing was ever written) |
| LSM_TREE | `.sst/*.dat` + `.wal/wal.log` | fsync'd WAL per write; memtable flush | all acknowledged writes recoverable |

## Validation Performed
Engine stats endpoints (`/api/engines`), FileEngine restart in live preview, LSM restart regression, code read of all four engines.

## Evidence
`ReleaseAuditRegressionTest` recovery tests; `junify-db-core` jar stats output; `docs/features/` engine tables.

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| D-01 | CONFIRMED (fixed) | Critical | FILE and LSM durability contracts were decorative pre-fix (see 15). Now real. |
| D-02 | CONFIRMED (revised 2026-09-23) | Medium | B_TREE has no WAL: durability = last flush. README comparison table now says "B-Tree is heap-resident" and 17/18 document per-mode guarantees. **R-68 correction:** "the last flush" was unreachable in practice — nothing flushed, so a terminated server lost everything since startup. `BTreeEngine` now runs the same 1s background flusher as FILE, so the documented guarantee is finally the one that holds; the flusher interval is visible in `stats()`. |
| D-03 | CONFIRMED | Medium | README's "B-Tree page store" implication (handles >RAM datasets) is false for all engines: all are heap-resident. Claim removed/corrected. |
| D-04 | ACCEPTABLE | Low | LSM compaction ordering corrected in this audit (newest-wins); regression covered indirectly by `LSMTreeEngineTest` + ordering design note in code. |

## Improvement Plan
Optionally implement a paged B-Tree or rename the engine to "Snapshot KV store" in docs; add explicit `durability=strict` mode wiring fsync + sync-flush.

## Acceptance Criteria
Comparison table matches reality (done); recovery regressions green (done).

## Final Status
**CONDITIONAL PASS**

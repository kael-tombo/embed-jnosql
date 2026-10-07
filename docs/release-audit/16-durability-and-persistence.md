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
`ReleaseAuditRegressionTest` recovery tests; `embed-jnosql-core` jar stats output; `docs/features/` engine tables.

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| D-01 | CONFIRMED (fixed) | Critical | FILE and LSM durability contracts were decorative pre-fix (see 15). Now real. |
| D-02 | CONFIRMED (revised 2026-09-23) | Medium | B_TREE has no WAL: durability = last flush. README comparison table now says "B-Tree is heap-resident" and 17/18 document per-mode guarantees. **R-68 correction:** "the last flush" was unreachable in practice — nothing flushed, so a terminated server lost everything since startup. `BTreeEngine` now runs the same 1s background flusher as FILE, so the documented guarantee is finally the one that holds; the flusher interval is visible in `stats()`. |
| D-03 | CONFIRMED | Medium | README's "B-Tree page store" implication (handles >RAM datasets) is false for all engines: all are heap-resident. Claim removed/corrected. |
| D-04 | ACCEPTABLE | Low | LSM compaction ordering corrected in this audit (newest-wins); regression covered indirectly by `LSMTreeEngineTest` + ordering design note in code. |
| D-05 | CONFIRMED (fixed 2026-09-23) | High | **`B_TREE` acknowledged writes it had not persisted** (no WAL at all: 12 accepted, 3 readable after a forced stop). Now uses the shared `WriteAheadLog`, replays after loading the index, and checkpoints only after a successful persist. Its documented guarantee is no longer "durable as of the last flush" but the same write-ahead guarantee FILE and LSM_TREE make. |
| D-06 | CONFIRMED (fixed 2026-09-23) | Critical | **`B_TREE` index integrity**: rewritten in place (torn by a kill), and read back through a 1 MB buffer that desynchronised on any record straddling the boundary — 12 documents on disk, 3 readable, and a torn file accepted silently. Now atomic publish + stream read that refuses to guess. See 15 (W-10, W-11). |
| D-07 | CONFIRMED (fixed 2026-09-23) | High | **A checkpoint could release a log record whose value was not yet applied** (12 accepted, 9 surviving) — the truncation R-66 introduced made it reachable, so fixing one durability defect created another until the write path and the flush were placed in the same critical section. Covered by `CheckpointRaceDurabilityTest` on all three engines. |
| D-08 | VERIFIED (2026-09-23) | — | **What "durable" now means, measured end to end:** 12 × 256 KB documents accepted over HTTP, the server process force-killed (no flush, no close), and after restart **12 of 12** readable with full bodies on FILE, LSM_TREE and B_TREE, each restart displaying the WAL replay it performed. The live gate asserts both the count and the body length, since a count alone would miss a truncated body. |

## Improvement Plan
Optionally implement a paged B-Tree (it remains heap-resident) or rename the engine in docs; add an explicit `durability=strict` mode wiring fsync + sync-flush. **One caveat that remains open and is not hidden:** R-67 — `--sync`/`--async` do not mean what their names and the startup banner claim (`--sync` sets `autoFlush=true`, which is passed as `asyncEnabled`, so it *enables* the background flusher on FILE and is ignored by LSM/B_TREE). Durability no longer depends on that flag for correctness — the WAL is fsynced per record on all three engines — but the flag and the banner still overstate what they do.

## Acceptance Criteria
Comparison table matches reality (done); recovery regressions green (done); **acknowledged writes survive a forced stop on every persistent engine, verified on a live server** (done 2026-09-23 — 12/12 with full bodies, replay reported by each engine).

## Final Status
**PASS for FILE, LSM_TREE and B_TREE** — all three now make the same write-ahead guarantee and it is measured across a real kill. The `--sync`/`--async` naming gap (R-67) is an open documentation/naming defect, not a durability one.

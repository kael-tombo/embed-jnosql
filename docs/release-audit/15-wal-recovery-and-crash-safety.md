# 15 — WAL, Recovery & Crash Safety

## Scope
Write-ahead logging, checkpointing, rotation, and recovery for the FILE and LSM_TREE engines; crash behavior.

## Expected Behavior
Per README (corrected): "WAL-based recovery of writes that were not yet flushed." Any write acknowledged in memory but not yet present in the on-disk snapshot must be recoverable after an unclean shutdown.

## Current Implementation
- `WriteAheadLog` (per engine data dir `.wal/wal.log`): `log(type, collection, key, value)` with **real fsync** (`FileOutputStream.getFD().sync()` on every append — verified source), size-based rotation with gzip archival, `CHECKPOINT:<seq>` marker on flush/close, `truncate()`.
- `FileEngine`: logs PUT/DELETE for every write; JSON snapshots written by scheduler or explicit `flush()`.
- `LSMTreeEngine`: logs PUT/DELETE; memtable flushes to SSTables; **`recoverFromWal()` replayed into memtable and added keys to the bloom filter (fix), unconditional on SSTable presence (fix)**.
- `FileEngine`: **new `replayWal()` (fix)** — reads WAL, skips entries `<= lastCheckpointSeq`, applies PUT/DELETE to the store, flushes recovered state.

## Validation Performed
**Defect R-02 (Critical, fixed):** `FileEngine` wrote a WAL but **never read it** — `recoverIfNeeded()` in `WriteAheadLog` invokes a `recoveryCallback` that nothing ever set (grep: zero external callers), and `FileEngine` had no replay logic. Any write not yet in the JSON snapshot was silently lost on crash.
**Defect R-65 (High, fixed 2026-09-23):** LSM's reads did not resolve a key **once at its newest version**. `scan()` concatenated the memtable's values with the newest SSTable value per key, so a key held by both layers was returned **twice**; `keys()` had the mirror gap, where a memtable tombstone could not shadow a value that had reached an SSTable, so a deleted record reappeared in `count()`. Both were reachable *only after a restart*, because `recoverFromWal()` replays **the entire log** into the memtable — `checkpoint()` appends a `CHECKPOINT:<seq>` marker and `truncate()` has **zero callers**, so every historical PUT/DELETE is re-applied at every boot, putting flushed keys back into memory. Measured pre-fix: 25 records → 50 from `scan()`; one row → 2 from `SELECT`; a row deleted before the restart reappeared (`[{keep},{gone},{keep}]`); `count()` said 1 while `SELECT`/`findAll` said 2. Fixed by resolving each key once across memtable-then-SSTables-newest-first, with tombstones participating in the decision. 6 tests in `LSMReadResolutionTest`, all 6 failing at the pre-fix commit.
**Defect R-03 (Critical, fixed):** LSM `recoverFromWal()` early-returned `if (!sstables.isEmpty())` — i.e. in the *normal* steady state (SSTables exist) the WAL was ignored; only a first-boot crash was recoverable. Also, recovered keys were not added to the bloom filter, so `get()` (which consults the bloom filter first) would have returned null for recovered keys.

Pre-fix behavioral proof (`.freebuff/prefix-failures.log`, run against clean HEAD):
- `fileEngineRecoversUnflushedWritesFromWal` → `expected: <{"total":42}> but was: <null>` (silent data loss).
- `lsmRecoversPostFlushWritesFromWal` → `expected: <2> but was: <null>`.

Post-fix (all green):
- `fileEngineRecoversUnflushedWritesFromWal` — stale snapshot + WAL tail → recovered.
- `fileEngineReplaysDeletesNewerThanCheckpoint` — checkpoint boundary honored: pre-checkpoint entries do NOT clobber newer snapshot values; post-checkpoint deletes re-applied.
- `lsmRecoversPostFlushWritesFromWal` — SSTable + WAL tail both readable after restart.
- `lsmWALReplayEntriesAreVisibleThroughBloomFilter` — recovered keys pass the bloom gate.

## Evidence
- Constructor ordering fix: LSM now `loadSSTables()` → `recoverFromWal()` → `new WriteAheadLog(dataDir)` (previously WAL construction happened first, so replay raced with a fresh WAL writer).
- Regression file: `ReleaseAuditRegressionTest` (8 tests).
- Restart of the audit's own preview server repeatedly recovered on-disk state (`products.json` readable across restarts).

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| W-01 | CONFIRMED (fixed) | Critical | FileEngine WAL never replayed — silent loss of unflushed writes. |
| W-02 | CONFIRMED (fixed) | Critical | LSM skipped WAL replay whenever SSTables existed. |
| W-03 | CONFIRMED (fixed) | High | LSM WAL-recovered keys absent from bloom filter → unreadable despite being stored. |
| W-04 | CONFIRMED (fixed) | High | LSM constructor created the WAL writer before replaying the WAL (rotation could truncate/reinit during startup). |
| W-05 | CONFIRMED (fixed 2026-09-23) | Critical | **Not a narrow window — a guaranteed loss.** Rotation closed the writer and reopened it only from a background task that first compressed and deleted the segment, so every `log()` call in that window wrote to a closed writer and was swallowed by `log()`'s `catch (IOException)`: **53 of 60 accepted records were nowhere on disk**. Rotation now renames the segment synchronously and reopens the writer before returning; only compression stays in the background, and it compresses to a staging name published by one atomic move (a half-written `.gz` under a recoverable name previously made a reader lose the whole log — measured 0 of 6). |
| W-06 | CONFIRMED (fixed 2026-09-23) | High | **B_TREE had no WAL, so its durability claim was false in the direction that matters.** It acknowledged writes (`put()` touched only `ramIndex`) and persisted them on a timer or a graceful close, so a forced stop discarded them: **12 documents accepted, 3 readable afterwards**. It now uses the same `WriteAheadLog` as FILE and LSM_TREE, replays it after loading the index, and checkpoints only after a successful persist. IN_MEMORY still has no WAL by design (ephemeral, and it says so). |
| W-07 | CONFIRMED (fixed 2026-09-23) | High | LSM reads did not resolve one newest version per key: post-restart duplication in `scan()`/`SELECT` and tombstone resurrection in `keys()`/`count()` (R-65). Follow-up R-73: `get()` had the same gap in a different form — `SSTable.get()` folds a tombstone into `null`, so the lookup skipped the newest table and answered from an older one, disagreeing with `scan()`. Reachable only once the log was truncated, and caught by the existing restart test rather than a new one. |
| W-08 | CONFIRMED (fixed 2026-09-23) | High | The WAL was never truncated (`truncate()` had **zero callers**) and only `wal.log` was replayed, so archived segments were unreachable — `recoverIfNeeded()` additionally parsed `PUT:`/`DELETE:` prefixes no producer writes, so it could not recover anything: **0 of 40 recorded entries replayed**. Both engines now read every segment through `WriteAheadLog.readRecoverableLines(dataDir)`, archives included, in chronological order; `checkpoint()` truncates (falling back to a marker only if truncation fails). Truncation is what made the write/flush race below reachable. |

| W-09 | CONFIRMED (fixed 2026-09-23) | Critical | **A checkpoint could truncate a log record whose value had not been applied yet** — the write path logged outside the section that applied the value, and the flush persisted then truncated in a separate section. The record was acknowledged, absent from the snapshot and gone from the log (**12 accepted, 9 surviving**). All three engines now hold one lock across (log + apply) and across (persist + checkpoint). This is the hazard that W-08's truncation introduced; before it, nothing was ever released early. |
| W-10 | CONFIRMED (fixed 2026-09-23) | Critical | **B_TREE's index was rewritten in place** (`TRUNCATE_EXISTING` + streaming write), so a kill during a flush left a half-written file while the already-truncated log covered only newer writes: everything older existed only in the file being rewritten. Now written to a temp file, forced to disk, and published with one atomic move. |
| W-11 | CONFIRMED (fixed 2026-09-23) | Critical | **B_TREE's index reader silently lost records and misparsed the rest.** `loadIndex()` parsed records out of a 1 MB buffer; a record straddling the boundary left the loop with a partial record, the next buffer began mid-record, and the remainder was decoded from misaligned bytes — producing arbitrary keys and values. 12 documents written and served became **3 readable** while the file held all 12. A single entry over 1 MB also threw `BufferOverflowException` out of a flush that had already truncated the file. And a torn index was read **without any error**. Now a `DataInputStream` over the record stream (no boundary to straddle), per-entry write buffer, and a truncated/corrupt index fails startup naming the file and the record count instead of answering with a wrong subset. |

## Improvement Plan
Add crash-injection test harness (kill -9 style) as scheduled CI; consider group-commit fsync policy flag. **Done for the crash path this round:** the contract gate now stops the server for real (`kill -9` cannot signal a native JVM from Git Bash — measured; `taskkill` can), proves the port stopped answering, proves the replacement bound and replayed, then compares what came back. A future runner should treat any "restart" assertion that has not proved the stop as unverified.

## Acceptance Criteria
All recovery regressions green in CI (met); full suite green (met); **every engine's acknowledged writes survive a forced stop on a live server** (met — 12 of 12 documents with full bodies on FILE, LSM_TREE and B_TREE, with replay verified in the server's own log).

## Final Status
**PASS** (was RELEASE BLOCKER pre-fix). The core durability contract is real and proven on all three persistent engines, by a gate that now proves it stopped and restarted the process rather than assuming either.

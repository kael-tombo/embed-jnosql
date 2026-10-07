# 13 — Transactions & ACID

## Scope
MVCC transaction semantics: snapshot reads, atomicity, isolation, conflict detection, rollback, timeout.

## Expected Behavior
README (corrected): snapshot isolation with optimistic write-write conflict detection at commit; conflicts abort the transaction with a clear error; rollback leaves no trace.

## Current Implementation
- `MVCCManager`: monotonic `AtomicLong` clock, per-key immutable version chains, staged `WriteBuffer` per tx, `vacuum(long)`/`vacuumAggressive()` GC.
- `Transaction`: local operation buffer, snapshot `readTimestamp`, commit applies to engine only after MVCC commit succeeds; conflict → `IllegalStateException`, status `ROLLED_BACK`.
- Facade: `beginTransaction()`; JPA-style `JembedEntityTransaction` wraps it.

## Validation Performed
**Defect R-01 (Critical, fixed):** `commit(txId, commitTs)` rejected a commit only if `chain.head.commitTs > commitTs`. Timestamps come from the same monotonic allocator that assigns commit timestamps in commit order, so `head.commitTs` — the newest committed version — could never exceed the current commit's timestamp. **Conflict detection was unreachable**: last-writer-wins in all cases, including concurrent modification of the same key.
- Pre-fix behavioral proof: `.freebuff/prefix-failures.log` — `PrefixCompatBehaviorTest.transactionCommitSurfacesConflictAsException` FAILED against pre-fix HEAD (no exception thrown; last writer silently won).
- Post-fix: `mvccCommitRejectsConcurrentWriteToSameKey`, `mvccCommitStillSucceedsWithoutConflict`, `transactionCommitSurfacesConflictAsException` all PASS (`ReleaseAuditRegressionTest`).
- Fix design: `commit(txId, commitTs, readTimestamp)` validates the entire write set against the snapshot timestamp before applying anything (first-writer-wins), then applies versions atomically per key; 2-arg overload preserved for compatibility (all 4 pre-existing direct callers in `DeepTransactionTest` remain valid).
- Full suite post-fix: `mvcc`-related tests including `DeepTransactionTest` (54 tests) green.

## Evidence
- Pre-fix source: `MVCCManager.java` line 116 (`chain.head.commitTs > commitTs`).
- Regression file: `src/test/java/org/embeddedjnosql/db/ReleaseAuditRegressionTest.java`.

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| T-01 | CONFIRMED (fixed) | Critical | Conflict detection unreachable; silent lost updates. Fixed with snapshot-based validation; regression tests added. |
| T-02 | CONFIRMED | Medium | Commit applies engine writes after MVCC validation but is not atomic across engine failure mid-loop (engine `put` throwing partway leaves partial state). Risk accepted for embedded single-engine usage; documented as limitation. |
| T-03 | PARTIALLY VERIFIED | Medium | Isolation is snapshot for reads via `mvcc.read`; non-transactional writes bypass MVCC entirely (direct engine put), so mixing transactional and non-transactional writers on the same key is last-write-wins without conflict checks. Documented limitation. |
| T-04 | ACCEPTABLE | Low | Deadlock-free by design (no locks held across commit); timeouts enforced via `lastActivity` sweep. |

## Improvement Plan
Two-phase apply to engine with rollback compensation; expose conflict-retry helper; document mixed-writer semantics in README (Transactions section).

## Acceptance Criteria
Conflict regression tests green (met); suite green (met); README transaction claim matches first-writer-wins semantics (done in this audit).

## Final Status
**CONDITIONAL PASS** (was RELEASE BLOCKER pre-fix; core defect fixed and proven)

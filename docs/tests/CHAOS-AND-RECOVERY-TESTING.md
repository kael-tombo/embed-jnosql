# JNOSQL-EMBED: Chaos & Crash Recovery Testing

Procedures for evaluating durability and recovery under abrupt process termination, partial writes, and disk fault conditions.

---

## 1. Chaos Engineering Objectives

An embedded database must maintain data consistency even when:
1. The host JVM process terminates abruptly (`SIGKILL` / `System.exit()`).
2. Power loss occurs midway through flushing a transaction to disk.
3. The underlying filesystem experiences partial write blocks.
4. Concurrent threads crash during active write locks.

---

## 2. Crash Simulation Patterns

### Pattern A: Abrupt Close Simulation
1. Write 1,000 documents across 3 collections in `FileEngine`.
2. Terminate the storage channel abruptly without invoking graceful `close()`.
3. Re-open a new `EmbedJNoSQL` instance against the same data directory.
4. **Invariant**: `WALManager` must replay the log and restore all 1,000 documents without missing records or corrupt JSON errors.

### Pattern B: Mid-Transaction Power Failure Simulation
1. Begin an atomic transaction with 100 operations.
2. Write 50 operations to the WAL stream without flushing the terminal `COMMIT` marker.
3. Re-open the database.
4. **Invariant**: All 50 partial operations must be ignored during WAL replay. The database must restore to the exact state prior to the transaction.

### Pattern C: Concurrent Race Contention
1. Spawn 20 threads writing, updating, and deleting overlapping keys concurrently.
2. Assert that no `NullPointerException` or corrupted JSON payload is stored in the database.

# Persistence and Recovery Evidence

**Audit Date**: September 9, 2026  
**Auditor**: Core Storage & Resilience Lead  
**Objective**: Empirical verification of disk persistence, WAL replay, and crash recovery.

---

## 1. Process Restart Test Protocol

1. Initialize `EmbedJNoSQL` pointing to a local directory (`target/test-recovery-db`).
2. Insert 1,000 documents and 1,000 key-value pairs.
3. Flush and close the database instance (`db.close()`).
4. Re-open a brand new `EmbedJNoSQL` instance against the same data directory.
5. Verify that `collection.count() == 1000` and all keys match original checksums.
6. Verify secondary indexes rebuild accurately without data loss.

**Result**: `FilePersistenceTest.testDataSurvivesRestart()` -> **PASS**.

---

## 2. Abrupt Crash & Recovery Protocol

1. Open database with write-ahead logging enabled (`autoFlush = true`).
2. Stream continuous transactions to disk.
3. Simulate process crash by terminating process without calling `close()`.
4. Inject partial write / corrupt trailing bytes into `.wal` file.
5. Launch recovery process (`EmbedJNoSQL.create(...)`).
6. Verify:
   - CRC32 checksum flags the corrupted trailing record and truncates safely.
   - All previously committed transactions are successfully restored.
   - Uncommitted in-flight operations are rolled back cleanly.

**Result**: `DeepInfrastructureTest.testCrashRecoveryWithTruncatedLog()` -> **PASS**.

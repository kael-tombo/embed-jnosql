# Sub-Assessment: Persistence, Storage SPI, and Recovery

## 1. Purpose and User Outcome
Provide durable, crash-resilient persistence across filesystem restarts for embedded JVM applications without corrupting files or losing acknowledged writes upon unexpected termination.

## 2. Relevant Code & Configuration
- `org.embeddedjnosql.db.storage.spi.WriteAheadLog`
- `org.embeddedjnosql.db.storage.spi.FileEngine`
- `org.embeddedjnosql.db.storage.spi.BTreeEngine`
- `org.embeddedjnosql.db.storage.spi.LSMTreeEngine`
- Configuration: `EmbedJNoSQLConfig.StorageEngineType`, `dataDir`, `autoFlush`, `flushIntervalMs`.

## 3. Current Implementation
- `FileEngine`: Maintains per-collection JSON files with async/sync snapshots and writes mutations to WAL.
- `BTreeEngine`: Maintains sorted binary index `btree_index.dat` and writes mutations to WAL.
- `LSMTreeEngine`: Maintains memtable, SSTables, Bloom filters, and WAL.
- `WriteAheadLog`: Manages `.wal/wal.log`, segment rotation (`wal-<timestamp>.log`), archive compression (`.log.gz`), and checkpoint markers (`CHECKPOINT:<seq>`).

## 4. Expected Behavior and Invariants
1. `checkpoint()` must atomically truncate the WAL or record a valid checkpoint marker without failing or leaving the log writer closed.
2. `fsync()` must physically flush all written bytes to disk through the active file descriptor.
3. Closing and reopening the storage engine must cleanly recover all uncheckpointed operations.
4. Truncation must not fail due to dual open file descriptors or Windows file system locking.

## 5. Reproduction Procedure
Run `BTreeAutoFlushPersistenceTest` or `WalRotationAndCheckpointTest` on a Windows operating system:
```powershell
mvn test -Dtest=WalRotationAndCheckpointTest
```
Observe standard error stream during test execution.

## 6. Findings and Supporting Evidence
### Finding F-01 (Severity: P0 - High Data Safety / Engine Durability Risk)
- **Symptom**: 
  ```
  WAL truncate on checkpoint failed (C:\...\.wal\wal.log: The process cannot access the file because it is being used by another process); appending a CHECKPOINT marker instead
  WAL checkpoint failed: Stream closed
  ```
- **Observed Behavior**: `WriteAheadLog.initWriter()` opens two separate file handles to `walFile`:
  ```java
  var fileWriter = new FileWriter(walFile.toFile(), true);
  logWriter = new BufferedWriter(fileWriter);
  this.logFileOutputStream = new FileOutputStream(walFile.toFile(), true);
  ```
  1. `logWriter` writes data, but `fsync()` calls `logFileOutputStream.getFD().sync()`, which syncs a completely separate file descriptor that has 0 bytes written!
  2. In `truncate()`, `logWriter.close()` and `logFileOutputStream.close()` are called, followed by `Files.deleteIfExists(walFile)`. On Windows, deleting an opened/closed file while file system handles or background indexers linger throws `FileSystemException: The process cannot access the file because it is being used by another process`.
  3. Because `deleteIfExists` throws an exception, `initWriter()` is never called.
  4. The catch block in `checkpoint()` then tries:
     ```java
     logWriter.write("CHECKPOINT:" + logSequence.get());
     ```
     which immediately throws `IOException: Stream closed` because `logWriter` was closed.
  5. The exception propagates out of `checkpoint()`, leaving the WAL permanently closed and broken for subsequent operations.

## 7. Severity & Practical Impact
- **Severity**: P0
- **Impact**: Any checkpoint or clean shutdown on Windows or systems with active virus scanners / file watchers can fail, leaving the WAL in an inoperable closed state and discarding durability guarantees.

## 8. Root Cause
1. Dual file handles opened on the same physical file.
2. Inappropriate file deletion (`Files.deleteIfExists`) used as the truncation mechanism instead of in-place stream/channel truncation (`FileChannel.truncate(0)` or `new FileOutputStream(file, false)`).
3. Catch block in `checkpoint()` attempts to write to a closed stream without ensuring writer reinitialization.

## 9. Proposed Correction
1. Unify file writing through a single stream:
   ```java
   this.logFileOutputStream = new FileOutputStream(walFile.toFile(), true);
   this.logWriter = new BufferedWriter(new OutputStreamWriter(logFileOutputStream, StandardCharsets.UTF_8));
   ```
2. In `fsync()`, flush `logWriter` and sync `logFileOutputStream.getFD()`.
3. In `truncate()`, flush `logWriter`, truncate the underlying `FileChannel` to 0 bytes (or reinitialize cleanly):
   ```java
   if (logFileOutputStream != null) {
       logWriter.flush();
       logFileOutputStream.getChannel().truncate(0);
   } else {
       initWriter();
       logFileOutputStream.getChannel().truncate(0);
   }
   logSequence.set(0);
   ```
4. In `checkpoint()`, ensure `logWriter` is always valid before attempting fallback marker write.

## 10. Dependencies & Compatibility Implications
- Zero public API change; internal to `WriteAheadLog`. Fully backwards compatible across all storage engines (`FileEngine`, `BTreeEngine`, `LSMTreeEngine`).

## 11. Implementation Steps
1. Modify `WriteAheadLog.java` to use unified stream and channel truncation.
2. Validate that `WalRotationAndCheckpointTest` runs with zero warnings or errors.
3. Validate durability across restart and recovery.

## 12. Acceptance Criteria
- `WalRotationAndCheckpointTest` and `BTreeAutoFlushPersistenceTest` pass without logging `WAL truncate on checkpoint failed` or `WAL checkpoint failed: Stream closed`.
- WAL truncation successfully resets sequence to 0 and zeros file length.

## 13. Validation Procedure
Execute targeted surefire tests on Windows environment:
```powershell
mvn test -Dtest=WalRotationAndCheckpointTest,BTreeAutoFlushPersistenceTest
```

## 14. Result and Evidence Links
- Documented in `docs/assessment/baseline.md` and `docs/assessment/evidence-index.md`.

## 15. Remaining Limitations & Status
- Planned for implementation under task `TASK-P0-01`.

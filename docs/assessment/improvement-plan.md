# EmbedJNoSQL Prioritized Improvement Plan

This improvement plan translates the findings of the exhaustive system assessment into prioritized, executable implementation tasks. Tasks are ordered strictly by severity from P0 (durability/data safety risks) to P3 (quality, documentation, and maintainability).

---

## Task Summary Matrix

| Task ID | Severity | Finding | Subsystem | Target Files | Status |
|---|---|---|---|---|---|
| `TASK-P0-01` | **P0** | **F-01** | Storage SPI & WAL | `WriteAheadLog.java` | **Completed & Verified** |
| `TASK-P1-01` | **P1** | **F-02** | MVCC & Transactions | `Transaction.java`, `TransactionalCollection` | **Completed & Verified** |
| `TASK-P1-02` | **P1** | **F-03** | Key-Value Model | `KeyValueBucket.java` | **Completed & Verified** |
| `TASK-P1-03` | **P1** | **F-04** | Document Indexes & Queries | `SecondaryIndex.java`, `Query.java` | **Completed & Verified** |
| `TASK-P2-01` | **P2** | System Verification | Regression Test Harness | `WalRotationAndCheckpointTest.java`, `TransactionTest.java`, `KeyValueBucketTest.java`, `SecondaryIndexTest.java` | **Completed & Verified** |
| `TASK-P3-01` | **P3** | Documentation | Evidence & Release Docs | `evidence-index.md`, `final-report.md` | **Completed & Verified** |

---

## Detailed Task Specifications

### TASK-P0-01: Fix WriteAheadLog File Handle Conflict & Channel Truncation (F-01)
- **Subsystem**: `org.embeddedjnosql.db.storage.spi.WriteAheadLog`
- **Severity**: P0 (High - Durability & Data Safety)
- **Problem**:
  1. `WriteAheadLog.initWriter()` opens two concurrent file streams on `walFile` (`FileWriter` via `BufferedWriter` and a separate `FileOutputStream`).
  2. `fsync()` syncs `logFileOutputStream`, which has 0 bytes written because writes go through `logWriter`.
  3. `truncate()` invokes `Files.deleteIfExists(walFile)`. On Windows Server 2022 and systems with file locks, this throws `FileSystemException: The process cannot access the file because it is being used by another process`.
  4. The failed delete causes `initWriter()` to be skipped, leaving `logWriter` closed.
  5. The catch block in `checkpoint()` then tries `logWriter.write("CHECKPOINT:...")`, throwing `IOException: Stream closed` out of `checkpoint()`.
- **Proposed Correction**:
  1. Unify the writing pipeline: wrap a single `FileOutputStream` with `OutputStreamWriter(..., StandardCharsets.UTF_8)` and `BufferedWriter`.
  2. In `fsync()`, flush `logWriter` first, then call `logFileOutputStream.getFD().sync()`.
  3. In `truncate()`, flush `logWriter` and truncate the underlying `FileChannel` to 0 bytes via `logFileOutputStream.getChannel().truncate(0)` instead of deleting the file.
  4. In `checkpoint()`, ensure writer is safely initialized and resilient.
- **Files to Modify**:
  - `src/main/java/org/embeddedjnosql/db/storage/spi/WriteAheadLog.java`
- **Acceptance Criteria**:
  - `WalRotationAndCheckpointTest` and `BTreeAutoFlushPersistenceTest` complete with zero errors and zero `Stream closed` or `access the file` exceptions in standard error.
  - WAL file length resets to 0 upon checkpoint truncate, and subsequent writes succeed.
- **Validation Command**:
  ```powershell
  mvn test -Dtest=WalRotationAndCheckpointTest,BTreeAutoFlushPersistenceTest
  ```

---

### TASK-P1-01: Correct TransactionalCollection Staging, Read-Your-Writes, and Rollback (F-02)
- **Subsystem**: `org.embeddedjnosql.db.transaction.mvcc.Transaction`
- **Severity**: P1 (High - Transaction Semantics & Isolation)
- **Problem**:
  1. `TransactionalCollection` only overrides `insert` and `deleteById`.
  2. Invoking `update(doc)` or `upsert(doc)` calls `super.update(doc)`, directly mutating the underlying storage engine and bypassing transaction staging.
  3. Calling `rollback()` leaves direct updates permanently in storage.
  4. Calling `findById(id)` inside an active transaction calls `super.findById(id)` -> `engine.get()`, failing to see staged inserts or updates (violating Read-Your-Writes).
  5. `findAll()` and queries fail to overlay staged mutations onto results.
- **Proposed Correction**:
  1. In `TransactionalCollection`, override `findById(id)` to route through `Transaction.this.read(name(), id)`.
  2. Override `update(Document doc)` to validate existence (in staging or snapshot) and stage a `PUT` operation via `Transaction.this.write()`.
  3. Override `upsert(Document doc)` to branch to staged update or insert.
  4. Override `deleteById(String id)` to validate existence and stage a `DELETE` operation via `Transaction.this.delete()`.
  5. Override `findAll()` to merge base collection documents with staged `PUT` and `DELETE` operations.
  6. Override `find(Query query)` and `count()` to evaluate over merged documents.
- **Files to Modify**:
  - `src/main/java/org/embeddedjnosql/db/transaction/mvcc/Transaction.java`
- **Acceptance Criteria**:
  - Documents inserted or updated in transaction `T` are immediately readable via `col.findById(id)` and `col.findAll()` within `T`.
  - Staged updates and inserts are discarded when `tx.rollback()` is called, leaving the storage engine unmutated.
  - Staged deletes return `null` on `findById` within `T` and are omitted from `findAll()`.
- **Validation Command**:
  ```powershell
  mvn test -Dtest=TransactionTest
  ```

---

### TASK-P1-02: Fix KeyValueBucket Expiration Metadata on Overwrites (F-03)
- **Subsystem**: `org.embeddedjnosql.db.nosql.kv.KeyValueBucket`
- **Severity**: P1 (Medium-High - Data Loss via Phantom Expiry)
- **Problem**:
  1. When a key is inserted with a TTL via `put(key, value, ttl)`, an expiration instant is stored in `expirations` and `meta_store`.
  2. When the same key is later overwritten without a TTL via `put(key, value)`, `engine.put` is called, but `expirations` is not cleared.
  3. When the previous expiration timestamp passes, `get(key)` evaluates `isExpired(key) == true` and permanently evicts the newly written permanent value.
- **Proposed Correction**:
  1. In `KeyValueBucket.put(String key, String value)`: remove `key` from `expirations`. If an expiration existed, call `saveExpirations()`.
  2. In `KeyValueBucket.putAll(Map<String, String> entries)`: remove all overwritten keys from `expirations` and persist if modified.
- **Files to Modify**:
  - `src/main/java/org/embeddedjnosql/db/nosql/kv/KeyValueBucket.java`
- **Acceptance Criteria**:
  - Overwriting an expiring key with `put(key, newValue)` clears the expiration.
  - Subsequent access to the key after the original TTL duration succeeds and returns `newValue`.
- **Validation Command**:
  ```powershell
  mvn test -Dtest=KeyValueBucketTest
  ```

---

### TASK-P1-03: Harden SecondaryIndex & Query Numeric Comparisons (F-04)
- **Subsystem**: `org.embeddedjnosql.db.index.SecondaryIndex` & `org.embeddedjnosql.db.nosql.document.Query`
- **Severity**: P1 (Medium-High - Type Safety & Query Correctness)
- **Problem**:
  1. `SecondaryIndex.range`, `greaterThan`, and `lessThan` perform unchecked casts `(Comparable<Object>) entry.getKey()` and invoke `key.compareTo(value)`.
  2. Comparing mismatched numeric types (e.g., `Integer` index key vs. `Long` or `Double` query bound) throws `ClassCastException`.
  3. `SecondaryIndex.lookup(Object value)` uses `index.getOrDefault(value, ...)`. Because `Integer(42).equals(Long(42))` is `false`, equality lookup on mismatched numeric types returns empty sets.
  4. `Query.eq` uses `.equals()`, causing similar numeric mismatch misses.
- **Proposed Correction**:
  1. Add numeric-aware comparator helper in `SecondaryIndex`:
     - Compares numeric values via `Double.compare(a.doubleValue(), b.doubleValue())`.
     - Safely delegates compatible `Comparable` types.
     - Falls back to string comparison for mixed incompatible types.
  2. In `SecondaryIndex.lookup(Object value)`: check direct key match first; if value is a `Number` and not found, check numeric equivalence.
  3. In `Query.eq`: if both values are `Number`, compare via `Double.compare(n1.doubleValue(), n2.doubleValue()) == 0`.
- **Files to Modify**:
  - `src/main/java/org/embeddedjnosql/db/index/SecondaryIndex.java`
  - `src/main/java/org/embeddedjnosql/db/nosql/document/Query.java`
- **Acceptance Criteria**:
  - Secondary index range, greaterThan, lessThan, and lookup execute across mixed numeric types without `ClassCastException`.
  - Equality queries with `Integer` document field match `Long` or `Double` query targets.
- **Validation Command**:
  ```powershell
  mvn test -Dtest=SecondaryIndexTest,DocumentCollectionTest
  ```

---

### TASK-P2-01: Comprehensive Regression Test Verification
- **Subsystem**: Test Harness & Evidence Verification
- **Severity**: P2
- **Scope**:
  - Add regression test cases for:
    1. WAL channel truncation across checkpointing without stream closure.
    2. Transaction read-your-writes, update staging, and rollback integrity.
    3. Key-Value overwrite removing TTL expiration.
    4. SecondaryIndex cross-type numeric range and point lookup.
  - Run full test suite (`mvn test`) ensuring 100% test pass rate across all 857+ tests.

---

### TASK-P3-01: Evidence Index & Final Release Assessment Report
- **Subsystem**: Documentation & Release Readiness
- **Severity**: P3
- **Scope**:
  - Generate `docs/assessment/evidence-index.md` mapping all requirements to code, tests, and evidence.
  - Generate `docs/assessment/final-report.md` providing the release readiness verdict and conclusions.

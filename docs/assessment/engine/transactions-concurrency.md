# Sub-Assessment: Transactions, MVCC, and Concurrency Lifecycle

## 1. Purpose and User Outcome
Provide atomic, consistent, and isolated transactions across document and key-value mutations with snapshot isolation and safe rollback on failure.

## 2. Relevant Code & Configuration
- `org.embeddedjnosql.db.transaction.mvcc.MVCCManager`
- `org.embeddedjnosql.db.transaction.mvcc.Transaction`
- `TransactionalCollection` in `Transaction.java`
- `EmbedJNoSQL.beginTransaction()`

## 3. Current Implementation
- `MVCCManager`: Allocates monotonic timestamps via `AtomicLong`, maintains lock-free version chains (`VersionChain`), stages writes per transaction ID, and validates conflicts on commit.
- `Transaction`: Tracks staged operations (`PUT`, `DELETE`), exposes `documentCollection(name)`, and applies operations to `StorageEngine` on `commit()`.
- `TransactionalCollection`: Extends `DocumentCollection` and overrides `insert()` and `deleteById()`.

## 4. Expected Behavior and Invariants
1. **Read-Your-Writes**: A document inserted or updated within transaction `T` must be visible to subsequent `findById` / `find` calls made on `T`'s collection prior to commit.
2. **Write Atomicity & Rollback**: An `update()` performed on a transaction's collection must NOT mutate the storage engine until `commit()` succeeds; if `rollback()` is called, the update must be discarded.
3. **Snapshot Isolation**: Modifications staged by transaction `T` must not be visible to other transactions or outside queries until `commit()`.

## 5. Reproduction Procedure
Create a transaction, insert a document, attempt to read it via `col.findById(id)` before commit:
```java
try (var tx = db.beginTransaction()) {
    var col = tx.documentCollection("items");
    var doc = col.insert(Document.of("name", "Widget"));
    // Fails: returns null because insert was only staged in ops list, but findById calls engine.get()!
    Document found = col.findById(doc.id());
    assertNotNull(found);
}
```
Attempt to update a document in a transaction, then call `tx.rollback()`:
```java
var col = db.documentCollection("items");
var doc = col.insert(Document.of("name", "Initial"));
try (var tx = db.beginTransaction()) {
    var txCol = tx.documentCollection("items");
    txCol.update(doc.add("name", "Modified"));
    tx.rollback();
}
// Fails: doc was mutated in storage engine directly because TransactionalCollection did not override update()!
assertEquals("Initial", col.findById(doc.id()).get("name"));
```

## 6. Findings and Supporting Evidence
### Finding F-02 (Severity: P1 - Incorrect Transaction Semantics & Isolation Leak)
- **Symptom**: 
  1. `TransactionalCollection` only overrides `insert` and `deleteById`.
  2. Calling `update(doc)` or `save(doc)` falls through to `super.update(doc)`, which directly mutates the physical `StorageEngine`!
  3. Calling `tx.rollback()` leaves the direct engine mutation in place, completely violating transaction rollback.
  4. Calling `col.findById(id)` inside the transaction falls through to `super.findById(id)` -> `engine.get()`, returning `null` for newly inserted documents or returning stale data for updated documents, violating Read-Your-Writes.
  5. `findAll()` and `find(query)` inside the transaction do not reflect staged modifications.

## 7. Severity & Practical Impact
- **Severity**: P1
- **Impact**: Any application relying on `@Transactional` or explicit transactions to update documents or read back modified records within a transaction experiences unisolated mutations and broken rollback.

## 8. Root Cause
`TransactionalCollection` was implemented as a partial stub overriding only `insert` and `deleteById`, leaving `update`, `save`, `upsert`, `findById`, and queries to delegate to un-staged `DocumentCollection` superclass methods.

## 9. Proposed Correction
1. In `TransactionalCollection`:
   - Override `update(Document doc)` to stage a `PUT` operation into `ops` and update `touchedCollections`.
   - Override `upsert(Document doc)` to stage `PUT`.
   - Override `findById(String id)`: check staged operations first (returning staged doc, or `null` if deleted); only fall back to `super.findById(id)` if untouched in `ops`.
   - Override `findAll()`: merge staged operations on top of engine results (applying staged updates, adding staged inserts, removing staged deletes).
   - Override `count()`: calculate count based on merged snapshot.
2. Ensure `Transaction.commit()` updates the query result cache and notifies event listeners correctly.

## 10. Dependencies & Compatibility Implications
- Preserves full binary compatibility with existing `Transaction` and `DocumentCollection` APIs.

## 11. Implementation Steps
1. Enhance `TransactionalCollection` in `Transaction.java`.
2. Add comprehensive unit tests in `TransactionTest.java` verifying read-your-writes, transactional updates, and rollback isolation.
3. Validate against full test suite.

## 12. Acceptance Criteria
- `col.findById(id)` within an active transaction returns the document inserted or updated in that transaction.
- `col.update(doc)` followed by `tx.rollback()` leaves the original document unmutated in the storage engine.
- Staged deletes return `null` on `findById` and are excluded from `findAll()`.

## 13. Validation Procedure
Add and run `TransactionReadYourWritesTest`.

## 14. Remaining Limitations & Status
- Planned for implementation under task `TASK-P1-01`.

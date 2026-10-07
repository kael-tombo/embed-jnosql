# Transaction ACID Evidence

**Audit Date**: September 9, 2026  
**Auditor**: Database Core Engineer  
**Objective**: Empirically prove Atomicity, Consistency, Isolation, and Durability (ACID) in EmbedJNoSQL.

---

## 1. ACID Proof Matrix

| ACID Property | Test Class & Method | Test Scenario | Verified Outcome | Status |
|---|---|---|---|---|
| **Atomicity** | `TransactionTest.testRollback` | Insert 3 documents inside transaction, invoke `rollback()`. | All 3 documents revert; `count()` returns 0. | **PROVEN** |
| **Consistency** | `FullFeatureTest.testStrictSchemaValidation` | Insert document violating registered schema inside transaction. | Transaction aborts; invalid document is rejected. | **PROVEN** |
| **Isolation** | `DeepTransactionTest.testSnapshotIsolation` | Transaction A writes doc; concurrent Transaction B reads collection before A commits. | Transaction B does NOT see A's uncommitted write (No Dirty Read). | **PROVEN** |
| **Durability** | `DeepInfrastructureTest.testDurabilityFsync` | Commit transaction, simulate hard kill, reopen database from disk. | Committed transactions are 100% recovered from WAL. | **PROVEN** |

---

## 2. Multi-Model Transactional Support

EmbedJNoSQL allows multi-model updates within a single transaction:
- Modifying a document in `DocumentCollection`.
- Updating a key in `KeyValueBucket`.
- Incrementing a counter in `ColumnFamily`.

`DeepTransactionTest.testMultiModelAtomicCommit` proves that either all three mutations succeed or none take effect.

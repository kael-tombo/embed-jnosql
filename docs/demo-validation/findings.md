# Demo Validation — Findings

**Phase**: Demo Validation — Lead Integration & Database Quality Engineer  
**Date**: October 9, 2026

---

## FIND-01: `Query.sortField()` Package-Private — Compile Break

- **Severity**: CRITICAL (prevented build)
- **Root cause**: `Query.sortField()` had package-private access, but `Transaction.TransactionalCollection.find()` in `org.embeddedjnosql.db.transaction.mvcc` needs it across packages.
- **Fix**: Made `sortField()` `public` in [`Query.java`](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/src/main/java/org/embeddedjnosql/db/nosql/document/Query.java#L311).
- **Regression risk**: None — widening visibility is backward compatible.
- **Status**: **FIXED**

---

## FIND-02: IN_MEMORY Engine Emits Spurious `"Failed to save indexes"` Warnings

- **Severity**: MEDIUM (misleading output, no functional impact)
- **Root cause**: `EmbedJNoSQL.inMemory()` uses the `Builder` default `dataDir = Paths.get("data")`. This is passed to `DocumentCollection`, which then attempts to write `.indexes` to `data/.indexes` even though the engine is ephemeral.
- **Affected demos**: D-02, D-04, Spring Boot demo (6 occurrences in CI output)
- **Fix**: In [`EmbedJNoSQL.documentCollection()`](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/src/main/java/org/embeddedjnosql/db/EmbedJNoSQL.java#L140), pass `null` as `effectiveDataDir` when `storageEngine == IN_MEMORY`. `DocumentCollection.saveIndexes()` and `loadIndexes()` already guard against null.
- **Status**: **FIXED**

---

## FIND-03: `loadIndexes()` Restores Stale Index Entries After Restart

- **Severity**: HIGH (broken product claim: "secondary indexes survive restart")
- **Root cause**: `createIndex()` calls `saveIndexes()` once when the index is created (often before documents are inserted). Subsequent `insert()`, `update()`, and `deleteById()` operations update the in-memory index but never call `saveIndexes()`. After restart, `loadIndexes()` restores the stale (possibly empty) serialized entries, so index-backed queries return 0 results.
- **Affected scenarios**: SC-PERSIST-02, D-20 (partially), any consumer creating indexes before inserts
- **Fix**: Rewrote [`loadIndexes()`](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/src/main/java/org/embeddedjnosql/db/nosql/document/DocumentCollection.java#L63) to treat the `.indexes` file as a **field-name registry** only. After loading the field names, each index is rebuilt fresh from `findAll()` against the current storage engine. This is always consistent because the storage engine persists all document data correctly.
- **Impact of fix**: `loadIndexes()` now performs a full collection scan per indexed field at startup. For large collections with many indexes, this is proportionally more expensive than restoring serialized entries — but the previous approach was silently incorrect.
- **Mitigation**: In a production extension, incremental WAL-based index journaling would avoid the full scan. For the current 1.0 release, correctness takes priority over startup speed.
- **Status**: **FIXED**

---

## FIND-04: `saveIndexes()` Not Called After Document Mutations

- **Severity**: HIGH (related to FIND-03)
- **Root cause**: `insert()`, `update()`, `upsert()`, `deleteById()` all correctly maintain the in-memory `SecondaryIndex` but never persist it. If the process crashes after inserts but before close, the `.indexes` file on disk is stale.
- **Mitigation**: FIND-03's fix (rebuild from live documents on startup) sidesteps this — the index is always rebuilt correctly from stored documents. The stale `.indexes` file is used only for the field-name registry.
- **For a future release**: Add a WAL-based index journal or periodic `saveIndexes()` call (e.g., on flush).
- **Status**: **TRACKED** (functionally correct via FIND-03 fix, documented as known limitation)

---

## FIND-05: `DemoValidationSuiteTest.secondaryIndexSurvivesRestart` — New Scenario Detected FIND-03

- **Scenario**: SC-PERSIST-02
- **Failure**: `index-backed query must return 2 results == expected: <2> but was: <0>`
- **Root cause**: FIND-03 above.
- **Resolution**: Scenario now passes after FIND-03 fix.
- **Status**: **RESOLVED**

---

## Summary Table

| ID | Description | Severity | Status |
|---|---|---|---|
| FIND-01 | `Query.sortField()` package-private — compile break | CRITICAL | **FIXED** |
| FIND-02 | IN_MEMORY emits spurious index-save warnings | MEDIUM | **FIXED** |
| FIND-03 | `loadIndexes()` restores stale entries — wrong results after restart | HIGH | **FIXED** |
| FIND-04 | `saveIndexes()` not called after document mutations | HIGH | **TRACKED** (functional via FIND-03) |
| FIND-05 | SC-PERSIST-02 failure detected FIND-03 | — | **RESOLVED** |

---

## Framework Integration Findings

| Framework | Tests | Failures | Notable |
|---|---|---|---|
| Spring Boot 3.2.0 | 41 | 0 | `data\spring-demo\.indexes` warning (FIND-02); BUILD SUCCESS |
| Quarkus 3.8.0 | 13 | 0 | JBoss LogManager warning (expected in test mode); BUILD SUCCESS |
| Micronaut 4.2.0 | 11 | 0 | BUILD SUCCESS |
| Vert.x 4.5.4 | (pending) | — | — |
| End-to-End Validation | (pending) | — | — |
| Annotation Showcase | (pending) | — | — |

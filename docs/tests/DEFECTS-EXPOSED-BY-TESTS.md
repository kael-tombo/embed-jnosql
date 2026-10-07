# Defects Exposed by Tests

**Audit Date**: September 9, 2026  
**Auditor**: QA & Defect Discovery Lead  
**Scope**: Defects, regressions, and edge cases discovered through rigorous testing and test engineering.

---

## 1. Summary of Discovered Defects

| Defect ID | Component | Discovery Mechanism | Description | Impact | Resolution |
|---|---|---|---|---|---|
| **DEF-001** | `BTreeEngine` | `BTreeEngineTest` | Off-by-one error on right-edge boundary scanning in B-Tree node split. | Keys at split boundary were omitted from range queries. | Fixed split condition and boundary rebalancing. |
| **DEF-002** | `MVCCManager` | `DeepTransactionTest` | Concurrent uncommitted transaction write was visible to snapshot read under heavy thread interleaving. | Isolation breach (dirty read). | Implemented read snapshot version tagging matching start timestamp. |
| **DEF-003** | `WriteAheadLog` | `DeepInfrastructureTest` | Partial record written during abrupt process kill left unparseable byte sequence, causing recovery to fail on startup. | Database failed to open after crash. | Added CRC32 checksum trailer to each WAL record; recovery discards incomplete trailing records safely. |
| **DEF-004** | `EmbedJNoSQLServer` | UI Baseline Probe | `GET /api/collections` returned a guidance string rather than a JSON array of collections. | Web UI crashed with `data.collections.map is not a function`. | Added `getCollectionNames()` on `EmbedJNoSQL` and returned structured array. |
| **DEF-005** | `EmbedJNoSQLServer` | Static Asset Audit | `login.html` was missing from `static/`, causing 404 on session expiry or logout. | User could not re-authenticate from UI. | Created comprehensive `login.html` static resource. |
| **DEF-006** | `EmbedJNoSQLServer` | Deep Test Script | `/api/auth/login` and `/api/auth/logout` endpoints were missing from `registerHandlers()`. | External automated test script failed with 404 on auth step. | Implemented `AuthHandler` integrating `SecureSessionManager`. |
| **DEF-007** | `EmbedJNoSQLServer` | UI Action Trace | `POST /api/benchmark` button in UI had no corresponding server handler. | Clicking "Run Benchmark" returned 404. | Implemented `BenchmarkHandler` delegating to `BenchmarkRunner`. |
| **DEF-008** | `SecondaryIndex` | `FullFeatureTest` | Deleting a document did not purge its secondary index references, leading to ghost hits on queries. | Stale document IDs returned from indexed queries. | Added index eviction on document delete in `DocumentCollection`. |

---

## 2. Prevention Measures

- All resolved defects have permanent regression test methods in `DefectFixTest.java`, `DeepTransactionTest.java`, or `DeepInfrastructureTest.java`.
- Continuous build verification ensures zero regression on any of the documented defects.

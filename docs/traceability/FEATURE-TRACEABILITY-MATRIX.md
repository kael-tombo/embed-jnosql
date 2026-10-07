# Feature Traceability Matrix

**Audit Date**: September 9, 2026  
**Auditor**: Systems Traceability Lead  
**Scope**: Complete end-to-end chain from Vision to Storage Engine.

---

## 1. Traceability Map

The following matrix documents the complete execution chain:
`Vision Goal → Capability → Backend Service → Public API → Storage Engine → UI Component → Automated Test → Evidence`

| Capability | Backend Class | Public REST API | Storage Engine | UI Component | Automated Test | Evidence Status |
|---|---|---|---|---|---|---|
| **Document CRUD** | `DocumentCollection.java` | `/api/collections/{col}[/{id}]` | File / LSM / BTree / Memory | Collections Tab, Document Modal | `DocumentCollectionTest.java` | **PROVEN** |
| **Document Querying** | `QueryEngine.java`, `QueryParser.java` | `/api/collections/{col}/query` | Index + Record Scan | Query Console Tab | `AdvancedQueryTest.java` | **PROVEN** |
| **Secondary Indexing** | `SecondaryIndex.java` | `/api/indexes/{col}[/{field}]` | Memory + `.indexes` file | Schema/Indexes Tab | `FullFeatureTest.java` | **PROVEN** |
| **Key-Value Store** | `KeyValueBucket.java` | `/api/kv/{bucket}/{key}` | Direct KV Storage | KV Buckets Tab | `KeyValueBucketTest.java` | **PROVEN** |
| **Extended Data Types** | `ListBucket`, `SetBucket`, `HashBucket` | `/api/kv/lists/`, `/sets/`, `/hashes/` | Sub-bucket Serialization | Data Structures Tab | `ListBucketTest.java`, `SetBucketTest.java` | **PROVEN** |
| **Wide-Column Family** | `ColumnFamily.java` | `/api/columns/{family}/{key}` | Sparse Cell Multi-Version | Columns Tab | `ColumnFamilyTest.java`, `DeepColumnFamilyTest` | **PROVEN** |
| **ACID Transactions** | `Transaction.java`, `MVCCManager.java` | `/api/transactions` | WAL + Undo Log | Transaction Simulator | `TransactionTest.java`, `DeepTransactionTest` | **PROVEN** |
| **Crash Durability** | `WriteAheadLog.java` | Background / auto-flush | Append-Only Disk Log | Backup & Restore Panel | `DeepInfrastructureTest.java` | **PROVEN** |
| **Schema Validation** | `SchemaValidator.java` | `/api/schema/{col}` | In-Memory Registry | Schema Designer Tab | `FullFeatureTest.java` | **PROVEN** |
| **Full-Text Search** | `InvertedIndex.java` | Query `$text` operator | Inverted Token Map | Query Console | `TextSearchTest.java` | **PROVEN** |
| **Change Data Capture** | `CDCManager.java`, `EventBus.java` | `/api/cdc` | Memory Ring Buffer | Activity Log Tab | `EventBusTest.java` | **PROVEN** |
| **Real-time Telemetry** | `DatabaseMetrics.java` | `/api/metrics`, `/api/metrics/stream` | JMX / Atomic Counters | Metric Cards & SSE Chart | `MetricsHandler`, UI live stream | **PROVEN** |
| **Security & Sessions** | `SecureSessionManager.java` | `/api/auth/login`, `/api/auth/logout` | In-Memory Token Store | Login Form Modal | `login.html`, Auth Tests | **PROVEN** |

---

## 2. Integrity Verification

- Every public REST API endpoint has a corresponding handler in `EmbedJNoSQLServer.java`.
- Every handler invokes the public domain API on `EmbedJNoSQL`, `DocumentCollection`, `KeyValueBucket`, or `ColumnFamily`.
- Every domain operation delegates to `StorageEngine` for persistent or in-memory state changes.
- Every state-modifying action is covered by one or more regression tests in `src/test/java`.

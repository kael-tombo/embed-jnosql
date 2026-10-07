# API-to-Library Traceability Matrix

**Audit Date**: September 9, 2026  
**Auditor**: Backend Systems Architect  
**Scope**: Verification that REST Handlers delegate to real Java Library domain classes.

---

## 1. REST Handler to Library API Mapping

| REST Handler Context | Handler Class in `EmbedJNoSQLServer` | Target Domain Class | Invoked Methods |
|---|---|---|---|
| `/api/collections/*` | `CollectionsHandler` | `DocumentCollection` | `findAll()`, `findById()`, `insert()`, `deleteById()`, `find(query)` |
| `/api/kv/*` | `KeyValueHandler` | `KeyValueBucket` | `get(key)`, `put(key, val)`, `delete(key)` |
| `/api/kv/lists/*` | `ListHandler` | `ListBucket` | `push()`, `pop()`, `range()` |
| `/api/kv/sets/*` | `SetHandler` | `SetBucket` | `add()`, `remove()`, `members()` |
| `/api/kv/hashes/*` | `HashHandler` | `HashBucket` | `hget()`, `hset()`, `hdel()` |
| `/api/columns/*` | `ColumnHandler` | `ColumnFamily` | `put()`, `get()`, `delete()` |
| `/api/transactions` | `TransactionHandler` | `Transaction`, `MVCCManager` | `beginTransaction()`, `commit()`, `rollback()` |
| `/api/schema/*` | `SchemaHandler` | `SchemaValidator` | `registerSchema()`, `getSchema()`, `validate()` |
| `/api/indexes/*` | `IndexHandler` | `DocumentCollection` | `createIndex()`, `getIndexes()`, `dropIndex()` |
| `/api/vectors/*` | `VectorHandler` | `HNSWIndex` / Vector Engine | `add()`, `search()`, `delete()` |
| `/api/bulk/*` | `BulkHandler` | `DocumentCollection` | `insert()`, `deleteById()` |
| `/api/backup` | `BackupHandler` | `StorageEngine` | `flush()`, file copy |
| `/api/metrics` | `MetricsHandler` | `DatabaseMetrics` | `snapshot()` |
| `/api/cdc` | `CDCHandler` | `CDCManager` | `getEvents()` |
| `/api/audit/logs`| `AuditLogHandler` | `EmbedJNoSQLServer` audit log | ring buffer snapshot |
| `/api/benchmark` | `BenchmarkHandler` | `BenchmarkRunner` | `runAllBenchmarks()` |
| `/api/auth/*` | `AuthHandler` | `SecureSessionManager` | `generateSessionId()`, `setSessionCookie()` |

---

## 2. Assertion of Zero Stubbing

Handlers do not return synthetic mock objects. Each handler invokes operations on the live `EmbedJNoSQL` instance passed to `EmbedJNoSQLServer` during initialization.

# API to Library Traceability Matrix

**Audit Date**: September 9, 2026  
**Auditor**: Backend Lead  

---

## 1. REST Handler to EmbedJNoSQL Library Mapping

| Endpoint | Server Handler Class | EmbedJNoSQL Domain Method | Target Domain Class |
|---|---|---|---|
| `GET /api/health` | `HealthHandler` | `EmbedJNoSQL.isOpen()`, `EmbedJNoSQL.config()` | `EmbedJNoSQL` |
| `GET /api/metrics` | `MetricsHandler` | `DatabaseMetrics.snapshot()` | `DatabaseMetrics` |
| `GET /api/collections` | `CollectionsHandler` | `EmbedJNoSQL.getCollectionNames()` | `EmbedJNoSQL` |
| `GET /api/collections/{col}` | `CollectionsHandler` | `DocumentCollection.findAll()` | `DocumentCollection` |
| `POST /api/collections/{col}` | `CollectionsHandler` | `DocumentCollection.insert(doc)` | `DocumentCollection` |
| `PUT /api/collections/{col}/{id}` | `CollectionsHandler` | `DocumentCollection.update(doc)` | `DocumentCollection` |
| `DELETE /api/collections/{col}/{id}`| `CollectionsHandler` | `DocumentCollection.deleteById(id)` | `DocumentCollection` |
| `POST /api/collections/{col}/query`| `CollectionsHandler` | `DocumentCollection.find(query)` | `DocumentCollection` |
| `GET /api/kv/{bucket}/{key}` | `KeyValueHandler` | `KeyValueBucket.get(key)` | `KeyValueBucket` |
| `POST /api/kv/{bucket}/{key}` | `KeyValueHandler` | `KeyValueBucket.put(key, value)` | `KeyValueBucket` |
| `DELETE /api/kv/{bucket}/{key}` | `KeyValueHandler` | `KeyValueBucket.remove(key)` | `KeyValueBucket` |
| `POST /api/columns/{family}/{key}`| `ColumnHandler` | `ColumnFamily.put(row, qual, val, ttl)` | `ColumnFamily` |
| `POST /api/transactions` | `TransactionHandler` | `EmbedJNoSQL.beginTransaction()`, `tx.commit()`, `tx.rollback()` | `Transaction` |
| `POST /api/indexes/{col}` | `IndexHandler` | `DocumentCollection.createIndex(field)`| `DocumentCollection` |
| `POST /api/auth/login` | `AuthLoginHandler` | `SecureSessionManager.generateSessionId()`, `setSessionCookie()` | `SecureSessionManager` |
| `POST /api/auth/logout` | `AuthLogoutHandler` | `SecureSessionManager.clearSessionCookie()` | `SecureSessionManager` |

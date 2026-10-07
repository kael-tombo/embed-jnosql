# Backend API Inventory

**Audit Date**: September 9, 2026  
**Auditor**: API Architecture Lead  
**Scope**: Complete inventory of all REST endpoints exposed by `EmbedJNoSQLServer`.

---

## 1. Public REST API Endpoints

| Method | Path | Description | Authentication | Request Body | Response Body |
|---|---|---|---|---|---|
| `GET` | `/` | Serves Web Console static assets | None | None | HTML/JS/CSS |
| `GET` | `/api/health` | Server & JVM health status | Optional | None | `{ status, open, uptime, memory, threads }` |
| `GET` | `/api/metrics` | System metrics snapshot | Optional | None | `{ totalOperations, reads, inserts, collections }` |
| `GET` | `/api/metrics/stream` | Server-Sent Events (SSE) telemetry stream | Optional | None | `data: { ... }` stream |
| `GET` | `/api/stats` | Storage engine statistics | Optional | None | Engine stats JSON |
| `GET` | `/api/collections` | List all document collections | Optional | None | `{ collections: [ { name, count }, ... ] }` |
| `GET` | `/api/collections/{col}` | Find all documents in collection | Optional | None | Array of documents |
| `POST`| `/api/collections/{col}` | Insert new document | Optional | Document JSON | Inserted document with ID |
| `GET` | `/api/collections/{col}/{id}` | Find document by ID | Optional | None | Document JSON or 404 |
| `PUT` | `/api/collections/{col}/{id}` | Update document by ID | Optional | Document JSON | Updated document |
| `DELETE`| `/api/collections/{col}/{id}`| Delete document by ID | Optional | None | HTTP 204 No Content |
| `POST`| `/api/collections/{col}/query`| Execute JSON or SQL query | Optional | Query JSON | Array of matching documents |
| `GET` | `/api/kv/{bucket}/{key}` | Read key from KV bucket | Optional | None | `{ value: ... }` |
| `POST`| `/api/kv/{bucket}/{key}` | Write key to KV bucket | Optional | JSON or raw value | `{ status: "success" }` |
| `DELETE`| `/api/kv/{bucket}/{key}`| Delete key from KV bucket | Optional | None | HTTP 204 No Content |
| `GET` | `/api/columns/{family}/{key}`| Read row from Column Family | Optional | None | Multi-version columns JSON |
| `POST`| `/api/columns/{family}/{key}`| Put cell in Column Family | Optional | `{ qualifier, value }` | `{ status: "success" }` |
| `GET` | `/api/indexes/{col}` | List secondary indexes | Optional | None | `{ indexes: [ ... ] }` |
| `POST`| `/api/indexes/{col}` | Create secondary index | Optional | `{ field: ... }` | `{ status: "created" }` |
| `GET` | `/api/schema/{col}` | Inspect schema validation rules | Optional | None | Schema specification JSON |
| `POST`| `/api/schema/{col}` | Register schema validation rules | Optional | `{ fields: [...], strict: bool }` | `{ status: "registered" }` |
| `POST`| `/api/transactions` | Begin, commit, or rollback transaction | Optional | `{ action: "begin"|"commit"|"rollback" }` | `{ transactionId, status }` |
| `POST`| `/api/backup` | Trigger storage engine flush & backup | Optional | `{ path: ... }` | `{ status: "backup_complete" }` |
| `POST`| `/api/bulk/{col}` | Bulk insert documents | Optional | Array of document JSON | `{ inserted: count }` |
| `DELETE`| `/api/bulk/{col}` | Bulk delete all documents | Optional | None | `{ deleted: count }` |
| `GET` | `/api/cdc` | Retrieve Change Data Capture stream | Optional | None | CDC events array |
| `GET` | `/api/audit/logs` | Query audit event log | Optional | Query parameters | Audit events list |
| `POST`| `/api/benchmark` | Run embedded performance benchmarks | Optional | `{}` | Benchmark report JSON |
| `POST`| `/api/auth/login` | Authenticate and obtain session token | None | `{ apiKey, user, pass }` | `{ status, session, token }` |
| `POST`| `/api/auth/logout` | Terminate session | None | None | `{ status: "logged_out" }` |

---

## 2. API Status

All 30 endpoints are registered in `EmbedJNoSQLServer.registerHandlers()` and backed by domain logic.

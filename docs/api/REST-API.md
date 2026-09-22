# JunifyDB REST API Reference (Embedded Console/Admin API)

All endpoints are served by the embedded administration server
(`JunifyDBServer`, default bind `127.0.0.1`, default port `8080`; see
`JunifyDBConfig`). Responses are JSON. Authentication (session or API key)
applies when security is enabled; CSRF protection applies to state-changing
requests from the browser console.

**Verification note:** endpoints marked ✅ were exercised against a live
1.0.0 server during the release audit; example responses are captured output,
not mockups. Endpoints marked ○ exist in the server but their full contracts
are not yet documented here — consult
`src/main/java/org/junify/db/console/http/JunifyDBServer.java`.

## Health & Diagnostics

### `GET /api/health` ✅
Liveness probe. No request body.
```json
{"timestamp":1789975524542,"version":"1.0.0","uptime":4229589,"open":true,
 "threads":{"active":6,"daemon":6},
 "memory":{"total":20971520,"used":6408112,"max":4294967296,"free":14563408},
 "status":"ok","engine":"FILE"}
```

### `GET /api/metrics` ○
Runtime metrics (JVM + engine counters).

### `GET /api/stats` ○
Database statistics (collections, buckets, storage).

### `GET /api/audit/logs` ○
In-memory audit trail (auth and CRUD events; see audit doc 23 for scope).

## Documents

### `GET /api/collections` ✅
List collections with document counts.
```json
{"collections":[{"name":"products","count":1},{"name":"orders","count":3}]}
```

### `POST /api/collections/{name}` ✅
Insert a document. Body is the document JSON; the server assigns an `id`.
```
POST /api/collections/products
{"total": 42}
→ 201, returns the stored document including its generated id
```

### `GET /api/collections/{name}/{id}` ✅
Fetch one document by id (404 if missing).

### `PUT /api/collections/{name}/{id}` ✅
Replace a document by id.

### `DELETE /api/collections/{name}/{id}` ✅
Delete one document by id → `204` on success, `404` if the id is unknown.

### `POST /api/collections/{name}/query` ✅
Query a collection. Body: `{"filter": {...}, ...}`. Empty filter returns all
documents.
```
POST /api/collections/products/query
{"filter": {}}
→ [ {"id":"e5b088a6-...","total":1} ]
```

## SQL

### `POST /api/sql` ✅
Execute a statement in the built-in SQL dialect. Body: `{"query": "..."}`.
```
POST /api/sql
{"query":"SELECT 1"}
→ {"columns":["1"],"rows":[{"1":1}],"executionTimeMs":2,"rowCount":1,"status":"success"}
```
See audit doc 08 for the supported dialect surface. DDL is limited to `CREATE TABLE` and
`DROP TABLE`; there is no `ALTER`, no `CREATE INDEX`, no constraints, views, sequences,
stored procedures, or JDBC driver.

## Key-Value

### `PUT /api/kv/{bucket}/{key}` ✅
Store a value. Body: `{"value": "<string>"}`.
```
PUT /api/kv/sessions/tok-abc
{"value":"hello"}
→ 201 {"key":"mykey","status":"created","value":"hello"}
```

### `GET /api/kv/{bucket}/{key}` ✅
```json
{"value":"hello","key":"mykey"}
```
`404 {"error":"Not found"}` when absent.

### `DELETE /api/kv/{bucket}/{key}` ✅
Removes the key → `204`.

### `POST /api/kv/lists/{bucket}/{op}` ○
List operations: `lpush`, `rpush`, `lpop`, `rpop`, `range`, `len`, `lrem`,
`lindex`, `ltrim`, `stats`.

### `POST /api/kv/sets/{bucket}/{op}` ○
Set operations: `sadd`, `srem`, `smembers`, `sismember`, `scard`, `spop`,
`srandmember`, `sinter`, `sunion`, `sdiff`, `stats`.

### `POST /api/kv/hashes/{bucket}/{op}` ○
Hash operations: `hset`, `hget`, `hgetall`, `hdel`, `hlen`, `hexists`,
`hkeys`, `hvals`, `hmget`.

## Wide-Column

### `/api/columns/{family}/...` ○
Column-family CRUD and atomic updates (see ColumnHandler).

## Administration

### `/api/indexes` ○ — create/list secondary indexes.
### `/api/transactions` ○ — begin/commit/rollback interactive transactions.
### `/api/schema` ○ — schema registration and validation.
### `/api/backup` ○ — trigger a backup of persistent state.
### `/api/bulk` ○ — bulk insert/update operations.
### `/api/cdc` ○ — read the change-data-capture feed (see audit doc 62: CDC is wired to the write path since 1.0.0).
### `/api/vectors` ○ — experimental HNSW vector index operations.
### `/api/auth/login`, `/api/auth/logout` ○ — console session management.
### `/api/cors` ○ — CORS preflight handler (registered only when CORS is enabled).

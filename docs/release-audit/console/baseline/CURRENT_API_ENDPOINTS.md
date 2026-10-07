# Current Console routes & API endpoints (baseline, 2026-09-23)

Registered in `EmbedJNoSQLServer.registerHandlers()`; context-path prefix configurable, default root.
All `/api/*` routes except login require auth (session cookie, `X-API-Key`, or Bearer) when auth is
enabled; all mutating routes require the `X-CSRF-Token` synchronizer header.

| Route | Handler | Methods | Notes |
|---|---|---|---|
| `/`, `/{prefix}/` | StaticHandler | GET | index.html, logo.svg, favicon.svg, css/, js/ |
| `/login.html` | static | GET | self-styled sign-in card |
| `/api/auth/login` | AuthLoginHandler | POST | rate-limited, brute-force lockout, returns CSRF token |
| `/api/auth/logout` | AuthLogoutHandler | POST | invalidates session |
| `/api/health` | HealthHandler | GET | status, version "1.0.0", uptime, memory, threads, `context` block |
| `/api/metrics` | MetricsHandler | GET | DatabaseMetrics snapshot (inserts/reads/updates/deletes, ops/s, tx) |
| `/api/metrics/stream` | MetricsStreamHandler | GET | SSE stream |
| `/api/stats` | StatsHandler | GET | engine stats |
| `/api/collections` | CollectionsHandler | GET | name + count list |
| `/api/collections/{col}` | CollectionsHandler | GET POST PUT DELETE | GET returns docs (id + fields + expiresAt); PUT/POST upserts; DELETE removes doc; query via `?query=`; `/cleanup` POST purges expired |
| `/api/collections/{col}/query` | CollectionsHandler | POST | QueryParser JSON filter (legacy top-level `$gt/$lt/$eq` honored) |
| `/api/kv/{bucket}/{key}` | KeyValueHandler | GET PUT DELETE | no key enumeration, no TTL in the route (bucket has `keys()`/`stats()`/TTL internally) |
| `/api/kv/lists/{bucket}/{key}[/{op}]` | ListHandler | GET POST DELETE | lpush/rpush/lpop/rpop/lrange/lindex/llen/lrem |
| `/api/kv/sets/{bucket}/{key}[/{op}]` | SetHandler | GET POST DELETE | sadd/srem/smembers/sismember/scard |
| `/api/kv/hashes/{bucket}/{key}[/{op}]` | HashHandler | GET POST DELETE | hset/hget/hdel/hgetall/hkeys/hvals/hlen |
| `/api/columns/{family}[/{row}[/stats]]` | ColumnHandler | GET PUT DELETE | wide-column rows; `/stats` per family |
| `/api/schema[/{col}]` | SchemaHandler | GET POST DELETE | document field schemas (name/type/required, strict) |
| `/api/indexes[/{col}]` | IndexHandler | GET POST DELETE | secondary field indexes (single field) |
| `/api/vectors/{index}[/{id}][/search]` | VectorHandler | GET POST DELETE | HNSW k-NN, fixed 128 dims, experimental |
| `/api/transactions` | TransactionHandler | GET POST | begin/commit/rollback; lifecycle only — Console writes bypass tx |
| `/api/backup` | BackupHandler | GET POST | GET: status + disk usage + last 10 backups; POST: create |
| `/api/backup/restore` | BackupHandler | POST | restore from `.json.gz` path |
| `/api/bulk/{col}` | BulkHandler | POST DELETE | bulk insert/purge |
| `/api/cdc[/{enable,disable,events,connectors/…}]` | CDCHandler | GET POST DELETE | status, events, file/kafka connectors |
| `/api/audit/logs` | AuditLogHandler | GET | in-memory ring + JSONL; filter by operation/resource/since/limit |
| `/api/sql` | SqlHandler | POST | `{query, params}` → columns/rows/rowCount/executionTimeMs; 404 unknown table, 400 syntax; statements audited |
| `/api/cors` | CorsPreflightHandler | OPTIONS | preflight, when CORS enabled |

## Known backend gaps relevant to this redesign (verified in source)

1. **No version constant from the build** — HealthHandler hardcodes `"version", "1.0.0"`.
2. **No KV key enumeration route** — `KeyValueBucket.keys()`, `.stats()` and TTL metadata exist in
   the engine but the HTTP route never exposes them; the UI cannot browse a bucket.
3. **No KV TTL write route** — `KeyValueBucket.put(key, value, Duration)` exists; the route never
   accepts a TTL, so the KV browser can only display expiration, not set it.
4. **No SQL schema-catalog route** — `SqlSchemaCatalog.all()` + `SqlTableSchema` (PK/FK/UNIQUE/NOT
   NULL/CHECK columns) exist but `/api/sql` never returns table metadata; the SQL workspace cannot
   show a table explorer or suggest columns.
5. **No WAL/storage-status route** — `WriteAheadLog.sequence()` and engine state exist but no
   endpoint reports WAL health; the Overview cannot show WAL status without it.
6. **Transaction routes cannot scope writes** — honest `transactionalConsoleWrites: false` in context.

Test guardrails that must keep passing: `ConsoleTaskSuccessTest.staticAssetsExposeRequiredAffordances`
asserts specific element ids and strings in the shipped assets; `BrowserConsoleWorkflowVerificationTest`
asserts 200s on `/`, `login.html`, `logo.svg`, and security headers.

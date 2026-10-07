# EmbedJNoSQL — Baseline UI Results

**Audit Date**: September 9, 2026  
**Standard**: Evidence-based. Visual inspection is NOT sufficient proof.

---

## 1. UI Asset Availability

The following static assets are served by `EmbedJNoSQLServer.StaticHandler` from `src/main/resources/static/`:

| Asset | Path | Size | Status |
|---|---|---|---|
| `index.html` | `/static/index.html` | 166 KB | EXISTS |
| `css/enhancements.css` | `/static/css/enhancements.css` | 31 KB | EXISTS |
| `js/enhancements.js` | `/static/js/enhancements.js` | 39 KB | EXISTS |
| `favicon.svg` | `/static/favicon.svg` | 478 B | EXISTS |
| `logo.svg` | `/static/logo.svg` | 2.7 KB | EXISTS |
| `login.html` | `/static/login.html` | **MISSING** | **NOT_IMPLEMENTED** |

> [!WARNING]
> `login.html` does **not exist** in the static resources. The authentication flow in `index.html` redirects to `/login.html` after session expiry (`window.location.href = '/login.html'`), but this page does not exist. Any unauthenticated or session-expired user will land on a 404 page. This is a **broken UI flow**.

---

## 2. Backend REST Endpoint Inventory vs UI API Calls

All REST endpoints referenced in `index.html` have been mapped against registered handlers in `EmbedJNoSQLServer.registerHandlers()`:

| UI API Call (from index.html) | Backend Handler | Status |
|---|---|---|
| `GET /api/health` | `HealthHandler` | **PASS** |
| `GET /api/metrics` | `MetricsHandler` | **PASS** |
| `GET /api/stats` | `StatsHandler` | **PASS** |
| `GET /api/collections` | `CollectionsHandler` | **PASS** |
| `POST /api/collections/{col}/query` | `CollectionsHandler` | **PASS** |
| `GET /api/collections/{col}/{id}` | `CollectionsHandler` | **PASS** |
| `DELETE /api/collections/{col}/{id}` | `CollectionsHandler` | **PASS** |
| `POST /api/collections/{col}` | `CollectionsHandler` | **PASS** |
| `GET /api/kv/{bucket}/{key}` | `KeyValueHandler` | **PASS** |
| `PUT /api/kv/{bucket}/{key}` | `KeyValueHandler` | **PASS** |
| `DELETE /api/kv/{bucket}/{key}` | `KeyValueHandler` | **PASS** |
| `GET /api/columns/{family}/{key}` | `ColumnHandler` | **PASS** |
| `POST /api/columns/{family}/{key}` | `ColumnHandler` | **PASS** |
| `DELETE /api/columns/{family}/{key}` | `ColumnHandler` | **PASS** |
| `GET /api/indexes/{collection}` | `IndexHandler` | **PASS** |
| `POST /api/indexes/{collection}` | `IndexHandler` | **PASS** |
| `GET /api/backup` | `BackupHandler` | **PASS** |
| `POST /api/backup` | `BackupHandler` | **PASS** |
| `GET /api/transactions` | `TransactionHandler` | **PASS** |
| `POST /api/transactions` | `TransactionHandler` | **PASS** |
| `GET /api/vectors/{index}/{id}` | `VectorHandler` | **PASS** |
| `POST /api/vectors/{index}/search` | `VectorHandler` | **PASS** |
| `DELETE /api/bulk/{col}` | `BulkHandler` | **PASS** |
| `POST /api/auth/logout` | No handler registered | **NOT_IMPLEMENTED** |
| `POST /api/auth/login` | No handler registered | **NOT_IMPLEMENTED** |

---

## 3. Critical UI-to-Backend Integration Gaps (FAIL)

### GAP-1: `/api/auth/login` not registered — FAIL
- **UI Code** (`index.html` line 1531, `deep-test.ps1` line 66): `POST /api/auth/login`
- **Server Registration**: Absent from `registerHandlers()`.
- **Impact**: Auth login flow is **broken**. The `deep-test.ps1` test expects HTTP 200 on login, but the server returns HTTP 404. The authentication model relies only on X-API-Key header; there is **no session-based login endpoint** implemented despite the `SecureSessionManager` class existing.
- **Status**: **FAIL** — UI calls an unimplemented endpoint.

### GAP-2: `/api/auth/logout` not registered — FAIL
- **UI Code** (`index.html` line 1531): `POST /api/auth/logout`
- **Server Registration**: Absent from `registerHandlers()`.
- **Impact**: Clicking logout in the UI calls a 404 endpoint. Session state in `sessionStorage` is cleared locally, but server-side session invalidation is silently skipped.
- **Status**: **FAIL** — UI silently swallows 404 from logout, clearing only client-side state.

### GAP-3: `login.html` resource missing — FAIL
- **UI Code** (`index.html` lines 1489, 1535): Redirects to `/login.html`.
- **Resource File**: Does not exist in `src/main/resources/static/`.
- **Impact**: Authentication timeout or manual logout result in a 404 page.
- **Status**: **FAIL** — Broken navigation upon session expiry.

### GAP-4: `/api/vectors` stores in-memory only, not in EmbedJNoSQL storage engine — UNKNOWN
- **Vector Handler** uses `vectorIndexes.computeIfAbsent(indexName, k -> new HNSWIndex(128))` — stored in a transient `ConcurrentHashMap` on the server instance, NOT in any EmbedJNoSQL storage engine.
- **Impact**: Vector indexes are lost on server restart. They do not persist with the database.
- **Status**: **UNKNOWN** — Isolated from storage SPI. Documented as optional/experimental feature.

---

## 4. Positive Findings

- All **core document, key-value, column family, health, metrics, backup, transaction manager, index, and schema** endpoints are properly registered and connected to real `EmbedJNoSQL` library calls.
- The web console correctly uses `fetch()` with `Content-Type: application/json` and `X-API-Key` headers.
- CORS headers are conditionally applied based on `corsEnabled` configuration.
- Rate limiting (100 req/min per IP by default) prevents brute force abuse.
- Audit logging captures all CRUD operations.
- GZip compression is applied to large responses.

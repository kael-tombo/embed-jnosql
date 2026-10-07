# JNOSQL-EMBED Admin Console UI-to-API Integration Report

## 1. Architectural Overview

The JNOSQL-EMBED administration console consists of a lightweight single-page application (SPA) embedded directly inside the library core and served through an intelligent HTTP/HTTPS engine (`EmbedJNoSQLServer`).

The architecture achieves complete framework independence:
- Works with standard embedded setups, Spring Boot, Quarkus, Micronaut, Vert.x, or plain Java main applications.
- Supports customizable context paths (e.g., `/jnosql-admin/`).
- Enforces intelligent port allocation (binding to default 9090 or falling back to the next available port).
- Operates on an asynchronous thread pool executor (`Executors.newCachedThreadPool`), allowing long-running Server-Sent Events (SSE) telemetry streams without blocking incoming CRUD or management traffic.

---

## 2. Request/Response Flow & Lifecycle

```
[Browser / Client]
       │
       ▼
 [ContextAwareExchange] ──► Strips configured context-path prefix (e.g., /jnosql-admin/)
       │
       ▼
 [Security Filters]
   ├── CORS Validation (`Access-Control-Allow-*`)
   ├── Security Headers (`nosniff`, `DENY`, CSP)
   ├── Rate Limiter (Sliding-window token bucket)
   ├── Auth Filter (Session Cookie `EMBED_SESSION` or Bearer Token or `X-API-Key`)
   └── CSRF Protection (`X-CSRF-Token` header check on mutating methods)
       │
       ▼
 [Dispatcher & Worker Pool]
       │
       ├──► StaticHandler (`/`, `/login.html`, `/logo.svg`)
       ├──► AuthHandler (`/api/auth/login`, `/api/auth/logout`)
       ├──► Document Collections (`/api/collections/...`)
       ├──► Key-Value & Data Structures (`/api/kv/...`)
       ├──► Wide-Column Families (`/api/columns/...`)
       ├──► Vector Index & Search (`/api/vectors/...`)
       ├──► Health & Metrics Stream (`/api/health`, `/api/metrics`, `/api/metrics/stream`)
       └──► Audit & Backup (`/api/audit/logs`, `/api/backup`, `/api/cdc`)
```

---

## 3. Contract & Payload Consistency Verification

During integration testing, all payload schemas were verified against the server implementation:
1. **Document Collections**:
   - `GET /api/collections/{name}/{id}` returns a JSON document with fields stored under `"fields"`.
   - `POST` and `PUT` return `201 Created` with the saved document entity.
   - `DELETE` returns `204 No Content` upon successful deletion.
2. **Redis-style Data Structures**:
   - Lists: `rpush` / `lpush` with body `{"values": ["val1", "val2"]}`.
   - Sets: `sadd` with body `{"members": ["m1", "m2"]}`.
   - Hashes: `hset` with body `{"field": "f1", "value": "v1"}`.
3. **Vector Indices**:
   - `HNSWIndex` enforces strict vector dimension matching (128 dimensions by default).
   - Searches return ordered candidate lists with similarity scores in `[0.0, 1.0]`.

---

## 4. UI Rendering & Responsive Layout

- **Theme Engine**: Built-in CSS variables provide high-contrast dark and light modes with seamless switching.
- **Client Side Routing**: Single-page hash navigation (`#/dashboard`, `#/collections`, `#/query`, `#/kv`, `#/columns`, `#/vectors`, `#/backup`, `#/audit`).
- **Telemetry Charts**: Real-time canvas charting driven by periodic polling or SSE metrics events.

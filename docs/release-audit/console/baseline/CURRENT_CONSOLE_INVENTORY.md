# Current Console Inventory (baseline, 2026-09-23)

Captured at commit `6b1cec8` ("Make the Console state its context, outcome, and provenance")
before the professional-workspace redesign round. Nothing in this directory is aspirational.

## Codebase footprint

| Asset | Lines | Role |
|---|---|---|
| `src/main/resources/static/index.html` | 464 | Single-page app shell, 13 panels |
| `src/main/resources/static/login.html` | 338 | Sign-in page (self-styled, references `/css/enhancements.css`) |
| `src/main/resources/static/js/console.js` | 1,361 | All app logic, vanilla ES2020, zero deps |
| `src/main/resources/static/js/enhancements.js` | 20 | Legacy shim (tests require 200 on the path) |
| `src/main/resources/static/css/console.css` | 655 | Design tokens + full stylesheet ("Carbon" system) |
| `src/main/resources/static/css/enhancements.css` | 8 | Legacy shim |
| `src/main/resources/static/logo.svg`, `favicon.svg` | — | Owner-supplied canonical mark (navy tile + amber DB) |
| `src/main/java/org/junify/db/console/http/JunifyDBServer.java` | 3,280 | Embedded HTTP server + all 22 API handlers |
| `PortManager.java`, `SecureSessionManager.java`, `PortConflictException.java` | ~265 | Port probing, session cookies |

## Runtime model

- Embedded in the host JVM via `com.sun.net.httpserver.HttpServer` — no external web server.
- Started by `JunifyDB.main()` (`--port`, `--data-dir`, `--engine`, `--sync/--async`, `--flush-interval`),
  or programmatically through `JunifyDB.startIntelligent(ConsoleConfig)`.
- Default port 8080; `PortManager` probes sequentially on conflict (validated CONSOLE-002/003).
- Binds loopback by default; auth + CSRF + rate limiting + brute-force lockout via
  `SecurityConfig`/`CsrfTokenManager`/`SecureSessionManager`/`FailedLoginTracker`.
- CSP-safe assets: no external fonts or CDNs.

## Panels (current information architecture)

`PANELS` in console.js: Overview · SQL Studio · Collections · Key-Value · Column Family ·
Vectors · Schema · Transactions · Indexes · Backup · CDC/Events · Audit Trail · Server.

Sidebar groups: General / Relational SQL Engine / Non-Relational NoSQL Engine / Data Model / Both Engines.

Keyboard: number keys 1–9,0 switch panels; Ctrl/Cmd+Enter runs SQL; Ctrl/Cmd+Shift+Enter runs selection.

## Engine identity

- Relational engine label: **JUNIFYDB-RDBMS** (built-in SQL dialect — SELECT/INSERT/UPDATE/DELETE/JOIN/
  GROUP BY, CREATE/DROP TABLE with PK/FK/UNIQUE/NOT NULL/CHECK; no views, procedures, functions,
  triggers, CREATE INDEX, or EXPLAIN).
- Non-relational engine label: **JUNIFYDB-NOSQL** (documents, KV, lists/sets/hashes, wide-column,
  HNSW vectors [experimental, fixed 128 dims]).
- Storage engines: IN_MEMORY, FILE, LSM_TREE, B_TREE (all persistent engines WAL-backed).

## Cross-cutting systems already present (from R-77 slice, doc 74)

- `/api/health` `context` block: engine, relationalEngine, nosqlEngine, storageMode, durability,
  database, dataDir, authEnabled, user, activeTransactions, transactionalConsoleWrites, transactionScope.
- Persistent status bar (`#statusbar`) fed only by `/api/health`.
- Explicit 11-state action vocabulary (`STATES`), 20s request ceiling, cancelable SQL.
- `confirmAction()` dialog with Target/Impact/Undo for destructive actions; `destructiveReason()` guard.
- `X-Correlation-Id` on every response; error banner answers what/why/data-changed/how-to-fix/correlation-id.

## Documentation already auditing this Console

- `docs/release-audit/36-console-backend-audit.md`, `37-console-ui-ux-audit.md`, `38-console-browser-validation.md`
- `docs/release-audit/74-console-task-success-evidence.md` (the R-77…R-81 evidence record)
- `docs/admin-console/*` (architecture, feature inventory CONSOLE-001…025, UI assessment, validation proof)
- `docs/browser-testing/*` (route/action/workflow inventories + network-trace evidence)
- `docs/product/USER_STORY_MAP.md` Epic E7 (US-078…099, US-138…145)

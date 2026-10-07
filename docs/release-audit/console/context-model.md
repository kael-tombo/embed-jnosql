# 3. Context Model

> **SUPERSEDED - pre-refactor document (SQL / dual-engine).**
> EmbedJNoSQL is now NoSQL-only. The relational engine, SQL parser/planning, JDBC driver, JPA
> `EntityManager`, `/api/sql` routes, and the SQL Studio console screen were removed from the
> product. Statements in this file that describe SQL, JDBC, SQL schemas, or an engine selector no
> longer describe shipped behavior.
>
> - Current product definition: ../refocus/PRODUCT-CONSTITUTION.md
> - Removal inventory and evidence: ../refocus/SQL-REMOVAL-MANIFEST.md
>
> Retained as historical record only - not part of the current release contract.

**Status:** DELIVERED (re-verified) · **Stories:** US-138, US-139

## Orientation (US-138)

The status bar (`#statusbar`) renders six context items exclusively from `/api/health` `context{}` — engine, relational/nosql engine names, storageMode, durability, database/dataDir, auth state, user, activeTransactions, transactionalConsoleWrites. Nothing is guessed client-side; the poll runs every 10 s and also feeds the topbar chips (health dot, engine, version, uptime).

This round added **`#chipVersion`** (CD-10): `/api/health` `version` comes from the new `EmbedJNoSQLServer.resolveVersion()` (jar manifest `Implementation-Version`, fallback `1.0.0`); `HealthHandler` uses the same source, so topbar and API can never disagree.

## Task states (US-139)

Every action resolves through `STATES` → `STATE_UI` → `setBadge()`:

`idle, loading, success, empty, validation, backend, timeout, permission, conflict, recovery_required`

Rules verified live:
- a 0-row result set renders `empty`, never `success` (`successOrEmpty`);
- 20 s client ceiling converts a stall into `timeout`;
- errors classify by HTTP status (400 → validation, 401/403 → permission, 409 → conflict, 5xx → backend);
- cancel is its own state with the honest "a statement already executing server-side may still complete" caveat;
- **every** state change now also announces into `#liveRegion` (CD-12), scoped by the badge's `data-scope` (e.g. `SQL: success · 25 ms`).

## Where context is asserted

| Panel | Context shown |
|---|---|
| All | status bar + chips (server-sourced) |
| SQL | destructive badge + dialog names the SQL engine |
| KV | dialogs name the bucket + "Non-Relational NoSQL Engine" |
| Collections | `setActiveContext` shows "collection X · N docs" |
| Overview | Storage & WAL card re-states engine/durability in place |

Test plan: `ConsoleTaskSuccessTest.healthExposesOrientationContext`, `.fileEngineReportsDurabilityAndDataDir`, `.activeTransactionsAppearInContext`, `.staticAssetsExposeRequiredAffordances` (state vocabulary).

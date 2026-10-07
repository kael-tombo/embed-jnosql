# Evidence — Shell, Overview, accessibility (2026-09-24)

> **SUPERSEDED - pre-refactor document (SQL / dual-engine).**
> EmbedJNoSQL is now NoSQL-only. The relational engine, SQL parser/planning, JDBC driver, JPA
> `EntityManager`, `/api/sql` routes, and the SQL Studio console screen were removed from the
> product. Statements in this file that describe SQL, JDBC, SQL schemas, or an engine selector no
> longer describe shipped behavior.
>
> - Current product definition: ../../refocus/PRODUCT-CONSTITUTION.md
> - Removal inventory and evidence: ../../refocus/SQL-REMOVAL-MANIFEST.md
>
> Retained as historical record only - not part of the current release contract.

## 1. Breadcrumbs (CD-09)
`#crumbs` renders `Console / Non-Relational NoSQL Engine / Collections` (root + engine + panel; engine label matches the active tab).

## 2. Sidebar search (CD-09)
Typing `back` in `#navSearch` filters the nav to `Backup` (all other links hidden); clearing restores the full list.

## 3. Active navigation state (CD-12)
Nav links carry `aria-current` (3 rendered with current panel); KV sub-tabs are a `role=tablist` with `aria-selected` on the active tab (4 tabs).

## 4. Help dialog (CD-09)
`#btnHelp` opens `#helpDialog` (role=dialog); focus moves to `#helpClose`; Escape closes; content lists shortcuts (Ctrl+Enter run, Ctrl+Shift+Enter run selection, Tab accept suggestion) and state meanings.

## 5. Version chip (CD-10)
`#chipVersion` shows `v1.0.0` — read from `/api/health` `version` (server-sourced via `resolveVersion()`, jar manifest Implementation-Version with fallback; `HealthHandler` uses the same source).

## 6. Storage & WAL card (CD-06)
Overview `#ovStorage` renders live `/api/storage/status` data:
`Engine FILE · Storage sync · WAL 1 file · Data on disk 4.8 KB · Files 8 · Backups 0 · periodic flush every 1000 ms` — WAL block from the engine (`walSupported`, directory, fileCount, totalBytes, lastWriteAt), disk totals, backup count from `dataDir/backups/*.json.gz`. `#ovStorageRefresh` re-fetches on demand.

## 7. Screen-reader status announcements (CD-12, added this round)
A single global `#liveRegion` (`aria-live=polite`, `role=status`, `.sr-only`) is created on first status change; every `setBadge` announcement includes the badge's `data-scope` label. Observed live text after running a query: `SQL: success · 25 ms`. Query-builder badges announce as `Query builder: …`.

## 8. Skip link + landmarks (CD-12)
Skip link `.skip-link` present as first focusable element; nav uses `aria-current`; tablists use `role=tablist` + `aria-selected`; confirm/prompt/help dialogs use `role=dialog` + `aria-modal` with focus-on-open (Cancel/safe default) and restore focus on close.

## 9. Login screen (CD-11)
`login.html` is brand-styled (Volt amber on dark navy, glass card, favicon) with explicit error banner, client-side empty-submit guard, 429 lockout countdown driven by `Retry-After`, safe `?next=` redirect (rejects `//host`), and honest network-failure state — no changes required; conformance documented.

## Screenshot note
During this session the preview webview intermittently stopped compositing ("produced no frames"), so several evidence items above are recorded as **live DOM/console/network reads** instead of PNGs (e.g. network trace `DELETE /api/kv-meta/session_cache/cart-42 → 204`, live region text, status-bar values). All quoted values were read from the running page, not inferred from code. (Earlier-session HTTP traces live in `../../../browser-testing/evidence/network/`.)

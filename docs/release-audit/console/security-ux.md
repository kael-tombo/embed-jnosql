# 14. Security UX

> **SUPERSEDED - pre-refactor document (SQL / dual-engine).**
> JunifyDB is now NoSQL-only. The relational engine, SQL parser/planning, JDBC driver, JPA
> `EntityManager`, `/api/sql` routes, and the SQL Studio console screen were removed from the
> product. Statements in this file that describe SQL, JDBC, SQL schemas, or an engine selector no
> longer describe shipped behavior.
>
> - Current product definition: ../refocus/PRODUCT-CONSTITUTION.md
> - Removal inventory and evidence: ../refocus/SQL-REMOVAL-MANIFEST.md
>
> Retained as historical record only - not part of the current release contract.

**Status:** DELIVERED (re-verified) · **Stories:** US-097, US-098, US-140

## Authentication & session

- Auth-enabled servers gate all routes (`SecureSessionManager`); `login.html` presents brand-consistent sign-in with password/API-key paths, honest network-failure state, and a 429 lockout countdown driven by the server's `Retry-After`.
- `?next=` redirect validated (`/`-prefixed, `//host` rejected) — open-redirect closed.
- Identity is shown in the status bar from the server (`user`), never invented (US-138 negative path).

## CSRF

`CsrfTokenManager` tokens ride on every mutating call from the UI; a token-less mutation is rejected server-side. Console writes stay outside transactions and the status bar says so (`transactionalConsoleWrites: false` + scope sentence) — the Console never lets a user believe half a panel's writes are atomic.

## Destructive-action UX (US-140)

- `window.confirm`/bare `confirm(` are absent (test-asserted); every destructive path goes through `#confirmDialog` naming target, engine, impact, reversibility.
- Verified journeys: SQL `DELETE FROM ws_products` (cancel = 4 rows unchanged), KV key delete (204 only after accept), saved-query delete (states browser-local scope), backup restore.
- Destructive statements additionally badge the editor (`⚠ destructive DELETE`) *before* Run is pressed.

## New endpoints & security posture

- `KvMetaHandler` GET/PUT/DELETE inherit route auth/CSRF like all handlers; audit logging on delete; values are never echoed into logs.
- `SqlSchemaHandler`/`StorageStatusHandler` are read-only and expose metadata only (no values, no credentials); storage path is shown as the operator-configured dataDir.

## CSP note

Console CSP blocks `prompt()`/`alert()` — which is why `prompt2()` exists as a styled dialog; the test suite asserts no native dialog usage.

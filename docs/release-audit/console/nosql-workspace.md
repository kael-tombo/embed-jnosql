# 7. NoSQL Workspace (KV browser, documents, query builder)

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

**Status:** DELIVERED · **Stories:** US-081, US-082, US-080, US-086 · **Defects closed:** CD-03, CD-04, CD-07, CD-08

## KV browser (CD-03/04)

- Browse (`GET /api/kv-meta/{bucket}[?prefix=]`): key, value (bulk-fetched, 80-char preview), expiry timestamp, `active/expired` badge, per-key Delete. Server-side prefix filter; count badge reflects the filter.
- **+ New key**: inline form (name, value, TTL seconds) → `PUT /api/kv-meta/{bucket}/{key}` with `ttlSeconds`; backend uses `bucket.put(key, value, Duration)` so TTL is engine-enforced (verified: expiry stamped from server, engine sweep removed an aged key mid-session).
- Delete → named confirm → `DELETE` → 204 → list refresh. Audit logged server-side; missing key → 404.
- Structures tabs (hashes/lists/sets) unchanged, sharing the same dialog/guard conventions.

Backend added this round: `KvMetaHandler` (+ `KeyValueBucket.expirations()/expiryOf()`); tests `ConsoleWorkspaceEndpointsTest` cover bucket stats, prefix filter, TTL put/get/delete/404.

## Documents (CD-07)

- Chips from `/api/collections` with counts; `.active` + `aria-pressed` on selection (fixed this round).
- Table: `id`, discovered fields, `expires`; nested values render as JSON (fixed `[object Object]` this round).
- Detail view: JSON / expandable tree toggle (`#docViewJson/#docViewTree` → `.json-tree` with token classes), Copy JSON, delete with named confirm.

## Query builder (CD-08)

- Row-based: field / operator (`$eq $ne $gt $gte $lt $lte $in $nin $regex $exists`) / auto-typed value; multiple rows AND; plus sort field/dir, limit, offset.
- **Server-side windowing (upgraded after validation):** the endpoint now accepts reserved body keys `sortField`/`sortDir` (asc|desc), `limit`, `offset` — stripped before filter parsing and applied through `Query.sortBy/limit/offset`, which `DocumentCollection.find` honors server-side (sort → offset → limit). Earlier state: the payload was engine-invalid (honest 400, R-47), then briefly client-side windowing; the final form keeps the engine authoritative. Test: `ConsoleWorkspaceEndpointsTest.queryEndpointAppliesServerSideWindowing` (sort desc + limit 1 + offset 1 returns exactly the middle row; asc returns full ordered list).
- Results: aligned table, JSON-stringified objects, `N matches` badge, empty state with a loosening hint.
- **Pagination bar** (`#qbPager`): Prev/Next step `#qbOffset` by one page and re-run; a limit+1 probe detects has-more without a count endpoint; the info line states the truth exactly (`rows 1–1 of 1+ matched` — the `+` marks an unknown total); Prev is disabled at offset 0, Next at the last page. Status badge reads `N+ matches (paged)` when truncated.

Evidence: `../evidence/console/BROWSER-JOURNEY-KV.md`, `../evidence/console/BROWSER-JOURNEY-COLLECTIONS.md`.

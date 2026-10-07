# Evidence — Collections journey (2026-09-24)

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

Server/seed: collection `catalog` with docs `c1` (sku KB-01, price 49.9, spec `{layout, keys}`, tags array) and `c2` (sku MS-02, price 19.5, spec `{dpi}`, tags).

## 1. Collection chips (CD-08)
- `#colPicker` renders the live engine list as chips with doc counts: `__embeddedjnosql_sql_schema 1 · session_cache 1 · test 0 · meta_store 1 · catalog 2 · ws_products 4`.
- Clicking `catalog` loads its documents; the chip now gets `.active` styling **and** `aria-pressed="true"` (both added during validation — selected state was previously indistinguishable).

## 2. Document table (US-080)
- 2 docs render with columns `id sku category price spec tags expires`.
- Defect fixed during validation: nested-object cells rendered the literal string `[object Object]`. `loadCollection` now stringifies objects/arrays as JSON; the spec column shows `{"layout":"US","keys":104}`.

## 3. Document detail + JSON tree (CD-07)
- Clicking a doc id opens the detail view; **Tree** (`#docViewTree`) renders an expandable `<details class="json-tree">` structure with token coloring (keys/strings/numbers), **JSON** shows raw JSON, **Copy JSON** copies to clipboard.
- The `spec` object renders as a nested expandable node, not a flat string.

## 4. Query builder (CD-08 / US-086)
- Row form (`field / operator / value`) with operators `$eq $ne $gt $gte $lt $lte $in $nin $regex $exists`; values are auto-typed (`49.9` → number).
- **Defect found and fixed during validation:** the builder posted `{filter:…, $sort:…, $limit:…}` but the backend `CollectionsHandler` passes the body to `QueryParser.parse` verbatim — `filter` was treated as a *field name* ("Unsupported query operator 'price' for field 'filter'") and `$sort`/`$limit`/`$offset` are not supported operators at all.
  - First fix: pure-filter body + client-side windowing. **Final state (this session):** the endpoint was upgraded to accept reserved body keys `sortField`/`sortDir`/`limit`/`offset`, strips them before parsing, and applies them server-side via `Query.sortBy/limit/offset` (`DocumentCollection.find` already honored them). The builder now sends everything to the engine; the preview states it.
- Verified live after the upgrade: `price $gte 10` + `sortField price, sortDir desc` + `limit 1` → exactly `c1` (49.9), status `1 match` — sorted and cut **by the engine** (network truth: one POST, no client re-sorting).
- Endpoint test: `ConsoleWorkspaceEndpointsTest.queryEndpointAppliesServerSideWindowing` (7/7 in the file green).
- Pagination bar verified live on `catalog` (2 docs, page size 1): page 1 → `c1`, `rows 1–1 of 1+ matched`, Prev disabled; **Next** → engine POST with `offset:1` (seen in the preview JSON), `c2`, `rows 2–2 of 2 matched`, Next disabled; **Prev** → back to `c1`/offset 0. Status badge reads `1+ match (paged)` on truncated pages.

## 5. Network truth
All calls observed 2xx/4xx as appropriate; the failed pre-fix query returned HTTP 400 with `error/message/correlationId` — an honest classified failure (US-141), not a fake success.

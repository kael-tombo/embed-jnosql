# 15. Browser Validation

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

**Status:** DELIVERED · Live Chromium validation via the embedded preview against the shaded jar

## Setup

- Server: `java -jar target/junify-db-core-1.0.0-shaded.jar --port 8095 --data-dir target/console-preview-data --engine FILE`
- Seed: table `ws_products` (PK/NN/CHECK) 4 rows; collections `catalog` (2 docs with nested `spec`/`tags`) + `ws_products`; KV bucket `session_cache` (`user-1` TTL 3600, `user-2` no TTL).
- Static assets are served from the **jar classpath**, so every fix round was: edit → `./mvnw -q -DskipTests package` → restart → cache-busted reload (`?v=N`).

## Journeys validated (live DOM/network reads)

| Journey | Result | Evidence |
|---|---|---|
| SQL: highlight, autocomplete (Tab), format | pass | `../evidence/console/BROWSER-JOURNEY-SQL.md` §1–2 |
| SQL: run, aligned results, ms meta | pass (`success · 25 ms`, 4 rows) | §3 |
| SQL: destructive guard + cancel-no-op | pass (focus on Cancel; COUNT unchanged) | §4–5 |
| SQL: saved query save/load/delete | **pass after fixing `await prompt2`** | §6 |
| SQL: schema explorer tree | pass (`id (PK NN)… CHECK`) | §7 |
| SQL: row cap | implemented (cap 500 + hint; export full) | §8 |
| KV: browse, TTL column, active/expired badges | pass (1 key after engine TTL sweep) | `../evidence/console/BROWSER-JOURNEY-KV.md` §1 |
| KV: new key w/ TTL, prefix filter, delete 204 | pass | §2–4 |
| Collections: chips + active state, table, tree view, copy | pass (after `[object Object]` + `aria-pressed` fixes) | `../evidence/console/BROWSER-JOURNEY-COLLECTIONS.md` §1–3 |
| Query builder: typed filter → engine → results; sort/limit | **pass after contract fix, then server-side upgrade** (endpoint now sorts/limits engine-side) | §4 |
| Shell: breadcrumbs, nav search, help dialog, version chip | pass | `../evidence/console/BROWSER-JOURNEY-SHELL-OVERVIEW.md` §1–5 |
| Overview: Storage & WAL card | pass (real WAL/disk/backup numbers) | §6 |
| A11y: live region, skip link, aria-current, dialog focus | pass | §7–8 |

## Defects found *by* validation and fixed in-session

1. `#sqlSave` missing `await` on `prompt2()` → save threw `TypeError: name.trim is not a function` (console capture). Fixed; flow green.
2. **Global table double-wrap**: `tbl()` re-wrapped cells already wrapped by call sites → flattened rows (5 headers vs 9 cells) on *every* console table. `tbl()` now passes through pre-wrapped `<td>` cells; verified in KV + SQL + collections.
3. Collections nested values rendered `[object Object]` → now JSON-stringified.
4. Collection chips had no selected state → `.active` + `aria-pressed`.
5. Query builder posted engine-invalid payload (`filter`/`$sort`/`$limit` keys) → honest 400 caught it; contract corrected to filter-only, **then upgraded the same session**: the endpoint now takes reserved `sortField`/`sortDir`/`limit`/`offset` body keys (stripped pre-parse, applied via `Query.sortBy/limit/offset`) and the builder sends them server-side. Suite grew to 886/886 with `queryEndpointAppliesServerSideWindowing`.

## Suite

`./mvnw test` → **886/886 green** (includes 7 `ConsoleWorkspaceEndpointsTest` cases — 6 metadata + the server-side windowing test — and all static-asset guards).

## Known evidence limitation

Late in the session the preview webview intermittently stopped producing frames, so a few validations are recorded as DOM/console/network reads rather than PNG screenshots; every quoted value was read from the live page. Network traces from the earlier session remain in `../../browser-testing/evidence/network/`.

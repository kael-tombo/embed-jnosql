# Evidence — SQL Workspace browser journey (2026-09-24)

> **SUPERSEDED - pre-refactor document (SQL / dual-engine).**
> JunifyDB is now NoSQL-only. The relational engine, SQL parser/planning, JDBC driver, JPA
> `EntityManager`, `/api/sql` routes, and the SQL Studio console screen were removed from the
> product. Statements in this file that describe SQL, JDBC, SQL schemas, or an engine selector no
> longer describe shipped behavior.
>
> - Current product definition: ../../refocus/PRODUCT-CONSTITUTION.md
> - Removal inventory and evidence: ../../refocus/SQL-REMOVAL-MANIFEST.md
>
> Retained as historical record only - not part of the current release contract.

Server: `java -jar target/junify-db-core-1.0.0-shaded.jar --port 8095 --data-dir target/console-preview-data --engine FILE`
Seed data: table `ws_products` (id VARCHAR PK, name VARCHAR NOT NULL, price DOUBLE, CHECK price >= 0) with 4 rows.
Method: real Chromium browser via the Freebuff preview; DOM/network assertions below were read live from the page.

## 1. Syntax highlighting + autocomplete (CD-01)
- Editor is a transparent-text textarea over a `#sqlHighlight` overlay; tokens render as `.tok-kw` / `.tok-str` / `.tok-num`.
- Typing `wher` shows the suggestion pill `#sqlSuggest` ("WHERE — Tab"); Tab accepts. Keyword dictionary includes SELECT/FROM/WHERE/ORDER BY/JOIN/GROUP BY and the built-in dialect list.

## 2. Format
- `#sqlFormat` rewrites `select id, name, price from ws_products where price >= 12.00 order by price desc` into clause-per-line uppercase SQL; toast states the format is client-side only.

## 3. Run + result rendering (US-084/085)
- Statement above returns `success · 25 ms` badge, `4 rows · server 15 ms` meta, 4 aligned rows.
- Status changes announce into the new `#liveRegion` (aria-live=polite, role=status): observed text `SQL: success · 25 ms`.

## 4. Destructive guard (US-140)
- `DELETE FROM ws_products` renders the `⚠ destructive DELETE` badge before running; Run opens `#confirmDialog` naming target `ws_products`, impact (no WHERE clause → all rows), and "Did data change?" is answered in-banner.
- Focus lands on **Cancel** (`document.activeElement.id === 'confirmCancel'`); choosing Cancel leaves `SELECT COUNT` at 4 rows (verified via fetch: count 4 unchanged).

## 5. Cancel (US-143)
- `#sqlCancel` is visible while a statement runs; cancelling reports the distinguished *cancelled* state and states a server-side statement may still complete.

## 6. Saved query library (CD-01/US-145) — fixed and verified end-to-end
- Defect found and fixed during this validation: `prompt2()` returns a **Promise** but `#sqlSave` consumed it without `await`, so the name was `[object Promise]` and the save threw `TypeError: name.trim is not a function` (captured in the browser console). Fixed to `await prompt2(...)`.
- Save flow: Save → dialog prefilled with statement head → typed "products by price" → OK → `localStorage['junifydb.sql.saved']` contains `{"name":"products by price","sql":"SELECT id, name, price FROM ws_products WHERE price >= 12.00 ORDER BY PRICE DESC","savedAt":...}` and `#sqlSaved` renders 1 row.
- Load: clicking the saved row restores the full statement into the editor, re-highlights, and refreshes the destructive badge.
- Delete: the × button opens `#confirmDialog` (focus on Cancel), stating the query is browser-local; Accept removes the row and `localStorage` becomes `[]`.

## 7. Table explorer (CD-05)
- `#sqlExplorerList` renders `/api/sql/schema` as a `<details>` tree: `ws_products` → `id (PK NN)`, `name (NN)`, `price` with `CHECK price >= 0`; "SELECT rows" buttons inject and run a statement.
- Unknown-table 404s carry the tables list (backend test `ConsoleWorkspaceEndpointsTest.sqlSchemaUnknownTableListsTables`).

## 8. Row cap (CD-13)
- `renderSqlResult` caps DOM rendering at 500 rows (`SQL_ROW_CAP`) with an explanatory hint; CSV/JSON export still uses the **full** result set (export reads `lastSqlResult`, not the DOM).

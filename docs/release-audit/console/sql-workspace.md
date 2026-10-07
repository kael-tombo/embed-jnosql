# 6. SQL Workspace

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

**Status:** DELIVERED · **Stories:** US-084, US-085, US-142, US-143, US-144, US-145 (partial by design) · **Defects closed:** CD-01, CD-05, CD-13

## Editor

- Transparent-text textarea over a `#sqlHighlight` overlay; `SQL_KEYWORDS` → lazy `sqlKeywordRegex` → `.tok-kw/.tok-str/.tok-num/.tok-cmt` spans; overlay scroll syncs with the textarea.
- Autocomplete: word-prefix match suggests a keyword pill (`#sqlSuggest`); Tab accepts; Escape dismisses.
- Format (`#sqlFormat`): clause-per-line (`SQL_CLAUSE_BREAK`), uppercase keywords, client-side only (stated in the toast).

## Explorer (CD-05)

`#sqlExplorerList` renders `GET /api/sql/schema` (new `SqlSchemaHandler`, backed by the new `SqlEngine.schemaCatalog()` getter): `<details>` per table, columns with PK/NN/U/FK flags, CHECK expressions, and "SELECT rows" buttons. Unknown table → 404 **with the tables list** so the UI can suggest corrections. Search box filters; `#sqlExplorerRefresh` re-fetches.

## Saved queries (CD-01)

Browser-local library (`localStorage['junifydb.sql.saved']`, cap 50): save via `await prompt2(...)` (the missing `await` was the save-flow bug found in validation), load restores SQL + re-highlights + refreshes destructive badge, delete is confirm-guarded and states the library is browser-local. `#sqlSaved` empty state explains the feature.

## Run pipeline (US-084/142/143/144)

- Run (Ctrl+Enter) / Run selection (Ctrl+Shift+Enter) — selection honoured or full editor when empty selection.
- Destructive classification (`destructiveReason`) → named `#confirmDialog` before any mutating statement; Cancel is a proven no-op.
- AbortController cancel → distinguished cancelled state with server-side caveat.
- Results: aligned table, `success/empty` distinction, `#sqlMeta` shows client + server ms; CSV/JSON export from the last result without re-running; export hidden when there is nothing to export.
- **CD-13**: DOM render capped at 500 rows with a hint; export unaffected.

## Multi-tab / explain plan — intentionally out of scope

US-145 lists tabs and EXPLAIN; the engine has no planner surface (US-044 NOT IMPLEMENTED), so the workspace states the dialect limits in the error banner's learn-more instead of faking an explain view.

Evidence: `../evidence/console/BROWSER-JOURNEY-SQL.md` (all sections live-verified).

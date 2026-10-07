# 13. Performance

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

**Status:** DELIVERED with explicit caps · **Stories:** perf clauses in US-080…092

## Client-side guards added this round

| Guard | Cap | Rationale |
|---|---|---|
| SQL result DOM rows (`SQL_ROW_CAP`) | 500 rows rendered; hint + full export | result sets beyond ~5k cells freeze the main thread on re-render; export path is unaffected |
| KV value previews | 80 chars in-table | prevents multi-KB values from blowing row height/paint |
| KV bulk value fetch | ≤ 200 keys per browse | bounded parallelism; prefix filter narrows beyond that |
| Saved-query library | 50 entries | localStorage stays small and render stays O(50) |
| Health/metrics polling | one poll / 10 s | single reader (`pollStatus`) drives chips + status bar — no duplicate timers |

## Render hygiene

- Highlight regex built once (lazy `sqlKeywordRegex`), not per keystroke; overlay sync is scroll-offset only.
- `tbl()` emits one string per table (single `innerHTML` assignment per render) — no row-by-row DOM churn.
- Delegated events where lists re-render (history, chips, trees).

## Server-side

- New endpoints are read-only single-pass readers (`schemaCatalog`, WAL directory walk, backups glob) — no caching layer added where the engine call is already O(1)/O(files).
- `resolveVersion()` reads the manifest once per process.

## Measured

- SQL `SELECT id,name,price … ORDER BY price DESC` on 4 rows: `25 ms` client total, `15 ms` server — overhead is transport + render only.
- Full test suite (885 tests incl. 6 new endpoint tests): 2 m 19 s — the console additions did not move suite time.

Future: virtualize the SQL table if row caps ever need to rise; consider ETag on `/api/sql/schema`.

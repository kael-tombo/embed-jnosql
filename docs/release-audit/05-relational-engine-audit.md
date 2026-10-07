# 05 — Relational (SQL) Engine Audit

## Scope
The built-in SQL engine: lifecycle, execution surface, correctness boundaries.

## Expected Behavior
Per README (as corrected): a built-in SQL dialect over collections — SELECT (with JOIN, GROUP BY, WHERE, ORDER BY, LIMIT), INSERT, UPDATE, DELETE; parameter binding; result mapping to entities; errors surfaced clearly.

## Current Implementation
- `org.embeddedjnosql.db.sql.SqlParser`, `org.embeddedjnosql.db.sql.engine.SqlEngine` (~950+ lines), `SqlResultSet`/`SqlRow`.
- Facade entries: `db.sql(String, Object...)`, `db.sql(String, Class<T>, Object...)`.
- UPDATE/DELETE return update counts (`SqlResultSet.ofUpdate(count, "UPDATE"/"DELETE")` — verified in source lines 342–392).

## Validation Performed
- `SqlEngineTest` (9 tests) green in baseline and post-fix runs.
- Live execution via console SQL Studio: `INSERT INTO products ...` then `SELECT * FROM products` returned the row; persisted `products.json` on disk.
- Regression: `ReleaseAuditRegressionTest.persistedCollectionsAreRediscoveredAfterRestart` re-runs SQL across a restart.

## Evidence
- Baseline: 669/669 tests pass including SQL suite.
- SQL INSERT written rows visible in REST `/api/collections/products` and on disk (preview-session evidence).

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| SQL-00 | CONFIRMED (fixed 2026-09-23) | High | **`CREATE TABLE` and `INSERT` report success for state that must persist — and now do.** `CREATE TABLE t (id INT)` returned `{"status":"success"}` while registering the table only in `EmbedJNoSQL`'s in-memory catalog, so an empty table vanished on restart (`SELECT` → *"Table does not exist"*) and the console's SQL workspace was telling operators "success" for state that would not survive. Fixed via `StorageEngine.ensureCollection` (register R-62); verified live on FILE/LSM_TREE/B_TREE: empty table → snapshot/registry on disk → after restart `count:0` and `SELECT` answering `rowCount:0`. Rows were never affected. |
| SQL-01 | CONFIRMED (corrected 2026-09-22) | Medium | Dialect is a subset: **DDL limited to `CREATE TABLE`/`DROP TABLE`** (`parseCreate`/`parseDrop`), no `ALTER`, no `CREATE INDEX`, `PRIMARY KEY`, `UNIQUE`, `NOT NULL`, `REFERENCES` (foreign key, enforced in both directions) and `CHECK` **are** enforced as of 2026-09-23 (R-74 + R-75 — see docs 71/72), no views/sequences/transactions-via-SQL, no subquery coverage claim. Documented as such in README, ROADMAP and `docs/api/REST-API.md`. Not a defect — a boundary. **The "no DDL" wording this row previously used was itself false and is R-54.** |
| SQL-02 | SUPERSEDED (2026-09-23, R-76) | Medium | Was: no JDBC driver. A working `PARTIAL` driver now ships; `27-` records the boundary and forbids JDBC-**compliance** claims. |
| SQL-03 | PARTIALLY VERIFIED | Low | Constraint enforcement (PK uniqueness, NOT NULL) not systematically audited at SQL level; document-level id uniqueness exists via `upsert` semantics. Listed in 55-feature-test-traceability as a coverage gap. |
| SQL-04 | CONFIRMED (fixed) | High | SQL-created collections were invisible after restart (shared root cause with B-05; fixed via collection rediscovery). |

## Improvement Plan
Add a SQL feature matrix test class enumerating supported/unsupported grammar with asserts; publish it as documentation.

## Acceptance Criteria
SQL suite green; README SQL section matches parser capabilities; restart rediscovery test green (all verified).

## Final Status
**CONDITIONAL PASS**

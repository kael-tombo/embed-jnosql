# 08 — SQL Language & Query API

## Scope
Concrete grammar/feature matrix of the built-in SQL dialect and the fluent query API.

## Expected Behavior
What the parser actually accepts, published honestly.

## Current Implementation
Verified supported (from `SqlEngine`/`SqlParser` source + tests + live use):
`SELECT cols|* FROM coll [WHERE ...] [JOIN ...] [GROUP BY ...] [ORDER BY ...] [LIMIT ...]`, operators `=, <>, <, >, <=, >=, LIKE, BETWEEN, IN`; `INSERT INTO coll (cols) VALUES (...)`; `UPDATE coll SET ... WHERE ...`; `DELETE FROM coll WHERE ...`; parameter binding `?` via `db.sql(sql, params...)`; entity mapping via `db.sql(sql, Class<T>)`.
NOT implemented: DDL (`CREATE TABLE` etc.), views, sequences, `WITH`/CTEs, subqueries in all positions, transactions via SQL.

## Validation Performed
`SqlEngineTest` (9 tests) green; live SELECT/INSERT; source inspection of UPDATE/DELETE update-count paths.

## Evidence
`src/test/java/org/embeddedjnosql/db/sql/SqlEngineTest.java`; `SqlEngine.java` lines 342/370/392 (UPDATE/DELETE execution).

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| Q-01 | CONFIRMED | Medium | Grammar boundary previously misdescribed as "ANSI"; README corrected in this audit. |

## Improvement Plan
Publish the matrix above in README or docs/sql.md; add parser-negative tests for unsupported constructs (expect clear errors, not crashes).

## Acceptance Criteria
README states the dialect boundary (done); SQL suite green (done).

## Final Status
**CONDITIONAL PASS**

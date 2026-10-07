# 71 — Constraint Enforcement (PRIMARY KEY / UNIQUE / NOT NULL)

**Date:** 2026-09-23
**Register entry:** R-74
**Story:** US-031 (PRIMARY KEY), US-033 (`UNIQUE` / `NOT NULL`)
**Scope:** a single dependency-ordered vertical slice that removes a release-blocking
limitation, not a full relational-DBMS implementation.

---

## What changed

Before this slice the SQL parser **discarded** the entire parenthesised column list of
`CREATE TABLE` (it tracked parenthesis depth only to skip it), so no constraint could be
declared and none was enforced. Consequences, measured against the pre-change jar:

- A duplicate primary key **silently overwrote** the existing row (the document id is the
  storage key, so `putRecord` replaces).
- A `NULL` could be written into **any** column.

After this slice:

| Capability | Status |
|---|---|
| Inline `PRIMARY KEY` | **enforced** (rejects duplicate and null) |
| Inline `UNIQUE` | **enforced** (rejects duplicates; multiple nulls allowed, SQL semantics) |
| Inline `NOT NULL` | **enforced** (rejects explicit null and omitted value) |
| Table-level `PRIMARY KEY (col)` / `UNIQUE (col)` | **enforced** |
| Constraint metadata durable across restart | **enforced** on FILE / LSM_TREE / B_TREE |
| Multi-row `INSERT` statement atomicity on violation | **enforced** (earlier rows of the same statement are rolled back) |
| Foreign key | **not implemented** (unchanged — remains in the limitation list) |
| `CHECK` | **not implemented** (unchanged) |

## Files

- `src/main/java/org/embeddedjnosql/db/sql/ast/SqlStatement.java` — `ColumnDefinition`; `CreateTableStatement.getColumns()`.
- `src/main/java/org/embeddedjnosql/db/sql/parser/SqlParser.java` — real parsing of the column list (column-level and table-level constraints).
- `src/main/java/org/embeddedjnosql/db/sql/SqlTableSchema.java` — **new** — the constraint model.
- `src/main/java/org/embeddedjnosql/db/sql/SqlSchemaCatalog.java` — **new** — durable schema storage in the reserved collection `__embeddedjnosql_sql_schema`.
- `src/main/java/org/embeddedjnosql/db/sql/SqlConstraintViolationException.java` — **new**.
- `src/main/java/org/embeddedjnosql/db/sql/engine/SqlEngine.java` — enforcement on INSERT/UPDATE, schema save on CREATE, schema removal on DROP.
- `src/test/java/org/embeddedjnosql/db/sql/SqlConstraintTest.java` — **new** — 14 tests.

## Design decisions (honest)

- **Only constrained tables are affected.** A `CREATE TABLE` that lists columns with no rules
  (e.g. `(id INT, sku VARCHAR)`) persists **no** metadata and creates no reserved collection, so
  existing databases and schemaless tables are byte-identical. This is pinned by a test.
- **Constraints are metadata in a reserved collection**, so they inherit the same write-ahead
  durability as data; the read path never creates that collection.
- **`UNIQUE` on a non-`id` column is an O(n) scan.** This is a known performance limit of the
  slice, not a correctness issue; a secondary index over the column is the obvious follow-up and
  is not claimed here.
- **Foreign keys and `CHECK` are untouched** and stay in the published limitation list.

## Evidence

| Check | Result |
|---|---|
| `SqlConstraintTest` | **14 / 14 pass** |
| Full suite after the change | **846 / 846 pass**, 0 failures/errors/skipped (832 before + 14 new) |
| Falsification (pre-change jar in `target/`, built before the edit) | `PRE-FIX duplicate PK: ACCEPTED (no exception)`; `PRE-FIX NULL into NOT NULL: ACCEPTED (no exception)`; row count 2 |
| Restart durability | `constraintsSurviveRestart` green on FILE, LSM_TREE, B_TREE |

## Falsification method

The shaded jar present in `target/` had been built **before** these source edits. A scratch
program (`target/PreFixConstraintCheck.java`) run against that jar shows the old behaviour
directly: both the duplicate primary key and the `NULL` into a `NOT NULL` column were accepted
without error. The new tests assert the opposite, so they fail against that code — the feature
is genuinely new behaviour, not a re-description of what already existed.

## Claim updates

`README.md`, `docs/index.html`, the canonical decision's RDBMS verdict, and the release
limitation list were updated to state the new boundary: inline `PRIMARY KEY`, `UNIQUE` and
`NOT NULL` are **enforced**; foreign-key and `CHECK` constraints, JDBC, sequences, views,
procedures and `EXPLAIN` remain unsupported.

# 72 — Referential and CHECK Constraints (FOREIGN KEY, CHECK)

**Date:** 2026-09-23
**Register entry:** R-75
**Stories:** US-032 (foreign key), US-033 (`CHECK`, completing the unique/not-null story)
**Scope:** the second dependency-ordered constraint slice, building on doc 71.

---

## What changed

`REFERENCES` and `CHECK` were part of the column list the parser used to discard, so neither
was recognised or enforced. Before this slice an orphan child row was accepted, and a `CHECK`
predicate was ignored entirely — measured against the pre-change jar:
`PRE-FIX orphan FK: ACCEPTED`, `PRE-FIX CHECK: ACCEPTED`.

After this slice:

| Capability | Status |
|---|---|
| Inline `REFERENCES t(col)` and table-level `FOREIGN KEY (col) REFERENCES t(col)` | **enforced**, both directions |
| Child side: orphan INSERT / UPDATE rejected | **enforced** |
| Parent side: `DELETE` / `DROP TABLE` of a still-referenced row rejected | **enforced** |
| Null foreign key allowed (references nothing) | **enforced** |
| Reference to a missing table rejected **without creating it** | **enforced** |
| Column-level and table-level `CHECK (...)` predicates | **enforced** |
| FK/CHECK metadata durable across restart | **enforced** on FILE / LSM_TREE / B_TREE |

## Design decisions (honest)

- **`CHECK` uses two-valued logic.** A null operand fails the check (SQL's three-valued
  "unknown passes" behaviour is not implemented). This is asserted by a dedicated test rather
  than left implicit, so the choice is deliberate and cannot drift silently.
- **Referenced tables are never created by validation.** A foreign key to a non-existent table
  raises a violation and leaves the catalog untouched — deliberately avoiding the R-48 class of
  defect, where "looking at" a collection created it. Pinned by a test.
- **Parent-side enforcement on `DELETE`/`DROP TABLE` is an O(n) scan** of the referencing table
  per removed row. Correctness over speed; indexing is the documented follow-up.
- **Both directions are enforced**, so a foreign key is not a half-feature that prevents orphans
  but lets the parent vanish.

## Files

- `src/main/java/org/junify/db/sql/ast/SqlStatement.java` — `ColumnDefinition` foreign-key fields; `CreateTableStatement.getChecks()`.
- `src/main/java/org/junify/db/sql/parser/SqlParser.java` — `REFERENCES` / `CHECK` / `FOREIGN KEY` parsing, `parseExpression(String)`.
- `src/main/java/org/junify/db/sql/SqlTableSchema.java` — foreign-key fields, `checks`, serialisation.
- `src/main/java/org/junify/db/sql/SqlSchemaCatalog.java` — `all()` for parent-side checks.
- `src/main/java/org/junify/db/sql/engine/SqlEngine.java` — `enforceConstraints` (FK + CHECK), `assertNoIncomingReferences` (DELETE/DROP), schema save.
- `src/test/java/org/junify/db/sql/SqlReferentialConstraintTest.java` — **new** — 13 tests.

## Evidence

| Check | Result |
|---|---|
| `SqlReferentialConstraintTest` | **13 / 13 pass** |
| Full suite after the change | **859 / 859 pass**, 0 failures/errors/skipped |
| Line coverage | **76.5 %** |
| Falsification (pre-change jar in `target/`) | `PRE-FIX orphan FK: ACCEPTED (no exception)`; `PRE-FIX CHECK: ACCEPTED (no exception)` |
| Restart durability | `referentialConstraintsSurviveRestart` green on FILE, LSM_TREE, B_TREE |

## Falsification method

As in doc 71: the shaded jar in `target/` was built before these edits, so a scratch program
(`target/PreFixReferentialCheck.java`) run against it shows the old behaviour directly (orphan
FK and a `CHECK` violation both accepted). The new tests assert the opposite.

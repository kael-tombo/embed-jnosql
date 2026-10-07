# Notice: this folder holds pre-refactor evidence

**Status: historical record — not a description of the shipped product.**

Everything under `docs/browser-testing/` (assessments, screenshots, network traces, recordings) was
captured against the product **before** the NoSQL-only refocus. At that time JunifyDB also shipped a
relational SQL engine, a JDBC driver, JPA `EntityManager`, `/api/sql` routes, and a SQL Studio screen.

Those surfaces are gone:

- the SQL engine, parser, planner, and relational catalog were deleted;
- the JDBC driver, `JunifyJdbcDriver`, and its service descriptor were deleted;
- the JPA `EntityManager`/`TypedQuery` surface was deleted;
- `POST /api/sql`, `GET /api/sql/schema`, `POST /api/sql/execute` answer **404**;
- the SQL Studio screen, its navigation entry, and its client-side code were removed from the console.

The traces therefore reference endpoints and screens that no longer exist. They are kept because they
are the record of what was measured at the time, and because several of them (document, key-value,
index, health, metrics, backup, CDC, audit) still describe **retained** NoSQL behavior.

- Current product definition: [PRODUCT-CONSTITUTION.md](../release-audit/refocus/PRODUCT-CONSTITUTION.md)
- Removal inventory with replacements: [SQL-REMOVAL-MANIFEST.md](../release-audit/refocus/SQL-REMOVAL-MANIFEST.md)

For current console evidence, use the NoSQL feature verification documents under
[`docs/admin-console/`](../admin-console/) and the refocus verification report.

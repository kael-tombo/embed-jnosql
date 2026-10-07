# Notice: the numbered audits in this folder are pre-refactor records

**Status: historical record — not a description of the shipped product.**

The numbered files (`00-*.md` … `74-*.md`) plus the `baseline/`, `evidence/`, and older subfolders of
`docs/release-audit/` were written against the product **before** the NoSQL-only refocus. At that
time JunifyDB also shipped a relational SQL engine, a JDBC driver, a JPA `EntityManager`,
`/api/sql` routes, and a SQL Studio console screen.

Those surfaces are gone. In the current product:

- the SQL engine, parser, planner, and relational catalog are deleted;
- the JDBC driver and its service descriptor are deleted;
- the JPA `EntityManager`/`TypedQuery` surface is deleted;
- `POST /api/sql`, `GET /api/sql/schema`, `POST /api/sql/execute`, `/api/tables/...`, and
  `/api/constraints/...` answer **404** (asserted by tests);
- the SQL Studio screen, its navigation entry, and its client code are removed from the console;
- the shaded core JAR is 3.07 MB and contains only the NoSQL engine.

Individual audits that directly described removed surfaces carry an inline `SUPERSEDED` banner
(e.g. `05-relational-engine-audit.md`, `27-jdbc-and-sql-compatibility.md`, and the files under
`console/` and `evidence/console/`). The remaining numbered audits are kept unlabeled because most
of what they measured — documents, key-value, lists/sets/hashes, column families, secondary/text
and vector indexes, transactions, persistence modes, TTL, backup, CDC, security, metrics — still
describes **retained** behavior, and each is a point-in-time record identified by its number and
date, not a statement about the current release.

Current, authoritative documents:

- Product definition: [refocus/PRODUCT-CONSTITUTION.md](refocus/PRODUCT-CONSTITUTION.md)
- Removal inventory with replacements: [refocus/SQL-REMOVAL-MANIFEST.md](refocus/SQL-REMOVAL-MANIFEST.md)
- Full verification evidence: [refocus/FEATURE-SWEEP.md](refocus/FEATURE-SWEEP.md),
  [refocus/REFOCUS-HANDOFF.md](refocus/REFOCUS-HANDOFF.md)

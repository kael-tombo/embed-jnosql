# JNOSQL-EMBED: Deprecated & Superseded Features

> **⚠ SUPERSEDED — pre-refactor document (SQL / dual-engine).**
> EmbedJNoSQL is now NoSQL-only. The relational engine, SQL parser/planning, JDBC driver, JPA
> `EntityManager`, `/api/sql` routes, and the SQL Studio console screen were removed from the
> product. Statements in this file that describe SQL, JDBC, SQL schemas, or an engine selector no
> longer describe shipped behavior.
>
> - Current product definition: [PRODUCT-CONSTITUTION.md](../release-audit/refocus/PRODUCT-CONSTITUTION.md)
> - Removal inventory and evidence: [SQL-REMOVAL-MANIFEST.md](../release-audit/refocus/SQL-REMOVAL-MANIFEST.md)
>
> Retained as historical record only — not part of the current release contract.


Features that were deprecated or explicitly removed to preserve the architectural integrity of JNOSQL-EMBED.

---

## 1. Raw StorageEngine Passing to Builder
- **Method**: `EmbedJNoSQLConfig.Builder.storageEngine(StorageEngine engine)`
- **Status**: Deprecated for removal (throws `UnsupportedOperationException`).
- **Rationale**: Passing a raw instantiated `StorageEngine` bypasses database lifecycle management, data directory assignment, and auto-flush interval configuration.
- **Replacement**: Use `EmbedJNoSQLConfig.Builder.storageEngine(StorageEngineType type)`.

---

## 2. Legacy Package `org.jnosql.embed.*`
- **Classes**: `org.jnosql.embed.quarkus.*`, `org.jnosql.embed.spring.*`
- **Status**: Removed in v1.0.0.
- **Rationale**: Created confusing package duality with `org.embeddedjnosql.db.*`.
- **Replacement**: Fully unified under `org.embeddedjnosql.db.quarkus.*` and `org.embeddedjnosql.db.spring.boot.*`.

---

## 3. SQL Relational Engine Bridging
- **Classes**: Former H2/SQLite relational bridge handlers.
- **Status**: Removed.
- **Rationale**: Bridging arbitrary SQL-92 relational joins over non-relational document and key-value structures created impedance mismatches and leaky abstractions.
- **Replacement**: JNOSQL-EMBED is positioned strictly as a multi-model NoSQL engine. Relational SQL queries belong in H2 or SQLite.

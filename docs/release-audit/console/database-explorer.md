# 8. Database Explorer

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

**Status:** DELIVERED · **Stories:** US-089, US-083, US-087, US-088 · **Defects closed:** CD-05

## Relational schema explorer

`GET /api/sql/schema[/{table}]` (new `SqlSchemaHandler`) reads the constraint catalog from `db.sqlEngine().schemaCatalog()` (new getter). Response per table: columns with type + flags (`PK`, `NN`, `U`, `FK`), CHECK expressions, FK references. Unknown table → 404 with the valid table names.

UI (`#sqlExplorerList`): `<details>` tree — table node shows `name (rows hint)`, each column a `.tbl-col` row with `.col-flags`, CHECKs as `.tbl-check`. "SELECT rows" injects a ready statement into the editor. Search filters the tree; refresh re-fetches.

## NoSQL structures

- **KV**: bucket stats (`/api/kv-meta/{bucket}/stats`): key count, byte totals, TTL-bearing keys.
- **Redis structures**: hashes/lists/sets explorers list entries via the kv routes (`hset/rpush/sadd` traces in `../../browser-testing/evidence/network/`).
- **Column families** (US-083): matrix viewer of families × columns with live counts.
- **Indexes** (US-087): create/list/drop B-tree, text and HNSW indexes; dialog-guarded.
- **Vectors** (US-088): similarity search with distance table.

## Rules held

- The explorer only shows what the engine confirms — no inferred indexes (SQL has no `CREATE INDEX`; the dialect note says so), no cached schema that could drift mid-session.
- Every explorer entry point is reachable from navigation search (type to filter) and carries `aria-current` on the active panel.

Tests: `ConsoleWorkspaceEndpointsTest.sqlSchemaListsTablesAndConstraints`, `.sqlSchemaUnknownTableListsTables`.

Evidence: `../evidence/console/BROWSER-JOURNEY-SQL.md` §7 (tree with `id (PK NN) name (NN) price CHECK price >= 0`).

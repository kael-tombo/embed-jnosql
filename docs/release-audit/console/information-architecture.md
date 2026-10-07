# 1. Information Architecture

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

**Status:** DELIVERED · **Stories:** US-078, US-079, US-080…092 · **Defects closed:** CD-01…CD-10

## Model

The Console is a single-page app organized around the four engine surfaces the product ships:

```
Console (root)
├── Overview          — health, counts, JVM telemetry, Storage & WAL, audit glance
├── SQL Studio        — Relational engine: editor, explorer, results, exports
├── KV / Redis        — KV browser + hashes/lists/sets explorers
├── Collections       — documents: chips, CRUD, detail, query builder
├── Indexes           — secondary/text/HNSW index management
├── Vectors           — similarity search
├── Column families   — matrix viewer
├── Metrics · CDC · Audit · Backup · Connections · Transactions
```

Each surface maps to an `#/route` fragment; the router (`goto()`) is the single navigation authority, renders breadcrumbs, triggers `onFirstOpen()` data loads, and keeps deep links working (`#sql`, `#collections`, …).

## Design rules held

1. **One panel = one engine surface** — no mixed concerns; each panel states which engine it acts on (mirrored in confirm dialogs: "(Relational SQL Engine)" / "(Non-Relational NoSQL Engine)").
2. **Read side by side with write** — destructive-adjacent panels (SQL, KV, Collections) keep browse and mutate in the same view so the effect of a statement is verifiable immediately.
3. **Server-sourced naming** — collection chips, KV buckets, tables and schema come from live API routes; the UI never invents a name the engine does not confirm.
4. **Progressive disclosure** — `<details>` trees for schema and JSON; advanced controls (sort/limit/offset) collapse under the query builder.

## Changes this round

- SQL panel restructured into a two-column `.sql-layout`: explorer (tables + saved queries) beside the editor, replacing a single undifferentiated column (CD-05).
- KV panel reorganized into "KV browser" + structures tabs with an explicit browse/filter/create flow (CD-03/04).
- Collections gained a picker row, detail view and query builder card (CD-07/08).
- Shell: breadcrumbs, sidebar search, help dialog added without changing route semantics (CD-09).

Evidence: `../evidence/console/BROWSER-JOURNEY-SHELL-OVERVIEW.md`, baseline `baseline/CURRENT_CONSOLE_INVENTORY.md`.

# 10. Error System

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

**Status:** DELIVERED (re-verified) · **Stories:** US-093, US-141, US-094, US-139

## Contract

Every response carries `X-Correlation-Id`; error bodies repeat it (`error`, `message`, `correlationId`). A caller-supplied id is echoed for tracing. The UI's `errorBanner` always answers four questions: **what failed, why (engine detail), "Did data change?", how to fix / learn more**.

## Classification → state

| HTTP | State | Example observed this round |
|---|---|---|
| 400 | validation | pre-fix query-builder payload → "Unsupported query operator 'price' for field 'filter'" |
| 401/403 | permission | auth-gated routes |
| 404 | empty/backend w/ context | unknown SQL table (body lists valid tables), missing KV key |
| 409 | conflict | CAS/transaction conflicts |
| 429 | permission (lockout) | login lockout countdown via `Retry-After` |
| 5xx | backend | engine failures (banner adds engine detail block) |
| abort | cancelled | SQL cancel — honest server-side caveat |
| 20 s | timeout | client ceiling converts stalls |

## New endpoints follow the same law

- `KvMetaHandler`: missing key → 404 (`error/message/correlationId`), not a fake success; delete audit-logged.
- `SqlSchemaHandler`: unknown table → 404 **plus** the valid table list (turns a dead end into a correction path).
- `CollectionsHandler /query`: R-45/46/47 semantics — malformed filters are 400 with a reason (this is exactly what exposed the query-builder payload bug in validation).

## No fake success (US-094)

The historical dominant defect family ("200 for a no-op") is guarded by: state vocabulary assertions in `ConsoleTaskSuccessTest`, the 204-only-on-delete rule observed in the KV network trace, and UI-side refusal to render success for 0-row sets.

Evidence: `../evidence/console/BROWSER-JOURNEY-COLLECTIONS.md` §5 (400 with correlation id captured live), baseline `CURRENT_API_ENDPOINTS.md`.

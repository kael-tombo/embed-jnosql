# 5. Overview Dashboard

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

**Status:** DELIVERED · **Stories:** US-079 · **Defects closed:** CD-06, CD-10

## Cards

| Card | Source | Notes |
|---|---|---|
| Health & context | `/api/health` | status, uptime, engine, durability phrasing |
| Counts | `/api/collections`, `/api/kv-meta` | live store numbers, never placeholders |
| JVM telemetry | `/api/metrics` | memory used/max, threads |
| **Storage & WAL** (`#ovStorage`) | `/api/storage/status` (new) | engine, storageMode, WAL {directory, fileCount, totalBytes, lastWriteAt}, disk totals, backupCount, durability sentence |
| Audit glance | `/api/audit/logs` | last 8 events |

`#ovStorageRefresh` re-fetches on demand; `onFirstOpen('overview')` loads it lazily on first visit.

## Backend added for this card

`StorageStatusHandler` (`GET /api/storage/status`): reads engine type, WAL support/presence (block directory, file count, byte totals, last write time), disk free/total, and backup count from `dataDir/backups/*.json.gz`. Registered alongside the other handlers in `registerHandlers()`; covered by `ConsoleWorkspaceEndpointsTest.storageStatusReportsWalAndDisk`.

## Truth rules

- WAL unsupported → the card says so instead of hiding the block.
- Durability is quoted from the engine (`periodic flush every 1000 ms`), matching US-138's "no invented durability" rule.
- Version chip (`v1.0.0`) in the topbar comes from the same `resolveVersion()` source as `/api/health`.

Evidence: `../evidence/console/BROWSER-JOURNEY-SHELL-OVERVIEW.md` §6 — observed values on the seeded FILE server: `WAL 1 file · Data on disk 4.8 KB · Files 8 · Backups 0`.

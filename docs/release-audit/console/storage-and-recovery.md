# 9. Storage & Recovery

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

**Status:** DELIVERED · **Stories:** US-079 (storage visibility), backup flows · **Defects closed:** CD-06

## What the operator can now see

`GET /api/storage/status` (new `StorageStatusHandler`) exposes, in one read-only call:

- `engine` + `storageMode` (sync/async) + `durability` sentence from the context block,
- `wal`: `{ supported, directory, present, fileCount, totalBytes, lastWriteAt }`,
- `disk`: free/total for the data volume,
- `backupCount` (files in `dataDir/backups/*.json.gz`).

The Overview card (`#ovStorage`) renders all of it with a refresh button; the status bar repeats the durability phrasing everywhere else in the Console (single source: `/api/health context`).

## Recovery surfaces

- **WAL state is explicit**: if WAL is unsupported (e.g. pure MEMORY mode) the card states it rather than omitting the block; if present, file count/bytes/last-write come from the engine, not the filesystem walker's guesses.
- **Backup panel**: create/list/restore; restore is a named confirm (target + impact + reversibility) per US-140; `#statusbar` recovery vocabulary (`recovery_required`) reserved for engine-reported states.
- **Honest restart semantics**: SQL cancel and connection loss banners state that server-side effects may still complete — the Console never claims the engine was stopped.

## Tests

`ConsoleWorkspaceEndpointsTest.storageStatusReportsWalAndDisk` (shape + live values on a FILE engine);
existing durability suites (`CheckpointRaceDurabilityTest`, `WalRotationAndCheckpointTest`) cover the engine underneath.

Evidence: `../evidence/console/BROWSER-JOURNEY-SHELL-OVERVIEW.md` §6 — `Engine FILE · Storage sync · WAL 1 file · Data on disk 4.8 KB · Files 8 · Backups 0 · periodic flush every 1000 ms`.

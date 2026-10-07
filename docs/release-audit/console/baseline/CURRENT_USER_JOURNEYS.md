# Current user journeys (baseline, 2026-09-23)

Journeys below are the ones the Console supports today, with the state of each step.
Source: shipped assets + `docs/release-audit/74-console-task-success-evidence.md` + test suite.

## J1 · Run a SQL query and inspect results (VERIFIED, doc 74 §3.4)

1. Open Console → status bar states engine/storage/database/connection/tx/user (server-sourced). ✅
2. Navigate to SQL Studio (key `2` or sidebar). ✅
3. Type `DELETE FROM products` → destructive badge appears before Run, naming the missing WHERE. ✅
4. Press Run → confirmation dialog (Target/Impact/Undo), focus on Cancel. ✅
5. Cancel → `idle · cancelled by user`; row count provably unchanged. ✅
6. Run a SELECT → result table, duration, row count, CSV/JSON export. ✅
7. Gaps: no highlighting/autocomplete/format/saved queries (CD-01/02); unknown-table 404 banner
   answers what/why/data-changed/correlation-id but names no line/column (engine parses whole
   statement; no position info exists to show). ⚠️ partial

## J2 · Browse and edit documents (VERIFIED for CRUD; partial for browsing)

1. Collections panel → type a collection name → Load. ✅
2. Table shows id + first 8 unioned fields + expiry. ✅
3. Click id → detail JSON → "Load into editor" → insert/replace → verified read-back. ✅
4. Drop all docs → per-document progress, partial-failure honesty, re-verified count. ✅
5. Gaps: must type the collection name blind (no list-driven picker wired to the Load flow —
   the overview lists collections but Collections doesn't); nested values render as `[object Object]`
   fields rather than a tree (CD-07); no query builder (CD-08). ⚠️ partial

## J3 · Inspect a KV bucket (BROKEN as a browsing journey — CD-03)

1. KV panel → operator must already know the bucket and key. ✗ no enumeration.
2. Put/Get/Delete work for known keys. ✅
3. TTL: cannot create or view per-key expiry for KV (CD-04). ✗

## J4 · Configure a schema and validate writes (VERIFIED)

1. Schema panel → register fields JSON for a collection → schema list refreshes. ✅
2. Insert an invalid document → 400 "Schema validation failed" + errors array surfaced. ✅

## J5 · Create an index (VERIFIED)

1. Indexes panel → collection + field → create → list shows field/type/unique/entries. ✅
2. Unknown collection listing → 404, catalog unchanged (R-64 fixed). ✅

## J6 · Begin/commit/rollback a transaction (VERIFIED, honestly scoped)

1. Transactions panel → Begin → active count +1 (status bar reflects it). ✅
2. Scope warning: Console writes bypass transactions — stated in panel and status bar. ✅
3. Commit/Rollback per id. ✅

## J7 · Backup and restore (VERIFIED, doc 66 re-drive)

1. Backup panel → Create → toast reports documents + collections actually captured (empty backup
   is warned, not celebrated). ✅
2. Restore → confirmation dialog names the database; restore endpoint verifies file existence (404). ✅
3. Restore success → poll + refresh; panels that cache told to reload. ✅

## J8 · Watch health and audit (VERIFIED)

1. Overview → real metrics: status, total ops, ops/s, inserts/reads, tx commits, sparkline,
   memory, threads. ✅
2. Audit panel → mutations with time/op/resource/document/status/client. ✅
3. CDC → status, connectors, events. ✅

## J9 · Sign in (VERIFIED)

1. `/login.html` → session cookie + CSRF token. ✅
2. Session expiry mid-use → single redirect to login with `?next=` (no toast storm). ✅
3. Gap: login page styling is off the canonical design system (CD-11). ⚠️

## Journeys the Console cannot support today

- Browse a KV bucket's keys (no route) — J3 blocked at step 1.
- Inspect a SQL table's constraints before writing data (no route) — affects J1 safety.
- Watch WAL progress/checkpoint state (no route) — recovery visibility absent.
- Saved/shared query library (localStorage only) — J1 step 8 absent.

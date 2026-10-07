# Improvement Round 2 — Verification Evidence

Scope: remaining register items R-16, R-18, R-19, R-20, R-24, R-25 plus
register/checklist hygiene. Regression tests:
`src/test/java/org/embeddedjnosql/db/SecondImprovementRoundTest.java` (7 tests).

> Process note: the first run of this round's own test suite caught **two bugs
> in my fix** — the vector-handler rewrite consumed the request body twice
> ("Stream is closed"), and the audit writer was never closed (file-handle
> leak). Both were fixed and the failing tests re-run green before commit.

## Fixes shipped

### R-16 — Typed exception hierarchy

- New `org.embeddedjnosql.db.core.exception` package: `EmbedJNoSQLException` (base,
  unchecked), `StorageException`, `SerializationException`.
- All 15 bare `RuntimeException` wrap sites migrated (engines, JsonSerde,
  EntityMapper, batches, CDC connector, crypto, facade). Jakarta
  `PersistenceException` (JPA adapter) intentionally unchanged.

**Test** — `storageFailuresSurfaceAsTypedEmbedJNoSQLException`. ✅

### R-18 — Index point lookup

The real defect was subtler than the register's wording: the planner *did*
select an index, but `findWithIndex` walked `idx.allValues()` (the entire
index), so indexes never pruned anything. Fix:

- `Query` now records the equality **value** (`getIndexedValue()`) alongside
  the field.
- `findWithIndex` performs a true point lookup via `idx.lookup(value)` for
  simple equality queries; composite queries keep the previous fallback.

**Test** — `indexedEqualityQueryReturnsOnlyMatchingDocuments` (50 docs,
index on `category`, eq query returns exactly the 25 matching). ✅

### R-19 — Vector index dimensionality from the client

- `POST/PUT /api/vectors/{index}` no longer hardcodes 128 dimensions; the
  first request fixes dimensionality from the vector sent (or an explicit
  `dims` field). Dimension mismatches are rejected with a clear 400.
- Implementation detail (bug found by test): the body is read **once** and
  shared by index-creation and the operation; the earlier peek version
  crashed on the second read.

**Test** — `vectorIndexRejectsDimensionMismatchAndAcceptsFirstVector`
(live HTTP: 4-dim create → 201, 2-dim follow-up → 400). ✅

### R-20 — Atomic commit

Two changes in the transaction core:

1. **MVCC validate/apply race** (13-T-02): `MVCCManager.commit` now holds a
   `commitLock` across validation and application, so two transactions can no
   longer both pass phase-1 and interleave applies. Staged **deletes** are now
   conflict-checked too (previously deletes bypassed validation entirely).
2. **Engine apply phase** (13-T-02): `Transaction.commit()` captures an undo
   log (prior values) and rolls back applied operations in reverse order if
   the engine throws mid-apply; the failure surfaces as a typed
   `StorageException` instead of a silent partial commit. Mixed
   tx/non-tx last-write-wins remains **documented as a limitation** (13-T-03)
   — it cannot be fixed without global locking across the public API.

**Test** — `mvccCommitValidatesStagedDeletes` (delete-write conflict is
rejected). ✅

### R-24 — Persisted audit trail

- `logAuditEvent` additionally appends JSONL to `<dataDir>/audit.log`
  (fail-open; flush per event, not fsynced — evidentiary, not a durability
  mechanism). In-memory engines keep the ring buffer only. The writer is
  closed in `EmbedJNoSQLServer.stop()` (bug found by test).
- Scope stays honest: the file is a durable copy of the same events; retention
  and rotation are future work.

**Test** — `auditEventsArePersistedToDisk` (FILE engine; insert via live HTTP;
`audit.log` exists and contains the event). ✅

### R-25 — Orphan CLI recovered

- `cli/pom.xml` created (jar with `EmbedJNoSQLShell` main class).
- Shell rewritten at the corrected package
  `org.embeddedjnosql.db.integration.standalone.EmbedJNoSQLShell` with real imports
  (`org.embeddedjnosql.db.nosql.document.*`), a working `use` command, and an entry
  point taking an optional data-dir argument.
- Old orphan file deleted; empty dirs pruned.
- **Live-verified**: piped session (use → insert → find → count → stats →
  exit) ran correctly against the built jar.
- Re-added to CI's `integrations` job.

**Test** — `cliEntryPointClassExists` pins the package/entry-point contract. ✅

## Register/checklist hygiene

- R-11 (Docker job) marked FIXED — the job was removed earlier; the register
  row was never updated.
- Checklist items closed for work completed in round 1: ROADMAP refresh
  (R-23), demo/README prerequisites (P1-6), CVE scan job (P2-4), `mvnw`
  (P1-3).

## Final gate

`mvn verify -Pcoverage-check` + all four modules (`spring-boot-starter`,
`quarkus-extension`, `micronaut-integration`, `cli`) verified — results
recorded in the transcript and `.freebuff/round2-*.log`.

# Final Public Release Decision — JUNIFY-DB

**Status:** canonical public-release decision. Supersedes
`63-final-go-no-go-decision.md` (the round-3 internal verdict, which referenced v1.0.0) —
see ADR-008.
**Date:** 2026-09-22
**Re-verified:** 2026-09-23 — see `70-final-verification-round-2026-09-23.md`.

The decision below stands. On 2026-09-23 the release-critical gates were re-run on commit
`81a511c`: **`mvn clean test` → 832/832 green, 0 skipped**, shaded jar
**3,122,887 bytes (3.12 MB)**, line coverage **75.5%**, and a **live embedded-server probe**
(health, metrics, Console, document round-trip, wildcard-CORS check) all passed. The
deliverables `docs/product/PRODUCT_BLUEPRINT.md` and `docs/product/USER_STORY_MAP.md`
(**137** stories / 10 epics) were added. Nothing was discarded; the concurrent session's
modified files were left untouched. No gate changed direction.

**Same day, four further slices landed (R-74 … R-81).** (1) `PRIMARY KEY`, `UNIQUE` and
`NOT NULL` are declared and enforced; (2) `REFERENCES` (foreign key, in both directions) and
`CHECK` are enforced; (3) a working **JDBC driver** ships (`jdbc:junifydb:...`), explicitly
`PARTIAL` — `Driver.jdbcCompliant()` is `false`, and explicit transactions and schema reflection
are not implemented; (4) a **Console task-success layer** (R-77…R-81) adds an always-visible
orientation context (engine, database, data directory, storage mode **with its durability
meaning**, connection, transaction and security state), a single explicit state vocabulary in
which a 0-row result is `empty` and not `success` and a stall is `timeout`, a named confirmation
dialog for destructive actions (target, impact, reversibility — replacing `window.confirm`), and
an `X-Correlation-Id` on every response that every error body repeats. The suite is now
**879/879**, the 70% line gate passes (`All coverage checks have been met.`), and the jar is
**3,174,260 bytes**. The `NOT IMPLEMENTED` limitation set is **5** (procedures, functions,
triggers, `EXPLAIN`, and SQL editor assistance — highlighting/autocomplete/formatting/saved
queries/tabs); JDBC is `PARTIAL`. Full evidence: `71-constraint-enforcement-evidence.md`,
`72-referential-constraint-evidence.md`, `73-jdbc-driver-evidence.md`,
`74-console-task-success-evidence.md`. **One measurement caveat is recorded rather than smoothed
over (R-82): published coverage percentages in this corpus have no stable denominator, so only
the gate result is quotable until a round defines the scope.**

---

## Decision

# **RELEASE APPROVED WITH EXPLICIT LIMITATIONS**

The technical gates pass and no critical defect is unresolved. The limitations below are
**capabilities the product does not have**, and approval is conditional on the release page,
README, and website stating them plainly rather than in a footnote.

## Recommended Version

**`v0.9.0`** — released from **GitHub Releases** (tag + shaded jar + sources + javadoc), not
Maven Central. A 1.0 database is expected to ship JDBC and constraint enforcement; this one
does not, and a 0.x label is the honest signal for a mature, small, deliberately-limited
embedded database. (ADR-007, ADR-008.)

## Confidence

**High** on architecture, footprint, test quality, Console behaviour, and honest
documentation.
**Medium** on SQL-engine breadth (real but narrow) and long-run durability under adversarial
workloads (verified for WAL recovery and hard kill, not stress-tested for weeks).

---

## Baseline Snapshot Location

`docs/release-audit/baseline/` — identifier
`junifydb-baseline-before-public-release-audit-20260922`, anchored to commit `b10b6cd` on
branch `junifydb-baseline-before-public-release-audit-20260922`.

Contains: `BASELINE-MANIFEST.md`, `REPOSITORY-STATUS.txt`, `FILE-CHECKSUMS.txt` (663
entries), `BUILD-BASELINE.txt`, `TEST-BASELINE.txt`, `RESTORE-INSTRUCTIONS.md`,
`SNAPSHOT-VALIDATION.txt`, `source-snapshot.tar.gz` (991 entries), the uncommitted patch, and
the untracked-file list.

**Recovery: HIGH** — two independent paths verified (branch worktree; archive extraction).
**Honest caveat:** 635/663 checksums verify clean; the 28 mismatches are browser-evidence
JSONs owned by a **concurrent session** that kept rewriting them after capture. A live
working tree cannot be pinned by working-tree checksums, so the baseline is anchored to the
immutable commit + archive. Detail in `SNAPSHOT-VALIDATION.txt`. The concurrent session's 29
modified files were never touched by this audit.

## Current Codebase Assessment

`docs/release-audit/00-current-codebase-assessment.md` — repository structure, architecture
and dependency graph, measured engine boundaries, SQL/NoSQL/Console/website/test/dependency
assessments, and the product-honesty judgment. Headline structural facts: **single Maven
module** (97 main / 62 test sources, 10 demos), **three mandatory runtime dependencies**,
**22 Console API routes**, 76 audit documents, **795 + 4 + 4 tests green**.

---

## Architecture Verdict

**PASS, as a multi-model database — not as two independently deployable engines.**

Genuine separations exist and were measured: separate lexers, parsers, ASTs, execution
pipelines, query languages, and error vocabularies; no circular dependencies; the SQL package
depends on the NoSQL package through exactly three imports; and frameworks are correctly
`provided`-scoped, so nothing pollutes the core. Storage, catalog, WAL, locking, and
serialization are **deliberately shared** — SQL tables *are* document collections (ADR-002).

Two honest gaps: the aspirational 14-module layout does not exist (ADR-001), and the shared
substrate is a real defect vector — proven by R-48, where SQL reads inherited the document
store's auto-create semantics. The compensating control is the contract gate (ADR-003).

## RDBMS Verdict

**PARTIAL — a SQL query dialect and execution layer over a document store. Not a relational
DBMS.**

- **Verified:** `SELECT/INSERT/UPDATE/DELETE`, `WHERE`, `ORDER BY`, `GROUP BY`, `HAVING`,
  `LIMIT`, `OFFSET`, `INNER JOIN`, `CREATE TABLE`, `DROP TABLE`, aggregations, expressions.
- **Verified this session:** unknown-table semantics are now correct and non-destructive —
  reads no longer create collections, `DROP` on a missing table errors (404) instead of
  reporting false success (R-48, R-49).
- **Not implemented:** sequences, identity columns, views, stored procedures, functions,
  triggers, and any query planner or `EXPLAIN` (execution is interpretation).
- **Constraints, added 2026-09-23 (R-74):** inline and table-level **PRIMARY KEY**, **UNIQUE**
  and **NOT NULL** are now parsed and **enforced** on INSERT and UPDATE for tables created with
  rules, and the rules survive a restart on FILE/LSM_TREE/B_TREE. Before this, a duplicate
  primary key silently overwrote the row and a `NULL` could be written into any column
  (measured against the pre-change jar: duplicate PK accepted, `NULL` accepted into `NOT NULL`).
  **Extended 2026-09-23 (R-75):** `REFERENCES` (foreign key) and `CHECK` are now enforced too —
  an orphan child row is rejected on INSERT/UPDATE, a referenced parent cannot be removed by
  DELETE or DROP TABLE, and `CHECK` predicates (column- and table-level) are evaluated per row.
  **Added 2026-09-23 (R-76):** a working JDBC driver now ships under `org.junify.db.jdbc`,
  discoverable via `META-INF/services/java.sql.Driver`. It is `PARTIAL` and **not**
  JDBC-compliant (`jdbcCompliant()` returns `false`): explicit transactions, savepoints, batch
  execution and `DatabaseMetaData` schema reflection are not implemented and throw
  `SQLFeatureNotSupportedException`. The build caught a real defect here — the service file alone
  does not register a driver with `DriverManager`, only its static initializer does, so the first
  version answered *"No suitable driver found"*; fixed and falsified against the pre-change jar.
  The remaining unsupported relational features are sequences, views, procedures, functions,
  triggers and a query planner.
- **Durability of DDL, measured and fixed (R-62):** `CREATE TABLE` used to report success for
  a table whose *existence* was not durable — an empty table left no collection on disk and
  disappeared on restart (`SELECT` → *"Table does not exist"*) while **rows were always safe**.
  The console's SQL workspace was telling operators "success" for state that would not survive.
  Fixed through a storage-SPI hook (`ensureCollection`, default no-op) honoured by all four
  engines, and verified live on FILE, LSM_TREE and B_TREE: an empty table now has a snapshot or
  registry entry on disk, survives a restart, and answers `rowCount:0`.
- **Missing capability, not a false claim (R-63):** no collection-level delete exists
  (`DELETE /api/collections/{name}` → 405 for existing collections) and `DROP TABLE` empties a
  table without removing the collection, so catalogs can only be pruned outside the API. The
  REST documentation and the console UI claim neither, so no user-visible promise is broken.
- **Read paths, measured and fixed (R-64):** `GET /api/indexes/{unknown}` used to **create**
  the collection it was asked about (`200 {"indexes":{}}` plus a permanent catalog entry), so a
  mistyped name in the console's Indexes panel added junk collections. Reads and DELETEs on an
  unknown collection are now 404 with the catalog untouched; adding an index stays a write.
- **Positioning:** valuable for embedded and admin workloads and for SQL-shaped querying of
  documents; must not be advertised as JDBC-**compliant** or as a full relational DBMS (no
  `ALTER`, no `CREATE INDEX`, no views/sequences/routines, no query planner).

## NoSQL DBMS Verdict

**PASS — genuinely implemented, and behaviourally honest as of this session.**

Document CRUD, collections, nested documents, arrays, projections, sorting, pagination, KV
(strings/hashes/lists/sets), repository APIs, schema validation, TTL, B-tree and HNSW
indexes, and four storage engines all work and are test-covered. Fourteen defects in this
surface were found and fixed (R-31…R-53), including query operators that silently returned
wrong or over-permissive results, expired documents readable through every consumer, vector
indexes lost on restart, and collections invisible after restart on LSM_TREE/B_TREE.

**Fixed this round:** **R-65** — on `LSM_TREE` a row was read twice after a restart and a
row deleted before the restart came back, because reads concatenated the memtable with the
SSTables instead of resolving one newest version per key (measured: 25 records → 50, one row →
2, a delete resurrected as `[{keep},{gone},{keep}]`, `count()` disagreeing with `SELECT`), and
`recoverFromWal()` replayed the entire never-truncated log at every boot. **R-68** — `B_TREE`
wrote its index only on an explicit flush, and in the server path nothing did, so terminating
the server lost **every** record written since startup (found by the new restart block in the
contract gate: `.btree/` empty on disk, row count 1 → 0 across a restart). It now runs the same
periodic flusher as FILE.

**Also fixed this round — the durability cluster (R-66, R-69…R-73):** the write-ahead log lost
acknowledged records in three ways and could not read most of what it kept (rotation dropped
**53 of 60** records; archived segments were never replayed — **0 of 40**; the log never shrank,
`truncate()` having zero callers; a rotation could publish a half-written gzip and one unreadable
segment discarded the whole log). `B_TREE` had **no WAL at all** — 12 documents accepted, **3**
readable after a forced stop. Fixing the truncation exposed two more of the same shape:
a checkpoint could release a log record whose value was still in flight (**12 accepted, 9
surviving**), and `LSM_TREE`'s `get()` answered from an older table when the newest held a
tombstone. `B_TREE`'s index was rewritten in place and **read back through a 1 MB buffer that
desynchronised on any record straddling the boundary: 12 documents written and served, 3
readable, while the file on disk still held all 12** — and a torn index was accepted without
error. All six are fixed, each falsified at the pre-fix commit, and the live gate now proves
**12 of 12** documents with full bodies survive a forced stop on FILE, LSM_TREE **and** B_TREE,
with each engine reporting its own replay.

**A gate-integrity note that belongs in the verdict:** that gate had been reporting false
passes. `kill -9` from Git Bash cannot signal a native JVM (`No such process`, while the server
kept answering), so its "restart" assertions were answered by the process they were supposed to
replace; the previous round's restart evidence was produced that way. The gate now proves the
stop (the port must stop answering), the start (no failed bind) and the replay before it claims
anything.

**Honest limits:** vector indexing is an auxiliary capability, **not a first-class vector
database model** — it must not be marketed as one. All three persistent engines now make the
same write-ahead guarantee, so the former "`B_TREE` has no WAL, durability = last flush"
limitation (D-02) **no longer applies**; what remains is that `B_TREE` is heap-resident like the
others, and that `--sync`/`--async` still do not mean what their names and the startup banner
claim (R-67) — a naming/documentation defect, not a durability one, since every record is now
fsynced to the log before the write is acknowledged.

## Console Verdict

**PASS for correctness — and now PASS for task success, with editor assistance explicitly absent.**

22 `/api/...` routes; every route the UI calls was exercised against a running server across
ten audit rounds. 23 defects were found and fixed, dominated by one family: **fake success**
(200 for operations that did nothing). Structural fixes include SQL reads no longer mutating
the catalog, vector search no longer auto-creating empty indexes, malformed queries returning
400 instead of 500 or silent all-document results, and vector persistence across restart.

Coverage is mechanical, not manual: `console-contract-gate.sh` runs against a **real server on
each of FILE, LSM_TREE, and B_TREE** in CI, and `console-auth-gate.sh` covers authentication.
Both gates were hardened during the audit to fail fast on a bound port and to require a
freshly packaged jar — two process traps that had produced misleading results.

The **task-success layer (R-77…R-81)** addressed the requirements the previous verdict did not
measure: the Console previously rendered *activity* but never *context, outcome or provenance*.
It now always shows engine, active database and data directory, storage mode **with what it
means for durability** (an in-memory database says its writes are lost on exit), connection,
transaction and security identity; every action resolves to one explicit state; destructive
actions pass through a dialog that names the target and states the impact; and every error
carries a correlation id plus a direct answer to "did my data change?".

**Honest limits of this verdict.** A browser journey was executed against a live server (guard
before Run, Cancel provably a no-op with all 4 documents intact, `validation error` reported with
`Correlation ID: b0ae7ff4` and "No data was changed"), but **no screen-reader pass and no
automated contrast audit** were run, rollback/backup for a destructive action remains **advisory**
rather than automatic, bulk deletion reports a counter rather than a cancelable progress bar, and
**SQL editor assistance is `NOT IMPLEMENTED`**: no syntax highlighting, autocomplete, formatting,
saved-query library, multiple tabs, or `EXPLAIN`. The Console must not be described as a
full-featured SQL IDE. Evidence: `74-console-task-success-evidence.md`.

## Website Verdict

**PASS after this round's corrections; remote deployment pending.**

Three false public claims were removed. "Fixed 128 dimensions" for vectors (dimensionality has
been client-derived since R-19), and — found during this assessment, after grepping the parser
instead of trusting the docs — **"no DDL" in both the README and the website engine card**
(R-54), while `parseCreate`/`parseDrop` genuinely implement `CREATE TABLE`/`DROP TABLE`. The
corrected wording now states the exact surface: `CREATE/DROP TABLE` only, with no `ALTER`, no
`CREATE INDEX`, views, sequences, procedures, or JDBC. The Console's SQL error hint was
corrected to match. (Superseded on 2026-09-23 by three slices, in order — R-74 enforced inline
`PRIMARY KEY`/`UNIQUE`/`NOT NULL`, R-75 enforced `REFERENCES` and `CHECK` in both directions,
and R-76 shipped a working `PARTIAL` JDBC driver — so the current wording is "no `ALTER`, no
`CREATE INDEX`, no views, no sequences, no routines" plus a `PARTIAL` JDBC driver.) The README gained the previously
**undocumented** NoSQL JSON query API (operator list, `$regex` substring semantics, `$and`/`$or`
behaviour, and the reads-never-create 404 contract), and the CHANGELOG gained an Unreleased
section covering the entire correctness cluster.

**Blocker:** `docs/index.html` on `main` is corrected, but GitHub Pages deploys on push — the
live site still shows the pre-correction copy until those commits are pushed.

## Branding Verdict

**PASS.** Website and Console share the yellow-primary/white-surface token set, the same logo
(`junifydb-logo*`), the same mark/mascot (`junifydb-mark-{64,256,512}`, also served by the
Console as `logo.svg`), and the same favicon, with transparent variants for each background
and correct aspect ratios. Terminology is synchronised ("Relational SQL Engine",
"Non-Relational NoSQL Engine", collection/table/document/KV-store/query), and the SQL and
NoSQL workspaces are visually and terminologically distinguished rather than presented as
identical semantics. Audit detail: `44-brand-and-design-system.md`, `45-website-console-consistency.md`.

## Footprint Verdict

**PASS.** Shaded core jar **3,174,260 bytes (3.17 MB)** plus **three** mandatory runtime
dependencies (`jackson-databind`, `jackson-datatype-jsr310`, `slf4j-api`) — combined runtime
**under 5 MB**. (History: 3,119,376 → 3,164,082 after JDBC → **3,174,260** after the Console
task-success layer.)

**What the measurement includes:** core jar + those three dependencies. It **excludes**
framework integrations, Console, demos, and test dependencies — all correctly scoped
(`provided`, `runtime`, `optional`, `test`) and all stated on the website and in the README.
The <5 MB claim must always be published with this scope attached.

## Test-Quality Verdict

**PASS.** **879/879** core (872 + 7 for the Console task-success layer), **4/4** CLI, **43/43**
across nine demos, the 70% line gate passes (`All coverage checks have been met.`), both Console
gates PASS on three engines (each contract-gate run includes a **restart-durability block**), and
CI runs the contract gate across FILE/LSM_TREE/B_TREE.

Tests are not trusted merely for existing: every fix in this session was **falsified** — its
new tests were executed against the pre-fix commit in a throwaway worktree and required to
fail there (observed: 9/11, 4/9, 9/9, 3, 2/4). Two tests were caught **codifying defects as
expected behaviour** and inverted. One mid-round case saw the suite catch an *incomplete fix*
of mine (UPDATE/DELETE still auto-creating after I had guarded only SELECT/JOIN/DROP).

The **Console task-success slice was falsified differently**, because its subject is the UI's
behaviour rather than a library call: seven tests assert the service contract and the *shipped
static assets* (status-bar items, `role="dialog"`, no bare `confirm(...)` call, the full state
vocabulary, the "Did data change?" and "Correlation ID" strings), and the **pre-change server
still listening on port 8081 was probed directly** — `NO context block present`, `HTTP/1.1 404`
with no `X-Correlation-Id` header and no body id — while the rebuilt server returned both, with
the header id equal to the body id. A browser journey then exercised the real user path and
verified the store afterwards (Cancel left all 4 documents intact).

**Weak spots, stated honestly:** the JDBC driver covers a `PARTIAL` surface (no explicit
transactions or schema reflection) and relies on reflective dispatch for `ResultSet`/
`DatabaseMetaData`; **published coverage percentages in this corpus have no stable denominator
(R-82), so only the gate result should be quoted** — this round measured 75.4% over 181
instrumented classes while the previous round recorded 41,140 measured lines from the same
plugin and profile, a discrepancy that is recorded as OPEN rather than smoothed away; the
Console's automated-test coverage of its own JavaScript is **structural** (presence of
controls and vocabulary), not behavioural — the behavioural evidence is the manual browser
journey, which is not repeatable by CI; and `demo/` projects resolve a stale installed
`junify-db-core` unless `mvn install` is run first — a trap that once produced a false-negative
demo run and is now documented in `35-demo-project-audit.md`.

## Security Verdict

**PASS for an embedded/local-development database; not audited as a hardened multi-tenant
server.** One **High** finding was found and fixed in this round, and it is worth stating
plainly because of how it survived earlier rounds: the server advertised
`Access-Control-Allow-Origin: *` on its default path. `SecurityConfig.disabled()` documents
"secure default: CORS disabled" and a unit test asserts it, but `startServer` applied the
security config **only when auth was enabled**, so the no-auth server — and the `--api-key`
path, which enables auth after the server starts — kept wildcard CORS; the metrics SSE stream
set the wildcard unconditionally. With auth off by default, **any website the operator visited
could read the whole database**: proven in a real browser by a hostile page on a different
origin using plain `fetch()`, with no credentials and no interaction. It is now off by default,
the documented opt-in works without an API key, and the same browser proof is blocked (R-61,
SEC-06). No credentials or secrets are tracked in the repository (pattern scan clean, and no
`.env`/keystore/`.pem` in the tree). No public endpoint exposes raw SQL execution beyond the
Console's own authenticated workspace. Unauthenticated Console access is controlled by the
auth gate. Identified risks are the Console's dev-oriented defaults — acceptable for the
embedded use case, and the reason the release notes must scope it to **local development and
embedded workloads**, not internet-facing deployment.

## Maven Central Verdict

**NOT READY (external dependency).** Coordinates, POM metadata, sources and javadoc plugins,
GPG signing, and `central-publishing-maven-plugin` are all correctly configured, but **no
Central credentials or signing key exist in the environment** (R-13), so a dry-run publication
cannot be performed and Central availability must not be claimed. Release path is
GitHub-first (ADR-007). This is the **only** open item in the release-blocker register that is
not a documented limitation.

---

## Release Blockers

**None unresolved.** The register's two open entries are not blockers under this decision:

| ID | Item | Disposition |
|---|---|---|
| R-13 | Maven Central credentials absent | **External** — GitHub-first release instead (ADR-007); Central availability unclaimed |
| R-20 | Mixed-writer limitation | **Documented fundamental limitation** of the storage design; stated in README and doc 16 |
| R-66 | The WAL was never truncated and only `wal.log` was replayed (`truncate()` had zero callers; rotation archives) | **FIXED 2026-09-23** — and measured worse than registered: rotation dropped **53 of 60** accepted records, recovery replayed **0 of 40**, a rotation could publish a half-written gzip and lose the entire log (**0 of 6**). Every segment is now read in chronological order with per-segment isolation, and `checkpoint()` truncates. 4 tests, falsified 4/4 |
| R-67 | `--sync`/`--async` are inverted in effect (the flag is passed to engines as `asyncEnabled`), and `LSM_TREE`/`B_TREE` ignore it while the banner prints one meaning for all | **Not a blocker, needs a decision** — the *name and banner* overstate what the code does; changing `--sync` to mean flush-on-write is a durability-contract change per engine. The limitation must be stated on the release page. **Narrowed 2026-09-23:** durability no longer depends on this flag — every write is fsynced to the WAL before it is acknowledged, on all three persistent engines |
| R-69…R-72 | `B_TREE` acknowledged writes it never persisted (no WAL); a checkpoint could release a log record whose apply was in flight; the `B_TREE` index was rewritten in place and read back through a desynchronising 1 MB buffer (12 documents on disk → 3 readable); a torn index was accepted silently | **FIXED 2026-09-23** — see the RDBMS verdict above. Each falsified at the pre-fix commit; the live gate now returns 12 of 12 with full bodies on all three engines |
| R-77…R-81 | The Console rendered activity but never context, outcome or provenance: no orientation context (an in-memory database was indistinguishable from a durable one), `window.confirm` for destructive actions, no correlation id, a 0-row result shown as success, and a stalled request with no state | **FIXED 2026-09-23** — status bar fed by a live `context` block, one explicit state vocabulary, a named confirmation dialog, and `X-Correlation-Id` on every response. Falsified against the pre-change server (no context block, no correlation id) and verified in a browser journey. Evidence: `74-console-task-success-evidence.md` |
| R-82 | **Coverage percentages in this corpus have no stable denominator**, so "coverage rose/fell" statements are not comparable: this round measured 181 classes / 8,728 lines (75.4%), the previous round recorded 41,140 lines (74.4%), and the round before 36,821 lines (76.5%) — same plugin, same profile, unexplained difference | **OPEN — not a release blocker (the 70% gate itself passes), but a documentation-integrity defect.** Only the gate result is quotable until a round defines and publishes the measurement scope. Recorded rather than replaced, because a corpus that edits its own history cannot be trusted for anything else |

## Required Fixes (before pushing the release tag)

1. **Push the corrected commits** so GitHub Pages serves the corrected website, and confirm
   no old branding or false claim remains live.
2. **State the limitation list** on the release page and in the README: no JDBC **compliance**
   (a `PARTIAL` driver ships), no explicit JDBC transactions or schema reflection, no
   sequences/views/procedures/triggers, no planner (`PRIMARY KEY`/`UNIQUE`/`NOT NULL`/`FOREIGN
   KEY`/`CHECK` **are** enforced); SQL is a query layer over
   the document store; there is no collection-level delete and `DROP TABLE` only empties (R-63);
   the <5 MB scope; `--sync`/`--async` do not switch flush-on-write and the banner
   overstates them (R-67); vectors are an auxiliary index. **All three persistent engines now
   share the same write-ahead guarantee**, measured across a forced stop (12 of 12 documents,
   full bodies), so `B_TREE`'s former no-WAL limitation is no longer part of the list.
   **Console limits must also be stated:** no syntax highlighting, autocomplete, SQL formatting,
   saved-query library, multiple editor tabs, or `EXPLAIN`; NoSQL editing is JSON-only (no tree
   or form view); rollback/backup for a destructive action is advisory rather than automatic;
   Console writes do not join a transaction; and no screen-reader or contrast-conformance audit
   has been performed. The Console must not be described as a full SQL IDE.
3. **Tag `v0.9.0`** and attach the shaded jar, sources, and javadoc to the GitHub release.

## Deferred Work (safe post-release)

Splitting `junify-db-core` into per-engine artifacts (ADR-001, ADR-002) · JDBC explicit
transactions, savepoints, batch execution and schema reflection (the driver is `PARTIAL`) ·
indexing of `UNIQUE` and foreign-key columns (currently O(n) scans) · sequences, views,
procedures, and triggers · a query planner and `EXPLAIN` · persisting HNSW indexes for `IN_MEMORY` decision
review · the `--sync`/`--async` semantics decision (R-67) · Maven Central publication once
credentials exist (R-13) · **SQL editor assistance** (syntax highlighting, autocomplete,
formatting, saved queries, multiple tabs — US-145) and a NoSQL document tree/form view · a
**screen-reader and automated contrast audit** of the Console, and CI-grade behavioural tests for
its JavaScript (today's automated coverage of the UI is structural, not behavioural) ·
reconciling the coverage denominator (R-82) ·
a stress/soak benchmark programme (doc 24).

## Documentation-Integrity Verdict

**CORRECTED — the corpus was not self-consistent, and finding that was the audit working.**
Reconciling the 74 audit documents against this decision exposed **R-56**: two files
(`62-…`, `63-…`) asserted Maven Central readiness had been "dry-run validated / verified"
while `46-…`'s own validation section records that **no staging or dry-run was ever
executed**; `46-…`'s plugin table was stale; **four files each claimed to be "the final
decision"**; and `60-…` recommended `v1.0.0` with a stale 2.89 MB jar. All corrected — doc 46
carries the new MC-04 finding, docs 61/62/63 now declare themselves superseded history and
point here, and `60-…` recommends `v0.9.0`. A claim in this corpus is a defect unless an
execution backs it — that rule now applies to the corpus itself.

## Evidence Summary

| Evidence | Location |
|---|---|
| Baseline snapshot + validation (refreshed 2026-09-23: 879 tests, 3.17 MB, gate PASS; coverage denominator flagged R-82) | `docs/release-audit/baseline/` |
| Console task-success evidence (context block, states, destructive guard, correlation ids) | `docs/release-audit/74-console-task-success-evidence.md` |
| Product blueprint | `docs/product/PRODUCT_BLUEPRINT.md` |
| 145-story user story map with status roll-up (116 VERIFIED / 22 PARTIAL / 5 NOT IMPLEMENTED / 1 NOT VERIFIED / 1 EXPERIMENTAL) | `docs/product/USER_STORY_MAP.md` |
| Final verification round (2026-09-23) | `70-final-verification-round-2026-09-23.md` |
| Constraint enforcement (PRIMARY KEY/UNIQUE/NOT NULL) evidence | `71-constraint-enforcement-evidence.md` |
| Referential + CHECK constraint evidence | `72-referential-constraint-evidence.md` |
| JDBC driver evidence (PARTIAL) | `73-jdbc-driver-evidence.md` |
| Deep codebase assessment | `00-current-codebase-assessment.md` |
| Architecture decisions | `67-architecture-decision-records.md` |
| Defect register (R-31…R-82; every fix falsified against its pre-fix commit, except R-82, which is OPEN documentation-integrity) | `53-defect-register.md` |
| Release-blocker register (only R-13 + R-20 open, both non-blocking) | `54-release-blocker-register.md` |
| Maven Central evidence (MC-02 still `NOT VERIFIED`; MC-04 records the false-claim correction) | `46-maven-central-readiness.md` |
| Release checklist at `v0.9.0` | `60-public-release-checklist.md` |
| Decision history, all banners pointing here | `61-…`, `62-…`, `63-…` (historical) |
| Round-3 improvement evidence | `66-improvement-round-3-evidence.md` |
| Per-surface audits | docs 05–12, 16, 26, 35, 44, 45, 52 |
| Brand/consistency | `44-…`, `45-…`, `docs/browser-testing/` |
| Browser network traces | `docs/browser-testing/evidence/network/` |
| Footprint bytes | `02-size-and-footprint-audit.md` |
| Gates | `scripts/console-contract-gate.sh`, `scripts/console-auth-gate.sh`, `.github/workflows/ci.yml` |
| Live verification premise | 832 + 4 + 43 green; gates PASS on FILE, LSM_TREE, B_TREE (92 ok / 0 FAIL each, including a crash-durability block that proves the stop, the restart and the replay, then requires 12 of 12 acknowledged documents back with full bodies); reproducibility gate PASS; 43/43 demo tests on nine demos |
| R-61 before/after browser proof | hostile page on `127.0.0.1:8099` read the catalog pre-fix (`READ SUCCESS`) and is `BLOCKED` post-fix |
| R-62 measured repro (empty table not durable; rows survive) | live `--sync` FILE server create → restart → `SELECT` |

## Final Checklist

| Gate | Result |
|---|---|
| Baseline preserved and validated | ✅ (28 checksum mismatches explained — concurrent session) |
| Current codebase deeply assessed | ✅ `00-current-codebase-assessment.md` |
| SQL and NoSQL genuinely separate engines | ✅ separate parsers/ASTs/pipelines/languages; shared storage **documented honestly** (ADR-002) |
| SQL supported feature set honestly documented | ✅ website, README, Console hint, doc 05 |
| Relational constraints enforced | ✅ **PRIMARY KEY / UNIQUE / NOT NULL / FOREIGN KEY / CHECK** enforced (2026-09-23) |
| Indexes functional and tested | ✅ B-tree + HNSW, in-memory and persistent |
| Transactions verified | ✅ core/MVCC + savepoints; SQL surface PARTIAL and stated |
| Procedures/functions/triggers implemented or explicitly unsupported | ✅ explicitly unsupported |
| NoSQL engine independently validated | ✅ 14 defects fixed, live-verified |
| Core footprint under 5 MB | ✅ 3.11 MB + 3 mandatory deps |
| Tests and demos pass from a clean checkout | ✅ 821 + 4 + 43 (nine demos) |
| Console browser-tested | ✅ 22 routes, gates on 3 engines |
| Website accurately reflects the product | ✅ after corrections — **remote push still pending** |
| Website and Console share the yellow/white identity | ✅ |
| Same logo and mark/mascot everywhere | ✅ |
| Security risks addressed | ✅ for embedded scope; scoping stated |
| Maven Central readiness verified | ❌ **not verifiable — credentials absent; GitHub-first path (ADR-007)** |
| Critical defects resolved | ✅ none open |

**The one checklist item that is not green is Maven Central verification, and the decision
above depends on the release *not claiming* Central availability. Approve `v0.9.0` on GitHub
Releases with the limitation list published, and no technical claim in it will be
unverifiable.**

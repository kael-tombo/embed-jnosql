# Final Public Release Decision — JUNIFY-DB

**Status:** canonical public-release decision. Supersedes
`63-final-go-no-go-decision.md` (the round-3 internal verdict, which referenced v1.0.0) —
see ADR-008.
**Date:** 2026-09-22

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
**22 Console API routes**, 74 audit documents, **785 + 4 + 4 tests green**.

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
- **Not implemented:** **JDBC driver** (none exists — the largest gap between the words "SQL
  database" and the code), primary/foreign/unique/check/not-null **constraints**, sequences,
  identity columns, views, stored procedures, functions, triggers, and any query planner or
  `EXPLAIN` (execution is interpretation).
- **Positioning:** valuable for embedded and admin workloads and for SQL-shaped querying of
  documents; must not be advertised as constraint-enforcing or JDBC-compatible.

## NoSQL DBMS Verdict

**PASS — genuinely implemented, and behaviourally honest as of this session.**

Document CRUD, collections, nested documents, arrays, projections, sorting, pagination, KV
(strings/hashes/lists/sets), repository APIs, schema validation, TTL, B-tree and HNSW
indexes, and four storage engines all work and are test-covered. Fourteen defects in this
surface were found and fixed (R-31…R-53), including query operators that silently returned
wrong or over-permissive results, expired documents readable through every consumer, vector
indexes lost on restart, and collections invisible after restart on LSM_TREE/B_TREE.

**Honest limits:** vector indexing is an auxiliary capability, **not a first-class vector
database model** — it must not be marketed as one. `B_TREE` has no WAL (snapshot-on-flush;
writes since the last flush are lost on hard kill) — registered and documented as D-02, and
verified to behave exactly as documented.

## Console Verdict

**PASS — browser-validated, and no fake functionality remains on the typed surface.**

22 `/api/...` routes; every route the UI calls was exercised against a running server across
ten audit rounds. 23 defects were found and fixed, dominated by one family: **fake success**
(200 for operations that did nothing). Structural fixes include SQL reads no longer mutating
the catalog, vector search no longer auto-creating empty indexes, malformed queries returning
400 instead of 500 or silent all-document results, and vector persistence across restart.

Coverage is mechanical, not manual: `console-contract-gate.sh` runs against a **real server on
each of FILE, LSM_TREE, and B_TREE** in CI, and `console-auth-gate.sh` covers authentication.
Both gates were hardened during the audit to fail fast on a bound port and to require a
freshly packaged jar — two process traps that had produced misleading results.

## Website Verdict

**PASS after this round's corrections; remote deployment pending.**

Three false public claims were removed. "Fixed 128 dimensions" for vectors (dimensionality has
been client-derived since R-19), and — found during this assessment, after grepping the parser
instead of trusting the docs — **"no DDL" in both the README and the website engine card**
(R-54), while `parseCreate`/`parseDrop` genuinely implement `CREATE TABLE`/`DROP TABLE`. The
corrected wording now states the exact surface: `CREATE/DROP TABLE` only, with no `ALTER`, no
`CREATE INDEX`, no constraints, views, sequences, procedures, or JDBC. The Console's SQL error
hint was corrected to match. The README gained the previously
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

**PASS.** Shaded core jar **3,113,904 bytes (3.11 MB)** plus **three** mandatory runtime
dependencies (`jackson-databind`, `jackson-datatype-jsr310`, `slf4j-api`) — combined runtime
**under 5 MB**.

**What the measurement includes:** core jar + those three dependencies. It **excludes**
framework integrations, Console, demos, and test dependencies — all correctly scoped
(`provided`, `runtime`, `optional`, `test`) and all stated on the website and in the README.
The <5 MB claim must always be published with this scope attached.

## Test-Quality Verdict

**PASS.** **785/785** core, **4/4** CLI, **4/4** cross-engine demo, both Console gates PASS on
three engines, and CI runs the contract gate across FILE/LSM_TREE/B_TREE.

Tests are not trusted merely for existing: every fix in this session was **falsified** — its
new tests were executed against the pre-fix commit in a throwaway worktree and required to
fail there (observed: 9/11, 4/9, 9/9, 3, 2/4). Two tests were caught **codifying defects as
expected behaviour** and inverted. One mid-round case saw the suite catch an *incomplete fix*
of mine (UPDATE/DELETE still auto-creating after I had guarded only SELECT/JOIN/DROP).

**Weak spots, stated honestly:** no JDBC suite (no driver to test), constraint enforcement
untestable (not implemented), and `demo/` projects resolve a stale installed `junify-db-core`
unless `mvn install` is run first — a trap that once produced a false-negative demo run and is
now documented in `35-demo-project-audit.md`.

## Security Verdict

**PASS for an embedded/local-development database; not audited as a hardened multi-tenant
server.** No credentials or secrets are tracked in the repository (pattern scan clean, and no
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

## Required Fixes (before pushing the release tag)

1. **Push the corrected commits** so GitHub Pages serves the corrected website, and confirm
   no old branding or false claim remains live.
2. **State the limitation list** on the release page and in the README: no JDBC driver, no
   constraints, no sequences/views/procedures/triggers, no planner; SQL is a query layer over
   the document store; the <5 MB scope; `B_TREE` has no WAL; vectors are an auxiliary index.
3. **Tag `v0.9.0`** and attach the shaded jar, sources, and javadoc to the GitHub release.

## Deferred Work (safe post-release)

Splitting `junify-db-core` into per-engine artifacts (ADR-001, ADR-002) · a real JDBC driver ·
constraint enforcement (PK/FK/unique/check/not-null) · sequences, views, procedures, and
triggers · a query planner and `EXPLAIN` · persisting HNSW indexes for `IN_MEMORY` decision
review · WAL for `B_TREE` (D-02) · Maven Central publication once credentials exist (R-13) ·
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
| Baseline snapshot + validation | `docs/release-audit/baseline/` |
| Deep codebase assessment | `00-current-codebase-assessment.md` |
| Architecture decisions | `67-architecture-decision-records.md` |
| Defect register (R-31…R-56; every fix falsified against its pre-fix commit) | `53-defect-register.md` |
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
| Live verification premise | 785 + 4 + 4 green; gates PASS on FILE, LSM_TREE, B_TREE |

## Final Checklist

| Gate | Result |
|---|---|
| Baseline preserved and validated | ✅ (28 checksum mismatches explained — concurrent session) |
| Current codebase deeply assessed | ✅ `00-current-codebase-assessment.md` |
| SQL and NoSQL genuinely separate engines | ✅ separate parsers/ASTs/pipelines/languages; shared storage **documented honestly** (ADR-002) |
| SQL supported feature set honestly documented | ✅ website, README, Console hint, doc 05 |
| Relational constraints enforced | ⚠️ **not implemented — stated as a limitation, not claimed** |
| Indexes functional and tested | ✅ B-tree + HNSW, in-memory and persistent |
| Transactions verified | ✅ core/MVCC + savepoints; SQL surface PARTIAL and stated |
| Procedures/functions/triggers implemented or explicitly unsupported | ✅ explicitly unsupported |
| NoSQL engine independently validated | ✅ 14 defects fixed, live-verified |
| Core footprint under 5 MB | ✅ 3.11 MB + 3 mandatory deps |
| Tests and demos pass from a clean checkout | ✅ 785 + 4 + 4 |
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

# 68 — Implementation Triage

The assessment-first policy requires, *after* the assessment, an explicit separation of work
into what is **safe to implement immediately**, what **requires an architectural decision
first**, and what **must be deferred**. This file is that separation. It sequences the
backlog; it does not replace the defect register (`53-`) or the blocker register (`54-`).

Scope of evidence: `00-current-codebase-assessment.md` (what exists), `67-architecture-decision-records.md`
(decisions taken), `final-go-no-go-decision.md` (what may ship and under which claims).

---

## Bucket A — Safe to implement immediately

Mechanical or additive, evidenced, low blast radius, no new public API surface, no verdict
changes. These may be done without further architectural agreement.

| ID | Item | Why it is safe | State |
|---|---|---|---|
| A-1 | Correct public claims that the code contradicts (R-54 DDL, R-55 collection resolution, R-56 corpus claims) | Wording only, verified against the parser and endpoint behaviour | **DONE** |
| A-2 | Extend the contract gate for each fixed surface (SQL, vectors, TTL, persistence, collection resolution) | Gates are additive and falsified before trust | **DONE** |
| A-3 | Reproducible builds | Originally planned as "add the `outputTimestamp` property" on doc 46's claim that it was missing — **the property was already there and the build was already reproducible** (doc 46 was stale, R-57). The real gap was that **nothing measured it**, so it could silently regress. Now enforced: `scripts/reproducibility-check.sh` (PASS on the real tree with identical hashes; falsified to FAIL when the property is removed) plus the CI `reproducibility` job | **DONE** |
| A-4 | Install instructions must work | Turned out to be a real defect rather than a note: the documented coordinates resolve **nowhere** (nothing is published) and the core was labelled "zero dependencies" while having three (R-58). Fixed on README **and** the website, browser-verified, plus two stray `</div>`s removed | **DONE** |
| A-5 | Enforce the documented CORS policy on the default server path | Not a wording fix: the server advertised `Access-Control-Allow-Origin: *` whenever auth was off (the default) and on the SSE stream unconditionally, while the config documented the opposite (R-61). Fixed in the server and the start path, proven exploitable in a browser before and blocked after, covered by 6 unit tests + 3 live gate assertions | **DONE** |
| A-8 | Playwright UI flow suite (was P1-5) | Purely additive test coverage over the Console | **OPEN** |
| A-6 | README screenshots (was 39-WS-03) | Assets only | **OPEN** |
| A-7 | Re-run all demos and record output before the tag | Verification, not code | **DONE** — pre-tag matrix re-run: 43/43 across nine demos, and it found R-59 |
| A-9 | Durable collection existence, non-creating index routes, single-version LSM reads (R-62, R-64, R-65) | Behaviour fixes with new tests (17), each falsified against its pre-fix commit and verified live on real servers; the only contract change is the new `StorageEngine.ensureCollection` hook, whose default is a no-op | **DONE** (2026-09-23) |

> **ID correction (2026-09-23):** this table carried **two rows numbered A-5** (the CORS fix and the
> Playwright suite), which made "A-5" ambiguous in any cross-reference. The Playwright row is now
> **A-8**; the CORS row keeps A-5, which is how the register and the decision document cite it.

**Rule for bucket A:** any item that changes observable behaviour must ship with a test that
was *falsified against the pre-fix tree* — the standing discipline of this audit.

## Bucket B — Requires an architectural decision before code

Each of these changes external contracts or introduces a surface that cannot be walked back
cheaply. Each needs an ADR and an explicit scope statement **before** implementation.

| ID | Item | The decision that must be made first | Why it cannot be improvised |
|---|---|---|---|
| B-1 | **JDBC driver** — the largest gap between the word "SQL database" and the code (doc 27; assessment §3) | Which subset: `Driver` registration, `Connection`, `Statement`, `PreparedStatement`, `ResultSet`, `DatabaseMetaData`? What type mapping? How do transactions bridge the core transaction layer? What is *honestly* declared unsupported? | JDBC is a very large interface family. A partial driver that declares its subset is valuable; one that pretends to be complete is the fake-success defect class at API scale |
| B-2 | **Constraint enforcement** — PK / FK / unique / check / not-null | Where constraints live (catalog vs. per-collection metadata), when they are enforced (write path vs. validation hook), and what error contract violations produce | Enforcement in the engine (not application code) is what makes "relational" honest; retrofitting after the document store becomes the de-facto catalog is far more expensive |
| B-3 | **Module split** into per-engine artifacts (`junify-db-sql`, `-nosql`, `-storage-*`, `-console`) | Whether the split is worth breaking coordinates and consumer imports for (ADR-001 deliberately deferred it) | High blast radius, invalidates demo/e2e verification, zero functional gain at this moment |
| B-4 | **Query planner + `EXPLAIN`** | Whether to introduce a plan representation and a cost model, or remain interpretive with better diagnostics | A planner is a subsystem, not a patch; the honest alternative is to keep documenting that execution is interpretive |
| B-5 | **Vector model as first-class** (not an auxiliary index) | Whether vectors become a supported model with its own guarantees (durability, recall, sizing) or stay an index feature | Today it must not be marketed as a vector database (assessment §3.1) |
| B-6 | **Transactional console writes** (R-60) | Whether REST/console write paths accept a `transactionId` and route through `ActiveTx.documentCollection`, or whether the console stays lifecycle-only | Half-transactional tooling is worse than none: the console currently offers begin/commit over writes it does not govern. Labelling removed the false impression; wiring needs a decision on response semantics (what does a write return before commit?), read visibility inside an open transaction, and bulk/SQL scope |
| B-7 | **Durable empty collections / `CREATE TABLE` existence** (R-62) | **DECIDED and implemented 2026-09-23:** a single SPI hook `StorageEngine.ensureCollection(name)` (default: no-op returning `false`) invoked at creation time only. Per engine: FILE writes an empty `{}` snapshot (its snapshots *are* its collection identity), LSM_TREE/B_TREE write a `.collections` registry atomically (their identity is otherwise derived from `collection:key` records, so an empty collection has no key), IN_MEMORY lists it for the process lifetime with no durability claim. Rejected alternative: declaring DDL existence session-scoped, which would have kept `CREATE TABLE` reporting success for state that will not persist | `CREATE TABLE` reported success for a collection that never entered the engine's `store`, so an empty table silently vanished on restart while rows survived. Fixed and verified live: empty table → `{}` on disk → survives restart with `count:0`, `SELECT` answering `rowCount:0` instead of *"Table does not exist"* (register R-62) |
| B-9 | **`--sync`/`--async` semantics** (R-67) | Whether `--sync` becomes flush-on-write (per write, with the WAL story per engine to match) and `--async` becomes the periodic flusher, or the flags are renamed to describe what they actually do (`--flush-interval`, `--no-scheduler`) | Today the flag is passed to the engines as `asyncEnabled`, so `--sync` enables a *background* flusher and `--async` disables it — while the banner prints "Flush mode: sync\|async" for all engines. `LSM_TREE`/`B_TREE` ignore it entirely. Renaming is the honest cheap option; changing the semantics alters the durability contract and needs its own evidence |
| B-8 | **Collection-level delete / `DROP TABLE` semantics** (R-63) | Whether a collection drop is a core operation that also discards its indexes, TTL state, vectors and attached KV structures — and whether `DROP TABLE` should mean "empty the table" or "remove the collection" | `DELETE /api/collections/{name}` is unimplemented (405 on existing collections) and `DROP TABLE` leaves the collection behind, so catalogs can only be pruned outside the API. No false claim is being made; the decision is what the destructive semantics should be. **Note (2026-09-23):** the workaround that existed in practice is now gone in the other direction — after R-62, a collection created by mistake (R-64's typo, a stray `CREATE TABLE`) is durable on every engine, so collection pruning is a real operational gap rather than a curiosity |

## Bucket C — Must be deferred (post-release)

Real, valuable, and explicitly out of scope for the first public release.

| ID | Item | Why deferred |
|---|---|---|
| C-1 | Maven Central publication | External: no credentials/signing key (R-13); Central must not be claimed (doc 46 MC-02 stays `NOT VERIFIED`) |
| C-2 | R-20 mixed-writer limitation | Fundamental to the storage design; documented, not fixable by a patch |
| C-3 | WAL for `B_TREE` | **CLOSED 2026-09-23 (R-69) — no longer post-release.** R-68's flusher narrowed the loss window but did not remove it: an acknowledged write still lived only in `ramIndex` until the next flush, and the gate measured **12 documents accepted, 3 readable after a forced stop**. `BTreeEngine` now uses the shared `WriteAheadLog` (write-ahead, then apply), replays it after loading the index, and checkpoints only after a successful persist — so all three persistent engines make the same guarantee, and the gate verifies it live (12/12 with full bodies) |
| C-7 | WAL truncation / archived-segment recovery (R-66) | **CLOSED 2026-09-23 (R-66)** — and it was worse than "not verified". Measured: rotation dropped **53 of 60** accepted records (the writer was closed and only reopened by the archiver), archived segments were never read so recovery replayed **0 of 40**, the log never shrank (`truncate()` had zero callers), and a half-written `.gz` under a recoverable name could lose the entire log (**0 of 6**) because one unreadable segment aborted the whole replay. All fixed; fixed-order chronological reading with per-segment isolation; 4 regression tests, **falsified 4/4 at `6d9233`**. The truncation itself then exposed R-70 (a checkpoint could release a record whose apply was in flight) and R-73 (LSM `get()` ignoring a newest-SSTable tombstone), both fixed |
| C-4 | Stress / soak benchmarking programme | Value is in long-run confidence, not in the release gate (doc 24) |
| C-5 | Sequences, views, stored procedures, triggers, `ALTER`, `CREATE INDEX` | Large SQL-dialect surface; each needs its own design, tests, and honest documentation |
| C-6 | Vector index durability for `IN_MEMORY` | `IN_MEMORY` promises no durability by design; revisit only with B-5 |

---

## Sequencing

1. **Freeze scope for the tag.** Bucket A-3/A-7 plus the release mechanics (ADR-007/ADR-008).
2. **Tag `v0.9.0`** from GitHub Releases with the limitation list, and nothing in that list
   may be contradicted by the artifact.
3. **Then** open B-1 (JDBC) as the first post-release design, because it unlocks the largest
   honest capability gain; B-2 next, because "relational" without constraints is the claim
   this audit most wants to be able to make truthfully.
4. Bucket C items are picked up as capacity allows and require no pre-work.

## Explicitly rejected for now

- Splitting modules before the first public tag (B-3): cost without benefit, and it would
  invalidate the demo/e2e verification the release rests on.
- Publishing to Maven Central without a verified staging run (C-1): the one thing this audit
  will not allow is an unbacked distribution claim.
- Adding any Console control for a capability that is not implemented — the fake-functionality
  rule that produced most defects in this session.

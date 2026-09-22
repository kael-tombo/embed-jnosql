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
| A-4 | README install snippet + explicit "not on Maven Central" note | Documentation; the release path is already decided (ADR-007) | OPEN — do with the tag |
| A-5 | Playwright UI flow suite (was P1-5) | Purely additive test coverage over the Console | **OPEN** |
| A-6 | README screenshots (was 39-WS-03) | Assets only | **OPEN** |
| A-7 | Re-run all demos and record output before the tag | Verification, not code | OPEN — pre-tag |

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

## Bucket C — Must be deferred (post-release)

Real, valuable, and explicitly out of scope for the first public release.

| ID | Item | Why deferred |
|---|---|---|
| C-1 | Maven Central publication | External: no credentials/signing key (R-13); Central must not be claimed (doc 46 MC-02 stays `NOT VERIFIED`) |
| C-2 | R-20 mixed-writer limitation | Fundamental to the storage design; documented, not fixable by a patch |
| C-3 | WAL for `B_TREE` | The no-WAL behaviour is the documented D-02 limitation and is verified to behave as documented |
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

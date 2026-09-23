# 60 — Public Release Checklist

## Scope
Objective, checkboxed verification for the public GitHub release. Version and release path
are set by the canonical decision, **`final-go-no-go-decision.md`**: recommended version
**`v0.9.0`**, published from **GitHub Releases** with the limitation list (ADR-007/ADR-008).
The older `1.0.0` framing in the checklist lines below is preserved as history.

## Checklist (state at audit close)
### Code & Tests
- [x] Clean-clone `mvn clean test` green — **669 baseline / 677 post-fix, 0 failures** (`.freebuff/baseline-build.log`, `.freebuff/postfix-full-suite2.log`)
- [x] Critical durability/conflict defects fixed **with pre-fix failing evidence** (`.freebuff/prefix-failures.log`)
- [x] New regression suite `ReleaseAuditRegressionTest` (8 tests) in CI path
- [x] No test asserting the buggy behavior remains (updated where semantics corrected)
- [x] **All 9 demos re-run on the fixed build — 43/43 tests green, 0 failures (2026-09-22, from a clean local `mvn install`; per-demo table and the R-59 defect this run caught are in 35)** ← P0-2 CLOSED
  - The earlier 2026-09-21 demos run also reported 43/43, which is exactly why the run was repeated before the tag: the older suite passed while a real catalog defect (R-59) was present but unexercised. Re-running from a clean install is the check that keeps this item meaningful
- [x] **Reproducibility gate in CI** (2026-09-22): `scripts/reproducibility-check.sh` builds twice from clean and requires byte-identical artifacts, wired in as the CI `reproducibility` job; verified PASS (`sha256 e7fe3558…93f18`) and falsified to FAIL with the timestamp property removed
- [x] **Core suite after the WAL/checkpoint round (R-66/R-69/R-70/R-71/R-72/R-73): 832/832** (+4/4 CLI); contract gate PASS on FILE, LSM_TREE and B_TREE (**92 ok / 0 FAIL** each), now including a crash-durability block — 12 × 256 KB documents, a **forced** stop (`kill -9` cannot signal a native JVM from Git Bash; `taskkill` can, and the gate now proves the port stopped answering before it claims a restart), and after restart **12 of 12 documents with full bodies**, each engine reporting its own WAL replay. That block was itself the finding: the previous round's "restart" assertions were answered by the process they were supposed to replace, so this round's numbers are the first measured ones. Also in this round: `B_TREE` gained a WAL (it acknowledged writes it never persisted — 12 accepted, 3 surviving), the index is published atomically and read as a stream (the old reader desynchronised at 1 MB boundaries: 12 documents on disk, 3 readable, a torn file accepted silently), and (log + apply) now share one critical section with (persist + checkpoint) so a checkpoint cannot release a record whose value is not on disk. Demos **43/43** across nine demos; register R-66/R-69..R-73
- [x] **Core suite re-run after the R-61 change: 801/801** (+4/4 CLI), contract gate PASS on FILE, LSM_TREE and B_TREE (now including three CORS-policy assertions), auth gate PASS, reproducibility gate PASS (identical jar hashes across two clean builds)
- [x] **Core suite after R-62/R-64/R-65/R-68: 821/821** (+4/4 CLI); contract gate PASS on FILE, LSM_TREE and B_TREE **with a new restart block** that creates an empty table, restarts the server on the same data dir and requires the table, its `0`-row `SELECT`, the row counts and the documents endpoint to agree afterwards — the check that found **R-68** (B_TREE lost every record written since startup, `.btree/` empty on disk) and that falsifies R-62/R-65 at the consumer level (pre-fix jar: `the empty table vanished across the restart: {"collections":[]}`); auth gate PASS; reproducibility gate PASS (`sha256 7c8b691e…`, 3,119,376 bytes); four defects fixed, each with pre-fix falsification and live verification (register R-62/R-64/R-65/R-68)
- [x] CHANGELOG amended with audit fixes (P0-3) — committed `ed6da57`
- [x] CI Docker job removed; benchmark job mainClass fixed (P0-4) — committed `ed6da57`

### Documentation & Claims
- [x] README claims verified/bounded/removed (43 verification log)
- [x] Vision-doc contradiction annotated (02)
- [x] Honest engine/durability table (16)
- [x] ROADMAP refresh (R-23) — done 2026-09-21, round 1
- [x] demo/README prerequisites (P1-6) — done 2026-09-21, round 1
- [x] LICENSE/CONTRIBUTING/SECURITY present and consistent

### Security
- [x] Bind-host default 127.0.0.1 verified (22)
- [x] Auth enforcement tests green (22)
- [x] No secrets in repo (54)
- [x] CVE scan job (P2-4) — done 2026-09-21, round 1 (OWASP dependency-check, failBuildOnCVSS=9)

### Release Mechanics
- [x] Repo litter removed ($null/server.*)
- [x] `mvnw` shell script (P1-3) — done 2026-09-21, round 1 (smoke-tested; CI dogfoods it)
- [ ] GitHub Release: tag **`v0.9.0`** and attach the core jar — **pending**. The earlier `v1.0.0` draft note here is unverified in this environment and is superseded by the canonical decision; the current artifact is `target/junify-db-core-1.0.0.jar` at **3,119,376 bytes (3.12 MB)** (`sha256 7c8b691e…`), size-gated (the shade step replaces the plain jar in place). The earlier "2.89 MB" and "3.11 MB" figures were stale
- [x] Recommended version: **`v0.9.0`** per `final-go-no-go-decision.md` (was `1.0.0` / 47-VER-02 — revised because a 1.0 database is expected to ship JDBC and constraint enforcement, which this build does not)
- [ ] Maven Central: blocked until 46 items (P2-1) — GitHub-first release is valid without it

### Console
- [x] 13 panels validated (58 matrix)
- [x] Zero browser console errors (37)
- [ ] Playwright flow suite (P1-5)
- [ ] README screenshots (39-WS-03)

## Final Status
**PASS** — all P0 pre-tag items closed. The remaining open boxes (Playwright UI suite, README screenshots, GitHub Release mechanics, Maven Central staging) are P1/P2 post-release items per `59-prioritized-fix-roadmap.md` and do not gate the tag. Improvement rounds 1–2 closed every engine/CI/docs item; see 64 and 65 (rounds 1–2, renumbered) and 66 / 63 (round 3) for evidence.

**Version gate (2026-09-22):** tag **`v0.9.0`** and publish from GitHub Releases with the
explicit limitation list — the release decision is `final-go-no-go-decision.md`, and
**Maven Central is not claimed** (doc 46 MC-02 stays `NOT VERIFIED`; R-13 is the missing
credential, not missing work).

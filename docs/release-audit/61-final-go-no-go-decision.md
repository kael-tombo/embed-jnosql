# 61 — Final Go / No-Go Decision (SUPERSEDED)

> **SUPERSEDED — historical record.** Superseded by `62-…`, then `63-…` (round 3), and now by
> the canonical **`final-go-no-go-decision.md`** (recommended version `v0.9.0`, GitHub-first
> release). Kept because its per-finding evidence is still cited. **Do not use this file as
> the release decision.**

## Scope
The evidence-based release decision for EmbedJNoSQL 1.0.0, applying the release-blocker policy to every finding in this audit.

## Decision Basis (strongest evidence)
1. **The worst problems were real, and they are now fixed with proof.** Four critical/high correctness defects — unreachable write-write conflict detection, FileEngine WAL never replayed, LSM WAL replay skipped whenever SSTables existed, collections invisible after restart — were each demonstrated failing against pre-fix HEAD (`.freebuff/prefix-failures.log`: `Tests run: 4, Failures: 4` with explicit data-loss assertions like `expected <{"total":42}> but was <null>`), fixed minimally, and locked in by `ReleaseAuditRegressionTest` (8 tests, green).
2. **The full suite is green and got stronger**: 669 tests baseline → 677 post-fix, 0 failures, 0 skipped; one pre-existing test that passed for the wrong reason now asserts correct behavior.
3. **Every public claim was verified, bounded, or removed** (43 verification log): ANSI→dialect, tamper-evident→in-memory ring, measured→indicative, page-store→heap-resident truth, <15 ms→millis. The durability claim, previously false, is now true and regression-tested.
4. **The console was validated end-to-end** against the real backend across all 13 panels (58 matrix) with zero browser errors, and the security posture (loopback default, opt-in key auth, loud warnings, header set) was source-and-test verified (22).
5. **Known gaps are documented, not hidden**: starters lack CI (44), Central publication lacks plugins+staging proof (46), CDC has no producer (23), screenshots unavailable in this environment (38) — each carries a disposition and a scheduled fix.

## Why GO
The three P0 conditions from the audit (60-checklist) are now closed with evidence:
1. **All 9 demos re-executed on the fixed build** — 43/43 demo tests green, 0 failures/errors/skipped, including real framework boots (Spring Boot 3.2.5 context, Quarkus 3.8.0 started in 4.38s) and the live stress harness (50 threads, 18,382 ops/sec, 0 failures). Per-demo table and evidence: `35-demo-project-audit.md`.
2. **CHANGELOG amended** with the audit fixes (commit `ed6da57`).
3. **CI Docker job removed** (no Dockerfile exists) and the benchmark job's wrong mainClass fixed (commit `ed6da57`).

## Decision

**GO**

## Release Version
**1.0.0** (GitHub-first). Keep `1.0.0` with a CHANGELOG "Fixed" amendment for the audit fixes; Maven Central publication follows after 46's requirements (P2-1).

## Confidence
**Medium-High** — high for the audited core (engines, transactions, console, claims), medium for framework starters (functional evidence, CI pending) and for claims involving environments not exercised here (CVE posture, JMH results, screenshot-based UI review).

## Evidence Summary
- Clean baseline: `mvn clean test` → 669/669 green, then fixes → 677/677 green (logs preserved).
- Pre-fix failure proof on clean HEAD worktree: 4/4 behavioral tests fail exactly as diagnosed.
- Regression suite added and green; SPI/API changes additive-only (pre-existing direct callers compile unchanged).
- Live console validation across all panels; multiple server restarts with data recovery.
- Claims audit: 8 public claims corrected with per-claim verification trails.

## Critical Blockers
**None open.** (All four critical findings R-01..R-03 + R-04/R-05/R-06 fixed with evidence.)

## Required Pre-Release Fixes (P0, mechanical) — ALL COMPLETE
1. ~~Re-run all demos on the final build and record output~~ ✅ 2026-09-21: 43/43 green (35).
2. ~~Amend CHANGELOG with the audit fixes~~ ✅ commit `ed6da57`.
3. ~~Remove or supply the missing CI `Dockerfile` job~~ ✅ commit `ed6da57` (also fixed benchmark mainClass).

## Safe Post-Release Improvements
Coverage-gate enforcement, starter/demo CI matrix, `mvnw` script, REST API reference, Playwright UI suite, demo README prerequisites, ROADMAP refresh, Maven Central plugins+staging, CDC decision (wire-or-remove), typed exceptions, CVE scanning, index-hint planning, configurable HNSW dims, snapshot quarantine, crash-injection CI (59-P1/P2).

## Verified Capabilities (evidence-backed, safe to claim)
- Embedded zero-config start; multi-model NoSQL (Document, KV, List, Set, Hash, Column) — full test traceability (55).
- Built-in SQL dialect: SELECT/INSERT/UPDATE/DELETE, JOIN, GROUP BY, LIKE/BETWEEN/IN, parameter binding, entity mapping (08).
- MVCC snapshot isolation with working first-writer-wins conflict detection (13).
- WAL-based crash recovery on FILE and LSM engines with fsync'd writes (15).
- Four storage engines with accurately documented durability (16).
- Redis-style KV structures; TTL; secondary indexes; text search (55).
- Multi-model web console with API-key auth, loopback default, security headers (36/37/38/22).
- JNoSQL-style + JPA-style annotation mapping (26).
- 677 automated tests, zero failures.

## Unsupported or Unverified Claims (removed/never-made)
- "ANSI SQL" (dialect only) — removed from README.
- "Tamper-evident audit log" — reworded to in-memory ring.
- "< 15 ms startup" / "Measured — Not Estimated" benchmarks — reworded to indicative with source harness.
- "B-Tree page store" / >RAM datasets — corrected: all engines heap-resident.
- "Production-grade" superlatives — removed.
- JNoSQL/JDBC/Testcontainers compatibility — never claimed; absences documented (26/27/34).
- CDC change streams — not claimed anywhere.

## Final Release Checklist
- [x] Clean-clone build + full suite green (677/677)
- [x] Critical defects fixed with pre/post evidence
- [x] Claims audit complete; corrections live in README
- [x] Console validated end-to-end; security posture verified
- [x] Demos re-run on final build — 43/43 green (P0-2, 2026-09-21)
- [x] CHANGELOG amendment (P0-3, commit `ed6da57`)
- [x] CI Docker job removed/fixed (P0-4, commit `ed6da57`)
- [ ] Tag `v1.0.0`, GitHub Release with shaded jar + audit docs link
- [ ] (Post-release) Maven Central path per 46

**All P0 gates cleared. The release is GO — remaining items are release mechanics (tag + GitHub Release) and post-release improvements.**

# 60 — Public Release Checklist

## Scope
Objective, checkboxed verification for the public GitHub release of 1.0.0.

## Checklist (state at audit close)
### Code & Tests
- [x] Clean-clone `mvn clean test` green — **669 baseline / 677 post-fix, 0 failures** (`.freebuff/baseline-build.log`, `.freebuff/postfix-full-suite2.log`)
- [x] Critical durability/conflict defects fixed **with pre-fix failing evidence** (`.freebuff/prefix-failures.log`)
- [x] New regression suite `ReleaseAuditRegressionTest` (8 tests) in CI path
- [x] No test asserting the buggy behavior remains (updated where semantics corrected)
- [x] **All 9 demos re-run on the fixed build — 43/43 tests green, 0 failures (2026-09-21; see 35 for the per-demo table and evidence)** ← P0-2 CLOSED
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
- [x] GitHub Release draft: tag `v1.0.0`, attach core jar (2.89 MB, size-gated), link audit docs (created 2026-09-21 after run #34 full green; jar is `junify-db-core-1.0.0.jar` — the shade step replaces the plain jar in place)
- [x] Recommended version: **1.0.0** (47-VER-02)
- [ ] Maven Central: blocked until 46 items (P2-1) — GitHub-first release is valid without it

### Console
- [x] 13 panels validated (58 matrix)
- [x] Zero browser console errors (37)
- [ ] Playwright flow suite (P1-5)
- [ ] README screenshots (39-WS-03)

## Final Status
**PASS** — all P0 pre-tag items closed. The remaining open boxes (Playwright UI suite, README screenshots, GitHub Release mechanics, Maven Central staging) are P1/P2 post-release items per `59-prioritized-fix-roadmap.md` and do not gate tagging `v1.0.0`. Improvement rounds 1–2 closed every engine/CI/docs item; see 64 and 65 (rounds 1–2, renumbered) and 66 / 63 (round 3 + current final decision) for evidence.

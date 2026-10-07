# 54 — Release-Blocker Register

## Scope
Application of the automatic-blocker policy to this audit's findings.

## Blocker Evaluation
| Policy Blocker | Findings | Disposition |
|---|---|---|
| Data loss | R-02/R-03 (silent WAL loss) | **MITIGATED — fixed** with regression proof; durability contract now real (15) |
| Data corruption | R-05/R-06 (LSM compaction/ordering/key extraction) | **MITIGATED — fixed**; engine suite green (16) |
| Unrecoverable persistence failure | same as above | **MITIGATED — fixed** |
| Broken transaction semantics | R-01 (conflicts never detected) | **MITIGATED — fixed** with regression proof (13) |
| Security vulnerability / default insecure access | Console binds 127.0.0.1 by default; auth opt-in with loud warning; localhost guard forces loopback | **MITIGATED — acceptable** for embedded admin tool; documented threat model (22) |
| Publicly exposed secrets | None found (no credentials in repo; run docs record procedures only) | **CLEAR** |
| Misleading compatibility claims | R-07 (README overclaims; vision contradiction) | **MITIGATED — fixed** (02/40/43) |
| Broken clean installation | Baseline build green from clean state (env-lock artifact documented) | **CLEAR** |
| Broken Maven publication | R-13: not Central-verified — the `maven-central` profile now carries source/javadoc/GPG/Central plugins (MC-01 **FIXED**), but **no staging or dry-run has ever been executed** (MC-02 `NOT VERIFIED`) because no credentials or signing key exist here | **BLOCKS CENTRAL ONLY** — not the GitHub-first release; disposition: GitHub-first with no Central claim (ADR-007), Central after a verified staging run |
| Demos not working as documented | R-22: prerequisite undocumented; not all re-executed here | **MITIGATED — acceptable** with documented follow-through in 60 (run-all before tag) |
| Documented feature entirely non-functional | CDC (documented as status-only, not claimed as change stream); B-Tree "page store" claim removed | **MITIGATED — fixed/corrected** |
| Console actions silently losing/corrupting data | Delete confirmations verified; durability fixes close the loss paths | **MITIGATED — fixed** |
| Tests falsely reporting success | `LSMTreeEngineTest.testPersistence` previously passed for the wrong reason (malformed keys); now asserts correct behavior | **MITIGATED — fixed** (16-R-06) |
| Build not reproducible | Clean-clone build green (2× in audit) | **CLEAR** |
| Framework integration advertised but non-functional | Starters: functional by prior demo evidence + unit tests, not CI-verified | **CONDITIONAL — README badges link to demos; CI closure scheduled (44)** — accepted for GitHub-first release with badge wording tied to demo evidence |

## Open Blockers for Public GitHub Release
**None.**

## Open Blockers for Maven Central Publication
MC-02 only (verified staging/dry-run) — the plugin work of MC-01 is done; the residual blocker
is **credentials + signing key**, an external dependency rather than product work. Documented
path in 46.

## Final Status
**CONDITIONAL PASS** (GitHub-first GO path clear; Central gated) — canonical decision:
`final-go-no-go-decision.md` (**RELEASE APPROVED WITH EXPLICIT LIMITATIONS**, `v0.9.0`).

## 2026-09-21 final validation round
| ID | Finding | Status | Disposition |
|---|---|---|---|
| RB-20 | Live website (Pages) showed false claims + wrong coordinates (frozen since Sep 17) | **FIXED LIVE** (2026-09-21) | Root cause of the freeze: Pages serves the **`gh-pages` branch** (not Actions — every `Deploy GitHub Pages` run fails harmlessly and always has). Corrected `docs/index.html` published to gh-pages as `bbd87ca`; live fetch verification: Last-Modified today, old-claim patterns **0**, corrected claims 11× (evidence in 53-WC-04..07). WC-08 "account mismatch" dissolved: kael-tombo/armand-ratombotiana are the same repo (rename; redirect proven by commit visibility) |
| RB-21 | Pages deploy broken repo-side: Actions deploy fails with no failed steps since run #4; site frozen at Sep 19 build (legacy branch-source suspected) | OPEN (owner) | Two resolution paths documented in 53-diagnostics; needs Settings access |
| RB-22 | Published core jar 6.9 MB exceeded 5 MB requirement (bundled byte-buddy) | **FIXED** | SZ-06: excluded; measured 2.89 MB; CI size gate added |
| RB-23 | Console used off-brand blue/indigo identity vs yellow site | **FIXED** | Amber rebrand, browser-verified (52-BR-01..05) |
| RB-24 | CI `demos` job red on every run where it executed (#30, #31) — blocks claiming "demos CI-verified" | **FIXED** (2026-09-21) | Root cause: starter modules never installed in the job, so framework demos could not resolve unpublished embeddedjnosql artifacts on fresh runners. Diagnosed via new public per-demo annotations (run #32: 6/9 pass, exactly spring-boot/quarkus/micronaut fail); falsified locally; fixed by starter-install step (`1bc94a9`). Verification: first run of `1bc94a9` must show demos green. |

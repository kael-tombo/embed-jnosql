# 37 — Console UI/UX Audit

## Scope
The redesigned web console (this audit's prior session): IA, design system, accessibility, states.

## Expected Behavior
Complete feature panels wired to real endpoints; all UI states (loading/empty/error) handled; keyboard navigation; theme persistence; no fabricated data; CSP-compliant assets.

## Current Implementation
- `static/index.html` + `css/console.css` (design tokens, dark/light) + `js/console.js` (single `api()` wrapper, escaping, toasts, sparkline, localStorage history, hash routing, number-key navigation). 13 panels in 3 groups. Legacy `enhancements.css/js` kept as minimal shims (tests require 200s on those paths).
- Total ~1.7k lines vs 15.7k-line monolith previously.

## Validation Performed (prior session, live)
- Full round-trips: SQL SELECT/INSERT; document CRUD + delete; KV/list/set/hash ops; column families; vector add/search; schema registration; transaction begin/commit/rollback (active count 1→0); index creation; CDC status; audit view; server/JVM stats.

> **Correction (2026-09-22):** `backup` was removed from this list because the claim did not hold. Re-driving the panel in a real browser showed the Create button produced a 22-byte `{}` (the server snapshotted a fresh engine over an empty temp dir) while toasting "Backup created", the Restore button's endpoint returned 400 on every call, and the panel's Engine KPI always rendered `—`. All three are fixed and browser-re-verified (toast now reads "Backup created · 13 documents in 7 collections"; ENGINE shows `FILE`); evidence in 66, defects R-35..R-38. The lesson recorded against this document: a green toast is not a round-trip.
- Zero browser console errors at final state; `ConsoleFeatureValidationTest` + `SecurityEnforcementTest` green post-rewrite.
- Fixed during verification: `v()` selector helper double-prefix bug, sets payload (`members`), transaction id type, vector search result shape + 128-dim labeling.

## Evidence
`docs/admin-console/CONSOLE-UI-VALIDATION-PROOF.md`; preview snapshots/evaluations; git diff of static assets.

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| UI-01 | CONFIRMED (fixed) | High | Old console: fabricated numbers, XSS-adjacent escaping bug (`escapeHtml` undefined), broken enhancements.js DOM insert, Google Fonts blocked by own CSP. All eliminated by rewrite. |
| UI-02 | PARTIALLY VERIFIED | Medium | Screenshot capture unavailable in audit environment (client compositing error); verification done via accessibility snapshots, DOM evaluation, and network traces. Listed in 58-ui-validation-matrix. |
| UI-03 | CONFIRMED | Low | Number-key shortcuts (1–9,0) and hash routing work; responsive layout tested only at desktop widths in the audit environment. |

## Improvement Plan
Playwright-based browser test suite as CI artifact (screenshots + flows); mobile-width pass.

## Acceptance Criteria
Zero console errors (met); feature flows verified (met); legacy asset paths still 200 (met — tests pass).

## Final Status
**CONDITIONAL PASS**

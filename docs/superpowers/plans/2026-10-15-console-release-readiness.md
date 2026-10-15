# Console Release Readiness Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Run the complete agreed validation set and publish concise, reproducible readiness evidence with all remaining gaps disclosed.

**Architecture:** Consume the inventory, theme, Playwright, compatibility, and branding artifacts. Rerun relevant verification after integration, assemble actual results into a traceability matrix and release report, and avoid changing behavior merely to improve reported totals.

**Tech Stack:** Maven Wrapper, Playwright, Chromium, Markdown, Git.

**Spec:** [2026-10-15-production-ready-console-design.md](../specs/2026-10-15-production-ready-console-design.md)

## Global Constraints

- Capture screenshots or other reproducible evidence without credentials.
- Do not infer runtime support from a successful build.
- Reports must distinguish implemented, tested and verified, partially verified, and not verified.
- Do not fabricate results or suppress failures/skips.
- Do not include credentials or sensitive values in artifacts.
- Existing user changes in the working tree must be preserved and integrated carefully.
- Do not stage or commit pre-existing user changes; inspect the staged diff before every commit.

## Review Focus

1. A test count copied from an earlier baseline — rerun commands and record only current output in `release-readiness.md`.
2. A skipped test mistaken for a pass — report skipped counts and reason separately.
3. A screenshot or log containing credentials — inspect evidence and redact/remove sensitive content before commit.
4. A browser test that asserts rendered controls but not engine state — identify it in traceability as UI-only or add state verification.
5. A framework claim without live validation or a visual asset without permitted-use evidence — keep it partial/unverified and disclose it.

---

### Task 1: Completing the Traceability Matrix

**Files:**
- Create: `docs/console-readiness/traceability.md`
- Inspect: `docs/console-readiness/feature-inventory.md`
- Inspect: `docs/console-readiness/user-stories.md`
- Inspect: Playwright tests and result output.

**Interfaces:**
- Consumes: Final story IDs, acceptance criteria, named tests, and actual Playwright results.
- Produces: One row per acceptance criterion with feature/story/test/result/evidence and explicit coverage gaps.

- [ ] **Step 1: Map each acceptance criterion**

Use exact Playwright test names and report paths. Mark non-browser tests as such; do not label static HTTP checks as Playwright.

- [ ] **Step 2: Validate completeness**

Expected: every story criterion maps to an executed test or a documented, justified limitation; no fabricated pass state.

### Task 2: Running Final Validation and Writing the Report

**Files:**
- Create: `docs/console-readiness/release-readiness.md`
- Inspect: all files under `docs/console-readiness/`
- Inspect: `tests/e2e/README.md`

**Interfaces:**
- Consumes: Baseline, feature/story inventory, traceability, framework matrix, and test outputs.
- Produces: Evidence-based executive summary, commands, exact pass/fail/skip counts, screenshots/log locations, unresolved risks, blockers, and next steps.

- [ ] **Step 1: Rerun focused console tests**

Run:

```powershell
.\mvnw.cmd '-Dtest=AdminConsoleConfigTest,ConsoleBackupEndpointTest,ConsoleCollectionResolutionTest,ConsoleEngineIntegrationExtensionTest,ConsoleFeatureValidationTest,ConsoleIndexAndCdcEndpointTest,ConsoleIndexRouteExistenceTest,ConsoleQueryEndpointTest,ConsoleTaskSuccessTest,ConsoleTransactionAndBulkTest,ConsoleVectorSearchTest,ConsoleWorkspaceEndpointsTest,CorsPolicyConsistencyTest,PortManagementTest,SecurityEnforcementTest' test
```

Expected: record actual final aggregate and per-class totals.

- [ ] **Step 2: Rerun the full browser suite and reviewed visual checks**

Run: `npm --prefix tests/e2e run test`

Expected: exact pass/fail/skip totals, deterministic screenshots, and no unreviewed snapshot updates.

- [ ] **Step 3: Rerun each framework validation command**

Use the exact module/demo commands recorded in `framework-compatibility.md`. Verify no stale service is mistaken for the launched demo and record runtime versions and blockers.

- [ ] **Step 4: Write `release-readiness.md`**

Report delivered/verified/partial/not verified status for each goal, exact commands/results, links to evidence, remaining defects/risks, environmental and licensing limits, and recommended follow-up.

- [ ] **Step 5: Review artifact hygiene and user changes**

Run `git diff --check`; inspect `git status --short` and `git diff --cached`. Remove only named, task-generated temporary files under `target` or `tests/e2e/test-results`; do not touch unrelated user content.

- [ ] **Step 6: Commit only readiness artifacts**

```powershell
git add -- docs/console-readiness/traceability.md docs/console-readiness/release-readiness.md
git commit -m "docs: report console release readiness"
```

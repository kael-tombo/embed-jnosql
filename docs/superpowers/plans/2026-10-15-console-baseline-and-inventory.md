# Console Baseline and Feature Inventory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish a reproducible baseline and a complete, source-anchored inventory of console features, stories, test gaps, and initial defects.

**Architecture:** Inspect the shipped static UI, Java HTTP handlers, engine operations, tests, demos, and documentation. Record findings as linked Markdown deliverables without changing product behavior.

**Tech Stack:** Java 17, Maven Wrapper, vanilla ES2020, JUnit 5, Markdown.

**Spec:** [2026-10-15-production-ready-console-design.md](../specs/2026-10-15-production-ready-console-design.md)

## Global Constraints

- Existing user changes in the working tree must be preserved and integrated carefully.
- Treat source code, build configuration, and executable behavior as authoritative.
- Mark implementation, documentation, experimental status, and verification independently.
- Reports must distinguish implemented, tested and verified, partially verified, and not verified.
- Do not include credentials or sensitive values in artifacts.
- Do not stage or commit pre-existing user changes; inspect the staged diff before every commit.

## Review Focus

1. A UI affordance without an engine/API effect — trace each action to a handler or mark its behavior unverified in `feature-inventory.md`.
2. A feature shown as working based only on a passing compile — distinguish static, HTTP, live-browser, and persisted-state evidence in `assessment.md`.
3. Authentication or CSRF blocking a workflow — record role/precondition and existing security test coverage for every protected action.
4. Empty, invalid, timeout, and recovery behavior absent from a happy-path story — include these alternatives in `user-stories.md` or mark the scenario not applicable with evidence.
5. A documented framework with no maintained integration or runnable demo — flag it in the inventory and do not count it as verified support.

---

## Plan Dependencies

Run the baseline/inventory plan first. Then run Task 1 (Playwright harness) from the Playwright plan so the theme plan can write and execute browser regression tests. Complete the theme plan next, followed by Playwright Tasks 2–6, framework compatibility, branding reconciliation, and release readiness. For tracked files already modified before this work, stage only reviewed task hunks with a cached patch; never stage the entire file or a broad asset directory.

### Task 1: Recording the Console Baseline

**Files:**
- Create: `docs/console-readiness/baseline.md`
- Inspect: `pom.xml`
- Inspect: `src/test/java/org/embeddedjnosql/db/ConsoleFeatureValidationTest.java`
- Inspect: `src/test/java/org/embeddedjnosql/db/ConsoleTaskSuccessTest.java`
- Inspect: `src/test/java/org/embeddedjnosql/db/ConsoleBackupEndpointTest.java`
- Inspect: `src/test/java/org/embeddedjnosql/db/ConsoleQueryEndpointTest.java`
- Inspect: `src/test/java/org/embeddedjnosql/db/ConsoleTransactionAndBulkTest.java`
- Inspect: `src/test/java/org/embeddedjnosql/db/ConsoleVectorSearchTest.java`

**Interfaces:**
- Consumes: The current uncommitted repository state; do not reset, stash, or alter unrelated files.
- Produces: A dated baseline record of exact commands, pass/fail/skip counts, and environmental blockers.

- [ ] **Step 1: Run the focused console backend baseline**

Run in PowerShell:

```powershell
.\mvnw.cmd '-Dtest=AdminConsoleConfigTest,ConsoleBackupEndpointTest,ConsoleCollectionResolutionTest,ConsoleEngineIntegrationExtensionTest,ConsoleFeatureValidationTest,ConsoleIndexAndCdcEndpointTest,ConsoleIndexRouteExistenceTest,ConsoleQueryEndpointTest,ConsoleTaskSuccessTest,ConsoleTransactionAndBulkTest,ConsoleVectorSearchTest,ConsoleWorkspaceEndpointsTest,CorsPolicyConsistencyTest,PortManagementTest,SecurityEnforcementTest' test
```

Expected: Maven reports the per-class totals and an aggregate exit status. Previous observation was 110 passed, 0 failed, 0 skipped; record the new run rather than copying that result.

- [ ] **Step 2: Record exact results in `docs/console-readiness/baseline.md`**

Include runtime/tool versions, command, totals, failures, skips, and whether any output came from a live HTTP test or a static asset assertion. Do not paste secrets or raw authentication headers.

- [ ] **Step 3: Review baseline artifact**

Check that every reported number comes from the completed test output and that generated temporary classpath/evidence files are not included.

- [ ] **Step 4: Commit only the baseline artifact**

```powershell
git add -- docs/console-readiness/baseline.md
git commit -m "docs: record console readiness baseline"
```

Expected: the commit contains only the baseline report.

### Task 2: Inventorying Features and User Stories

**Files:**
- Create: `docs/console-readiness/feature-inventory.md`
- Create: `docs/console-readiness/user-stories.md`
- Inspect: `src/main/resources/static/index.html`
- Inspect: `src/main/resources/static/js/console.js`
- Inspect: `src/main/resources/static/js/login.js`
- Inspect: `src/main/java/org/embeddedjnosql/db/console/http/EmbedJNoSQLServer.java`
- Inspect: `src/test/java/org/embeddedjnosql/db/console/ConsoleInteractiveTestServer.java`

**Interfaces:**
- Consumes: `baseline.md` from Task 1 and the repository implementation.
- Produces: One inventory row and at least one independently verifiable story/use case per actual feature/action; IDs use `CONSOLE-###`.

- [ ] **Step 1: Enumerate actual console panels and controls**

Start from the `PANELS` array and all `panel-*` sections in `index.html`. Trace event wiring and API calls in `console.js` into the registered Java handlers. Include sign-in/session behavior, theme, navigation, refresh, dialogs, and destructive confirmations.

- [ ] **Step 2: Write `feature-inventory.md`**

For each item record purpose, route/panel, controls, preconditions, permission/CSRF requirements, API/engine effects, states, validation/recovery, accessibility/responsive expectations, dependencies, test references, and evidence status. Mark Vectors experimental as implemented by the source.

- [ ] **Step 3: Write `user-stories.md`**

For each independently testable feature, provide an ID/title, actor, preconditions, initial state, main scenario, alternatives, failure/recovery, UI outcome, API/engine interaction, observable evidence, related test or explicit gap, and acceptance criteria.

- [ ] **Step 4: Review inventory completeness**

Cross-check every navigation panel, every distinct API action wired by the UI, and every `ConsoleFeatureValidationTest` feature label against an inventory row. Expected: no unexplained panel or UI action is missing.

- [ ] **Step 5: Commit only the two inventory artifacts**

```powershell
git add -- docs/console-readiness/feature-inventory.md docs/console-readiness/user-stories.md
git commit -m "docs: inventory console features and stories"
```

### Task 3: Prioritizing Defects and Coverage Gaps

**Files:**
- Create: `docs/console-readiness/assessment.md`
- Inspect: `docs/console-readiness/baseline.md`
- Inspect: `docs/console-readiness/feature-inventory.md`
- Inspect: `docs/console-readiness/user-stories.md`

**Interfaces:**
- Consumes: Tasks 1–2.
- Produces: Prioritized, reproducible defect/gap list and recommendations linked to story IDs.

- [ ] **Step 1: Classify observed defects and gaps**

For each item record severity, exact reproduction, source-anchored root cause, proposed fix, verification method, and status. Separate confirmed defects from unverified behavior and design recommendations.

- [ ] **Step 2: Record framework claims as unverified leads**

Compare the README and website claims with repository modules only far enough to establish a verification queue. Do not label a framework compatible in this assessment without live validation.

- [ ] **Step 3: Run documentation consistency checks**

Run: `git diff --check -- docs/console-readiness`

Expected: no whitespace errors; every defect/gap references a feature or story ID.

- [ ] **Step 4: Commit only the assessment**

```powershell
git add -- docs/console-readiness/assessment.md
git commit -m "docs: prioritize console readiness findings"
```

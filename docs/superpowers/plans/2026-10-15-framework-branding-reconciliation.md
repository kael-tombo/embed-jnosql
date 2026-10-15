# Framework Branding Reconciliation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Present consistent, accurate framework names, support status, links, and permitted assets in the Console Help view, README, and project website.

**Architecture:** Use the completed compatibility matrix as the source for an explicit metadata manifest. Render its verified status compactly in the existing Help view, reconcile README/site claims and assets, and test references/availability without adding unsupported integrations.

**Tech Stack:** Vanilla HTML/CSS/ES2020, static JSON/SVG assets, Maven/JUnit, Playwright, GitHub Pages.

**Spec:** [2026-10-15-production-ready-console-design.md](../specs/2026-10-15-production-ready-console-design.md)

## Global Constraints

- Establish the supported framework list from maintained integration modules, build metadata, documentation, and runnable demos.
- Use official brand assets only when permitted usage can be confirmed; otherwise disclose the limitation and do not copy an unlicensed logo.
- Make names, support status, links, and visual treatment consistent without suggesting unsupported integrations.
- Existing user changes in the working tree must be preserved and integrated carefully.
- Do not include credentials or sensitive values in artifacts.
- Do not stage or commit pre-existing user changes; inspect the staged diff before every commit. For already-modified tracked files, stage only reviewed task hunks with a cached patch; if separation is unclear, leave those changes unstaged.

## Review Focus

1. A framework row without live verification — render its explicit status and never show a verified badge.
2. A missing, broken, or unlicensed logo — use a neutral text fallback and record the asset source/license decision.
3. A mismatch among console, README, and website names/versions/links — compare all surfaces from the same metadata and assert parity.
4. A color treatment unreadable in either theme — inspect light/dark Help screenshots and run contrast checks before accepting assets.
5. A narrow Help modal clipping cards or links — test modal at 760px and 390px with no document-level overflow.

---

### Task 1: Creating Verified Framework Metadata

**Files:**
- Create: `src/main/resources/static/framework-support.json`
- Inspect: `docs/console-readiness/framework-compatibility.md`
- Inspect: `docs/assets/`

**Interfaces:**
- Consumes: Final verified/partial/not-verified framework matrix.
- Produces: JSON array entries with `name`, `status`, `websiteUrl`, `docsUrl`, nullable `integrationPath` and `demoPath`, optional `version`, and optional `logoAsset`/`assetSource`. `status` is exactly one of `verified`, `partial`, `not_verified`, or `unsupported`.

- [ ] **Step 1: Add manifest contract tests**

Add a test in `ConsoleTaskSuccessTest` that parses the static manifest, validates required properties, HTTPS URLs, and the exact status enum (`verified`, `partial`, `not_verified`, `unsupported`), and rejects a `verified` status without compatibility evidence in the framework report.

- [ ] **Step 2: Run the test and confirm missing manifest fails**

Run: `.\mvnw.cmd '-Dtest=ConsoleTaskSuccessTest' test`

Expected: fail only because the support manifest is absent.

- [ ] **Step 3: Populate verified metadata**

Include only entries supported by the completed framework matrix. Source image assets only from official brand resources with permitted use confirmed; otherwise omit `logoAsset` and supply a text fallback.

- [ ] **Step 4: Run manifest validation**

Run: `.\mvnw.cmd '-Dtest=ConsoleTaskSuccessTest' test`

Expected: all manifest assertions pass; links and status agree with the compatibility report.

### Task 2: Adding Framework Information to Console Help

**Files:**
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/js/console.js`
- Modify: `src/main/resources/static/css/console.css`
- Test: `tests/e2e/tests/framework-help.spec.ts`

**Interfaces:**
- Consumes: `/framework-support.json` static resource and the existing Help dialog.
- Produces: A compact `#helpFrameworks` section in Help populated from the manifest, with accessible names, explicit status text, HTTPS-only links using `rel="noopener noreferrer"`, and image fallback.

- [ ] **Step 1: Write the failing browser test**

Test `help lists verified integrations and honest statuses`; open Help, assert every matrix entry appears with its status/link and that unverified entries are not labeled supported.

- [ ] **Step 2: Run and confirm test fails**

Run: `npm --prefix tests/e2e run test -- tests/framework-help.spec.ts`

Expected: current Help view has no framework list.

- [ ] **Step 3: Render manifest entries in existing Help**

Fetch the static JSON, render using escaped text/validated URLs, and show a clear loading/error state. Use the approved compact placement; do not add primary navigation or false success fallback.

- [ ] **Step 4: Verify browser behavior**

Run the focused Playwright test in both themes and at desktop/mobile widths. Verify missing images fall back to text and links use the canonical metadata URLs.

- [ ] **Step 5: Commit Help view changes**

```powershell
git add -- src/main/resources/static/js/console.js src/main/resources/static/framework-support.json tests/e2e/tests/framework-help.spec.ts src/test/java/org/embeddedjnosql/db/ConsoleTaskSuccessTest.java
# Apply a reviewed task-only patch for the index.html/CSS hunks:
git apply --cached "reviewed-help-hunks.patch"
git diff --cached --check
git --no-pager diff --cached
git commit -m "feat: show verified framework support in console help"
```

### Task 3: Reconciling README and Website Claims

**Files:**
- Modify: `README.md`
- Modify: `docs/index.html`
- Modify only as required after inspection: `.github/workflows/pages.yml`
- Create: exact, individually listed verified framework asset files under `docs/assets/`, only when permitted use is confirmed.

**Interfaces:**
- Consumes: Framework metadata manifest and live compatibility report.
- Produces: Identical canonical names, status labels, links, and permitted visual assets across all three surfaces.

- [ ] **Step 1: Compare existing public claims with the manifest**

List each badge, card, logo, banner, version, and URL in README/site/Console Help. Preserve user edits and make only relevant changes.

- [ ] **Step 2: Synchronize metadata and asset delivery**

Make the Pages build publish the canonical manifest/assets only if its current configuration requires it. Do not replace unrelated user changes in `.github/workflows/pages.yml`.

- [ ] **Step 3: Verify asset licensing and presentation**

Record source and allowed-use notes for each adopted framework asset; check alt text, aspect ratio, fallback, light/dark display, and responsive sizing.

- [ ] **Step 4: Run consistency checks**

Run: `.\mvnw.cmd '-Dtest=ConsoleTaskSuccessTest' test` and `npm --prefix tests/e2e run test -- tests/framework-help.spec.ts`

Expected: console manifest assertions and browser Help behavior pass. Compare README/site names, statuses, versions, and links against the manifest.

- [ ] **Step 5: Commit only reconciled branding surfaces**

```powershell
# Apply a reviewed task-only patch for the README/site/workflow hunks:
git apply --cached "reviewed-branding-hunks.patch"
# Stage exact new asset paths only; never stage docs/assets as a directory.
git add -- src/main/resources/static/framework-support.json
git diff --cached --check
git --no-pager diff --cached
git commit -m "docs: align verified framework branding"
```

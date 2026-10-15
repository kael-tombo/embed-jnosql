# Console Theme and UX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make light mode the reliable first-visit default, retain dark-mode preference, and ensure the console and sign-in page render consistently.

**Architecture:** Share a small early-loading theme controller between the console and sign-in documents so the saved theme is applied before styles render. Keep the existing toggle and preference key, with graceful recovery from bad or inaccessible storage.

**Tech Stack:** Vanilla ES2020, HTML, CSS custom properties, Playwright Chromium tests, JUnit 5 static-resource checks.

**Spec:** [2026-10-15-production-ready-console-design.md](../specs/2026-10-15-production-ready-console-design.md)

## Global Constraints

- Make light the initial HTML theme and the fallback when no saved preference exists.
- Retain explicit light/dark selection using the existing `embedjnosql.theme` key.
- Accept only the supported theme values; recover from absent, invalid, or inaccessible storage without preventing the UI from rendering.
- Keep the toggle immediate and avoid a full-page reload.
- Apply the saved preference consistently to the console and sign-in experience.
- Existing user changes in the working tree must be preserved and integrated carefully.
- Do not include credentials or sensitive values in artifacts.
- Do not stage or commit pre-existing user changes; inspect the staged diff before every commit.
- For already-modified tracked files, create a patch containing only this task's reviewed hunks and apply it with `git apply --cached <patch-file>`; never stage the whole file. If the hunks cannot be isolated safely, leave them unstaged.

## Review Focus

1. Cleared storage — the first loaded console and sign-in document use light mode; pin in `tests/e2e/tests/theme.spec.ts`.
2. A saved dark/light value — reload and navigation preserve it; pin both values in `theme.spec.ts`.
3. Invalid stored theme — fallback is light and the UI remains usable; pin in `theme.spec.ts`.
4. Storage access throws — theme application still succeeds in memory; pin in `theme.spec.ts` with a controlled browser context.
5. Narrow viewports — no document-level horizontal overflow at 390px, 760px, 999px, and 1440px; pin in `tests/e2e/tests/responsive.spec.ts`.

---

### Task 1: Establishing Theme Regression Tests

**Files:**
- Create: `tests/e2e/tests/theme.spec.ts`
- Create: `tests/e2e/tests/responsive.spec.ts`
- Consume: `tests/e2e/playwright.config.ts` and the isolated server fixture from the Playwright plan.

**Interfaces:**
- Consumes: A Playwright project exporting an authenticated seeded `page` fixture, a separate unauthenticated page fixture, and fresh isolated contexts.
- Produces: Browser assertions for theme initialization/persistence/recovery and three viewport overflow checks.

- [ ] **Step 1: Write failing browser tests**

Name tests `uses light theme with empty storage`, `persists dark theme across reload and panel navigation`, `switches back to light without reload`, `recovers from invalid theme storage`, `keeps the UI usable when storage access is denied`, `applies the saved theme on sign-in`, and `does not overflow at supported viewport widths`. Assert `document.documentElement.dataset.theme`, computed body/card colors, and `document.documentElement.scrollWidth === document.documentElement.clientWidth`; run the overflow assertion at 390px, 760px, 999px, and 1440px.

- [ ] **Step 2: Run the focused tests and confirm the current mismatch**

Run: `npm --prefix tests/e2e run test -- --grep "theme|overflow"`

Expected: tests exposing the current dark first-visit fallback fail before implementation; existing dark preference behavior remains a passing test if already supported.

### Task 2: Applying a Shared Light-First Theme

**Files:**
- Create: `src/main/resources/static/js/theme.js`
- Modify: `src/main/resources/static/index.html`
- Modify: `src/main/resources/static/login.html`
- Modify: `src/main/resources/static/js/console.js`
- Modify: `src/main/resources/static/css/console.css`
- Test: `tests/e2e/tests/theme.spec.ts`
- Test: `src/test/java/org/embeddedjnosql/db/ConsoleTaskSuccessTest.java`

**Interfaces:**
- Consumes: Playwright theme contract from Task 1.
- Produces: `window.EmbedJNoSQLTheme.setTheme(theme: 'light' | 'dark')`; initialization uses light if storage is absent, invalid, or unreadable. An explicit toggle updates the rendered theme immediately even if persisting fails; it must not throw or reload the page.

- [ ] **Step 1: Implement the failing tests**

If not already present, add static-resource assertions in `ConsoleTaskSuccessTest` that the console HTML declares light by default and both UI documents load the shared theme script before the stylesheet.

- [ ] **Step 2: Run tests and confirm failure**

Run: `.\mvnw.cmd '-Dtest=ConsoleTaskSuccessTest' test`

Expected: fail on the current dark document/fallback or missing shared script.

- [ ] **Step 3: Add the early shared theme controller**

Load `theme.js` synchronously in both document heads before `console.css`. Apply a valid saved preference before CSS paints; use light for missing, invalid, or unreadable storage. Route the console toggle through `window.EmbedJNoSQLTheme.setTheme(theme)`. If a storage write fails, keep the chosen theme in memory and preserve UI operation.

- [ ] **Step 4: Make sign-in styles theme-aware**

Replace hard-coded dark-only login surfaces, input text/backgrounds, focus, badge, and error colors with the existing console custom-property palette and `[data-theme="light"]` tokens. Keep the sign-in form behavior and API calls unchanged.

- [ ] **Step 5: Run regression tests**

Run:

```powershell
.\mvnw.cmd '-Dtest=ConsoleTaskSuccessTest' test
npm --prefix tests/e2e run test -- --grep "theme|overflow"
```

Expected: all focused Java and browser tests pass; switching theme makes no navigation request and persisted selection is correct on sign-in.

- [ ] **Step 6: Review visual states**

Capture and inspect deterministic overview and sign-in screenshots in both themes. Verify readable focus, disabled, error, table, dialog, and notification states; do not update reference images without reviewing the visual difference.

- [ ] **Step 7: Commit only theme code and tests**

```powershell
git add -- src/main/resources/static/js/theme.js src/main/resources/static/login.html tests/e2e/tests/theme.spec.ts tests/e2e/tests/responsive.spec.ts
# Apply a reviewed task-only patch for changed index.html/console.js/CSS/JUnit hunks:
git apply --cached "reviewed-task-hunks.patch"
git diff --cached --check
git --no-pager diff --cached
git commit -m "feat: make console theme light-first"
```

### Task 3: Verifying the Responsive Fix

**Files:**
- Inspect: `src/main/resources/static/css/console.css`
- Test: `tests/e2e/tests/responsive.spec.ts`

**Interfaces:**
- Consumes: Existing responsive grid fix and Playwright fixture.
- Produces: Evidence that the topbar and all panels fit at 760px, 999px, and 1440px without document overflow.

- [ ] **Step 1: Exercise all twelve panels at each viewport**

At 390px, 760px, 999px, and 1440px, visit `overview`, `collections`, `kv`, `columns`, `vectors`, `schema`, `tx`, `indexes`, `backup`, `cdc`, `audit`, and `server`; assert visible active panel, no document-level overflow, and no clipped topbar controls.

- [ ] **Step 2: Run the responsive test**

Run: `npm --prefix tests/e2e run test -- tests/responsive.spec.ts`

Expected: all three viewport sizes pass; any intentional table-local horizontal scrolling is scoped to the table container, not the document.

- [ ] **Step 3: Commit any narrowly required responsive correction**

Apply a reviewed CSS task-only patch with `git apply --cached "reviewed-css-hunks.patch"`, stage the responsive test if it is not already staged, inspect the cached diff, and commit with `fix: prevent console overflow on narrow screens`. Do not stage the entire CSS file if it contains pre-existing changes.

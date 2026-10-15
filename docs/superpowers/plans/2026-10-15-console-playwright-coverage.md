# Console Playwright Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a reproducible Playwright suite that exercises every independently testable console feature against a real isolated EmbedJNoSQL engine.

**Architecture:** Keep browser tooling under `tests/e2e/`, launch the seeded Java fixture with an isolated data directory and dynamically selected local port, and use accessible locators plus engine/API verification. A small shared fixture handles authentication, server lifecycle, and cleanup.

**Tech Stack:** Node.js, TypeScript, `@playwright/test`, Chromium, `@axe-core/playwright`, Maven Wrapper, Java 17.

**Spec:** [2026-10-15-production-ready-console-design.md](../specs/2026-10-15-production-ready-console-design.md)

## Global Constraints

- It must launch an isolated, deterministic, seeded EmbedJNoSQL instance and exercise real engine-backed workflows.
- Mocking is reserved for failures that cannot be induced reliably through the live service, such as specific network interruption scenarios.
- Use accessible roles/names and stable selectors.
- Include normal, empty, invalid, failed-request, reload/persistence, responsive, keyboard, and relevant failure-recovery cases.
- Verify resulting engine/API state where appropriate, use isolated test data, and clean up deterministically.
- Capture screenshots or other reproducible evidence without credentials.
- Do not include credentials or sensitive values in artifacts.
- Do not stage or commit pre-existing user changes; inspect the staged diff before every commit.

## Review Focus

1. Empty/missing collection or vector index — assert a truthful empty state and no misleading successful data; cover in `data-models.spec.ts`.
2. Invalid JSON, boundary values, duplicate IDs, and conflicts — assert useful validation and engine state unchanged; cover in the relevant workflow tests.
3. Session expiration or missing CSRF — assert one sign-in recovery and no duplicate unsafe write; cover in `security.spec.ts`.
4. Timeout/network loss after a write request — assert no false success, inspect actual engine state before recommending retry, and never repeat an unsafe write automatically; cover using one controlled aborted request in `errors.spec.ts`.
5. Browser refresh during an action or stale panel — assert no stale success display and deterministic recovery; cover in `navigation.spec.ts` and `errors.spec.ts`.

---

### Task 1: Creating the Playwright Project and Isolated Fixture

**Files:**
- Create: `tests/e2e/package.json`
- Create: `tests/e2e/package-lock.json`
- Create: `tests/e2e/tsconfig.json`
- Create: `tests/e2e/playwright.config.ts`
- Create: `tests/e2e/fixtures/console.ts`
- Create: `tests/e2e/tests/smoke.spec.ts`
- Modify: `src/test/java/org/embeddedjnosql/db/console/ConsoleInteractiveTestServer.java`
- Create: `tests/e2e/README.md`

**Interfaces:**
- Consumes: `docs/console-readiness/feature-inventory.md`, `user-stories.md`, and the existing seeded console fixture.
- Produces: Exports `test` and `expect` from `fixtures/console.ts`; `test` supplies `page` (authenticated seeded session), `anonymousPage` (fresh unauthenticated session), and per-worker `baseURL`. Each worker uses a Java fixture on an OS-assigned loopback port and a unique temporary data/backup directory. npm scripts are `test`, `test:headed`, and `test:report`.

- [ ] **Step 1: Add the minimal project manifest and failing smoke test**

Create `tests/e2e/tests/smoke.spec.ts` with test `loads the authenticated console overview`; import `test` and `expect` from `../fixtures/console`, then assert page title, Healthy status, and seeded collections.

- [ ] **Step 2: Install only the requested test dependencies**

Run: `npm --prefix tests/e2e install --save-dev @playwright/test typescript @axe-core/playwright`

Expected: package lock is created under `tests/e2e`; no root application dependencies change.

- [ ] **Step 3: Make the fixture isolated and discoverable**

Add fixture arguments for port `0` and a caller-supplied data directory. Let the Java server bind port zero, then print its actual bound loopback URL as a readiness line on stdout without credentials. Ensure test data and backup files live under a unique temporary directory. Terminate the exact child process and remove only its resolved temporary directory in teardown.

- [ ] **Step 4: Run the smoke test**

Run: `npm --prefix tests/e2e run test -- tests/smoke.spec.ts`

Expected: a real browser loads the fixture and observes the seeded overview. Startup failure, timeout, or fixture exit fails the test explicitly.

- [ ] **Step 5: Document setup and cleanup**

Document Node/Java requirements, browser installation, scripts, evidence location, and credential handling in `tests/e2e/README.md`.

- [ ] **Step 6: Commit the E2E foundation**

```powershell
git add -- tests/e2e/package.json tests/e2e/package-lock.json tests/e2e/tsconfig.json tests/e2e/playwright.config.ts tests/e2e/fixtures/console.ts tests/e2e/tests/smoke.spec.ts tests/e2e/README.md src/test/java/org/embeddedjnosql/db/console/ConsoleInteractiveTestServer.java
git diff --cached --check
git --no-pager diff --cached
git commit -m "test: add isolated console Playwright harness"
```

### Task 2: Testing Navigation, Authentication, and Accessibility

**Files:**
- Create: `tests/e2e/tests/navigation.spec.ts`
- Create: `tests/e2e/tests/security.spec.ts`
- Create: `tests/e2e/tests/accessibility.spec.ts`

**Interfaces:**
- Consumes: The `page` fixture from Task 1 and `CONSOLE-###` story IDs.
- Produces: Browser proof for all twelve panel routes, keyboard navigation, sign-in/logout/session recovery, and automated accessibility scans.

- [ ] **Step 1: Test panel routing and keyboard operation**

Assert each of the twelve panel controls reaches the matching `#<panel>` route and visible heading. Test documented numeric shortcuts, help open/close and focus restoration, and keyboard tab order.

- [ ] **Step 2: Test auth and session boundaries**

Verify anonymous access is redirected, valid sign-in reaches the requested panel, invalid sign-in shows a useful error, logout invalidates the session, and a 401 during polling causes one sign-in redirect.

- [ ] **Step 3: Run the accessibility scan**

Use `@axe-core/playwright` on authenticated Overview, Collections, Help, confirmation dialog, and Sign In. Assert no critical/serious violations; record lesser findings for review rather than suppressing them.

- [ ] **Step 4: Run focused tests**

Run: `npm --prefix tests/e2e run test -- tests/navigation.spec.ts tests/security.spec.ts tests/accessibility.spec.ts`

Expected: route and auth assertions pass, with actionable violations on failure.

### Task 3: Testing Documents, Querying, Schema, and Indexes

**Files:**
- Create: `tests/e2e/tests/documents.spec.ts`
- Create: `tests/e2e/tests/schema-indexes.spec.ts`

**Interfaces:**
- Consumes: Isolated fixture and document/schema/index stories.
- Produces: Verified collection list/load, CRUD, detail views, TTL/cleanup, filter/sort/pagination, schema, and index workflows.

- [ ] **Step 1: Cover collection and document workflows**

Test loading a seeded collection, empty/missing collection, insert, detail JSON/tree view, update, delete, invalid JSON, duplicate/conflict response, refresh, and cleanup. Verify the engine/API after each mutation.

- [ ] **Step 2: Cover query builder behavior**

Test `$eq`, `$gt`, combined conditions, sorting, limit/offset and next/previous boundaries; compare visible row IDs to the corresponding API result. Test malformed value handling.

- [ ] **Step 3: Cover schema and index workflows**

Register a schema, demonstrate accepted and rejected inserts, inspect/create an index, and verify invalid fields produce clear feedback without changing engine state.

- [ ] **Step 4: Run tests**

Run: `npm --prefix tests/e2e run test -- tests/documents.spec.ts tests/schema-indexes.spec.ts`

Expected: browser state and engine/API observations agree for successes and failures.

### Task 4: Testing Key-Value, Wide-Column, and Vector Workflows

**Files:**
- Create: `tests/e2e/tests/data-models.spec.ts`

**Interfaces:**
- Consumes: Isolated fixture with seeded KV/list/set/hash/column data and vector stories.
- Produces: Verified operations and error/empty behavior for Key-Value, Column Family, and experimental Vectors panels.

- [ ] **Step 1: Test simple KV and Redis-style structures**

Exercise put/get/delete, list push/range/pop, set add/remove/membership, and hash set/get/delete. Verify persisted in-engine values via API after UI actions.

- [ ] **Step 2: Test wide-column operations**

Create/read a row, inspect stats/range and TTL where supported; verify stored columns and expiry behavior through the API.

- [ ] **Step 3: Test vector index lifecycle**

Add a valid vector, inspect index dimensions/count, search and verify returned ID; test empty/missing index and wrong dimensions. Label the capability experimental in test descriptions and evidence.

- [ ] **Step 4: Run tests**

Run: `npm --prefix tests/e2e run test -- tests/data-models.spec.ts`

Expected: engine-backed state matches the browser result; unsupported behavior is explicitly marked rather than silently skipped.

### Task 5: Testing Operations, Diagnostics, and Failure Recovery

**Files:**
- Create: `tests/e2e/tests/operations.spec.ts`
- Create: `tests/e2e/tests/errors.spec.ts`

**Interfaces:**
- Consumes: Isolated fixture and transactions, backup, CDC, audit, server, and error stories.
- Produces: Verified action outcomes and recovery states across remaining panels.

- [ ] **Step 1: Test transactions and transaction boundaries**

Begin, inspect, commit, and roll back a transaction; verify status changes and that unrelated direct console writes are accurately described as outside that transaction.

- [ ] **Step 2: Test backup/restore**

Create a backup, verify it contains seeded data, remove a test record, restore, and verify the record is present again. Keep all artifacts in the fixture's temporary directory.

- [ ] **Step 3: Test CDC, audit, and server diagnostics**

Load events, exercise an enabled/disabled state where configured, inspect audit entries after a known mutation, and verify health/metrics render from live API data.

- [ ] **Step 4: Test explicit failure and recovery states**

Induce a controlled backend failure, timeout/aborted request, invalid request, and a refresh/retry. Assert error state, data-change explanation, correlation ID where provided, and no false success.

- [ ] **Step 5: Run focused tests**

Run: `npm --prefix tests/e2e run test -- tests/operations.spec.ts tests/errors.spec.ts`

Expected: failures remain explicit and all temporary backup/test data is cleaned up.

### Task 6: Capturing Visual Evidence and Full Traceability

**Files:**
- Create: `tests/e2e/tests/visual.spec.ts`
- Modify: `docs/console-readiness/traceability.md`
- Create: `tests/e2e/.gitignore`

**Interfaces:**
- Consumes: `feature-inventory.md`, `user-stories.md`, all tests from Tasks 1–5.
- Produces: Deterministic light/dark desktop/mobile screenshot evidence and feature-to-test execution matrix.

- [ ] **Step 1: Add reviewed visual checks**

Capture stable Overview and Collections screenshots in light/dark at desktop/tablet/mobile. Fix locale/time/seed data; review each diff before updating a snapshot.

- [ ] **Step 2: Run the full E2E suite**

Run: `npm --prefix tests/e2e run test`

Expected: Playwright reports exact passed/failed/skipped totals. Store report/screenshots in ignored test output and record paths/counts in `traceability.md`.

- [ ] **Step 3: Complete feature mapping**

Map every story acceptance criterion to a test name and actual run result. Record gaps and justified limitations explicitly.

- [ ] **Step 4: Commit suite and matrix**

```powershell
git add -- tests/e2e/playwright.config.ts tests/e2e/tests/smoke.spec.ts tests/e2e/tests/navigation.spec.ts tests/e2e/tests/security.spec.ts tests/e2e/tests/accessibility.spec.ts tests/e2e/tests/documents.spec.ts tests/e2e/tests/schema-indexes.spec.ts tests/e2e/tests/data-models.spec.ts tests/e2e/tests/operations.spec.ts tests/e2e/tests/errors.spec.ts tests/e2e/tests/visual.spec.ts tests/e2e/tests/theme.spec.ts tests/e2e/tests/responsive.spec.ts tests/e2e/tests/framework-help.spec.ts tests/e2e/fixtures/console.ts tests/e2e/.gitignore docs/console-readiness/traceability.md
git diff --cached --check
git --no-pager diff --cached
git commit -m "test: cover console workflows with Playwright"
```

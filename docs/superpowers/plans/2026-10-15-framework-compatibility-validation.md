# Framework Compatibility Validation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Verify every actually supported EmbedJNoSQL framework integration through its module, runnable demo, engine operations, and console-visible behavior.

**Architecture:** Build an authoritative candidate list from repository modules and demos, then validate each candidate independently. Report compile/test/startup/runtime/console evidence as separate facts; do not infer runtime support from a successful build.

**Tech Stack:** Java 17, Maven Wrapper, Spring Boot, Quarkus, Micronaut, Vert.x, Playwright.

**Spec:** [2026-10-15-production-ready-console-design.md](../specs/2026-10-15-production-ready-console-design.md)

## Global Constraints

- Establish the supported framework list from maintained integration modules, build metadata, documentation, and runnable demos.
- Do not infer support from a badge, dependency declaration, or successful compilation alone.
- For every verified framework, record versions and separately report module build, demo startup, engine lifecycle, representative operations, console visibility/metadata, tests, limitations, and blockers.
- Existing user changes in the working tree must be preserved and integrated carefully.
- Reports must distinguish implemented, tested and verified, partially verified, and not verified.
- Do not include credentials or sensitive values in artifacts.
- Do not stage or commit pre-existing user changes; inspect the staged diff before every commit.

## Review Focus

1. A demo compiles but does not start — require a real startup and health signal for each framework plan task.
2. Startup succeeds but no engine operations work — verify representative CRUD/read results against the engine.
3. Shutdown/restart leaks resources or data unexpectedly — check lifecycle and persistent state for every framework.
4. An integration is listed on a docs surface but has no maintained module/demo — record it as unverified or unsupported, not compatible.
5. Framework identity is absent or misleading in the Console — capture actual console metadata behavior and avoid assuming framework discovery exists.

---

### Task 1: Establishing the Authoritative Framework Matrix

**Files:**
- Create: `docs/console-readiness/framework-compatibility.md`
- Inspect: `README.md`
- Inspect: `docs/index.html`
- Inspect: `spring-boot-starter/pom.xml`
- Inspect: `micronaut-integration/pom.xml`
- Inspect: `quarkus-extension/pom.xml`
- Inspect: `demo/spring-boot-demo/pom.xml`
- Inspect: `demo/quarkus-demo/pom.xml`
- Inspect: `demo/micronaut-demo/pom.xml`
- Inspect: `demo/vertx-demo/pom.xml`

**Interfaces:**
- Consumes: Baseline/inventory reports.
- Produces: One row per claimed framework with canonical status, maintained integration path, demo path, pinned version source, and validation commands.

- [ ] **Step 1: Reconcile claims with maintained sources**

Record frameworks found in build modules, demo modules, and docs separately. Investigate website-only claims such as Javalin, Helidon, and Dropwizard; do not treat keyword metadata as support evidence.

- [ ] **Step 2: Record exact versions and entry points**

Read the framework and plugin versions from the relevant POMs. Record each module test/build command and documented application start command; do not guess plugin goals.

- [ ] **Step 3: Validate the candidate inventory**

Run: `git diff --check -- docs/console-readiness/framework-compatibility.md`

Expected: every claimed candidate has a source reference and status of “not yet validated” until its own live task passes.

### Task 2: Validating Spring Boot

**Files:**
- Modify: `docs/console-readiness/framework-compatibility.md`
- Test/build: `spring-boot-starter/pom.xml`
- Test/run: `demo/spring-boot-demo/pom.xml`
- Inspect: Spring Boot demo source and configuration.

**Interfaces:**
- Consumes: Matrix from Task 1.
- Produces: Reproducible Spring Boot module and live-demo evidence with engine-backed operations and console status.

- [ ] **Step 1: Build and test the starter**

Run: `.\mvnw.cmd -f spring-boot-starter/pom.xml test`

Expected: record exact Maven counts and version.

- [ ] **Step 2: Run the demo using its documented Maven goal**

Run the command confirmed from `demo/spring-boot-demo/pom.xml`; wait for the documented ready/health signal.

- [ ] **Step 3: Verify a representative operation and lifecycle**

Create/read a demo entity through its actual application path, confirm engine state and console health/metadata, then stop/restart and verify expected cleanup/persistence.

- [ ] **Step 4: Record only verified claims**

Update Spring Boot's row with command, version, runtime result, console result, evidence paths, and limits.

### Task 3: Validating Quarkus

**Files:**
- Modify: `docs/console-readiness/framework-compatibility.md`
- Test/build: `quarkus-extension/pom.xml`
- Test/run: `demo/quarkus-demo/pom.xml`

**Interfaces:**
- Consumes: Matrix from Task 1.
- Produces: Separate extension and live-demo evidence.

- [ ] **Step 1: Build and test the extension**

Run: `.\mvnw.cmd -f quarkus-extension/pom.xml test`

Expected: record results independently for deployment and runtime modules.

- [ ] **Step 2: Run the Quarkus demo**

Use the goal/configuration confirmed from its POM and README. Verify startup health.

- [ ] **Step 3: Verify engine operations, Console visibility, and shutdown**

Perform a representative write/read, compare with engine/API state, inspect console status, then stop and record lifecycle behavior.

- [ ] **Step 4: Record evidence and limitations**

Do not infer the demo validates extension deployment metadata unless that path is exercised.

### Task 4: Validating Micronaut

**Files:**
- Modify: `docs/console-readiness/framework-compatibility.md`
- Test/build: `micronaut-integration/pom.xml`
- Test/run: `demo/micronaut-demo/pom.xml`

**Interfaces:**
- Consumes: Matrix from Task 1.
- Produces: Micronaut module and live-demo evidence.

- [ ] **Step 1: Build and test the integration**

Run: `.\mvnw.cmd -f micronaut-integration/pom.xml test`

Expected: record Maven result and configured framework version.

- [ ] **Step 2: Run the demo using its configured goal**

Read its POM before selecting the goal, start the real demo, and verify the documented health signal.

- [ ] **Step 3: Verify injection, database operation, console state, and shutdown**

Exercise one framework-managed database write/read and confirm its result through the engine and console.

- [ ] **Step 4: Record evidence and limits**

Keep compile/test/startup/runtime/discovery outcomes as separate matrix columns.

### Task 5: Validating Vert.x

**Files:**
- Modify: `docs/console-readiness/framework-compatibility.md`
- Test/build/run: `demo/vertx-demo/pom.xml`

**Interfaces:**
- Consumes: Matrix from Task 1.
- Produces: Vert.x live-demo evidence and a verified/partial/not-verified conclusion.

- [ ] **Step 1: Build and test the Vert.x demo**

Run: `.\mvnw.cmd -f demo/vertx-demo/pom.xml test`

Expected: exact pass/fail/skip counts and configured Vert.x version.

- [ ] **Step 2: Start the real demo**

Confirm its Maven entry point from the POM/readme, run it, and wait for its documented ready/health signal.

- [ ] **Step 3: Verify engine operation, console status, and shutdown**

Write/read through the Vert.x application, verify engine/API state and console health, then stop cleanly.

- [ ] **Step 4: Record a truthful status**

If the project has no maintained integration or runnable demo, mark that explicitly rather than adding a support claim.

### Task 6: Closing Framework Gaps

**Files:**
- Modify: `docs/console-readiness/framework-compatibility.md`
- Modify only after approval in later branding plan: `README.md`, `docs/index.html`

**Interfaces:**
- Consumes: All framework task results.
- Produces: Final compatibility matrix with evidence and exact environment blockers.

- [ ] **Step 1: Verify additional docs-only framework claims**

For each additional framework named in public metadata, find a maintained integration and perform the same applicable live checks or mark it unsupported/unverified.

- [ ] **Step 2: Run matrix consistency review**

Expected: every “verified” row links to build, live operation, and lifecycle evidence; every missing result is labeled partial/not verified.

- [ ] **Step 3: Commit only the compatibility report**

```powershell
git add -- docs/console-readiness/framework-compatibility.md
git commit -m "docs: record framework compatibility evidence"
```

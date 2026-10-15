# Production-Ready EmbedJNoSQL Console Design

**Date:** 2026-10-15  
**Status:** Approved in conversation; awaiting written-spec review

## Goal

Make the EmbedJNoSQL Console a reliable, accessible, evidence-backed experience across its real features and supported framework integrations. Set light mode as the first-visit default while preserving dark mode, and do not claim compatibility, visual quality, or test coverage without verification.

This is a staged readiness effort, not a visual-only redesign. Existing user changes in the working tree must be preserved and integrated carefully.

## Current Evidence and Constraints

- The repository contains a vanilla HTML/CSS/JavaScript console backed by the embedded Java HTTP server.
- The console has twelve navigable panels: Overview, Collections, Key-Value, Column Family, Vectors, Schema, Transactions, Indexes, Backup, CDC / Events, Audit Trail, and Server.
- The README advertises Spring Boot, Quarkus, Micronaut, and Vert.x integration demos. The website metadata mentions additional frameworks; these claims must be resolved against maintained integrations and runnable examples before publication.
- Existing Java tests exercise console HTTP/API behavior, and a seeded interactive console fixture exists. No Playwright configuration or suite was found in the initial repository discovery.
- The console currently sets `data-theme="dark"` in its HTML and uses dark as the no-preference fallback. It persists the user's selection under `embedjnosql.theme`.
- A browser QA pass found horizontal overflow at 760px and 999px. A narrow CSS fix has been made and verified at 760px, 999px, and 1440px; all twelve panels rendered at 999px after the fix.
- A prior focused console test run passed 110 tests. This is a baseline observation, not proof of complete feature coverage.
- The working tree already contains unrelated edits, including README, workflow, Java, HTML, and branding changes. Do not overwrite or revert them.

## Design

### 1. Baseline, feature inventory, and defect triage

Treat source code, build configuration, and executable behavior as authoritative. For each actual console feature, document:

- Purpose, panel/route, controls, and actors.
- Preconditions, configuration, permissions, and security considerations.
- Main workflow and observable UI and engine/API effects.
- Validation, empty/loading/success/error/disabled states, and recovery behavior.
- Accessibility, keyboard, and responsive requirements.
- Framework or engine dependencies.
- Existing tests, missing coverage, and evidence required.

Create a prioritized defect list with severity, reproduction, root cause, proposed resolution, and verification method. Mark implementation, documentation, experimental status, and verification independently.

### 2. Theme and console UX

- Make light the initial HTML theme and the fallback when no saved preference exists.
- Retain explicit light/dark selection using the existing `embedjnosql.theme` key.
- Accept only the supported theme values; recover from absent, invalid, or inaccessible storage without preventing the UI from rendering.
- Keep the toggle immediate and avoid a full-page reload.
- Apply the saved preference consistently to the console and sign-in experience.
- Review both themes for readability, focus, hover, disabled, error, table, dialog, notification, and chart states, along with keyboard operation and responsive layout.
- Add regression coverage for first visit, toggle, persistence through reload/navigation, and storage recovery.

### 3. Playwright coverage

Create a dedicated, maintainable Playwright project. It must launch an isolated, deterministic, seeded EmbedJNoSQL instance and exercise real engine-backed workflows. Mocking is reserved for failures that cannot be induced reliably through the live service, such as specific network interruption scenarios.

Use accessible roles/names and stable selectors. Map each independently testable story and acceptance criterion to one or more tests. Include normal, empty, invalid, failed-request, reload/persistence, responsive, keyboard, and relevant failure-recovery cases. Verify resulting engine/API state where appropriate, use isolated test data, and clean up deterministically. Capture screenshots or other reproducible evidence without credentials.

### 4. Framework verification and branding

Establish the supported framework list from maintained integration modules, build metadata, documentation, and runnable demos. Do not infer support from a badge, dependency declaration, or successful compilation alone.

For every verified framework, record versions and separately report module build, demo startup, engine lifecycle, representative operations, console visibility/metadata, tests, limitations, and blockers. The repository's stated candidates are Spring Boot, Quarkus, Micronaut, and Vert.x; investigate other website claims and either verify them or correct their status.

Maintain shared framework metadata where it provides a useful source of truth for the console Help view, README, and website/docs. Add a compact verified Frameworks section to the existing Help view rather than promoting it to primary navigation. Use official brand assets only when permitted usage can be confirmed; otherwise disclose the limitation and do not copy an unlicensed logo. Make names, support status, links, and visual treatment consistent without suggesting unsupported integrations.

### 5. Evidence and reporting

Place new deliverables under `docs/console-readiness/`:

- A complete feature inventory, stories/use cases, and prioritized assessment.
- A traceability matrix: feature → user story → acceptance criteria → Playwright test → execution result → evidence.
- A framework compatibility matrix with verified versions, results, limitations, and blockers.
- A release-readiness report with executed commands, test totals, screenshots/log references, unresolved defects, and risks.

Reports must distinguish implemented, tested and verified, partially verified, and not verified. Do not fabricate results or suppress failures/skips. Do not include credentials or sensitive values in artifacts.

## Execution Order

1. Record the baseline, inventory, authoritative framework list, and defects.
2. Implement the approved light-first theme and focused UX fixes with regression tests.
3. Add and execute Playwright coverage mapped to the inventory.
4. Validate supported framework integrations individually; reconcile Help, README, and website branding and claims.
5. Rerun affected builds, tests, browser checks, accessibility/responsive checks, and review final evidence and risks.

Dependencies must remain explicit: Playwright cases depend on the feature inventory; framework claims depend on framework verification; final readiness depends on the preceding workstreams. If environment or licensing constraints prevent verification, record the exact limitation.

## Acceptance Criteria

- First-time or cleared-storage users see light mode; saved dark mode survives reload and navigation; switching back to light works immediately.
- Major console and sign-in UI surfaces render consistently in either theme and remain usable by keyboard at desktop, tablet, and mobile widths.
- Every actual independently testable console feature has a documented story, acceptance criteria, and verification mapping, or an explicit justified limitation.
- Playwright tests perform meaningful user interactions and verify engine-backed outcomes; executed counts and failures are reported honestly.
- Each officially supported framework is assessed independently using its actual integration/demo. No framework is called verified based only on compilation.
- Framework names, statuses, links, and permitted assets are consistent across the Help view, README, and project website/docs.
- Existing unrelated user edits are preserved.
- All reports distinguish verified results from partial or unverified work and disclose remaining issues and blockers.

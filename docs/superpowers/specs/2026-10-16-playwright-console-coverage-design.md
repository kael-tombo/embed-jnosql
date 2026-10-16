# Playwright Console Coverage Design

**Date:** 2026-10-16
**Status:** Proposed
**Input:** User request to add 300+ executable Playwright use cases across the console UI

## Purpose

Expand browser-level coverage so maintainers can verify the existing EmbedJNoSQL console's supported workflows, including common success paths, validation failures, empty/missing states, and navigation between features. Each added scenario must be individually identifiable in Playwright output and must demonstrate observable UI behavior rather than merely calling an API.

## Scope

- Add at least 300 distinct, independently reported Playwright test cases beyond the 38 tests currently discovered by `playwright test --list` (at least 338 total when the new suite is complete).
- Cover all 12 existing console panels: Overview, Collections, Key-Value, Column Family, Vectors, Schema, Transactions, Indexes, Backup, CDC / Events, Audit Trail, and Server.
- Add timestamped-document scenarios through Collections only where existing collection fields and controls support them. Do not introduce a Time Series panel, API, or other product capability.
- Cover current authentication, navigation, responsive behavior, themes, and accessibility where applicable to console workflows.
- Include all added cases in the default `npm --prefix tests/e2e run test` command.
- Change only the E2E tests and directly related E2E documentation/configuration needed to discover and execute them. Do not modify application code, the catalog CSV, or unrelated working-tree changes.
- Keep experimental vector cases clearly labelled and separate from release-gate claims.

## Out of scope

- Adding or implying support for a Time Series product feature that is not present in the current UI.
- Rewriting existing console behavior or fixing application defects as part of test expansion.
- Generating executable tests from the catalog traceability CSV.
- Changing the existing catalog evidence or claiming that a case passed before the test is executed.

## Design

Organize the new cases into feature-specific Playwright spec files near the existing suite. Use explicit data-driven case tables where actions and assertions genuinely share a workflow; each table row must create a separately named `test()` result. Keep separate cases for materially different behavior rather than combining many requirements into one long scenario. Use small feature-local helpers for repetitive setup and interaction; avoid a generic test engine or CSV-driven generation layer.

Tests must use the existing isolated console fixture. Each test creates uniquely named data where needed and cleans up its own resources. Browser actions drive the behavior under test. Direct API calls may be used for fixture setup, cleanup, or independent state verification, but must not replace the UI action/assertion being tested. Prefer Playwright's locator auto-waiting and response/event waits over fixed sleeps.

## Coverage areas

The case inventory will be organized around controls and behaviors present in the current console:

| Area | Representative coverage |
| --- | --- |
| Shell and navigation | All panel routes and headings, deep links, browser reload, navigation filtering, keyboard shortcuts, dialogs, focus, and empty/error surfaces |
| Overview and Server | Health and engine state, counters, refresh behavior, storage/WAL summaries, server telemetry, and unavailable-state feedback |
| Collections | Collection selection and refresh; document insert, read, replace, delete, duplicate IDs, invalid JSON, filters, sort, pagination, TTL/expiry, and timestamped documents where supported |
| Key-Value | Scalar keys, TTL, lists, sets, hashes, validation, empty/missing distinctions, and cleanup |
| Column Family | Row writes and reads, range selection, TTL, invalid input, and empty/missing state |
| Vectors | Existing experimental index, insert, search, invalid vector, and dimension validation; label these tests as experimental |
| Schema and Indexes | Create/read/remove configuration, valid and invalid documents/fields, and preservation of state after rejected input |
| Transactions | Begin, commit, rollback, invalid transitions, and UI-visible transaction state |
| Backup | Snapshot creation/listing, restore behavior, validation, and recoverable error states using only fixture-owned data |
| CDC and Audit Trail | Event generation, ordering/filtering where supported, empty state, and consistency with UI-visible mutations |
| Shared cross-cutting behavior | Authentication/session expiry, theme persistence, supported viewport widths, accessibility, error messaging, and safe cleanup |

The final implementation must keep an explicit count of newly discovered test cases by feature area and verify that the total increment is at least 300. Coverage rows must correspond to existing UI behavior; unimplemented or unsupported capabilities are not represented as passing cases.

## Isolation and failure handling

- Preserve the current worker-scoped Java console server and per-test browser context unless test evidence demonstrates a need for a targeted fixture adjustment.
- Make each test independent of execution order and avoid shared mutable test data.
- Use unique collection, key, row, and index names; cleanup must target only data created by that test.
- Assert user-visible outcomes and, when needed, verify persisted state independently.
- Let unexpected setup, response, and cleanup failures fail visibly; do not silently skip or convert them into success.
- Do not increase parallelism or weaken isolation merely to reduce runtime. Measure the completed suite before proposing runner changes.

## Verification and acceptance criteria

1. `npm --prefix tests/e2e exec -- playwright test --list` reports at least 338 tests, including at least 300 new uniquely named cases.
2. Every console panel has feature-relevant UI coverage; each scenario's title makes its behavior and feature recognizable in test output.
3. The test actions operate through the browser UI for the target behavior. Any API use is limited to setup, cleanup, or independent verification.
4. The suite uses isolated/unique test data and cleans up resources it creates without changing the shared browser session.
5. Experimental vector coverage remains explicitly identified as experimental.
6. The default full E2E command runs the expanded suite. Run the targeted new specs during development, then run the full suite and report exact pass/fail/skip totals and any environment blockers.
7. No application source or unrelated pre-existing worktree changes are modified.

## Implementation boundary

The current repository worktree contains unrelated uncommitted changes. Implementation must preserve them and restrict edits to the agreed E2E scope. Any discovered requirement for a product UI/API change is a separate request and must not be smuggled into this test-only expansion.

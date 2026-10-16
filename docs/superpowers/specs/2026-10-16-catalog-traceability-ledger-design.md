# Catalog Traceability Ledger Design

**Date:** 2026-10-16
**Status:** Proposed
**Input:** User-provided “Embed-JNoSQL: Playwright User Story & Use-Case Catalog” snapshot (448 candidate scenarios across 28 domains)

## Purpose

Create a durable, conservative evidence map from every candidate `EJ-###` scenario in the user-provided catalog to the repository’s applicable features, tests, and actual execution evidence. The ledger must make it possible to distinguish implemented behavior from behavior that is fully verified, partially verified, not yet tested, or not applicable.

The catalog is a candidate specification, not evidence of product support. Neither a source-code reference nor the existence of a test class is sufficient to claim that a scenario passed.

## Scope

- Include all 448 scenario IDs and all 28 domains from the supplied snapshot.
- Preserve each scenario’s ID, domain, and title; do not rewrite its acceptance intent.
- Inspect repository source, documentation, and tests to make conservative applicability and coverage decisions.
- Record only execution results backed by an observed test-run result. Historical evidence must be labelled with its date and scope.
- Map UI, engine, framework, and release cases. Do not assume every candidate case is applicable or supported.
- Do not modify the read-only catalog attachment, the release notes, application code, tests, CI, or unrelated readiness records as part of ledger creation.

## Deliverables

1. `docs/console-readiness/catalog-traceability.csv` — one row for each `EJ-001` through `EJ-448`.
2. `docs/console-readiness/catalog-traceability.md` — source description, field definitions, evidence rules, audited revision and run context, and summary counts.

The CSV is the case-level source of truth. The Markdown report explains how to interpret it and summarizes counts derived from the CSV. It must not introduce totals that are not reproducible from the ledger.

## Ledger schema

The CSV uses these columns, in this order:

| Column | Meaning |
| --- | --- |
| `case_id` | Stable catalog identifier, exactly one unique `EJ-###` value. |
| `domain_id` | Catalog domain identifier, such as `ENGINE-01`, `UI-01`, or `FW-01`. |
| `domain_name` | Domain title as supplied in the catalog. |
| `scenario_title` | Scenario title as supplied in the catalog, without formatting markup. |
| `applicability` | One of `applicable`, `unsupported`, `not_applicable`, or `unassessed`. |
| `applicability_evidence` | Concise repository evidence and rationale for the applicability decision. Required for `unsupported` and `not_applicable`; otherwise use `unassessed` if evidence is insufficient. |
| `coverage` | One of `full`, `partial`, `none`, or `not_assessed`, describing how much of the scenario’s stated acceptance criteria is demonstrated by cited tests. |
| `test_references` | Repository-relative paths and stable test names/IDs, or empty when none are mapped. |
| `execution_status` | One of `passed`, `failed`, `mixed`, `not_run`, `blocked`, or `not_applicable`. It records the observed outcome of the cited test evidence, independently of how much of the scenario that evidence covers. |
| `test_command` | Exact command used for the cited execution evidence; empty when no execution is claimed. |
| `environment` | Relevant runtime/browser/OS details known for the cited run; unknown details must not be guessed. |
| `run_date` | Date of the cited run in ISO `YYYY-MM-DD` format; empty when not run. |
| `evidence_artifact` | Repository-relative report, result file, or other reproducible evidence; empty when none is retained. |
| `issue` | Related issue reference if known; otherwise empty. |
| `notes` | Limitation, partial-coverage detail, or follow-up context. |

For missing information, use the field’s designated empty value or status, not an invented result. CSV quoting must preserve commas, quotes, and punctuation in catalog titles and evidence.

## Status and evidence rules

- `applicable` requires evidence that the described behavior is part of the repository’s implemented or declared product scope. A passing test alone does not prove that the behavior is a supported product feature.
- `unsupported` requires explicit evidence that the capability is unsupported, absent by documented design, or explicitly marked experimental/unsupported. Lack of a matching test is not enough.
- `not_applicable` requires a specific scope-based rationale. It is distinct from unsupported and from unassessed.
- `unassessed` is the default when available repository evidence does not justify a stronger applicability decision.
- `full` coverage means the cited assertions exercise the complete acceptance intent of that scenario. `partial` means only a subset is demonstrated. `none` means no relevant test evidence is mapped. `not_assessed` means coverage has not yet been evaluated.
- `execution_status=passed` records that all cited required tests passed in the recorded run, even when they cover only part of the scenario. A catalog case counts as fully passed only when applicability is `applicable`, coverage is `full`, and `execution_status=passed`; passing partial evidence must not be counted as a fully passed catalog case.
- `failed` indicates a required mapped assertion failed. `mixed` indicates the mapped run had both passing and failing required evidence, or evidence does not support one unambiguous outcome. `not_run` is used when no applicable mapped test was run. `blocked` requires a concrete execution blocker. `not_applicable` is reserved for cases with an evidenced not-applicable decision.
- A source location, manifest entry, build success, test class name, or dated report without the relevant executed assertion is not a scenario pass.
- Framework compatibility claims require scenario-relevant framework evidence. Module presence or compilation alone is insufficient for an end-to-end compatibility claim.
- Experimental cases must retain that qualification in notes and must not be included in release-gate pass counts unless the catalog or repository policy explicitly defines them as such.

## Population and reporting

The initial population will parse the supplied snapshot to establish the 448-row skeleton, then review repository evidence by domain. Each populated reference must identify a test or evidence artifact precisely enough for a maintainer to locate it. Where test assertions do not match the whole candidate acceptance intent, record `partial` rather than promoting the scenario to passed.

The report will include:

- Catalog counts by domain and total.
- Applicability counts by status.
- Coverage counts by status.
- Execution counts by status.
- Fully passed applicable cases, calculated only from rows satisfying the pass rule.
- A short list of known evidence limitations and unassessed domains/cases.
- Audited Git revision and execution command/date for each current run represented.

The report must distinguish current execution from historical evidence. For the starting evidence currently available, the recorded Playwright result is 38 passed, 0 failed, and 0 skipped on revision `13bda90a70daf10d27532e6ff6f6ce62b7a5c0e0`, using `npm --prefix tests/e2e run test` on 2026-10-16. The 110-test backend report is dated 2026-10-15 and is historical evidence; it must not be presented as a run performed during the ledger task. Framework suites not run during the ledger task remain `not_run`.

## Validation

Before accepting the deliverables:

1. Verify the CSV parses with the declared header and contains exactly 448 data rows.
2. Verify all IDs from `EJ-001` to `EJ-448` appear exactly once, with no gaps or duplicates.
3. Verify all domain IDs and names correspond to the 28 catalog domains and all status fields use the allowed values.
4. Verify every `unsupported` and `not_applicable` row has a rationale, every execution claim has a command/date/environment and test reference, and every `passed` row satisfies the full-coverage pass rule.
5. Recompute every summary count in the Markdown report from the CSV.
6. Keep the pre-existing working-tree changes and the user-provided read-only attachments untouched.

## Out of scope

- Implementing missing product features or adding/updating tests.
- Changing CI or release workflows.
- Declaring all 448 scenarios applicable or supported.
- Claiming a test pass based solely on existing files or old documentation.
- Editing or replacing the supplied catalog snapshot or `RELEASE-NOTES-v1.0.2.md`.

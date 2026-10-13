# Repository Consistency Remediation Design

**Status:** Conversational design approved; written-spec review pending  
**Scope:** Repository consistency remediation only

## Goal

Make the maintained repository reproducible from a clean checkout and ensure its
Maven metadata, CI behavior, and user-facing support claims agree with the
published artifacts and verified project behavior.

The user confirmed that documentation should reflect demonstrated, reproducible
behavior, that dated audit/evidence documents remain historical snapshots, and
that this work must not push changes or publish artifacts.

## Evidence motivating the work

- The core and Spring Boot starter are published at version `1.0.1`, while many
  current consumers—including CLI, framework integrations, demo projects, and
  generated single-feature demos—still request core `1.0.0`.
- Quarkus and Micronaut integration coordinates remain source-checkout
  artifacts at `1.0.0`; they are not available from Maven Central. Current
  README examples instead present their `1.0.1` coordinates as installable.
- A recent Quarkus demo run failed during CDI indexing with
  `Producer method return type not found in index: jakarta.nosql.Template`.
- Some CI jobs, the demo runbook, and demo documentation invoke bare `mvn`
  despite the repository providing a Maven Wrapper. CI does not run the
  20 generated single-feature demo suites that the demo documentation
  describes.
- Maintained documentation has contradictory Maven Central and test-count
  claims, a Java 17 target alongside a root POM comment saying Java 21 is
  required, an unverified Spring Boot compatibility range, a Vert.x version
  mismatch, and SQL/JPA phrasing that can imply relational database
  functionality.

These findings are the remediation scope, not a claim that every historical
record is current.

## Design

### 1. Maven coordinates and release boundaries

- Use published core `1.0.1` for every current Maven consumer, including the
  CLI, Quarkus/Micronaut integrations, demo projects, and the 20 generated
  single-feature demos.
- Update both the generated demo POMs and `demo/gen-demos.sh` so regeneration
  does not restore the stale core version.
- Keep unpublished Quarkus/Micronaut integration artifact versions at `1.0.0`.
  Describe them as source-checkout/local-install integrations; do not imply
  their coordinates resolve from Maven Central.
- Do not alter the version or implementation of the already-published core or
  Spring Boot starter as part of this repository-consistency task.
- Do not create tags, deploy artifacts, or publish any module.

### 2. Reproducible builds and behavior

- Add a focused regression test for the Quarkus CDI/indexing failure and make
  the smallest extension-level change that resolves it.
- Use the Maven Wrapper for Maven invocations in CI and maintained build/run
  documentation.
- Ensure CI installs source-built modules in dependency order and runs the
  existing framework demos plus all 20 generated single-feature demo suites.
- Test the documented Java 17 minimum and Java 21 in CI.
- Validate dependency resolution and builds using an isolated, clean local
  Maven repository so cached obsolete artifacts cannot hide missing or stale
  coordinates.
- Leave core and Spring Boot starter implementation code unchanged.

### 3. Maintained documentation and support claims

- Correct the root README, demo README, runbook, validation matrix, and active
  workflow labels/summaries to reflect the current Central publication state,
  verified framework versions, Java support, and results from this remediation's
  validation run.
- Remove or qualify unsupported integration/maturity claims and unverified
  compatibility ranges. Keep illustrative framework-neutral usage distinct
  from tested or published integrations.
- Correct version examples and distinguish annotation mapping onto documents
  from a JPA implementation or relational SQL backend.
- Preserve dated audit/evidence documents unchanged.

## Non-goals

- Pushing commits or publishing/releasing artifacts.
- Bumping unpublished integration artifact versions or adding them to a
  release.
- Changing behavior or versions in the published core and Spring Boot starter.
- Rewriting dated audit/evidence snapshots.
- Broad framework upgrades or unrelated refactoring.

## Acceptance criteria

1. Every current Maven consumer of `embed-jnosql-core` uses `1.0.1`, and demo
   regeneration produces POMs with the same core version.
2. The source integration modules build against the published core without
   relying on a cached `1.0.0` artifact; their own unreleased artifact versions
   remain clearly identified as local/source-checkout artifacts.
3. The Quarkus CDI/indexing regression test passes and the Quarkus demo suite
   succeeds.
4. CI uses the Maven Wrapper consistently, tests Java 17 and 21, installs
   source-built modules in dependency order, and exercises all documented demo
   suites.
5. Maintained documentation and workflow result labels agree with verified
   coordinates, supported/tested versions, terminology, and observed test
   results. Historical audit/evidence documents are unchanged.
6. Targeted builds and tests pass, including checks that expose dependency
   resolution from a clean Maven local repository.
7. No push, tag, deployment, or publication is performed.

## Risks and mitigations

- Running every standalone demo in CI increases CI time. Measure the added
  duration during verification; retain the coverage unless it causes a
  material, demonstrated CI problem.
- Quarkus indexing may require more than dependency-version alignment. Keep the
  fix constrained to the failing CDI integration path and prove it with the
  regression test and demo suite.
- Some existing support descriptions may exceed available test evidence.
  Prefer precise, narrower claims over retaining unsupported compatibility
  promises.

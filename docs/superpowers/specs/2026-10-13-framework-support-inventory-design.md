# Framework Support Inventory Design

**Status:** Approved in conversation; awaiting written-spec review  
**Date:** 2026-10-13  
**Project:** EmbedJNoSQL  
**Phase:** First independently scoped phase of the repository audit

## Intent

Produce an internal, evidence-backed inventory of the Java framework support
that EmbedJNoSQL implements or claims. The inventory must help maintainers
distinguish code that exists from integrations that build, have passing tests,
have runnable demos, are published to Maven Central, or are merely described
in documentation.

This phase is an assessment deliverable. It does not change product behavior,
publish an artifact, or announce a support commitment.

## Scope

Inspect each framework named in repository code, build metadata, tests, demos,
CI, and public-facing documentation. At minimum, this includes Spring Boot,
Quarkus, Micronaut, Vert.x, Javalin, Ktor, Helidon, and Dropwizard if those
claims remain present in the current checkout. Add any other framework
integration discovered during the inventory.

For each framework, record:

| Evidence area | Required information |
| --- | --- |
| Claim | Where and how the project describes the integration or support level |
| Implementation | Integration module, source entry points, artifact coordinates, and mechanism |
| Versions | Framework, integration artifact, core artifact, and declared JDK versions |
| Build | Whether the module builds from source and the exact prerequisites |
| Tests | Executed test count, failures, errors, skipped tests, and test scope |
| Demo | Whether a demo starts and which behaviors it verifies; note setup and lifecycle boundaries |
| Publication | Whether the exact artifact/version is available from Maven Central |
| Reproducibility | Whether a consumer build resolves from Central alone or requires sibling artifacts installed locally |
| Evidence | Repository paths, CI run/job links, and Maven Central coordinates checked |
| Assessment | Gaps, contradictions, caveats, and a dated evidence status |

The inventory must assess the categories independently. Use these labels as
evidence dimensions, not as a single cumulative support badge:

- **Implemented** — integration code or a concrete framework usage pattern exists.
- **Module-build verified** — the integration module built from the checked-out source.
- **Test verified** — relevant tests ran; include failures, errors, and skips.
- **Demo verified** — a relevant demo test or documented runtime check completed.
- **Published** — the exact artifact coordinate and version were found on Maven Central.
- **Docs-only / unverified** — a claim has no matching implementation or executable evidence.

Do not infer official or production support from a passing compile, a generic
Java usage example, a skipped test, or documentation wording alone.

## Verification approach

1. Trace claims across README, other documentation, POMs, source, tests, and CI.
2. Build integration modules and run relevant targeted tests on the available
   supported JDK, recording exact commands, versions, and outcomes.
3. Test demo reproducibility as two separate cases:
   - a consumer build using only artifacts available from Maven Central;
   - a source build after the required sibling modules are built/installed locally.
4. Verify publication for each exact artifact/version against Maven Central.
5. Check the latest relevant CI run and record the conclusion of each pertinent
   job, without treating a release step's success as proof that the whole
   workflow passed.
6. Cross-check claims against the evidence and explicitly record missing or
   contradictory evidence.

The inventory reports observed facts and blockers. It does not repair the
Quarkus CDI failure, publish the Quarkus/Micronaut artifacts, align versions,
or rewrite public support claims.

## Deliverable

Create `docs/assessment/framework-support-inventory.md` as one concise internal
report with:

1. A date-stamped summary and scope.
2. The framework evidence matrix described above.
3. A short list of contradictions and reproducibility blockers.
4. Evidence references that allow a maintainer to reproduce each status.
5. An explicit statement that the inventory is not a support certification.

Do not modify README, changelog, release metadata, source, tests, CI, or the
user-provided attachment as part of this phase. Later audit phases may propose
separate changes after their own scoping and approval.

## Acceptance criteria

- Every framework claim discovered in the selected repository scope is either
  represented in the matrix or explicitly recorded as out of scope with a
  reason.
- Every status has a direct source, executed test/build record, CI record, or
  artifact lookup; stale assessments are treated as leads, not proof.
- Test outcomes retain failures, errors, and skips rather than reducing them
  to a pass/fail label.
- Source-built module evidence is never presented as proof that a standalone
  consumer can resolve the artifact from Maven Central.
- Documentation-only examples are not described as dedicated integrations.
- No product files or public claims are changed, and no artifact is published.

## Known starting observations

These are leads for verification, not final inventory conclusions:

- Core `1.0.1` and Spring Boot starter `1.0.1` were previously verified as
  published to Maven Central. The README still contains an unavailable-on-
  Central claim and stale test-count/framework-version statements.
- The Spring Boot starter declares Spring Boot 4.0.0 and Java 17. Its targeted
  starter tests and demo tests passed locally during initial discovery.
- The Quarkus and Micronaut integration modules declare version `1.0.0`.
  Their standalone demos could not resolve those artifacts from Maven Central;
  after local installation of the source-built integrations, Micronaut's demo
  tests passed, while the Quarkus demo encountered a CDI build-time error:
  producer return type `jakarta.nosql.Template` was not found in the index.
- The Vert.x evidence is a demo usage pattern rather than a dedicated
  integration artifact. Its local demo tests passed.
- Workflow run
  [38352007375](https://github.com/kael-tombo/embed-jnosql/actions/runs/38352007375)
  completed with failure: the core and starter verification and both
  publication steps succeeded, but the post-publish validation job failed
  during its module-verification/install step. The exact failure detail must
  be verified before it is attributed to a framework.

## Constraints and non-goals

- Keep the audit NoSQL-specific; do not introduce relational/SQL assumptions
  when characterizing repository APIs.
- Do not modify the attachment or expose credentials.
- Do not publish packages, deploy websites, create external announcements, or
  claim official support.
- Do not fix unrelated or observed defects in this phase. Report them as
  evidence-backed follow-up candidates instead.
- The broader engine, Console UI, integrations, demos, test quality, and
  public-claims audit remains decomposed into later, separately scoped phases.

## Review and implementation gates

This design has been approved conversationally. The written spec must be
self-reviewed for placeholders, contradictions, scope, and ambiguity, then
reviewed by the user. Only after written-spec approval should an
implementation plan be created. The inventory itself must not be generated
until the implementation plan has been reviewed and its execution method
selected.

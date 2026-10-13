# Maven Central Full Release Design

**Status:** Written spec approved; implementation plan in review
**Target release:** `1.0.2`
**Scope:** Publish all first-party library and integration artifacts

## Goal

Push and publish a coherent `1.0.2` release of the first-party library and
integration artifacts to Maven Central. Release publication must be gated on
validation of the release commit, and each published coordinate must have the
metadata and signed artifacts required by Maven Central.

This design follows the user's explicit follow-up request to push and publish.
It supersedes the earlier repository-remediation design's non-goal of publishing
artifacts. The user selected version `1.0.2` and confirmed that demos are not
part of the Central release.

## Current state

- The root project and Spring Boot starter are configured for the existing
  `maven-central` profile and the publish workflow currently deploys only those
  two artifacts.
- Quarkus is a Maven aggregator with a parent POM, runtime module, and
  deployment module. Its child POMs refer to the aggregator as their parent.
- Micronaut and CLI are standalone JAR projects. Their POMs do not currently
  have the Central publishing profile used by core and the starter.
- Quarkus, Micronaut, and CLI currently depend on core `1.0.1`; their own
  artifact versions are `1.0.0`.
- Existing demos are consumers and validation projects, not release artifacts.
- The existing publish workflow supports tag pushes and a manual dispatch
  version input. The manual input is not currently checked against POM
  versions.

## Release artifacts

Publish these seven coordinates at version `1.0.2`:

| Artifact | Packaging | Purpose |
|---|---|---|
| `io.github.kael-tombo:embed-jnosql-core` | `jar` | Core library |
| `io.github.kael-tombo:embed-jnosql-spring-boot-starter` | `jar` | Spring Boot integration |
| `io.github.kael-tombo:embedjnosql-micronaut-integration` | `jar` | Micronaut integration |
| `io.github.kael-tombo:embed-jnosql-cli` | `jar` | CLI |
| `io.github.kael-tombo:embed-jnosql-quarkus-extension` | `pom` | Quarkus parent/aggregator POM |
| `io.github.kael-tombo:embed-jnosql-quarkus-extension-runtime` | `jar` | Quarkus runtime module |
| `io.github.kael-tombo:embed-jnosql-quarkus-extension-deployment` | `jar` | Quarkus deployment module |

Demo applications and generated feature demos remain unpublished. Their
dependencies on first-party release artifacts are updated to `1.0.2` so the
release validation exercises the candidate coordinates.

## Design

### 1. Publishing configuration

- Keep the existing project layout; do not introduce a new shared parent POM.
- Reuse the root/starter Central publishing conventions for the standalone
  Micronaut and CLI POMs, including project metadata, sources, Javadocs,
  signatures, and the Central Publisher plugin.
- Put shared publishing metadata and configuration in the Quarkus aggregator
  where it can be inherited by both child modules. Publish the aggregator POM
  because the child POMs declare it as their parent.
- Use the existing GitHub Actions secrets for Central credentials and GPG.
  Never add credentials to source files, logs, or workflow arguments beyond
  the existing environment-variable pattern.
- Update internal first-party dependencies and child/parent versions to
  `1.0.2`. Keep demo application versions independent.

### 2. Release workflow

- Keep tag-triggered releases using `v1.0.2` as the canonical release signal.
- Push the release commit to the intended branch, wait for its required hosted
  CI checks to pass, and then push the tag. The tag-triggered release workflow
  independently repeats its full pre-publication validation before deployment.
- Require all pre-publication validation jobs to pass before any deployment to
  Maven Central. Validation covers core, starter, Quarkus parent/runtime/
  deployment, Micronaut, CLI, application demos, and all 20 single-feature
  demos.
- Publish in dependency order: core; starter, Micronaut, and CLI; then the
  Quarkus aggregator reactor (parent, runtime, deployment). Install core
  locally for dependent build steps where necessary.
- Validate that every first-party release POM resolves to version `1.0.2`.
  For manual dispatch, require the requested version to match the POM versions;
  do not let an input value silently diverge from the artifacts being deployed.
- Keep post-publication checks clearly distinct from pre-publication tests.
  Verify that all seven coordinates become resolvable from Maven Central,
  allowing for repository propagation delay. A failed validation must never
  produce a success-shaped summary.
- Do not retry a partially published immutable release under the same version.
  Surface the exact failed coordinates and stop for operator intervention.

### 3. Push and tag safety

- Commit the approved release changes and push them with a normal, non-force
  push. Create and push `v1.0.2` only after the pushed commit's required CI
  checks pass; the release workflow must then pass its own validation before
  publishing.
- Do not overwrite an existing tag or release coordinate. If the remote branch
  has advanced or repository protection prevents the push, stop and report the
  blocker rather than force-pushing or bypassing protection.
- The release tag must identify the exact validated commit used by the
  publication workflow.

### 4. Documentation

- Add a `1.0.2` changelog/release entry that describes only verified changes
  and artifact availability.
- Update maintained dependency examples, release workflow instructions, and
  support/validation documentation to distinguish published artifacts from
  demo modules.
- Preserve historical release notes and dated audit/evidence documents.

## Non-goals

- Publishing demo applications or generated single-feature demos.
- Refactoring all modules to a new Maven parent hierarchy.
- Changing application behavior solely to justify a version number.
- Publishing artifacts manually from the local workstation.
- Force-pushing, overwriting tags, or bypassing branch protection.

## Acceptance criteria

1. All seven listed first-party artifacts have version `1.0.2` and valid,
   Central-compliant POM metadata.
2. Each deployable JAR includes source and Javadoc artifacts and valid
   signatures; the Quarkus parent POM is published with its child artifacts.
3. The complete pre-publication validation suite passes before the publishing
   job can start.
4. The workflow publishes the seven artifacts in dependency order and checks
   that all seven coordinates resolve from Central after publication.
5. Workflow summaries report only checks that actually ran and do not claim
   success after any relevant failure.
6. The pushed release tag points to the exact validated release commit. No
   existing tag or artifact version is overwritten.
7. Demo applications remain consumers and are not published.

## Risks and mitigations

- A partially accepted Central release is immutable and cannot be repaired by
  reusing the same version. Validate all POMs and artifacts before deployment;
  stop on partial failure and require a new version for remediation.
- Central synchronization may lag publication. Use bounded polling for
  coordinate availability and report propagation timeout distinctly from
  deployment failure.
- Quarkus consumers need both runtime and deployment artifacts and the parent
  POM. Publish all three from the reactor and verify the generated extension
  metadata before release.
- Local work may be based on commits not yet present on remote `main`. Recheck
  ancestry and remote state immediately before pushing; never force-push.
- Hosted release credentials cannot be verified locally. The workflow must
  report missing or invalid secrets as an explicit publish failure without
  exposing secret values.

# Release Notes — EmbedJNoSQL v1.0.2

**Release status as of 2026-10-13:** prepared; Maven Central publication is pending
**Planned tag:** `v1.0.2`
**License:** Apache License 2.0

> This document describes the prepared release candidate. The tag has not been pushed and none of
> the `1.0.2` artifacts has been published to Maven Central. The candidate should be treated as
> available only after hosted release validation and post-publication checks succeed.

## Release scope

This release prepares the seven first-party library and integration artifacts for Maven Central.
Demo applications are validation consumers and are not published artifacts.

| Group ID | Artifact ID | Version |
|---|---|---|
| `io.github.kael-tombo` | `embed-jnosql-core` | `1.0.2` |
| `io.github.kael-tombo` | `embed-jnosql-spring-boot-starter` | `1.0.2` |
| `io.github.kael-tombo` | `embedjnosql-micronaut-integration` | `1.0.2` |
| `io.github.kael-tombo` | `embed-jnosql-cli` | `1.0.2` |
| `io.github.kael-tombo` | `embed-jnosql-quarkus-extension` (aggregator POM) | `1.0.2` |
| `io.github.kael-tombo` | `embed-jnosql-quarkus-extension-runtime` | `1.0.2` |
| `io.github.kael-tombo` | `embed-jnosql-quarkus-extension-deployment` | `1.0.2` |

## Changes

- Align the first-party release modules and demo consumer references on version `1.0.2`.
- Add Maven Central publishing metadata and profiles for the Micronaut integration, CLI, and
  Quarkus extension modules; correct inherited Quarkus SCM metadata.
- Gate publication on the complete core, integration, framework-demo, showcase-demo, and
  feature-demo validation set.
- Publish artifacts in dependency order and check Maven Central for the POMs, main JARs, source
  JARs, Javadoc JARs, and signatures after deployment.
- Keep demo applications out of the published artifact set.

This is a distribution and release-process update; it does not introduce a new database runtime
feature.

## Validation and availability

The release preflight confirmed that all seven `1.0.2` coordinates were absent from Maven Central
at the time it ran. Effective-POM, metadata, packaging, and workflow syntax checks have been
performed locally. The full release-candidate test matrix, hosted CI, tag push, publication, and
post-publication verification are still required before these coordinates can be used from Maven
Central.

Until publication is verified, install the relevant modules from the repository checkout as
described in the [README](./README.md).

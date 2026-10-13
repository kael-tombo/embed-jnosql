# Release Notes — EmbedJNoSQL v1.0.2

**Release status as of 2026-10-13:** tagged; Maven Central deployment is in progress
**Release tag:** [`v1.0.2`](https://github.com/kael-tombo/embed-jnosql/tree/v1.0.2)
**License:** Apache License 2.0

> The `v1.0.2` tag is pushed and all hosted validation gates have passed. Maven Central deployment
> and artifact/signature verification are still in progress. Treat the coordinates below as
> unavailable from Central until the release workflow confirms publication.

## Release scope

This release comprises seven first-party library and integration artifacts for Maven Central.
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

The release preflight confirmed that all seven `1.0.2` coordinates were absent from Maven Central.
The hosted core, integration, application-demo, and feature-demo validation jobs have passed. The
publish job and post-publication checks are still running; Central availability is not yet
confirmed.

Until publication is verified, install the relevant modules from the repository checkout as
described in the [README](./README.md).

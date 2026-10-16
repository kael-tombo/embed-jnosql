# Release Notes — EmbedJNoSQL v1.0.3

**Status:** Release candidate; not yet published
**Target version:** `1.0.3`
**License:** Apache License 2.0

> This document describes a release candidate prepared from committed `HEAD`. No `v1.0.3` tag
> has been created, and no `1.0.3` artifacts have been published to Maven Central.

## Release scope

The candidate aligns the seven first-party library and integration artifacts and their demo
consumers on version `1.0.3`. Demo applications are validation consumers and are not published
artifacts.

| Group ID | Artifact ID | Version |
|---|---|---|
| `io.github.kael-tombo` | `embed-jnosql-core` | `1.0.3` |
| `io.github.kael-tombo` | `embed-jnosql-spring-boot-starter` | `1.0.3` |
| `io.github.kael-tombo` | `embedjnosql-micronaut-integration` | `1.0.3` |
| `io.github.kael-tombo` | `embed-jnosql-cli` | `1.0.3` |
| `io.github.kael-tombo` | `embed-jnosql-quarkus-extension` (aggregator POM) | `1.0.3` |
| `io.github.kael-tombo` | `embed-jnosql-quarkus-extension-runtime` | `1.0.3` |
| `io.github.kael-tombo` | `embed-jnosql-quarkus-extension-deployment` | `1.0.3` |

## Changes

- Simplify the public website while retaining its core install, feature, and demo content.
- Set light mode as the default for the embedded console.
- Expand Playwright coverage across console navigation, core data workflows, backup/restore,
  CDC, schema and indexes, vector search, server operations, and responsive behavior.
- Add framework integration guidance for Vert.x, Javalin, Helidon SE, Dropwizard, and other JVM
  web frameworks.
- Align first-party release POMs and demo consumer dependencies on `1.0.3`.

This is a documentation, console presentation, test-coverage, and release-alignment update; it
does not claim new database runtime features.

## Validation status

- All seven release POMs resolve to `1.0.3`.
- Maven `clean verify -Pcoverage-check` passed after the candidate version edits.
- The full Playwright baseline ran 368 tests: 366 passed and 2 failed. The failures were:
  - `tests/e2e/tests/cross-cutting-coverage.spec.ts`: the top bar did not wrap at a viewport width
    of 1100 px.
  - `tests/e2e/tests/responsive.spec.ts`: the overview panel overflowed horizontally at a viewport
    width of 390 px.
- These two baseline failures are recorded and were not fixed as part of this release preparation.
- Maven Central preflight found all seven `1.0.3` coordinates absent. Candidate artifacts, signatures,
  and hosted release gates remain unverified pending a future release run.

Version `1.0.2` remains the latest published release until a separate, approved release process
creates the tag and publishes the candidate.

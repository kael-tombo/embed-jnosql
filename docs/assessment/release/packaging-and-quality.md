# Sub-Assessment: Release Quality, Packaging, and Distribution

## 1. Build Reproducibility & Packaging

- **Maven Compiler**: Configured for `release 17` targets.
- **Reproducible Builds**: `project.build.outputTimestamp` pinned to `2026-09-21T00:00:00Z`.
- **Shading Policy**:
  - Excludes `net.bytebuddy` from Jackson.
  - Excludes `org.slf4j:slf4j-simple` from shaded JAR to prevent logging framework collisions.
  - Bundles necessary Jackson databind and JSR-310 modules.
  - Shaded JAR size: **2.94 MB** (comfortably under the 5 MB budget).

## 2. Test Suite Quality & Coverage

- **Total Test Cases**: 857 automated unit and integration tests.
- **Coverage Profile**: JaCoCo 70% line coverage enforcement available via profile `coverage-check` (`mvn verify -P coverage-check`).
- **Isolation**: Tests use ephemeral temporary directories (`Files.createTempDirectory`) or in-memory engines to prevent test state cross-contamination.

## 3. Central Publishing Configuration

- Profile `maven-central`:
  - `maven-source-plugin` attaches `-sources.jar`.
  - `maven-javadoc-plugin` attaches `-javadoc.jar`.
  - `maven-gpg-plugin` for artifact signing.
  - `central-publishing-maven-plugin` (0.4.0) targeting Sonatype Central.

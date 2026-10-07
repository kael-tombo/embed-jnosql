# Size & Footprint Audit — strict 5 MB core-runtime requirement

## Scope
Every artifact a Maven Central consumer pulls with
`org.embeddedjnosql.db:embed-jnosql-core:1.0.0`, measured in bytes — not assumed.

## Expected Behavior
Product philosophy (doc 01) requires an embedded, lightweight runtime. The
public claim must state exactly what is included: the measured number must
cover the core jar **plus mandatory runtime dependencies**, because a consumer
cannot run the database without them.

## Current Implementation
- Shaded jar: `target/embed-jnosql-core-1.0.0.jar` (shade replaces the main jar in place; no separate `-shaded` name).
- Runtime dependencies (pom.xml): jackson-databind + jackson-jsr310 (JSON), slf4j-api (logging), jnosql-mapping-api-core (annotations), jakarta.enterprise.cdi-api (annotations), jakarta.persistence-api (provided/optional, annotations only).
- Test-only: junit-jupiter. Profile-only (not published): jmh, micrometer. `provided/optional`: hibernate-core.
- Module artifacts: `spring-boot-starter`, `quarkus-extension` (runtime+deployment), `micronaut-integration`, `cli` — all separate poms, none required by core.

## Validation Performed (measured 2026-09-21, after SZ-06 fix)

```bash
mvn -DskipTests package && stat -c %s target/embed-jnosql-core-1.0.0.jar
mvn dependency:tree            # byte-buddy traced to jackson-databind 2.17.0
mvn verify -Pcoverage-check    # SZ-06 fix verified: 689 tests green + 70% gate
unzip -l target/embed-jnosql-core-1.0.0.jar | awk ...   # uncompressed content
```

**Measured figures (bytes):**

| Artifact / component | Size |
|---|---|
| **Published core jar (after SZ-06 fix)** | **3,032,897 (2.89 MB) — UNDER 5 MB** |
| Core jar before fix (shaded, with byte-buddy) | 7,236,861 (6.90 MB) |
| Own `org/embeddedjnosql/db/**` classes (uncompressed) | 1,063,505 |
| byte-buddy (uncompressed, removed) | 10,159,234 (4.2 MB compressed ≈ 68% of old jar) |
| jackson (databind+core+annotations+jsr310, uncompressed) | 5,874,399 |
| Console static assets | 11 files (html/css/js/svg + favicon) |
| Dep jars (for thin-jar consumers): jackson-databind 1,649,184 · jackson-core 581,556 · jackson-annotations 78,488 · jsr310 132,320 · slf4j-api 68,115 · cdi-api 151,307 · jnosql-mapping-api-core 29,847 | ≈ 2.69 MB total |
| Module artifacts (separate, optional): spring-boot-starter, quarkus-extension (runtime+deployment), micronaut-integration, cli | not part of core claim |

## Findings

| ID | Status | Severity | Finding |
|---|---|---|---|
| SZ-01 | **PASS** | — | **Core runtime jar 3,032,897 bytes (2.89 MB) < 5 MB** — measured after SZ-06. Claim is now honest as stated. |
| SZ-02 | **RESOLVED** | High | Was: shaded jar 7,236,861 bytes with bundled byte-buddy — over the limit. Fixed by SZ-06; no claim wording change needed. |
| SZ-03 | PASS | Medium | `slf4j-simple` excluded from shade (logback/JBoss-logging conflicts); consumers bring their own backend. Verified in pom.xml artifactSet excludes. |
| SZ-04 | PASS (documented) | Low | Console ships in the core jar (11 static assets, optional at runtime — console disabled by default). Philosophy-compatible; `embed-jnosql-console` module deferred to 1.1. |
| SZ-05 | NOT IMPLEMENTED | Low | Further split (`embed-jnosql-sql`, `embed-jnosql-nosql`, storage modules) deferred to 1.1 — current single-jar shape is defensible at 2.89 MB. |
| SZ-06 | **FIXED** | **High** | **byte-buddy 4.2 MB was bundled in the published jar via a jackson-databind 2.17.0 transitive dependency; nothing in the codebase references it.** Excluded in pom.xml; verified by full suite + coverage gate (exit 0) and by `unzip -l` (0 bytebuddy entries). Jar: 7,236,861 → **3,032,897**. |

## Improvement Plan
1. **Pre-release (this audit):** README/website claim wording — see doc 40/39 corrections; CI size gate below.
2. **1.1:** evaluate thin-jar publication + explicit module split.

## CI size gate (added this audit)
`.github/workflows/ci.yml` build job now asserts the published core jar stays
under the approved limit (see workflow diff in this commit). The gate fails CI
if future work pushes the core jar past the limit without a deliberate,
documented decision.

## Final Assessment
- **Status:** PASS — measured 2.89 MB published core jar (< 5 MB limit), CI gate enforcing.
- **Release impact:** none — footprint requirement met.
- **Next action:** thin-jar publication remains optional 1.1 work.

# 28 — Plain Java Integration

## Scope
Zero-framework embedded usage (the primary target persona).

## Expected Behavior
Single dependency; optional deps degrade gracefully; the shaded jar runs standalone (`java -jar`) including the console.

## Current Implementation
- `embed-jnosql-core-1.0.0.jar` (shaded) with manifest main class; console launches from CLI args (`--port --engine --data-dir --sync`) — used by this audit's preview server across multiple restarts.
- Optional/provided deps: CDI, JNoSQL API, JPA API, Hibernate, slf4j-simple (excluded from shade) — plain-Java consumers get a working default logger via their own addition or none at all (JUL fallback for SLF4J absent-provider prints a warning, harmless).

## Validation Performed
- Shaded jar executed repeatedly in this audit (console server on 8081, FILE engine, data persisted/recovered).
- `EmbedJNoSQLFeatureDemo` wired as exec-maven-plugin main class.
- Full suite exercises plain embedded usage on every test.

## Evidence
Preview server sessions; `pom.xml` jar/shade config; exec plugin config.

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| PJ-01 | CONFIRMED | Low | CLI arg parsing is ad-hoc in `EmbedJNoSQL.main`; errors print usage inconsistently. Polish item. |
| PJ-02 | ACCEPTABLE | Low | No native deps; Java 17+ bytecode (release 17) — matches README badge. |

## Improvement Plan
Argument `-h` help + input validation polish.

## Acceptance Criteria
Standalone jar run verified (met across restarts).

## Final Status
**PASS**

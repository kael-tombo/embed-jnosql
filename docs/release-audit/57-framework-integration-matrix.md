# 57 — Framework Integration Matrix

## Scope
Framework compatibility at a glance, with honest verification levels.

| Integration | Artifact | Automated Tests | Live Demo Evidence | CI Coverage | Claim Level |
|---|---|---|---|---|---|
| Plain Java (embedded) | core jar | ✅ suite | ✅ this audit (jar runs) | ✅ core CI | **SUPPORTED** |
| Plain Java (console server) | shaded jar | console tests | ✅ repeated restarts | ✅ | **SUPPORTED** |
| Spring Boot 3.2 | starter + demo | module unit tests (not run here) | ✅ prior demo runs | ❌ | D functional — CI pending |
| Quarkus 3.8 | extension + demo | demo tests (not run here) | ✅ prior demo runs | ❌ | D functional — CI pending |
| Micronaut 4.2 | integration + demo | demo tests (not run here) | ✅ prior demo runs | ❌ | D functional — CI pending |
| Vert.x 4.5 | demo only | verticle test (not run here) | ✅ prior demo runs | ❌ | example — CI pending |
| JNoSQL annotations | core (optional dep) | ✅ `EmbedRepositoryTest` etc. | — | ✅ | annotation dialect |
| JPA annotations | core (optional dep) | ✅ `JpaEntityManagerTest` | — | ✅ | annotation dialect |
| JDBC | — | — | — | — | not provided |
| Testcontainers | — | — | — | — | not provided (by design) |
| Kafka CDC connector | core (optional code) | excluded from coverage | ❌ never executed | ❌ | experimental, unclaimed |

## Definition of Done for "SUPPORTED" (rule 6)
Green CI job building the module + executing its demo boot/integration test, on every push to main.

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| FM-01 | CONFIRMED | Medium | Core + console + annotations = SUPPORTED today; starters = functional-with-evidence, CI-DoD pending (44-CI-04). |

## Final Status
**CONDITIONAL PASS**

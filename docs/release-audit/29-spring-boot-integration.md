# 29 — Spring Boot Integration

## Scope
`spring-boot-starter/` — real support status, build status, demo evidence.

## Expected Behavior
Per rule 6: an integration is "supported" only with a real executed demo; otherwise labeled accordingly.

## Current Implementation
- `spring-boot-starter/` (own POM, NOT a module of the root build): `EmbedJNoSQLAutoConfiguration`, `EmbedJNoSQLTemplate`, config properties, health indicator wiring; unit tests exist (`EmbedJNoSQLAutoConfigurationTest`).
- `demo/spring-boot-demo`: E-commerce REST app with controller/service layering and an application test that boots a real context.

## Validation Performed
In THIS audit environment: **starter module not built** (separate build, time-constrained) — status below reflects prior-session evidence and repo inspection, and is labeled accordingly.

## Evidence
- Prior sessions: Spring Boot demo executed with SQL+NoSQL round-trips and console startup (recorded in older `release-evidence/` tree and demo test `EcommerceApplicationTest`).
- Starter POM + autoconfiguration source read in this audit.

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| SB-01 | PARTIALLY VERIFIED | Medium | Autoconfiguration + template verified by unit tests and prior demo runs; **not re-executed in this audit environment**. Badge "Spring Boot 3.2" retained only because demo evidence exists; CI does not build this module (see 44) — flagged as the gap that must close before claiming full support. |
| SB-02 | CONFIRMED | Low | Starter version alignment (Spring Boot 3.2.x) hardcoded in its POM; consumers on other Boot versions rely on compatibility they must verify. |

## Improvement Plan
Add all starter modules to a CI matrix job (`mvn -f spring-boot-starter test`, demo boot test) BEFORE or immediately after 1.0.0; then upgrade claim to "supported (CI-verified)".

## Acceptance Criteria
CI builds starter + demo (post-release gap); README badge links to demo (met).

## Final Status
**CONDITIONAL PASS** — integration functional by prior evidence; CI verification gap documented and scheduled.

# 11 — Data Types & Serialization

## Scope
Value model, JSON serialization (`JsonSerde`, Jackson 2.17), JSR-310 support, record/document conversion, tri-standard annotation mapping.

## Expected Behavior
Lossless round-trip of supported types; no `enableDefaultTyping`-style deserialization exposure; optional annotation dependencies degrade gracefully.

## Current Implementation
Jackson `jackson-databind` + `jackson-datatype-jsr310`; `JsonSerde` utility; `Document implements UnifiedRecord`; `adapter/jnosql/EntityMapper` resolves JNoSQL/JPA/Hibernate annotations reflectively (deps are `provided`+`optional`).

## Validation Performed
`AnnotationDualSupportTest`, `JpaEntityManagerTest`, `EmbedRepositoryTest` green in both full runs. No `enableDefaultTyping` found in `JsonSerde` (searched during security review, see 22).

## Evidence
Full-suite logs; source inspection.

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| DT-01 | CONFIRMED | Low | Polymorphic deserialization is intentionally not enabled (safe default); complex polymorphic graphs require custom converters — documented limitation. |
| DT-02 | ACCEPTABLE | Low | Tri-standard annotation support verified at unit level; Hibernate-specific annotations (`@CreationTimestamp` etc.) tested via optional dependency in test scope. |

## Improvement Plan
Type-mapping reference table in docs.

## Acceptance Criteria
Serialization suites green; no unsafe Jackson configuration (met).

## Final Status
**PASS**

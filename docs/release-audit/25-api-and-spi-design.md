# 25 — API & SPI Design

## Scope
Public facade API, SPI surfaces, backward-compatibility risk of this audit's changes.

## Expected Behavior
Clean, discoverable public API; SPI changes backward compatible; 1.0.0 as semver baseline.

## Current Implementation
- Facade: `EmbedJNoSQL.inMemory()/temporary()/embed().build()/create(config)`, model accessors, `sql()`, `beginTransaction()`, console lifecycle.
- SPI: `StorageEngine` (+ new default methods `collectionNames()` and, from the R-62 fix, `ensureCollection(String)`), `WriteAheadLog`, `FileEnginePool`.
  - **`ensureCollection(String)` (R-62, 2026-09-23):** called when a collection is *created* (SQL DDL, the console's create route, repository/annotation materialisation) so its existence is durable even with no records. Default: no-op returning `false`, so an existing external `StorageEngine` implementation keeps compiling and keeps its old semantics. Implementations: FILE writes an empty `{}` snapshot; LSM_TREE/B_TREE record the name in a `.collections` registry behind an atomic move (their identity is otherwise derived from `collection:key` records, so an empty collection has no key); IN_MEMORY lists it for the process lifetime and promises nothing beyond it.
- Annotations adapters, `EntityQuery`, reactive API, JPA bridge.
- Compatibility of audit changes: `MVCCManager.commit` 2-arg overload retained (4 pre-existing direct callers compile unchanged); `StorageEngine.collectionNames()` and `ensureCollection()` are default methods (external implementors unaffected); `Transaction` signature unchanged; `BTreeEngine(Path)` and `BTreeEngine(Path,int)` are retained alongside the new 3-arg constructor that takes a flush interval (R-68).

## Validation Performed
Full suite green post-change (677 tests) — including `DeepTransactionTest` which calls `mvcc.commit` directly; compile success of pre-fix-API test subset against post-fix code (overload intact).

## Evidence
This audit's diffs + full-suite logs.

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| API-01 | CONFIRMED | Low | API surface is broad for a 1.0 (reactive, JPA bridge, adapters). Acceptable; deprecation policy documented in 47. |
| API-02 | ACCEPTABLE | Low | `mvcc()` exposure returns the live manager — power-user surface, fine for embedded. |

## Improvement Plan
Freeze 1.0 API signatures; publish an upgrade guide for 1.1.

## Acceptance Criteria
Backward-compatible changes only in this audit (verified by suite + compile checks).

## Final Status
**PASS**

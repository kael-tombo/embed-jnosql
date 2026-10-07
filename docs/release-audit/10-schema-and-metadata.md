# 10 — Schema & Metadata

## Scope
Schema handling (schema-free documents + optional registered schemas), metadata APIs, collection enumeration.

## Expected Behavior
`getCollectionNames()` reflects persisted state after restart; schema registration persists and is listed; indexes persist per collection.

## Current Implementation
- Facade: `documentCollection(name)` lazily materializes; `getCollectionNames()` returns in-memory map keys.
- Schema endpoint/panel verified live (POST /api/schema round-trip in prior console session).
- Indexes persist to `<dataDir>/.indexes` per collection (`DocumentCollection.loadIndexes/saveIndexes`).

## Validation Performed
**Regression added**: `ReleaseAuditRegressionTest.persistedCollectionsAreRediscoveredAfterRestart` — creates a collection via SQL, closes, reopens, asserts `getCollectionNames().contains("products")` + SQL + document reads. **Failed pre-fix** (`expected: <true> but was: <false>` — see `.freebuff/prefix-failures.log`), **passes post-fix**.

## Evidence
- Fix: `EmbedJNoSQL.create()` now calls `materializePersistedCollections()`, backed by the new `StorageEngine.collectionNames()` SPI (FileEngine implements it from on-disk state).

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| SC-01 | CONFIRMED (fixed) | High | Collections invisible after restart until touched by name; console and API enumeration empty on fresh start despite disk data. |
| SC-02 | CONFIRMED | Low | B-Tree/InMemory engines return empty `collectionNames()` (InMemory is ephemeral by definition; B-Tree lacks per-collection identity) — a documented engine-capability difference, not a defect. |

## Improvement Plan
Mirror the on-disk discovery for B-Tree snapshots post-1.0.

## Acceptance Criteria
Restart-enumeration regression test green in CI (met — included in suite).

## Final Status
**PASS** (after fix; was RELEASE BLOCKER pre-fix)

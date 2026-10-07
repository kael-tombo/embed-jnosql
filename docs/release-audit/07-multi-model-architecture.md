# 07 — Multi-Model Architecture

## Scope
Coherence of the multi-model claim: one engine substrate serving documents, KV structures, column families, and SQL.

## Expected Behavior
Per README: "Both engines share the same in-memory or disk storage substrate."

## Current Implementation
Facade `EmbedJNoSQL` holds exactly one `StorageEngine`; `DocumentCollection`, all KV buckets, `ColumnFamily`, and `SqlEngine` read/write through it. Namespaces share the engine's key space (`DocumentCollection` uses the collection name; buckets use the bucket name; `KeyValueBucket` stores expirations under `meta_store`).

## Validation Performed
- Code read of facade + model classes (write paths all call `engine.put/putRecord`).
- Cross-model live test: SQL `INSERT INTO products` → visible via `/api/collections/products` and document API after restart.

## Evidence
Facade constructor (read in this audit): single `this.engine = config.storageEngine().create(...)`; `SqlEngine(this)` over the same instance.

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| MM-01 | CONFIRMED | Low | Namespace collisions possible between a document collection and a bucket of the same name (shared key space). Not observed in practice; documented as a caveat in 53-defect-register. |
| MM-02 | ACCEPTABLE | Low | Column-family and KV models do not emit CDC/events uniformly (only document layer + buckets emit); observability asymmetry noted. |

## Improvement Plan
Namespace prefixing strategy post-1.0 (requires migration); embed event emission.

## Acceptance Criteria
Substrate-sharing claim verified by cross-model test (done); no README claim of isolation between models.

## Final Status
**PASS**

# 19 — TTL & Expiration

## Scope
TTL on documents and KV entries; expiration semantics and cleanup.

## Expected Behavior
TTL'd entries become invisible after expiry; space eventually reclaimed; TTL across restarts honored.

## Current Implementation
- Documents: `insert(doc, ttlSeconds)` sets `expiresAt`; `isExpired()` checks on read; `cleanupExpired()` sweeps.
- KV: expirations serialized under `meta_store` collection (`kv_expirations_<bucket>`), so they persist across restarts for durable engines.

## Validation Performed
TTL-related tests green in full suites; code read of expiry checks (`isExpired`, `cleanupExpired`, KV expiration storage).

**Correction (2026-09-22, round 3):** the green suite above did **not** cover the persistence round-trip, and this PASS was wrong for documents on every engine. Driving the running console exposed it: `products.json` on disk held `"expiresAt":1790053862005` while the API read the document back as `expiresAt:null`, a 2s TTL left the document present with `expired:false` after 4s, and `ttlStats.withTtl` stayed 0. Root cause (R-33): `JsonSerde.fromJson`'s custom `Document` path rebuilt only `id` + `fields`. Fixed and now covered by `DocumentTtlPersistenceTest`; live re-verified (`expiresAt:1790057665804`, 2s TTL → `"expired":true`, `/stats` → `withTtl:1`).

## Evidence
`DocumentCollection` lines 440–448 (`cleanupExpired`); `KeyValueBucket` lines 43–77.

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| TTL-01 | CONFIRMED | Medium | Expiry is lazy: expired entries remain on disk until read or swept; no background reaper. Acceptable for 1.0; documented. |
| TTL-02 | ACCEPTABLE | Low | TTL timestamps wall-clock based; clock skew can shorten/lengthen TTLs (standard caveat). |
| TTL-05 | CONFIRMED (2026-09-22) | Low | API-shape pitfall on the row route: `PUT/POST /api/columns/{family}/{row}` accepts a flat body and writes **one column per key**, so `{"value":"x","ttlSeconds":2}` silently creates columns literally named `value` and `ttlSeconds` instead of "a value with a TTL". Per-column TTL needs `…/{row}/column/{column}` (write) or the console's nested form `{"col":{"value":"x","ttlSeconds":60}}`. No data is lost and the console only sends correct shapes, but the trap is now recorded rather than implied. |
| TTL-04 | CONFIRMED (2026-09-22) | Low | KV TTL is Java-API only: `KeyValueBucket.put(key, value, Duration)` has no REST/console route (the console exposes plain KV PUT/GET without TTL). No fake UI exists — the console simply does not offer KV TTL — but the gap is explicit here rather than implied by the panel. |
| TTL-03 | FIXED (2026-09-22) | High | **Document TTL never took effect (R-33).** `JsonSerde.fromJson`'s custom `Document` deserializer restored only `id` and `fields`, silently dropping the persisted `expiresAt` on every read: `isExpired()` was always false, expired documents were never filtered by reads nor removed by `cleanupExpired()`, and `ttlStats.withTtl` reported 0 — while the file on disk did contain the expiry. Falsified live on the preview server; fixed in `JsonSerde`; regression `DocumentTtlPersistenceTest` (2 tests). |

## Improvement Plan
Scheduled active-expiry sweep; TTL metrics.

## Acceptance Criteria
TTL tests green; behavior documented (met).

**KV half re-verified with evidence (2026-09-22, round 3):** `KvTtlPersistenceTest`
(3 tests) proves expiry on read (`get` returns null and `keys()` hides the expired key
after its TTL), that the expiry is honored **after a restart** on the FILE engine — which
only happens if `meta_store` expirations were reloaded, so the persistence claim now has
behavioural proof rather than code reading — and that keys written without a TTL never
expire. Newly noted: KV TTL has no REST route (TTL-04).

**Column-family half verified (2026-09-22, round 3):** live on the running console —
`POST /api/columns/inventory/item-1/column/shortlived {"value":"temporary-note","ttlSeconds":2}`
→ `GET …/ttl/shortlived` returns `hasTtl:true`, `expiresAt:1790055300188`,
`remainingTtlSeconds:1`; after 3s both the column and its TTL route return **404** — so the
per-column expiry is read back out of storage, not just held in memory.
`ColumnFamilyTtlPersistenceTest` (2 tests) adds the durable half: with the FILE engine the
TTL metadata is present after a close/reopen, an expired column reads null after the
restart, and a column written without TTL never expires.

## Final Status
**PASS (after correction — see TTL-03; KV and column-family halves re-verified in round 3)**

The original PASS did not survive live validation this round; the document-TTL path
was broken end to end and is now fixed and regression-covered. KV expirations were not
part of the falsification and are re-stated here only at their earlier confidence.

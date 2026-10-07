# 50 — Java In-Memory Database Comparison

## Scope
Comparison vs the common Java in-memory options per the Baeldung reference.

## Verified Comparison
| Dimension | EmbedJNoSQL | Redis-embedded style (e.g. ephemeral KV libs) | MapDB-style | HSQLDB/H2 (in-mem) |
|---|---|---|---|---|
| Pure Java, no daemon | ✅ | ✅ | ✅ | ✅ |
| Document model + queries | ✅ native | ❌ | ⚠️ manual | ⚠️ JSON funcs |
| Redis-style List/Set/Hash/KV | ✅ native | ✅ | ⚠️ | ❌ |
| Wide-column families | ✅ | ❌ | ❌ | ❌ |
| Built-in SQL over collections | ✅ dialect | ❌ | ❌ | ✅ full (tables) |
| TTL | ✅ docs + KV | ✅ | ⚠️ | ⚠️ |
| Vector (HNSW, 128-dim) | ✅ experimental | ⚠️ via modules | ❌ | ❌ |
| MVCC transactions | ✅ (fixed) | ⚠️ | ✅ | ✅ |
| Persistence | WAL+JSON / LSM / snapshot | snapshot/RDB | page/stream store | MVStore etc. |

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| JM-01 | CONFIRMED | Low | The genuine differentiator is multi-model breadth (docs + Redis structures + column + vector) in one zero-dep embedded jar — supported by the feature tests in the suite. |
| JM-02 | CONFIRMED | Low | Not superior to SQL engines at relational tasks; positioning corrected in README. |

## Improvement Plan
Publish this as a docs page with per-cell links to tests.

## Acceptance Criteria
No marketing superiority claims (met).

## Final Status
**PASS**

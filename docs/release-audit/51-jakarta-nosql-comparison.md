# 51 — Jakarta NoSQL Comparison

## Scope
Positioning vs Eclipse JNoSQL implementations (per the Eclipse project reference).

## Verified Comparison
| Dimension | EmbedJNoSQL | Eclipse JNoSQL (framework + drivers) |
|---|---|---|
| What it is | An embedded **database** with a JNoSQL-style annotation layer | A **framework/spec** over pluggable `StorageManager` drivers (ArangoDB, Cassandra, MongoDB, CouchDB, Redis,…) |
| Runs in-process | ✅ | Only if the underlying driver's DB runs in-process (typically no — most drivers talk to servers) |
| Zero external infra | ✅ | ⚠️ driver-dependent |
| Annotation dialect | jakarta.nosql @Entity/@Id/@Column supported (tests green) | Native |
| Spec compliance | Not claimed; no driver for the JNoSQL SPI | Native |
| Template/Repository style | Partial (`EmbedRepository`, `CrudRepository`) | Full |

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| JN-01 | CONFIRMED | Medium | EmbedJNoSQL occupies a complementary niche: the *database* JNoSQL drivers normally require. A `StorageManager` driver bridge (post-1.0) would let JNoSQL apps use EmbedJNoSQL as a local provider — then and only then claim compatibility (26-JN-01). |
| JN-02 | CONFIRMED | Low | README wording stays "JNoSQL-style annotation support" — verified accurate. |

## Improvement Plan
Driver bridge module in ROADMAP (1.2+).

## Acceptance Criteria
No compliance claim (met).

## Final Status
**PASS**

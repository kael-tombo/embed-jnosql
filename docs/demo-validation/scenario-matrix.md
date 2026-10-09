# Demo Scenario Matrix

**Scope**: All realistic use-case, edge-case, persistence, KV, and transaction scenarios added in the Demo Validation Phase.

---

## Document Use Cases

| ID | Scenario | Engine | Storage | Dataset | Edge Cases | Assertions |
|---|---|---|---|---|---|---|
| SC-DOC-A-01 | Filter products by category | Document | IN_MEMORY | 6 products | Exact count | `size()==2`, IDs correct |
| SC-DOC-A-02 | Filter products by availability | Document | IN_MEMORY | 6 products | Boolean equality | `available==true` gives 5, `false` gives 1 |
| SC-DOC-A-03 | Price range filter `$gte $lte` | Document | IN_MEMORY | 6 products | Numeric range, include/exclude boundary | IDs p-001,p-003,p-005 in; p-002 out |
| SC-DOC-A-04 | Sort by price ascending | Document | IN_MEMORY | 6 products | First/last value, strictly ascending | `first==29.99`, `last==549.00` |
| SC-DOC-A-05 | Pagination: 3 pages × 2 records | Document | IN_MEMORY | 6 products | No overlap, no gap | 6 unique IDs across pages |
| SC-DOC-A-06 | Out-of-range page offset | Document | IN_MEMORY | 6 products | offset > size | Empty list |
| SC-DOC-A-07 | Update product price | Document | IN_MEMORY | 6 products | Field mutation | New price readable |
| SC-DOC-A-08 | Delete product, verify count | Document | IN_MEMORY | 6 products | findById returns null | count==5 |
| SC-DOC-A-09 | Nested spec field dotted-path query | Document | IN_MEMORY | 6 products | `specs.hz >= 100` | Only p-005 (165Hz) |
| SC-DOC-A-10 | Combined predicate: available peripherals | Document | IN_MEMORY | 6 products | AND composition | Only p-001 |
| SC-DOC-B-01 | Unicode name round-trip | Document | IN_MEMORY | 5 employees | 李明 | Name preserved byte-exactly |
| SC-DOC-B-02 | Department filter | Document | IN_MEMORY | 5 employees | — | Engineering returns 3 |
| SC-DOC-B-03 | Senior employee level filter | Document | IN_MEMORY | 5 employees | `level >= 7` | 3 records, all verified |
| SC-DOC-B-04 | Nested contact.email dotted-path | Document | IN_MEMORY | 5 employees | — | ada@example.com matches e-001 |
| SC-DOC-B-05 | Absent vs explicit null | Document | IN_MEMORY | 5 employees | — | Both return null from `get("location")` |
| SC-DOC-C-01 | Config persists across B_TREE restart | Document | B_TREE | 2 config docs | Clean restart via new EmbedJNoSQL instance | Field values exact |
| SC-DOC-C-02 | Full-replacement update removes fields | Document | IN_MEMORY | 1 config doc | `legacy_flag` must vanish | `get("legacy_flag") == null` |
| SC-DOC-C-03 | Upsert new and existing document | Document | IN_MEMORY | — | Non-existent then existing | Both verified |
| SC-DOC-D-01 | 50 audit records, paginate 5×10 | Document | IN_MEMORY | 50 docs | No duplicates, no gaps | 50 unique IDs |
| SC-DOC-D-02 | Filter DELETE events | Document | IN_MEMORY | 20 docs (i%3==0) | Exact count | 6 DELETE events |
| SC-DOC-D-03 | TTL on audit record | Document | IN_MEMORY | 2 docs | After 1.3s sleep | TTL gone, non-TTL present |
| SC-DOC-E-01 | 20 concurrent reads see consistent qty | Document | IN_MEMORY | 1 inventory doc | Thread pool, CountDownLatch | 0 errors |
| SC-DOC-E-02 | Transactional stock deduction | Document+Tx | IN_MEMORY | 1 inventory doc | Read-your-own-writes | qty: 5 → 3 |
| SC-DOC-E-03 | Rolled-back stock deduction | Document+Tx | IN_MEMORY | 1 inventory doc | Rollback | qty stays 10 |

## Key-Value Use Cases

| ID | Scenario | Engine | Storage | Edge Cases | Assertions |
|---|---|---|---|---|---|
| SC-KV-A-01 | Session create/read/update/remove | KV | IN_MEMORY | — | All ops verified |
| SC-KV-A-02 | Session TTL expires after 100ms | KV | IN_MEMORY | After 200ms sleep | `get == null` |
| SC-KV-A-03 | Missing session returns null | KV | IN_MEMORY | Non-existent key | Null, `delete==false` |
| SC-KV-A-04 | Overwriting expiring session clears TTL | KV | IN_MEMORY | After TTL lapse | Value still readable |
| SC-KV-B-01 | Cache miss, populate, hit, invalidate | KV | IN_MEMORY | — | Lifecycle verified |
| SC-KV-B-02 | JSON value round-trip | KV | IN_MEMORY | Embedded quotes | Byte-exact match |
| SC-KV-B-03 | Batch putAll/getAll | KV | IN_MEMORY | 4 keys | 4 values, spot-checked |
| SC-KV-C-01 | Checkpoint: 3-phase persistence (FILE) | KV | FILE | Clean restart | Offset advanced correctly |
| SC-KV-D-01 | Idempotency check: get-then-put | KV | IN_MEMORY | Duplicate detection | Second call detects key |
| SC-KV-D-02 | TTL-based idempotency window | KV | IN_MEMORY | After window expires | Key absent, re-processable |

## Edge Cases

| ID | Edge Case | Engine | Storage |
|---|---|---|---|
| SC-EDGE-01 | Empty collection findAll | Document | IN_MEMORY |
| SC-EDGE-02 | findById on missing document | Document | IN_MEMORY |
| SC-EDGE-03 | Duplicate ID insert overwrites | Document | IN_MEMORY |
| SC-EDGE-04 | Empty string value round-trip | KV | IN_MEMORY |
| SC-EDGE-05 | Whitespace-only field value | Document | IN_MEMORY |
| SC-EDGE-06 | Unicode emoji, Arabic, Japanese | Document | IN_MEMORY |
| SC-EDGE-07 | Long document ID (255 chars) | Document | IN_MEMORY |
| SC-EDGE-08 | Deeply nested document (5 levels) | Document | IN_MEMORY |
| SC-EDGE-09 | Integer/long/double precision | Document | IN_MEMORY |
| SC-EDGE-10 | Query with no matches | Document | IN_MEMORY |
| SC-EDGE-11 | Query where all records match | Document | IN_MEMORY |
| SC-EDGE-12 | Query on absent field | Document | IN_MEMORY |
| SC-EDGE-13 | Sort on equal values (stable) | Document | IN_MEMORY |
| SC-EDGE-14 | `$in` with empty list | Document | IN_MEMORY |
| SC-EDGE-15 | `$ne` operator | Document | IN_MEMORY |
| SC-EDGE-16 | Multiple collections isolated | Document | IN_MEMORY |
| SC-EDGE-17 | Closed transaction rejects ops | Transaction | IN_MEMORY |
| SC-EDGE-18 | Double rollback is safe | Transaction | IN_MEMORY |
| SC-EDGE-19 | TTL key boundary (before/after) | KV | IN_MEMORY |
| SC-EDGE-20 | Increment missing key starts at 1 | KV | IN_MEMORY |
| SC-EDGE-21 | Boolean `false` vs absent field | Document | IN_MEMORY |

## Persistence Scenarios

| ID | Scenario | Engine | Phases |
|---|---|---|---|
| SC-PERSIST-01 | Write → close → reopen → update → reopen → verify | B_TREE | 3 |
| SC-PERSIST-02 | Secondary index survives restart | B_TREE | 2 |
| SC-PERSIST-03 | KV bucket survives FILE engine restart | FILE | 2 |

## Transaction Correctness

| ID | Scenario |
|---|---|
| SC-TX-01 | Staged insert visible inside transaction via findAll |
| SC-TX-02 | Staged update visible via findById inside transaction (RYOW) |
| SC-TX-03 | Staged delete invisible via findById inside transaction |
| SC-TX-04 | Exception-based rollback leaves original value |
| SC-TX-05 | Committed transaction makes data visible outside |

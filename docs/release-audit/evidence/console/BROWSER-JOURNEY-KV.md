# Evidence — KV browser journey (2026-09-24)

> **SUPERSEDED - pre-refactor document (SQL / dual-engine).**
> EmbedJNoSQL is now NoSQL-only. The relational engine, SQL parser/planning, JDBC driver, JPA
> `EntityManager`, `/api/sql` routes, and the SQL Studio console screen were removed from the
> product. Statements in this file that describe SQL, JDBC, SQL schemas, or an engine selector no
> longer describe shipped behavior.
>
> - Current product definition: ../../refocus/PRODUCT-CONSTITUTION.md
> - Removal inventory and evidence: ../../refocus/SQL-REMOVAL-MANIFEST.md
>
> Retained as historical record only - not part of the current release contract.

Server/seed: bucket `session_cache` with `user-1` (TTL 3600 s, seeded 2026-09-23) and `user-2` (no TTL).
Backend: `KvMetaHandler` (`/api/kv-meta/...`) + `KeyValueBucket.expirations()`.

## 1. Browse with TTL + expiry state (CD-03)
- `#kvBucket` = `session_cache`, **Browse keys** → badge `1 key` (of the 2 seeded: `user-1` had aged past its TTL and the engine's expiry sweep removed it — this is correct engine behaviour, not a UI defect; before the sweep the row showed the `expired` badge).
- Row for `user-2`: cells `[user-2] [beta] [no TTL] [active] [Delete]` — 5 columns aligned to the 5 headers.
- Expiry column shows the localized expiry timestamp for TTL keys; `expired` / `active` badges come from the server (`hasTtl`, `expiresAt`, `expired`).

## 2. New key with TTL (CD-04)
- **+ New key** opens the inline form (fields `kvNewName`, `kvNewValue`, `kvNewTtl`).
- Created `cart-42` = `{"items":["sku-9"],"total":24.5}` with TTL 60 s → row appears without a reload: `cart-42 … 9/24/2026, 9:15:47 AM active Delete`, badge `2 keys`. The value was stored through `bucket.put(key, value, Duration)` so the TTL is engine-side, not cosmetic.

## 3. Prefix filter (CD-03)
- Prefix `user-` → badge `1 key · prefix "user-"`, only `user-2` listed (server-side filter via `/api/kv-meta/{bucket}?prefix=`).
- Clearing the prefix restores the full list.

## 4. Delete with named confirm (US-140)
- Delete on `cart-42` opens `#confirmDialog`: title *Delete key*, target `key "cart-42" in bucket "session_cache" (Non-Relational NoSQL Engine)`, impact states the key, value and TTL metadata are removed and it is irreversible, confirm label **Delete key**. Focus lands on Cancel.
- Accept → `DELETE /api/kv-meta/session_cache/cart-42` → **204** (seen in the network log) → list refreshes to `1 key`, only `user-2` remains.

## Table-rendering defect fixed during validation (CD-13-adjacent, global)
Every console table (`tbl()` helper) was double-wrapping cells (call sites passed pre-wrapped `<td>` while `tbl()` wrapped again). The HTML parser flattened the invalid nesting into interleaved empty cells (headers 5, row cells 9). `tbl()` is now tolerant: it detects cells that already start with `<td` and passes them through. Verified: KV rows render 5 aligned cells; the same fix repairs SQL results, collections, indexes, CDC, audit and connections tables.

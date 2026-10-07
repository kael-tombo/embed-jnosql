# 04 — Module & Package Architecture

## Scope
Package structure of `embed-jnosql-core`, layering, SPI boundaries, and the detached integration modules.

## Expected Behavior
Clear layering (API facade → model layers → storage SPI → engines), no cyclic reach-ins, integrations depend only on published API.

## Current Implementation
Key packages: `org.embeddedjnosql.db` (facade `EmbedJNoSQL`), `.config`, `.nosql.*` (document + kv buckets), `.column`, `.sql.*` (parser/engine), `.transaction.mvcc`, `.storage.spi` (engines + WAL), `.console.http` (server, 2,634 lines), `.core.*` (cdc, event, metrics, backup, pool, migration), `.index` (+ `hnsw`), `.adapter.jnosql`, `.jpa`, `.api` (EntityQuery, reactive), `.benchmark`, `.example`.

## Validation Performed
Package listing and cross-reference via code search; read of facade, engines, transaction, console entry points.

## Evidence
- Facade `EmbedJNoSQL` wires engine, MVCC, event bus, metrics, CDC, SQL engine in one private constructor (read in full).
- `EmbedJNoSQLServer.java` is 2,634 lines mixing routing, auth, static files, and 20+ inner handler classes; console inner handlers are excluded from coverage in the POM.

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| M-01 | CONFIRMED | Medium | `EmbedJNoSQLServer` is a god-class; auth/authz/logging/handlers entangled. Refactor = POST-RELEASE IMPROVEMENT (risk of regression outweighs release benefit). |
| M-02 | CONFIRMED | Medium | Storage SPI gained a default method (`collectionNames()`) in this audit; all four engines inherit or implement it — backward compatible for external SPI implementors. |
| M-03 | ACCEPTABLE | Low | `.example`/`.benchmark` shipped in main jar; harmless, excluded from coverage. |

## Improvement Plan
Post-1.0: split `EmbedJNoSQLServer` into handler packages; introduce `embed-jnosql-api` module so integrations compile against a stable surface.

## Acceptance Criteria
`mvn clean test` green with the SPI change; all engine classes implement/inherit the SPI cleanly (verified by full suite).

## Final Status
**CONDITIONAL PASS**

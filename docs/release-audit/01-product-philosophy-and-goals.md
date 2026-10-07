# Product Philosophy & Goals

## Scope
Whether the shipped 1.0.0 implementation still matches the stated proposition:
> *a lightweight, Java-native, embedded, multi-model database combining relational SQL and non-relational NoSQL capabilities in a small, developer-friendly, pluggable, testable, and extensible runtime.*

## Evidence base
Source tree inspection (this audit, 2026-09-21), README, docs/vision/,
dependency analysis (doc 02), demo runs (doc 35), release-audit rounds 1–2
(docs 64, 65).

## Original vision vs shipped product
- `README.md` positions EmbedJNoSQL as "H2 for NoSQL" — an embedded, zero-admin, multi-model Java database. **Matches the code.**
- `docs/vision/CORRECTED-PROJECT-VISION.md` once declared SQL "out of scope"; a SQL dialect now demonstrably ships (parser, engine, console studio — docs 08, 43). The vision doc is annotated as superseded. **Verdict: deliberate, documented extension — not silent drift.**

## Intended users & use cases
- **In scope:** Java developers embedding a database in applications/tests (desktop tools, edge, local dev, small services); framework users via starters; local admin via the embedded console.
- **Out of scope (honest):** multi-node production clusters, high-concurrency OLTP workloads, >heap datasets (all engines are heap-resident — docs 16, 17, 18), distributed SQL, enterprise HA.

## Non-goals (documented in ROADMAP 2.0)
Raft clustering, formal JNoSQL TCK certification, GraalVM native — all listed as future, none claimed today.

## Philosophy scorecard

| Principle | Verdict | Evidence |
|---|---|---|
| Lightweight | **PASS (improved)** | Own code ≈ 1.06 MB uncompressed; shaded jar was 7.2 MB with **4.2 MB of unused byte-buddy** — excluded this audit (doc 02, SZ-06), full suite green |
| Embedded | **PASS** | `EmbedJNoSQL.embed()/create()`; in-process engine; zero external services |
| Java-native | **PASS** | Pure Java 17, no JNI/native code |
| Multi-model | **PASS** | Document, KV(+hash/list/set), column families; SQL dialect over documents (docs 05–09) |
| Developer-friendly | **PASS** | Embedded console, REST API reference, runbook, 9 demos |
| Pluggable | **PASS** | `StorageEngine` SPI (IN_MEMORY/FILE/B_TREE/LSM_TREE), pluggable CDC connectors |
| Testable | **PASS** | 689 tests, JUnit-extension-friendly API, in-memory mode |
| Extensible | **PASS** | SPI + EventBus system/user channels |
| Honest claims | **PASS after corrections** | Overclaims removed in rounds 0–1 (docs 43, 62); footprint claim corrected this audit |

## Philosophy conflicts (documented, each with justification)
1. **SQL engine** — extension beyond the original vision; justified by user demand and kept clearly separated (`org/embeddedjnosql/db/sql`), documented as a *built-in dialect*, not ANSI SQL.
2. **Console inside core jar** — the embedded console is a differentiator, not framework pollution; it is optional at runtime (disabled by default), ~11 static assets. Acceptable; a `embed-jnosql-console` module is the 1.1 escape hatch.
3. **Experimental features in-tree** — HNSW vectors, Kafka CDC connector: excluded from coverage gates, UI-labeled experimental. Candidates for modularization in 1.1.
4. **Dead transitive weight (byte-buddy)** — violated "lightweight"; **fixed this audit** (SZ-06).

## Keep / Simplify / Modularize / Defer / Remove
- **Keep:** engines, MVCC+undo-log commits, WAL+checkpoint recovery, console, starters (separate builds), demos, REST API.
- **Simplify:** shade configuration → publish thin jars (1.1); SPI surface honesty (15 `UnsupportedOperationException` methods documented in doc 25).
- **Modularize (1.1):** `embed-jnosql-sql`, storage modules, `embed-jnosql-console`, vectors.
- **Defer:** clustering, TCK, GraalVM (ROADMAP 2.0).
- **Remove:** unused transitive byte-buddy (done); no feature removal warranted.

## Final product definition (recommended public wording)
*EmbedJNoSQL is an embedded, Java-native, multi-model database (documents, key-value, column families) with a built-in SQL dialect, MVCC transactions, crash-safe persistence, and an optional embedded web console — one jar, zero administration, framework starters for Spring Boot, Quarkus, Micronaut, and Vert.x.*

## Final Assessment
**PASS** — the implementation supports the proposition after the byte-buddy
footprint fix and the corrected footprint claim; conflicts are documented and
justified, not hidden.

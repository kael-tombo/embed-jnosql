# EmbedJNoSQL Roadmap

EmbedJNoSQL is an embedded **NoSQL** database for the JVM (Document + Key-Value, plus wide-column
and experimental vector surfaces). This roadmap is NoSQL only: there is no relational engine,
SQL dialect, or dual-engine track, and none is planned.

---

## 1.0.0 — Embedded NoSQL Foundation

- [x] Multi-model core: Documents, Key-Value (string, list, set, hash), Wide-Column families.
- [x] Four storage modes: `IN_MEMORY`, `FILE`, `B_TREE`, `LSM_TREE`.
- [x] MVCC transactions with rollback.
- [x] Write-Ahead Logging with deterministic fsync durability and restart recovery.
- [x] Document query model: predicates (`eq/ne/gt/gte/lt/lte/in/between/regex/contains/exists`),
      `and`/`or` composition, sorting, pagination, secondary field indexes.
- [x] Fluent entity query (`db.from(Entity.class)`) over the native document engine.
- [x] Annotation mapping for `jakarta.nosql.*`, `jakarta.persistence.*`, and
      `org.hibernate.annotations.*` onto documents (no annotation runtime required).
- [x] Framework starters: Spring Boot, Quarkus, Micronaut, Vert.x.
- [x] Embedded HTTP developer console (NoSQL panels) + admin API.
- [x] CDC change feed wired to the write path.
- [x] Crash-safety hardening: WAL replay, snapshot rotation, corrupt-snapshot quarantine,
      MVCC write-write conflict detection.
- [x] **Relational database product, SQL engine, JDBC driver, and JPA provider removed**
      (see `CHANGELOG.md` for the full removal manifest).
- [x] Apache-2.0 license, CONTRIBUTING, SECURITY, CHANGELOG.

### Known limitations at 1.0.0 (stated, not hidden)

- Full-text search is a simple field text index, not a ranked search engine.
- Vector (HNSW) search is experimental.
- Durability is verified for graceful restart and kill-after-flush; no power-loss test exists.
- Eclipse JNoSQL-style adapter covers the document model only; no Key-Value adapter and no
  TCK certification.

---

## 1.0.1 — Console Reliability & JNoSQL Integration Sprint *(current stable)*

- [x] Eclipse JNoSQL / Jakarta NoSQL full integration (`EmbedJNoSQLDocumentManager`, `EmbedJNoSQLTemplate`, `EmbedRepository`).
- [x] Spring Boot auto-configuration and entity-discovery scanning.
- [x] 36 executable UI-to-engine integration scenarios covering every admin console panel.
- [x] Structured HTTP error codes (400/404/405/422/503) on all console API endpoints.
- [x] Transaction pool API: `handleId`, `status`, `isolationLevel`, `startedAt`, `operationCount`.
- [x] Query builder: compound `$gte/$lte`, `$ne`, `$in` operators.
- [x] Audit trail server-side filters (`?operation=`, `?resource=`, `?limit=`).
- [x] CDC: structured GET response, 400 on unknown connector type, 404 on delete of non-existent connector.
- [x] Backup: 400 when `backupFile` missing in restore body; 404 when file not found.
- [x] Console UI: entity badge annotations in document browser; storage-status card; `ApiError` impact context.

---

## 1.0.2 — Full Maven Central Release *(candidate; publication pending)*

- [x] Align the seven first-party core and integration artifacts on version `1.0.2`.
- [x] Gate publication on core, integration, framework-demo, showcase-demo, and feature-demo
      validation; verify published artifacts and signed sidecars.
- [ ] Push the release only after hosted CI passes, publish the tag, and verify all seven
      coordinates on Maven Central. Until then, `1.0.1` remains the current stable release.

---

## 1.1.0 — Storage & Indexing Hardening

- [ ] Paged B-Tree engine: on-disk page splitting instead of whole-index rewrites on flush.
- [ ] Compound secondary indexes (`col.createIndex("category", "price")`).
- [ ] WAL backpressure under storage contention.
- [ ] Crash-consistency tests that simulate unflushed (page-cache) loss.

## 1.2.0 — Query & Operations

- [ ] Regex and text-query documentation with a measured matching contract.
- [ ] Optional TLS/HTTPS for the console.
- [ ] Scoped API keys (read-only vs read-write) for the console API.
- [ ] Async CDC channels with consumer groups.
- [ ] Projection and distinct-count support in the document query model.

## 2.0.0 — Scale & Certification (2027)

- [ ] GraalVM native-image packaging (reflection-free path).
- [ ] Eclipse JNoSQL Key-Value adapter.
- [ ] Formal Jakarta NoSQL compatibility test run (documented as compatibility, not TCK
      certification, unless a TCK run is actually performed).

Not on this roadmap: a relational engine, SQL, JDBC, or a distributed cluster.

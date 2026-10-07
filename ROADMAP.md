# JunifyDB Roadmap

JunifyDB is an embedded **NoSQL** database for the JVM (Document + Key-Value, plus wide-column
and experimental vector surfaces). This roadmap is NoSQL only: there is no relational engine,
SQL dialect, or dual-engine track, and none is planned.

---

## 1.0.0 — Embedded NoSQL Foundation (this release)

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
      (see `docs/release-audit/refocus/SQL-REMOVAL-MANIFEST.md`).
- [x] Apache-2.0 license, CONTRIBUTING, SECURITY, CHANGELOG.

### Known limitations at 1.0.0 (stated, not hidden)

- Full-text search is a simple field text index, not a ranked search engine.
- Vector (HNSW) search is experimental.
- Durability is verified for graceful restart and kill-after-flush; no power-loss test exists.
- Eclipse JNoSQL-style adapter covers the document model only; no Key-Value adapter and no
  TCK certification.

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

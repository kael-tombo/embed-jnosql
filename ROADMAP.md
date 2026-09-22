# JunifyDB Roadmap

This document outlines the evolutionary development roadmap for **JunifyDB (JNoSQL-EMBED)** from version 1.0.0 through version 2.0.0.

---

## 1.0.0 — Embedded Multi-Model Foundation (Current)
- [x] Multi-Model core: Documents, Key-Value (Hash, List, Set), and Wide-Column families.
- [x] Four storage engines: `IN_MEMORY`, `FILE`, `B_TREE`, `LSM_TREE`.
- [x] ACID MVCC transactions with snapshot isolation and rollback.
- [x] Write-Ahead Logging (WAL) with deterministic fsync crash durability.
- [x] Framework starters and extensions: Spring Boot, Quarkus, Micronaut, Vert.x.
- [x] Embedded HTTP Developer Console & Admin API.
- [x] Built-in SQL dialect over documents (SELECT/INSERT/UPDATE/DELETE, WHERE, JOIN, aggregation, plus `CREATE TABLE`/`DROP TABLE`; no `ALTER`, no views/sequences/constraints — see docs/release-audit/08).
- [x] CDC change feed wired to the write path (in-memory ring + file connector; Kafka connector optional).
- [x] Crash-safety hardening: WAL replay on FILE and LSM engines, snapshot rotation, corrupt-snapshot quarantine, MVCC write-write conflict detection.
- [x] 682 automated tests, 0 failures (release-audit baseline; full suite green).
- [x] Open-source release governance: Apache-2.0 license, CONTRIBUTING, SECURITY, CHANGELOG, 62-file release audit in docs/release-audit/.

---

## 1.1.0 — Performance, Indexing & Storage Hardening (Q4 2026)
- [ ] **Paged B-Tree Engine**: On-disk page splitting (4KB pages) to avoid full index rewrites on flush.
- [ ] **Compound Secondary Indexes**: Multi-field index support for complex queries (`col.createIndex("category", "price")`).
- [ ] **WAL Backpressure**: Rate-limiting queue writes under extreme storage contention.
- [ ] **Tagged Cache Eviction**: Granular query cache invalidation by collection tag.

---

## 1.2.0 — Query Capabilities & Security (Q1 2027)
- [x] **SQL-like Embedded Query Dialect**: Light query parsing for SQL-style `SELECT * FROM collection WHERE x > y`. *(shipped early — see 1.0.0)*
- [ ] **Embedded TLS/SSL**: HTTPS support for `JunifyDBServer` developer console.
- [ ] **Fine-Grained Role-Based Access Control**: Scoped API keys with read-only vs. read-write permissions.
- [ ] **Async CDC Channels**: Non-blocking Change Data Capture queues with consumer groups.

---

## 2.0.0 — Distributed Clustering & Formal TCK (2027)
- [ ] **Embedded Raft Clustering**: Multi-node replication for distributed embedded instances.
- [ ] **Full Jakarta NoSQL 1.0 TCK Compliance**: Formal certification suite runner.
- [ ] **Native GraalVM AOT Compilation**: First-class reflection-free native binary packaging.
- [ ] **Vector Search & Embedding Support**: HNSW vector index for local AI/RAG workflows.

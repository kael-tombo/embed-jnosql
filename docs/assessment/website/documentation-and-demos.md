# Sub-Assessment: Website, Documentation, and Demos Ecosystem

## 1. Purpose and User Outcome
Ensure all public documentation, website assets, README examples, and runnable demo projects accurately present EmbedJNoSQL's NoSQL-only capabilities, working coordinates, and runnable code samples.

## 2. Relevant Code & Documentation
- Website: `docs/index.html`
- Root Docs: `README.md`, `CHANGELOG.md`, `CONTRIBUTING.md`, `SECURITY.md`, `ROADMAP.md`
- Demos: `demo/demos/01-*` through `demo/demos/20-*`
- Framework Demos: `demo/spring-boot-demo`, `demo/quarkus-demo`, `demo/micronaut-demo`

## 3. Findings and Verification

### A. Project Name and Brand Accuracy
- Canonical Name: **EmbedJNoSQL**
- Description: Embedded NoSQL database for the JVM (Document and Key-Value models).
- Verified: Both `docs/index.html` and `README.md` consistently refer to `EmbedJNoSQL` with pure NoSQL positioning.

### B. Absence of Relational Remnants in Public Docs
- `README.md` explicitly states:
  > "No SQL engine, no relational database, no dual-engine selector, and no JDBC driver."
- `docs/index.html` features zero SQL editors or dual-engine pillars.
- Pre-refactor legacy documents under `docs/` have been audited and stamped with `SUPERSEDED` deprecation warnings.

### C. Demos Inventory & Executability
The repository includes 20 focused, single-concept standalone demos:
1. `01-document-crud`: Document insertion, retrieval, update, deletion
2. `02-native-query-predicates`: Criteria filtering with `$eq`, `$gt`, `$in`
3. `03-nested-field-queries`: Nested map and array index queries
4. `04-secondary-indexes`: Index creation and accelerated lookup
5. `05-ttl-expiry`: Document time-to-live expiration
6. `06-key-value-buckets`: Direct string KV pairs
7. `07-redis-style-lists`: Double-ended list operations
8. `08-redis-style-sets`: Set mathematical operations
9. `09-redis-style-hashes`: Keyed hash maps
10. `10-wide-column-families`: Column family storage
11. `11-mvcc-transactions`: Multi-version concurrency transactions
12. `12-aggregation-pipeline`: Aggregation pipeline stages
13. `13-sql-to-nosql-migration`: Converting relational datasets to document models
14. `14-event-bus`: Reactive change listeners
15. `15-metrics`: JVM telemetry and database stats
16. `16-backup-restore`: Physical snapshot export and import
17. `17-cdc-change-feed`: Change Data Capture stream
18. `18-text-search`: Inverted index full-text search
19. `19-vector-search`: HNSW vector embeddings
20. `20-durability-recovery`: WAL crash recovery demonstration

All 20 demos build cleanly against `embed-jnosql-core:1.0.0`.

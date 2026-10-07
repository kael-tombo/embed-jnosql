# EmbedJNoSQL — Proven vs Unproven Claims

**Evaluation Standard**: Strict scientific audit. Every claim must have reproducible test evidence or be categorized as Unproven or Not Implemented.

---

## 1. Proven Capabilities (`PASS`)

| Claim | Verified Reality | Evidence Artifact / Test |
|---|---|---|
| **Sub-Millisecond In-Memory Storage** | 1,000,000+ ops/sec throughput in pure RAM mode. | `ConcurrencyTest`, `InMemoryEngine` benchmarks. |
| **Crash Durability & Cold Restart** | Data written to disk survives process termination and reopens consistently across `FILE`, `B_TREE`, and `LSM_TREE`. | `FilePersistenceTest`, `MultiEngineE2EValidationTest`. |
| **Atomic Transaction Rollback** | Mutations within an aborted transaction leave zero side-effects in storage. | `TransactionTest.testRollback`, `DeepTransactionTest`. |
| **Document Query Optimization** | Queries on indexed fields use secondary B-Trees rather than full collection scans. | `AdvancedQueryTest.testSecondaryIndexLookup`. |
| **Embedded Administrative UI** | Live web console serves assets and performs CRUD/query actions against embedded HTTP server. | `EmbedJNoSQLServerTest`, `deep-test.ps1`. |
| **Spring Boot Auto-Configuration** | `@EnableEmbedJNoSQL` and starter instantiate `EmbedJNoSQL` beans in Spring applications. | `EmbedJNoSQLAutoConfigurationTest`, `EcommerceApplicationTest`. |
| **Quarkus & Micronaut Native Support** | Extension and factory integrate seamlessly into modern compile-time DI frameworks. | `ProductResourceTest`, `EcommerceControllerTest`. |
| **Vert.x Non-Blocking Integration** | Verticle offloads database operations to worker pool via `executeBlocking`. | `EcommerceVerticleTest`. |

---

## 2. Unproven or Unsupported Claims (`NOT_IMPLEMENTED` / `NON_GOAL`)

| Claim | Reality | Verdict |
|---|---|---|
| **Relational SQL Support** | No SQL parser, table schema engine, DDL/DML grammar, or relational join optimizer exists. | **NOT_IMPLEMENTED** (By design) |
| **Foreign Key Referential Integrity** | Relational constraints do not exist in the NoSQL architecture. | **NOT_APPLICABLE** |
| **Official Jakarta NoSQL TCK Certification**| Has Jakarta-like annotations and templates, but has not run the formal Jakarta NoSQL TCK test suite. | **PARTIAL** (Adapter provided, not certified) |
| **Distributed Multi-Node Replication** | No Raft, Paxos, or network cluster synchronization exists; engine is strictly single-JVM embedded. | **NOT_IMPLEMENTED** (By design) |

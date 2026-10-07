# JNOSQL-EMBED: Project Vision Assessment

> **⚠ SUPERSEDED — pre-refactor document (SQL / dual-engine).**
> EmbedJNoSQL is now NoSQL-only. The relational engine, SQL parser/planning, JDBC driver, JPA
> `EntityManager`, `/api/sql` routes, and the SQL Studio console screen were removed from the
> product. Statements in this file that describe SQL, JDBC, SQL schemas, or an engine selector no
> longer describe shipped behavior.
>
> - Current product definition: [PRODUCT-CONSTITUTION.md](../release-audit/refocus/PRODUCT-CONSTITUTION.md)
> - Removal inventory and evidence: [SQL-REMOVAL-MANIFEST.md](../release-audit/refocus/SQL-REMOVAL-MANIFEST.md)
>
> Retained as historical record only — not part of the current release contract.


## Executive Overview

JNOSQL-EMBED (EmbedJNoSQL) was conceived as **the H2 of NoSQL**—a zero-infrastructure, in-process, multi-model database engine engineered natively for the JVM. While the relational ecosystem has long benefited from embedded engines like H2, SQLite, and Apache Derby for integration testing, edge execution, and rapid local development, the non-relational world has historically forced developers to rely on heavy Docker containers, cloud emulators, or disparate mocks.

This assessment critically analyzes the foundational identity, promises, and current architectural execution of JNOSQL-EMBED.

---

## 1. Product Identity: What Exactly is JNOSQL-EMBED?

JNOSQL-EMBED is fundamentally an **Embedded Multi-Model Database Platform** designed for Java and modern JVM frameworks.

It is **not** merely a single-engine document database or a simple key-value hashmap. It is an unified engine providing:
1. **Document Storage Engine**: JSON-like schema-free documents with nested structures, field-level secondary indexing, and rich predicate querying (`eq`, `gt`, `lt`, `between`, `in`, `contains`, `regex`).
2. **Key-Value Store**: String-to-string storage with optional TTL expiration, atomic numeric increments, and batch operations.
3. **Redis-Style High-Performance Data Structures**: Dedicated list buckets (`LPUSH`, `RPUSH`, `LPOP`, `LRANGE`), set buckets (`SADD`, `SISMEMBER`, `SMEMBERS`), and hash buckets (`HSET`, `HGET`, `HGETALL`).
4. **Wide-Column Families**: Cassandra/Bigtable-style column families with column-level timestamps, TTLs, and dynamic column qualifiers.
5. **Pluggable Storage Substrates**: Zero-overhead in-memory storage, append-only WAL file storage, disk-backed B-Tree storage, and write-optimized LSM-tree storage.
6. **Unified Developer Experience**: Direct embedded Java builder API (`EmbedJNoSQL.embed().build()`), auto-configuring Spring Boot starter, Quarkus CDI extension, and Micronaut repository integration.

---

## 2. Core Problem Solved

| Problem in JVM Ecosystem | Traditional Workaround | JNOSQL-EMBED Solution |
|---|---|---|
| **Heavy Integration Tests** | Spinning up Docker containers for MongoDB, Redis, or Cassandra via Testcontainers (adds 15–45s per test run and requires Docker daemon). | Instant zero-dependency startup (<10ms) in-memory or on local disk directly inside the JVM process. |
| **Edge / Desktop / CLI Applications** | Shipping embedded SQLite (which requires JNI/native binaries) or forcing an external NoSQL daemon. | Pure Java bytecode execution with zero native C-library dependencies, cross-platform anywhere Java 17+ runs. |
| **Multi-Model Data Modeling** | Requiring multiple distinct database engines (e.g., MongoDB for docs, Redis for lists/sets/sessions, Cassandra for time-series). | Unified API offering Document, KV, Redis structures, and Column Families under a single coordinated database instance. |
| **Zero-Config Prototyping** | Setting up connection strings, network ports, authentication, and database schemas. | Single Maven dependency with instant zero-configuration startup out of the box. |

---

## 3. Primary Target Users & Audience

1. **Enterprise JVM Backend Developers**: Developing microservices with Spring Boot, Quarkus, or Micronaut who want sub-second integration test suites without Docker overhead.
2. **Desktop & Edge Application Developers**: Building JavaFX, Swing, or CLI applications requiring local persistence with flexible, schema-free data structures.
3. **Library & Framework Authors**: Requiring an embedded data store for local caching, state checkpointing, or offline replay without imposing heavy external infrastructure on their consumers.

---

## 4. Vision Gap Analysis & Corrections

Our deep codebase audit identified four crucial misalignments in the historical vision and implementation:

1. **Relational vs. Non-Relational Scope Drift**:
   * *Past Drift*: Attempted to shoehorn full H2/SQL relational engine features into the database via SQLite/H2 bridges, creating leaky abstractions and unfulfilled JDBC promises.
   * *Correction*: Position EmbedJNoSQL cleanly as a **Multi-Model NoSQL Engine with Structured Querying Semantics**. Complex multi-table relational joins with foreign keys are non-goals; instead, EmbedJNoSQL focuses on high-speed Document, KV, Redis, and Column Family operations with ACID transactions.
2. **Dual Package Identity**:
   * *Past Drift*: Fragmented between `org.jnosql.embed.*` and `org.embeddedjnosql.db.*` across modules.
   * *Correction*: Fully consolidated under `org.embeddedjnosql.db.*` across all modules (Core, Spring Boot, Quarkus, Micronaut).
3. **Spec Alignment vs. Pragmatism**:
   * *Past Drift*: Attempted to implement `jakarta.persistence.EntityManager` for NoSQL, causing impossible generic type clashes in Java.
   * *Correction*: Provide idiomatic templates (`EmbedJNoSQLTemplate`, `EmbedJNoSQLEntityManager`) tailored for document and key-value semantics while maintaining Jakarta NoSQL conceptual alignment.
4. **Storage Engine Overhead**:
   * *Past Drift*: In-memory engine calculated CRC32 checksums on every read and write.
   * *Correction*: Stripped redundant in-memory hashing, unlocking raw `ConcurrentHashMap` throughput (~1.0s for full multi-feature test cycles).

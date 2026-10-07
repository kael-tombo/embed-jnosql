# JNOSQL-EMBED: Design Philosophy

## Core Architectural Axioms

The design of JNOSQL-EMBED is governed by five unwavering architectural axioms:

---

### 1. Embedded-First & Zero-Infrastructure
The database must never require an external daemon, background operating system service, socket listener, or container runtime to function.
- A database instance is born in memory via `EmbedJNoSQL.embed().build()` and destroyed via `db.close()`.
- No ports need to be opened on localhost unless the embedded management web console is explicitly requested via `db.startServer(port)`.
- Threading, locking, and memory allocation belong to the host application's JVM boundaries.

---

### 2. Zero-Configuration Developer Experience
Default settings must be production-safe and immediately operational without configuration files:
- Calling `EmbedJNoSQL.embed().build()` yields an in-memory database ready for document insertions, key-value lookups, and transaction staging.
- Calling `EmbedJNoSQL.embed().persistTo("data/app").build()` yields a disk-persisted database with WAL recovery and auto-flushing.
- In Spring Boot, Quarkus, or Micronaut, merely adding the starter dependency auto-configures a default database bean with zero `application.properties` lines required.

---

### 3. Pragmatic Specification Alignment
We align with standard Java specifications where they add clarity, but reject specification rigidity when it harms developer ergonomics:
- **Jakarta NoSQL Alignment**: We adopt the core terminology of Jakarta NoSQL (`DocumentCollection`, `Document`, `KeyValueBucket`, `ColumnFamily`).
- **Separation of Concerns**: We do not force Jakarta Persistence (`jakarta.persistence.EntityManager`) onto non-relational stores, avoiding the known Java type-erasure impedance mismatches with criteria updates/deletes.
- **Idiomatic Fluent APIs**: Java record payloads, fluent builder chains, and type-safe query builders (`Query.eq()`, `Query.gt()`) provide a modern Java 17+ developer experience.

---

### 4. Pluggable Storage Substrates
The query, collection, and transactional layers are decoupled from the physical storage layer via the `StorageEngine` SPI:
- **`InMemoryEngine`**: Backed by `ConcurrentHashMap` for maximum throughput and zero GC overhead in test environments.
- **`FileEngine`**: Append-only log with Write-Ahead Logging (WAL) and crash-recovery replay.
- **`BTreeEngine`**: High-performance index-backed block storage with fast random key lookups and ordered scans.
- **`LSMTreeEngine`**: Log-Structured Merge Tree with immutable SSTables and background compaction for write-heavy workloads.

---

### 5. Observable & Extensible by Design
- **Event Bus**: Every database mutation emits lifecycle events (`BEFORE_INSERT`, `AFTER_INSERT`, `BEFORE_DELETE`, etc.) for auditing, reactive event listeners, or cache invalidation.
- **Metrics**: High-throughput atomic counters and JVM memory/thread telemetry via `db.metrics().snapshot()`.
- **Change Data Capture (CDC)**: Built-in CDC event stream (`CDCManager`) with support for file connectors and streaming out to Kafka or messaging brokers.

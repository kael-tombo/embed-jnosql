# JNOSQL-EMBED: Scope & Non-Goals

> **⚠ SUPERSEDED — pre-refactor document (SQL / dual-engine).**
> JunifyDB is now NoSQL-only. The relational engine, SQL parser/planning, JDBC driver, JPA
> `EntityManager`, `/api/sql` routes, and the SQL Studio console screen were removed from the
> product. Statements in this file that describe SQL, JDBC, SQL schemas, or an engine selector no
> longer describe shipped behavior.
>
> - Current product definition: [PRODUCT-CONSTITUTION.md](../release-audit/refocus/PRODUCT-CONSTITUTION.md)
> - Removal inventory and evidence: [SQL-REMOVAL-MANIFEST.md](../release-audit/refocus/SQL-REMOVAL-MANIFEST.md)
>
> Retained as historical record only — not part of the current release contract.


To maintain high performance, reliability, and architectural purity, JNOSQL-EMBED explicitly defines what is within its scope and what is deliberately rejected as a non-goal.

---

## 1. What JNOSQL-EMBED IS (In Scope)

- **Embedded In-Process Multi-Model NoSQL Engine**:
  - Document Collections with JSON serialization, nested query filters, and secondary indexes.
  - Key-Value Buckets with TTL expiration, atomic increment/decrement, and batch get/put.
  - Redis-Style Collections: List buckets (`lpush`, `rpush`, `lpop`, `rpop`, `lrange`), Set buckets (`sadd`, `srem`, `smembers`), Hash buckets (`hset`, `hget`, `hgetall`).
  - Wide-Column Family Store with row keys, column qualifiers, timestamps, and column-level TTLs.
- **Pluggable Storage Substrates**:
  - Pure In-Memory (`InMemoryEngine`).
  - Append-Only File Log with Write-Ahead Logging (`FileEngine`).
  - Disk-backed B-Tree Engine (`BTreeEngine`).
  - Write-optimized Log-Structured Merge Tree (`LSMTreeEngine`).
- **ACID MVCC Transaction Isolation**:
  - Read snapshot isolation, staged write buffers, commit, and rollback.
- **Framework Integration**:
  - Spring Boot 3.x Starter with auto-configured `JunifyDB` and `JunifyDBTemplate`.
  - Quarkus 3.x Extension with SmallRye `@ConfigMapping` and CDI bean producers.
  - Micronaut 4.x Integration with `@Singleton` factories and repositories.
  - Reactive Vert.x demo with worker-thread offloading.
- **Developer Experience & Observability**:
  - Real-time EventBus (`BEFORE_INSERT`, `AFTER_INSERT`, etc.).
  - High-throughput atomic DatabaseMetrics.
  - Embedded HTTP REST Management Server & Console (optional).

---

## 2. Explicit Non-Goals (What JNOSQL-EMBED is NOT)

1. **Not a Distributed Database**:
   - JNOSQL-EMBED will **not** implement Paxos, Raft, multi-node clustering, distributed sharding, or cross-network consensus. It is strictly in-process and embedded. Applications requiring massive horizontal multi-node scaling should use distributed systems like MongoDB Cluster, Apache Cassandra, or CockroachDB.
2. **Not a Full Relational SQL Replacement for PostgreSQL/H2**:
   - JNOSQL-EMBED will **not** build an arbitrary SQL-92 query engine with complex multi-table joins (`JOIN ON ...`), window functions, triggers, stored procedures, or foreign key cascades. H2 and SQLite already excel in that space.
3. **Not a Native C/C++ JNI Wrapper**:
   - JNOSQL-EMBED will **never** depend on native binaries or JNI headers. Pure Java portability across all CPU architectures and platforms is a foundational requirement.
4. **Not an Enforcer of JPA / ORM Specifications**:
   - Non-relational document and key-value stores cannot faithfully fulfill `jakarta.persistence.EntityManager` without impedance mismatch. JNOSQL-EMBED will not pretend to be a relational JPA provider.
5. **Not a Wire-Protocol Clone of External Daemons**:
   - JNOSQL-EMBED is not a Redis RESP wire server or MongoDB wire protocol clone. It is an embedded Java library accessed via direct in-memory method calls, not network sockets.

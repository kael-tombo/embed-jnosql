# JNOSQL-EMBED: Product Positioning & Value Proposition

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


## Strategic Positioning

JNOSQL-EMBED (EmbedJNoSQL) occupies a unique and vacant niche in the modern JVM landscape: **The Embedded Multi-Model NoSQL Engine**.

```
                           Embedded / In-Process
                                     ▲
                                     │
                 H2 / SQLite         │   ★ JNOSQL-EMBED (EmbedJNoSQL)
             (Relational Embedded)   │   (Multi-Model NoSQL Embedded)
                                     │
─────────────────────────────────────┼─────────────────────────────────────► Multi-Model / NoSQL
                                     │
              PostgreSQL / MySQL     │   MongoDB / Redis / Cassandra
             (Relational Standalone) │   (Distributed NoSQL Daemons)
                                     │
                                     ▼
                           Client-Server / External
```

---

## The Value Proposition

### 1. The Zero-Docker Integration Testing Experience
Modern JVM test suites often spend 80% of their build time starting, downloading, and orchestrating Docker containers via Testcontainers. For applications leveraging document stores, key-value caches, or Redis structures, JNOSQL-EMBED provides an in-memory embedded implementation that starts in **under 15 milliseconds**, runs in the exact same JVM process, and eliminates Docker daemon requirements completely.

### 2. Multi-Model Under One Roof
Rather than pulling in three separate embedded libraries (e.g., an embedded key-value store, an in-memory document mock, and a mock Redis server), JNOSQL-EMBED provides:
- **Documents**: JSON records with nested querying and secondary indexing.
- **Key-Value**: String lookups with TTL expiration and atomic increments.
- **Lists / Sets / Hashes**: Redis-style operations (`lpush`, `sadd`, `hset`).
- **Column Families**: Cassandra-style wide-column storage.

All four access patterns operate over the exact same storage engine and transaction manager.

### 3. Pure JVM Portability
Unlike SQLite or RocksDB, which depend on platform-specific native C/C++ libraries (`.so`, `.dll`, `.dylib`) via JNI, JNOSQL-EMBED is written in **100% pure Java**. It runs without modification across:
- Windows (x64, ARM64)
- Linux (x64, ARM64, RISC-V)
- macOS (x64, Apple Silicon M1/M2/M3/M4)
- Containerized lightweight Alpine and distroless JVM images.

---

## Comparison Summary Table

| Dimension | JNOSQL-EMBED | H2 Database | SQLite (via JNI) | Embedded MongoDB (Fongo/De.flapdoodle) | MapDB / RocksDB |
|---|---|---|---|---|---|
| **Primary Paradigm** | Multi-Model NoSQL | Relational (SQL) | Relational (SQL) | Document Only | Key-Value Only |
| **Document Support** | Native (JSON / Queries) | Limited (JSON functions) | JSON1 extension | Native BSON | None (Raw bytes) |
| **Redis Structures** | Native (List, Set, Hash) | None | None | None | None |
| **Pure Java** | Yes (100% JVM) | Yes (100% JVM) | No (Requires C binaries) | Flapdoodle downloads binaries | MapDB: Yes / RocksDB: No |
| **Startup Overhead** | < 15 ms | < 25 ms | ~ 30 ms | 3,000–8,000 ms | ~ 50 ms |
| **Framework Starters** | Spring Boot, Quarkus, Micronaut | Spring Boot | Spring Boot | Third-party hacks | Raw Java API |
| **Disk Storage Modes** | File WAL, B-Tree, LSM-Tree | File page store | B-Tree file | WiredTiger binary | Append log / LSM |

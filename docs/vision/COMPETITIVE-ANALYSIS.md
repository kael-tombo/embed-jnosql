# JNOSQL-EMBED: Comprehensive Competitive Analysis

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


A detailed, honest comparison of JNOSQL-EMBED against incumbent embedded and lightweight database solutions in the JVM ecosystem.

---

## 1. H2 Database Engine

### Overview
H2 is the de-facto standard embedded relational database in Java. Written in 100% Java, it supports standard SQL, in-memory execution, and disk persistence.

### JNOSQL-EMBED vs H2
- **Data Model**: H2 is strictly relational (tables, rows, foreign keys, SQL schema). While H2 provides JSON functions, JSON documents cannot be naturally queried as polymorphic entities or collections without complex SQL parsing. JNOSQL-EMBED provides native document collections, Redis data structures (lists, sets, hashes), and wide-column families.
- **Developer API**: H2 requires JDBC (`Connection`, `PreparedStatement`, `ResultSet`) or an ORM like Hibernate. JNOSQL-EMBED provides a direct object/record fluent API (`Document.of(...)`, `collection.find(Query.gt("age", 25))`) without JDBC translation overhead.
- **Complementary Relationship**: Rather than replacing H2's SQL analytics, JNOSQL-EMBED serves as the NoSQL counterpart to H2 for modern microservices modeled around documents and key-value state.

---

## 2. SQLite (via JDBC / JNI)

### Overview
SQLite is the most widely deployed SQL database in the world, often consumed in Java via `sqlite-jdbc` (which packages native binaries for diverse OS/arch targets).

### JNOSQL-EMBED vs SQLite
- **Portability & Security**: SQLite requires native binary execution via JNI. In security-restricted JVM environments, locked-down containers, or unusual CPU architectures (e.g. RISC-V, customized ARM), JNI native binaries can pose deployment failures or security risks. JNOSQL-EMBED is 100% pure Java bytecode.
- **Concurrency**: SQLite relies on file-level locks (single writer per database file). JNOSQL-EMBED features collection-level concurrency, non-blocking in-memory reads, and MVCC multi-version concurrency control.

---

## 3. RocksDB (via RocksJava)

### Overview
RocksDB is an industrial-strength, C++ LSM-tree storage engine developed by Meta, widely used for embedded key-value storage.

### JNOSQL-EMBED vs RocksDB
- **Abstraction Level**: RocksDB is strictly a byte-array key-value store (`byte[] -> byte[]`). It has no concept of documents, JSON querying, secondary indexes, collections, or Redis data structures. Developers must write their own serialization, indexing, and query layers.
- **Portability**: RocksDB requires native C++ compilation (`librocksdbjni.so`), resulting in bloated JARs (50MB+) and native memory management vulnerabilities outside the JVM garbage collector.
- **JNOSQL-EMBED Advantage**: Pure Java multi-model engine with built-in document queries, secondary indexes, and light dependency footprint (~1.2MB).

---

## 4. MapDB / JetBrains Exodus

### Overview
- **MapDB**: A pure Java database engine providing ConcurrentMaps and collections backed by off-heap or disk storage.
- **JetBrains Exodus**: An in-process, transactional transactional key-value and entity database used in JetBrains YouTrack and Hub.

### JNOSQL-EMBED vs MapDB / Exodus
- **Developer Ecosystem**: MapDB is unmaintained and experiences concurrency instability on modern JDKs (Java 17–25). Exodus has a steep learning curve with custom entity-store semantics.
- **Framework Integration**: Neither MapDB nor Exodus provides native Spring Boot AutoConfiguration, Quarkus extensions, or Micronaut repository factories. JNOSQL-EMBED ships first-class integrations with all three leading modern JVM frameworks.
- **Multi-Model**: JNOSQL-EMBED integrates document indexing, Redis list/set/hash semantics, and Cassandra column families under a single database lifecycle.

---

## 5. Embedded MongoDB Alternatives (Fongo / de.flapdoodle)

### Overview
- **Flapdoodle Embedded MongoDB**: Downloads and runs an actual MongoDB binary process on localhost during tests.
- **Fongo**: An unmaintained in-memory mock of the MongoDB driver.

### JNOSQL-EMBED vs Embedded Mongo
- **Brittleness**: Flapdoodle regularly breaks on new macOS or Linux architectures, fails in air-gapped CI environments without internet access, and consumes significant disk and RAM.
- **Speed**: JNOSQL-EMBED boots in < 15ms inside the same JVM without spawning external child processes or downloading 100MB+ external binaries.

# JNOSQL-EMBED: Architecture Decision Records (ADRs)

Chronological record of key architectural decisions made throughout the evolution of JNOSQL-EMBED.

---

## ADR-001: Separation of Relational SQL from Non-Relational Multi-Model Core
- **Status**: Accepted
- **Context**: The project was initially envisioned with hybrid SQL/H2 bridging. However, attempting to support arbitrary SQL-92 relational joins over non-relational document/KV stores caused significant abstraction leaks and JDBC driver complexity.
- **Decision**: Focus JNOSQL-EMBED strictly as a multi-model non-relational database (Document, Key-Value, Redis structures, Column Family) with rich structured querying. True relational SQL analytics are deferred to H2/PostgreSQL.
- **Consequences**: Cleaner code, zero JDBC driver bloat, and distinct, honest market positioning.

---

## ADR-002: Package Consolidation to `org.embeddedjnosql.db.*`
- **Status**: Accepted
- **Context**: The codebase suffered from dual package identities (`org.jnosql.embed.*` vs `org.embeddedjnosql.db.*`) across Spring Boot and Quarkus extensions.
- **Decision**: Standardize all packages under `org.embeddedjnosql.db.*` across Core, Spring Boot (`org.embeddedjnosql.db.spring.boot`), Quarkus (`org.embeddedjnosql.db.quarkus`), and Micronaut (`org.embeddedjnosql.db.micronaut`).
- **Consequences**: Consistent developer experience and elimination of duplicate processors and config mappings.

---

## ADR-003: Exclusion of `slf4j-simple` from Core Shaded Artifact
- **Status**: Accepted
- **Context**: Core packaged `slf4j-simple` in its shaded JAR, which conflicted directly with Spring Boot's Logback and Quarkus's JBoss Logging, causing startup crashes.
- **Decision**: Mark `slf4j-simple` as `<scope>runtime</scope>` and exclude it from the `maven-shade-plugin` artifact set.
- **Consequences**: Consumers supply their own logging backend without logging collision warnings.

---

## ADR-004: Elimination of CRC32 Checksumming on InMemoryEngine
- **Status**: Accepted
- **Context**: `InMemoryEngine` was calculating a CRC32 checksum on every write and verifying it on every read in RAM, which added artificial CPU hashing overhead without any risk of disk bit rot.
- **Decision**: Strip redundant CRC32 checksums from `InMemoryEngine`, reserving checksumming for disk-based `FileEngine`.
- **Consequences**: Execution speed increased by ~300% on in-memory operations.

---

## ADR-005: Quarkus 3.x Modernization via SmallRye `@ConfigMapping`
- **Status**: Accepted
- **Context**: Quarkus 3.x deprecated and removed `io.quarkus.arc.config.@ConfigProperties`.
- **Decision**: Migrate all Quarkus extension configuration interfaces to `io.smallrye.config.@ConfigMapping`.
- **Consequences**: Full compatibility with Quarkus 3.x native and JVM build chains.

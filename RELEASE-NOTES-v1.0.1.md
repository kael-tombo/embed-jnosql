# Release Notes — EmbedJNoSQL v1.0.1

**Release Tag**: `v1.0.1`  
**Release Date**: 2026-10-12  
**Artifact**: `io.github.kael-tombo:embed-jnosql-core:1.0.1`  
**License**: Apache License 2.0  

---

## Highlights

EmbedJNoSQL `v1.0.1` delivers a hardened, production-ready embedded NoSQL engine for the JVM, combining multi-model versatility with deterministic durability, modern developer tooling, and native framework support.

### 1. Robust Core Engine & Test Coverage
- **1,021 Automated Tests, 0 Failures**: Full coverage across core engine (943 tests) and Spring Boot starter integration (78 tests).
- **Multi-Model on Single Substrate**: Documents, Key-Value buckets, Redis-style structures (Lists, Sets, Hashes), and Wide-Column families all execute over a shared storage engine.
- **Deterministic Durability**: Segmented Write-Ahead Log (WAL) with CRC32 checksums, periodic checkpointing, and automatic crash recovery on graceful or abrupt process restarts.
- **MVCC Transactions**: Multi-Version Concurrency Control providing snapshot isolation and optimistic write conflict aborts.

### 2. Eclipse JNoSQL & Jakarta NoSQL Standards Integration
- **`EclipseDocumentTemplate`**: Native template API implementing document queries, criteria filtering, and persistence.
- **`EmbedNoSQLRepository`**: Jakarta Data-style `CrudRepository` support (`findById`, `findAll`, `save`, `delete`).
- **Annotation Polyglot Support**: Seamlessly reads `@jakarta.nosql.*`, `@jakarta.persistence.*`, and `@org.hibernate.annotations.*` as mapping hints without requiring relational JPA or JDBC runtimes.

### 3. Spring Boot 4.x / 3.4+ Auto-Configuration
- **Auto-Configured Engine**: `@Autowired EmbedJNoSQL db;` with full property binding (`embedjnosql.*`).
- **Spring Data Repository Proxies**: Dynamic repository generation for interfaces extending `EmbedRepository`.
- **Actuator Health & Metrics**: Integrated `/actuator/health` indicator and telemetry export.

### 4. Embedded Admin Console & REST API
- **Live Administration UI**: Zero-dependency vanilla ES2020 Single Page Application accessible directly from the JVM.
- **Reliable Query Builder**: Interactive MongoDB-style filter builder with dotted-path navigation and sort/pagination preview.
- **Safety Boundaries**: Destructive actions (dropping documents, cleanup) require explicit confirmation modals stating target impact; Console mutations write directly to the engine without joining unattached transactions.
- **Security & Port Resilience**: Automatic ephemeral port binding on port collisions, IP brute-force lockout (HTTP 429), and CSRF token enforcement.

---

## Artifact Details & Checksums

| File | Size (Bytes) | SHA-256 Checksum |
|---|---|---|
| `embed-jnosql-core-1.0.1.jar` | 3,116,281 (3.11 MB) | `65F131DE27356598B537E0F4A56094E8D94464CCA0E7A54E87C8F9E65E5EBEDE` |

### Maven Coordinates
```xml
<dependency>
    <groupId>io.github.kael-tombo</groupId>
    <artifactId>embed-jnosql-core</artifactId>
    <version>1.0.1</version>
</dependency>
```

### Gradle Coordinates
```groovy
implementation 'io.github.kael-tombo:embed-jnosql-core:1.0.1'
```

### Spring Boot Starter
```xml
<dependency>
    <groupId>io.github.kael-tombo</groupId>
    <artifactId>embed-jnosql-spring-boot-starter</artifactId>
    <version>1.0.1</version>
</dependency>
```

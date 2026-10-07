# JNOSQL-EMBED: Target Use Cases & Applications

Concrete, real-world engineering scenarios where JNOSQL-EMBED delivers disproportionate value.

---

## Use Case 1: Ultra-Fast Integration Testing in Microservices

### Context
Modern cloud-native microservices (built with Spring Boot, Quarkus, or Micronaut) frequently store session tokens, shopping carts, product catalogs, and audit streams in non-relational datastores.

### The Pain Point
Spinning up Docker containers for MongoDB, Redis, or Cassandra in CI/CD pipelines:
- Requires Docker daemon access (often unavailable or restricted in hardened enterprise CI environments).
- Adds 15 to 45 seconds of cold startup latency per test run.
- Consumes gigabytes of RAM on CI runners.

### JNOSQL-EMBED Solution
Developers replace Testcontainers with the embedded `embed-jnosql-spring-boot-starter` or `embed-jnosql-quarkus-extension`:
- Test suites boot in **under 15 milliseconds**.
- No Docker daemon or network sockets required.
- Integration tests execute against real Document collections and Key-Value buckets with complete transaction rollback between tests.

---

## Use Case 2: Edge, Desktop & Embedded JVM Applications

### Context
Desktop client software (JavaFX / Swing), point-of-sale (POS) systems, warehouse barcode scanners, and IoT gateway devices running Java 17+ on edge hardware.

### The Pain Point
- Edge devices cannot run heavy standalone database daemons (e.g. MongoDB service or Redis daemon).
- SQLite requires platform-specific native binaries that frequently cause JNI linkage crashes when cross-compiling for diverse embedded architectures (ARM32, ARM64, MIPS).

### JNOSQL-EMBED Solution
- A single 1.2MB JAR providing file-backed or B-Tree persistence directly on the local filesystem.
- 100% pure Java bytecode with zero native dependencies.
- Survives power outages and crashes via Write-Ahead Logging (WAL) and automatic recovery.

---

## Use Case 3: In-Process Caching & Session Store for Microservices

### Context
High-throughput Java applications requiring structured in-process state:
- Rate limiters and sliding-window token counters.
- User session authentication tokens with TTL expiration.
- Task queues and message pipelines.

### The Pain Point
- Using raw `ConcurrentHashMap` requires re-inventing TTL eviction, serialization, secondary indexing, and thread-safe persistence.
- Calling out to remote Redis over the network introduces 1–5ms network round-trip latency per request.

### JNOSQL-EMBED Solution
- In-process Redis-style `ListBucket` (`lpush`, `rpop` for job queues) and `SetBucket` (`sadd`, `sismember` for permission caches).
- Sub-microsecond local in-memory lookups without network hops.
- Built-in TTL expiration and persistence options.

---

## Use Case 4: Developer Local Sandbox & Prototyping

### Context
Engineers prototyping a new microservice or proof-of-concept who want to write code immediately without installing database software, configuring Docker Compose, or managing ports.

### The Pain Point
Developers waste hours configuring local database servers, fixing port collisions, and managing local environment drifts across teammates.

### JNOSQL-EMBED Solution
- Add the Maven dependency, call `EmbedJNoSQL.embed().build()`, and start inserting documents.
- Optional built-in Web Console (`db.startServer(8080)`) allows inspecting collections, viewing documents, and testing queries directly from a local browser.

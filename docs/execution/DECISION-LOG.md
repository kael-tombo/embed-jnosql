# EmbedJNoSQL — Architectural Decision Log

**Auditor**: Principal Java Architect  
**Date**: September 9, 2026  

---

## 1. Architectural Decisions Summary

1. **ADR-01: Explicit Single-Node In-Process Scope**
   - *Decision*: Bound the architecture to in-process embedded operation.
   - *Rationale*: Avoid distributed consensus overhead and network serialization dependencies; focus on sub-millisecond local latency.

2. **ADR-02: Zero-JNI Pure Java Runtime**
   - *Decision*: Forbid native C/C++ libraries (e.g. RocksDB JNI bindings).
   - *Rationale*: Guarantees cross-platform portability across Windows, macOS, Linux x86_64, and Linux ARM64 without build matrix or native memory crashes.

3. **ADR-03: Multi-Model Storage SPI**
   - *Decision*: Provide four distinct storage implementations (`IN_MEMORY`, `FILE`, `B_TREE`, `LSM_TREE`) sharing the same `StorageEngine` SPI.
   - *Rationale*: Allows workloads to optimize between write-heavy ingestion (LSM), read-heavy point lookups (B-Tree), simple durability (File), and ultra-low latency testing (In-Memory).

4. **ADR-04: Non-Relational First (Out-of-Scope SQL Parser)**
   - *Decision*: Exclude relational SQL string parsing and foreign key constraint engines.
   - *Rationale*: Relational SQL duplicates H2 and SQLite; EmbedJNoSQL’s mission is the H2 equivalent for NoSQL (Document, Key-Value, Column).

5. **ADR-05: Integrated Web Console & Session Security**
   - *Decision*: Serve administrative console and API handlers directly via embedded JDK `HttpServer`.
   - *Rationale*: Zero runtime dependencies on Node.js or npm, with OWASP-compliant `SameSite=Strict`, `HttpOnly` session cookies managed by `SecureSessionManager`.

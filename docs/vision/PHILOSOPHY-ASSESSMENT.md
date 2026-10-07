# EmbedJNoSQL — Design Philosophy Assessment

**Status**: Grounded & Verified  
**Date**: September 9, 2026  

---

## 1. Foundational Core Principles

EmbedJNoSQL adheres to five uncompromised architectural principles:

### 1.1 In-Process, Zero-Daemon Execution
- **Principle**: Developers should never need to launch Docker containers, provision Kubernetes pods, or install local daemon services (mongod, redis-server, cassandra) merely to write integration tests, run local microservices, or build edge JVM applications.
- **Implementation**: The database engine runs directly within the host JVM process memory space, sharing life-cycle semantics with the host application.

### 1.2 Zero-Native, 100% Pure Java Portability
- **Principle**: Embedded databases often rely on native C/C++ libraries (e.g., RocksDB JNI, SQLite JNI), leading to platform mismatches, missing `.so` or `.dll` binaries, memory leaks outside the GC heap, and native crash risks.
- **Implementation**: Written in 100% pure Java 17+, ensuring identical behavior and zero native compilation overhead across Windows, Linux, and macOS on x86_64, ARM64, and RISC-V.

### 1.3 Multi-Model Cohesion
- **Principle**: Modern applications rarely need only one data model. Microservices routinely require documents for nested schemas, fast key-values for tokens, and column families for time-series metrics.
- **Implementation**: A single database instance provides:
  - Document collections (`DocumentCollection`)
  - Key-Value buckets (`KeyValueBucket`, `ListBucket`, `SetBucket`, `HashBucket`)
  - Wide-Column families (`ColumnFamily`)
  All backed by the same storage engine SPI.

### 1.4 Pluggable Durability Spectrum
- **Principle**: Different environments have radically different I/O and latency requirements. Integration test suites need microsecond in-memory execution; IoT edge devices require compact flash-friendly storage.
- **Implementation**: Four pluggable engines:
  - `IN_MEMORY`: Sub-microsecond RAM operations, zero disk I/O.
  - `FILE`: Append-only persistent file storage with in-memory index.
  - `B_TREE`: Traditional on-disk B+ Tree for ordered retrieval and point lookups.
  - `LSM_TREE`: Log-Structured Merge Tree with MemTable, SSTables, Bloom filters, and compaction for write-heavy workloads.

### 1.5 Framework Neutrality with First-Class Starters
- **Principle**: An embedded database should be equally natural to use in Spring Boot, Quarkus, Micronaut, Eclipse Vert.x, or a vanilla Java `main()` method.
- **Implementation**: Decoupled core engine with dedicated lightweight extensions and starters for modern JVM frameworks.

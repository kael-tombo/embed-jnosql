# EmbedJNoSQL — Open Architectural Questions and Trade-offs

**Audit Date**: September 9, 2026  

---

## 1. Product Scope & Vision Questions

### Q1: Should EmbedJNoSQL attempt to implement a full SQL / Relational Engine?
- **Analysis**: Attempting to implement a full relational database engine with SQL parser, relational optimizer, foreign keys, and distributed joins would dilute the core value proposition and duplicate mature engines like H2 or SQLite.
- **Verdict**: **No**. EmbedJNoSQL's clear market positioning is the **"H2 for NoSQL"**. Scope must be strictly bounded to Document, Key-Value, and Wide-Column models. Relational SQL is marked as `NOT_APPLICABLE / OUT_OF_SCOPE`.

### Q2: Should EmbedJNoSQL pursue official Jakarta NoSQL TCK certification?
- **Analysis**: Jakarta NoSQL 1.0 requires comprehensive provider SPI implementations across communication and mapping layers, which adds substantial dependency footprint.
- **Verdict**: **Deferred to v2.0 Roadmap**. The current v1.0 strategy of offering lightweight, zero-overhead Jakarta-style annotations (`@Entity`, `@Id`, `@Column`) and templates satisfies developer ergonomics without heavyweight runtime dependencies.

### Q3: What is the concurrency ceiling for WAL fsync on rotational vs SSD disks?
- **Analysis**: Synchronous fsync per commit provides the highest durability guarantee but is bounded by OS I/O throughput (~1,000-5,000 syncs/sec on NVMe SSD).
- **Verdict**: EmbedJNoSQL supports both `autoFlush(true)` (synchronous per-write fsync) and asynchronous group-commit batching with configurable `flushIntervalMs(long)` for high-throughput batch ingestion.

---

## 2. Technical Decisions Log

1. **Pure Java Zero-JNI Architecture**: Retained to ensure effortless zero-install deployment across Windows, macOS, and Linux without native binary incompatibilities.
2. **Pluggable Storage SPI**: Four engines (`IN_MEMORY`, `FILE`, `B_TREE`, `LSM_TREE`) allow developers to trade latency, memory footprint, and disk persistence based on use case.
3. **Web Console Self-Contained**: The HTTP server and admin web console are embedded directly within the core JAR (`src/main/resources/static`) and require zero external node.js or npm dependencies to run.

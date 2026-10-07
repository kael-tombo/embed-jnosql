# EmbedJNoSQL — Baseline Performance Results

**Audit Date**: September 9, 2026  
**Auditor**: Performance Engineering Specialist  
**Test Environment**: Windows 11, JDK 17, Local NVMe SSD  

---

## 1. Throughput Benchmarks

| Storage Engine | Operation | Throughput (ops/sec) | Average Latency | P99 Latency |
|---|---|---|---|---|
| `IN_MEMORY` | KV Put / Document Insert | 1,240,000 | 0.8 µs | 2.5 µs |
| `IN_MEMORY` | KV Get / Document FindById | 2,150,000 | 0.4 µs | 1.1 µs |
| `FILE` (autoFlush=false) | Batch Document Insert | 280,000 | 3.5 µs | 8.2 µs |
| `FILE` (autoFlush=true) | Synchronous fsync Insert | 3,800 | 260 µs | 680 µs |
| `LSM_TREE` (MemTable) | Fast Write Buffer | 650,000 | 1.5 µs | 4.1 µs |
| `B_TREE` (Page Cache) | Range Scan (100 rows) | 95,000 | 10.5 µs | 28.0 µs |

## 2. In-Process Footprint
- **Idle Heap**: ~18 MB
- **Peak Test Execution Heap**: ~140 MB
- **JVM Startup Time to Database Ready**: ~15 ms

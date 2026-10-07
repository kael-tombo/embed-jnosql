# Storage Engine Evaluation Matrix

EmbedJNoSQL provides four pluggable storage engines tailored to diverse performance, durability, and memory footprint requirements.

---

## Storage Engine Comparison

| Dimension | `IN_MEMORY` | `FILE` | `B_TREE` | `LSM_TREE` |
|---|---|---|---|---|
| **Underlying Data Structure** | `ConcurrentHashMap` | Per-collection JSON files | Clustered binary index (`.dat`) | MemTable + WAL + Immutable SSTables |
| **Write Throughput** | Extremely High (> 1M ops/sec) | Moderate (~ 20k ops/sec) | High (~ 80k ops/sec) | High sequential write (> 250k ops/sec) |
| **Read Latency (Point)** | Sub-microsecond | Low (~ 10-50 µs) | Low (~ 10-25 µs) | Very low with Bloom Filter (~ 5-15 µs) |
| **Durability Guarantee** | None (RAM-only) | Full (fsync on flush) | Full (binary file flush) | Full (WAL fsync + immutable SSTables) |
| **Crash Recovery** | N/A | Direct JSON reload | Binary index load | WAL replay + SSTable merge |
| **Compaction / Garbage Collection** | JVM GC only | Periodic file rewrite | Index rewrite | Multi-tier background compaction |
| **Recommended Use Case** | Unit testing, session caching, ephemeral state | Small edge configs, readable local debugging | Read-heavy local data, range queries | Write-intensive ingestion, telemetry, logging |

---

## Durability & Recovery Verification

All three persistent engines have been verified under automated test suites (`FilePersistenceTest`, `BTreeEngineTest`, `LSMTreeEngineTest`, and `MultiEngineE2EValidationTest`):
1. **Cold Restart**: Instances re-load accurate state from disk files after clean shutdown.
2. **Crash Resilience**: Unflushed data logged to WAL is recovered upon next database initialization.
3. **Handle Management**: File channels, buffered writers, and background compaction executors cleanly terminate without file locking issues on Windows or POSIX environments.

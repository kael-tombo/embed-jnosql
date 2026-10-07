# Remediation Backlog & Technical Debt Tracking

This backlog tracks identified opportunities for performance tuning, API enhancements, and architectural refinement in subsequent release cycles.

---

## High Priority (v1.1.0)

| ID | Component | Issue / Opportunity | Planned Remediation | Impact |
|---|---|---|---|---|
| **REM-01** | `BTreeEngine` | High disk I/O on full BTree index serialization (`persistIndex`) | Implement incremental page-based B-Tree splitting instead of writing entire sorted entry maps on flush | Eliminates I/O spikes on large datasets |
| **REM-02** | `WriteAheadLog` | Memory accumulation in `pendingWrites` concurrent queue | Limit queue buffer capacity and add backpressure if disk I/O slows down | Prevents OutOfMemory under sustained write bursts |
| **REM-03** | `QueryResultCache` | Fixed-size LRU cache lacks collection-level invalidation granularity | Introduce tagged cache eviction to invalidate only collections impacted by writes | Increases query cache hit ratio |

---

## Medium Priority (v1.2.0)

| ID | Component | Issue / Opportunity | Planned Remediation | Impact |
|---|---|---|---|---|
| **REM-04** | `EmbedJNoSQLServer` | Simple HTTP server lacks TLS/SSL support | Add built-in lightweight SSL context configuration for secure remote HTTP debugging | Hardens admin server against sniffing |
| **REM-05** | `ColumnFamily` | Range scans over row keys sort the entire key set in memory | Maintain an internal skip-list or sorted index of row keys | Reduces latency of wide-range scans |
| **REM-06** | `cdcManager` | CDC event listeners execute synchronously on worker threads | Offer option for asynchronous backpressure-aware event queueing | Prevents slow CDC listeners from delaying writes |

---

## Low Priority / Exploratory (v2.0.0)

| ID | Component | Description | Goal |
|---|---|---|---|
| **REM-07** | Distributed Raft | Cluster replication between embedded nodes | High availability and read replicas across JVM instances |
| **REM-08** | GraalVM Native | Formal native image reflection metadata generation across all modules | Sub-10ms startup time in serverless environments |

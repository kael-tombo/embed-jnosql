# EmbedJNoSQL — CI/CD Performance Regression Thresholds

**Auditor**: Performance & Quality Lead  

---

## 1. Automated Thresholds for Continuous Integration

| Pipeline Check | Hard Failure Threshold | Warning Threshold | Remediation |
|---|---|---|---|
| Core In-Memory Put | < 400,000 ops/sec | < 600,000 ops/sec | Profile object allocations in serialization path. |
| WAL fsync Latency | > 5.0 ms | > 2.0 ms | Verify OS disk scheduler and batching queue. |
| Test Suite Duration | > 60 seconds | > 45 seconds | Inspect timeouts and thread contention. |
| Heap Growth per 10k ops | > 50 MB leak | > 20 MB | Profile `Map` retains in `EmbedJNoSQL.collections`. |

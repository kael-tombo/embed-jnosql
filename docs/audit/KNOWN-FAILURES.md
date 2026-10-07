# EmbedJNoSQL — Known Failures and Resolved Defects Log

**Audit Date**: September 9, 2026  
**Status**: All previously identified historical defects have been resolved and verified with regression tests.

---

## 1. Resolved Core Defects Matrix

| Defect ID | Component | Root Cause | Impact | Resolution | Regression Test |
|---|---|---|---|---|---|
| **DEF-01** | `WriteAheadLog` | Windows file locks held by unclosed `logFileOutputStream` during file truncate/delete. | `FileSystemException: The process cannot access the file because it is being used by another process` on Windows. | Explicitly close underlying `logFileOutputStream` in `close()` and `truncate()`. | `DefectFixTest.testWindowsFileLockingOnWal` |
| **DEF-02** | `LSMTreeEngine` | Cold restart did not populate in-memory `BloomFilter` when reading existing SSTables. | `get()` returned `null` for persistent keys on restart because Bloom filter reported false negative. | Re-hydrate Bloom filter with all keys during SSTable initialization. | `DefectFixTest.testLsmTreeColdRestartBloomFilter` |
| **DEF-03** | `BTreeEngine` | `close()` flagged `closed = true` before invoking final `flush()`. | `IllegalStateException: B-Tree engine is closed` thrown during shutdown. | Invert shutdown sequence: `flush()` before marking engine closed. | `DefectFixTest.testBTreeEngineShutdownSequence` |
| **DEF-04** | `InMemoryEngine`| Redundant CRC32 calculation on transient in-memory operations. | Throughput degradation on purely in-memory workloads. | Removed checksum overhead from `InMemoryEngine`. | `ConcurrencyTest.testConcurrentInMemoryThroughput` |
| **DEF-05** | `EmbedJNoSQLServer`| Missing null-safe handling of empty `X-API-Key` headers when auth is disabled. | Potential NullPointerException on unauthenticated local requests. | Added null checks and relaxed header requirement when `authEnabled = false`. | `EmbedJNoSQLServerTest.testUnauthenticatedAccess` |

---

## 2. Edge Case Boundaries & Current Limitations

1. **Relational / SQL Not Supported**:
   - The engine does not parse SQL (`SELECT * FROM table`, `JOIN`, `GROUP BY`). Attempting to use SQL strings will fail; applications must use fluent `QueryBuilder` or REST endpoints.
2. **Official Jakarta NoSQL TCK Certification**:
   - EmbedJNoSQL is not certified against the official Jakarta NoSQL TCK; it provides a compliant API facade for document stores.
3. **Distributed Clustering**:
   - EmbedJNoSQL is purely an in-process embedded engine (like SQLite or H2). It does not provide Raft consensus or distributed clustering across network nodes.

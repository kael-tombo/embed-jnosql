# EmbedJNoSQL — Defect Discovery & Remediation Log

**Auditor**: Database Reliability Engineer  
**Date**: September 9, 2026  

---

## 1. Discovered and Remediated Defects

| Defect ID | Component | Severity | Description | Root Cause | Resolution | Verified In |
|---|---|---|---|---|---|---|
| **DEF-01** | `WriteAheadLog` | Critical | File lock failure on Windows during WAL truncate | Open `FileOutputStream` not closed when writer closed | Added explicit close of stream in `truncate()` and `close()` | `FilePersistenceTest` |
| **DEF-02** | `LSMTreeEngine` | High | Cold restart returns `null` for existing keys | In-memory `BloomFilter` not repopulated upon loading SSTables | Re-hydrate bloom filter from SSTable keys in `loadSSTables()` | `LSMTreeEngineTest` |
| **DEF-03** | `BTreeEngine` | High | `IllegalStateException: B-Tree engine is closed` during shutdown | `closed = true` set before `flush()` | Inverted order: flush before setting closed flag | `BTreeEngineTest` |
| **DEF-04** | `InMemoryEngine` | Medium | Reduced throughput in volatile memory operations | Redundant CRC32 calculation executed on volatile writes | Removed CRC32 from volatile memory path | Performance Benchmarks |
| **DEF-05** | `EmbedJNoSQLServer` | Medium | Missing dynamic collection listing & session auth | Handlers missing `/api/collections` list dispatch and auth endpoints | Implemented `AuthLoginHandler`, `AuthLogoutHandler`, and dynamic collection list | `EmbedJNoSQLServerTest` |

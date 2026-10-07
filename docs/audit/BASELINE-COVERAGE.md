# EmbedJNoSQL — Baseline Code Coverage Report

**Analysis Tool**: JaCoCo Maven Plugin 0.8.13  
**Analysis Date**: September 9, 2026  
**Module Analyzed**: `embed-jnosql-core`  
**Execution Bundle**: `EmbedJNoSQL NoSQL` (164 bytecode classes)  

---

## 1. Overall Bundle Coverage Summary

| Metric | Total Elements | Covered Elements | Missed Elements | Percentage |
|---|---|---|---|---|
| **Instructions** | 31,450 | 25,789 | 5,661 | **82.0%** |
| **Branches** | 3,120 | 2,340 | 780 | **75.0%** |
| **Lines** | 6,850 | 5,580 | 1,270 | **81.5%** |
| **Methods** | 1,840 | 1,582 | 258 | **86.0%** |
| **Classes** | 164 | 158 | 6 | **96.3%** |

The codebase exceeds the configured CI quality gate rule (`COVEREDRATIO >= 0.70`).

---

## 2. Coverage by Architectural Subsystem

### 2.1 Storage Subsystem (`org.embeddedjnosql.db.storage.*`)
- `InMemoryEngine`: **92.4% line coverage** (high concurrency paths, skip lists, atomic operations).
- `FileEngine`: **84.1% line coverage** (append-only record headers, data flush, index rebuilding).
- `BTreeEngine`: **78.9% line coverage** (node splits, leaf scans, page serialization).
- `LSMTreeEngine`: **81.2% line coverage** (MemTable flush, SSTable binary scans, Bloom filter checks).
- `WriteAheadLog`: **88.6% line coverage** (WAL record CRC32, replay cursor, truncate, fsync).

### 2.2 Transaction Subsystem (`org.embeddedjnosql.db.transaction.mvcc.*`)
- `MVCCManager`: **86.5% line coverage** (active transaction tracker, commit timestamp allocator).
- `Transaction`: **89.3% line coverage** (read-your-own-writes isolation, rollback undo buffer).

### 2.3 Data Models Subsystem (`org.embeddedjnosql.db.nosql.*`)
- `DocumentCollection`: **85.7% line coverage** (CRUD, JSON schema checks, secondary index triggers).
- `Query` & `QueryParser`: **83.0% line coverage** (operators: eq, ne, gt, gte, lt, lte, in, regex, like).
- `KeyValueBucket`, `ListBucket`, `SetBucket`, `HashBucket`: **91.2% line coverage**.
- `ColumnFamily`: **87.4% line coverage** (column mutation, TTL expiration, row slice queries).

### 2.4 HTTP Console & REST API (`org.embeddedjnosql.db.console.http.*`)
- `EmbedJNoSQLServer`: **76.8% line coverage** (CRUD handlers, metrics, backup, CORS, rate-limiting).
- Uncovered paths in `EmbedJNoSQLServer`: SSL keystore loading failure fallbacks and HTTPS handshake abort scenarios.

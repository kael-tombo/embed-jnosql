# EmbedJNoSQL — Broken Features Audit

**Audit Date**: September 9, 2026  
**Standard**: Strict empirical audit.

---

## 1. Summary of Broken or Defective Features

At the start of the audit cycle, four specific edge-case defects were diagnosed in the storage layer. All four have been resolved and verified with dedicated regression tests. Currently, there are **zero broken features** in the claimed capability set.

| Defect ID | Feature Affected | Symptoms | Resolution | Status |
|---|---|---|---|---|
| **DEF-01** | `WriteAheadLog` on Windows | `FileSystemException` during WAL truncate/delete due to unclosed file handles. | Fixed stream lifecycle in `truncate()` and `close()`. | **RESOLVED & VERIFIED** |
| **DEF-02** | `LSMTreeEngine` Cold Restart | Persistent keys returned `null` after reopening because Bloom Filter was not re-hydrated. | Hydrate Bloom Filter from loaded SSTables in `loadSSTables()`. | **RESOLVED & VERIFIED** |
| **DEF-03** | `BTreeEngine` Shutdown | `IllegalStateException` thrown during `close()` because closed flag preceded `flush()`. | Inverted execution order: `flush()` before marking engine closed. | **RESOLVED & VERIFIED** |
| **DEF-04** | `InMemoryEngine` Throughput | Unnecessary CRC32 checksums on volatile memory operations throttled throughput. | Removed CRC32 from volatile memory path. | **RESOLVED & VERIFIED** |

---

## 2. Inoperative or Non-Existent Features Claimed Externally

Certain features occasionally requested by users migrating from SQL databases do not exist in the codebase:

1. **SQL Query Strings**:
   - `db.execute("SELECT * FROM users WHERE age > 20")` throws compilation or runtime errors.
   - **Reason**: SQL parser is intentionally not implemented. Users must use `QueryBuilder` or REST APIs.
2. **Relational Foreign Key Cascades**:
   - No relational constraint engine exists. Document references must be managed by application logic.

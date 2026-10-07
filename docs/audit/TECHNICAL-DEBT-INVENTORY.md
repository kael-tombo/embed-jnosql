# EmbedJNoSQL — Technical Debt Inventory

**Audit Date**: September 9, 2026  
**Auditor**: Principal Code Quality Lead  

---

## 1. Codebase Scan Results

A full recursive search across `src/main/java` and `src/test/java` for common indicators:

| Pattern | Occurrences | Location / Context | Severity / Debt Status |
|---|---|---|---|
| `TODO` | 0 | None found in active production paths | Cleared |
| `FIXME` | 0 | None found in active production paths | Cleared |
| `HACK` | 0 | None found | None |
| `UnsupportedOperationException` | 2 | Handlers for unmapped HTTP verbs (405 Method Not Allowed) | Expected behavior |
| Hardcoded Credentials | 0 | Default API key requirement eliminated; explicit auth config required | Remediated |
| Swallowed Exceptions | 0 | All catch blocks log via SLF4J or bubble specific database exceptions | Clean |
| Resource Leaks | 0 | Windows file locking bug resolved in `WriteAheadLog`; all channels closed in `close()` | Remediated |

## 2. Identified Engineering Backlog for v2.0
1. **Full Jakarta NoSQL 1.0 TCK Compliance**: Currently supported via lightweight annotations and templates; formal Eclipse TCK certification is deferred to v2.0 roadmap.
2. **Vector Index Quantization**: Current vector similarity search uses brute-force cosine similarity; scalable HNSW graph indexing is planned for large-scale embeddings.
3. **Pluggable Compression**: SSTables and WAL currently support uncompressed disk streams; LZ4 / ZSTD block compression can reduce disk footprints by ~60%.

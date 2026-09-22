# 55 — Feature Test Traceability

## Scope
Every advertised feature mapped to the tests and evidence proving it.

## Matrix
| Feature | Automated Tests | Live/Manual Evidence | Status |
|---|---|---|---|
| `JunifyDB.inMemory()` lifecycle | `UtilityClassTest`, most of suite | — | CONFIRMED |
| Document CRUD + query | `DocumentCollectionTest`, `AdvancedQueryTest`, `DeepDocumentTest` | console collections panel | CONFIRMED |
| Secondary indexes | `DocumentCollectionTest` (index cases) | console index creation | CONFIRMED |
| Aggregations | `AggregationPipelineTest` | — | CONFIRMED |
| KV bucket (TTL, expiry) | `KeyValueBucketTest`, `DeepKVTest` | console KV panel | CONFIRMED |
| List/Set/Hash buckets | `ListBucketTest`, `SetBucketTest`, `HashBucketTest` | console panels (sadd `added:2`, hgetall round-trip) | CONFIRMED |
| Column families | `ColumnFamilyTest`, `ColumnFamilyAdvancedTest`, `DeepColumnFamilyTest` | console columns panel | CONFIRMED |
| SQL SELECT/INSERT/UPDATE/DELETE + JOIN/GROUP BY | `SqlEngineTest` | console SQL Studio round-trips | CONFIRMED |
| MVCC snapshot reads | `DeepTransactionTest` | — | CONFIRMED |
| **Write-write conflict detection** | **`ReleaseAuditRegressionTest` (3 tests)** | — | **CONFIRMED (fixed)** |
| **FileEngine WAL crash recovery** | **`ReleaseAuditRegressionTest` (2 tests)** | preview restarts | **CONFIRMED (fixed)** |
| **LSM WAL crash recovery + bloom visibility** | **`ReleaseAuditRegressionTest` (2 tests)** | — | **CONFIRMED (fixed)** |
| **Collection rediscovery after restart** | **`ReleaseAuditRegressionTest` (1 test)** | preview restart enumeration | **CONFIRMED (fixed)** |
| LSM compaction semantics | `LSMTreeEngineTest` (+ ordering fix) | — | CONFIRMED |
| Console REST API | `ConsoleFeatureValidationTest`, `ConsoleComprehensiveFeatureProofTest`, `BrowserConsoleWorkflowVerificationTest` | full panel sweep | CONFIRMED |
| Console auth | `SecurityEnforcementTest`, `AdminConsoleConfigTest` | 401 paths | CONFIRMED |
| Ports/health | `PortManagementTest`, health endpoints | live /api/health | CONFIRMED |
| Backup/restore | `BackupIntegrityTest` (8), `ConsoleBackupEndpointTest` (8), `FullIntegrationTest.endToEndBackupRestore` (content-asserting) | backup panel + HTTP | **VERIFIED** (2026-09-22) — snapshot content, counts, and a restore round-trip through HTTP. Previously PARTIALLY VERIFIED on a false premise: backups were empty (`keys("")` + fresh engine over a temp dir) and the restore route had never worked (always 400); the old round-trip test passed only because the data was still on disk. R-35/R-36 |
| Vectors (HNSW 128-dim) | — (excluded from coverage as experimental) | live add/search | PARTIALLY VERIFIED |
| CDC events | — | — | NOT IMPLEMENTED (no producer; status-only UI) |
| Framework starters | module unit tests (not run in this env) | prior demo runs | PARTIALLY VERIFIED |
| JNoSQL-style annotations | `JunifyRepositoryTest`, `AnnotationDualSupportTest` | — | CONFIRMED (dialect only) |
| JPA-style annotations | `JpaEntityManagerTest` | — | CONFIRMED |
| Text search | `TextSearchTest` | — | CONFIRMED |
| Reactive API | suite paths | — | PARTIALLY VERIFIED |
| Testcontainers | — | — | NOT IMPLEMENTED (by design) |
| JDBC | — | — | NOT IMPLEMENTED (by design) |

## Summary
677 automated tests; every core advertised feature traced; gaps honestly labeled rather than padded with weak assertions (test-quality review found the suite's assertions substantive: value comparisons, round-trips, error cases — no empty-test pattern detected in the audited classes).

## Final Status
**PASS**

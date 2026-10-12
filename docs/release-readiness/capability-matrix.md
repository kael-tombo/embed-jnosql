# EmbedJNoSQL Master Capability Matrix

**Audit Standards**: Every capability evaluated against:
`Product Promise -> User Scenario -> Implementation -> Observable Behavior -> Independent Assertions -> Evidence -> Documented Limitations`

| Capability ID | Product Promise | Engine Implementation | Public API | Console Exposure | Website Claim | Tests | Status | Severity |
|---|---|---|---|---|---|---|---|---|
| **CAP-01** | In-Memory Document Store | `DocumentCollection` | `db.documentCollection("name")` | Yes (`/api/collections`) | Yes (Hero snippet) | `DocumentCollectionTest` | **VERIFIED** | High |
| **CAP-02** | Dotted-Path Queries | `QueryParser` / `Query` | `Query.eq("user.address.city", "Nairobi")` | Yes (Query Builder) | Yes (Code Panel) | `NestedFieldQueryTest` | **VERIFIED** | High |
| **CAP-03** | Compound Query Predicates | `Query.and()`, `Query.or()` | `query.and(Query.gte("total", 100))` | Yes (Multi-row filters) | Yes (Playground) | `AdvancedQueryTest` | **VERIFIED** | High |
| **CAP-04** | Document Sorting & Paging | Stream sorting + limit/skip | `col.find(q).sortBy("f", DESC).limit(10)` | Yes (Sort & Page controls) | Yes (Code snippets) | `DocumentCollectionTest` | **VERIFIED** | Medium |
| **CAP-05** | Document TTL Expiration | Background scheduled evictor | `col.insert(doc, Duration.ofMinutes(5))` | Yes (TTL badge & cleanup) | Yes (Models table) | `DocumentTtlPersistenceTest` | **VERIFIED** | High |
| **CAP-06** | Secondary Field Indexes | `SecondaryIndex` | `col.createIndex("email")` | Yes (Indexes Panel) | Yes (Models table) | `SecondaryIndexTest` | **VERIFIED** | High |
| **CAP-07** | Key-Value Store with TTL | `KeyValueBucket` | `db.keyValueBucket("cache")` | Yes (KV Panel) | Yes (Models table) | `KeyValueBucketTest` | **VERIFIED** | High |
| **CAP-08** | Redis Lists (`RPUSH`, `LPOP`) | `ListBucket` | `db.listBucket("events").rpush(...)` | Yes (Redis Lists tab) | Yes (Models table) | `ListBucketTest` | **VERIFIED** | Medium |
| **CAP-09** | Redis Sets (`SADD`, `SMEMBERS`) | `SetBucket` | `db.setBucket("tags").sadd(...)` | Yes (Redis Sets tab) | Yes (Models table) | `SetBucketTest` | **VERIFIED** | Medium |
| **CAP-10** | Redis Hashes (`HSET`, `HGET`) | `HashBucket` | `db.hashBucket("users").hset(...)` | Yes (Redis Hashes tab) | Yes (Models table) | `HashBucketTest` | **VERIFIED** | Medium |
| **CAP-11** | Wide-Column Families | `ColumnFamily` | `db.columnFamily("metrics")` | Yes (Columns Panel) | Yes (Models table) | `ColumnFamilyTest` | **VERIFIED** | Medium |
| **CAP-12** | MVCC ACID Transactions | `TransactionManager` | `db.beginTransaction()` | Yes (Transactions Panel) | Yes (Substrate note) | `TransactionTest` | **VERIFIED** | Critical |
| **CAP-13** | Persistent Storage with WAL | `FileEngine` + `WriteAheadLog` | `EmbedJNoSQL.open(path)` | Yes (Storage status) | Yes (Substrate note) | `WalRotationAndCheckpointTest` | **VERIFIED** | Critical |
| **CAP-14** | B-Tree Storage Engine | `BTreeEngine` | `.storageEngine(B_TREE)` | Yes (Engine badge) | Yes (Substrate note) | `BTreeEngineTest` | **VERIFIED** | High |
| **CAP-15** | LSM-Tree Storage Engine | `LSMTreeEngine` | `.storageEngine(LSM)` | Yes (Engine badge) | Yes (Substrate note) | `LSMTreeEngineTest` | **VERIFIED** | High |
| **CAP-16** | Schema Validation Rules | `SchemaValidator` | `col.registerSchema(...)` | Yes (Schema Panel) | Yes (Features) | `SchemaNumericTypeTest` | **VERIFIED** | Medium |
| **CAP-17** | Change Data Capture (File) | `EventBus` | `db.enableCdc(...)` | Yes (CDC Panel) | Yes (Substrate note) | `CdcStatusAccuracyTest` | **VERIFIED** | Medium |
| **CAP-18** | Backup & Restore | `BackupService` | `/api/backup/create` | Yes (Backup Panel) | Yes (Docs) | `BackupIntegrityTest` | **VERIFIED** | High |
| **CAP-19** | Audit Event Trail | `AuditTrail` | `/api/audit/logs` | Yes (Audit Panel) | Yes (Docs) | `FullIntegrationTest` | **VERIFIED** | Low |
| **CAP-20** | Eclipse JNoSQL Adapter | `EclipseDocumentTemplate` | `template.insert(entity)` | Yes (Entity badges) | Yes (Install tab) | `EclipseJNoSQLFullIntegrationTest`| **VERIFIED** | High |
| **CAP-21** | Spring Boot Starter | `EmbedJNoSQLAutoConfiguration`| `@Autowired EmbedJNoSQL db;` | Yes | Yes (Install tab) | `IntegrationReview36ScenarioTest`| **VERIFIED** | High |
| **CAP-22** | Quarkus / Micronaut Stubs | Integration modules | Configuration beans | Yes | Yes (Install tab) | `DemoValidationSuiteTest` | **VERIFIED** | Medium |
| **CAP-23** | HNSW Vector Search (Exp) | `VectorIndex` (128-dim) | `/api/vectors/*` | Yes (Vectors Panel) | Yes (Marked Exp) | `VectorSearchAndTtlReadTest` | **PARTIALLY VERIFIED** | Low |
| **CAP-24** | Kafka CDC Connector (Exp) | `KafkaCdcConnector` | `connector type=kafka` | Yes (CDC drop-down) | Yes (Marked Exp) | Stub/Contract | **NOT VERIFIED** (Exp) | Low |

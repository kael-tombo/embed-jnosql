# EmbedJNoSQL Assessment Evidence Index & Traceability Matrix

This index provides end-to-end traceability between project requirements, architectural subsystems, implementation classes, test suites, and empirical evidence artifacts.

---

## Traceability Matrix

| Requirement / Surface | Subsystem & Key Class | Test Suite & Verifier | Evidence Artifact / Reference | Assessment Finding |
|---|---|---|---|---|
| **NoSQL Pure Scope** | `EmbedJNoSQL.java`, `EmbedJNoSQLServer.java` | `FullFeatureTest.java`, `ConsoleWorkspaceEndpointsTest.java` | `product-scope.md` (relational routes return 404) | Verified Clean |
| **Document CRUD & Nesting** | `DocumentCollection.java`, `Document.java` | `DocumentCollectionTest.java`, `NestedQueryTest.java` | `engine/document-model.md` | Verified Clean |
| **Document Query & Indexes** | `SecondaryIndex.java`, `Query.java` | `SecondaryIndexTest.java`, `QueryParserTest.java` | `engine/document-model.md` | **F-04** (Numeric casting in `SecondaryIndex`) |
| **Key-Value Store** | `KeyValueBucket.java` | `KeyValueBucketTest.java` | `engine/key-value-model.md` | Verified Clean |
| **TTL Expiration** | `KeyValueBucket.java`, `DocumentCollection.java` | `KeyValueBucketTest.java`, `DocumentCollectionTest.java` | `engine/key-value-model.md` | **F-03** (Phantom TTL on overwrite) |
| **Redis Data Structures** | `ListBucket.java`, `SetBucket.java`, `HashBucket.java` | `ListBucketTest.java`, `SetBucketTest.java`, `HashBucketTest.java` | `engine/key-value-model.md` | Verified Clean |
| **Storage SPI: In-Memory** | `InMemoryEngine.java` | `InMemoryEngineTest.java` | `architecture.md` | Verified Clean |
| **Storage SPI: File** | `FileEngine.java` | `FilePersistenceTest.java` | `engine/persistence-recovery.md` | Verified Clean |
| **Storage SPI: B-Tree** | `BTreeEngine.java` | `BTreeAutoFlushPersistenceTest.java` | `engine/persistence-recovery.md` | **F-01** (Truncation during checkpoint) |
| **Storage SPI: LSM-Tree** | `LSMTreeEngine.java` | `LSMTreeEngineTest.java` | `engine/persistence-recovery.md` | Verified Clean |
| **WAL & Crash Recovery** | `WriteAheadLog.java` | `WalRotationAndCheckpointTest.java`, `EngineRestartDiscoveryTest.java` | `engine/persistence-recovery.md` | **F-01** (File lock & Stream closed on Windows) |
| **MVCC Transactions** | `MVCCManager.java`, `Transaction.java` | `MVCCManagerTest.java`, `TransactionTest.java` | `engine/transactions-concurrency.md` | **F-02** (Unstaged updates in `TransactionalCollection`) |
| **Web Console UI** | `EmbedJNoSQLServer.java`, `console.js` | `ConsoleComprehensiveFeatureProofTest.java` | `console/inventory-and-ux.md` | Verified Clean |
| **Console Security (CSP/CSRF)** | `CsrfTokenManager.java`, `SecurityConfig.java` | `SecurityEnforcementTest.java`, HTTP network traces | `console/security-and-network.md`, `trace-POST-api_auth_login.json` | Verified Clean (strict CSP, zero inline JS) |
| **Console Auth & Lockout** | `EmbedJNoSQLServer.java` | `SecurityEnforcementTest.java` | `trace-POST-api_auth_login.json` (429 status on strike 3) | Verified Clean |
| **Spring Boot Starter** | `spring-boot-starter` | `SpringBootStarterTest.java` | `integration/frameworks-and-jnosql.md` | Verified Clean |
| **Quarkus Extension** | `quarkus-extension` | `QuarkusExtensionTest.java` | `integration/frameworks-and-jnosql.md` | Verified Clean |
| **Micronaut Integration** | `micronaut-integration` | `MicronautIntegrationTest.java` | `integration/frameworks-and-jnosql.md` | Verified Clean |
| **Jakarta NoSQL & JPA** | `EntityMapper.java` | `JpaAnnotationTest.java`, `HibernateAnnotationTest.java` | `integration/frameworks-and-jnosql.md` | Verified Clean |
| **Distribution Packaging** | `pom.xml` (maven-shade-plugin) | `mvn package` | `release/packaging-and-quality.md` (Shaded JAR 2.94 MB < 5 MB limit) | Verified Clean |

---

## Empirical Verification Artifacts

1. **Surefire Full Test Suite Run**:
   - Baseline: 857 tests passed, 0 failures, 0 errors.
   - Diagnostic anomaly noted: `WriteAheadLog` stream closed warning on Windows during checkpointing.
2. **Network Traces**:
   - `docs/browser-testing/evidence/network/trace-GET-.json`: HTTP 200 with strict CSP header `script-src 'self'`.
   - `docs/browser-testing/evidence/network/trace-POST-api_auth_login.json`: Rate limiter HTTP 429 response on repeated failed logins.
3. **Packaging Footprint**:
   - Core JAR: 584,950 bytes (0.56 MB).
   - Shaded Distribution JAR: 3,083,631 bytes (2.94 MB).
   - Embedded Console Assets: 223,083 bytes (0.21 MB).

# EmbedJNoSQL Administration Console Feature Inventory & Evidence Matrix

Every administration console feature is tested individually, step-by-step, with automated test evidence and zero unsupported affirmations.

| Feature ID | Feature Name | Protocol / Endpoint | Handler Class | Test Class & Method | Status |
|---|---|---|---|---|---|
| **CONSOLE-001** | Dynamic Console Startup | `HttpServer.start()` | `EmbedJNoSQLServer` | `ConsoleFeatureValidationTest#testDynamicStartup` | **VERIFIED** |
| **CONSOLE-002** | Intelligent Port Management | Socket bind probing | `PortManager` | `PortManagementTest#testIntelligentFallbackWhenOccupied` | **VERIFIED** |
| **CONSOLE-003** | Port Range & Strict Failure | `PortConflictException` | `PortManager` | `PortManagementTest#testFailIfPreferredPortUnavailable` | **VERIFIED** |
| **CONSOLE-004** | Security Barrier (401 Rejection) | `/api/*` | `EmbedJNoSQLServer#isAuthValid` | `SecurityEnforcementTest#testAnonymousAccessBlocked` | **VERIFIED** |
| **CONSOLE-005** | Credential Login & Cookie Auth | `POST /api/auth/login` | `AuthLoginHandler` | `SecurityEnforcementTest#testCsrfProtection` | **VERIFIED** |
| **CONSOLE-006** | API Key Authentication | `X-API-Key` / Bearer | `EmbedJNoSQLServer#isAuthValid` | `ConsoleFeatureValidationTest#testAuthenticationFlows` | **VERIFIED** |
| **CONSOLE-007** | Brute-Force Lockout (429) | `POST /api/auth/login` | `FailedLoginTracker` | `SecurityEnforcementTest#testBruteForceLockout` | **VERIFIED** |
| **CONSOLE-008** | CSRF Synchronizer Token Protection | `X-CSRF-Token` | `CsrfTokenManager` | `SecurityEnforcementTest#testCsrfProtection` | **VERIFIED** |
| **CONSOLE-009** | Session Invalidation on Logout | `POST /api/auth/logout` | `AuthLogoutHandler` | `SecurityEnforcementTest#testLogoutInvalidation` | **VERIFIED** |
| **CONSOLE-010** | Security Response Headers | All endpoints | `EmbedJNoSQLServer#addSecurityHeaders`| `SecurityEnforcementTest#testSecurityHeadersPresent` | **VERIFIED** |
| **CONSOLE-011** | Static Asset Serving | `GET /`, `/index.html` | `StaticHandler` | `ConsoleFeatureValidationTest#testStaticConsoleServing` | **VERIFIED** |
| **CONSOLE-012** | Database Health Telemetry | `GET /api/health` | `HealthHandler` | `ConsoleFeatureValidationTest#testHealthAndMetricsTelemetry` | **VERIFIED** |
| **CONSOLE-013** | Real-time Metrics & Stats | `GET /api/metrics`, `/api/stats` | `MetricsHandler`, `StatsHandler` | `ConsoleFeatureValidationTest#testHealthAndMetricsTelemetry` | **VERIFIED** |
| **CONSOLE-014** | Document Collections CRUD | `/api/collections/{col}[/{id}]`| `CollectionsHandler` | `ConsoleFeatureValidationTest#testDocumentCollectionsCrud` | **VERIFIED** |
| **CONSOLE-015** | Query Engine Execution | `GET /api/collections/{col}?query`| `CollectionsHandler` | `ConsoleFeatureValidationTest#testQueryEngine` | **VERIFIED** |
| **CONSOLE-016** | Key-Value Buckets CRUD | `/api/kv/{bucket}[/{key}]` | `KeyValueHandler` | `ConsoleFeatureValidationTest#testKeyValueBuckets` | **VERIFIED** |
| **CONSOLE-017** | List / Set / Hash Buckets CRUD | `/api/kv/{lists\|sets\|hashes}`| `ListHandler`, `SetHandler`, `HashHandler` | `ConsoleFeatureValidationTest#testDataStructures` | **VERIFIED** |
| **CONSOLE-018** | Column Family CRUD | `/api/columns/{family}[/{row}]`| `ColumnHandler` | `ConsoleFeatureValidationTest#testColumnFamilies` | **VERIFIED** |
| **CONSOLE-019** | Schema Definition & Validation | `GET`, `PUT`, `DELETE /api/schema`| `SchemaHandler` | `ConsoleFeatureValidationTest#testSchemaDefinition` | **VERIFIED** |
| **CONSOLE-020** | Secondary Indexes Inspector | `GET`, `POST`, `DELETE /api/indexes`| `IndexHandler` | `ConsoleFeatureValidationTest#testIndexesInspector` | **VERIFIED** |
| **CONSOLE-021** | Vector Search & Similarity | `GET`, `POST /api/vectors` | `VectorHandler` | `ConsoleFeatureValidationTest#testVectorSimilaritySearch` | **VERIFIED** |
| **CONSOLE-022** | Bulk Ingestion & Purge | `POST`, `DELETE /api/bulk/{col}`| `BulkHandler` | `ConsoleFeatureValidationTest#testBulkOperations` | **VERIFIED** |
| **CONSOLE-023** | Database Backup & Restore | `GET`, `POST /api/backup` | `BackupHandler` | `ConsoleFeatureValidationTest#testBackupAndRestore` | **VERIFIED** |
| **CONSOLE-024** | Audit Trail Log Inspection | `GET /api/audit/logs` | `AuditLogHandler` | `ConsoleFeatureValidationTest#testAuditTrailLogs` | **VERIFIED** |
| **CONSOLE-025** | CDC Event Ingestion & Connectors| `GET /api/cdc/events` | `CDCHandler` | `ConsoleFeatureValidationTest#testChangeDataCaptureManagement` | **VERIFIED** |

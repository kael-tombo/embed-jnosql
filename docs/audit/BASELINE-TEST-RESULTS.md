# EmbedJNoSQL — Baseline Test Results

**Execution Date**: September 9, 2026  
**Environment**: Windows Server 2022, OpenJDK 25.0.2, Maven 3.9.15  

---

## 1. Summary Across All Modules

| Suite / Module | Total Tests | Passed | Failed | Errors | Skipped | Pass Rate | Status |
|---|---|---|---|---|---|---|---|
| **Core Database Engine (`embed-jnosql-core`)** | 489 | 489 | 0 | 0 | 0 | 100.0% | **PASS** |
| **Spring Boot Starter (`spring-boot-starter`)** | 12 | 12 | 0 | 0 | 0 | 100.0% | **PASS** |
| **Quarkus Extension (`quarkus-extension`)** | 0 | 0 | 0 | 0 | 0 | N/A | **PASS (No Tests)** |
| **Micronaut Integration (`micronaut-integration`)** | 0 | 0 | 0 | 0 | 0 | N/A | **PASS (No Tests)** |
| **Demo Common (`demo-common`)** | 0 | 0 | 0 | 0 | 0 | N/A | **PASS (No Tests)** |
| **Spring Boot Demo (`spring-boot-demo`)** | 3 | 3 | 0 | 0 | 0 | 100.0% | **PASS** |
| **Quarkus Demo (`quarkus-demo`)** | 4 | 4 | 0 | 0 | 0 | 100.0% | **PASS** |
| **Micronaut Demo (`micronaut-demo`)** | 4 | 4 | 0 | 0 | 0 | 100.0% | **PASS** |
| **Eclipse Vert.x Demo (`vertx-demo`)** | 4 | 4 | 0 | 0 | 0 | 100.0% | **PASS** |
| **End-to-End Multi-Engine (`end-to-end-validation`)** | 4 | 4 | 0 | 0 | 0 | 100.0% | **PASS** |
| **TOTAL** | **520** | **520** | **0** | **0** | **0** | **100.0%** | **PASS** |

---

## 2. Core Engine Detailed Class Breakdown

| Test Class | Tests Run | Failures | Errors | Time (s) |
|---|---|---|---|---|
| `org.embeddedjnosql.db.AdvancedQueryTest` | 14 | 0 | 0 | 0.052 s |
| `org.embeddedjnosql.db.AggregationPipelineTest` | 8 | 0 | 0 | 0.015 s |
| `org.embeddedjnosql.db.BTreeEngineTest` | 8 | 0 | 0 | 0.380 s |
| `org.embeddedjnosql.db.ColumnFamilyAdvancedTest` | 24 | 0 | 0 | 0.065 s |
| `org.embeddedjnosql.db.ColumnFamilyTest` | 7 | 0 | 0 | 0.009 s |
| `org.embeddedjnosql.db.ConcurrencyTest` | 8 | 0 | 0 | 0.412 s |
| `org.embeddedjnosql.db.deep.DeepColumnFamilyTest` | 25 | 0 | 0 | 0.048 s |
| `org.embeddedjnosql.db.deep.DeepDocumentTest` | 40 | 0 | 0 | 0.076 s |
| `org.embeddedjnosql.db.deep.DeepInfrastructureTest` | 45 | 0 | 0 | 0.220 s |
| `org.embeddedjnosql.db.deep.DeepKVTest` | 38 | 0 | 0 | 0.058 s |
| `org.embeddedjnosql.db.deep.DeepTransactionTest` | 43 | 0 | 0 | 0.193 s |
| `org.embeddedjnosql.db.DefectFixTest` | 7 | 0 | 0 | 0.739 s |
| `org.embeddedjnosql.db.DocumentCollectionTest` | 15 | 0 | 0 | 0.034 s |
| `org.embeddedjnosql.db.EventBusTest` | 9 | 0 | 0 | 0.016 s |
| `org.embeddedjnosql.db.FilePersistenceTest` | 5 | 0 | 0 | 0.110 s |
| `org.embeddedjnosql.db.FullFeatureTest` | 72 | 0 | 0 | 0.425 s |
| `org.embeddedjnosql.db.HashBucketTest` | 20 | 0 | 0 | 0.015 s |
| `org.embeddedjnosql.db.integration.FullIntegrationTest` | 7 | 0 | 0 | 0.393 s |
| `org.embeddedjnosql.db.EmbedJNoSQLServerTest` | 7 | 0 | 0 | 0.778 s |
| `org.embeddedjnosql.db.KeyValueBucketTest` | 9 | 0 | 0 | 0.006 s |
| `org.embeddedjnosql.db.ListBucketTest` | 14 | 0 | 0 | 0.009 s |
| `org.embeddedjnosql.db.LSMTreeEngineTest` | 8 | 0 | 0 | 0.688 s |
| `org.embeddedjnosql.db.SetBucketTest` | 17 | 0 | 0 | 0.012 s |
| `org.embeddedjnosql.db.TextSearchTest` | 7 | 0 | 0 | 0.016 s |
| `org.embeddedjnosql.db.TransactionTest` | 7 | 0 | 0 | 0.007 s |
| **Total Core Engine** | **489** | **0** | **0** | **4.279 s** |

---

## 3. Integration & Demo Suites Breakdown

| Test Suite | Class Name | Tests | Failures | Status |
|---|---|---|---|---|
| `spring-boot-starter` | `EmbedJNoSQLAutoConfigurationTest` | 12 | 0 | PASS |
| `spring-boot-demo` | `EcommerceApplicationTest` | 3 | 0 | PASS |
| `quarkus-demo` | `ProductResourceTest` | 4 | 0 | PASS |
| `micronaut-demo` | `EcommerceControllerTest` | 4 | 0 | PASS |
| `vertx-demo` | `EcommerceVerticleTest` | 4 | 0 | PASS |
| `end-to-end-validation`| `MultiEngineE2EValidationTest` | 4 | 0 | PASS |

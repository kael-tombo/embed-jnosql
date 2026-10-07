# Demonstration Validation Matrix

This matrix documents the real test execution results across all demonstration applications and frameworks in the EmbedJNoSQL ecosystem.

## Framework Execution Matrix

| Demo Application | Framework & Version | Integration Mechanism | Test Class | Tests Run | Failures | Errors | Status | Execution Time |
|---|---|---|---|---|---|---|---|---|
| **[annotation-showcase-demo](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/demo/annotation-showcase-demo)** | Jakarta NoSQL / JPA / Hibernate | Tri-standard annotations & SQL | `AnnotationShowcaseTest` | 5 | 0 | 0 | **PASS** | 0.68 s |
| **[spring-boot-demo](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/demo/spring-boot-demo)** | Spring Boot 3.2.5 | `embed-jnosql-spring-boot-starter` | `EcommerceApplicationTest` | 6 | 0 | 0 | **PASS** | 4.78 s |
| **[quarkus-demo](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/demo/quarkus-demo)** | Quarkus 3.8.0 | `embed-jnosql-quarkus-extension-runtime` | `ProductResourceTest` | 4 | 0 | 0 | **PASS** | 17.74 s |
| **[micronaut-demo](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/demo/micronaut-demo)** | Micronaut 4.2.0 | `embedjnosql-micronaut-integration` | `EcommerceControllerTest` | 4 | 0 | 0 | **PASS** | 3.59 s |
| **[vertx-demo](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/demo/vertx-demo)** | Eclipse Vert.x 4.5.4 | `embed-jnosql-core` (`executeBlocking`) | `EcommerceVerticleTest` | 4 | 0 | 0 | **PASS** | 2.00 s |
| **[end-to-end-validation](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/demo/end-to-end-validation)** | JUnit 5.10.2 | Native Multi-Engine Matrix | `MultiEngineE2EValidationTest` | 4 | 0 | 0 | **PASS** | 0.76 s |
| **[batch-processing-demo](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/demo/batch-processing-demo)** | EmbedJNoSQL Core — Document + KV + List | Atomic batch, chunked ingestion, fault injection | `BatchProcessingDemoTest` | 5 | 0 | 0 | **PASS** | — |
| **[advanced-queries-demo](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/demo/advanced-queries-demo)** | EmbedJNoSQL SQL + NoSQL Dual-Engine | SQL JOINs, aggregations, entity fluent API | `AdvancedQueriesDemoTest` | 5 | 0 | 0 | **PASS** | — |
| **[load-and-stress-demo](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/demo/load-and-stress-demo)** | EmbedJNoSQL Core — Concurrent load & charge | CountDownLatch burst, latency percentiles | `LoadAndChargeTest` | 6 | 0 | 0 | **PASS** | 2.927 s |

---

## Multi-Engine Capabilities Matrix

| Storage Engine | Documents (`products`/`orders`) | Key-Value (`price_cache`) | Wide-Column (`inventory`) | MVCC ACID Transactions | Restart Durability | Status |
|---|---|---|---|---|---|---|
| `IN_MEMORY` | Yes | Yes | Yes | Yes | N/A (RAM-only) | **VERIFIED** |
| `FILE` | Yes | Yes | Yes | Yes | Verified | **VERIFIED** |
| `B_TREE` | Yes | Yes | Yes | Yes | Verified | **VERIFIED** |
| `LSM_TREE` | Yes | Yes | Yes | Yes | Verified | **VERIFIED** |

---

## Load Testing Benchmarks (load-and-stress-demo)

Measured on JVM in-process with `IN_MEMORY` engine. All scenarios: **6/6 PASS, 0 failures, error rate = 0%**.

| Test ID | Scenario | Threads | Total Ops | Duration | p50 Latency | p95 Latency | p99 Latency | Throughput |
|---|---|---|---|---|---|---|---|---|
| LOAD-01 | Concurrent Writes | 10 | 1,000 | 464 ms | 0 ms | 1 ms | 305 ms | **2,155 ops/sec** |
| LOAD-02 | 50-Thread Safety | 50 | 2,500 | 168 ms | 0 ms | 1 ms | 1 ms | **14,881 ops/sec** |
| LOAD-03 | Read-After-Write | 8 | 400 | 51 ms | 0 ms | 1 ms | 32 ms | **7,843 ops/sec** |
| LOAD-04 | Mixed Engine (Doc+KV) | 12 | 1,200 | 22 ms | 0 ms | 1 ms | 1 ms | **54,545 ops/sec** |
| LOAD-05 | Saturation / Charge | 20 | 12,815 | 1,996 ms | 1 ms | 2 ms | 10 ms | **6,420 ops/sec** |
| LOAD-06 | MetricsReport Stats | — | — | — | — | — | — | **Unit verified** |

> **Read-after-write consistency:** 0 violations across 400 concurrent write+read pairs.  
> **Saturation:** 12,815 documents written in 2 seconds by 20 threads with 0 failures.

---

## Verification Evidence Log
- All demo sub-modules compile and execute using standard `mvn clean test` commands.
- Zero mock libraries: real disk files and actual in-process memory structures are allocated, asserted, and closed cleanly.
- Transaction abort scenarios verify complete isolation: inventory levels never corrupt upon rollback.
- Load testing validates thread-safety: 50 concurrent writers produce zero data corruption or orphaned records.


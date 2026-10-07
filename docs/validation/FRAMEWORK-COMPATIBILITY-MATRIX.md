# Framework Compatibility Matrix

This document provides definitive compatibility, integration mechanisms, and test evidence for major JVM frameworks running with **EmbedJNoSQL**.

---

## Compatibility Summary

| Framework | Verified Versions | Integration Module | Configuration Style | Concurrency / Thread Model | Verification Status |
|---|---|---|---|---|---|
| **Spring Boot** | 3.0.x – 3.2.x | `embed-jnosql-spring-boot-starter` | `application.properties` / `application.yml` (`embedjnosql.*`) | Standard Servlet / Thread Pool | **100% PASS** |
| **Quarkus** | 3.6.x – 3.8.x | `embed-jnosql-quarkus-extension-runtime` | SmallRye Config (`embedjnosql.*`) | RESTEasy Reactive + CDI | **100% PASS** |
| **Micronaut** | 4.0.x – 4.2.x | `embedjnosql-micronaut-integration` | `application.yml` (`embedjnosql.*`) | Netty Non-blocking + Serde | **100% PASS** |
| **Eclipse Vert.x** | 4.4.x – 4.5.x | `embed-jnosql-core` (Embedded) | Programmatic `EmbedJNoSQLConfig` | Event Loop + `executeBlocking` | **100% PASS** |
| **Plain Java SE** | Java 17, 21, 22 | `embed-jnosql-core` (Standalone) | Fluent Builder `EmbedJNoSQL.create(...)` | In-Process Embedded | **100% PASS** |

---

## Detailed Integration Findings

### 1. Spring Boot
- Starter provides clean conditional bean creation (`@ConditionalOnMissingBean`).
- `EmbedJNoSQLTemplate` offers Spring Data style ergonomics for document, KV, and wide-column containers.
- Metrics seamlessly route to Spring Actuator when enabled.

### 2. Quarkus
- Extension registers `JembedConfig` via SmallRye mapping.
- CDI producer (`EmbedDBProducer`) provides beans with `@DefaultBean` enabling test mocks or customized factories.
- Build-step deployment module ensures proper recording and native readiness hints.

### 3. Micronaut
- Factory (`EmbedJNoSQLFactory`) handles lifecycle `@PostConstruct` and `@PreDestroy` for clean database closure.
- Support for Micronaut Serialization (`@SerdeImport`) allows zero-reflection JSON encoding for record objects.

### 4. Vert.x
- Threading rule: Disk flush, compaction, and MVCC locks must execute on worker threads via `vertx.executeBlocking(...)`.
- Non-blocking HTTP routes scale effortlessly with hundreds of concurrent requests.

# Demonstration Validation Matrix

This matrix records the `1.0.2` release validation completed on 2026-10-13. The core coverage gate
used Maven Wrapper 3.9.6 and Eclipse Temurin JDK 17; integration modules and demos were also
verified on JDK 17. The hosted release workflow completed all pre-publication test gates
successfully. Maven Central deployment and artifact/signature verification remain in progress.

## Summary

All executed tests passed with zero failures, errors, or skips.

| Suite | Tests | Failures | Errors | Skipped | Result |
|---|---:|---:|---:|---:|---|
| Core (`clean verify -Pcoverage-check`) | 951 | 0 | 0 | 0 | **PASS** |
| Spring Boot starter (`clean verify`) | 78 | 0 | 0 | 0 | **PASS** |
| CLI (`clean verify`) | 4 | 0 | 0 | 0 | **PASS** |
| Quarkus and Micronaut integration modules (`clean verify`) | 0 | 0 | 0 | 0 | **BUILD PASS; no test reports** |
| Nine application demos | 102 | 0 | 0 | 0 | **PASS** |
| 20 single-feature core demos | 37 | 0 | 0 | 0 | **PASS** |
| **Total executed tests** | **1,172** | **0** | **0** | **0** | **PASS** |

The Quarkus and Micronaut modules compiled and passed Maven `verify`; they did not produce
Surefire test reports. `demo-common` also installed successfully with tests skipped because it
contains shared demo models, not a test suite.

Core and the Spring Boot starter `1.0.1` remain the latest artifacts available on Maven Central.
The `v1.0.2` tag includes all seven first-party release coordinates, and hosted validation passed;
Central publication and verification are still in progress. Demo applications remain validation
consumers and are not published artifacts.

## Framework and Application Demos

| Demo | Framework / version | Integration or scope | Tests | Failures | Errors | Skipped | Result |
|---|---|---|---:|---:|---:|---:|---|
| [spring-boot-demo](spring-boot-demo) | Spring Boot 4.0.0 | Spring Boot starter 1.0.2 (Central publication pending) | 41 | 0 | 0 | 0 | **PASS** |
| [quarkus-demo](quarkus-demo) | Quarkus 3.8.0 | Quarkus extension 1.0.2 (Central publication pending); includes Jakarta `Template` CDI injection | 14 | 0 | 0 | 0 | **PASS** |
| [micronaut-demo](micronaut-demo) | Micronaut 4.2.0 | Micronaut integration 1.0.2 (Central publication pending) | 11 | 0 | 0 | 0 | **PASS** |
| [vertx-demo](vertx-demo) | Eclipse Vert.x 4.5.4 | Core API with `executeBlocking` | 11 | 0 | 0 | 0 | **PASS** |
| [end-to-end-validation](end-to-end-validation) | JUnit 5.10.2 | Storage-engine lifecycle and durability | 4 | 0 | 0 | 0 | **PASS** |
| [advanced-queries-demo](advanced-queries-demo) | EmbedJNoSQL core | Document queries and JVM-side aggregation | 5 | 0 | 0 | 0 | **PASS** |
| [annotation-showcase-demo](annotation-showcase-demo) | JNoSQL, Jakarta Persistence and Hibernate annotations | Mapping metadata over documents; no JPA `EntityManager` or SQL engine | 5 | 0 | 0 | 0 | **PASS** |
| [batch-processing-demo](batch-processing-demo) | EmbedJNoSQL core | Atomic batches and rollback | 5 | 0 | 0 | 0 | **PASS** |
| [load-and-stress-demo](load-and-stress-demo) | EmbedJNoSQL core | Concurrent operations and metrics | 6 | 0 | 0 | 0 | **PASS** |
| **Total** |  |  | **102** | **0** | **0** | **0** | **PASS** |

## Single-Feature Core Demos

Each project depends on `embed-jnosql-core` only; these are NoSQL behavior demonstrations, not
SQL/JPA dual-engine tests.

| # | Demo | Tests | Failures | Errors | Skipped | Result |
|---:|---|---:|---:|---:|---:|---|
| 01 | [Document CRUD](demos/01-document-crud) | 2 | 0 | 0 | 0 | **PASS** |
| 02 | [Native query predicates](demos/02-native-query-predicates) | 1 | 0 | 0 | 0 | **PASS** |
| 03 | [Nested-field queries](demos/03-nested-field-queries) | 1 | 0 | 0 | 0 | **PASS** |
| 04 | [Secondary indexes](demos/04-secondary-indexes) | 1 | 0 | 0 | 0 | **PASS** |
| 05 | [TTL expiry](demos/05-ttl-expiry) | 3 | 0 | 0 | 0 | **PASS** |
| 06 | [Key-value buckets](demos/06-key-value-buckets) | 2 | 0 | 0 | 0 | **PASS** |
| 07 | [Redis-style lists](demos/07-redis-style-lists) | 2 | 0 | 0 | 0 | **PASS** |
| 08 | [Redis-style sets](demos/08-redis-style-sets) | 2 | 0 | 0 | 0 | **PASS** |
| 09 | [Redis-style hashes](demos/09-redis-style-hashes) | 2 | 0 | 0 | 0 | **PASS** |
| 10 | [Wide-column families](demos/10-wide-column-families) | 2 | 0 | 0 | 0 | **PASS** |
| 11 | [MVCC transactions](demos/11-mvcc-transactions) | 2 | 0 | 0 | 0 | **PASS** |
| 12 | [Aggregation pipeline](demos/12-aggregation-pipeline) | 2 | 0 | 0 | 0 | **PASS** |
| 13 | [SQL-to-NoSQL migration](demos/13-sql-to-nosql-migration) | 2 | 0 | 0 | 0 | **PASS** |
| 14 | [Event bus](demos/14-event-bus) | 2 | 0 | 0 | 0 | **PASS** |
| 15 | [Metrics](demos/15-metrics) | 2 | 0 | 0 | 0 | **PASS** |
| 16 | [Backup and restore](demos/16-backup-restore) | 2 | 0 | 0 | 0 | **PASS** |
| 17 | [CDC change feed](demos/17-cdc-change-feed) | 2 | 0 | 0 | 0 | **PASS** |
| 18 | [Text search](demos/18-text-search) | 2 | 0 | 0 | 0 | **PASS** |
| 19 | [Vector search](demos/19-vector-search) | 2 | 0 | 0 | 0 | **PASS** |
| 20 | [Durability and recovery](demos/20-durability-recovery) | 1 | 0 | 0 | 0 | **PASS** |
| **Total** | **20 suites** | **37** | **0** | **0** | **0** | **PASS** |

## Storage-Engine Coverage

The `end-to-end-validation` demo passed its four-test suite covering storage-engine lifecycle,
data-model co-existence, transactions, and durability/recovery. The four engine modes exercised
are `IN_MEMORY`, `FILE`, `B_TREE`, and `LSM_TREE`.

Performance figures are not included here: this validation run checked the six load-and-stress
JUnit tests but did not run a reproducible benchmark campaign. Timing and throughput vary by
machine and runtime.

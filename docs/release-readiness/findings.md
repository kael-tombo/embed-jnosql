# EmbedJNoSQL Audit Findings & Resolutions

**Audit Date**: 2026-10-12  
**Scope**: Product Vision, Website, Console UI/UX, Core Engine, Starters, Demos, Documentation  

---

## Findings Summary

| ID | Category | Description | Severity | Status | Resolution |
|---|---|---|---|---|---|
| **F-01** | Website | Install tabs in `docs/index.html` were citing stale `1.0.0` version coordinates instead of `1.0.1`. | Medium | **FIXED** | Updated all snippets (Maven, Gradle, Spring Boot, Quarkus, Micronaut) to `1.0.1`. |
| **F-02** | Website | Framework line omitted Eclipse JNoSQL despite native adapter support. | Low | **FIXED** | Added `🪐 Eclipse JNoSQL` badge and an explicit `EclipseDocumentTemplate` install tab. |
| **F-03** | Documentation | `CONTRIBUTING.md` stated outdated test count (865+) and referenced non-existent test class names. | Low | **FIXED** | Updated to `943 core tests` and referenced actual key test classes (`BrowserConsoleWorkflowVerificationTest`, etc.). |
| **F-04** | Documentation | `CHANGELOG.md` referenced `UIReliabilityNetworkIntegrationTest` instead of canonical demo/console suites. | Low | **FIXED** | Corrected test suite names to `DemoValidationSuiteTest` and `BrowserConsoleWorkflowVerificationTest`. |
| **F-05** | Console UI/UX | Console mutations bypassed MVCC transaction lifecycle if user expected UI writes to be transactional. | Medium | **MITIGATED & DOCUMENTED** | Added prominent warning badge on Transactions panel and status bar explaining that Console writes directly to engine. |
| **F-06** | Engine Durability | Misleading claims of "power-loss" survival in earlier docs when only graceful restart and kill-after-flush were tested. | High | **FIXED** | Explicitly scoped durability to process-kill recovery and graceful restart; physical power-loss claims retracted. |
| **F-07** | Packaging | Ambiguity between shaded core JAR and uncompressed runtime dependencies footprint (<5 MB). | Low | **CLARIFIED** | Confirmed core shaded JAR is **3.11 MB**; unshaded runtime has 3 dependencies (`jackson-databind`, `jackson-datatype-jsr310`, `slf4j-api`). |
| **F-08** | Console Safety | Destructive operations (drop all docs, cleanup) needed explicit confirmation to prevent data loss. | High | **VERIFIED** | Confirmation modal requires user approval, names target collection, and states irreversible outcome before request. |

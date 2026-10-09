# Demo Validation — Execution Guide

## Quick Start

```powershell
# 1. Install core (from repository root)
mvn install -DskipTests

# 2. Install demo-common
mvn install -f demo/demo-common/pom.xml

# 3. Run new demo validation suite (63 tests)
mvn test -Dtest=DemoValidationSuiteTest -pl .

# 4. Run all 20 single-feature demos
Get-ChildItem demo/demos -Directory | ForEach-Object { mvn -B test -f "$($_.FullName)/pom.xml" }

# 5. Run all framework integration demos
mvn test -f demo/spring-boot-demo/pom.xml
mvn test -f demo/quarkus-demo/pom.xml
mvn test -f demo/micronaut-demo/pom.xml
mvn test -f demo/vertx-demo/pom.xml
mvn test -f demo/end-to-end-validation/pom.xml
mvn test -f demo/annotation-showcase-demo/pom.xml
```

## Group Commands

### Smoke (fast, in-memory only)
```powershell
mvn test -Dtest="DemoValidationSuiteTest#*Catalog*+*Employee*+*Cache*+*Edge*" -pl .
```

### Persistence scenarios (file-backed, @TempDir)
```powershell
mvn test -Dtest="DemoValidationSuiteTest\$PersistenceScenario+DemoValidationSuiteTest\$CheckpointStorageScenario" -pl .
```

### Transaction correctness
```powershell
mvn test -Dtest="DemoValidationSuiteTest\$TransactionCorrectnessScenario+DemoValidationSuiteTest\$InventoryScenario" -pl .
```

### Full suite
```powershell
mvn test -pl .
```

## Understanding Test Output

Each nested class maps to a scenario group:
- `ProductCatalogScenario` → SC-DOC-A
- `EmployeeDirectoryScenario` → SC-DOC-B
- `AppConfigurationScenario` → SC-DOC-C
- `AuditRecordsScenario` → SC-DOC-D
- `InventoryScenario` → SC-DOC-E
- `SessionStorageScenario` → SC-KV-A
- `LocalCacheScenario` → SC-KV-B
- `CheckpointStorageScenario` → SC-KV-C
- `IdempotencyScenario` → SC-KV-D
- `EdgeCaseScenario` → SC-EDGE
- `PersistenceScenario` → SC-PERSIST
- `TransactionCorrectnessScenario` → SC-TX

## Expected Output (all passing)

```
[INFO] Tests run: 63, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

## Failure Diagnosis

If any test fails:
1. Check the `@DisplayName` for the exact scenario ID (e.g., `SC-DOC-A-04`).
2. Refer to `docs/demo-validation/scenario-matrix.md` for the expected outcome.
3. Refer to `docs/demo-validation/findings.md` for known defects.
4. Run with `-e` for full stack traces: `mvn test -e -Dtest=DemoValidationSuiteTest -pl .`

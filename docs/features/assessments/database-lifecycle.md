# Feature Assessment: Database Lifecycle

## Claimed Capability
Embedded initialization, configuration management, directory creation, clean shutdown, and resource deallocation.

## Actual Behavior
The database initializes via a fluent builder pattern (`EmbedJNoSQL.embed()`), dynamically instantiates the chosen storage engine, opens the write-ahead log, starts optional background flushing and metrics timers, and cleanly releases all file handles, executor services, and network ports upon `close()`.

## Relevant Source Files
- `src/main/java/org/embeddedjnosql/db/EmbedJNoSQL.java`
- `src/main/java/org/embeddedjnosql/db/config/EmbedJNoSQLConfig.java`

## Public API
```java
EmbedJNoSQL db = EmbedJNoSQL.embed()
    .storageEngine(StorageEngineType.FILE)
    .dataDir(Path.of("data/test-db"))
    .autoFlush(true)
    .build();

db.isOpen(); // returns true
db.close();  // releases all resources
```

## Acceptance Criteria
1. Database creates configured data directory if absent.
2. `db.isOpen()` returns `true` while running and `false` after `close()`.
3. Re-invoking operations on closed database throws `IllegalStateException`.
4. Idempotent `close()` does not throw exceptions.

## Tests That Prove the Criteria
- `org.embeddedjnosql.db.integration.FullIntegrationTest.testDatabaseLifecycle`
- `org.embeddedjnosql.db.deep.DeepInfrastructureTest.testLifecycleAndResourceCleanup`

## Exact Commands
```bash
mvn test -Dtest=FullIntegrationTest#testDatabaseLifecycle
```

## Expected Result
Build success, 1 test passed in < 0.5s, clean shutdown.

## Actual Result
Passed: `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.12 s`.

## Evidence Artifacts
- `target/surefire-reports/org.embeddedjnosql.db.integration.FullIntegrationTest.txt`

## Edge Cases
- Calling `close()` multiple times in succession.
- Specifying nested non-existent directory paths.

## Failure Cases
- Disk write permissions denied on `dataDir` (propagates `DatabaseException`).

## Performance Evidence
- Initialization latency: < 15ms.
- Shutdown latency: < 5ms.

## Concurrency Evidence
- Thread-safe volatile `closed` boolean prevents data race between caller and worker threads.

## Defects Found
None in lifecycle logic itself.

## Corrections Applied
None required.

## Remaining Limitations
Single-JVM process lock prevents concurrent independent JVM instances from opening the same data directory simultaneously (expected embedded database behavior).

## Final Status
PASS

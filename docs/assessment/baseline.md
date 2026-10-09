# System Baseline and Environment Report

## 1. Git State and Repository Provenance

- **Initial Branch**: `main`
- **Initial Head Commit**: `78b2b9c Wire the query result cache into document collections`
- **Preserved Working Tree Checkpoint Commit**: `5b2977b chore: checkpoint pre-existing work and extract login.js for CSP compliance`
- **Dedicated Improvement Branch**: `feature/rigorous-assessment-and-improvements`
- **Current Commit**: `78d6adc test(evidence): update network traces with tightened CSP`
- **Working Tree Cleanliness**: Verified clean (`git status` reports nothing to commit).

## 2. Execution Environment & Tooling

| Component | Value | Verification Command |
|---|---|---|
| Operating System | Microsoft Windows Server 2022 Datacenter (amd64, 10.0.20348) | `mvn -version` |
| JVM Vendor & Version | Eclipse Adoptium Temurin 25.0.4.1 (build 25.0.4.1+1-LTS) | `mvn -version` |
| Java Language Level | Java 17 bytecode compatibility (`release 17` in `pom.xml`) | `mvn help:effective-pom` |
| Build Tool | Apache Maven 3.9.15 | `mvn -version` |
| Default Encoding | UTF-8 (`project.build.sourceEncoding=UTF-8`) | `pom.xml` |

## 3. Test Suite Baseline Execution

The full test suite was executed against the clean repository state via Maven:

```powershell
mvn test
```

### Results Summary
- **Total Tests Executed**: 857
- **Failures**: 0
- **Errors**: 0
- **Skipped**: 0
- **Total Execution Time**: 2 minutes 24 seconds
- **JaCoCo Classes Analyzed**: 131 classes in bundle `EmbedJNoSQL NoSQL`

### Runtime Warnings and Diagnostic Anomalies Observed
During the execution of `BTreeAutoFlushPersistenceTest` and `WalRotationAndCheckpointTest`, the following diagnostic errors were logged:
```
WAL truncate on checkpoint failed (C:\Users\JRATOM~1\AppData\Local\Temp\2\junit17648186345586542048\.wal\wal.log: The process cannot access the file because it is being used by another process); appending a CHECKPOINT marker instead
WAL checkpoint failed: Stream closed
```
**Diagnostic Analysis**:
1. `WriteAheadLog.java` holds two distinct open file descriptors on the same physical file (`FileWriter` via `BufferedWriter` and a separate `FileOutputStream`).
2. When `truncate()` attempts `Files.deleteIfExists(walFile)`, Windows file-locking semantics reject file deletion while any handle or OS file system filter holds an open lock.
3. The resulting `FileSystemException` causes `truncate()` to abort before calling `initWriter()`.
4. The fallback in `checkpoint()` then tries `logWriter.write("CHECKPOINT:...")`, but `logWriter` was closed prior to the attempted delete. This causes an unhandled `IOException: Stream closed` to escape out of `checkpoint()`.
5. This is cataloged as critical finding **F-01**.

## 4. Minimal Workflow Baseline Verification

### A. Minimal Document Workflow
- Exercised via `org.embeddedjnosql.db.DocumentCollectionTest`:
  - Creation of collection `users`
  - Document insertion with generated UUID: `col.insert(Document.of("name", "Alice"))`
  - Document retrieval: `col.findById(id)`
  - Predicate query evaluation: `col.find(Query.eq("name", "Alice"))`
  - Document update and replacement: `col.update(...)`
  - Document deletion: `col.deleteById(id)`
- **Result**: Passed (15 tests, 0 failures, 0.928s).

### B. Minimal Key-Value Workflow
- Exercised via `org.embeddedjnosql.db.KeyValueBucketTest`:
  - Bucket creation: `db.keyValueBucket("cache")`
  - KV put and get: `bucket.put("key1", "val1")`
  - Expiry with TTL: `bucket.put("session", "abc", Duration.ofSeconds(10))`
  - Atomic increment/decrement: `bucket.increment("counter")`
  - Batch operations: `bucket.putAll(map)`
- **Result**: Passed (9 tests, 0 failures, 0.650s).

### C. File-Backed Persistence Across Restart
- Exercised via `org.embeddedjnosql.db.FilePersistenceTest` & `EngineRestartDiscoveryTest`:
  - Data directory initialized with `StorageEngineType.FILE`
  - Insert documents and commit flush
  - Close database instance cleanly: `db.close()`
  - Reopen new database pointing to same directory: `EmbedJNoSQL.embed().dataDir(dir).build()`
  - Read back records: documents and KV entries recovered successfully
- **Result**: Passed.

## 5. Artifact Size & Distribution Measurements

The project has an architectural goal of maintaining a lightweight footprint (<5 MB total).
The build was packaged via `mvn package -DskipTests` to produce the distributable artifacts:

| Artifact | Measured Size (Bytes) | Size (MB) | Contents & Scope |
|---|---|---|---|
| `original-embed-jnosql-core-1.0.0.jar` | 584,950 | **0.56 MB** | Core engine classes, models, SPI, console handlers, static assets |
| `embed-jnosql-core-1.0.0-shaded.jar` | 3,083,631 | **2.94 MB** | Shaded uber-JAR with runtime dependencies (`jackson-databind`, `jackson-datatype-jsr310`, `slf4j-api`) |
| Embedded Static Web Console Assets | 223,083 | **0.21 MB** | `index.html`, `login.html`, `console.js`, `login.js`, `console.css`, SVGs |

**Evaluation**: The full shaded distribution measures **2.94 MB**, which is **41.2% below** the maximum 5.0 MB target limit.

## 6. Capability Classification Matrix

| Subsystem / Capability | Classification | Verification Status & Notes |
|---|---|---|
| Document Model CRUD | Implemented & Verified | Full test coverage in `DocumentCollectionTest` |
| Document Nested Paths (`a.b.c`) | Implemented & Verified | Dotted path traversal across maps and lists |
| Document Secondary Indexes | Implemented & Partially Verified | Secondary index lookup, range; numeric type casting issue identified (**F-04**) |
| Document Query Result Cache | Implemented & Verified | Wired into `DocumentCollection`, invalidated on writes |
| Key-Value Bucket CRUD | Implemented & Verified | Verified in `KeyValueBucketTest` |
| Key-Value TTL Expiration | Implemented with Defect | Overwriting without TTL fails to clear prior expiration (**F-03**) |
| Redis Data Structures (Lists, Sets, Hashes) | Implemented & Verified | Covered in `ListBucketTest`, `SetBucketTest`, `HashBucketTest` |
| Wide-Column Family | Implemented & Verified | Row-key + column + value model with TTL and range scans |
| Vector Index (HNSW) | Implemented (Experimental) | Cosine / Euclidean similarity with persistence in JSON |
| MVCC Transaction Engine | Implemented with Defect | `TransactionalCollection` fails to isolate updates/reads (**F-02**) |
| Storage SPI: InMemory | Implemented & Verified | Zero file I/O, volatile storage |
| Storage SPI: FileEngine | Implemented & Verified | JSON snapshot per collection with WAL recovery |
| Storage SPI: BTreeEngine | Implemented & Verified | Binary stream index with WAL and periodic background flush |
| Storage SPI: LSMTreeEngine | Implemented & Verified | Memtable + SSTable files + Bloom filter + WAL |
| Write-Ahead Log (WAL) | Implemented with Defect | Windows handle conflict & Stream closed error on checkpoint (**F-01**) |
| Web Console Server | Implemented & Verified | Built on Sun HttpServer, handles REST endpoints & static files |
| Web Console Security (CSP & Auth) | Implemented & Verified | Token session, CSRF enforcement, script-src 'self' without unsafe-inline |
| Relational / SQL Engine | **Obsolete & Removed** | Verified 404 on `/api/sql`, `/api/tables`, `/api/constraints` |

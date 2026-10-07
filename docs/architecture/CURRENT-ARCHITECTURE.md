# JNOSQL-EMBED: Current Architecture Documentation

## Physical Codebase Layout

The current repository is organized into a core multi-engine library and framework adapters:

```
JNoSQL-EMBED/
├── pom.xml                                  (Root build file for embed-jnosql-core)
├── src/main/java/org/embeddedjnosql/db/
│   ├── EmbedJNoSQL.java                        (Main engine facade)
│   ├── config/                              (EmbedJNoSQLConfig builder & options)
│   ├── console/http/                        (Embedded HTTP server & REST console)
│   ├── core/
│   │   ├── cdc/                             (Change Data Capture engine)
│   │   ├── event/                           (In-process EventBus)
│   │   ├── metrics/                         (Database metrics counters & JFR)
│   │   ├── record/                          (UnifiedRecord interface & metadata)
│   │   └── util/                            (JsonSerde & ChecksumUtil)
│   ├── index/                               (SecondaryIndex, B-Tree, HNSW vector stub)
│   ├── nosql/
│   │   ├── column/                          (ColumnFamily)
│   │   ├── document/                        (Document, DocumentCollection, Query)
│   │   └── kv/                              (KeyValueBucket, ListBucket, SetBucket, HashBucket)
│   ├── storage/spi/                         (StorageEngine, InMemory, File, LSM, BTree)
│   └── transaction/mvcc/                    (Transaction, MVCCManager)
├── spring-boot-starter/                     (Spring Boot 3.x Starter)
│   ├── pom.xml
│   └── src/main/java/org/embeddedjnosql/db/spring/boot/
├── quarkus-extension/                       (Quarkus 3.x Extension)
│   ├── pom.xml
│   ├── runtime/                             (Runtime module: JembedConfig, Producer, Recorder)
│   └── deployment/                          (Deployment module: JembedExtensionProcessor)
└── micronaut-integration/                   (Micronaut 4.x Integration)
    ├── pom.xml
    └── src/main/java/org/embeddedjnosql/db/micronaut/
```

---

## Core Component Interactions

```mermaid
sequenceDiagram
    participant App as Application Code
    participant DB as EmbedJNoSQL
    participant DC as DocumentCollection
    participant Idx as SecondaryIndex
    participant Eng as StorageEngine
    participant Evt as EventBus

    App->>DB: documentCollection("users")
    DB->>DC: computeIfAbsent("users")
    DB-->>App: DocumentCollection instance

    App->>DC: insert(doc)
    DC->>Evt: emit(BEFORE_INSERT, doc)
    DC->>Eng: putRecord("users", doc)
    DC->>Idx: add(doc)
    DC->>Evt: emit(AFTER_INSERT, doc)
    DC-->>App: inserted Document (with generated ID)

    App->>DC: find(Query.eq("status", "ACTIVE"))
    DC->>Idx: lookup("status", "ACTIVE")
    Idx-->>DC: Matching Document IDs
    DC->>Eng: get("users", id)
    DC-->>App: List<Document>
```

---

## Subsystem Lifecycle

1. **Instantiation**: `EmbedJNoSQL db = EmbedJNoSQL.embed().storageEngine(type).build()`. The `StorageEngine` is instantiated based on configuration (`IN_MEMORY`, `FILE`, `B_TREE`, `LSM_TREE`).
2. **Execution**: Multiple concurrent threads access collections and buckets without external locking. Concurrent hash tables coordinate namespace isolation.
3. **Flushing & Persistence**: If `autoFlush` is active, background thread pools flush mutations to disk periodically (default 1000ms).
4. **Shutdown**: `db.close()` invokes `engine.flush()`, closes file channels, stops background timers, and shuts down any active HTTP management server.

# JNOSQL-EMBED: Target Architecture Blueprint

## Architectural North Star

The target architecture of JNOSQL-EMBED refines the multi-model core into a clean, modular, high-performance engine with clear API/SPI separation, enterprise-grade framework integration, and robust storage resilience.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        Framework Adapters                              │
│   spring-boot-starter  │  quarkus-extension  │  micronaut-integration  │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│                          Public API Layer                              │
│  EmbedJNoSQL | DocumentCollection | KeyValueBucket | ColumnFamily | Query │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│                         Core Engine Kernel                             │
│   - Unified Record Metadata & Serde (JSON / Zero-Copy ByteBuf)        │
│   - Multi-Version Concurrency Control (MVCC) & Snapshot Isolation      │
│   - Secondary Index Manager & Predicate Query Optimizer                │
│   - In-Process EventBus, Metrics & CDC Change Log Pipeline             │
└──────────────────────────────────┬─────────────────────────────────────┘
                                   │
┌──────────────────────────────────▼─────────────────────────────────────┐
│                       StorageEngine SPI Layer                          │
└──────────────┬───────────────────┼───────────────────┬─────────────────┘
               │                   │                   │
      ┌────────▼────────┐ ┌────────▼────────┐ ┌────────▼────────┐
      │ InMemoryEngine  │ │   FileEngine    │ │   BTreeEngine   │
      │(ConcurrentMap)  │ │ (WAL + Journal) │ │  (Page Files)   │
      └─────────────────┘ └─────────────────┘ └─────────────────┘
```

---

## Key Target Architectural Principles

### 1. Pure API / SPI Decoupling
- **API (`org.embeddedjnosql.db.*`, `org.embeddedjnosql.db.nosql.*`)**: Only clean interfaces, fluent builders, and immutable records visible to consumers.
- **SPI (`org.embeddedjnosql.db.storage.spi.*`)**: Storage engine interfaces allowing third-party or custom persistence backends to be plugged in seamlessly.
- **Internal (`org.embeddedjnosql.db.core.*`)**: Internal plumbing (serialization, caching, metrics) shielded from public consumer code.

### 2. Unified Record Abstraction
Every stored object adheres to `UnifiedRecord` with `RecordMetadata` tracking:
- Unique entity ID.
- Entity type / namespace.
- Creation timestamp and optional TTL expiration.
- Payload representation (JSON or zero-copy raw byte array).

### 3. Unified Transaction Coordination
- Transactions span across all collections and key-value buckets within the database instance.
- Reads adhere to Snapshot Isolation based on logical commit timestamps.
- Atomic commit guarantees all staged operations write together or none do.

### 4. Enterprise Framework Alignment
- Single configuration namespace (`embedjnosql.*`) across Spring Boot (`application.yml`), Quarkus (`application.properties`), and Micronaut (`application.yml`).
- Automatic lifecycle registration (closing database cleanly on Spring context shutdown, Quarkus shutdown event, or Micronaut stop).

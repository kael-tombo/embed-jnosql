# JNOSQL-EMBED: Feature Dependencies Graph

This document details the inter-dependencies between subsystems and features within JNOSQL-EMBED.

---

## Subsystem Dependency Hierarchy

```mermaid
graph TD
    A[Public Application Layer] --> B[EmbedJNoSQL Facade]
    
    B --> C[DocumentCollection]
    B --> D[KeyValueBucket]
    B --> E[List / Set / Hash Buckets]
    B --> F[ColumnFamily]
    B --> G[Transaction / MVCC]
    
    C --> H[JsonSerde Serialization]
    C --> I[SecondaryIndex Manager]
    C --> J[Query Predicate Evaluator]
    
    C --> K[StorageEngine SPI]
    D --> K
    E --> K
    F --> K
    G --> K
    
    K --> L[InMemoryEngine]
    K --> M[FileEngine + WALManager]
    K --> N[BTreeEngine]
    K --> O[LSMTreeEngine]
    
    C --> P[EventBus & Metrics]
    D --> P
    E --> P
    G --> P
```

---

## Critical Invariants

1. **StorageEngine Independence**: The high-level data abstractions (`DocumentCollection`, `KeyValueBucket`, `ListBucket`, etc.) depend strictly on the `StorageEngine` interface, never on a concrete engine class. Any new `StorageEngine` automatically supports all data models.
2. **Serialization Layer**: `JsonSerde` depends on Jackson Databind, but is encapsulated internally. High-level client code interacts with `Document` records and Java maps/primitives.
3. **Transaction Buffering**: `Transaction` wraps `StorageEngine` and provides an uncommitted write layer before persisting mutations.

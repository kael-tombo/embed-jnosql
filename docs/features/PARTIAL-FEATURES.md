# JNOSQL-EMBED: Partially Implemented Features

This document tracks features where the core architecture exists, but full feature parity or advanced edge-case coverage remains in progress.

---

## 1. Change Data Capture (CDC) Connectors

### Current Status: `PARTIALLY_IMPLEMENTED`
- **What Works**:
  - `CDCEvent` record with `eventId`, `eventType` (`INSERT`, `UPDATE`, `DELETE`), `collection`, `key`, `previousValue`, `newValue`, and `timestamp`.
  - `CDCProcessor` with subscriber event log and pub/sub routing.
  - `FileCDCConnector` append-only JSON file sink.
  - `KafkaCDCConnector` stub for streaming change events to an external Kafka cluster.
- **What Remains**:
  - CDC events are currently emitted explicitly via `CDCManager` rather than hooked into `StorageEngine` write triggers automatically across all operations.
  - Resumable CDC cursor positions across engine restart.

---

## 2. Aggregation Pipeline Operators

### Current Status: `PARTIALLY_IMPLEMENTED`
- **What Works**:
  - `DocumentCollection.aggregate()` supports basic pipelines: `$match`, `$group`, `$count`, `$sum`, `$avg`.
- **What Remains**:
  - Unwinding array fields (`$unwind`).
  - Lookup across collections (`$lookup`).
  - Composite multi-stage projection calculations.

---

## 3. Vector Similarity Search (HNSW)

### Current Status: `EXPERIMENTAL`
- **What Works**:
  - `HNSWVectorIndex` class exists in `org.embeddedjnosql.db.index`.
  - Supports Euclidean distance and cosine similarity vector scoring in-memory.
- **What Remains**:
  - On-disk quantization and vector index persistence.
  - Multi-threaded graph construction optimizations for embeddings > 1536 dimensions.

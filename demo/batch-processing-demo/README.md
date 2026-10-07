# EmbedJNoSQL Demo: High-Throughput Batch Processing

This demonstration project validates high-volume batch ingestion, atomic all-or-nothing transactions, chunked streaming, and bulk Key-Value operations in EmbedJNoSQL.

## Features Demonstrated

1. **Atomic Batch Ingestion**: `collection.insertAll(docs, true)` inserting thousands of records in a single transactional unit.
2. **Fault Injection & Atomic Rollbacks**: Proves that an unexpected runtime failure rolls back 100% of previously inserted records in the batch, leaving zero orphaned data.
3. **Chunked Processing**: Ingestion of massive datasets in configurable chunks (e.g. 500 items/chunk) to optimize memory and transaction boundaries.
4. **Bulk Key-Value with TTL**: Fast key-value bulk loading with automated expiration.
5. **Redis-Style Batch Queueing**: Batch pushing (`rpush`) into distributed FIFO queues.

## Running the Demo

```bash
# Run tests
mvn test

# Run interactive CLI
mvn compile exec:java -Dexec.mainClass="org.embeddedjnosql.db.demo.batch.BatchDemoApplication"
```

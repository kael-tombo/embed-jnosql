# JNOSQL-EMBED: Fully Implemented Features

This document provides in-depth technical documentation and concrete code examples for all features with status `IMPLEMENTED`.

---

## 1. Document Collection & Query Engine
- **Classes**: `org.embeddedjnosql.db.nosql.document.DocumentCollection`, `Document`, `Query`
- **Behavior**: Provides schema-free document persistence serialized as JSON. Supports secondary index acceleration on equality and range fields, sort order (`ASC`, `DESC`), offset pagination, and count statistics.
- **Example**:
  ```java
  EmbedJNoSQL db = EmbedJNoSQL.embed().build();
  DocumentCollection users = db.documentCollection("users");
  users.createIndex("email");

  Document doc = new Document()
      .id("usr-1")
      .add("name", "Alice")
      .add("email", "alice@example.com")
      .add("score", 95);
  users.insert(doc);

  List<Document> highScorers = users.find(Query.gt("score", 90));
  ```

---

## 2. Key-Value & Redis-Style Buckets
- **Classes**: `KeyValueBucket`, `ListBucket`, `SetBucket`, `HashBucket`
- **Behavior**:
  - `KeyValueBucket`: Basic string key-value storage with optional TTL expiration, atomic `increment(key, delta)`, and batch `putAll(Map)`.
  - `ListBucket`: Doubly-linked list abstraction supporting `lpush`, `rpush`, `lpop`, `rpop`, `lrange`.
  - `SetBucket`: Unordered set of unique strings supporting `sadd`, `srem`, `smembers`, `sismember`.
  - `HashBucket`: Map of field-value pairs supporting `hset`, `hget`, `hgetall`, `hdel`.
- **Example**:
  ```java
  ListBucket queue = db.listBucket("jobs");
  queue.rpush("incoming", "job-101", "job-102");
  queue.lpush("incoming", "priority-job-000");
  String nextJob = queue.lpop("incoming"); // priority-job-000
  ```

---

## 3. Wide-Column Family Store
- **Classes**: `org.embeddedjnosql.db.nosql.column.ColumnFamily`
- **Behavior**: Multi-dimensional sparse table supporting row keys, column names, cell values, and optional column-level TTLs.
- **Example**:
  ```java
  ColumnFamily telemetry = db.columnFamily("sensors");
  telemetry.put("device-1", "temp", 24.5, 3600); // expires in 1 hour
  Object temp = telemetry.get("device-1", "temp");
  Map<String, Object> allCols = telemetry.getRow("device-1");
  ```

---

## 4. MVCC & ACID Transactions
- **Classes**: `org.embeddedjnosql.db.transaction.mvcc.Transaction`, `MVCCManager`
- **Behavior**: Staged write buffering with snapshot isolation. Reads uncommitted writes within the same transaction (`Read-Your-Own-Writes`), atomic `commit()` to storage, or clean `rollback()`.
- **Example**:
  ```java
  try (Transaction tx = db.beginTransaction()) {
      tx.write("ledger", "tx-1", "{\"amount\": 100}");
      tx.write("balances", "acc-1", "{\"balance\": 900}");
      tx.commit();
  }
  ```

---

## 5. Multi-Engine Persistence Substrates
- **Classes**: `InMemoryEngine`, `FileEngine`, `BTreeEngine`, `LSMTreeEngine`
- **Behavior**:
  - `IN_MEMORY`: Pure RAM `ConcurrentHashMap` with zero I/O latency.
  - `FILE`: Append-only Write-Ahead Log (WAL) with automatic recovery.
  - `B_TREE`: On-disk block page storage with $O(\log N)$ lookups.
  - `LSM_TREE`: MemTable + SSTables with background compaction.

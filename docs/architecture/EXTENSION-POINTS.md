# JNOSQL-EMBED: Extension Points & Customization SPIs

This document catalogs all available extension points allowing users and library authors to customize, extend, or monitor JNOSQL-EMBED.

---

## 1. Custom Storage Engine SPI (`StorageEngine.java`)

Developers can implement custom storage backends (e.g. distributed S3-backed cache, off-heap shared memory, or encrypted disk storage) by implementing `StorageEngine`:

```java
package org.embeddedjnosql.db.storage.spi;

public interface StorageEngine extends AutoCloseable {
    String name();
    void put(String collection, String key, String value);
    String get(String collection, String key);
    void delete(String collection, String key);
    List<String> scan(String collection);
    Set<String> keys(String collection);
    void flush();
    void close();
    int size();
    Map<String, Object> stats();
}
```

---

## 2. Real-Time In-Process EventBus (`EventBus.java`)

Applications can attach reactive observers to database lifecycle mutations:

```java
EmbedJNoSQL db = EmbedJNoSQL.embed().build();

// Subscribe to mutation events
db.eventBus().on(EventBus.EventType.AFTER_INSERT, event -> {
    System.out.println("Document inserted in " + event.collection() + ": " + event.data());
});

db.eventBus().on(EventBus.EventType.AFTER_COMMIT, event -> {
    System.out.println("Transaction committed successfully.");
});
```

### Supported Event Types:
- `BEFORE_INSERT`, `AFTER_INSERT`
- `BEFORE_UPDATE`, `AFTER_UPDATE`
- `BEFORE_DELETE`, `AFTER_DELETE`
- `BEFORE_COMMIT`, `AFTER_COMMIT`
- `BEFORE_ROLLBACK`, `AFTER_ROLLBACK`
- `COLLECTION_CREATED`, `BUCKET_CREATED`

---

## 3. Change Data Capture (CDC) Connectors (`CDCManager.java`)

JNOSQL-EMBED includes a streaming CDC subsystem allowing change events (`INSERT`, `UPDATE`, `DELETE`) to be streamed out of process:
- **File Connector**: Writes an append-only JSON stream of change events to a directory on disk.
- **Kafka Connector**: Ships change events asynchronously to an Apache Kafka topic.
- **Custom CDC Consumer**: Attach a Java `Consumer<CDCEvent>` to `db.cdcManager().processor().subscribe(...)`.

---

## 4. Framework Producers & CDI Overrides

In Spring Boot, Quarkus, and Micronaut, all database beans are marked as conditional/default:
- In Spring: `@ConditionalOnMissingBean(EmbedJNoSQL.class)` allows applications to supply their own customized `EmbedJNoSQL` `@Bean`.
- In Quarkus: All producers in `EmbedDBProducer` use `@DefaultBean`, allowing applications to override `EmbedJNoSQL`, `DocumentCollection`, or `KeyValueBucket` via standard `@Produces` methods.
- In Micronaut: Custom `@Replaces(EmbedJNoSQLFactory.class)` beans can completely customize database construction.

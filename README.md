<div align="center">

<img src="docs/assets/embedjnosql-mark-512.png" alt="EmbedJNoSQL — the embedded NoSQL database for Java" width="220" />

**The embedded NoSQL database for the JVM — Document and Key-Value.**  
One JAR, zero infrastructure. No server, no Docker, no daemon, no network.

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen.svg)](demo/spring-boot-demo)
[![Quarkus](https://img.shields.io/badge/Quarkus-3.8-purple.svg)](demo/quarkus-demo)
[![Micronaut](https://img.shields.io/badge/Micronaut-4.2-red.svg)](demo/micronaut-demo)
[![Vert.x](https://img.shields.io/badge/Vert.x-4.5-blue.svg)](demo/vertx-demo)

</div>

---

## Why EmbedJNoSQL?

> **"EmbedJNoSQL is to Document and Key-Value stores what H2 is to Relational databases."**

Java developers carry a hidden tax on every project: before writing a single line of business
logic they must provision infrastructure — Docker containers, Redis daemons, MongoDB processes.
Even for a unit test. Even for a local prototype.

**EmbedJNoSQL removes that tax.** Embed a NoSQL database directly inside your JVM process. Call one
line of code. Write your business logic. Ship.

```java
// Everything you need. Nothing you don't.
try (var db = EmbedJNoSQL.inMemory()) {
    db.documentCollection("users").insert(Document.of(user.toMap()).id(user.id()));
    db.keyValueBucket("sessions").put("tok-1", "active");
    db.setBucket("permissions").sadd("admin", "write", "delete");
}
```

---

## What this product is (and is not)

EmbedJNoSQL is **NoSQL only**: Document and Key-Value are data models of one product, and
in-memory vs file-backed is a *storage mode*, not an engine choice. There is no relational
database, no SQL engine, no dual-engine selector, and no JDBC driver.

| | **EmbedJNoSQL** | H2 | MongoDB / Redis |
|---|---|---|---|
| **Primary model** | Document + Key-Value (+ wide-column, experimental vector) | Relational SQL | Single-model daemons |
| **Deployment** | Embedded, in-process | Embedded | External server |
| **Pure Java** | ✅ | ✅ | ❌ |
| **Server required** | ❌ (optional local console) | ❌ | ✅ |
| **Document queries** | ✅ Native | ⚠️ JSON functions | ✅ |
| **Redis-style structures** | ✅ Lists, sets, hashes, TTL | ❌ | ✅ (Redis) |
| **SQL** | ❌ intentionally removed | ✅ | ❌ |
| **Persistence** | In-memory, file snapshots + WAL, B-Tree, LSM tree | Page store | WiredTiger / RDB |

H2 appears here only as the reference point for *embedded simplicity*. EmbedJNoSQL's scope is
NoSQL: it does not implement SQL, and does not aim to.

---

## Installation

> **Not on Maven Central (yet).** EmbedJNoSQL is distributed from **GitHub Releases**; no artifact is
> published to a public repository, so the coordinates below do **not** resolve on their own.
> Install locally first — then they work exactly as written.

```bash
git clone <repository-url> && cd JNoSQL-EMBED
./mvnw -DskipTests install     # installs embed-jnosql-core and the starters into your local repo
```

Alternatively, download the shaded jar from the GitHub Release and put it on your classpath.

### Core (Java 17+, three runtime dependencies)

Maven pulls these in automatically; they are the only mandatory runtime dependencies:
`jackson-databind`, `jackson-datatype-jsr310`, and `slf4j-api`. Everything else — Spring,
Quarkus, Micronaut, Jakarta CDI, Hibernate annotations, Micrometer — is optional (`provided` or
`optional` scope) and absent unless you add it.

```xml
<dependency>
    <groupId>org.embeddedjnosql.db</groupId>
    <artifactId>embed-jnosql-core</artifactId>
    <version>1.0.1</version>
</dependency>
```

### Measured artifact sizes (this repository, this build)

| Artifact | Bytes | Human |
|---|---|---|
| Shaded core jar (`target/embed-jnosql-core-1.0.1.jar`: core classes + Jackson + slf4j-api + console assets) | 3,115,815 | 2.97 MiB / 3.11 MB |
| Core classes only (`target/original-embed-jnosql-core-1.0.1.jar`, no bundled dependencies) | 584,240 | 570 KiB |

The **whole distribution** target (< 5 MB for the bundled, ready-to-run core jar) is met with
headroom; the "core JAR" in the narrow sense is ~570 KiB. Framework starters, the Quarkus
extension, and demo applications add their own dependencies and are **not** included in these
figures.

### Framework Starters & Standards

```xml
<!-- Spring Boot 3.x Starter -->
<dependency>
    <groupId>org.embeddedjnosql.db</groupId>
    <artifactId>embed-jnosql-spring-boot-starter</artifactId>
    <version>1.0.1</version>
</dependency>

<!-- Quarkus Extension -->
<dependency>
    <groupId>org.embeddedjnosql.db</groupId>
    <artifactId>embed-jnosql-quarkus-extension-runtime</artifactId>
    <version>1.0.1</version>
</dependency>

<!-- Micronaut Integration -->
<dependency>
    <groupId>org.embeddedjnosql.db</groupId>
    <artifactId>embedjnosql-micronaut-integration</artifactId>
    <version>1.0.1</version>
</dependency>
```

---

## Quick Start

### Fast In-Memory Database (zero config, tests, microservices)

```java
try (var db = EmbedJNoSQL.inMemory()) {

    // ── Document Collection ───────────────────────────────────
    var users = db.documentCollection("users");
    users.insert(Document.of(Map.of("name", "Alice", "email", "alice@example.com")).id("u1"));
    Document alice = users.findById("u1");

    // ── Document Queries (predicates, sorting, paging) ────────
    List<Document> adults = users.find(
            Query.gte("age", 18)
                 .and(Query.eq("status", "ACTIVE"))
                 .sortBy("name", Query.SortOrder.ASC)
                 .limit(20));

    // ── Entity Mapping & Fluent Query ─────────────────────────
    List<Product> affordable = db.from(Product.class)
            .where("category = ? AND price <= ?", "Peripherals", 100.0)
            .orderBy("price ASC")
            .limit(10)
            .list();

    // ── Key-Value Store ──────────────────────────────────────
    db.keyValueBucket("sessions").put("tok-abc", "user-1");
    db.keyValueBucket("sessions").expire("tok-abc", Duration.ofMinutes(30));

    // ── Redis-Style Data Structures ──────────────────────────
    db.listBucket("job-queue").rpush("tasks", "send-email", "resize-image");
    db.setBucket("permissions").sadd("admin", "write", "delete", "export");
    db.hashBucket("profiles").hset("u1", "tier", "GOLD");

    // ── Wide-Column Family ───────────────────────────────────
    var metrics = db.columnFamily("node_metrics");
    metrics.put("node-01", "cpu_pct", "42.3");
    metrics.put("node-01", "mem_mb", "2048");
}
```

### Persistent File Database (One-liner)

```java
// Open or create a persistent database with append-only WAL
try (var db = EmbedJNoSQL.open("./data/myapp")) {
    var orders = db.documentCollection("orders");
    orders.insert(Document.of(order.toMap()).id(order.id())); // durable across restarts
}

// Temporary file database with automatic JVM shutdown hook cleanup
try (var db = EmbedJNoSQL.openTemp()) {
    var scratch = db.documentCollection("scratch");
    scratch.insert(Document.of("tempKey", "tempValue"));
}
```

### Advanced Engine Configuration

```java
try (var db = EmbedJNoSQL.create(EmbedJNoSQL.embed()
        .storageEngine(StorageEngineType.FILE)   // or IN_MEMORY, B_TREE, LSM_TREE
        .persistTo("data/myapp")
        .autoFlush(true)
        .flushIntervalMs(1000)
        .buildConfig())) {

    var orders = db.documentCollection("orders");
    orders.insert(Document.of(order.toMap()).id(order.id()));
}
```

### Annotation-Driven Mapping

EmbedJNoSQL reads your existing entity annotations at runtime with **zero** additional classpath
requirements:

| Standard | Package | What EmbedJNoSQL resolves |
|---|---|---|
| **Eclipse JNoSQL** | `jakarta.nosql.*` | `@Entity`, `@Column`, `@Id` |
| **Jakarta Persistence** | `jakarta.persistence.*` | `@Entity`, `@Table`, `@Column`, `@Id`, `@Transient`, `@Enumerated` |
| **Hibernate extras** | `org.hibernate.annotations.*` | `@UuidGenerator`, `@CreationTimestamp`, `@UpdateTimestamp`, `@Formula` |

```java
@jakarta.persistence.Entity
@jakarta.persistence.Table(name = "products")
public class Product {
    @jakarta.persistence.Id
    private String id;

    @jakarta.persistence.Column(name = "unit_price")
    private double price;

    @jakarta.nosql.Column("product_name")
    private String name;
}

// Works transparently — mapped onto documents, no JPA runtime needed
List<Product> items = db.from(Product.class).where("price > ?", 50.0).list();
```

> **Note:** these are *annotation mappings onto documents*, not a JPA implementation. There is no
> `EntityManager`, no JPQL, and no relational schema. Hibernate is not required on the classpath.

### Eclipse JNoSQL / Jakarta NoSQL Native Integration

EmbedJNoSQL directly integrates with the [Eclipse JNoSQL](https://github.com/eclipse-jnosql/jnosql) / Jakarta NoSQL API:

```java
import org.embeddedjnosql.db.adapter.jnosql.EclipseDocumentTemplate;
import org.embeddedjnosql.db.adapter.jnosql.DocumentTemplate;

// 1. Create template over EmbedJNoSQL instance
EmbedJNoSQL db = EmbedJNoSQL.inMemory();
DocumentTemplate template = new EclipseDocumentTemplate(db);

// 2. Persist Jakarta NoSQL entities directly
User user = new User("u1", "Judicael");
template.insert(user);

// 3. Find by ID or run document queries
Optional<User> found = template.find(User.class, "u1");

// 4. Jakarta Data CrudRepository support
EmbedNoSQLRepository<User, String> repo = new EmbedNoSQLRepository<>(template, User.class);
repo.save(new User("u2", "Ada"));
Iterable<User> all = repo.findAll();
```

### Spring Boot 3.x Starter & Auto-Configuration

In your `pom.xml`:
```xml
<dependency>
    <groupId>org.embeddedjnosql.db</groupId>
    <artifactId>embed-jnosql-spring-boot-starter</artifactId>
    <version>1.0.1</version>
</dependency>
```

In your application code:
```java
@SpringBootApplication
public class Application {

    @Autowired
    private EmbedJNoSQL db; // Auto-configured bean!

    @Bean
    CommandLineRunner runner() {
        return args -> {
            var col = db.documentCollection("events");
            col.insert(Document.of("type", "BOOT_UP").add("timestamp", System.currentTimeMillis()));
        };
    }
}
```

Configurable properties in `application.yml`:
```yaml
embedjnosql:
  storage-engine: FILE # or IN_MEMORY, B_TREE, LSM_TREE
  data-dir: ./data
  auto-flush: true
  flush-interval-ms: 1000
  console:
    enabled: true
    port: 8080
```

### Transactions

```java
try (var tx = db.beginTransaction()) {
    tx.documentCollection("accounts").update("acc-1", "balance", 4750.0);
    tx.documentCollection("accounts").update("acc-2", "balance", 5250.0);
    tx.commit();   // both writes commit atomically, or both roll back on error
}
```

Transactions apply to writes issued on the transaction handle. Each console/REST write is its
own operation and is **not** governed by a transaction begun elsewhere — the console states this
boundary in its status bar rather than hiding it.

---

## Storage Modes

| Mode | Backing structure | Best for |
|---|---|---|
| `IN_MEMORY` | `ConcurrentHashMap` | Unit tests, ephemeral state, microservice sessions. **No durability**: data is lost on exit. |
| `FILE` | Per-collection JSON snapshot + write-ahead log | Simple local persistence, single-writer apps |
| `B_TREE` | B+ tree index over the same records | Read-heavy local apps; background flusher |
| `LSM_TREE` | Memtable + SSTables + log | Write-heavy workloads |

Switching storage mode does not change the query or collection API.

---

## Document Query Operators

`POST /api/collections/{name}/query` accepts MongoDB-style JSON filters:

```jsonc
{
  "category": { "$eq": "Peripherals" },
  "price":    { "$lt": 100 },
  "sortField": "price", "sortDir": "asc", "limit": 20, "offset": 0
}
```

Supported: `$eq $ne $gt $gte $lt $lte $in $nin $regex $exists $and $or`.
`$regex` is substring matching (anchor with `^`/`$`). Unknown operators and malformed filters are
rejected with HTTP 400 — they are never silently ignored or reinterpreted.

---

## Developer Console (optional, local)

The console is a **local management UI**, not a requirement for using the database. Nothing
starts an HTTP server unless you ask for it.

```java
try (var db = EmbedJNoSQL.create(EmbedJNoSQL.embed()
        .storageEngine(StorageEngineType.FILE)
        .persistTo("data")
        .console(ConsoleConfig.builder().enabled(true).port(8080).build())
        .buildConfig())) {
    System.out.println("Console at " + db.consoleUrl());
}
```

Or from the shaded jar:

```bash
java -jar target/embed-jnosql-core-1.0.1.jar --port 8080 --engine FILE --data-dir ./data
```

Panels: **Overview, Collections (documents), Key-Value & Redis Structures, Wide-Column
Families, Vector Index (experimental), Schema Validation, Transactions, Secondary Indexes,
Backup & Restore, Change Data Capture, Audit Trail, Server.**

The console shows its active context in a persistent status bar (storage mode with its durability
meaning, data directory, connection state, transaction state, identity) read from `GET
/api/health`'s `context` block rather than guessed in the browser. Destructive actions name their
target and impact before running. Dual document view provides both syntax-highlighted **JSON**
and collapsible interactive visual **Tree** inspection. There is **no SQL editor** — the SQL Studio
was removed with the SQL product.

---

## REST API (when the embedded server is enabled)

```bash
# Health (includes the context block)
curl http://localhost:8080/api/health

# Insert a document
curl -X POST http://localhost:8080/api/collections/products \
  -H "Content-Type: application/json" \
  -d '{"name":"Keyboard","price":75.0,"category":"Peripherals"}'

# List / read
curl http://localhost:8080/api/collections/products
curl http://localhost:8080/api/collections/products/p1

# Filter (see operators above)
curl -X POST http://localhost:8080/api/collections/products/query \
  -H "Content-Type: application/json" \
  -d '{"category":{"$eq":"Peripherals"},"price":{"$lt":100}}'

# Key-value operations
curl -X PUT http://localhost:8080/api/kv/sessions/tok-abc -d '"user-1"'

# Bucket/key metadata and TTL
curl http://localhost:8080/api/kv-meta/buckets
curl http://localhost:8080/api/kv-meta/sessions

# Storage & WAL status
curl http://localhost:8080/api/storage/status
```

Other routes: `/api/columns`, `/api/kv/lists`, `/api/kv/sets`, `/api/kv/hashes`,
`/api/indexes`, `/api/transactions`, `/api/schema`, `/api/vectors`, `/api/bulk`, `/api/backup`,
`/api/cdc`, `/api/audit/logs`, `/api/metrics`, `/api/stats`.

The removed `/api/sql` and `/api/sql/schema` routes answer 404.

---

## Migration from the SQL-enabled builds

EmbedJNoSQL 1.0.0 removed the relational product. If you used an earlier build:

| Removed | What to use instead |
|---|---|
| `db.sql("SELECT ...")`, `db.sql("INSERT ...")`, `db.sqlEngine()` | `db.documentCollection(name).find(Query...)` / `.insert(doc)` / `.update(doc)` / `.deleteById(id)`, or `db.from(Entity.class).where(...)` |
| `CREATE TABLE`, `DROP TABLE`, column types, `PRIMARY KEY`/`FOREIGN KEY`/`UNIQUE`/`CHECK` | There is no schema DDL. Collections appear on first write; constraints are not enforced. Use `SchemaValidator` (`/api/schema`) for insert-time validation rules. |
| `JOIN`, `GROUP BY`, `HAVING` | Resolve related documents in your application through stored ids; aggregate with the document helpers (`DocumentAggregation`) or your own code. A document reference is **not** a foreign key and carries no referential guarantee. |
| `POST /api/sql`, `POST /api/sql/schema` | `POST /api/collections/{name}/query`; `/api/storage/status` |
| JDBC driver (`jdbc:embedjnosql:...`) | Not available. Use the document/key-value API, or a JDBC driver for a different database if you need SQL. |
| `JembedPersistence.createEntityManager(...)`, `EntityManager`, JPQL | JNoSQL-style repositories (`EmbedRepository`, `CrudRepository`) and the fluent entity query. Annotation mapping is retained. |
| Console "SQL Studio" | Collections and Key-Value panels. |

**Data compatibility:** SQL tables were stored as document collections, so rows written by the
old SQL engine are ordinary documents and remain readable through `documentCollection(name)`.
No data migration is required, and no data files were deleted by this change.

---

## Build & Test

```bash
# Core: build and run the full suite
./mvnw test

# Install core, then run a framework demo (each demo is a standalone Maven project)
./mvnw -DskipTests install
./mvnw -f demo/spring-boot-demo/pom.xml test

# All demos
for m in demo/*/pom.xml; do ./mvnw -f "$m" test; done
```

Verified status for this release:
core **943 tests, 0 failures**; Spring Boot starter **78 tests (covering all 36 validation scenarios), 0 failures**;
the demo projects **43 tests, 0 failures**; the CLI **4 tests, 0 failures**.
Total: **1,021 tests, 0 failures, 100% green**.

---

## Target Use Cases

- **Integration testing without Docker** — start in milliseconds in the same JVM.
- **Edge & desktop JVM applications** — one jar, file-backed persistence, WAL recovery.
- **In-process caching & session stores** — local key-value with TTL, no Redis round-trip.
- **Local prototypes** — add the dependency, call `EmbedJNoSQL.inMemory()`, ship.
- **Microservice state** — rate limiters, feature flags, task queues inside the app boundary.

---

## License

[Apache License 2.0](LICENSE)

---

<div align="center">

**EmbedJNoSQL** — Write code. Not infrastructure.

</div>

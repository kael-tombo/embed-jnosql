# JunifyDB Annotation Showcase Demo

This demonstration application shows how **JunifyDB** maps annotated Java classes onto ordinary
documents, with no ORM runtime and no relational engine:

- **Eclipse JNoSQL (Jakarta NoSQL)** annotations plus the generic `JunifyRepository<T, ID>`.
- **Jakarta Persistence (JPA) annotations** (`@Entity`, `@Table`, `@Id`, `@Column`, `@Transient`)
  used as **mapping hints** — the JPA `EntityManager`/`TypedQuery` surface is not part of the
  product.
- **Hibernate annotation extensions** (`@UuidGenerator`, `@CreationTimestamp`, `@UpdateTimestamp`,
  `@Formula`, `@Enumerated`), applied at mapping time.
- **Document aggregation in application code**: summaries and per-customer groupings are computed
  from documents. JunifyDB ships no SQL engine, so there is no `GROUP BY` or `JOIN` to call.

---

## Architecture Overview

```
demo/annotation-showcase-demo/
├── pom.xml
├── README.md
└── src/
    ├── main/java/org/junify/db/demo/annotation/
    │   ├── AnnotationShowcaseApplication.java     # Runnable CLI demonstration
    │   ├── model/
    │   │   ├── CatalogProduct.java               # Eclipse JNoSQL (@Entity, @Id, @Column)
    │   │   ├── CustomerAccount.java              # JPA Standard (@Entity, @Table, @Id, @Column)
    │   │   ├── InvoiceRecord.java                # Hibernate (@UuidGenerator, @CreationTimestamp, @Formula)
    │   │   └── InvoiceStatus.java                # Enum mapped via @Enumerated(EnumType.STRING)
    │   ├── repository/
    │   │   └── CatalogProductRepository.java     # Extends JunifyRepository<CatalogProduct, String>
    │   └── service/
    │       └── OrderInvoiceService.java          # EntityMapper + MVCC transactions, no ORM
    └── test/java/org/junify/db/demo/annotation/
        └── AnnotationShowcaseTest.java           # Full integration test suite
```

---

## 1. Eclipse JNoSQL Standard

Define domain entities with standard `jakarta.nosql.*` annotations:

```java
@Entity("catalog_products")
public class CatalogProduct {
    @Id
    private String id;

    @Column("title")
    private String title;

    @Column("unit_price")
    private Double unitPrice;

    // Getters and setters
}
```

Use `JunifyRepository<T, ID>` for out-of-the-box CRUD and derived query operations:

```java
public class CatalogProductRepository extends JunifyRepository<CatalogProduct, String> {
    public CatalogProductRepository(JunifyDB db) {
        super(CatalogProduct.class, db);
    }

    public List<CatalogProduct> findByCategory(String category) {
        return findBy("category", category);   // field equality on documents
    }

    public List<CatalogProduct> inStock(int minStock) {
        return findByQuery(Query.gte("stock_qty", minStock)
                .sortBy("unit_price", Query.SortOrder.ASC));
    }
}
```

The fluent entity query (`db.from(X.class).where("price < ?", 25.0).list()`) takes **document field
filters with bound `?` parameters** — `field OP ?` terms combined with `AND` — and compiles them
into native `Query` predicates. It is not SQL text and no statement is parsed as SQL.

---

## 2. JPA-Annotated Entities, Persisted as Documents

`jakarta.persistence.*` annotations are read reflectively and used as mapping metadata. There is no
`EntityManager` and no `db.createEntityManager()` in the product — persistence goes through the
document API, and `EntityMapper` applies the annotations for you:

```java
// The collection name comes from @Table(name = "customer_accounts")
DocumentCollection customers =
        db.documentCollection(EntityMapper.getCollectionName(CustomerAccount.class));

CustomerAccount account = new CustomerAccount("CUST-1", "Alice", "alice@example.com", "PLATINUM", 500.0);

try (var tx = db.beginTransaction()) {
    tx.documentCollection("customer_accounts").insert(EntityMapper.toDocument(account));
    tx.commit();
}

// Reads use native document predicates — not JPQL
List<Document> platinum = customers.find(Query.eq("loyalty_tier", "PLATINUM"));
CustomerAccount loaded = EntityMapper.fromDocument(platinum.get(0), CustomerAccount.class);
```

`@Entity`, `@Table`, `@Id`, `@Column`, `@Transient` and `@EmbeddedId` all influence the mapping
(table name, id field, column names, ignored fields). Nothing is enforced at the database level: a
document has no foreign key, so a reference is an id your code resolves.

---

## 3. Hibernate Annotations

Mapping-time generation and derived fields work without heavy ORM infrastructure:

```java
@Entity
@Table(name = "invoices")
public class InvoiceRecord {
    @Id
    @UuidGenerator // Automated UUID generation on persist
    private String invoiceId;

    @CreationTimestamp // Automated audit creation timestamp
    private Instant createdAt;

    @UpdateTimestamp // Automated update timestamp
    private Instant updatedAt;

    private Double amount;

    @Formula("amount * 1.20") // Derived value evaluated at mapping time
    private Double totalWithTax;

    @Enumerated(EnumType.STRING)
    private InvoiceStatus status;
}
```

`@UuidGenerator` assigns the id, `@CreationTimestamp` / `@UpdateTimestamp` fill audit fields as the
document is written, and `@Formula` computes derived values during mapping.

---

## 4. Aggregation Without a SQL Engine

Entities persisted as documents can be summarised immediately — but the grouping is explicit
application code, because no SQL `GROUP BY` / `JOIN` engine is shipped:

```java
// Group documents by a field and aggregate in the application
Map<String, List<Document>> byStatus = invoices.findAll().stream()
        .collect(Collectors.groupingBy(d -> String.valueOf(d.get("status"))));

for (var entry : byStatus.entrySet()) {
    long count = entry.getValue().size();
    double total = entry.getValue().stream()
            .mapToDouble(d -> ((Number) d.get("amount")).doubleValue())
            .sum();
    System.out.println(entry.getKey() + " → " + count + " invoices, total " + total);
}

// "Join" two collections by resolving ids yourself
List<InvoiceRecord> customerInvoices =
        invoices.find(Query.eq("customer_id", customerId)); // no query planner would rewrite this into a JOIN
```

If you need SQL for reporting, run it in a relational database — not in the embedded NoSQL database.

---

## Running the Demo

### Via Maven Exec Plugin:
```bash
cd demo/annotation-showcase-demo
mvn compile exec:java
```

### Running the Integration Tests:
```bash
cd demo/annotation-showcase-demo
mvn test
```

# EmbedJNoSQL Demo: Advanced Document Queries

This demonstration application shows the query surface of the embedded **NoSQL** database: native
document predicates, compound filters, sorting, pagination, secondary indexes, and aggregation
computed from documents. There is no SQL engine and no relational engine in EmbedJNoSQL, so nothing
here is executed as a statement — every query is a predicate tree evaluated by the document engine.

## Features Demonstrated

1. **Compound document filtering**: native predicates — `Query.eq`, `ne`, `gt`, `gte`, `lt`, `lte`,
   `in`, `between`, `contains`, `regex`, `exists` — composed with `and(...)` / `or(...)`.
2. **MongoDB-style filter documents**: the same collection accepts `{"age": {"$gt": 18},
   "name": {"$regex": "^A"}}` through `QueryParser` (this is the form the console sends to
   `POST /api/collections/{name}/query`).
3. **Sorting and pagination**: `sortBy(field, ASC|DESC)`, `limit(n)`, `offset(n)` with
   deterministic ordering.
4. **Related-data enrichment in application code**: parent and child documents are matched by id
   explicitly — a reference is an id you resolve, never an enforced foreign key.
5. **Aggregation over documents**: counts, sums, averages, and min/max grouped by a field, computed
   with the Java stream API rather than a `GROUP BY` clause.
6. **Fluent entity query API**: `db.from(Customer.class).where("tier = ? AND totalSpend >= ?", "GOLD", 500.0)`
   — document field filters with bound parameters, mapped onto entities.

## Source of truth

- `AdvancedQueriesDemoApplication` — runnable walkthrough of the scenarios above.
- `AnalyticsQueryService` — the query, enrichment, and aggregation operations.
- `AdvancedQueriesDemoTest` — assertions for each scenario (runs under `mvn test`).

## Running the Demo

```bash
# Run tests
mvn test

# Run interactive CLI
mvn compile exec:java -Dexec.mainClass="org.embeddedjnosql.db.demo.query.AdvancedQueriesDemoApplication"
```

## What this demo does *not* do

SQL `JOIN`, `GROUP BY`, window functions, views, stored procedures, and a JDBC driver are **not part
of the product**. If you need those, use a relational database. EmbedJNoSQL's guarantee is that the
same process holds your documents, key-value state, Redis-style structures, and wide-column families
without a server, a dialect, or a schema migration.

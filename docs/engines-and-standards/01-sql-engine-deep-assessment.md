# JunifyDB Deep Assessment: SQL Engine & ANSI SQL Query Capabilities

> **⚠ SUPERSEDED — pre-refactor document (SQL / dual-engine).**
> JunifyDB is now NoSQL-only. The relational engine, SQL parser/planning, JDBC driver, JPA
> `EntityManager`, `/api/sql` routes, and the SQL Studio console screen were removed from the
> product. Statements in this file that describe SQL, JDBC, SQL schemas, or an engine selector no
> longer describe shipped behavior.
>
> - Current product definition: [PRODUCT-CONSTITUTION.md](../release-audit/refocus/PRODUCT-CONSTITUTION.md)
> - Removal inventory and evidence: [SQL-REMOVAL-MANIFEST.md](../release-audit/refocus/SQL-REMOVAL-MANIFEST.md)
>
> Retained as historical record only — not part of the current release contract.


**Subsystem**: `org.junify.db.sql`  
**Components**: `SqlEngine`, `SqlParser`, `SqlLexer`, `SqlStatement`, `Expression`, `SqlResultSet`, `SqlRow`  
**Status**: Fully Functional Core ANSI SQL Engine  

---

## 1. Architectural Architecture & Engine Mechanics

The JunifyDB SQL Engine provides a zero-overhead relational querying abstraction directly over embedded NoSQL document collections. Unlike relational database engines that require strict predefined physical table layouts and B-tree clustered rows, JunifyDB maps document collections dynamically as relational tables at execution time:

```
                  ┌──────────────────────┐
                  │   Raw SQL Query      │
                  └──────────┬───────────┘
                             │
                             ▼
                  ┌──────────────────────┐
                  │      SqlLexer        │
                  └──────────┬───────────┘
                             │
                             ▼
                  ┌──────────────────────┐
                  │      SqlParser       │ (Recursive Descent Parser)
                  └──────────┬───────────┘
                             │
                             ▼
                  ┌──────────────────────┐
                  │    SqlStatement      │ (Select, Insert, Update, Delete, DDL)
                  └──────────┬───────────┘
                             │
                             ▼
                  ┌──────────────────────┐
                  │      SqlEngine       │
                  └──────────┬───────────┘
                             │
        ┌────────────────────┴────────────────────┐
        ▼                                         ▼
┌───────────────────────────────┐     ┌───────────────────────────────┐
│ Dynamic Document Collections  │     │ Relational Execution Pipeline │
│ (db.documentCollection(name)) │     │ (JOINs, WHERE, GROUP BY, AGG) │
└───────────────────────────────┘     └───────────────────────────────┘
```

### Supported Statement Taxonomies
1. **SELECT**:
   - Projections: Specific columns (`SELECT name, age`), Wildcard (`SELECT *`), Computed expressions (`SELECT price * 1.2 AS total`).
   - Relational `JOIN`: `INNER JOIN`, `LEFT JOIN` with ON predicates (`JOIN orders o ON u.id = o.user_id`).
   - Filtering: Comparison operators (`=`, `!=`, `<`, `<=`, `>`, `>=`), logical operators (`AND`, `OR`, `NOT`), `LIKE` (contains/pattern matching), `IN (...)` literal lists, `IS NULL`, `IS NOT NULL`.
   - Aggregations: `COUNT(*)`, `COUNT(col)`, `SUM(col)`, `AVG(col)`, `MIN(col)`, `MAX(col)`.
   - Grouping & Ordering: `GROUP BY col1, col2`, `ORDER BY col ASC/DESC`.
   - Paging: `LIMIT n`, `OFFSET m`.
2. **DML (Data Modification Language)**:
   - `INSERT INTO table (cols) VALUES (...)`
   - `UPDATE table SET col = val WHERE condition`
   - `DELETE FROM table WHERE condition`
3. **DDL (Data Definition Language)**:
   - `CREATE TABLE [IF NOT EXISTS] tableName (colDef, ...)`
   - `DROP TABLE [IF EXISTS] tableName`

---

## 2. Developer Productivity & Java Ergonomics

### Parameter Binding & Type Safety
The SQL engine supports both positional parameters (`?`) and indexed parameters, preventing SQL injection and allowing clean programmatic queries:
```java
SqlResultSet rs = db.sql("SELECT * FROM users WHERE age > ? AND status = ?", 21, "ACTIVE");
```

### Automatic Entity Mapping
`SqlResultSet` provides direct POJO and Java Record mapping via reflection:
```java
List<CatalogProduct> products = db.sql("SELECT * FROM catalog_products WHERE price < ?", 50.0)
                                  .mapTo(CatalogProduct.class);
```

---

## 3. Identified Gaps & Opportunities for Improvement

1. **Subqueries**: `SELECT * FROM users WHERE id IN (SELECT user_id FROM orders)` is currently not parsed; subquery AST nodes should be implemented.
2. **LIKE Wildcard Semantics**: Current `LIKE` treats the expression as a substring match or prefix match; full `%` and `_` regex conversion should be added.
3. **BETWEEN Operator**: `WHERE age BETWEEN 18 AND 30` is currently written as `WHERE age >= 18 AND age <= 30`; adding `BETWEEN` simplifies developer queries.
4. **Secondary Index Utilization**: The SQL Engine currently scans document collections and evaluates expressions in memory; it should consult collection secondary indexes (`IndexManager`) when a `WHERE` clause targets an indexed field.

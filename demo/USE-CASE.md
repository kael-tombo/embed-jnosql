# Use Case Specification: Real-Time E-Commerce & Inventory Management

## Context & Problem Statement
Modern edge and embedded applications, microservices, and branch office systems require rich database capabilities without the operational overhead, memory consumption, or external service dependencies of standalone database clusters (e.g. MongoDB, Cassandra, Redis).

Developers frequently resort to H2 for relational use cases, but when applications deal with dynamic documents, fast key-value caches, and wide-column time series/inventory matrices, no lightweight, pure-Java embedded solution has existed.

**EmbedJNoSQL** fills this gap as the embedded NoSQL counterpart to H2.

---

## User Journeys & Requirements

### Journey 1: Product Catalog Browsing & Search
1. **Catalog Seeding**: System initializes with rich product items featuring flexible attributes (`pages`, `switches`, `resolution`, `batteryHours`).
2. **Category Search**: Users search products by category (e.g. `Books`, `Laptops`).
   - Query engine leverages secondary index without full table scans.
3. **Price Lookups**: Fast price check utilizes the in-memory KV bucket (`price_cache`).

### Journey 2: Transactional Checkout with Stock Deduction
1. **Order Initiation**: Customer places an order with multiple line items.
2. **Inventory Verification**: The transaction reads the wide-column family `inventory` for each product.
3. **Sufficient Stock**:
   - Deducts quantities atomically from the `available` column.
   - Saves `Order` into the document collection `orders` with status `CONFIRMED`.
   - Commits transaction.
4. **Insufficient Stock**:
   - Detects negative stock condition.
   - Rolls back transaction immediately.
   - Order is discarded, inventory remains intact.

### Journey 3: Durability Across Cold Restarts
1. Service terminates abruptly or restarts for upgrade.
2. Re-opened embedded instance reloads SSTables, indexes, and WAL logs.
3. Data consistency is fully verified with zero lost records.

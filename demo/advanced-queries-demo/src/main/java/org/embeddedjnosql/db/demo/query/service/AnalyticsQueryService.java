package org.embeddedjnosql.db.demo.query.service;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.adapter.jnosql.EntityMapper;
import org.embeddedjnosql.db.demo.query.model.CatalogProduct;
import org.embeddedjnosql.db.demo.query.model.Customer;
import org.embeddedjnosql.db.demo.query.model.Order;
import org.embeddedjnosql.db.demo.query.model.OrderItem;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentAggregation;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.document.Query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service demonstrating the NoSQL query surface of EmbedJNoSQL:
 * <ul>
 *   <li>Document collection queries with a compound predicate (eq / gte / lte), sorted and paged</li>
 *   <li>Range and substring filters (the BETWEEN / LIKE style bounds, expressed as document
 *       predicates)</li>
 *   <li>Aggregation helpers (count, min, max, sum, avg) over documents</li>
 *   <li>Application-side enrichment of related documents — there is no relational JOIN engine;
 *       a document reference is an id the application resolves itself</li>
 *   <li>Fluent entity queries ({@code db.from(Entity.class)})</li>
 * </ul>
 */
public class AnalyticsQueryService {

    private final EmbedJNoSQL db;
    private final DocumentCollection customers;
    private final DocumentCollection orders;
    private final DocumentCollection catalog;

    public AnalyticsQueryService(EmbedJNoSQL db) {
        this.db = db;
        db.registerEntity(Customer.class, Order.class, OrderItem.class, CatalogProduct.class);
        this.customers = db.documentCollection(EntityMapper.getCollectionName(Customer.class));
        this.orders = db.documentCollection(EntityMapper.getCollectionName(Order.class));
        this.catalog = db.documentCollection(EntityMapper.getCollectionName(CatalogProduct.class));
    }

    public void seedData() {
        // Customers and orders are stored through the entity mapping so the fluent entity
        // query (db.from(Customer.class)) reads exactly what was written.
        customers.insert(EntityMapper.toDocument(
                new Customer("cust-1", "Alice Vance", "alice@example.com", "GOLD", 3450.00)));
        customers.insert(EntityMapper.toDocument(
                new Customer("cust-2", "Bob Smith", "bob@example.com", "SILVER", 1200.50)));
        customers.insert(EntityMapper.toDocument(
                new Customer("cust-3", "Charlie Brown", "charlie@example.com", "PLATINUM", 8900.00)));
        customers.insert(EntityMapper.toDocument(
                new Customer("cust-4", "Diana Prince", "diana@example.com", "BRONZE", 450.00)));

        orders.insert(EntityMapper.toDocument(
                new Order("ord-101", "cust-1", 450.00, "COMPLETED", "2026-03-01")));
        orders.insert(EntityMapper.toDocument(
                new Order("ord-102", "cust-1", 1200.00, "COMPLETED", "2026-03-05")));
        orders.insert(EntityMapper.toDocument(
                new Order("ord-103", "cust-2", 300.00, "PENDING", "2026-03-08")));
        orders.insert(EntityMapper.toDocument(
                new Order("ord-104", "cust-3", 2500.00, "COMPLETED", "2026-03-10")));

        catalog.insert(EntityMapper.toDocument(
                new CatalogProduct("prod-1", "Developer Laptop Pro", "Hardware", 2499.99, 4.9, 15)));
        catalog.insert(EntityMapper.toDocument(
                new CatalogProduct("prod-2", "Gaming Laptop X", "Hardware", 1899.50, 4.7, 8)));
        catalog.insert(EntityMapper.toDocument(
                new CatalogProduct("prod-3", "UltraWide Monitor", "Peripherals", 1199.00, 4.8, 25)));
        catalog.insert(EntityMapper.toDocument(
                new CatalogProduct("prod-4", "Mechanical Keyboard", "Peripherals", 149.99, 4.6, 50)));
        catalog.insert(EntityMapper.toDocument(
                new CatalogProduct("prod-5", "Ergonomic Mouse", "Peripherals", 89.99, 4.5, 75)));
        catalog.insert(EntityMapper.toDocument(
                new CatalogProduct("prod-6", "IDE Enterprise License", "Software", 499.00, 4.9, 999)));

        // A raw document collection for the document-criteria demonstration.
        DocumentCollection col = db.documentCollection("products_nosql");
        col.insert(Document.of(Map.of("name", "Developer Laptop Pro", "category", "Hardware",
                "price", 2499.99, "rating", 4.9, "tags", List.of("laptop", "developer", "m3"))).id("doc-1"));
        col.insert(Document.of(Map.of("name", "Gaming Laptop X", "category", "Hardware",
                "price", 1899.50, "rating", 4.7, "tags", List.of("laptop", "gaming", "rtx"))).id("doc-2"));
        col.insert(Document.of(Map.of("name", "UltraWide Monitor", "category", "Peripherals",
                "price", 1199.00, "rating", 4.8, "tags", List.of("display", "4k"))).id("doc-3"));
        col.insert(Document.of(Map.of("name", "Mechanical Keyboard", "category", "Peripherals",
                "price", 149.99, "rating", 4.6, "tags", List.of("accessory", "rgb"))).id("doc-4"));
    }

    /**
     * Completed orders, newest value first, each enriched with its customer's name and tier.
     *
     * <p>The relational engine is gone, so this is not a JOIN: the orders are matched by a
     * document predicate, sorted by the engine, and the related customer is resolved by the
     * application from the {@code customerId} reference. No referential guarantee is implied.</p>
     */
    public List<Map<String, Object>> findCompletedOrdersEnriched() {
        Query completed = Query.eq("status", "COMPLETED")
                .sortBy("totalAmount", Query.SortOrder.DESC);
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Document order : orders.find(completed)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orderId", order.getId());
            row.put("totalAmount", order.getRaw("totalAmount"));
            String customerId = String.valueOf(order.getRaw("customerId"));
            Document customer = customers.findById(customerId);
            row.put("customerName", customer != null ? customer.getRaw("name") : null);
            row.put("tier", customer != null ? customer.getRaw("tier") : null);
            enriched.add(row);
        }
        return enriched;
    }

    /**
     * Aggregate pricing statistics over the catalog, computed with the document aggregation
     * helpers rather than a relational GROUP BY.
     */
    public Map<String, Object> catalogPriceStats() {
        List<Document> docs = catalog.findAll();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("count", DocumentAggregation.count(docs));
        stats.put("min", DocumentAggregation.min(docs, "price").map(d -> d.getRaw("price")).orElse(null));
        stats.put("max", DocumentAggregation.max(docs, "price").map(d -> d.getRaw("price")).orElse(null));
        stats.put("sum", DocumentAggregation.sum(docs, "price"));
        stats.put("avg", DocumentAggregation.avg(docs, "price"));
        stats.put("byCategory", DocumentAggregation.groupBy(docs, "category"));
        return stats;
    }

    /**
     * Catalog products whose price falls in an inclusive range and whose name contains a
     * substring, cheapest first — the document-predicate form of {@code BETWEEN} + {@code LIKE}.
     */
    public List<Document> findCatalogInPriceRangeContaining(double minPrice, double maxPrice, String nameSubstring) {
        Query q = Query.between("price", minPrice, maxPrice)
                .and(Query.contains("name", nameSubstring))
                .sortBy("price", Query.SortOrder.ASC);
        return catalog.find(q);
    }

    /**
     * Demonstrates Fluent Entity Queries (`db.from(Entity.class)`).
     */
    public List<Customer> executeFluentCustomerQuery(String tier, double minSpend) {
        return db.from(Customer.class)
                .where("tier = ? AND lifetimeSpend >= ?", tier, minSpend)
                .orderBy("lifetimeSpend DESC")
                .list();
    }

    /**
     * Demonstrates NoSQL Document queries using complex Query criteria.
     */
    public List<Document> executeNoSqlCatalogQuery(String category, double minRating, double maxPrice) {
        DocumentCollection col = db.documentCollection("products_nosql");
        Query q = Query.eq("category", category)
                .and(Query.gte("rating", minRating))
                .and(Query.lte("price", maxPrice));
        return col.find(q);
    }
}

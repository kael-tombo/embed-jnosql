package org.embeddedjnosql.db.demo.query;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.demo.query.model.Customer;
import org.embeddedjnosql.db.demo.query.service.AnalyticsQueryService;
import org.embeddedjnosql.db.nosql.document.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated test suite validating document predicate queries, aggregation helpers,
 * range/substring filters, fluent entity queries, and compound document criteria.
 */
public class AdvancedQueriesDemoTest {

    private EmbedJNoSQL db;
    private AnalyticsQueryService service;

    @BeforeEach
    void setUp() {
        db = EmbedJNoSQL.inMemory();
        service = new AnalyticsQueryService(db);
        service.seedData();
    }

    @AfterEach
    void tearDown() {
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

    @Test
    @DisplayName("QUERY-01: completed orders are filtered, sorted, and enriched")
    void testCompletedOrdersEnriched() {
        List<Map<String, Object>> rows = service.findCompletedOrdersEnriched();
        assertNotNull(rows);
        assertEquals(3, rows.size(), "ord-101, ord-102, ord-104 are COMPLETED");

        // Engine-side sort: totalAmount DESC.
        double prev = Double.MAX_VALUE;
        for (Map<String, Object> row : rows) {
            double amount = ((Number) row.get("totalAmount")).doubleValue();
            assertTrue(amount <= prev, "rows must be ordered descending by totalAmount");
            prev = amount;
            assertNotNull(row.get("customerName"), "each order is enriched with its customer name");
        }
        assertEquals("Charlie Brown", rows.get(0).get("customerName"));
    }

    @Test
    @DisplayName("QUERY-02: document aggregation helpers count/min/max/sum/avg")
    void testCatalogAggregations() {
        Map<String, Object> stats = service.catalogPriceStats();
        assertEquals(6L, ((Number) stats.get("count")).longValue());
        assertEquals(89.99, ((Number) stats.get("min")).doubleValue(), 0.01);
        assertEquals(2499.99, ((Number) stats.get("max")).doubleValue(), 0.01);
        assertTrue(((Number) stats.get("avg")).doubleValue() > 0);

        @SuppressWarnings("unchecked")
        Map<Object, Long> byCategory = (Map<Object, Long>) stats.get("byCategory");
        assertEquals(2L, byCategory.get("Hardware"));
        assertEquals(3L, byCategory.get("Peripherals"));
        assertEquals(1L, byCategory.get("Software"));
    }

    @Test
    @DisplayName("QUERY-03: inclusive price range + name substring predicate")
    void testRangeAndSubstringFilter() {
        List<Document> rs = service.findCatalogInPriceRangeContaining(1000.0, 3000.0, "Laptop");
        assertEquals(2, rs.size(), "Should match Developer Laptop Pro and Gaming Laptop X");

        for (Document doc : rs) {
            String name = String.valueOf(doc.getRaw("name"));
            assertTrue(name.contains("Laptop"));
            double price = ((Number) doc.getRaw("price")).doubleValue();
            assertTrue(price >= 1000.0 && price <= 3000.0);
        }
    }

    @Test
    @DisplayName("QUERY-04: Fluent Entity Queries (db.from(Customer.class))")
    void testFluentCustomerQuery() {
        List<Customer> customers = service.executeFluentCustomerQuery("GOLD", 3000.0);
        assertNotNull(customers);
        assertEquals(1, customers.size());
        Customer alice = customers.get(0);
        assertEquals("Alice Vance", alice.getName());
        assertEquals("GOLD", alice.getTier());
        assertTrue(alice.getLifetimeSpend() >= 3000.0);
    }

    @Test
    @DisplayName("QUERY-05: Advanced NoSQL Document Criteria Query")
    void testNoSqlCompoundQuery() {
        List<Document> hardwareDocs = service.executeNoSqlCatalogQuery("Hardware", 4.8, 2600.0);
        assertNotNull(hardwareDocs);
        assertEquals(1, hardwareDocs.size());
        Document doc = hardwareDocs.get(0);
        assertEquals("Developer Laptop Pro", doc.get("name"));
        assertEquals("Hardware", doc.get("category"));
    }
}

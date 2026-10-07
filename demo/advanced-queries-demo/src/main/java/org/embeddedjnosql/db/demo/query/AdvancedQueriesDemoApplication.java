package org.embeddedjnosql.db.demo.query;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.demo.query.model.Customer;
import org.embeddedjnosql.db.demo.query.service.AnalyticsQueryService;
import org.embeddedjnosql.db.nosql.document.Document;

import java.util.List;
import java.util.Map;

/**
 * CLI runner demonstrating EmbedJNoSQL's NoSQL query surface: document predicates, range and
 * substring filters, aggregation helpers, application-side enrichment, and fluent entity
 * queries.
 */
public class AdvancedQueriesDemoApplication {

    public static void main(String[] args) {
        System.out.println("=========================================================");
        System.out.println("   EmbedJNoSQL Demo: NoSQL Query Engine                     ");
        System.out.println("=========================================================");

        try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
            AnalyticsQueryService service = new AnalyticsQueryService(db);
            service.seedData();
            System.out.println("Seeded document collections.\n");

            // 1. Document predicate + engine-side sort + application-side enrichment
            System.out.println("[1/4] Completed orders, newest value first, enriched with the customer...");
            List<Map<String, Object>> orders = service.findCompletedOrdersEnriched();
            System.out.println("  Matched documents (" + orders.size() + "):");
            for (Map<String, Object> row : orders) {
                System.out.printf("   - %s | %s (%s) | $%s%n",
                        row.get("orderId"), row.get("customerName"), row.get("tier"), row.get("totalAmount"));
            }

            // 2. Aggregation helpers over documents
            System.out.println("\n[2/4] Catalog price statistics (count / min / max / sum / avg)...");
            Map<String, Object> stats = service.catalogPriceStats();
            System.out.printf("   - count: %s | min: $%s | max: $%s | sum: $%s | avg: $%s%n",
                    stats.get("count"), stats.get("min"), stats.get("max"), stats.get("sum"), stats.get("avg"));
            System.out.println("   - by category: " + stats.get("byCategory"));

            // 3. Range + substring predicate
            System.out.println("\n[3/4] Catalog products priced 1000-3000 whose name contains \"Laptop\"...");
            for (Document doc : service.findCatalogInPriceRangeContaining(1000.0, 3000.0, "Laptop")) {
                System.out.printf("   - %s | Price: $%s | Rating: %s%n",
                        doc.getRaw("name"), doc.getRaw("price"), doc.getRaw("rating"));
            }

            // 4. Fluent entity query
            System.out.println("\n[4/4] Fluent entity query (db.from(Customer.class).where(...))...");
            for (Customer c : service.executeFluentCustomerQuery("GOLD", 3000.0)) {
                System.out.printf("   - %s | Email: %s | Tier: %s | Spend: $%.2f%n",
                        c.getName(), c.getEmail(), c.getTier(), c.getLifetimeSpend());
            }

            System.out.println("\n=========================================================");
            System.out.println("   Advanced NoSQL Queries Demo Completed Successfully!   ");
            System.out.println("=========================================================");
        }
    }
}

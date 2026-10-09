package org.embeddedjnosql.db.demo.demos.aggregationpipeline;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.Query;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Demo 12 — Aggregation: grouping and aggregates (count, sum, avg, min, max)
 * are computed from documents in the JVM with the streams API. EmbedJNoSQL
 * deliberately has no SQL engine: no GROUP BY, no JOIN — you fetch with
 * predicates and aggregate in your own code, which this demo shows idiomatically.
 */
public class AggregationDemo {

    public record Result(Map<String, Double> revenueByCategory, Map<String, Long> countByCategory,
                         double overallAvg, double maxPrice, double minPrice) {}

    public static Result runDemo(EmbedJNoSQL db) {
        var orders = db.documentCollection("sales");
        orders.insert(new Document().id("s1").add("category", "Laptops").add("amount", 1899.99));
        orders.insert(new Document().id("s2").add("category", "Laptops").add("amount", 1299.00));
        orders.insert(new Document().id("s3").add("category", "Audio").add("amount", 349.00));
        orders.insert(new Document().id("s4").add("category", "Audio").add("amount", 199.00));
        orders.insert(new Document().id("s5").add("category", "Books").add("amount", 59.99));

        // Predicated fetch: the engine narrows the rows, the JVM aggregates them
        List<Document> sales = orders.find(Query.gt("amount", 0));

        Map<String, Double> revenue = sales.stream().collect(Collectors.groupingBy(
                d -> d.get("category"),
                Collectors.summingDouble(d -> ((Number) d.get("amount")).doubleValue())));

        Map<String, Long> counts = sales.stream().collect(Collectors.groupingBy(
                d -> d.get("category"),
                Collectors.counting()));

        double avg = sales.stream().mapToDouble(d -> ((Number) d.get("amount")).doubleValue()).average().orElse(0);
        double max = sales.stream().mapToDouble(d -> ((Number) d.get("amount")).doubleValue()).max().orElse(0);
        double min = sales.stream().mapToDouble(d -> ((Number) d.get("amount")).doubleValue()).min().orElse(0);

        Result r = new Result(new LinkedHashMap<>(revenue), new LinkedHashMap<>(counts), avg, max, min);
        if (Math.abs(r.revenueByCategory().get("Laptops") - 3198.99) > 0.01
                || r.countByCategory().get("Audio") != 2L || Math.abs(r.maxPrice() - 1899.99) > 0.01) {
            throw new AssertionError("Unexpected aggregation results: " + r);
        }
        return r;
    }

    public static void main(String[] args) {
        try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
            Result r = runDemo(db);
            System.out.println("== Aggregation demo ==");
            System.out.println("revenue by category : " + r.revenueByCategory());
            System.out.println("count by category   : " + r.countByCategory());
            System.out.printf("avg / min / max     : %.2f / %.2f / %.2f%n", r.overallAvg(), r.minPrice(), r.maxPrice());
        }
    }
}

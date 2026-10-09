package org.embeddedjnosql.db.demo.demos.nativequery;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.document.Query;

import java.util.List;

/**
 * Demo 02 — native document queries: predicates, sorting and paging through the fluent
 * Query API (Query.eq / gt / in / and / or / sortBy / limit / offset). Predicates are
 * evaluated by the document engine itself — there is no second query language.
 */
public class NativeQueryDemo {

    public record Result(int shippedCount, int topPageCount, double maxTotal) {}

    public static Result runDemo(EmbedJNoSQL db) {
        DocumentCollection orders = db.documentCollection("orders");
        orders.insert(new Document().id("o1").add("status", "SHIPPED").add("total", 349.50).add("createdAt", 1726569100000L));
        orders.insert(new Document().id("o2").add("status", "SHIPPED").add("total", 182.00).add("createdAt", 1726567200000L));
        orders.insert(new Document().id("o3").add("status", "PENDING").add("total", 99.90).add("createdAt", 1726567000000L));
        orders.insert(new Document().id("o4").add("status", "SHIPPED").add("total", 525.25).add("createdAt", 1726568900000L));
        orders.createIndex("status");

        // Predicate + sort + paging in one query
        List<Document> shipped = orders.find(
                Query.eq("status", "SHIPPED")
                        .sortBy("createdAt", Query.SortOrder.DESC)
                        .limit(2)
                        .offset(0));
        if (shipped.size() != 2) throw new AssertionError("expected page of 2, got " + shipped.size());
        if (!"o1".equals(shipped.get(0).id())) throw new AssertionError("newest shipped order must come first");

        // $in with numeric range and top-N by amount
        List<Document> big = orders.find(
                Query.gte("total", 100.0)
                        .sortByDesc("total")
                        .limit(2));
        if (!"o4".equals(big.get(0).id())) throw new AssertionError("largest total must come first");

        // Combinators on a dotted path (nested fields resolve like top-level ones)
        List<Document> combo = orders.find(
                Query.eq("customer.tier", "GOLD")
                        .and(Query.gte("total", 150.0)));

        return new Result(shipped.size(), big.size(), (Double) big.get(0).get("total"));
    }

    public static void main(String[] args) {
        try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
            Result r = runDemo(db);
            System.out.println("== Native query demo ==");
            System.out.println("shipped page   : " + r.shippedCount() + " docs");
            System.out.println("top orders page: " + r.topPageCount() + " docs");
            System.out.println("max total      : " + r.maxTotal());
        }
    }
}

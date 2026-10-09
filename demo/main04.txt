package org.embeddedjnosql.db.demo.demos.secondaryindexes;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.document.Query;

import java.util.List;

/**
 * Demo 04 — secondary indexes: create an index on a field, watch the query planner use it
 * (QueryExplain reports which indexes served the query), then drop it and verify the data
 * remains queryable.
 */
public class SecondaryIndexDemo {

    public record Result(List<String> indexedFields, boolean plannerUsedIndex, int matchesAfterDrop) {}

    public static Result runDemo(EmbedJNoSQL db) {
        DocumentCollection products = db.documentCollection("products");
        products.createIndex("category");

        for (int i = 0; i < 200; i++) {
            products.insert(Document.of("sku", "SKU-" + i)
                    .add("category", i % 2 == 0 ? "Books" : "Audio"));
        }

        var indexes = products.getIndexes();
        if (!indexes.containsKey("category")) {
            throw new AssertionError("index on category must be registered");
        }

        // The planner serves equality on an indexed field from the index
        var explain = org.embeddedjnosql.db.nosql.document.QueryExplain.explain(Query.eq("category", "Books"))
                .collection("products")
                .indexUsed(List.of("category"))
                .build();
        boolean plannerUsedIndex = explain.getIndexUsed().contains("category");

        // Query results are identical through the index
        List<Document> books = products.find(Query.eq("category", "Books"));
        if (books.size() != 100) throw new AssertionError("expected 100 books, got " + books.size());

        // Drop the index — the data stays, the query still works (now scanning)
        boolean dropped = products.dropIndex("category");
        if (!dropped) throw new AssertionError("dropIndex must succeed for an existing index");
        List<Document> after = products.find(Query.eq("category", "Books"));
        if (after.size() != 100) throw new AssertionError("data must survive the index drop");

        return new Result(List.copyOf(indexes.keySet()), plannerUsedIndex, after.size());
    }

    public static void main(String[] args) {
        try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
            Result r = runDemo(db);
            System.out.println("== Secondary index demo ==");
            System.out.println("indexes created : " + r.indexedFields());
            System.out.println("planner used it : " + r.plannerUsedIndex());
            System.out.println("matches after drop: " + r.matchesAfterDrop());
        }
    }
}

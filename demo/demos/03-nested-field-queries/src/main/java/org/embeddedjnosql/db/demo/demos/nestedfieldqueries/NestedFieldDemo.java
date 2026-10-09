package org.embeddedjnosql.db.demo.demos.nestedfieldqueries;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.document.Query;

import java.util.List;
import java.util.Map;

/**
 * Demo 03 — nested (dotted-path) queries: document values can be maps and lists, and
 * every predicate, sort and $exists resolves dotted paths through nested maps and
 * array indices. Filtering on {"address.city": "Nairobi"} matches the nested value.
 */
public class NestedFieldDemo {

    public record Result(int inNairobi, int southern, int withAddress) {}

    public static Result runDemo(EmbedJNoSQL db) {
        DocumentCollection customers = db.documentCollection("customers");

        customers.insert(Document.of("name", "Ada")
                .add("address", Map.of("city", "Nairobi", "geo", Map.of("lat", -1.2)))
                .add("tags", List.of("vip", "east")));
        customers.insert(Document.of("name", "Bob")
                .add("address", Map.of("city", "Mombasa", "geo", Map.of("lat", -4.0))));
        customers.insert(Document.of("name", "Cara")); // no address at all

        // 1. Equality on a nested map path
        List<Document> inNairobi = customers.find(Query.eq("address.city", "Nairobi"));
        if (inNairobi.size() != 1 || !"Ada".equals(inNairobi.get(0).get("name"))) {
            throw new AssertionError("nested equality must match only Ada");
        }

        // 2. Numeric comparison two levels deep (address.geo.lat)
        List<Document> southern = customers.find(Query.lt("address.geo.lat", -3.0));
        if (southern.size() != 1 || !"Bob".equals(southern.get(0).get("name"))) {
            throw new AssertionError("deep numeric predicate must match only Bob");
        }

        // 3. $exists on a dotted path — documents without the parent don't match
        List<Document> withAddress = customers.find(Query.exists("address.city"));
        if (withAddress.size() != 2) {
            throw new AssertionError("exists(address.city) must match exactly the two addressed docs");
        }

        // 4. Sorting by a nested path works the same way
        List<Document> byLat = customers.find(Query.exists("address.geo.lat")
                .sortByAsc("address.geo.lat"));
        if (!"Bob".equals(byLat.get(0).get("name"))) {
            throw new AssertionError("ascending lat must put Bob (-4.0) first");
        }

        return new Result(inNairobi.size(), southern.size(), withAddress.size());
    }

    public static void main(String[] args) {
        try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
            Result r = runDemo(db);
            System.out.println("== Nested field query demo ==");
            System.out.println("eq(address.city=Nairobi) : " + r.inNairobi() + " match");
            System.out.println("lt(address.geo.lat=-3.0) : " + r.southern() + " match");
            System.out.println("exists(address.city)     : " + r.withAddress() + " matches");
        }
    }
}

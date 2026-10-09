package org.embeddedjnosql.db.demo.demos.metrics;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.document.Document;

import java.util.Map;

/**
 * Demo 15 — Metrics: the engine records every operation. The snapshot reports
 * insert/read/update/delete/query/transaction counters plus memory stats, and
 * per-collection sizes are tracked as documents come and go.
 */
public class MetricsDemo {

    public record Result(long inserts, long reads, long queries, long deletes,
                         Map<String, Object> snapshot) {}

    public static Result runDemo(EmbedJNoSQL db) {
        var col = db.documentCollection("measured");
        var kv = db.keyValueBucket("measured_kv");

        // Drive some traffic
        col.insert(new Document().id("m1").add("v", 1));
        col.insert(new Document().id("m2").add("v", 2));
        col.findById("m1");
        col.find(org.embeddedjnosql.db.nosql.document.Query.gt("v", 0));
        col.deleteById("m2");
        kv.put("k", "v");
        kv.get("k");

        var snap = db.metrics().snapshot();

        long inserts = ((Number) snap.getOrDefault("inserts", 0)).longValue();
        long reads = ((Number) snap.getOrDefault("reads", 0)).longValue();
        long queries = ((Number) snap.getOrDefault("queries", 0)).longValue();
        long deletes = ((Number) snap.getOrDefault("deletes", 0)).longValue();

        Result r = new Result(inserts, reads, queries, deletes, snap);
        if (r.inserts() < 3 || r.reads() < 2 || r.queries() < 1 || r.deletes() < 1) {
            throw new AssertionError("Unexpected metric counts: " + r);
        }
        return r;
    }

    public static void main(String[] args) {
        try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
            Result r = runDemo(db);
            System.out.println("== Metrics demo ==");
            System.out.println("inserts : " + r.inserts());
            System.out.println("reads   : " + r.reads());
            System.out.println("queries : " + r.queries());
            System.out.println("deletes : " + r.deletes());
            System.out.println("snapshot keys: " + r.snapshot().keySet());
        }
    }
}

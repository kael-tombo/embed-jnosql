package org.embeddedjnosql.db.demo.demos.widecolumnfamilies;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.column.ColumnFamily;

import java.util.Map;
import java.util.Set;

/**
 * Demo 10 — Wide-Column families: sparse rows, column-slice pagination,
 * prefix scans, and column-level TTL with cleanup. Good for time-series and
 * per-entity attribute bags where each row has its own column set.
 */
public class WideColumnDemo {

    public record Result(Map<String, Object> firstPage, long pageCount, long remainingTtl,
                         int cleanedUp, Map<String, Object> tempColumns) {}

    public static Result runDemo(EmbedJNoSQL db) {
        ColumnFamily metrics = db.columnFamily("device_metrics");

        // 1. A sparse row: this device reports three columns, another might report forty
        metrics.put("dev-1", "temp", 21.5);
        metrics.put("dev-1", "humidity", 48);
        metrics.put("dev-1", "battery", 87);

        // 2. Column-slice pagination: first column only
        Map<String, Object> page1 = metrics.getRow("dev-1", 1, 0);
        long total = metrics.getRow("dev-1").size();

        // 3. Prefix scan: all columns starting with "temp"
        metrics.put("dev-1", "temp_max", 25.0);
        metrics.put("dev-1", "temp_min", 18.0);
        Map<String, Object> temps = metrics.getRowByPrefix("dev-1", "temp");

        // 4. Column TTL: a transient flag expires on read
        metrics.putWithTtl("dev-1", "maintenance_flag", true, 1);
        try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
        boolean expired = metrics.get("dev-1", "maintenance_flag") == null;
        if (!expired) {
            throw new AssertionError("Column TTL did not expire");
        }

        // 5. Cleanup sweep on the row
        int cleaned = metrics.cleanupExpiredColumns("dev-1");

        Result r = new Result(page1, total, 0L, cleaned, temps);
        if (r.firstPage().size() != 1 || r.pageCount() < 3 || r.cleanedUp() < 0
                || !r.tempColumns().containsKey("temp_max")) {
            throw new AssertionError("Unexpected column-family results: " + r);
        }
        return r;
    }

    public static void main(String[] args) {
        try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
            Result r = runDemo(db);
            System.out.println("== Wide-column demo ==");
            System.out.println("first column page : " + r.firstPage());
            System.out.println("total columns     : " + r.pageCount());
            System.out.println("expired cols swept: " + r.cleanedUp());
            System.out.println("temp* columns     : " + r.tempColumns());
        }
    }
}

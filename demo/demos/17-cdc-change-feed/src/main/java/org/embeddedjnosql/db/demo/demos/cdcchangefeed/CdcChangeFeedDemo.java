package org.embeddedjnosql.db.demo.demos.cdcchangefeed;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.core.cdc.CDCEvent;
import org.embeddedjnosql.db.nosql.document.Document;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Demo 17 — Change Data Capture: every insert/update/delete on the document
 * path flows into the CDC manager. Subscribers see typed events; the event log
 * is queryable by timestamp; a file connector can persist the stream.
 */
public class CdcChangeFeedDemo {

    public record Result(int inserts, int updates, int deletes, int logSize, String lastJson) {}

    public static Result runDemo(EmbedJNoSQL db) {
        var cdc = db.cdcManager();
        List<CDCEvent> seen = new CopyOnWriteArrayList<>();
        cdc.processor().subscribe(seen::add);

        var col = db.documentCollection("cdc_demo");
        col.insert(new Document().id("c1").add("status", "NEW"));
        var doc = col.findById("c1");
        doc.add("status", "SHIPPED");
        col.update(doc);
        col.deleteById("c1");

        var log = cdc.processor().getEventLog();
        CDCEvent last = log.isEmpty() ? null : log.get(log.size() - 1);

        Result r = new Result(
                (int) seen.stream().filter(e -> e.eventType() == CDCEvent.EventType.INSERT).count(),
                (int) seen.stream().filter(e -> e.eventType() == CDCEvent.EventType.UPDATE).count(),
                (int) seen.stream().filter(e -> e.eventType() == CDCEvent.EventType.DELETE).count(),
                log.size(),
                last != null ? last.toJson() : null);

        if (r.inserts() != 1 || r.updates() != 1 || r.deletes() != 1 || r.logSize() < 3) {
            throw new AssertionError("Unexpected CDC results: " + r);
        }
        return r;
    }

    public static void main(String[] args) {
        try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
            Result r = runDemo(db);
            System.out.println("== CDC change feed demo ==");
            System.out.println("insert events : " + r.inserts());
            System.out.println("update events : " + r.updates());
            System.out.println("delete events : " + r.deletes());
            System.out.println("log size      : " + r.logSize());
            System.out.println("last event    : " + r.lastJson());
        }
    }
}

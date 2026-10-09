package org.embeddedjnosql.db.demo.demos.eventbus;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.core.event.EventBus;
import org.embeddedjnosql.db.nosql.document.Document;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Demo 14 — Event bus: subscribe to document lifecycle events (BEFORE/AFTER
 * insert, update, delete). Listeners run synchronously on the write path and
 * {@code clear()} detaches user listeners but not internal system ones.
 */
public class EventBusDemo {

    public record Result(int inserts, int updates, int deletes, int userListeners) {}

    public static Result runDemo(EmbedJNoSQL db) {
        EventBus bus = db.eventBus();
        List<String> audit = new CopyOnWriteArrayList<>();

        bus.on(EventBus.EventType.AFTER_INSERT, e -> audit.add("INS:" + e.collection()));
        bus.on(EventBus.EventType.AFTER_UPDATE, e -> audit.add("UPD:" + e.collection()));
        bus.on(EventBus.EventType.AFTER_DELETE, e -> audit.add("DEL:" + e.collection()));

        var users = db.documentCollection("audited_users");
        users.insert(new Document().id("u1").add("name", "Ada"));

        var ada = users.findById("u1");
        ada.add("name", "Ada Lovelace");
        users.update(ada);

        users.deleteById("u1");

        int inserts = (int) audit.stream().filter(s -> s.startsWith("INS")).count();
        int updates = (int) audit.stream().filter(s -> s.startsWith("UPD")).count();
        int deletes = (int) audit.stream().filter(s -> s.startsWith("DEL")).count();

        Result r = new Result(inserts, updates, deletes, bus.listenerCount());
        if (r.inserts() != 1 || r.updates() != 1 || r.deletes() != 1 || r.userListeners() != 3) {
            throw new AssertionError("Unexpected event results: " + r);
        }
        return r;
    }

    public static void main(String[] args) {
        try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
            Result r = runDemo(db);
            System.out.println("== Event bus demo ==");
            System.out.println("insert events  : " + r.inserts());
            System.out.println("update events  : " + r.updates());
            System.out.println("delete events  : " + r.deletes());
            System.out.println("user listeners : " + r.userListeners());
        }
    }
}

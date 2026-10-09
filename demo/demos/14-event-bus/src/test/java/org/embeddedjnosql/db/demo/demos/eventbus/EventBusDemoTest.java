package org.embeddedjnosql.db.demo.demos.eventbus;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EventBusDemoTest {

    private EmbedJNoSQL db;

    @BeforeEach
    void setUp() { db = EmbedJNoSQL.inMemory(); }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    @Test
    @DisplayName("insert/update/delete each fire exactly one event")
    void runDemo() {
        var r = EventBusDemo.runDemo(db);
        assertEquals(1, r.inserts());
        assertEquals(1, r.updates());
        assertEquals(1, r.deletes());
        assertEquals(3, r.userListeners());
    }

    @Test
    @DisplayName("clear() removes user listeners; internal CDC listeners survive")
    void clearKeepsSystemListeners() {
        var bus = db.eventBus();
        AtomicInteger hits = new AtomicInteger();
        bus.on(org.embeddedjnosql.db.core.event.EventBus.EventType.AFTER_INSERT, e -> hits.incrementAndGet());
        db.documentCollection("eb_clear").insert(new org.embeddedjnosql.db.nosql.document.Document().id("a"));
        assertEquals(1, hits.get());
        bus.clear();
        db.documentCollection("eb_clear").insert(new org.embeddedjnosql.db.nosql.document.Document().id("b"));
        assertEquals(1, hits.get()); // no more user events
        assertEquals(0, bus.listenerCount());
    }
}

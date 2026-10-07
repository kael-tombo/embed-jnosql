package org.embeddedjnosql.db;

import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.document.Query;
import org.embeddedjnosql.db.core.event.EventBus;
import org.embeddedjnosql.db.core.event.EventBus.Event;
import org.embeddedjnosql.db.core.event.EventBus.EventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    private EmbedJNoSQL db;
    private EventBus eventBus;
    private DocumentCollection users;

    @BeforeEach
    void setUp() {
        db = EmbedJNoSQL.embed().build();
        eventBus = db.eventBus();
        users = db.documentCollection("users");
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void beforeInsertEvent() {
        var events = new ArrayList<Event>();
        eventBus.on(EventType.BEFORE_INSERT, e -> events.add(e));

        users.insert(Document.of("name", "Alice"));

        assertEquals(1, events.size());
        assertEquals(EventType.BEFORE_INSERT, events.get(0).type());
        assertEquals("users", events.get(0).collection());
    }

    @Test
    void afterInsertEvent() {
        var events = new ArrayList<Event>();
        eventBus.on(EventType.AFTER_INSERT, e -> events.add(e));

        var doc = users.insert(Document.of("name", "Alice"));

        assertEquals(1, events.size());
        assertEquals(EventType.AFTER_INSERT, events.get(0).type());
    }

    @Test
    void beforeDeleteEvent() {
        var events = new ArrayList<Event>();
        eventBus.on(EventType.BEFORE_DELETE, e -> events.add(e));

        var doc = users.insert(Document.of("name", "Alice"));
        users.deleteById(doc.id());

        assertEquals(1, events.size());
        assertEquals(EventType.BEFORE_DELETE, events.get(0).type());
    }

    @Test
    void multipleListeners() {
        var count1 = new AtomicInteger();
        var count2 = new AtomicInteger();
        eventBus.on(EventType.AFTER_INSERT, e -> count1.incrementAndGet());
        eventBus.on(EventType.AFTER_INSERT, e -> count2.incrementAndGet());

        users.insert(Document.of("name", "Alice"));

        assertEquals(1, count1.get());
        assertEquals(1, count2.get());
    }

    @Test
    void listenerCount() {
        assertEquals(0, eventBus.listenerCount());
        eventBus.on(EventType.AFTER_INSERT, e -> {});
        eventBus.on(EventType.BEFORE_INSERT, e -> {});
        assertEquals(2, eventBus.listenerCount());
    }

    @Test
    void clearListeners() {
        eventBus.on(EventType.AFTER_INSERT, e -> {});
        eventBus.on(EventType.BEFORE_INSERT, e -> {});
        assertEquals(2, eventBus.listenerCount());

        eventBus.clear();
        assertEquals(0, eventBus.listenerCount());
    }

    @Test
    void collectionCreatedEvent() {
        var events = new ArrayList<Event>();
        eventBus.on(EventType.COLLECTION_CREATED, e -> events.add(e));

        db.documentCollection("orders");

        assertEquals(1, events.size());
        assertEquals("orders", events.get(0).collection());
    }

    @Test
    void transactionCommitEvents() {
        var events = new ArrayList<Event>();
        eventBus.on(EventType.BEFORE_COMMIT, e -> events.add(e));
        eventBus.on(EventType.AFTER_COMMIT, e -> events.add(e));

        try (var tx = db.beginTransaction()) {
            var col = tx.documentCollection("users");
            col.insert(Document.of("name", "Alice"));
            tx.commit();
        }

        assertEquals(2, events.size());
        assertEquals(EventType.BEFORE_COMMIT, events.get(0).type());
        assertEquals(EventType.AFTER_COMMIT, events.get(1).type());
    }

    @Test
    void transactionRollbackEvents() {
        var events = new ArrayList<Event>();
        eventBus.on(EventType.BEFORE_ROLLBACK, e -> events.add(e));
        eventBus.on(EventType.AFTER_ROLLBACK, e -> events.add(e));

        try (var tx = db.beginTransaction()) {
            var col = tx.documentCollection("users");
            col.insert(Document.of("name", "Alice"));
            tx.rollback();
        }

        assertEquals(2, events.size());
        assertEquals(EventType.BEFORE_ROLLBACK, events.get(0).type());
        assertEquals(EventType.AFTER_ROLLBACK, events.get(1).type());
    }
}

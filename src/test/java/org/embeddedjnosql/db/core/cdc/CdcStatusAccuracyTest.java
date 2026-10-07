package org.embeddedjnosql.db.core.cdc;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression coverage for R-34: the CDC status must report the real subscriber count.
 *
 * <p>Before the fix {@code CDCManager.getStatus()} returned the event-log size for the
 * {@code subscribers} key, so the console's "Subscribers" KPI showed a non-zero value
 * with no subscriber connected and moved as events accumulated.</p>
 */
class CdcStatusAccuracyTest {

    @Test
    void subscriberCountIsIndependentOfEventLogSize() {
        var processor = new CDCProcessor();

        processor.onEvent(CDCEvent.insert("orders", "o1", "{\"id\":\"o1\"}"));
        processor.onEvent(CDCEvent.insert("orders", "o2", "{\"id\":\"o2\"}"));

        assertEquals(2, processor.getEventLog().size(), "events should be logged");
        assertEquals(0, processor.subscriberCount(),
                "no subscriber is connected, so the count must be 0 — not the event-log size");

        var subscriber = (java.util.function.Consumer<CDCEvent>) event -> { };
        processor.subscribe(subscriber);
        assertEquals(1, processor.subscriberCount());

        processor.unsubscribe(subscriber);
        assertEquals(0, processor.subscriberCount(),
                "an unsubscribed consumer must not be counted");
    }

    @Test
    void disablingDoesNotInventSubscribers() {
        var processor = new CDCProcessor();
        processor.onEvent(CDCEvent.insert("orders", "o1", "{}"));
        processor.disable();

        assertEquals(0, processor.subscriberCount());
        assertEquals(1, processor.getEventLog().size(), "existing events remain in the log");
    }
}

package org.embeddedjnosql.db.core.cdc;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class CDCProcessor {

    private final List<Consumer<CDCEvent>> subscribers;
    private final List<CDCEvent> eventLog;
    private final int maxLogSize;
    private boolean enabled;

    public CDCProcessor() {
        this(1000);
    }

    public CDCProcessor(int maxLogSize) {
        this.subscribers = new CopyOnWriteArrayList<>();
        this.eventLog = new CopyOnWriteArrayList<>();
        this.maxLogSize = maxLogSize;
        this.enabled = true;
    }

    public void onEvent(CDCEvent event) {
        if (!enabled) return;
        
        eventLog.add(event);
        if (eventLog.size() > maxLogSize) {
            eventLog.remove(0);
        }
        
        for (var subscriber : subscribers) {
            try {
                subscriber.accept(event);
            } catch (Exception e) {
                System.err.println("CDC subscriber error: " + e.getMessage());
            }
        }
    }

    public void subscribe(Consumer<CDCEvent> consumer) {
        subscribers.add(consumer);
    }

    public void unsubscribe(Consumer<CDCEvent> consumer) {
        subscribers.remove(consumer);
    }

    /**
     * Number of currently registered subscribers (connectors, SSE clients).
     *
     * <p>R-34: the admin console reported this KPI as the event-log size, so a
     * dashboard with no subscribers displayed a non-zero "Subscribers" count.</p>
     */
    public int subscriberCount() {
        return subscribers.size();
    }

    public List<CDCEvent> getEventLog() {
        return List.copyOf(eventLog);
    }

    public List<CDCEvent> getEventsSince(long timestamp) {
        return eventLog.stream()
            .filter(e -> e.timestamp() > timestamp)
            .toList();
    }

    public void clear() {
        eventLog.clear();
    }

    public void enable() {
        enabled = true;
    }

    public void disable() {
        enabled = false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void clearSubscribers() {
        subscribers.clear();
    }
}
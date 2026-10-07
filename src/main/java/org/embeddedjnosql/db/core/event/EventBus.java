package org.embeddedjnosql.db.core.event;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class EventBus {

    public enum EventType {
        BEFORE_INSERT, AFTER_INSERT,
        BEFORE_UPDATE, AFTER_UPDATE,
        BEFORE_DELETE, AFTER_DELETE,
        BEFORE_COMMIT, AFTER_COMMIT,
        BEFORE_ROLLBACK, AFTER_ROLLBACK,
        COLLECTION_CREATED, BUCKET_CREATED
    }

    public record Event(EventType type, String collection, Object data) {
    }

    private final List<Consumer<Event>> listeners = new CopyOnWriteArrayList<>();

    /**
     * Internal subsystem listeners (e.g. CDC change feed). Kept separate from
     * user listeners so that {@link #clear()} and {@link #listenerCount()}
     * only reflect user-registered handlers and user code cannot accidentally
     * detach internal subsystems.
     */
    private final List<Consumer<Event>> systemListeners = new CopyOnWriteArrayList<>();

    public void on(EventType type, Consumer<Event> handler) {
        listeners.add(event -> {
            if (event.type() == type) {
                handler.accept(event);
            }
        });
    }

    /**
     * Registers an internal subsystem listener. System listeners always run
     * (before user listeners) and are not affected by {@link #clear()}.
     */
    public void onSystem(EventType type, Consumer<Event> handler) {
        systemListeners.add(event -> {
            if (event.type() == type) {
                handler.accept(event);
            }
        });
    }

    public void emit(EventType type, String collection, Object data) {
        var event = new Event(type, collection, data);
        for (var listener : systemListeners) {
            try {
                listener.accept(event);
            } catch (Exception ignored) {
            }
        }
        for (var listener : listeners) {
            try {
                listener.accept(event);
            } catch (Exception ignored) {
            }
        }
    }

    public void emit(EventType type, String collection) {
        emit(type, collection, null);
    }

    public int listenerCount() {
        return listeners.size();
    }

    public void clear() {
        listeners.clear();
    }
}

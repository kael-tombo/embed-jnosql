package org.embeddedjnosql.db.core.cdc;

import org.embeddedjnosql.db.core.event.EventBus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class CDCManager {

    private final CDCProcessor processor;
    private final Map<String, FileCDCConnector> fileConnectors;
    private final Map<String, KafkaCDCConnector> kafkaConnectors;

    public CDCManager() {
        this.processor = new CDCProcessor();
        this.fileConnectors = new ConcurrentHashMap<>();
        this.kafkaConnectors = new ConcurrentHashMap<>();
    }

    public CDCProcessor processor() {
        return processor;
    }

    /**
     * Returns an {@link EventBus} listener that feeds document change events
     * into this manager, turning the previously write-path-orphaned CDC
     * subsystem into a live change feed. Register it once on the database's
     * event bus (EmbedJNoSQL does this automatically at construction):
     *
     * <pre>{@code
     * var listener = cdcManager.changeListener();
     * eventBus.on(EventBus.EventType.AFTER_INSERT, listener);
     * eventBus.on(EventBus.EventType.AFTER_UPDATE, listener);
     * eventBus.on(EventBus.EventType.AFTER_DELETE, listener);
     * }</pre>
     *
     * <p>Events carry {@code collection} and a data object: the stored
     * {@link org.embeddedjnosql.db.core.record.UnifiedRecord} for inserts/updates, or
     * the deleted document id ({@code String}) for deletes. Updates resolve
     * the previous value at write time, matching CDC semantics.
     */
    public Consumer<EventBus.Event> changeListener() {
        return event -> {
            switch (event.type()) {
                case AFTER_INSERT -> recordInsert(event.collection(), idOf(event.data()), jsonOf(event.data()));
                case AFTER_UPDATE -> recordUpdate(event.collection(), idOf(event.data()), null, jsonOf(event.data()));
                case AFTER_DELETE -> recordDelete(event.collection(), String.valueOf(event.data()), null);
                default -> { }
            }
        };
    }

    private static String idOf(Object data) {
        return data instanceof org.embeddedjnosql.db.core.record.UnifiedRecord r ? r.id() : String.valueOf(data.hashCode());
    }

    private static String jsonOf(Object data) {
        try {
            return data instanceof org.embeddedjnosql.db.core.record.UnifiedRecord r ? r.toJson() : String.valueOf(data);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public void recordInsert(String collection, String key, String value) {
        processor.onEvent(CDCEvent.insert(collection, key, value));
    }

    public void recordUpdate(String collection, String key, String oldValue, String newValue) {
        processor.onEvent(CDCEvent.update(collection, key, oldValue, newValue));
    }

    public void recordDelete(String collection, String key, String oldValue) {
        processor.onEvent(CDCEvent.delete(collection, key, oldValue));
    }

    public FileCDCConnector addFileConnector(String name, java.nio.file.Path outputDir) {
        var connector = new FileCDCConnector(processor, outputDir, name);
        connector.start();
        // Subscribe the connector to the event stream. Without this the connector was
        // live but starved: its queue had no producer, so a configured connector wrote
        // no events while the console reported it as connected.
        processor.subscribe(connector);
        fileConnectors.put(name, connector);
        return connector;
    }

    public void removeFileConnector(String name) {
        var connector = fileConnectors.remove(name);
        if (connector != null) {
            processor.unsubscribe(connector);
            connector.close();
        }
    }

    /** Whether a connector with this name is currently registered (file or kafka). */
    public boolean hasConnector(String name) {
        return fileConnectors.containsKey(name) || kafkaConnectors.containsKey(name);
    }

    public KafkaCDCConnector addKafkaConnector(String name, String bootstrapServers, String topic) {
        var connector = new KafkaCDCConnector(processor, bootstrapServers, topic);
        connector.start();
        kafkaConnectors.put(name, connector);
        return connector;
    }

    public void removeKafkaConnector(String name) {
        var connector = kafkaConnectors.remove(name);
        if (connector != null) {
            connector.close();
        }
    }

    public Map<String, Object> getStatus() {
        return Map.of(
            "enabled", processor.isEnabled(),
            "eventsInLog", processor.getEventLog().size(),
            "subscribers", processor.subscriberCount(),
            "fileConnectors", fileConnectors.keySet(),
            "kafkaConnectors", kafkaConnectors.keySet()
        );
    }

    public void close() {
        processor.disable();
        for (var connector : fileConnectors.values()) {
            connector.close();
        }
        fileConnectors.clear();
        
        for (var connector : kafkaConnectors.values()) {
            connector.close();
        }
        kafkaConnectors.clear();
        
        processor.clear();
    }
}
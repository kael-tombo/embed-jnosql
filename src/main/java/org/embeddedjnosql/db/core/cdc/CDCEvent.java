package org.embeddedjnosql.db.core.cdc;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CDCEvent(
    String eventId,
    EventType eventType,
    String collection,
    String key,
    String previousValue,
    String newValue,
    long timestamp,
    Map<String, Object> metadata
) {
    public enum EventType {
        INSERT,
        UPDATE,
        DELETE,
        TRUNCATE
    }

    public static CDCEvent insert(String collection, String key, String value) {
        return new CDCEvent(
            UUID.randomUUID().toString(),
            EventType.INSERT,
            collection,
            key,
            null,
            value,
            System.currentTimeMillis(),
            Map.of()
        );
    }

    public static CDCEvent update(String collection, String key, String oldValue, String newValue) {
        return new CDCEvent(
            UUID.randomUUID().toString(),
            EventType.UPDATE,
            collection,
            key,
            oldValue,
            newValue,
            System.currentTimeMillis(),
            Map.of()
        );
    }

    public static CDCEvent delete(String collection, String key, String oldValue) {
        return new CDCEvent(
            UUID.randomUUID().toString(),
            EventType.DELETE,
            collection,
            key,
            oldValue,
            null,
            System.currentTimeMillis(),
            Map.of()
        );
    }

    public CDCEvent withMetadata(String key, Object value) {
        var newMeta = new java.util.HashMap<>(metadata);
        newMeta.put(key, value);
        return new CDCEvent(eventId, eventType, collection, key, previousValue, newValue, timestamp, newMeta);
    }

    public String toJson() {
        return org.embeddedjnosql.db.core.util.JsonSerde.toJson(this);
    }
}
package org.embeddedjnosql.db.nosql.document;

import org.embeddedjnosql.db.core.record.RecordMetadata;
import org.embeddedjnosql.db.core.record.UnifiedRecord;
import org.embeddedjnosql.db.core.util.JsonSerde;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class Document implements UnifiedRecord {

    private String id;
    private Map<String, Object> fields;
    private Long expiresAt;
    private RecordMetadata metadata;

    public Document() {
        this.fields = new LinkedHashMap<>();
    }

    public static Document of(String key, Object value) {
        return new Document().add(key, value);
    }

    public static Document of(Map<String, Object> fields) {
        var doc = new Document();
        doc.fields = new LinkedHashMap<>(fields);
        return doc;
    }

    public static Document fromMap(String id, Map<String, Object> fields) {
        var doc = new Document();
        doc.id = id;
        doc.fields = new LinkedHashMap<>(fields);
        return doc;
    }

    public Document add(String key, Object value) {
        this.fields.put(key, value);
        return this;
    }

    public Document id(String id) {
        this.id = id;
        return this;
    }

    public String getId() {
        return id;
    }

    @Override
    public String id() {
        return id;
    }

    /**
     * Java 25: Returns the entity type name for routing in hybrid engines.
     */
    @Override
    public String entityName() {
        return get("_entity");
    }

    /**
     * Java 25: Returns the payload as raw bytes for zero-copy storage.
     */
    @Override
    public byte[] payload() {
        return toJson().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public RecordMetadata metadata() {
        if (metadata == null) {
            metadata = RecordMetadata.initial(RecordMetadata.RecordType.DOCUMENT);
        }
        return metadata;
    }

    @Override
    public UnifiedRecord withMetadata(RecordMetadata metadata) {
        var doc = new Document();
        doc.id = this.id;
        doc.fields = new LinkedHashMap<>(this.fields);
        doc.expiresAt = this.expiresAt;
        doc.metadata = metadata;
        return doc;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Map<String, Object> getFields() {
        return fields;
    }

    public void setFields(Map<String, Object> fields) {
        this.fields = fields;
    }

    public Long getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Long expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Document expiresAt(long epochMilli) {
        this.expiresAt = epochMilli;
        return this;
    }

    public Document expiresAt(Instant instant) {
        this.expiresAt = instant.toEpochMilli();
        return this;
    }

    public boolean isExpired() {
        if (expiresAt == null) return false;
        return Instant.now().toEpochMilli() > expiresAt;
    }

    public long ttlSeconds() {
        if (expiresAt == null) return -1;
        long remaining = (expiresAt - Instant.now().toEpochMilli()) / 1000;
        return Math.max(0, remaining);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) fields.get(key);
    }

    public Object getRaw(String key) {
        return fields.get(key);
    }

    public Map<String, Object> fields() {
        return Map.copyOf(fields);
    }

    public boolean has(String key) {
        return fields.containsKey(key);
    }

    /** Alias for {@link #has(String)} — Map-style compatibility. */
    public boolean containsKey(String key) {
        return fields.containsKey(key);
    }

    public Document remove(String key) {
        fields.remove(key);
        return this;
    }

    public int size() {
        return fields.size();
    }

    public static Document fromJson(String json) {
        return org.embeddedjnosql.db.core.util.JsonSerde.fromJson(json, Document.class);
    }

    public String toJson() {
        return org.embeddedjnosql.db.core.util.JsonSerde.toJson(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Document document = (Document) o;
        return Objects.equals(id, document.id) && Objects.equals(fields, document.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fields, id);
    }

    @Override
    public String toString() {
        return "Document{id='" + id + "', fields=" + fields + "}";
    }
}

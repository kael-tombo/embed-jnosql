package org.embeddedjnosql.db.core.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.embeddedjnosql.db.nosql.document.Document;
import java.util.Map;

public final class JsonSerde {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);

    private JsonSerde() {
    }

    public static String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new org.embeddedjnosql.db.core.exception.SerializationException("Failed to serialize to JSON", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T fromJson(String json, Class<T> type) {
        try {
            if (type == Document.class) {
                var node = MAPPER.readTree(json);
                var doc = new Document();
                if (node.has("id") && !node.get("id").isNull()) {
                    doc.setId(node.get("id").asText());
                }
                if (node.has("fields") && node.get("fields").isObject()) {
                    var fieldsNode = node.get("fields");
                    var fields = convertNode(fieldsNode);
                    doc.setFields(fields);
                } else {
                    var fields = new java.util.LinkedHashMap<String, Object>();
                    var it = node.fields();
                    while (it.hasNext()) {
                        var entry = it.next();
                        String key = entry.getKey();
                        if (!"id".equals(key) && !"expiresAt".equals(key)) {
                            fields.put(key, toJavaObject(entry.getValue()));
                        }
                    }
                    doc.setFields(fields);
                }
                // R-33: the custom Document path rebuilt only id + fields, so a persisted
                // TTL was silently dropped on every read (isExpired() stayed false and
                // documents never expired). Serialization already wrote expiresAt.
                if (node.has("expiresAt") && !node.get("expiresAt").isNull()) {
                    doc.setExpiresAt(node.get("expiresAt").asLong());
                }
                return (T) doc;
            }
            return MAPPER.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new org.embeddedjnosql.db.core.exception.SerializationException("Failed to deserialize from JSON: " + json, e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> convertNode(com.fasterxml.jackson.databind.JsonNode node) {
        var result = new java.util.LinkedHashMap<String, Object>();
        var fields = node.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            result.put(entry.getKey(), toJavaObject(entry.getValue()));
        }
        return result;
    }

    private static Object toJavaObject(com.fasterxml.jackson.databind.JsonNode node) {
        if (node.isTextual()) return node.asText();
        if (node.isInt()) return node.asInt();
        if (node.isLong()) return node.asLong();
        if (node.isDouble()) return node.asDouble();
        if (node.isBoolean()) return node.asBoolean();
        if (node.isNull()) return null;
        if (node.isObject()) {
            var map = new java.util.LinkedHashMap<String, Object>();
            var fields = node.fields();
            while (fields.hasNext()) {
                var e = fields.next();
                map.put(e.getKey(), toJavaObject(e.getValue()));
            }
            return map;
        }
        if (node.isArray()) {
            var list = new java.util.ArrayList<>();
            for (var item : node) {
                list.add(toJavaObject(item));
            }
            return list;
        }
        return node.asText();
    }
}

package org.embeddedjnosql.db.micronaut;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import jakarta.inject.Singleton;
import jakarta.persistence.LockModeType;
import jakarta.persistence.FlushModeType;
import jakarta.transaction.Transactional;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

/**
 * EmbedJNoSQL implementation of a document-oriented persistence manager for Micronaut.
 *
 * <p>This class deliberately does <em>not</em> implement
 * {@code jakarta.persistence.EntityManager} — the JPA interface has incompatible
 * generic overloads that cannot be implemented for a NoSQL engine without violating
 * the Java type-erasure rules. Instead, it exposes the EmbedJNoSQL document API
 * directly through typed helper methods.
 *
 * <p>Micronaut applications should inject this bean and use the document /
 * key-value / column-family APIs of the underlying {@link EmbedJNoSQL} instance via
 * {@link #getDatabase()}.
 */
@Singleton
public class EmbedDBEntityManager {

    private final EmbedJNoSQL db;
    private final String persistenceUnit;
    private boolean open = true;

    public EmbedDBEntityManager(EmbedJNoSQL db, String persistenceUnit) {
        this.db = db;
        this.persistenceUnit = persistenceUnit;
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────

    public boolean isOpen() { return open; }
    public void close() { this.open = false; }
    public EmbedJNoSQL getDatabase() { return db; }

    // ── CRUD ───────────────────────────────────────────────────────────────

    @Transactional
    public <T> void persist(T entity) {
        var collection = db.documentCollection(getCollectionName(entity.getClass()));
        var doc = toDocument(entity);
        var id = getIdValue(entity);
        if (id != null) {
            doc.id(String.valueOf(id));
        }
        collection.insert(doc);
    }

    @Transactional
    public <T> T merge(T entity) {
        var collection = db.documentCollection(getCollectionName(entity.getClass()));
        var doc = toDocument(entity);
        var id = getIdValue(entity);
        if (id != null) {
            doc.id(String.valueOf(id));
            collection.update(doc);
        } else {
            collection.insert(doc);
        }
        return entity;
    }

    @Transactional
    public <T> void remove(T entity) {
        var collection = db.documentCollection(getCollectionName(entity.getClass()));
        var id = getIdValue(entity);
        if (id != null) {
            collection.deleteById(String.valueOf(id));
        }
    }

    public <T> T find(Class<T> entityClass, Object primaryKey) {
        var collection = db.documentCollection(getCollectionName(entityClass));
        var doc = collection.findById(String.valueOf(primaryKey));
        return doc == null ? null : toEntity(doc, entityClass);
    }

    public <T> T getReference(Class<T> entityClass, Object primaryKey) {
        return find(entityClass, primaryKey);
    }

    public boolean contains(Object entity) {
        try {
            var id = getIdValue(entity);
            if (id == null) return false;
            var collection = db.documentCollection(getCollectionName(entity.getClass()));
            return collection.findById(String.valueOf(id)) != null;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Convenience helpers ────────────────────────────────────────────────

    public <T> List<T> findAll(Class<T> entityClass) {
        var collection = db.documentCollection(getCollectionName(entityClass));
        return collection.findAll().stream()
                .map(doc -> toEntity(doc, entityClass))
                .collect(Collectors.toList());
    }

    public <T> List<T> findByField(Class<T> entityClass, String fieldName, Object value) {
        var collection = db.documentCollection(getCollectionName(entityClass));
        return collection.findAll().stream()
                .filter(doc -> Objects.equals(value, doc.get(fieldName)))
                .map(doc -> toEntity(doc, entityClass))
                .collect(Collectors.toList());
    }

    public void flush() {}
    public void clear() {}
    public void joinTransaction() {}
    public FlushModeType getFlushMode() { return FlushModeType.AUTO; }
    public void setFlushMode(FlushModeType mode) {}
    public LockModeType getLockMode(Object entity) { return LockModeType.NONE; }

    // ── Internal helpers ───────────────────────────────────────────────────

    private String getCollectionName(Class<?> entityClass) {
        var table = entityClass.getAnnotation(jakarta.persistence.Table.class);
        if (table != null && !table.name().isEmpty()) return table.name();
        var entity = entityClass.getAnnotation(jakarta.persistence.Entity.class);
        if (entity != null && !entity.name().isEmpty()) return entity.name();
        return entityClass.getSimpleName().toLowerCase();
    }

    private Object getIdValue(Object entity) {
        for (var field : entity.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            if (field.isAnnotationPresent(jakarta.persistence.Id.class)) {
                try { return field.get(entity); } catch (Exception e) { return null; }
            }
        }
        return null;
    }

    private <T> Document toDocument(T entity) {
        var doc = new Document();
        for (var field : entity.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            try { doc.add(getColumnName(field), field.get(entity)); }
            catch (Exception ignored) {}
        }
        return doc;
    }

    private <T> T toEntity(Document doc, Class<T> entityClass) {
        try {
            var entity = entityClass.getDeclaredConstructor().newInstance();
            for (var field : entityClass.getDeclaredFields()) {
                field.setAccessible(true);
                var col = getColumnName(field);
                if (doc.containsKey(col)) {
                    var val = doc.get(col);
                    if (val != null) field.set(entity, convertValue(val, field.getType()));
                }
            }
            var id = doc.getId();
            if (id != null) {
                for (var field : entityClass.getDeclaredFields()) {
                    if (field.isAnnotationPresent(jakarta.persistence.Id.class)) {
                        field.setAccessible(true);
                        field.set(entity, convertValue(id, field.getType()));
                        break;
                    }
                }
            }
            return entity;
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert document to entity", e);
        }
    }

    private String getColumnName(Field field) {
        var col = field.getAnnotation(jakarta.persistence.Column.class);
        if (col != null && !col.name().isEmpty()) return col.name();
        return field.getName();
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (targetType.isInstance(value)) return value;
        if (targetType == String.class) return String.valueOf(value);
        if ((targetType == int.class || targetType == Integer.class) && value instanceof Number)
            return ((Number) value).intValue();
        if ((targetType == long.class || targetType == Long.class) && value instanceof Number)
            return ((Number) value).longValue();
        if (targetType == boolean.class || targetType == Boolean.class)
            return Boolean.parseBoolean(String.valueOf(value));
        return value;
    }
}
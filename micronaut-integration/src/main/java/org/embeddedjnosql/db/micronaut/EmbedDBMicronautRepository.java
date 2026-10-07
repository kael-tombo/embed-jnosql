package org.embeddedjnosql.db.micronaut;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;

import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.persistence.Id;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;


@Singleton
public class EmbedJNoSQLMicronautRepository<T, ID> {

    private final Class<T> entityClass;
    private final Class<ID> idClass;
    private final EmbedJNoSQL embeddedjnosqlDB;
    private final DocumentCollection collection;

    public EmbedJNoSQLMicronautRepository(EmbedJNoSQL embeddedjnosqlDB, Class<T> entityClass, Class<ID> idClass) {
        this.embeddedjnosqlDB = embeddedjnosqlDB;
        this.entityClass = entityClass;
        this.idClass = idClass;
        this.collection = embeddedjnosqlDB.documentCollection(getEntityName());
    }

    @Transactional
    public <S extends T> S save(S entity) {
        var id = getIdValue(entity);
        if (id == null) {
            id = generateId();
            setIdValue(entity, id);
        }

        var doc = toDocument(entity);
        doc.setId(String.valueOf(id));
        collection.insert(doc);
        return entity;
    }

    @Transactional
    public <S extends T> Iterable<S> saveAll(Iterable<S> entities) {
        var results = new ArrayList<S>();
        for (S entity : entities) {
            results.add(save(entity));
        }
        return results;
    }

    @Transactional
    public Optional<T> findById(ID id) {
        var doc = collection.findById(String.valueOf(id));
        return doc != null ? Optional.of(toEntity(doc)) : Optional.empty();
    }

    public boolean existsById(ID id) {
        return collection.findById(String.valueOf(id)) != null;
    }

    public List<T> findAll() {
        var docs = collection.findAll();
        return docs.stream().map(this::toEntity).collect(Collectors.toList());
    }

    public long count() {
        return collection.count();
    }

    @Transactional
    public void deleteById(ID id) {
        collection.deleteById(String.valueOf(id));
    }

    @Transactional
    public void delete(T entity) {
        var id = getIdValue(entity);
        if (id != null) {
            collection.deleteById(String.valueOf(id));
        }
    }

    public List<T> findByField(String fieldName, Object value) {
        return collection.findAll().stream()
            .filter(doc -> Objects.equals(value, doc.get(fieldName)))
            .map(this::toEntity)
            .collect(Collectors.toList());
    }

    public <C extends Comparable<C>> List<T> findByFieldLessThan(String fieldName, C value) {
        return collection.findAll().stream()
            .filter(doc -> {
                var fieldValue = doc.get(fieldName);
                if (fieldValue instanceof Comparable) {
                    try {
                        @SuppressWarnings("unchecked")
                        var cmp = (Comparable<C>) fieldValue;
                        return cmp.compareTo(value) < 0;
                    } catch (Exception ignored) {
                    }
                }
                return false;
            })
            .map(this::toEntity)
            .collect(Collectors.toList());
    }

    public <C extends Comparable<C>> List<T> findByFieldGreaterThan(String fieldName, C value) {
        return collection.findAll().stream()
            .filter(doc -> {
                var fieldValue = doc.get(fieldName);
                if (fieldValue instanceof Comparable) {
                    try {
                        @SuppressWarnings("unchecked")
                        var cmp = (Comparable<C>) fieldValue;
                        return cmp.compareTo(value) > 0;
                    } catch (Exception ignored) {
                    }
                }
                return false;
            })
            .map(this::toEntity)
            .collect(Collectors.toList());
    }

    public List<T> findByFieldContaining(String fieldName, String value) {
        return collection.findAll().stream()
            .filter(doc -> {
                var fieldValue = doc.get(fieldName);
                return fieldValue != null && fieldValue.toString().contains(value);
            })
            .map(this::toEntity)
            .collect(Collectors.toList());
    }

    private String getEntityName() {
        var jpaTable = entityClass.getAnnotation(jakarta.persistence.Table.class);
        if (jpaTable != null && !jpaTable.name().isEmpty()) {
            return jpaTable.name();
        }

        var jpaEntity = entityClass.getAnnotation(jakarta.persistence.Entity.class);
        if (jpaEntity != null && !jpaEntity.name().isEmpty()) {
            return jpaEntity.name();
        }

        return entityClass.getSimpleName().toLowerCase();
    }

    private ID getIdValue(T entity) {
        for (Field field : entityClass.getDeclaredFields()) {
            field.setAccessible(true);

            if (field.isAnnotationPresent(jakarta.persistence.Id.class) ||
                
                field.isAnnotationPresent(Id.class)) {
                try {
                    @SuppressWarnings("unchecked")
                    ID id = (ID) field.get(entity);
                    return id;
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    private void setIdValue(T entity, ID id) {
        for (Field field : entityClass.getDeclaredFields()) {
            field.setAccessible(true);

            if (field.isAnnotationPresent(jakarta.persistence.Id.class) ||
                
                field.isAnnotationPresent(Id.class)) {
                try {
                    field.set(entity, id);
                    return;
                } catch (Exception ignored) {
                }
            }
        }
    }

    private ID generateId() {
        try {
            if (idClass == String.class) {
                return idClass.cast(UUID.randomUUID().toString());
            }
            if (idClass == long.class || idClass == Long.class) {
                return idClass.cast(System.nanoTime());
            }
            return idClass.cast(UUID.randomUUID().toString());
        } catch (Exception e) {
            return idClass.cast(UUID.randomUUID().toString());
        }
    }

    private Document toDocument(T entity) {
        var doc = new Document();

        for (Field field : entityClass.getDeclaredFields()) {
            field.setAccessible(true);

            var columnName = getColumnName(field);

            try {
                var value = field.get(entity);
                doc.add(columnName, value);
            } catch (Exception ignored) {
            }
        }

        return doc;
    }

    private T toEntity(Document doc) {
        try {
            var entity = entityClass.getDeclaredConstructor().newInstance();
            var id = doc.getId();

            for (Field field : entityClass.getDeclaredFields()) {
                field.setAccessible(true);

                var columnName = getColumnName(field);

                if (doc.containsKey(columnName)) {
                    var value = doc.get(columnName);
                    if (value != null) {
                        field.set(entity, convertValue(value, field.getType()));
                    }
                }
            }

            if (id != null) {
                for (Field field : entityClass.getDeclaredFields()) {
                    if (field.isAnnotationPresent(jakarta.persistence.Id.class) ||
                        
                        field.isAnnotationPresent(Id.class)) {
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
        var jpaCol = field.getAnnotation(jakarta.persistence.Column.class);
        if (jpaCol != null && !jpaCol.name().isEmpty()) {
            return jpaCol.name();
        }

        return field.getName();
    }

    private Object convertValue(Object value, Class<?> targetType) {
        if (targetType.isInstance(value)) {
            return value;
        }

        if (targetType == String.class) {
            return String.valueOf(value);
        }

        if ((targetType == int.class || targetType == Integer.class) && value instanceof Number) {
            return ((Number) value).intValue();
        }

        if ((targetType == long.class || targetType == Long.class) && value instanceof Number) {
            return ((Number) value).longValue();
        }

        if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(String.valueOf(value));
        }

        return value;
    }
}

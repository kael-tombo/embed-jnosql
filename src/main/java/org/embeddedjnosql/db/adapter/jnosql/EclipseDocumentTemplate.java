package org.embeddedjnosql.db.adapter.jnosql;

import jakarta.nosql.QueryMapper;
import jakarta.nosql.Template;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Eclipse JNoSQL DocumentTemplate implementation aligned with both the EmbedJNoSQL
 * {@link DocumentTemplate} interface and the standard {@link jakarta.nosql.Template} specification.
 */
public class EclipseDocumentTemplate implements DocumentTemplate, Template {

    private final DocumentCollection collection;

    private EclipseDocumentTemplate(DocumentCollection collection) {
        this.collection = collection;
    }

    public static EclipseDocumentTemplate of(DocumentCollection collection) {
        return new EclipseDocumentTemplate(collection);
    }

    // -------------------------------------------------------------------------
    // Insert operations
    // -------------------------------------------------------------------------

    @Override
    public <T> T insert(T entity) {
        Document doc = EntityMapper.toDocument(entity);
        collection.insert(doc);
        return entity;
    }

    @Override
    public <T> T insert(T entity, long ttlSeconds) {
        Document doc = EntityMapper.toDocument(entity);
        collection.insert(doc, ttlSeconds);
        return entity;
    }

    @Override
    public <T> T insert(T entity, Duration ttl) {
        long seconds = ttl != null ? ttl.toSeconds() : 0L;
        return insert(entity, seconds);
    }

    @Override
    public <T> Iterable<T> insert(Iterable<T> entities) {
        return insertAll(entities);
    }

    @Override
    public <T> Iterable<T> insert(Iterable<T> entities, Duration ttl) {
        long seconds = ttl != null ? ttl.toSeconds() : 0L;
        List<Document> docs = new ArrayList<>();
        for (T entity : entities) {
            docs.add(EntityMapper.toDocument(entity));
        }
        for (Document d : docs) {
            collection.insert(d, seconds);
        }
        return entities;
    }

    @Override
    public <T> Iterable<T> insertAll(Iterable<T> entities) {
        List<Document> docs = new ArrayList<>();
        for (T entity : entities) {
            docs.add(EntityMapper.toDocument(entity));
        }
        collection.insertAll(docs);
        return entities;
    }

    // -------------------------------------------------------------------------
    // Update operations
    // -------------------------------------------------------------------------

    @Override
    public <T> T update(T entity) {
        Document doc = EntityMapper.toDocument(entity, true);
        collection.update(doc);
        return entity;
    }

    @Override
    public <T> Iterable<T> update(Iterable<T> entities) {
        for (T entity : entities) {
            update(entity);
        }
        return entities;
    }

    // -------------------------------------------------------------------------
    // Delete operations
    // -------------------------------------------------------------------------

    @Override
    public <T> void delete(T entity) {
        Object id = EntityMapper.getIdValue(entity);
        if (id != null) {
            collection.deleteById(id.toString());
        }
    }

    @Override
    public <T, K> void delete(Class<T> entityClass, K id) {
        deleteById(entityClass, id);
    }

    @Override
    public <T, ID> void deleteById(Class<T> entityClass, ID id) {
        if (id != null) {
            collection.deleteById(id.toString());
        }
    }

    @Override
    public <T> QueryMapper.MapperDeleteFrom delete(Class<T> entityClass) {
        return EclipseQueryMapper.delete(entityClass, collection);
    }

    // -------------------------------------------------------------------------
    // Find operations
    // -------------------------------------------------------------------------

    @Override
    public <T, ID> Optional<T> find(Class<T> entityClass, ID id) {
        if (id == null) return Optional.empty();
        Document doc = collection.findById(id.toString());
        if (doc == null) return Optional.empty();
        return Optional.of(EntityMapper.fromDocument(doc, entityClass));
    }

    @Override
    public <T, ID> boolean existsById(Class<T> entityClass, ID id) {
        if (id == null) return false;
        return collection.findById(id.toString()) != null;
    }

    @Override
    public <T> List<T> findAll(Class<T> entityClass) {
        String entityName = EntityMapper.getCollectionName(entityClass);
        return collection.findAll().stream()
                .filter(doc -> {
                    Object val = doc.getRaw("_entity");
                    return val == null || entityName.equalsIgnoreCase(val.toString());
                })
                .map(doc -> EntityMapper.fromDocument(doc, entityClass))
                .collect(Collectors.toList());
    }

    @Override
    public <T> List<T> find(Class<T> entityClass, Function<Document, Boolean> filter) {
        return collection.findAll().stream()
                .filter(d -> filter.apply(d))
                .map(doc -> EntityMapper.fromDocument(doc, entityClass))
                .collect(Collectors.toList());
    }

    @Override
    public <T> List<T> find(Class<T> entityClass, org.embeddedjnosql.db.nosql.document.Query query) {
        return collection.find(query).stream()
                .map(doc -> EntityMapper.fromDocument(doc, entityClass))
                .collect(Collectors.toList());
    }

    public <T> List<T> find(Class<T> entityClass, Predicate<Document> predicate) {
        return collection.findAll().stream()
                .filter(predicate)
                .map(doc -> EntityMapper.fromDocument(doc, entityClass))
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // QueryMapper select
    // -------------------------------------------------------------------------

    @Override
    public <T> QueryMapper.MapperFrom select(Class<T> entityClass) {
        return EclipseQueryMapper.select(entityClass, collection);
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    @Override
    public <T> long count(Class<T> entityClass) {
        String entityName = EntityMapper.getCollectionName(entityClass);
        return collection.findAll().stream()
                .filter(doc -> {
                    Object val = doc.getRaw("_entity");
                    return val == null || entityName.equalsIgnoreCase(val.toString());
                })
                .count();
    }

    @Override
    public String getCollectionName() {
        return collection.name();
    }

    @Override
    public DocumentCollection getCollection() {
        return collection;
    }
}

package org.embeddedjnosql.db.adapter.jnosql;

import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.core.util.JsonSerde;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class EclipseDocumentTemplate implements DocumentTemplate {

    private final DocumentCollection collection;

    private EclipseDocumentTemplate(DocumentCollection collection) {
        this.collection = collection;
    }

    public static EclipseDocumentTemplate of(DocumentCollection collection) {
        return new EclipseDocumentTemplate(collection);
    }

    public <T> T insert(T entity) {
        Document doc = EntityMapper.toDocument(entity);
        collection.insert(doc);
        return entity;
    }

    public <T> T insert(T entity, long ttl) {
        Document doc = EntityMapper.toDocument(entity);
        collection.insert(doc, ttl);
        return entity;
    }

    public <T> Iterable<T> insertAll(Iterable<T> entities) {
        List<Document> docs = new java.util.ArrayList<>();
        for (T entity : entities) {
            docs.add(EntityMapper.toDocument(entity));
        }
        collection.insertAll(docs);
        return entities;
    }

    public <T> T update(T entity) {
        Document doc = EntityMapper.toDocument(entity, true);
        collection.update(doc);
        return entity;
    }

    public <T> void delete(T entity) {
        Object id = EntityMapper.getIdValue(entity);
        if (id != null) {
            collection.deleteById(id.toString());
        }
    }

    public <T, ID> Optional<T> find(Class<T> entityClass, ID id) {
        Document doc = collection.findById(id.toString());
        if (doc == null) return Optional.empty();
        return Optional.of(EntityMapper.fromDocument(doc, entityClass));
    }

    @Override
    public <T, ID> boolean existsById(Class<T> entityClass, ID id) {
        return collection.findById(id.toString()) != null;
    }

    public <T> List<T> findAll(Class<T> entityClass) {
        return collection.findAll().stream()
                .map(doc -> EntityMapper.fromDocument(doc, entityClass))
                .collect(Collectors.toList());
    }

    public <T> List<T> find(Class<T> entityClass, Function<Document, Boolean> filter) {
        return collection.findAll().stream()
                .filter(d -> filter.apply(d))
                .map(doc -> EntityMapper.fromDocument(doc, entityClass))
                .collect(Collectors.toList());
    }

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

    public <T> long count(Class<T> entityClass) {
        String entityName = EntityMapper.getCollectionName(entityClass);
        return collection.findAll().stream()
                .filter(doc -> {
                    Object val = doc.getRaw("_entity");
                    return val == null || entityName.equalsIgnoreCase(val.toString());
                })
                .count();
    }

    public <T, ID> void deleteById(Class<T> entityClass, ID id) {
        collection.deleteById(id.toString());
    }

    public String getCollectionName() {
        return collection.name();
    }

    public DocumentCollection getCollection() {
        return collection;
    }
}

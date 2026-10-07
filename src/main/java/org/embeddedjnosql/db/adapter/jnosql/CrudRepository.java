package org.embeddedjnosql.db.adapter.jnosql;

import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.document.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class CrudRepository<T, ID> {

    private final DocumentCollection collection;
    private final Class<T> entityClass;

    public CrudRepository(DocumentCollection collection, Class<T> entityClass, Class<ID> idClass) {
        this.collection = collection;
        this.entityClass = entityClass;
    }

    public void save(T entity) {
        Document doc = EntityMapper.toDocument(entity);
        Object idValue = EntityMapper.getIdValue(entity);
        if (idValue == null) {
            doc.id(UUID.randomUUID().toString());
        }
        collection.insert(doc);
    }

    public void saveAll(Iterable<T> entities) {
        List<Document> docs = new ArrayList<>();
        for (T entity : entities) {
            Document doc = EntityMapper.toDocument(entity);
            Object idValue = EntityMapper.getIdValue(entity);
            if (idValue == null) {
                doc.id(UUID.randomUUID().toString());
            }
            docs.add(doc);
        }
        collection.insertAll(docs);
    }

    public Optional<T> findById(ID id) {
        Document doc = collection.findById(id.toString());
        if (doc == null) return Optional.empty();
        return Optional.of(EntityMapper.fromDocument(doc, entityClass));
    }

    public List<T> findAll() {
        return collection.findAll().stream()
                .map(doc -> EntityMapper.fromDocument(doc, entityClass))
                .collect(Collectors.toList());
    }

    public void deleteById(ID id) {
        collection.deleteById(id.toString());
    }

    public void delete(T entity) {
        Object idValue = EntityMapper.getIdValue(entity);
        if (idValue != null) {
            deleteById((ID) idValue);
        }
    }

    public long count() {
        return collection.count();
    }

    public boolean existsById(ID id) {
        return collection.exists(id.toString());
    }

    public List<T> findBy(String field, Object value) {
        var results = collection.find(Query.eq(field, value));
        return results.stream()
                .map(doc -> EntityMapper.fromDocument(doc, entityClass))
                .collect(Collectors.toList());
    }

    public Optional<T> findOneBy(String field, Object value) {
        List<T> results = findBy(field, value);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public List<T> query(String queryString) {
        var results = collection.find(Query.fromQuery(queryString));
        return results.stream()
                .map(doc -> EntityMapper.fromDocument(doc, entityClass))
                .collect(Collectors.toList());
    }

    public static <T, ID> CrudRepository<T, ID> of(DocumentCollection collection, Class<T> entityClass, Class<ID> idClass) {
        return new CrudRepository<>(collection, entityClass, idClass);
    }
}

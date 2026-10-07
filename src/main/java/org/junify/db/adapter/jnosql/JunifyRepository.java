package org.junify.db.adapter.jnosql;

import org.junify.db.JunifyDB;

import java.util.*;

/**
 * Standard repository implementation for Eclipse JNoSQL entities.
 *
 * @param <T>  Entity type
 * @param <ID> Primary key identifier type
 */
public class JunifyRepository<T, ID> {

    protected final Class<T> entityClass;
    protected final EclipseDocumentTemplate template;
    protected final JunifyDB db;

    public JunifyRepository(Class<T> entityClass, JunifyDB db) {
        this.entityClass = entityClass;
        this.db = db;
        String colName = EntityMapper.getCollectionName(entityClass);
        this.template = EclipseDocumentTemplate.of(db.documentCollection(colName));
    }

    public JunifyDB getDb() {
        return db;
    }

    public static <T, ID> JunifyRepository<T, ID> of(Class<T> entityClass, JunifyDB db) {
        return new JunifyRepository<>(entityClass, db);
    }

    @SuppressWarnings("unchecked")
    public T save(T entity) {
        Object id = EntityMapper.getIdValue(entity);
        if (id != null && existsById((ID) id)) {
            return template.update(entity);
        }
        return template.insert(entity);
    }

    public Iterable<T> saveAll(Iterable<T> entities) {
        List<T> result = new ArrayList<>();
        for (T entity : entities) {
            result.add(save(entity));
        }
        return result;
    }

    public Optional<T> findById(ID id) {
        return template.find(entityClass, id);
    }

    public boolean existsById(ID id) {
        return template.existsById(entityClass, id);
    }

    public List<T> findAll() {
        return template.findAll(entityClass);
    }

    public long count() {
        return template.count(entityClass);
    }

    public void deleteById(ID id) {
        template.deleteById(entityClass, id);
    }

    public void delete(T entity) {
        template.delete(entity);
    }

    public void deleteAll() {
        for (T entity : findAll()) {
            delete(entity);
        }
    }

    /**
     * Finds every entity whose {@code fieldName} document field equals {@code value}.
     * Resolved through the native document predicate API — no text query language.
     */
    public List<T> findBy(String fieldName, Object value) {
        return findByQuery(org.junify.db.nosql.document.Query.eq(fieldName, value));
    }

    /**
     * Runs a native document predicate and maps the matching documents to entities.
     * Subclasses use this to express repository finders without a query language.
     */
    protected List<T> findByQuery(org.junify.db.nosql.document.Query query) {
        String colName = EntityMapper.getCollectionName(entityClass);
        return db.documentCollection(colName)
                .find(query)
                .stream()
                .map(doc -> EntityMapper.fromDocument(doc, entityClass))
                .collect(java.util.stream.Collectors.toList());
    }

    public EclipseDocumentTemplate getTemplate() {
        return template;
    }

    public Class<T> getEntityClass() {
        return entityClass;
    }
}

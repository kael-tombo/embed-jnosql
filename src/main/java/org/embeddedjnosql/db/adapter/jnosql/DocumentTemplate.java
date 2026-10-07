package org.embeddedjnosql.db.adapter.jnosql;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.embeddedjnosql.db.nosql.document.Document;

/**
 * DocumentTemplate interface — Eclipse JNoSQL spec alignment.
 * Provides type-safe CRUD operations for entity-to-document mapping.
 *
 * Usage:
 * <pre>
 * {@literal @}Inject
 * DocumentTemplate template;
 *
 * User user = template.insert(new User("Alice", 30));
 * Optional&lt;User&gt; found = template.find(User.class, "id");
 * </pre>
 */
public interface DocumentTemplate {

    /**
     * Insert an entity. Auto-generates ID if not set.
     */
    <T> T insert(T entity);

    /**
     * Insert an entity with TTL (time-to-live) in seconds.
     */
    <T> T insert(T entity, long ttlSeconds);

    /**
     * Insert multiple entities in a batch.
     */
    <T> Iterable<T> insertAll(Iterable<T> entities);

    /**
     * Update an existing entity. Throws if entity not found.
     */
    <T> T update(T entity);

    /**
     * Delete an entity by its ID value.
     */
    <T> void delete(T entity);

    /**
     * Delete an entity by class and ID.
     */
    <T, ID> void deleteById(Class<T> entityClass, ID id);

    /**
     * Find an entity by class and ID.
     */
    <T, ID> Optional<T> find(Class<T> entityClass, ID id);

    /**
     * Check if an entity exists by ID.
     */
    <T, ID> boolean existsById(Class<T> entityClass, ID id);

    /**
     * Find all entities of a given type.
     */
    <T> List<T> findAll(Class<T> entityClass);

    /**
     * Find entities using a predicate function.
     */
    <T> List<T> find(Class<T> entityClass, Function<Document, Boolean> filter);

    /**
     * Find entities using a Query object.
     */
    <T> List<T> find(Class<T> entityClass, org.embeddedjnosql.db.nosql.document.Query query);

    /**
     * Count all entities of a given type.
     */
    <T> long count(Class<T> entityClass);

    /**
     * Get the underlying collection name.
     */
    String getCollectionName();

    /**
     * Get the underlying DocumentCollection.
     */
    org.embeddedjnosql.db.nosql.document.DocumentCollection getCollection();
}

package org.embeddedjnosql.db.micronaut;

import org.embeddedjnosql.db.EmbedJNoSQL;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Factory that creates typed {@link EmbedDBMicronautRepository} instances
 * for a given entity class. Inject this bean in Micronaut components.
 */
@Singleton
public class EmbedDBRepositoryFactory {

    @Inject
    EmbedJNoSQL embeddedjnosqlDB;

    public <T, ID> EmbedDBMicronautRepository<T, ID> createRepository(
            Class<T> entityClass, Class<ID> idClass) {
        return new EmbedDBMicronautRepository<>(embeddedjnosqlDB, entityClass, idClass);
    }
}

package org.embeddedjnosql.db.micronaut;

import org.embeddedjnosql.db.EmbedJNoSQL;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Factory that creates typed {@link EmbedJNoSQLMicronautRepository} instances
 * for a given entity class. Inject this bean in Micronaut components.
 */
@Singleton
public class EmbedJNoSQLRepositoryFactory {

    @Inject
    EmbedJNoSQL embeddedjnosqlDB;

    public <T, ID> EmbedJNoSQLMicronautRepository<T, ID> createRepository(
            Class<T> entityClass, Class<ID> idClass) {
        return new EmbedJNoSQLMicronautRepository<>(embeddedjnosqlDB, entityClass, idClass);
    }
}

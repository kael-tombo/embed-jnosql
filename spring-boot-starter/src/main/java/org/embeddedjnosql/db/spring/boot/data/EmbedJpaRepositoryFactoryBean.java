package org.embeddedjnosql.db.spring.boot.data;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.core.support.RepositoryFactoryBeanSupport;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

/**
 * Spring Data {@code FactoryBean} for an embed-jnosql-backed repository interface — the exact
 * role {@code JpaRepositoryFactoryBean} plays for relational stores. Declared per repository
 * interface with {@code @EnableEmbedJpaRepositories} (the embed-jnosql counterpart of
 * {@code @EnableJpaRepositories}, see {@link EmbedJpaRepositoryRegistrar}).
 *
 * @param <T>  repository interface type (extends {@link Repository})
 * @param <S>  entity type
 * @param <ID> id type
 */
public class EmbedJpaRepositoryFactoryBean<T extends Repository<S, ID>, S, ID>
        extends RepositoryFactoryBeanSupport<T, S, ID> {

    private final EmbedJpaRepositoryFactory factory;

    public EmbedJpaRepositoryFactoryBean(Class<? extends T> repositoryInterface, EmbedJNoSQL db) {
        super(repositoryInterface);
        this.factory = new EmbedJpaRepositoryFactory(db);
        setRepositoryBaseClass(EmbedJpaBaseRepository.class);
    }

    @Override
    protected RepositoryFactorySupport createRepositoryFactory() {
        return factory;
    }
}

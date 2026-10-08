package org.embeddedjnosql.db.spring.boot.data;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;

/**
 * Spring Data {@link RepositoryFactorySupport} for the embed-jnosql document engine. Creates
 * {@link EmbedJpaBaseRepository} targets and resolves derived queries through
 * {@link EmbedJpaQueryLookupStrategy} — the same factory layer
 * {@code JpaRepositoryFactory} provides for relational stores.
 */
public class EmbedJpaRepositoryFactory extends RepositoryFactorySupport {

    private final EmbedJNoSQL db;

    public EmbedJpaRepositoryFactory(EmbedJNoSQL db) {
        this.db = db;
    }

    @Override
    public <T, ID> EmbedJpaEntityInformation<T, ID> getEntityInformation(Class<T> domainClass) {
        return new EmbedJpaEntityInformation<>(domainClass);
    }

    @Override
    protected Object getTargetRepository(RepositoryInformation information) {
        return new EmbedJpaBaseRepository<>(db, information.getDomainType());
    }

    @Override
    protected Class<?> getRepositoryBaseClass(RepositoryMetadata metadata) {
        return EmbedJpaBaseRepository.class;
    }

    @Override
    protected java.util.Optional<org.springframework.data.repository.query.QueryLookupStrategy> getQueryLookupStrategy(
            org.springframework.data.repository.query.QueryLookupStrategy.Key key,
            org.springframework.data.repository.query.QueryMethodEvaluationContextProvider provider) {
        // NULL key = no explicit configuration: Spring Data's default CREATE_IF_NOT_FOUND
        EmbedJpaQueryLookupStrategy.Strategy strategy = key == null
                ? EmbedJpaQueryLookupStrategy.Strategy.CREATE_IF_NOT_FOUND
                : switch (key) {
                    case CREATE -> EmbedJpaQueryLookupStrategy.Strategy.CREATE;
                    case USE_DECLARED_QUERY -> EmbedJpaQueryLookupStrategy.Strategy.USE_DECLARED_QUERY;
                    case CREATE_IF_NOT_FOUND -> EmbedJpaQueryLookupStrategy.Strategy.CREATE_IF_NOT_FOUND;
                };
        return java.util.Optional.of(
                new EmbedJpaQueryLookupStrategy(new EmbedJpaQueryLookupStrategy.RepositoryResolver() {
                    @Override
                    public EmbedJpaBaseRepository<?, ?> forDomain(Class<?> domainType) {
                        return repositoryForQueries(domainType);
                    }
                }, strategy, NamedQueriesHolder.HOLDER));
    }

    /**
     * Base repository for query execution, cached per domain type so every derived query runs
     * against the same collection/entity mapping as the target repository of that interface
     * (a shared Object-typed instance would execute against a nonexistent "object" collection).
     */
    private final java.util.Map<Class<?>, EmbedJpaBaseRepository<?, ?>> queryRepos =
            new java.util.concurrent.ConcurrentHashMap<>();

    EmbedJpaBaseRepository<?, ?> repositoryForQueries(Class<?> domainType) {
        return queryRepos.computeIfAbsent(domainType, d -> new EmbedJpaBaseRepository<>(db, d));
    }

    /** Named-queries holder: no properties-backed named queries wired yet. */
    private static final class NamedQueriesHolder {
        static final org.springframework.data.repository.core.NamedQueries HOLDER =
                new org.springframework.data.repository.core.NamedQueries() {
                    @Override public boolean hasQuery(String name) { return false; }
                    @Override public String getQuery(String name) { return null; }
                };
    }
}

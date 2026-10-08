package org.embeddedjnosql.db.spring.boot.data;

import org.embeddedjnosql.db.nosql.document.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import org.springframework.data.repository.core.NamedQueries;
import org.springframework.data.repository.core.RepositoryMetadata;
import org.springframework.data.repository.query.QueryLookupStrategy;
import org.springframework.data.repository.query.QueryMethod;
import org.springframework.data.repository.query.RepositoryQuery;
import org.springframework.data.repository.query.parser.PartTree;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Resolves Spring Data repository query methods against the typed document predicate API:
 * a method named like a Spring Data JPA derived query ({@code findByTitleIgnoreCase},
 * {@code findFirst10ByPriceLessThanOrderByNameAsc}...) or carrying an is-count / is-exists /
 * is-delete subject is parsed with the shared {@link PartTree} grammar and translated by
 * {@link EmbedJpaPartTreeQueryCreator} — the same job
 * {@code org.springframework.data.jpa.repository.query.JpaQueryLookupStrategy} does for
 * relational stores, over the embedded document engine.
 *
 * <p>Honors {@code CREATE} / {@code USE_DECLARED_QUERY} / {@code CREATE_IF_NOT_FOUND}.
 * Named queries are recognized when declared as properties
 * ({@code spring.data.jpa.repositories.named-queries.*}-style properties maps), matching the
 * default {@code CREATE_IF_NOT_FOUND} behavior: a declared query wins, otherwise the method
 * name derives the predicate. A declared query string is the exact document query grammar
 * the engine parses at runtime.</p>
 */
class EmbedJpaQueryLookupStrategy implements QueryLookupStrategy {

    enum Strategy { CREATE, USE_DECLARED_QUERY, CREATE_IF_NOT_FOUND }

    /** Resolves the base repository instance for a query method's domain type. */
    interface RepositoryResolver {
        EmbedJpaBaseRepository<?, ?> forDomain(Class<?> domainType);
    }

    private final RepositoryResolver repositoryResolver;
    private final Strategy strategy;
    private final NamedQueries namedQueries;

    /** NamedQueries with no entries — the interface ships no shared empty constant. */
    private static final NamedQueries NO_NAMED_QUERIES = new NamedQueries() {
        @Override public boolean hasQuery(String name) { return false; }
        @Override public String getQuery(String name) { return null; }
    };

    EmbedJpaQueryLookupStrategy(RepositoryResolver repositoryResolver, Strategy strategy,
                                NamedQueries namedQueries) {
        this.repositoryResolver = repositoryResolver;
        this.strategy = strategy;
        this.namedQueries = namedQueries == null ? NO_NAMED_QUERIES : namedQueries;
    }

    @Override
    public RepositoryQuery resolveQuery(Method method, RepositoryMetadata metadata,
                                        ProjectionFactory projectionFactory, NamedQueries __) {
        String declared = namedQueries.hasQuery(method.getName())
                ? namedQueries.getQuery(method.getName()) : null;
        switch (strategy) {
            case USE_DECLARED_QUERY:
                if (declared == null) {
                    throw new IllegalStateException(
                            "No named query found for method " + method.getName()
                                    + " (USE_DECLARED_QUERY lookup strategy is active)");
                }
                EmbedJpaBaseRepository<?, ?> declaredRepo =
                        repositoryResolver.forDomain(metadata.getDomainType());
                return new DeclaredStringRepositoryQuery(method, metadata, declaredRepo, declared);
            case CREATE_IF_NOT_FOUND:
                if (declared != null) {
                    return new DeclaredStringRepositoryQuery(method, metadata,
                            repositoryResolver.forDomain(metadata.getDomainType()), declared);
                }
                // fall through to derive
            case CREATE:
            default:
                EmbedJpaBaseRepository<?, ?> repository =
                        repositoryResolver.forDomain(metadata.getDomainType());
                return new PartTreeRepositoryQuery(method, metadata, repository, projectionFactory);
        }
    }

    // ---------------------------------------------------------------------------
    // PartTree-derived query
    // ---------------------------------------------------------------------------

    static final class PartTreeRepositoryQuery implements RepositoryQuery {

        private final Method method;
        private final RepositoryMetadata metadata;
        private final EmbedJpaBaseRepository<?, ?> repository;
        private final QueryMethod queryMethod;

        PartTreeRepositoryQuery(Method method, RepositoryMetadata metadata,
                                EmbedJpaBaseRepository<?, ?> repository, ProjectionFactory projectionFactory) {
            this.method = method;
            this.metadata = metadata;
            this.repository = repository;
            this.queryMethod = new QueryMethod(method, metadata, projectionFactory);
        }

        @Override
        public QueryMethod getQueryMethod() {
            return queryMethod;
        }

        @Override
        public Object execute(Object[] parameters) {
            PartTree tree = new PartTree(method.getName(), metadata.getDomainType());
            Query query = new EmbedJpaPartTreeQueryCreator(
                    tree, new BindableIterator(filterBindable(parameters)),
                    repository.getEntityType()).createQuery();

            Sort declaredSort = sortParameter(parameters);
            Pageable pageable = pageableParameter(parameters);

            if (tree.isDelete()) {
                return repository.executeDelete(query);
            }
            if (tree.isCountProjection()) {
                return repository.countMatching(query);
            }
            if (tree.isExistsProjection()) {
                return repository.executeExists(query);
            }
            Sort effectiveSort = declaredSort;
            Integer maxResults = tree.getMaxResults();
            if (pageable != null && pageable.isPaged()) {
                if (effectiveSort == null && pageable.getSort().isSorted()) {
                    effectiveSort = pageable.getSort();
                }
                return repository.executePage(query, pageable, effectiveSort);
            }
            return repository.execute(query, effectiveSort, maxResults);
        }
    }

    // ---------------------------------------------------------------------------
    // Declared named query (raw document query string)
    // ---------------------------------------------------------------------------

    static final class DeclaredStringRepositoryQuery implements RepositoryQuery {

        private final Method method;
        private final RepositoryMetadata metadata;
        private final EmbedJpaBaseRepository<?, ?> repository;
        private final String queryString;
        private final QueryMethod queryMethod;

        DeclaredStringRepositoryQuery(Method method, RepositoryMetadata metadata,
                                      EmbedJpaBaseRepository<?, ?> repository, String queryString) {
            this.method = method;
            this.metadata = metadata;
            this.repository = repository;
            this.queryString = queryString;
            this.queryMethod = new QueryMethod(method, metadata,
                    new SpelAwareProxyProjectionFactory());
        }

        @Override
        public QueryMethod getQueryMethod() {
            return queryMethod;
        }

        @Override
        public Object execute(Object[] parameters) {
            Query query = Query.fromQuery(queryString);
            // positional ?value substitution is handled by Query.fromQuery's literal parse;
            // parameterized declared queries keep the derived-path coercion-free semantics
            Pageable pageable = pageableParameter(parameters);
            Sort sort = sortParameter(parameters);
            if (pageable != null && pageable.isPaged()) {
                return repository.executePage(query, pageable, sort);
            }
            return repository.execute(query, sort, null);
        }
    }

    // ---------------------------------------------------------------------------
    // helpers
    // ---------------------------------------------------------------------------

    private static Object[] filterBindable(Object[] parameters) {
        if (parameters == null) {
            return new Object[0];
        }
        List<Object> out = new ArrayList<>(parameters.length);
        for (Object p : parameters) {
            if (p instanceof Pageable || p instanceof Sort || p instanceof Class) {
                continue;
            }
            out.add(p);
        }
        return out.toArray();
    }

    private static Pageable pageableParameter(Object[] parameters) {
        if (parameters == null) return null;
        for (Object p : parameters) {
            if (p instanceof Pageable pageable) {
                return pageable;
            }
        }
        return null;
    }

    private static Sort sortParameter(Object[] parameters) {
        if (parameters == null) return Sort.unsorted();
        for (Object p : parameters) {
            if (p instanceof Sort sort && sort.isSorted()) {
                return sort;
            }
        }
        return Sort.unsorted();
    }

    /**
     * A minimal {@link org.springframework.data.repository.query.ParameterAccessor} over the
     * raw bindable values (Pageable/Sort/projection classes are filtered out up front) so the
     * query creator's AbstractQueryCreator plumbing can drive parameter consumption.
     */
    static final class BindableIterator
            implements Iterator<Object>, org.springframework.data.repository.query.ParameterAccessor {

        private final Object[] values;
        private int cursor;

        BindableIterator(Object[] values) {
            this.values = values;
        }

        @Override
        public boolean hasNext() {
            return cursor < values.length;
        }

        @Override
        public Object next() {
            if (cursor >= values.length) {
                throw new NoSuchElementException("derived query consumed more parameters than declared");
            }
            return values[cursor++];
        }

        @Override
        public Object getBindableValue(int index) {
            return index < values.length ? values[index] : null;
        }

        @Override
        public boolean hasBindableNullValue() {
            for (Object v : values) {
                if (v == null) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public Iterator<Object> iterator() {
            return this;
        }

        @Override
        public Pageable getPageable() {
            return Pageable.unpaged();
        }

        @Override
        public Sort getSort() {
            return Sort.unsorted();
        }

        @Override
        public org.springframework.data.domain.ScrollPosition getScrollPosition() {
            return org.springframework.data.domain.ScrollPosition.offset();
        }

        @Override
        public Class<?> findDynamicProjection() {
            return null;
        }
    }
}

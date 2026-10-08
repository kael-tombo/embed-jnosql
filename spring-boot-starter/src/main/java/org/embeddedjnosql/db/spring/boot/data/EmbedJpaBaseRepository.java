package org.embeddedjnosql.db.spring.boot.data;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.adapter.jnosql.EntityMapper;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.document.Query;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.QueryByExampleExecutor;
import org.springframework.data.support.PageableExecutionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The repository implementation Spring Data's proxy machinery delegates to — the embed-jnosql
 * counterpart of Spring Data JPA's {@code SimpleJpaRepository}. Implements the full
 * {@code CrudRepository} + {@code PagingAndSortingRepository} + {@code JpaRepository} +
 * {@code QueryByExampleExecutor} surface over the document store.
 *
 * <p>Transaction semantics come from the seam itself: every collection handle is resolved
 * from the (routed) {@link EmbedJNoSQL} bean per call, so inside a {@code @Transactional}
 * method the writes stage in the MVCC transaction and commit/rollback with it — the same
 * contract the auto-registered {@code EmbedRepository} keeps.</p>
 *
 * <p>Modeled after Spring Data JPA's {@code SimpleJpaRepository}:</p>
 * <ul>
 *   <li>{@code save} = insert when the id is absent, upsert-by-existence otherwise — the
 *       document-store analog of {@code em.merge} without a session;</li>
 *   <li>{@code flush}/{@code saveAndFlush} are immediate (writes are never deferred — the
 *       store has no {@code em.clear}-driven batch boundary to race);</li>
 *   {@code getReferenceById} resolves eagerly (no session to hang a lazy proxy on);</li>
 *   <li>{@code deleteAllInBatch} deletes by matched documents, not one-by-one entity
 *       callbacks (no managed cascades to honor).</li>
 * </ul>
 */
public class EmbedJpaBaseRepository<T, ID>
        implements CrudRepository<T, ID>, PagingAndSortingRepository<T, ID>,
        org.springframework.data.jpa.repository.JpaRepository<T, ID>, QueryByExampleExecutor<T> {

    private final EmbedJNoSQL db;
    private final Class<T> entityType;
    private final String collectionName;
    private final String idColumn;

    public EmbedJpaBaseRepository(EmbedJNoSQL db, Class<T> entityType) {
        this.db = db;
        this.entityType = entityType;
        this.collectionName = EntityMapper.getCollectionName(entityType);
        this.idColumn = EntityMapper.getIdFieldName(entityType);
    }

    public EmbedJNoSQL getDb() {
        return db;
    }

    public Class<T> getEntityType() {
        return entityType;
    }

    // ---------------------------------------------------------------------------
    // Base plumbing
    // ---------------------------------------------------------------------------

    /**
     * Resolved per call so @Transactional staging applies — via the routed proxy when the
     * captured reference is the auto-instrumented bean, or via the thread-bound transaction
     * fallback otherwise (an eagerly created factory-bean reference can bypass proxy
     * instrumentation entirely; see {@link TxCollectionFactory}).
     */
    DocumentCollection collection() {
        return TxCollectionFactory.forDb(db, d -> d.documentCollection(collectionName), collectionName);
    }

    /** Document field names are entity-property resolvable per call; cheap and property-safe. */
    String col(String property) {
        return MappingColumns.column(entityType, property);
    }

    /** Document ids are strings internally; non-string ids are stringified for the lookup. */
    String idString(Object id) {
        if (id == null) {
            throw new IllegalArgumentException("ID must not be null");
        }
        return id instanceof String s ? s : String.valueOf(id);
    }

    private T toEntity(Document doc) {
        return EntityMapper.fromDocument(doc, entityType);
    }

    // ---------------------------------------------------------------------------
    // CrudRepository
    // ---------------------------------------------------------------------------

    @Override
    @SuppressWarnings("unchecked")
    public <S extends T> S save(S entity) {
        Object id = EntityMapper.getIdValue(entity);
        if (id == null) {
            return org.embeddedjnosql.db.adapter.jnosql.EclipseDocumentTemplate.of(collection()).insert(entity);
        }
        if (collection().exists(idString(id))) {
            return org.embeddedjnosql.db.adapter.jnosql.EclipseDocumentTemplate.of(collection()).update(entity);
        }
        return org.embeddedjnosql.db.adapter.jnosql.EclipseDocumentTemplate.of(collection()).insert(entity);
    }

    @Override
    public <S extends T> List<S> saveAll(Iterable<S> entities) {
        List<S> saved = new ArrayList<>();
        for (S entity : entities) {
            saved.add(save(entity));
        }
        return saved;
    }

    @Override
    public Optional<T> findById(ID id) {
        Document doc = collection().findById(idString(id));
        return Optional.ofNullable(doc == null ? null : toEntity(doc));
    }

    @Override
    public boolean existsById(ID id) {
        return collection().exists(idString(id));
    }

    @Override
    public List<T> findAll() {
        List<Document> docs = collection().findAll();
        return mapAll(docs);
    }

    @Override
    public List<T> findAllById(Iterable<ID> ids) {
        List<T> found = new ArrayList<>();
        for (ID id : ids) {
            findById(id).ifPresent(found::add);
        }
        return found;
    }

    @Override
    public long count() {
        return collection().count();
    }

    @Override
    public void deleteById(ID id) {
        collection().deleteById(idString(id));
    }

    @Override
    public void delete(T entity) {
        Object id = EntityMapper.getIdValue(entity);
        if (id == null) {
            throw new IllegalArgumentException(
                    "Entity of type " + entityType.getName() + " has no id; cannot delete");
        }
        deleteById((ID) id);
    }

    @Override
    public void deleteAll(Iterable<? extends T> entities) {
        for (T entity : entities) {
            delete(entity);
        }
    }

    @Override
    public void deleteAll() {
        for (Document doc : collection().findAll()) {
            collection().deleteById(doc.getId());
        }
    }

    @Override
    public void deleteAllById(Iterable<? extends ID> ids) {
        for (ID id : ids) {
            deleteById(id);
        }
    }

    // ---------------------------------------------------------------------------
    // PagingAndSortingRepository
    // ---------------------------------------------------------------------------

    @Override
    public List<T> findAll(Sort sort) {
        List<Document> docs = collection().find(Query.all());
        return sortDocs(sort, docs);
    }

    @Override
    public Page<T> findAll(Pageable pageable) {
        Query q = applyPageable(pageable);
        List<Document> docs = collection().find(q);
        List<T> content = sortDocs(pageable.getSort(), docs);
        return PageableExecutionUtils.getPage(content, pageable, this::count);
    }

    private Query applyPageable(Pageable pageable) {
        Query q = Query.all();
        q.offset((int) Math.min(pageable.getOffset(), Integer.MAX_VALUE));
        q.limit(pageable.getPageSize());
        return q;
    }

    private List<T> mapAll(List<Document> docs) {
        List<T> out = new ArrayList<>(docs.size());
        for (Document doc : docs) {
            out.add(toEntity(doc));
        }
        return out;
    }

    List<T> sortDocs(Sort sort, List<Document> docs) {
        if (sort == null || !sort.isSorted()) {
            return mapAll(docs);
        }
        List<Sort.Order> orders = new ArrayList<>();
        sort.forEach(orders::add);
        Comparator<Document> comparator = null;
        for (Sort.Order order : orders) {
            String column = MappingColumns.column(entityType, order.getProperty());
            Comparator<Document> c = Comparator.comparing(d -> sortableValue(d, column), NULLS_LAST);
            if (order.isDescending()) {
                c = c.reversed();
            }
            comparator = comparator == null ? c : comparator.thenComparing(c);
        }
        if (comparator == null) {
            return mapAll(docs);
        }
        List<Document> copy = new ArrayList<>(docs);
        copy.sort(comparator);
        return mapAll(copy);
    }

    private static final Comparator<Object> NULLS_LAST = (a, b) -> {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue());
        }
        return a.toString().compareTo(b.toString());
    };

    private Object sortableValue(Document doc, String column) {
        Object raw = doc.getRaw(column);
        if (raw instanceof String s && idColumn.equals(column)) {
            return s;
        }
        return raw;
    }

    // ---------------------------------------------------------------------------
    // JpaRepository additions
    // ---------------------------------------------------------------------------

    /** Writes are applied/staged immediately — nothing to flush. */
    @Override
    public void flush() {
        // no-op by design: the document store never buffers past the write call
    }

    @Override
    public <S extends T> S saveAndFlush(S entity) {
        return save(entity);
    }

    @Override
    public <S extends T> List<S> saveAllAndFlush(Iterable<S> entities) {
        return saveAll(entities);
    }

    @Override
    public void deleteInBatch(Iterable<T> entities) {
        deleteAll(entities);
    }

    @Override
    public void deleteAllInBatch(Iterable<T> entities) {
        deleteAll(entities);
    }

    @Override
    public void deleteAllByIdInBatch(Iterable<ID> ids) {
        deleteAllById(ids);
    }

    @Override
    public void deleteAllInBatch() {
        deleteAll();
    }

    /** Eager read (no persistence context to hang a lazy proxy on). */
    @Override
    public T getOne(ID id) {
        return getById(id);
    }

    @Override
    public T getById(ID id) {
        return findById(id).orElseThrow(() ->
                new java.util.NoSuchElementException(
                        "No entity of type " + entityType.getName() + " with id '" + id + "'"));
    }

    @Override
    public T getReferenceById(ID id) {
        return getById(id);
    }

    // ---------------------------------------------------------------------------
    // Query push-down helpers shared with the Spring Data proxy layer
    // ---------------------------------------------------------------------------

    List<T> execute(Query query, Sort sort, Integer firstResult) {
        List<Document> docs = collection().find(query);
        List<Document> picked = firstResult != null && docs.size() > firstResult
                ? new ArrayList<>(docs.subList(0, firstResult))
                : docs;
        return sortDocs(sort, picked);
    }

    boolean executeExists(Query query) {
        return !collection().find(org.embeddedjnosql.db.nosql.document.Query
                .matching(query.docPredicate()).limit(1)).isEmpty();
    }

    List<T> executeDelete(Query query) {
        List<Document> docs = collection().find(query);
        for (Document doc : docs) {
            collection().deleteById(doc.getId());
        }
        List<T> deleted = new ArrayList<>(docs.size());
        for (Document doc : docs) {
            deleted.add(toEntity(doc));
        }
        return deleted;
    }

    Page<T> executePage(Query query, Pageable pageable) {
        return executePage(query, pageable, pageable.getSort());
    }

    Page<T> executePage(Query query, Pageable pageable, Sort explicitSort) {
        Query copy = org.embeddedjnosql.db.nosql.document.Query.matching(query.docPredicate())
                .offset((int) Math.min(pageable.getOffset(), Integer.MAX_VALUE))
                .limit(pageable.getPageSize());
        List<Document> docs = collection().find(copy);
        Sort effective = explicitSort != null && explicitSort.isSorted()
                ? explicitSort : pageable.getSort();
        List<T> content = sortDocs(effective, docs);
        return PageableExecutionUtils.getPage(content, pageable, () -> rawCount(query));
    }

    private long rawCount(Query predicateSource) {
        Query countQuery = org.embeddedjnosql.db.nosql.document.Query
                .matching(predicateSource.docPredicate());
        return collection().find(countQuery).size();
    }

    long countMatching(Query query) {
        return rawCount(query);
    }

    // ---------------------------------------------------------------------------
    // Query-by-Example
    // ---------------------------------------------------------------------------

    private Predicate<Document> examplePredicate(Example<?> example) {
        return QueryByExampleBuilder.predicate(example, entityType);
    }

    @Override
    public <S extends T> Optional<S> findOne(Example<S> example) {
        Predicate<Document> pred = examplePredicate(example);
        List<Document> docs = collection().find(Query.matching(pred));
        return docs.isEmpty()
                ? Optional.empty()
                : Optional.of(asSub(toEntity(docs.get(0)), example.getProbeType()));
    }

    @Override
    public <S extends T> List<S> findAll(Example<S> example) {
        return findAll(example, Sort.unsorted());
    }

    @Override
    public <S extends T> List<S> findAll(Example<S> example, Sort sort) {
        Predicate<Document> pred = examplePredicate(example);
        List<Document> docs = collection().find(Query.matching(pred));
        return sortDocs(sort, docs).stream()
                .map(d -> asSub(d, example.getProbeType()))
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public <S extends T> Page<S> findAll(Example<S> example, Pageable pageable) {
        Predicate<Document> pred = examplePredicate(example);
        return asPage(executePage(Query.matching(pred), pageable), example.getProbeType());
    }

    @Override
    public <S extends T> long count(Example<S> example) {
        return countMatching(Query.matching(examplePredicate(example)));
    }

    @Override
    public <S extends T> boolean exists(Example<S> example) {
        return !collection().find(Query.matching(examplePredicate(example))).isEmpty();
    }

    @Override
    public <S extends T, R> R findBy(Example<S> example,
                                     java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> applyFunction) {
        return applyFunction.apply(new EmbedFluentQuery<>(this, example));
    }

    @SuppressWarnings("unchecked")
    private <R extends T> R asSub(T entity, Class<R> asType) {
        return (R) entity;
    }

    @SuppressWarnings("unchecked")
    private <S extends T> Page<S> asPage(Page<T> page, Class<S> asType) {
        return (Page<S>) page;
    }

    /**
     * Builds the document predicate behind a Spring Data {@link Example}: probe properties
     * are projected to their stored shape (same coercions as the core mapping layer),
     * {@link org.springframework.data.domain.ExampleMatcher} ignored paths, null handling,
     * per-property string matchers and ignore-case are honored.
     */
    static final class QueryByExampleBuilder {

        private QueryByExampleBuilder() {
        }

        static Predicate<Document> predicate(Example<?> example, Class<?> entityType) {
            Object probe = example.getProbe();
            org.springframework.data.domain.ExampleMatcher matcher = example.getMatcher();
            boolean all = matcher.isAllMatching();

            List<Predicate<Document>> parts = new ArrayList<>();
            List<Field> fields = declaredPersistedFields(entityType);

            for (Field field : fields) {
                String property = field.getName();
                if (matcher.isIgnoredPath(property)) {
                    continue;
                }
                Object value;
                try {
                    field.setAccessible(true);
                    value = field.get(probe);
                } catch (IllegalAccessException e) {
                    continue;
                }
                if (value == null && matcher.getNullHandler()
                        == org.springframework.data.domain.ExampleMatcher.NullHandler.IGNORE) {
                    continue;
                }
                Object stored = storedShape(value);
                boolean ignoreCase = resolveIgnoreCase(matcher, property);
                org.springframework.data.domain.ExampleMatcher.StringMatcher sm =
                        resolveStringMatcher(matcher, property);
                Predicate<Document> p = queryForProperty(
                        MappingColumns.column(entityType, property), stored, sm, ignoreCase).docPredicate();
                parts.add(p);
            }

            if (parts.isEmpty()) {
                return doc -> true;
            }
            if (parts.size() == 1) {
                return parts.get(0);
            }
            if (all) {
                Predicate<Document> allPredicate = doc -> true;
                for (Predicate<Document> p : parts) {
                    allPredicate = allPredicate.and(p);
                }
                return allPredicate;
            }
            Predicate<Document> anyPredicate = doc -> false;
            for (Predicate<Document> p : parts) {
                anyPredicate = anyPredicate.or(p);
            }
            return anyPredicate;
        }

        private static org.embeddedjnosql.db.nosql.document.Query queryForProperty(
                String column, Object value,
                org.springframework.data.domain.ExampleMatcher.StringMatcher sm,
                boolean ignoreCase) {
            boolean isString = value instanceof String;
            String s = isString && ignoreCase ? MappingColumns.lower(value) : null;
            Query byEquality = Query.eq(column, value);
            return switch (sm) {
                case DEFAULT, EXACT -> {
                    if (isString && ignoreCase) {
                        yield Query.matching(doc -> {
                            Object raw = doc.getRaw(column);
                            return raw != null && MappingColumns.lower(raw).equals(s);
                        });
                    }
                    yield byEquality;
                }
                case STARTING -> queried(startsPredicate(column, value, ignoreCase));
                case ENDING -> queried(endsPredicate(column, value, ignoreCase));
                case CONTAINING -> queried(containsPredicate(column, value, ignoreCase, false));
                case REGEX -> Query.matching(doc -> {
                    Object raw = doc.getRaw(column);
                    return raw != null && raw.toString().matches(value.toString());
                });
            };
        }

        /** Wraps a raw document predicate as a (predicate-only) engine Query. */
        private static org.embeddedjnosql.db.nosql.document.Query queried(Predicate<Document> predicate) {
            return org.embeddedjnosql.db.nosql.document.Query.matching(predicate);
        }

        private static Predicate<Document> startsPredicate(String column, Object value, boolean ignoreCase) {
            String prefix = ignoreCase ? MappingColumns.lower(value) : value.toString();
            return doc -> {
                Object raw = doc.getRaw(column);
                if (raw == null) return false;
                String text = ignoreCase ? MappingColumns.lower(raw) : raw.toString();
                return text.startsWith(prefix);
            };
        }

        private static Predicate<Document> containsPredicate(
                String column, Object value, boolean ignoreCase, boolean negated) {
            String needle = ignoreCase ? MappingColumns.lower(value) : value.toString();
            Predicate<Document> positive = doc -> {
                Object raw = doc.getRaw(column);
                if (raw == null) return false;
                String s = ignoreCase ? MappingColumns.lower(raw) : raw.toString();
                return s.contains(needle);
            };
            return negated ? positive.negate() : positive;
        }

        private static Predicate<Document> endsPredicate(String column, Object value, boolean ignoreCase) {
            String suffix = ignoreCase ? MappingColumns.lower(value) : value.toString();
            return doc -> {
                Object raw = doc.getRaw(column);
                if (raw == null) return false;
                String s = ignoreCase ? MappingColumns.lower(raw) : raw.toString();
                return s.endsWith(suffix);
            };
        }

        /**
         * Persisted shape of a probe value — mirrors what {@code EntityMapper.toDocument}
         * writes: enums as names, {@code Instant}/{@code Date} as ISO strings.
         */
        static Object storedShape(Object value) {
            if (value instanceof Enum<?> e) {
                return e.name();
            }
            if (value instanceof java.time.Instant i) {
                return i.toString();
            }
            if (value instanceof java.time.LocalDateTime ldt) {
                return ldt.toString();
            }
            if (value instanceof java.util.Date d) {
                return d.toInstant().toString();
            }
            return value;
        }

        private static boolean resolveIgnoreCase(org.springframework.data.domain.ExampleMatcher matcher,
                                                 String property) {
            if (matcher.getPropertySpecifiers().hasSpecifierForPath(property)) {
                Boolean ignoreCase = matcher.getPropertySpecifiers().getForPath(property).getIgnoreCase();
                return ignoreCase != null ? ignoreCase : matcher.isIgnoreCaseEnabled();
            }
            return matcher.isIgnoreCaseEnabled();
        }

        private static org.springframework.data.domain.ExampleMatcher.StringMatcher resolveStringMatcher(
                org.springframework.data.domain.ExampleMatcher matcher, String property) {
            if (matcher.getPropertySpecifiers().hasSpecifierForPath(property)) {
                org.springframework.data.domain.ExampleMatcher.StringMatcher sm =
                        matcher.getPropertySpecifiers().getForPath(property).getStringMatcher();
                if (sm != null) {
                    return sm;
                }
            }
            return matcher.getDefaultStringMatcher();
        }

        private static List<Field> declaredPersistedFields(Class<?> entityType) {
            List<Field> fields = new ArrayList<>();
            Class<?> current = entityType;
            while (current != null && current != Object.class) {
                for (Field field : current.getDeclaredFields()) {
                    int mods = field.getModifiers();
                    if (java.lang.reflect.Modifier.isStatic(mods)
                            || java.lang.reflect.Modifier.isTransient(mods)) {
                        continue;
                    }
                    String name = field.getName();
                    if (name.startsWith("this$")) {
                        continue;
                    }
                    fields.add(field);
                }
                current = current.getSuperclass();
            }
            return fields;
        }
    }

    // ---------------------------------------------------------------------------
    // FluentQuery for findBy(example, function)
    // ---------------------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private class EmbedFluentQuery<S extends T> implements
            org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S> {

        private final QueryByExampleExecutor<?> executor;
        private final Example<S> example;
        private Sort sort = Sort.unsorted();
        private Integer limit;

        EmbedFluentQuery(EmbedJpaBaseRepository<T, ID> owner, Example<S> example) {
            this.executor = owner;
            this.example = example;
        }

        private EmbedFluentQuery(EmbedFluentQuery<?> source) {
            this.executor = source.executor;
            this.example = (Example<S>) source.example;
            this.sort = source.sort;
            this.limit = source.limit;
        }

        @Override
        public org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S> sortBy(Sort sort) {
            EmbedFluentQuery<S> copy = new EmbedFluentQuery<>(this);
            copy.sort = sort;
            return copy;
        }

        @Override
        public org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S> limit(int limit) {
            EmbedFluentQuery<S> copy = new EmbedFluentQuery<>(this);
            copy.limit = limit;
            return copy;
        }

        @Override
        public <R> org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<R> as(Class<R> resultType) {
            throw new UnsupportedOperationException(
                    "findBy(...).as(DTO) projection mapping is not supported by the embed-jnosql "
                            + "Spring Data integration; project onto the entity type itself");
        }

        @Override
        public org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S> project(String... properties) {
            throw new UnsupportedOperationException(
                    "findBy(...).project(fields) is not supported by the embed-jnosql Spring Data "
                            + "integration; query the entity type directly");
        }

        @Override
        public org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S> project(java.util.Collection<String> properties) {
            throw new UnsupportedOperationException(
                    "findBy(...).project(fields) is not supported by the embed-jnosql Spring Data "
                            + "integration; query the entity type directly");
        }

        @Override
        public Optional<S> one() {
            List<S> all = allInternal();
            return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
        }

        @Override
        public S oneValue() {
            List<S> all = allInternal();
            if (all.size() > 1) {
                throw new org.springframework.dao.IncorrectResultSizeDataAccessException(
                        "Expected one result, found " + all.size(), 1, all.size());
            }
            return all.isEmpty() ? null : all.get(0);
        }

        @Override
        public Optional<S> first() {
            return one();
        }

        @Override
        public S firstValue() {
            List<S> all = allInternal();
            return all.isEmpty() ? null : all.get(0);
        }

        @Override
        public List<S> all() {
            return allInternal();
        }

        private List<S> allInternal() {
            Predicate<Document> pred = examplePredicate(example);
            Query query = Query.matching(pred);
            if (limit != null) {
                query.limit(limit);
            }
            return sortDocs(sort, collection().find(query)).stream()
                    .map(d -> (S) d)
                    .toList();
        }

        @Override
        public org.springframework.data.domain.Window<S> scroll(org.springframework.data.domain.ScrollPosition position) {
            throw new UnsupportedOperationException(
                    "Scroll queries are not supported by the embed-jnosql Spring Data integration");
        }

        @Override
        public Page<S> page(Pageable pageable) {
            return asPage(executePage(Query.matching(examplePredicate(example)), pageable),
                    (Class<S>) entityType);
        }

        @Override
        public Stream<S> stream() {
            return allInternal().stream();
        }

        @Override
        public long count() {
            return countMatching(Query.matching(examplePredicate(example)));
        }

        @Override
        public boolean exists() {
            return !collection().find(Query.matching(examplePredicate(example))).isEmpty();
        }
    }
}

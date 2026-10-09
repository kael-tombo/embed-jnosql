package org.embeddedjnosql.db.adapter.jnosql;

import jakarta.data.Order;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import jakarta.data.page.impl.PageRecord;
import org.eclipse.jnosql.mapping.NoSQLRepository;
import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.document.Query;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Standard implementation of the Eclipse JNoSQL {@link NoSQLRepository} specification.
 * Provides type-safe repository CRUD, pagination, batching, and query operations
 * against EmbedJNoSQL.
 *
 * @param <T> Entity type
 * @param <K> Identifier type
 */
public class EmbedNoSQLRepository<T, K> implements NoSQLRepository<T, K> {

    protected final Class<T> entityClass;
    protected final EmbedJNoSQL db;

    public EmbedNoSQLRepository(Class<T> entityClass, EmbedJNoSQL db) {
        this.entityClass = Objects.requireNonNull(entityClass, "entityClass");
        this.db = Objects.requireNonNull(db, "db");
    }

    public static <T, K> EmbedNoSQLRepository<T, K> of(Class<T> entityClass, EmbedJNoSQL db) {
        return new EmbedNoSQLRepository<>(entityClass, db);
    }

    protected EclipseDocumentTemplate template() {
        return EclipseDocumentTemplate.of(db.documentCollection(EntityMapper.getCollectionName(entityClass)));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <S extends T> S save(S entity) {
        Object id = EntityMapper.getIdValue(entity);
        if (id != null && existsById((K) id)) {
            return template().update(entity);
        }
        return template().insert(entity);
    }

    @Override
    public <S extends T> List<S> saveAll(List<S> entities) {
        List<S> result = new ArrayList<>();
        if (entities != null) {
            for (S entity : entities) {
                result.add(save(entity));
            }
        }
        return result;
    }

    @Override
    public <S extends T> S insert(S entity) {
        return template().insert(entity);
    }

    @Override
    public <S extends T> List<S> insertAll(List<S> entities) {
        List<S> result = new ArrayList<>();
        if (entities != null) {
            for (S entity : entities) {
                result.add(insert(entity));
            }
        }
        return result;
    }

    @Override
    public <S extends T> S update(S entity) {
        return template().update(entity);
    }

    @Override
    public <S extends T> List<S> updateAll(List<S> entities) {
        List<S> result = new ArrayList<>();
        if (entities != null) {
            for (S entity : entities) {
                result.add(update(entity));
            }
        }
        return result;
    }

    @Override
    public Optional<T> findById(K id) {
        return template().find(entityClass, id);
    }

    @Override
    public boolean existsById(K id) {
        return template().existsById(entityClass, id);
    }

    @Override
    public Stream<T> findAll() {
        return template().findAll(entityClass).stream();
    }

    @Override
    public Page<T> findAll(PageRequest pageRequest, Order<T> order) {
        List<T> all = template().findAll(entityClass);
        if (order != null && order.sorts() != null && !order.sorts().isEmpty()) {
            all.sort((o1, o2) -> {
                for (var sort : order.sorts()) {
                    String prop = sort.property();
                    Object v1 = EntityMapper.readPropertyValue(o1, prop);
                    Object v2 = EntityMapper.readPropertyValue(o2, prop);
                    int cmp = compareValues(v1, v2, sort.ignoreCase());
                    if (cmp != 0) {
                        return sort.isAscending() ? cmp : -cmp;
                    }
                }
                return 0;
            });
        }
        long pageNum = pageRequest != null ? pageRequest.page() : 1;
        int size = pageRequest != null ? pageRequest.size() : (all.isEmpty() ? 10 : all.size());
        int fromIdx = (int) Math.max(0, (pageNum - 1) * size);
        int toIdx = Math.min(all.size(), fromIdx + size);
        List<T> paged = fromIdx < all.size() ? all.subList(fromIdx, toIdx) : Collections.emptyList();
        return new PageRecord<>(pageRequest, paged, all.size());
    }

    public long count() {
        return template().count(entityClass);
    }

    @Override
    public long countBy() {
        return count();
    }

    @Override
    public void deleteById(K id) {
        template().deleteById(entityClass, id);
    }

    @Override
    public void delete(T entity) {
        template().delete(entity);
    }

    @Override
    public void deleteAll(List<? extends T> entities) {
        if (entities != null) {
            for (T entity : entities) {
                delete(entity);
            }
        }
    }

    @Override
    public void deleteAll() {
        for (T entity : template().findAll(entityClass)) {
            delete(entity);
        }
    }

    @Override
    public Stream<T> findByIdIn(Iterable<K> ids) {
        List<T> result = new ArrayList<>();
        if (ids != null) {
            for (K id : ids) {
                findById(id).ifPresent(result::add);
            }
        }
        return result.stream();
    }

    @Override
    public void deleteByIdIn(Iterable<K> ids) {
        if (ids != null) {
            for (K id : ids) {
                deleteById(id);
            }
        }
    }

    public List<T> findBy(String fieldName, Object value) {
        return template().find(entityClass, Query.eq(fieldName, value));
    }

    public Class<T> getEntityClass() {
        return entityClass;
    }

    public EmbedJNoSQL getDb() {
        return db;
    }

    public EclipseDocumentTemplate getTemplate() {
        return template();
    }

    private static int compareValues(Object a, Object b, boolean ignoreCase) {
        if (a == null) return b == null ? 0 : -1;
        if (b == null) return 1;
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue());
        }
        if (ignoreCase && a instanceof String sa && b instanceof String sb) {
            return sa.compareToIgnoreCase(sb);
        }
        if (a instanceof Comparable ca) {
            try {
                return ca.compareTo(b);
            } catch (Exception ignored) {}
        }
        return a.toString().compareTo(b.toString());
    }
}

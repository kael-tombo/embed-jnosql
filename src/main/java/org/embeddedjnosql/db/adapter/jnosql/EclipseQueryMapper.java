package org.embeddedjnosql.db.adapter.jnosql;

import jakarta.nosql.QueryMapper;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Fluent query builders implementing Jakarta NoSQL / Eclipse JNoSQL {@link QueryMapper}
 * interfaces for querying and deleting entities against an EmbedJNoSQL {@link DocumentCollection}.
 */
public class EclipseQueryMapper {

    public static <T> QueryMapper.MapperFrom select(Class<T> entityClass, DocumentCollection collection) {
        return new SelectMapper<>(entityClass, collection);
    }

    public static <T> QueryMapper.MapperDeleteFrom delete(Class<T> entityClass, DocumentCollection collection) {
        return new DeleteMapper<>(entityClass, collection);
    }

    private enum Op { EQ, LIKE, GT, GTE, LT, LTE, BETWEEN, IN }
    private enum Combine { AND, OR }

    private static class Condition {
        final String property;
        final Op op;
        final Object val1;
        final Object val2;
        final boolean negated;
        final Combine combine;

        Condition(String property, Op op, Object val1, Object val2, boolean negated, Combine combine) {
            this.property = property;
            this.op = op;
            this.val1 = val1;
            this.val2 = val2;
            this.negated = negated;
            this.combine = combine;
        }
    }

    private static class OrderClause {
        final String property;
        final boolean ascending;

        OrderClause(String property, boolean ascending) {
            this.property = property;
            this.ascending = ascending;
        }
    }

    // -------------------------------------------------------------------------
    // Select Query Builder
    // -------------------------------------------------------------------------

    public static class SelectMapper<T> implements
            QueryMapper.MapperFrom,
            QueryMapper.MapperWhere,
            QueryMapper.MapperNameCondition,
            QueryMapper.MapperNotCondition,
            QueryMapper.MapperOrder,
            QueryMapper.MapperNameOrder,
            QueryMapper.MapperSkip,
            QueryMapper.MapperLimit,
            QueryMapper.MapperQueryBuild {

        private final Class<T> entityClass;
        private final DocumentCollection collection;

        private final List<Condition> conditions = new ArrayList<>();
        private final List<OrderClause> sortOrders = new ArrayList<>();

        private String currentProperty;
        private String currentOrderProperty;
        private boolean currentNegated = false;
        private Combine nextCombine = Combine.AND;

        private long skipCount = 0;
        private long limitCount = -1;

        public SelectMapper(Class<T> entityClass, DocumentCollection collection) {
            this.entityClass = Objects.requireNonNull(entityClass, "entityClass");
            this.collection = Objects.requireNonNull(collection, "collection");
        }

        @Override
        public QueryMapper.MapperNameCondition where(String name) {
            this.currentProperty = name;
            this.currentNegated = false;
            this.nextCombine = Combine.AND;
            return this;
        }

        @Override
        public QueryMapper.MapperNameCondition and(String name) {
            this.currentProperty = name;
            this.currentNegated = false;
            this.nextCombine = Combine.AND;
            return this;
        }

        @Override
        public QueryMapper.MapperNameCondition or(String name) {
            this.currentProperty = name;
            this.currentNegated = false;
            this.nextCombine = Combine.OR;
            return this;
        }

        @Override
        public QueryMapper.MapperNotCondition not() {
            this.currentNegated = !this.currentNegated;
            return this;
        }

        @Override
        public <V> QueryMapper.MapperWhere eq(V value) {
            addCondition(Op.EQ, value, null);
            return this;
        }

        @Override
        public QueryMapper.MapperWhere like(String pattern) {
            addCondition(Op.LIKE, pattern, null);
            return this;
        }

        @Override
        public <V> QueryMapper.MapperWhere gt(V value) {
            addCondition(Op.GT, value, null);
            return this;
        }

        @Override
        public <V> QueryMapper.MapperWhere gte(V value) {
            addCondition(Op.GTE, value, null);
            return this;
        }

        @Override
        public <V> QueryMapper.MapperWhere lt(V value) {
            addCondition(Op.LT, value, null);
            return this;
        }

        @Override
        public <V> QueryMapper.MapperWhere lte(V value) {
            addCondition(Op.LTE, value, null);
            return this;
        }

        @Override
        public <V> QueryMapper.MapperWhere between(V valA, V valB) {
            addCondition(Op.BETWEEN, valA, valB);
            return this;
        }

        @Override
        public <V> QueryMapper.MapperWhere in(Iterable<V> values) {
            List<Object> list = new ArrayList<>();
            if (values != null) {
                for (V v : values) list.add(v);
            }
            addCondition(Op.IN, list, null);
            return this;
        }

        private void addCondition(Op op, Object val1, Object val2) {
            conditions.add(new Condition(currentProperty, op, val1, val2, currentNegated, nextCombine));
            this.currentNegated = false;
        }

        @Override
        public QueryMapper.MapperOrder orderBy(String name) {
            this.currentOrderProperty = name;
            return this;
        }

        @Override
        public QueryMapper.MapperNameOrder asc() {
            if (currentOrderProperty != null) {
                sortOrders.add(new OrderClause(currentOrderProperty, true));
            }
            return this;
        }

        @Override
        public QueryMapper.MapperNameOrder desc() {
            if (currentOrderProperty != null) {
                sortOrders.add(new OrderClause(currentOrderProperty, false));
            }
            return this;
        }

        @Override
        public QueryMapper.MapperSkip skip(long n) {
            this.skipCount = Math.max(0, n);
            return this;
        }

        @Override
        public QueryMapper.MapperLimit limit(long n) {
            this.limitCount = Math.max(0, n);
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <R> List<R> result() {
            return (List<R>) executeQuery().collect(Collectors.toList());
        }

        @Override
        @SuppressWarnings("unchecked")
        public <R> Stream<R> stream() {
            return (Stream<R>) executeQuery();
        }

        @Override
        @SuppressWarnings("unchecked")
        public <R> Optional<R> singleResult() {
            List<T> list = executeQuery().limit(1).collect(Collectors.toList());
            return list.isEmpty() ? Optional.empty() : Optional.of((R) list.get(0));
        }

        private Stream<T> executeQuery() {
            List<Document> matched = findMatchingDocuments(collection, entityClass, conditions);

            if (!sortOrders.isEmpty()) {
                matched.sort((d1, d2) -> {
                    for (OrderClause order : sortOrders) {
                        String docField = EntityMapper.resolveDocumentField(entityClass, order.property);
                        Object v1 = d1.resolvePath(docField);
                        Object v2 = d2.resolvePath(docField);
                        int cmp = compareValues(v1, v2);
                        if (cmp != 0) {
                            return order.ascending ? cmp : -cmp;
                        }
                    }
                    return 0;
                });
            }

            Stream<Document> stream = matched.stream();
            if (skipCount > 0) {
                stream = stream.skip(skipCount);
            }
            if (limitCount >= 0) {
                stream = stream.limit(limitCount);
            }

            return stream.map(doc -> EntityMapper.fromDocument(doc, entityClass));
        }
    }

    // -------------------------------------------------------------------------
    // Delete Query Builder
    // -------------------------------------------------------------------------

    public static class DeleteMapper<T> implements
            QueryMapper.MapperDeleteFrom,
            QueryMapper.MapperDeleteWhere,
            QueryMapper.MapperDeleteNameCondition,
            QueryMapper.MapperDeleteNotCondition,
            QueryMapper.MapperDeleteQueryBuild {

        private final Class<T> entityClass;
        private final DocumentCollection collection;

        private final List<Condition> conditions = new ArrayList<>();

        private String currentProperty;
        private boolean currentNegated = false;
        private Combine nextCombine = Combine.AND;

        public DeleteMapper(Class<T> entityClass, DocumentCollection collection) {
            this.entityClass = Objects.requireNonNull(entityClass, "entityClass");
            this.collection = Objects.requireNonNull(collection, "collection");
        }

        @Override
        public QueryMapper.MapperDeleteNameCondition where(String name) {
            this.currentProperty = name;
            this.currentNegated = false;
            this.nextCombine = Combine.AND;
            return this;
        }

        @Override
        public QueryMapper.MapperDeleteNameCondition and(String name) {
            this.currentProperty = name;
            this.currentNegated = false;
            this.nextCombine = Combine.AND;
            return this;
        }

        @Override
        public QueryMapper.MapperDeleteNameCondition or(String name) {
            this.currentProperty = name;
            this.currentNegated = false;
            this.nextCombine = Combine.OR;
            return this;
        }

        @Override
        public QueryMapper.MapperDeleteNotCondition not() {
            this.currentNegated = !this.currentNegated;
            return this;
        }

        @Override
        public <V> QueryMapper.MapperDeleteWhere eq(V value) {
            addCondition(Op.EQ, value, null);
            return this;
        }

        @Override
        public QueryMapper.MapperDeleteWhere like(String pattern) {
            addCondition(Op.LIKE, pattern, null);
            return this;
        }

        @Override
        public <V> QueryMapper.MapperDeleteWhere gt(V value) {
            addCondition(Op.GT, value, null);
            return this;
        }

        @Override
        public <V> QueryMapper.MapperDeleteWhere gte(V value) {
            addCondition(Op.GTE, value, null);
            return this;
        }

        @Override
        public <V> QueryMapper.MapperDeleteWhere lt(V value) {
            addCondition(Op.LT, value, null);
            return this;
        }

        @Override
        public <V> QueryMapper.MapperDeleteWhere lte(V value) {
            addCondition(Op.LTE, value, null);
            return this;
        }

        @Override
        public <V> QueryMapper.MapperDeleteWhere between(V valA, V valB) {
            addCondition(Op.BETWEEN, valA, valB);
            return this;
        }

        @Override
        public <V> QueryMapper.MapperDeleteWhere in(Iterable<V> values) {
            List<Object> list = new ArrayList<>();
            if (values != null) {
                for (V v : values) list.add(v);
            }
            addCondition(Op.IN, list, null);
            return this;
        }

        private void addCondition(Op op, Object val1, Object val2) {
            conditions.add(new Condition(currentProperty, op, val1, val2, currentNegated, nextCombine));
            this.currentNegated = false;
        }

        @Override
        public void execute() {
            List<Document> matched = findMatchingDocuments(collection, entityClass, conditions);
            for (Document doc : matched) {
                if (doc.getId() != null) {
                    collection.deleteById(doc.getId());
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Shared evaluation logic
    // -------------------------------------------------------------------------

    private static <T> List<Document> findMatchingDocuments(
            DocumentCollection collection, Class<T> entityClass, List<Condition> conditions) {
        String expectedEntity = EntityMapper.getCollectionName(entityClass);
        Predicate<Document> compositePredicate = doc -> {
            Object entityMeta = doc.getRaw("_entity");
            if (entityMeta != null && !expectedEntity.equalsIgnoreCase(entityMeta.toString())) {
                return false;
            }
            if (conditions.isEmpty()) return true;

            boolean result = true;
            for (int i = 0; i < conditions.size(); i++) {
                Condition cond = conditions.get(i);
                boolean matches = evalCondition(doc, entityClass, cond);
                if (i == 0) {
                    result = matches;
                } else if (cond.combine == Combine.OR) {
                    result = result || matches;
                } else {
                    result = result && matches;
                }
            }
            return result;
        };

        return collection.findAll().stream()
                .filter(compositePredicate)
                .collect(Collectors.toList());
    }

    private static <T> boolean evalCondition(Document doc, Class<T> entityClass, Condition cond) {
        String docField = EntityMapper.resolveDocumentField(entityClass, cond.property);
        Object actual = doc.resolvePath(docField);
        if (actual == Document.PATH_MISSING) {
            actual = null;
        }

        boolean match = switch (cond.op) {
            case EQ -> compareEq(actual, cond.val1);
            case LIKE -> likeMatch(actual != null ? actual.toString() : null, cond.val1 != null ? cond.val1.toString() : null);
            case GT -> compareValues(actual, cond.val1) > 0;
            case GTE -> compareValues(actual, cond.val1) >= 0;
            case LT -> compareValues(actual, cond.val1) < 0;
            case LTE -> compareValues(actual, cond.val1) <= 0;
            case BETWEEN -> {
                int c1 = compareValues(actual, cond.val1);
                int c2 = compareValues(actual, cond.val2);
                yield c1 >= 0 && c2 <= 0;
            }
            case IN -> {
                if (cond.val1 instanceof Iterable<?> iter) {
                    boolean found = false;
                    for (Object target : iter) {
                        if (compareEq(actual, target)) {
                            found = true;
                            break;
                        }
                    }
                    yield found;
                }
                yield false;
            }
        };

        return cond.negated ? !match : match;
    }

    private static boolean compareEq(Object a, Object b) {
        if (a == null) return b == null;
        if (b == null) return false;
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue()) == 0;
        }
        return a.equals(b) || a.toString().equals(b.toString());
    }

    private static int compareValues(Object a, Object b) {
        if (a == null) return b == null ? 0 : -1;
        if (b == null) return 1;
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue());
        }
        if (a instanceof Comparable ca) {
            try {
                return ca.compareTo(b);
            } catch (Exception ignored) {}
        }
        return a.toString().compareTo(b.toString());
    }

    private static boolean likeMatch(String text, String pattern) {
        if (text == null || pattern == null) return false;
        String regex = Pattern.quote(pattern)
                .replace("%", "\\E.*\\Q")
                .replace("_", "\\E.\\Q");
        return Pattern.compile("^" + regex + "$", Pattern.CASE_INSENSITIVE)
                .matcher(text).matches();
    }
}

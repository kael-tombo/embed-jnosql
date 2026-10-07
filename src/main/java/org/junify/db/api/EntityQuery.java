package org.junify.db.api;

import org.junify.db.JunifyDB;
import org.junify.db.adapter.jnosql.EntityMapper;
import org.junify.db.nosql.document.DocumentCollection;
import org.junify.db.nosql.document.Query;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Fluent, type-safe entity query builder designed for Java developer productivity.
 *
 * <p>Queries are evaluated by the native document engine
 * ({@link DocumentCollection#find(Query)}): the fluent layer maps entity fields to
 * document fields and compiles the filter to a {@link Query} predicate. No text query
 * language is parsed or executed here.
 *
 * <p>Example usage:
 * <pre>{@code
 * List<Product> cheap = db.from(Product.class)
 *                         .where("price < ?", 25.0)
 *                         .orderBy("name")
 *                         .limit(10)
 *                         .list();
 *
 * Optional<Product> item = db.from(Product.class)
 *                            .where("id = ?", "p-100")
 *                            .first();
 * }</pre>
 *
 * @param <T> The target Java entity or record class.
 */
public class EntityQuery<T> {

    private final JunifyDB db;
    private final Class<T> entityClass;
    private final String collectionName;
    private String whereClause;
    private final List<Object> parameters = new ArrayList<>();
    private String orderByField;
    private boolean ascending = true;
    private int limit = -1;
    private int offset = -1;

    public EntityQuery(JunifyDB db, Class<T> entityClass) {
        this.db = Objects.requireNonNull(db, "JunifyDB instance cannot be null");
        this.entityClass = Objects.requireNonNull(entityClass, "Entity class cannot be null");
        this.collectionName = EntityMapper.getCollectionName(entityClass);
    }

    /**
     * Sets the filter for this query with optional positional parameters.
     *
     * <p>The accepted form is a document field filter — one or more
     * {@code field OP ?} terms combined with {@code AND}, where {@code OP} is one of
     * {@code =}, {@code !=}, {@code <>}, {@code >}, {@code >=}, {@code <}, {@code <=}.
     * Values are always bound through the {@code ?} placeholders; they are never
     * interpolated into the filter text.
     *
     * @param clause field filter (e.g. {@code "age > ? AND status = ?"})
     * @param params positional parameter values, in placeholder order
     */
    public EntityQuery<T> where(String clause, Object... params) {
        this.whereClause = clause;
        this.parameters.clear();
        if (params != null) {
            Collections.addAll(this.parameters, params);
        }
        return this;
    }

    /**
     * Orders the query results by the specified field ascending. A trailing
     * {@code ASC}/{@code DESC} token is honored, so {@code orderBy("price DESC")}
     * and {@code orderBy("price", false)} are equivalent.
     */
    public EntityQuery<T> orderBy(String field) {
        String trimmed = field == null ? "" : field.trim();
        String upper = trimmed.toUpperCase();
        if (upper.endsWith(" DESC")) {
            return orderBy(trimmed.substring(0, trimmed.length() - 5).trim(), false);
        }
        if (upper.endsWith(" ASC")) {
            return orderBy(trimmed.substring(0, trimmed.length() - 4).trim(), true);
        }
        return orderBy(trimmed, true);
    }

    /**
     * Orders the query results by the specified field.
     */
    public EntityQuery<T> orderBy(String field, boolean ascending) {
        this.orderByField = field;
        this.ascending = ascending;
        return this;
    }

    /**
     * Limits the maximum number of entities returned.
     */
    public EntityQuery<T> limit(int limit) {
        this.limit = limit;
        return this;
    }

    /**
     * Offsets the query results.
     */
    public EntityQuery<T> offset(int offset) {
        this.offset = offset;
        return this;
    }

    /**
     * Executes the query and returns all matching entities as a List.
     */
    public List<T> list() {
        DocumentCollection collection = resolveCollection();
        if (collection == null) {
            return List.of();
        }
        return collection.find(toQuery()).stream()
                .map(doc -> EntityMapper.fromDocument(doc, entityClass))
                .collect(Collectors.toList());
    }

    /**
     * Returns a sequential Stream of matching entities.
     */
    public Stream<T> stream() {
        return list().stream();
    }

    /**
     * Executes the query and returns the first matching entity, or Optional.empty().
     */
    public Optional<T> first() {
        int originalLimit = this.limit;
        this.limit = 1;
        try {
            List<T> results = list();
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } finally {
            this.limit = originalLimit;
        }
    }

    /**
     * Counts the total number of matching entities.
     */
    public long count() {
        DocumentCollection collection = resolveCollection();
        if (collection == null) {
            return 0;
        }
        return collection.count(toQuery());
    }

    /**
     * Resolves the backing collection without creating it. A query against a
     * collection that does not exist matches nothing, exactly as a read on an
     * unknown collection should — reads never materialize an empty collection.
     */
    private DocumentCollection resolveCollection() {
        if (!db.getCollectionNames().contains(collectionName)) {
            return null;
        }
        return db.documentCollection(collectionName);
    }

    private Query toQuery() {
        Query query = buildPredicate();
        if (orderByField != null && !orderByField.isBlank()) {
            query.sortBy(orderByField.trim(), ascending ? Query.SortOrder.ASC : Query.SortOrder.DESC);
        }
        if (limit > 0) {
            query.limit(limit);
        }
        if (offset > 0) {
            query.offset(offset);
        }
        return query;
    }

    /**
     * Compiles the {@code field OP ?} filter into a {@link Query} predicate tree.
     * Terms are combined with logical AND, which is the only composition the
     * fluent builder advertises.
     */
    private Query buildPredicate() {
        if (whereClause == null || whereClause.isBlank()) {
            return Query.all();
        }
        String[] terms = whereClause.trim().split("(?i)\\s+AND\\s+");
        Query combined = null;
        int paramIndex = 0;
        for (String rawTerm : terms) {
            String term = rawTerm.trim();
            if (term.isEmpty()) {
                continue;
            }
            int opIndex = indexOfOperator(term);
            if (opIndex < 0) {
                throw new IllegalArgumentException(
                        "Unsupported filter term '" + term + "': expected 'field OP ?'");
            }
            String field = term.substring(0, opIndex).trim();
            String operator = readOperator(term, opIndex);
            String rhs = term.substring(opIndex + operator.length()).trim();
            if (!"?".equals(rhs)) {
                throw new IllegalArgumentException(
                        "Unsupported filter term '" + term + "': values must be bound with '?'");
            }
            if (field.isEmpty()) {
                throw new IllegalArgumentException("Missing field name in filter term '" + term + "'");
            }
            Object value = paramIndex < parameters.size() ? parameters.get(paramIndex++) : null;
            Query termQuery = predicateFor(field, operator, value);
            combined = combined == null ? termQuery : combined.and(termQuery);
        }
        return combined != null ? combined : Query.all();
    }

    /** Returns the index of the first comparison operator in the term, or -1. */
    private static int indexOfOperator(String term) {
        for (String op : new String[]{">=", "<=", "!=", "<>", "=", ">", "<"}) {
            int idx = term.indexOf(op);
            if (idx > 0) {
                return idx;
            }
        }
        return -1;
    }

    /** Reads the operator starting at {@code index}, preferring the two-character forms. */
    private static String readOperator(String term, int index) {
        if (index + 1 < term.length()) {
            String two = term.substring(index, index + 2);
            if (two.equals(">=") || two.equals("<=") || two.equals("!=") || two.equals("<>")) {
                return two;
            }
        }
        return term.substring(index, index + 1);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Query predicateFor(String field, String operator, Object value) {
        switch (operator) {
            case "=":
                return Query.eq(field, value);
            case "!=":
            case "<>":
                return Query.ne(field, value);
            case ">":
                if (value instanceof Number n) {
                    return Query.gt(field, n);
                }
                return Query.matching(doc -> compare(doc, field, value) > 0);
            case ">=":
                if (value instanceof Number n) {
                    return Query.gte(field, n);
                }
                return Query.matching(doc -> compare(doc, field, value) >= 0);
            case "<":
                if (value instanceof Number n) {
                    return Query.lt(field, n);
                }
                return Query.matching(doc -> compare(doc, field, value) < 0);
            case "<=":
                if (value instanceof Number n) {
                    return Query.lte(field, n);
                }
                return Query.matching(doc -> compare(doc, field, value) <= 0);
            default:
                throw new IllegalArgumentException("Unsupported filter operator: " + operator);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static int compare(org.junify.db.nosql.document.Document doc, String field, Object value) {
        Object actual = doc.getRaw(field);
        if (actual == null || value == null) {
            return Integer.MIN_VALUE; // missing field never satisfies an ordering comparison
        }
        if (actual instanceof Comparable a && value.getClass().isInstance(actual)) {
            return a.compareTo(value);
        }
        return actual.toString().compareTo(value.toString());
    }
}

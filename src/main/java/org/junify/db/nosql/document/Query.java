package org.junify.db.nosql.document;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

public class Query {

    private final Predicate<Document> docPredicate;
    private SortOrder sortOrder;
    private String sortField;
    private int limit = Integer.MAX_VALUE;
    private int offset = 0;
    private Set<QueryHint> hints = new java.util.HashSet<>();
    private String forceIndexName;
    /**
     * The field name targeted by an equality predicate, if this query was
     * created via {@link #eq(String, Object)}. Used by the index optimizer
     * to choose an index without scanning live data.
     */
    private String indexedField;
    /**
     * The value targeted by that equality predicate (audit R-18 / 12-IX-01):
     * lets the planner perform a point lookup instead of walking the whole
     * index. {@code null} when the query has no single equality target.
     */
    private Object indexedValue;

    private Query(Predicate<Document> docPredicate) {
        this.docPredicate = docPredicate;
        this.sortOrder = SortOrder.NONE;
        this.sortField = null;
    }

    /**
     * Package-private constructor for QueryParser.
     */
    Query(Predicate<Document> docPredicate, SortOrder sortOrder, String sortField, int limit, int offset) {
        this.docPredicate = docPredicate;
        this.sortOrder = sortOrder != null ? sortOrder : SortOrder.NONE;
        this.sortField = sortField;
        this.limit = limit;
        this.offset = offset;
    }

    public static Query all() {
        return new Query(doc -> true);
    }

    /**
     * Builds a query from an arbitrary document predicate.
     *
     * <p>This is the escape hatch for the fluent entity layer ({@code db.from(Entity.class)}):
     * a {@code field OP value} filter that is not covered by a typed factory (for example an
     * ordering comparison on a non-numeric field) is still expressed as a plain
     * {@link Document} predicate here, never as a query string handed to a different engine.</p>
     */
    public static Query matching(Predicate<Document> docPredicate) {
        return new Query(Objects.requireNonNull(docPredicate, "docPredicate"));
    }

    public static Query eq(String field, Object value) {
        var q = new Query(doc -> {
            if (!doc.has(field)) return false;
            var v = doc.getRaw(field);
            if (v == null) return value == null;
            return v.equals(value);
        });
        q.indexedField = field;
        q.indexedValue = value;
        return q;
    }

    public static Query fromQuery(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return all();
        }
        String trimmed = queryString.trim();
        if (trimmed.contains("=")) {
            int eqIndex = trimmed.indexOf('=');
            String field = trimmed.substring(0, eqIndex).trim();
            String value = trimmed.substring(eqIndex + 1).trim();
            if (value.startsWith("\"") && value.endsWith("\"")) {
                value = value.substring(1, value.length() - 1);
            }
            return eq(field, value);
        }
        return all();
    }

    public static Query contains(String field, String substring) {
        return new Query(doc -> {
            if (!doc.has(field)) return false;
            var v = doc.getRaw(field);
            return v != null && v.toString().contains(substring);
        });
    }

    public static Query gt(String field, Number value) {
        return new Query(doc -> {
            if (!doc.has(field)) return false;
            var v = doc.getRaw(field);
            if (v instanceof Number n) {
                return n.doubleValue() > value.doubleValue();
            }
            return false;
        });
    }

    public static Query gte(String field, Number value) {
        return new Query(doc -> {
            if (!doc.has(field)) return false;
            var v = doc.getRaw(field);
            if (v instanceof Number n) {
                return n.doubleValue() >= value.doubleValue();
            }
            return false;
        });
    }

    public static Query lt(String field, Number value) {
        return new Query(doc -> {
            if (!doc.has(field)) return false;
            var v = doc.getRaw(field);
            if (v instanceof Number n) {
                return n.doubleValue() < value.doubleValue();
            }
            return false;
        });
    }

    public static Query lte(String field, Number value) {
        return new Query(doc -> {
            if (!doc.has(field)) return false;
            var v = doc.getRaw(field);
            if (v instanceof Number n) {
                return n.doubleValue() <= value.doubleValue();
            }
            return false;
        });
    }

    public static Query in(String field, List<?> values) {
        return new Query(doc -> {
            if (!doc.has(field)) return false;
            var v = doc.getRaw(field);
            return values.contains(v);
        });
    }

    public static Query ne(String field, Object value) {
        return new Query(doc -> {
            if (!doc.has(field)) return false;
            var v = doc.getRaw(field);
            if (v == null) return value != null;
            return !v.equals(value);
        });
    }

    public static Query exists(String field) {
        return new Query(doc -> doc.has(field));
    }

    public static Query between(String field, Number lower, Number upper) {
        return new Query(doc -> {
            if (!doc.has(field)) return false;
            var v = doc.getRaw(field);
            if (v instanceof Number n) {
                return n.doubleValue() >= lower.doubleValue() && n.doubleValue() <= upper.doubleValue();
            }
            return false;
        });
    }

    public static Query regex(String field, String pattern) {
        var regex = java.util.regex.Pattern.compile(pattern);
        return new Query(doc -> {
            if (!doc.has(field)) return false;
            var v = doc.getRaw(field);
            return v != null && regex.matcher(v.toString()).find();
        });
    }

    public Query and(Query other) {
        var q = new Query(this.docPredicate.and(other.docPredicate));
        q.sortOrder = this.sortOrder;
        q.sortField = this.sortField;
        q.limit = this.limit;
        q.offset = this.offset;
        // Preserve indexed field only if both sides target the same field
        if (this.indexedField != null && this.indexedField.equals(other.indexedField)) {
            q.indexedField = this.indexedField;
        }
        return q;
    }

    public Query or(Query other) {
        var q = new Query(this.docPredicate.or(other.docPredicate));
        q.sortOrder = this.sortOrder;
        q.sortField = this.sortField;
        q.limit = this.limit;
        q.offset = this.offset;
        // OR across different fields cannot be resolved via a single index
        if (this.indexedField != null && this.indexedField.equals(other.indexedField)) {
            q.indexedField = this.indexedField;
        }
        return q;
    }

    public Query sortBy(String field, SortOrder order) {
        this.sortField = field;
        this.sortOrder = order;
        return this;
    }

    public Query sortByAsc(String field) {
        return sortBy(field, SortOrder.ASC);
    }

    public Query sortByDesc(String field) {
        return sortBy(field, SortOrder.DESC);
    }

    public Query limit(int limit) {
        this.limit = limit;
        return this;
    }

    public Query offset(int offset) {
        this.offset = offset;
        return this;
    }

    public Query page(int page, int pageSize) {
        this.offset = page * pageSize;
        this.limit = pageSize;
        return this;
    }

    public Query withHint(QueryHint hint) {
        this.hints.add(hint);
        return this;
    }

    public Query withHint(String indexName) {
        this.hints.add(QueryHint.FORCE_INDEX);
        this.forceIndexName = indexName;
        return this;
    }

    public Query noIndex() {
        this.hints.add(QueryHint.NO_INDEX);
        return this;
    }

    public Query parallel() {
        this.hints.add(QueryHint.PARALLEL);
        return this;
    }

    public boolean hasHint(QueryHint hint) {
        return hints.contains(hint);
    }

    public String getForceIndexName() {
        return forceIndexName;
    }

    /**
     * Returns the field name targeted by an equality predicate, or {@code null}
     * if this query was not built via {@link #eq(String, Object)} or if the
     * equality field was lost through composition ({@link #and}/{@link #or}).
     * Used by the index optimizer to select an appropriate index without a
     * full-collection scan.
     */
    public String getIndexedField() {
        return indexedField;
    }

    /**
     * The equality value for {@link #getIndexedField()}, or {@code null}.
     */
    public Object getIndexedValue() {
        return indexedValue;
    }

    public Set<QueryHint> getHints() {
        return Set.copyOf(hints);
    }

    public Predicate<Document> docPredicate() {
        return docPredicate;
    }

    public SortOrder sortOrder() {
        return sortOrder;
    }

    String sortField() {
        return sortField;
    }

    public int limit() {
        return limit;
    }

    public List<Document> apply(List<Document> documents) {
        return documents.stream().filter(docPredicate).collect(java.util.stream.Collectors.toList());
    }

    public int offset() {
        return offset;
    }

    @Override
    public String toString() {
        return "Query{pred=" + docPredicate + ", sort=" + sortOrder + " " + sortField + 
               ", limit=" + limit + ", offset=" + offset + ", hints=" + hints + "}";
    }

    public enum SortOrder { ASC, DESC, NONE }

    public enum QueryHint {
        FORCE_INDEX,
        NO_INDEX,
        PARALLEL,
        CACHE_RESULT,
        NO_CACHE
    }
}
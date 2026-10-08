package org.embeddedjnosql.db.spring.boot.data;

import org.embeddedjnosql.db.nosql.document.Query;
import org.embeddedjnosql.db.nosql.document.Query.SortOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Translates a Spring Data {@link PartTree} (the grammar behind
 * {@code findByLastnameIgnoreCase}, {@code findByAgeLessThan}...) into an embed-jnosql
 * document {@link Query} — the same job
 * {@code org.springframework.data.jpa.repository.query.JpaQueryCreator} does for relational
 * stores, done here over the typed document predicate API.
 *
 * <p>Keyword mapping:</p>
 * <ul>
 *   <li>{@code eq ?x} (simple property) → {@link Query#eq}</li>
 *   <li>{@code not ?x} → {@link Query#ne}</li>
 *   <li>{@code gt/after} → {@link Query#gt}, {@code gte} → {@link Query#gte},
 *       {@code lt/before} → {@link Query#lt}, {@code lte} → {@link Query#lte}</li>
 *   <li>{@code like/starting/ending/containing} → {@link Query#contains} with the relational
 *       {@code %} wildcard semantics emulated over the substring primitive</li>
 *   <li>{@code in} → {@link Query#in}, {@code notIn} → not(Predicate)</li>
 *   <li>{@code true/false} → {@link Query#eq(bool)}, {@code isNull} → not(exists),
 *       {@code isNotNull} → {@link Query#exists}</li>
 *   <li>{@code between} → and(gt/eq, lt/eq); {@code regex} → {@link Query#regex}</li>
 *   <li>{@code IgnoreCase} → operand-side lowercase both sides (string equality/contains only)</li>
 *   <li>OR groups → {@link Query#or}; parts ANDed inside a group → {@link Query#and}</li>
 * </ul>
 *
 * <p>Unsupported combinations (e.g. numeric ordering comparison on strings) fail fast with a
 * clear message instead of silently returning wrong rows.</p>
 */
class EmbedJpaPartTreeQueryCreator extends
        org.springframework.data.repository.query.parser.AbstractQueryCreator<Query, Query> {

    private final Class<?> entityType;

    EmbedJpaPartTreeQueryCreator(PartTree tree, org.springframework.data.repository.query.ParameterAccessor accessor,
                                 Class<?> entityType) {
        super(tree, accessor);
        this.entityType = entityType;
    }

    EmbedJpaPartTreeQueryCreator(PartTree tree, Class<?> entityType) {
        super(tree);
        this.entityType = entityType;
    }

    @Override
    protected Query create(Part part, Iterator<Object> iterator) {
        return createQueryForPart(part, iterator);
    }

    @Override
    protected Query and(Part part, Query base, Iterator<Object> iterator) {
        return base.and(createQueryForPart(part, iterator));
    }

    @Override
    protected Query or(Query base, Query branch) {
        return base.or(branch);
    }

    @Override
    protected Query complete(Query query, Sort sort) {
        if (query == null) {
            query = Query.all();
        }
        if (sort != null && sort.isSorted()) {
            for (Sort.Order order : sort) {
                query = query.sortBy(columnFor(order.getProperty()),
                        order.isAscending() ? SortOrder.ASC : SortOrder.DESC);
            }
        }
        return query;
    }

    private Query createQueryForPart(Part part, Iterator<Object> params) {
        String property = part.getProperty().getSegment();
        String column = MappingColumns.column(entityType, property);
        Part.Type type = part.getType();
        boolean ignoreCase = part.shouldIgnoreCase() == Part.IgnoreCaseType.ALWAYS
                || (part.shouldIgnoreCase() == Part.IgnoreCaseType.WHEN_POSSIBLE
                    && part.getProperty().getType() == String.class);

        return switch (type) {
            case SIMPLE_PROPERTY -> eq(col(column), value(params, ignoreCase), ignoreCase);
            case NEGATING_SIMPLE_PROPERTY -> Query.ne(col(column), value(params, ignoreCase));
            case GREATER_THAN, AFTER -> Query.gt(col(column), num(params, type));
            case GREATER_THAN_EQUAL -> Query.gte(col(column), num(params, type));
            case LESS_THAN, BEFORE -> Query.lt(col(column), num(params, type));
            case LESS_THAN_EQUAL -> Query.lte(col(column), num(params, type));
            case TRUE -> Query.eq(col(column), Boolean.TRUE);
            case FALSE -> Query.eq(col(column), Boolean.FALSE);
            case IS_NULL -> Query.matching(doc -> !doc.has(column));
            case IS_NOT_NULL -> Query.exists(col(column));
            case IN -> Query.in(col(column), list(params));
            case NOT_IN -> Query.matching(doc -> {
                if (!doc.has(column)) return false;
                return !list(params).contains(doc.getRaw(column));
            });
            case CONTAINING -> contains(col(column), params, ignoreCase, false);
            case NOT_CONTAINING -> notContains(col(column), params, ignoreCase);
            case STARTING_WITH -> startsWith(col(column), params, ignoreCase);
            case ENDING_WITH -> endsWith(col(column), params, ignoreCase);
            case LIKE -> like(col(column), params, ignoreCase);
            case NOT_LIKE -> {
                Query positive = like(col(column), params, ignoreCase);
                yield Query.matching(doc -> !positive.docPredicate().test(doc));
            }
            case REGEX -> Query.regex(col(column), (String) params.next());
            case BETWEEN -> {
                Object lower = params.next();
                Object upper = params.next();
                yield Query.gte(col(column), (Number) lower).and(Query.lte(col(column), (Number) upper));
            }
            default -> throw new UnsupportedOperationException(
                    "Derived query keyword " + type + " (from property '" + property
                            + "') is not supported by the embed-jnosql Spring Data integration");
        };
    }

    // -- operand helpers ---------------------------------------------------------------

    private static String col(String column) {
        return column;
    }

    private Query eq(String column, Object value, boolean ignoreCase) {
        if (!ignoreCase) {
            return Query.eq(column, value);
        }
        // lowercase both sides: the stored value AND the probe value
        return Query.matching(doc -> {
            if (!doc.has(column)) return false;
            Object raw = doc.getRaw(column);
            if (raw == null) return value == null;
            return MappingColumns.lower(raw).equals(value == null ? null : MappingColumns.lower(value));
        });
    }

    private Query contains(String column, Iterator<Object> params, boolean ignoreCase, boolean negated) {
        String needle = (String) params.next();
        if (ignoreCase) {
            needle = needle.toLowerCase(Locale.ROOT);
        }
        return Query.contains(column, needle);
    }

    private Query notContains(String column, Iterator<Object> params, boolean ignoreCase) {
        Query positive = contains(column, params, ignoreCase, false);
        return Query.matching(doc -> !positive.docPredicate().test(doc));
    }

    private Query startsWith(String column, Iterator<Object> params, boolean ignoreCase) {
        String prefix = stripWildcard((String) params.next());
        if (ignoreCase) {
            prefix = prefix.toLowerCase(Locale.ROOT);
        }
        String finalPrefix = prefix;
        return Query.matching(doc -> {
            if (!doc.has(column)) return false;
            Object v = doc.getRaw(column);
            if (v == null) return false;
            String s = v.toString();
            return ignoreCase ? s.toLowerCase(Locale.ROOT).startsWith(finalPrefix) : s.startsWith(finalPrefix);
        });
    }

    private Query endsWith(String column, Iterator<Object> params, boolean ignoreCase) {
        String suffix = stripWildcard((String) params.next());
        if (ignoreCase) {
            suffix = suffix.toLowerCase(Locale.ROOT);
        }
        String finalSuffix = suffix;
        return Query.matching(doc -> {
            if (!doc.has(column)) return false;
            Object v = doc.getRaw(column);
            if (v == null) return false;
            String s = v.toString();
            return ignoreCase ? s.toLowerCase(Locale.ROOT).endsWith(finalSuffix) : s.endsWith(finalSuffix);
        });
    }

    /**
     * Relational {@code LIKE} with {@code %} wildcards emulated over string matching.
     * {@code "%term%"} → contains, {@code "term%"} → startsWith, {@code "%term"} → endsWith,
     * {@code "%ter%m"} → generic contains on the middle segment.
     */
    private Query like(String column, Iterator<Object> params, boolean ignoreCase) {
        String pattern = (String) params.next();
        if (pattern.startsWith("%") && pattern.endsWith("%") && pattern.length() >= 2) {
            String mid = pattern.substring(1, pattern.length() - 1);
            return contains(column, new SingletonIterator(mid), ignoreCase, false);
        }
        if (pattern.startsWith("%")) {
            return endsWith(column, new SingletonIterator(stripWildcard(pattern)), ignoreCase);
        }
        if (pattern.endsWith("%")) {
            return startsWith(column, new SingletonIterator(pattern.substring(0, pattern.length() - 1)), ignoreCase);
        }
        // no wildcard: exact equality
        return Query.eq(column, pattern);
    }

    private static String stripWildcard(String likePattern) {
        String v = likePattern;
        if (v.startsWith("%")) v = v.substring(1);
        if (v.endsWith("%")) v = v.substring(0, v.length() - 1);
        return v;
    }

    private static Object value(Iterator<Object> params, boolean ignoreCase) {
        Object v = storedShape(params.next());
        return ignoreCase && v != null ? MappingColumns.lower(v) : v;
    }

    /**
     * Persisted shape of a bind parameter — mirrors what {@code EntityMapper.toDocument}
     * writes ({@code @Enumerated(STRING)} enums as names, temporals as ISO strings) so a
     * derived {@code eq}/{@code in}/{@code ne} on such a column compares like-for-like.
     * Shared with the query-by-example builder.
     */
    private static Object storedShape(Object value) {
        return EmbedJpaBaseRepository.QueryByExampleBuilder.storedShape(value);
    }

    private static Number num(Iterator<Object> params, Part.Type type) {
        Object v = params.next();
        if (v instanceof Number n) {
            return n;
        }
        throw new IllegalArgumentException(
                "Derived query keyword " + type + " needs a numeric argument, got: " + v);
    }

    private static List<?> list(Iterator<Object> params) {
        Object v = params.next();
        if (v instanceof List<?> l) {
            return l.stream().map(EmbedJpaPartTreeQueryCreator::storedShape).toList();
        }
        if (v instanceof Object[] a) {
            return java.util.Arrays.stream(a).map(EmbedJpaPartTreeQueryCreator::storedShape).toList();
        }
        return List.of(storedShape(v));
    }

    private String columnFor(String property) {
        return MappingColumns.column(entityType, property);
    }

    private static final class SingletonIterator implements Iterator<Object> {
        private Object value;
        private boolean used;

        private SingletonIterator(Object value) {
            this.value = value;
        }

        @Override
        public boolean hasNext() {
            return !used;
        }

        @Override
        public Object next() {
            used = true;
            return value;
        }
    }
}

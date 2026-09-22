package org.junify.db.nosql.document;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * MongoDB-style Query Parser for JunifyDB.
 * 
 * Parses JSON queries like:
 * {"age": {"$gt": 18, "$lt": 65}, "name": {"$regex": "^A"}}
 * 
 * Supported operators:
 * - $eq, $ne, $gt, $gte, $lt, $lte
 * - $in, $nin
 * - $regex
 * - $exists
 * - $and, $or
 */
public class QueryParser {

    /**
     * Thrown for malformed queries: unknown operators, operator values of the
     * wrong type, invalid regex patterns, or bad logical-combinator shapes.
     * The console HTTP layer maps this to 400; it must never surface as 500.
     */
    public static class QueryFormatException extends IllegalArgumentException {
        public QueryFormatException(String message) {
            super(message);
        }
    }

    private QueryParser() {
        // Utility class
    }

    /**
     * Parse a MongoDB-style query map into a Query object.
     *
     * @throws IllegalArgumentException for malformed queries: unknown operators,
     *         operator values of the wrong type, or invalid regex patterns. The
     *         console maps this to HTTP 400. (R-45/R-46/R-47: these conditions
     *         previously either silently matched nothing or silently matched
     *         everything.)
     */
    public static Query parse(Map<String, Object> queryMap) {
        if (queryMap == null || queryMap.isEmpty()) {
            return Query.all();
        }

        Predicate<Document> combined = doc -> true;

        // R-46: top-level $and / $or are logical combinators over whole sub-queries,
        // not field names. They were previously only handled inside parseOperators,
        // which is unreachable for them (their values are arrays, so parseField
        // fell through to "simple equality on a field named $and" and every
        // document failed the test). Extract them before the field loop.
        // Both combinators are ANDed with the rest of the top-level conditions
        // (MongoDB semantics): {"$or":[...], "stock":{"$gt":5}} means
        // (a OR b) AND stock > 5. The OR itself lives inside parseOr.
        if (queryMap.containsKey("$and")) {
            combined = combined.and(parseAnd(castQueries(queryMap.get("$and"), "$and")));
        }
        if (queryMap.containsKey("$or")) {
            combined = combined.and(parseOr(castQueries(queryMap.get("$or"), "$or")));
        }

        for (Map.Entry<String, Object> entry : queryMap.entrySet()) {
            String field = entry.getKey();
            if (field.equals("$and") || field.equals("$or")) {
                continue; // handled above
            }
            Object criteria = entry.getValue();

            Predicate<Document> fieldPredicate = parseField(field, criteria);
            combined = combined.and(fieldPredicate);
        }

        return new Query(combined, null, null, Integer.MAX_VALUE, 0);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castQueries(Object value, String operator) {
        if (!(value instanceof List<?> list)) {
            throw new QueryFormatException(operator + " expects an array of query objects");
        }
        for (Object o : list) {
            if (!(o instanceof Map)) {
                throw new QueryFormatException(operator + " expects an array of query objects, found "
                        + o.getClass().getSimpleName());
            }
        }
        return (List<Map<String, Object>>) (List<?>) list;
    }

    @SuppressWarnings("unchecked")
    private static Predicate<Document> parseField(String field, Object criteria) {
        if (criteria instanceof Map) {
            return parseOperators(field, (Map<String, Object>) criteria);
        } else {
            // Simple equality
            return Query.eq(field, criteria).docPredicate();
        }
    }

    @SuppressWarnings("unchecked")
    private static Predicate<Document> parseOperators(String field, Map<String, Object> operators) {
        Predicate<Document> combined = doc -> true;

        for (Map.Entry<String, Object> op : operators.entrySet()) {
            String operator = op.getKey();
            Object value = op.getValue();

            Predicate<Document> opPredicate = switch (operator) {
                case "$eq" -> parseEq(field, value);
                case "$ne" -> parseNe(field, value);
                case "$gt" -> parseGt(field, value);
                case "$gte" -> parseGte(field, value);
                case "$lt" -> parseLt(field, value);
                case "$lte" -> parseLte(field, value);
                case "$in" -> parseIn(field, requireList(field, "$in", value));
                case "$nin" -> parseNin(field, requireList(field, "$nin", value));
                case "$regex" -> parseRegex(field, value.toString());
                case "$exists" -> parseExists(field, requireBoolean(field, value));
                case "$and" -> parseAnd(castQueries(value, "$and"));
                case "$or" -> parseOr(castQueries(value, "$or"));
                // R-47: an unknown operator used to be silently ignored
                // (default -> doc -> true), so a typo like "$gteX" returned every
                // document as if no filter had been sent. Refuse it loudly.
                default -> throw new QueryFormatException(
                        "Unsupported query operator '" + operator + "' for field '" + field + "'");
            };

            combined = combined.and(opPredicate);
        }

        return combined;
    }

    private static Predicate<Document> parseEq(String field, Object value) {
        return doc -> {
            if (!doc.has(field)) return value == null;
            var v = doc.getRaw(field);
            return v != null ? v.equals(value) : value == null;
        };
    }

    private static Predicate<Document> parseNe(String field, Object value) {
        return parseEq(field, value).negate();
    }

    private static Predicate<Document> parseGt(String field, Object value) {
        return doc -> {
            if (!doc.has(field)) return false;
            var v = doc.getRaw(field);
            if (v instanceof Number n && value instanceof Number nv) {
                return n.doubleValue() > nv.doubleValue();
            }
            return false;
        };
    }

    private static Predicate<Document> parseGte(String field, Object value) {
        return doc -> {
            if (!doc.has(field)) return false;
            var v = doc.getRaw(field);
            if (v instanceof Number n && value instanceof Number nv) {
                return n.doubleValue() >= nv.doubleValue();
            }
            return false;
        };
    }

    private static Predicate<Document> parseLt(String field, Object value) {
        return doc -> {
            if (!doc.has(field)) return false;
            var v = doc.getRaw(field);
            if (v instanceof Number n && value instanceof Number nv) {
                return n.doubleValue() < nv.doubleValue();
            }
            return false;
        };
    }

    private static Predicate<Document> parseLte(String field, Object value) {
        return doc -> {
            if (!doc.has(field)) return false;
            var v = doc.getRaw(field);
            if (v instanceof Number n && value instanceof Number nv) {
                return n.doubleValue() <= nv.doubleValue();
            }
            return false;
        };
    }

    private static Predicate<Document> parseIn(String field, List<Object> values) {
        return doc -> {
            if (!doc.has(field)) return false;
            var v = doc.getRaw(field);
            return values.contains(v);
        };
    }

    private static Predicate<Document> parseNin(String field, List<Object> values) {
        return parseIn(field, values).negate();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> requireList(String field, String operator, Object value) {
        if (!(value instanceof List<?> list)) {
            throw new QueryFormatException(operator + " expects an array for field '" + field + "'");
        }
        return (List<Object>) list;
    }

    private static Boolean requireBoolean(String field, Object value) {
        if (!(value instanceof Boolean b)) {
            throw new QueryFormatException("$exists expects true or false for field '" + field + "'");
        }
        return b;
    }

    /**
     * R-45: regex matching is substring ({@link java.util.regex.Matcher#find}),
     * not whole-string. {@link String#matches} implicitly anchors both ends, so
     * the documented pattern style {"name": {"$regex": "^A"}} worked but the
     * equally valid substring pattern "Key" never matched "Keyboard". Users who
     * want whole-string matching write "^Keyboard$".
     */
    private static Predicate<Document> parseRegex(String field, String pattern) {
        java.util.regex.Pattern compiled;
        try {
            compiled = java.util.regex.Pattern.compile(pattern);
        } catch (java.util.regex.PatternSyntaxException e) {
            throw new QueryFormatException(
                    "Invalid $regex pattern for field '" + field + "': " + e.getMessage());
        }
        return doc -> {
            if (!doc.has(field)) return false;
            var v = doc.getRaw(field);
            if (v == null) return false;
            return compiled.matcher(v.toString()).find();
        };
    }

    private static Predicate<Document> parseExists(String field, Boolean shouldExist) {
        return doc -> doc.has(field) == shouldExist;
    }

    private static Predicate<Document> parseAnd(List<Map<String, Object>> queries) {
        Predicate<Document> combined = doc -> true;
        for (Map<String, Object> q : queries) {
            combined = combined.and(parse(q).docPredicate());
        }
        return combined;
    }

    private static Predicate<Document> parseOr(List<Map<String, Object>> queries) {
        Predicate<Document> combined = doc -> false;
        for (Map<String, Object> q : queries) {
            combined = combined.or(parse(q).docPredicate());
        }
        return combined;
    }
}

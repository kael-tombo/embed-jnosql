package org.embeddedjnosql.db.nosql.query;

import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.document.Query;

import java.util.function.Function;

public class QueryBuilder {

    private QueryBuilder() {
    }

    public static SelectQueryBuilder selectFrom(String collection) {
        return new SelectQueryBuilder(collection);
    }

    public static class SelectQueryBuilder {
        private final String collection;
        private String whereField;
        private Object whereValue;
        private WhereCondition condition = WhereCondition.EQUALS;
        private String sortField;
        private boolean sortAscending = true;
        private int limit = Integer.MAX_VALUE;
        private int offset = 0;

        private SelectQueryBuilder(String collection) {
            this.collection = collection;
        }

        public SelectQueryBuilder where(String field) {
            this.whereField = field;
            return this;
        }

        public SelectQueryBuilder eq(Object value) {
            this.whereValue = value;
            this.condition = WhereCondition.EQUALS;
            return this;
        }

        public SelectQueryBuilder ne(Object value) {
            this.whereValue = value;
            this.condition = WhereCondition.NOT_EQUALS;
            return this;
        }

        public SelectQueryBuilder gt(Number value) {
            this.whereValue = value;
            this.condition = WhereCondition.GREATER_THAN;
            return this;
        }

        public SelectQueryBuilder gte(Number value) {
            this.whereValue = value;
            this.condition = WhereCondition.GREATER_THAN_OR_EQUALS;
            return this;
        }

        public SelectQueryBuilder lt(Number value) {
            this.whereValue = value;
            this.condition = WhereCondition.LESS_THAN;
            return this;
        }

        public SelectQueryBuilder lte(Number value) {
            this.whereValue = value;
            this.condition = WhereCondition.LESS_THAN_OR_EQUALS;
            return this;
        }

        public SelectQueryBuilder contains(String value) {
            this.whereValue = value;
            this.condition = WhereCondition.CONTAINS;
            return this;
        }

        public SelectQueryBuilder in(Object... values) {
            this.whereValue = java.util.Arrays.asList(values);
            this.condition = WhereCondition.IN;
            return this;
        }

        public SelectQueryBuilder exists() {
            this.condition = WhereCondition.EXISTS;
            return this;
        }

        public SelectQueryBuilder regex(String pattern) {
            this.whereValue = pattern;
            this.condition = WhereCondition.REGEX;
            return this;
        }

        public SelectQueryBuilder like(String pattern) {
            this.whereValue = pattern;
            this.condition = WhereCondition.LIKE;
            return this;
        }

        public SelectQueryBuilder between(Number lower, Number upper) {
            this.whereValue = new Object[]{lower, upper};
            this.condition = WhereCondition.BETWEEN;
            return this;
        }

        public SelectQueryBuilder orderBy(String field) {
            this.sortField = field;
            return this;
        }

        public SelectQueryBuilder asc() {
            this.sortAscending = true;
            return this;
        }

        public SelectQueryBuilder desc() {
            this.sortAscending = false;
            return this;
        }

        public SelectQueryBuilder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public SelectQueryBuilder offset(int offset) {
            this.offset = offset;
            return this;
        }

        public SelectQueryBuilder page(int page, int pageSize) {
            this.offset = page * pageSize;
            this.limit = pageSize;
            return this;
        }

        public Query build() {
            Query query;
            
            if (whereField == null) {
                query = Query.all();
            } else {
                query = switch (condition) {
                    case EQUALS -> Query.eq(whereField, whereValue);
                    case NOT_EQUALS -> Query.ne(whereField, whereValue);
                    case GREATER_THAN -> Query.gt(whereField, (Number) whereValue);
                    case GREATER_THAN_OR_EQUALS -> Query.gte(whereField, (Number) whereValue);
                    case LESS_THAN -> Query.lt(whereField, (Number) whereValue);
                    case LESS_THAN_OR_EQUALS -> Query.lte(whereField, (Number) whereValue);
                    case CONTAINS -> Query.contains(whereField, (String) whereValue);
                    case IN -> Query.in(whereField, (java.util.List<?>) whereValue);
                    case EXISTS -> Query.exists(whereField);
                    case REGEX -> Query.regex(whereField, (String) whereValue);
                    case LIKE -> Query.regex(whereField, ((String) whereValue).replace("%", ".*"));
                    case BETWEEN -> {
                        var range = (Object[]) whereValue;
                        yield Query.between(whereField, (Number) range[0], (Number) range[1]);
                    }
                    default -> Query.all();
                };
            }

            if (sortField != null) {
                query = query.sortBy(sortField, sortAscending ? Query.SortOrder.ASC : Query.SortOrder.DESC);
            }

            return query.limit(limit).offset(offset);
        }

        public java.util.List<Document> execute(DocumentCollection collection) {
            return collection.find(build());
        }

        public Document executeFirst(DocumentCollection collection) {
            return collection.findOne(build());
        }
    }

    private enum WhereCondition {
        EQUALS,
        NOT_EQUALS,
        GREATER_THAN,
        GREATER_THAN_OR_EQUALS,
        LESS_THAN,
        LESS_THAN_OR_EQUALS,
        CONTAINS,
        IN,
        EXISTS,
        REGEX,
        LIKE,
        BETWEEN
    }

    @FunctionalInterface
    public interface SelectFunction extends Function<DocumentCollection, java.util.List<Document>> {
    }
}

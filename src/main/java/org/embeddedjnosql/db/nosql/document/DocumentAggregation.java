package org.embeddedjnosql.db.nosql.document;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DocumentAggregation {

    public static long count(List<Document> documents) {
        return documents.size();
    }

    public static long count(Query query, List<Document> documents) {
        return query.apply(documents).size();
    }

    public static Optional<Document> min(List<Document> documents, String field) {
        return documents.stream()
                .filter(doc -> doc.has(field))
                .min(Comparator.comparingDouble(doc -> toDouble(doc.getRaw(field))));
    }

    public static Optional<Document> max(List<Document> documents, String field) {
        return documents.stream()
                .filter(doc -> doc.has(field))
                .max(Comparator.comparingDouble(doc -> toDouble(doc.getRaw(field))));
    }

    public static double sum(List<Document> documents, String field) {
        return documents.stream()
                .filter(doc -> doc.has(field))
                .mapToDouble(doc -> toDouble(doc.getRaw(field)))
                .sum();
    }

    public static double avg(List<Document> documents, String field) {
        var values = documents.stream()
                .filter(doc -> doc.has(field))
                .mapToDouble(doc -> toDouble(doc.getRaw(field)))
                .toArray();
        
        if (values.length == 0) return 0;
        return Arrays.stream(values).average().orElse(0);
    }

    public static Map<Object, Long> groupBy(List<Document> documents, String field) {
        return documents.stream()
                .filter(doc -> doc.has(field))
                .collect(Collectors.groupingBy(
                        doc -> doc.getRaw(field),
                        Collectors.counting()
                ));
    }

    public static Map<Object, List<Document>> groupByDocuments(List<Document> documents, String field) {
        return documents.stream()
                .filter(doc -> doc.has(field))
                .collect(Collectors.groupingBy(doc -> doc.getRaw(field)));
    }

    public static <T> Map<T, Long> groupBy(List<Document> documents, String field, Function<Document, T> transformer) {
        return documents.stream()
                .collect(Collectors.groupingBy(
                        transformer,
                        Collectors.counting()
                ));
    }

    public static List<Document> distinct(List<Document> documents, String field) {
        var seen = new HashSet<>();
        return documents.stream()
                .filter(doc -> doc.has(field))
                .filter(doc -> seen.add(doc.getRaw(field)))
                .collect(Collectors.toList());
    }

    public static List<Object> distinctValues(List<Document> documents, String field) {
        return documents.stream()
                .filter(doc -> doc.has(field))
                .map(doc -> doc.getRaw(field))
                .distinct()
                .collect(Collectors.toList());
    }

    public static Optional<Document> first(List<Document> documents) {
        return documents.isEmpty() ? Optional.empty() : Optional.of(documents.get(0));
    }

    public static Optional<Document> last(List<Document> documents) {
        return documents.isEmpty() ? Optional.empty() : Optional.of(documents.get(documents.size() - 1));
    }

    public static List<Document> limit(List<Document> documents, int n) {
        return documents.stream().limit(n).collect(Collectors.toList());
    }

    public static List<Document> skip(List<Document> documents, int n) {
        return documents.stream().skip(n).collect(Collectors.toList());
    }

    public static List<Document> orderBy(List<Document> documents, String field, Query.SortOrder order) {
        var cmp = Comparator.comparingDouble((Document doc) -> toDouble(doc.getRaw(field)));
        if (order == Query.SortOrder.DESC) {
            cmp = cmp.reversed();
        }
        return documents.stream().sorted(cmp).collect(Collectors.toList());
    }

    private static double toDouble(Object value) {
        if (value == null) return 0;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static class AggregationResult {
        private final Map<String, Object> metrics;

        public AggregationResult() {
            this.metrics = new LinkedHashMap<>();
        }

        public AggregationResult add(String name, Object value) {
            metrics.put(name, value);
            return this;
        }

        public Object get(String name) {
            return metrics.get(name);
        }

        public Map<String, Object> toMap() {
            return Map.copyOf(metrics);
        }

        @Override
        public String toString() {
            return "AggregationResult" + metrics;
        }
    }
}

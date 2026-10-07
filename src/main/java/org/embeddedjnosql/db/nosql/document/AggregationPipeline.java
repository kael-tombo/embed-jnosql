package org.embeddedjnosql.db.nosql.document;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AggregationPipeline {

    private final DocumentCollection collection;
    private final List<AggregationStage> stages;

    private AggregationPipeline(DocumentCollection collection) {
        this.collection = collection;
        this.stages = new ArrayList<>();
    }

    public static AggregationPipeline create(DocumentCollection collection) {
        return new AggregationPipeline(collection);
    }

    public AggregationPipeline match(Query query) {
        stages.add(new MatchStage(query));
        return this;
    }

    public AggregationPipeline group(String field, GroupOperation operation) {
        stages.add(new GroupStage(field, operation));
        return this;
    }

    public AggregationPipeline sortByAsc(String field) {
        stages.add(new SortStage(field, Query.SortOrder.ASC));
        return this;
    }

    public AggregationPipeline sortByDesc(String field) {
        stages.add(new SortStage(field, Query.SortOrder.DESC));
        return this;
    }

    public AggregationPipeline limit(int count) {
        stages.add(new LimitStage(count));
        return this;
    }

    public AggregationPipeline skip(int count) {
        stages.add(new SkipStage(count));
        return this;
    }

    public AggregationPipeline project(String... fields) {
        stages.add(new ProjectStage(Arrays.asList(fields)));
        return this;
    }

    public AggregationPipeline count(String outputField) {
        stages.add(new CountStage(outputField));
        return this;
    }

    public AggregationPipeline sum(String field, String outputField) {
        stages.add(new SumStage(field, outputField));
        return this;
    }

    public AggregationPipeline avg(String field, String outputField) {
        stages.add(new AvgStage(field, outputField));
        return this;
    }

    public AggregationPipeline min(String field, String outputField) {
        stages.add(new MinStage(field, outputField));
        return this;
    }

    public AggregationPipeline max(String field, String outputField) {
        stages.add(new MaxStage(field, outputField));
        return this;
    }

    public List<Document> execute() {
        List<Document> results = collection.findAll();
        
        for (AggregationStage stage : stages) {
            results = stage.process(results);
        }
        
        return results;
    }

    public Optional<Document> executeOne() {
        List<Document> results = execute();
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    public long count() {
        return execute().size();
    }

    public interface AggregationStage {
        List<Document> process(List<Document> input);
    }

    public enum GroupOperation {
        SUM, AVG, MIN, MAX, COUNT, FIRST, LAST
    }

    private static class MatchStage implements AggregationStage {
        private final Query query;

        MatchStage(Query query) {
            this.query = query;
        }

        @Override
        public List<Document> process(List<Document> input) {
            return input.stream()
                    .filter(query.docPredicate())
                    .collect(Collectors.toList());
        }
    }

    private static class GroupStage implements AggregationStage {
        private final String groupByField;
        private final GroupOperation operation;
        private final String aggregateField;

        GroupStage(String groupByField, GroupOperation operation) {
            this(groupByField, operation, groupByField);
        }

        GroupStage(String groupByField, GroupOperation operation, String aggregateField) {
            this.groupByField = groupByField;
            this.operation = operation;
            this.aggregateField = aggregateField;
        }

        @Override
        public List<Document> process(List<Document> input) {
            Map<Object, List<Document>> groups = new LinkedHashMap<>();
            
            for (Document doc : input) {
                Object key = doc.getRaw(groupByField);
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(doc);
            }
            
            List<Document> results = new ArrayList<>();
            for (var entry : groups.entrySet()) {
                Document result = Document.of("_id", entry.getKey());
                List<Document> groupDocs = entry.getValue();
                
                switch (operation) {
                    case COUNT -> result.add("count", groupDocs.size());
                    case SUM -> {
                        double sum = 0;
                        for (Document d : groupDocs) {
                            Object v = d.getRaw(aggregateField);
                            if (v instanceof Number n) sum += n.doubleValue();
                        }
                        result.add("sum", sum);
                    }
                    case AVG -> {
                        double sum = 0, cnt = 0;
                        for (Document d : groupDocs) {
                            Object v = d.getRaw(aggregateField);
                            if (v instanceof Number n) {
                                sum += n.doubleValue();
                                cnt++;
                            }
                        }
                        result.add("avg", cnt > 0 ? sum / cnt : 0);
                    }
                    case MIN -> {
                        double min = Double.MAX_VALUE;
                        for (Document d : groupDocs) {
                            Object v = d.getRaw(aggregateField);
                            if (v instanceof Number n) {
                                min = Math.min(min, n.doubleValue());
                            }
                        }
                        result.add("min", min == Double.MAX_VALUE ? null : min);
                    }
                    case MAX -> {
                        double max = Double.MIN_VALUE;
                        for (Document d : groupDocs) {
                            Object v = d.getRaw(aggregateField);
                            if (v instanceof Number n) {
                                max = Math.max(max, n.doubleValue());
                            }
                        }
                        result.add("max", max == Double.MIN_VALUE ? null : max);
                    }
                    case FIRST -> result.add("first", groupDocs.get(0).getRaw(aggregateField));
                    case LAST -> result.add("last", groupDocs.get(groupDocs.size() - 1).getRaw(aggregateField));
                }
                results.add(result);
            }
            
            return results;
        }
    }

    private static class SortStage implements AggregationStage {
        private final String field;
        private final Query.SortOrder order;

        SortStage(String field, Query.SortOrder order) {
            this.field = field;
            this.order = order;
        }

        @Override
        public List<Document> process(List<Document> input) {
            Comparator<Document> cmp = (a, b) -> {
                Object va = a.getRaw(field);
                Object vb = b.getRaw(field);
                if (va == null && vb == null) return 0;
                if (va == null) return -1;
                if (vb == null) return 1;
                if (va instanceof Comparable ca && vb instanceof Comparable cb) {
                    return ca.compareTo(cb);
                }
                return va.toString().compareTo(vb.toString());
            };
            if (order == Query.SortOrder.DESC) cmp = cmp.reversed();
            
            List<Document> sorted = new ArrayList<>(input);
            sorted.sort(cmp);
            return sorted;
        }
    }

    private static class LimitStage implements AggregationStage {
        private final int limit;

        LimitStage(int limit) {
            this.limit = limit;
        }

        @Override
        public List<Document> process(List<Document> input) {
            return input.stream().limit(limit).collect(Collectors.toList());
        }
    }

    private static class SkipStage implements AggregationStage {
        private final int skip;

        SkipStage(int skip) {
            this.skip = skip;
        }

        @Override
        public List<Document> process(List<Document> input) {
            return input.stream().skip(skip).collect(Collectors.toList());
        }
    }

    private static class ProjectStage implements AggregationStage {
        private final List<String> fields;

        ProjectStage(List<String> fields) {
            this.fields = fields;
        }

        @Override
        public List<Document> process(List<Document> input) {
            return input.stream().map(doc -> {
                Document projected = Document.of("id", doc.id());
                for (String f : fields) {
                    if (doc.has(f)) {
                        projected.add(f, doc.getRaw(f));
                    }
                }
                return projected;
            }).collect(Collectors.toList());
        }
    }

    private static class CountStage implements AggregationStage {
        private final String outputField;

        CountStage(String outputField) {
            this.outputField = outputField;
        }

        @Override
        public List<Document> process(List<Document> input) {
            Document result = Document.of(outputField, input.size());
            return List.of(result);
        }
    }

    private static class SumStage implements AggregationStage {
        private final String field;
        private final String outputField;

        SumStage(String field, String outputField) {
            this.field = field;
            this.outputField = outputField;
        }

        @Override
        public List<Document> process(List<Document> input) {
            double sum = 0;
            for (Document d : input) {
                Object v = d.getRaw(field);
                if (v instanceof Number n) sum += n.doubleValue();
            }
            Document result = Document.of(outputField, sum);
            return List.of(result);
        }
    }

    private static class AvgStage implements AggregationStage {
        private final String field;
        private final String outputField;

        AvgStage(String field, String outputField) {
            this.field = field;
            this.outputField = outputField;
        }

        @Override
        public List<Document> process(List<Document> input) {
            double sum = 0, cnt = 0;
            for (Document d : input) {
                Object v = d.getRaw(field);
                if (v instanceof Number n) {
                    sum += n.doubleValue();
                    cnt++;
                }
            }
            Document result = Document.of(outputField, cnt > 0 ? sum / cnt : 0);
            return List.of(result);
        }
    }

    private static class MinStage implements AggregationStage {
        private final String field;
        private final String outputField;

        MinStage(String field, String outputField) {
            this.field = field;
            this.outputField = outputField;
        }

        @Override
        public List<Document> process(List<Document> input) {
            double min = Double.MAX_VALUE;
            for (Document d : input) {
                Object v = d.getRaw(field);
                if (v instanceof Number n) {
                    min = Math.min(min, n.doubleValue());
                }
            }
            Document result = Document.of(outputField, min == Double.MAX_VALUE ? null : min);
            return List.of(result);
        }
    }

    private static class MaxStage implements AggregationStage {
        private final String field;
        private final String outputField;

        MaxStage(String field, String outputField) {
            this.field = field;
            this.outputField = outputField;
        }

        @Override
        public List<Document> process(List<Document> input) {
            double max = Double.MIN_VALUE;
            for (Document d : input) {
                Object v = d.getRaw(field);
                if (v instanceof Number n) {
                    max = Math.max(max, n.doubleValue());
                }
            }
            Document result = Document.of(outputField, max == Double.MIN_VALUE ? null : max);
            return List.of(result);
        }
    }
}

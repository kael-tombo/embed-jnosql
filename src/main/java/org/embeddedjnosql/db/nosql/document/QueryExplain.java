package org.embeddedjnosql.db.nosql.document;

import java.util.*;

public class QueryExplain {

    private final Query query;
    private final String collection;
    private final List<String> indexUsed;
    private final long estimatedCost;
    private final int estimatedResults;
    private final String executionPlan;
    private final long analysisTimeMs;

    private QueryExplain(QueryExplainBuilder builder) {
        this.query = builder.query;
        this.collection = builder.collection;
        this.indexUsed = builder.indexUsed;
        this.estimatedCost = builder.estimatedCost;
        this.estimatedResults = builder.estimatedResults;
        this.executionPlan = builder.executionPlan;
        this.analysisTimeMs = builder.analysisTimeMs;
    }

    public static QueryExplainBuilder explain(Query query) {
        return new QueryExplainBuilder(query);
    }

    public Query getQuery() {
        return query;
    }

    public String getCollection() {
        return collection;
    }

    public List<String> getIndexUsed() {
        return indexUsed;
    }

    public long getEstimatedCost() {
        return estimatedCost;
    }

    public int getEstimatedResults() {
        return estimatedResults;
    }

    public String getExecutionPlan() {
        return executionPlan;
    }

    public long getAnalysisTimeMs() {
        return analysisTimeMs;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("collection", collection);
        map.put("query", query.toString());
        map.put("executionPlan", executionPlan);
        map.put("indexUsed", indexUsed.isEmpty() ? "NONE (full scan)" : indexUsed);
        map.put("estimatedCost", estimatedCost + " operations");
        map.put("estimatedResults", estimatedResults);
        map.put("analysisTimeMs", analysisTimeMs);
        return map;
    }

    @Override
    public String toString() {
        return "QueryExplain{" +
                "collection='" + collection + '\'' +
                ", query=" + query +
                ", executionPlan='" + executionPlan + '\'' +
                ", indexUsed=" + indexUsed +
                ", estimatedCost=" + estimatedCost +
                ", estimatedResults=" + estimatedResults +
                ", analysisTimeMs=" + analysisTimeMs +
                '}';
    }

    public static class QueryExplainBuilder {
        private final Query query;
        private String collection = "unknown";
        private List<String> indexUsed = new ArrayList<>();
        private long estimatedCost = 0;
        private int estimatedResults = 0;
        private String executionPlan = "";
        private long analysisTimeMs = 0;

        QueryExplainBuilder(Query query) {
            this.query = query;
        }

        public QueryExplainBuilder collection(String collection) {
            this.collection = collection;
            return this;
        }

        public QueryExplainBuilder indexUsed(List<String> indexes) {
            this.indexUsed = indexes;
            return this;
        }

        public QueryExplainBuilder estimatedCost(long cost) {
            this.estimatedCost = cost;
            return this;
        }

        public QueryExplainBuilder estimatedResults(int results) {
            this.estimatedResults = results;
            return this;
        }

        public QueryExplainBuilder executionPlan(String plan) {
            this.executionPlan = plan;
            return this;
        }

        public QueryExplainBuilder analysisTimeMs(long time) {
            this.analysisTimeMs = time;
            return this;
        }

        public QueryExplain build() {
            return new QueryExplain(this);
        }
    }
}

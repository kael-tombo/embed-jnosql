package org.embeddedjnosql.db.nosql.document;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class TextSearch {

    private final String field;
    private final String query;
    private final double minScore;
    private final int maxResults;

    private TextSearch(String field, String query, double minScore, int maxResults) {
        this.field = field;
        this.query = query.toLowerCase();
        this.minScore = minScore;
        this.maxResults = maxResults;
    }

    public static TextSearchBuilder builder() {
        return new TextSearchBuilder();
    }

    public String getField() {
        return field;
    }

    public String getQuery() {
        return query;
    }

    public double getMinScore() {
        return minScore;
    }

    public int getMaxResults() {
        return maxResults;
    }

    public List<SearchResult> search(List<Document> documents) {
        String[] queryTerms = query.toLowerCase().split("\\s+");
        
        List<SearchResult> results = new ArrayList<>();
        
        for (Document doc : documents) {
            if (!doc.has(field)) continue;
            
            Object fieldValue = doc.getRaw(field);
            String text = fieldValue != null ? fieldValue.toString().toLowerCase() : "";
            
            double score = calculateScore(text, queryTerms);
            
            if (score >= minScore) {
                results.add(new SearchResult(doc, score));
            }
        }
        
        return results.stream()
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(maxResults)
                .collect(Collectors.toList());
    }

    private double calculateScore(String text, String[] terms) {
        if (text.isEmpty() || terms.length == 0) return 0;
        
        double score = 0;
        String[] textWords = text.split("\\s+");
        
        for (String term : terms) {
            if (text.contains(term)) {
                score += 1.0;
                
                int termCount = 0;
                for (String word : textWords) {
                    if (word.equals(term)) termCount++;
                }
                score += 0.5 * termCount;
                
                if (text.startsWith(term)) {
                    score += 2.0;
                }
                
                if (textWords.length > 0) {
                    score += 1.0 / textWords.length;
                }
            }
        }
        
        double maxPossibleScore = terms.length * 4.5;
        return score / maxPossibleScore;
    }

    public record SearchResult(Document document, double score) {}

    public static class TextSearchBuilder {
        private String field = "text";
        private String query = "";
        private double minScore = 0.1;
        private int maxResults = 10;

        public TextSearchBuilder field(String field) {
            this.field = field;
            return this;
        }

        public TextSearchBuilder query(String query) {
            this.query = query;
            return this;
        }

        public TextSearchBuilder minScore(double minScore) {
            this.minScore = minScore;
            return this;
        }

        public TextSearchBuilder maxResults(int maxResults) {
            this.maxResults = maxResults;
            return this;
        }

        public TextSearch build() {
            if (query.isEmpty()) {
                throw new IllegalArgumentException("Query cannot be empty");
            }
            return new TextSearch(field, query, minScore, maxResults);
        }
    }

    public static class FuzzySearch {
        
        public static int levenshteinDistance(String s1, String s2) {
            int[][] dp = new int[s1.length() + 1][s2.length() + 1];
            
            for (int i = 0; i <= s1.length(); i++) dp[i][0] = i;
            for (int j = 0; j <= s2.length(); j++) dp[0][j] = j;
            
            for (int i = 1; i <= s1.length(); i++) {
                for (int j = 1; j <= s2.length(); j++) {
                    int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                    dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                    );
                }
            }
            
            return dp[s1.length()][s2.length()];
        }
        
        public static double similarity(String s1, String s2) {
            if (s1.isEmpty() && s2.isEmpty()) return 1.0;
            int distance = levenshteinDistance(s1.toLowerCase(), s2.toLowerCase());
            int maxLen = Math.max(s1.length(), s2.length());
            return 1.0 - (double) distance / maxLen;
        }
    }
}

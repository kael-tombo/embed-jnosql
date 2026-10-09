package org.embeddedjnosql.db.demo.demos.textsearch;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.TextSearch;

import java.util.List;

/**
 * Demo 18 — Text search: scored term matching over a document field, plus
 * Levenshtein-based fuzzy matching for typo tolerance. Runs in the JVM on
 * document lists — there is no external search server involved.
 */
public class TextSearchDemo {

    public record Result(List<String> rankedIds, double bestScore, double typoSimilarity) {}

    public static Result runDemo(EmbedJNoSQL db) {
        var articles = db.documentCollection("articles");
        articles.insert(new Document().id("a1").add("title", "Embedded NoSQL databases in Java"));
        articles.insert(new Document().id("a2").add("title", "Cooking pasta for beginners"));
        articles.insert(new Document().id("a3").add("title", "NoSQL data modeling patterns"));
        articles.insert(new Document().id("a4").add("title", "Java concurrency in practice"));

        // 1. Scored search: "nosql java" ranks matching docs, best first
        var search = TextSearch.builder()
                .field("title")
                .query("nosql java")
                .minScore(0.05)
                .maxResults(10)
                .build();
        var hits = search.search(articles.findAll());

        List<String> ids = hits.stream().map(h -> h.document().getId()).toList();
        double best = hits.isEmpty() ? 0 : hits.get(0).score();

        // 2. Fuzzy matching: "NOSQL" vs a typo'd "Nosqul"
        double typo = TextSearch.FuzzySearch.similarity("nosql", "nosqul");

        Result r = new Result(ids, best, typo);
        if (!r.rankedIds().contains("a1") || !r.rankedIds().contains("a3")
                || r.rankedIds().contains("a2") || r.typoSimilarity() < 0.7) {
            throw new AssertionError("Unexpected text-search results: " + r);
        }
        return r;
    }

    public static void main(String[] args) {
        try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
            Result r = runDemo(db);
            System.out.println("== Text search demo ==");
            System.out.println("ranked ids      : " + r.rankedIds());
            System.out.println("best score      : " + r.bestScore());
            System.out.println("typo similarity : " + r.typoSimilarity());
        }
    }
}

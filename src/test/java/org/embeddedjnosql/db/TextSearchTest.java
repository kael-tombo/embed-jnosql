package org.embeddedjnosql.db;

import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.document.TextSearch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextSearchTest {

    private DocumentCollection articles;

    @BeforeEach
    void setUp() {
        var db = EmbedJNoSQL.embed().build();
        articles = db.documentCollection("articles");
        
        articles.insertAll(List.of(
                Document.of("title", "Java Programming Guide").add("content", "Learn Java programming with this comprehensive guide"),
                Document.of("title", "Python Basics").add("content", "Python is a great language for beginners"),
                Document.of("title", "Advanced Java Topics").add("content", "Deep dive into Java concurrency and performance"),
                Document.of("title", "JavaScript Essentials").add("content", "JavaScript for web development")
        ));
    }

    @AfterEach
    void tearDown() {
        articles.clear();
    }

    @Test
    void testTextSearchSimple() {
        var results = TextSearch.builder()
                .field("title")
                .query("Java")
                .build()
                .search(articles.findAll());
        
        assertEquals(3, results.size());
    }

    @Test
    void testTextSearchWithContent() {
        var results = TextSearch.builder()
                .field("content")
                .query("programming")
                .build()
                .search(articles.findAll());
        
        assertTrue(results.size() >= 1);
    }

    @Test
    void testTextSearchRelevance() {
        var results = TextSearch.builder()
                .field("title")
                .query("Java")
                .maxResults(5)
                .build()
                .search(articles.findAll());
        
        assertFalse(results.isEmpty());
        for (var r : results) {
            assertTrue(r.score() >= 0);
            assertTrue(r.score() <= 1.0);
        }
    }

    @Test
    void testTextSearchMinScore() {
        var results = TextSearch.builder()
                .field("title")
                .query("Java")
                .minScore(0.5)
                .build()
                .search(articles.findAll());
        
        for (var r : results) {
            assertTrue(r.score() >= 0.5);
        }
    }

    @Test
    void testTextSearchNoResults() {
        var results = TextSearch.builder()
                .field("title")
                .query("NonExistentTerm")
                .build()
                .search(articles.findAll());
        
        assertTrue(results.isEmpty());
    }

    @Test
    void testFuzzySearchSimilarity() {
        double sim = TextSearch.FuzzySearch.similarity("Java", "Java");
        assertEquals(1.0, sim, 0.001);
        
        double sim2 = TextSearch.FuzzySearch.similarity("Java", "Javascript");
        assertTrue(sim2 > 0.3 && sim2 < 1.0);
    }

    @Test
    void testFuzzySearchDistance() {
        int dist = TextSearch.FuzzySearch.levenshteinDistance("Java", "Java");
        assertEquals(0, dist);
        
        int dist2 = TextSearch.FuzzySearch.levenshteinDistance("Java", "Javascript");
        assertTrue(dist2 > 0);
    }
}

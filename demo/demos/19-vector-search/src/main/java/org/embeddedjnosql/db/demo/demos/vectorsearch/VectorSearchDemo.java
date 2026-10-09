package org.embeddedjnosql.db.demo.demos.vectorsearch;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.index.hnsw.VectorIndex;

/**
 * Demo 19 — Vector search (experimental): an HNSW index over float vectors
 * supports k-nearest-neighbor lookup and removal. Use it to embed documents
 * and find semantic neighbors — similarity here is cosine or Euclidean on the
 * vectors you supply, computed in the JVM.
 */
public class VectorSearchDemo {

    public record Result(String topMatch, int indexSize, boolean removedFromIndex,
                         boolean secondMatchSane) {}

    public static Result runDemo(EmbedJNoSQL db) {
        // 8-dimensional toy embeddings; in production these come from your model
        var index = new VectorIndex(8, 4, 8);
        float[] docA = {1, 0, 0, 0, 0, 0, 0, 0};   // "database docs"
        float[] docB = {0, 1, 0, 0, 0, 0, 0, 0};   // "cooking docs"
        float[] docC = {0.9f, 0.1f, 0, 0, 0, 0, 0, 0}; // near-duplicate of A

        index.add("a", docA);
        index.add("b", docB);
        index.add("c", docC);

        // 1. kNN search: query near A should return A and C, not B
        var top2 = index.search(new float[]{0.95f, 0.05f, 0, 0, 0, 0, 0, 0}, 2);

        // 2. Remove an entry and verify it no longer appears
        index.remove("c");
        var afterRemoval = index.search(docA, 3);
        boolean cGone = !afterRemoval.contains("c");

        Result r = new Result(top2.get(0), index.size(), cGone, top2.size() == 2);
        if (!"a".equals(r.topMatch()) || r.indexSize() != 2 || !r.removedFromIndex()) {
            throw new AssertionError("Unexpected vector results: " + r);
        }
        return r;
    }

    public static void main(String[] args) {
        try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
            Result r = runDemo(db);
            System.out.println("== Vector search demo (experimental) ==");
            System.out.println("top match       : " + r.topMatch());
            System.out.println("index size      : " + r.indexSize());
            System.out.println("removed from idx: " + r.removedFromIndex());
            System.out.println("k=2 honored     : " + r.secondMatchSane());
        }
    }
}

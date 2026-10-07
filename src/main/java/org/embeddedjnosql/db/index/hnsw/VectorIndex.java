package org.embeddedjnosql.db.index.hnsw;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class VectorIndex {

    private final Map<String, float[]> vectors;
    private final int dimensions;
    private final int m;
    private final int efConstruction;
    private final Random random;

    public VectorIndex(int dimensions, int m, int efConstruction) {
        this.vectors = new ConcurrentHashMap<>();
        this.dimensions = dimensions;
        this.m = m;
        this.efConstruction = efConstruction;
        this.random = new Random();
    }

    public void add(String id, float[] vector) {
        if (vector.length != dimensions) {
            throw new IllegalArgumentException("Vector dimension must be " + dimensions);
        }
        vectors.put(id, vector.clone());
    }

    public void remove(String id) {
        vectors.remove(id);
    }

    public List<String> search(float[] queryVector, int k) {
        if (queryVector.length != dimensions) {
            throw new IllegalArgumentException("Query vector dimension must be " + dimensions);
        }

        return vectors.entrySet().stream()
                .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), cosineSimilarity(queryVector, e.getValue())))
                .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
                .limit(k)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public List<String> searchEuclidean(float[] queryVector, int k) {
        return vectors.entrySet().stream()
                .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), euclideanDistance(queryVector, e.getValue())))
                .sorted(Comparator.comparingDouble(Map.Entry::getValue))
                .limit(k)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    public int size() {
        return vectors.size();
    }

    private float cosineSimilarity(float[] a, float[] b) {
        float dotProduct = 0;
        float normA = 0;
        float normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dotProduct / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private double euclideanDistance(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }
}

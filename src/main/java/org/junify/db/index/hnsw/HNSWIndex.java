package org.junify.db.index.hnsw;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class HNSWIndex {

    private final int dimensions;
    private final int maxLayers;
    private final int m;
    private final int efConstruction;
    private final double levelLambda;
    private final Map<Integer, List<GraphNode>> graph;
    private final Map<String, float[]> vectors;
    private int size;

    public HNSWIndex(int dimensions) {
        this(dimensions, 6, 16, 200);
    }

    public HNSWIndex(int dimensions, int maxLayers, int m, int efConstruction) {
        this.dimensions = dimensions;
        this.maxLayers = maxLayers;
        this.m = m;
        this.efConstruction = efConstruction;
        this.levelLambda = 1.0 / Math.log(2.0);
        this.graph = new ConcurrentHashMap<>();
        this.vectors = new ConcurrentHashMap<>();
        this.size = 0;
    }

    public void add(String id, float[] vector) {
        if (vector.length != dimensions) {
            throw new IllegalArgumentException("Vector dimension must be " + dimensions);
        }
        
        vectors.put(id, vector.clone());
        int level = sampleLevel();
        
        Map<String, List<String>> connectionsByLevel = new HashMap<>();
        for (int l = 0; l < maxLayers; l++) {
            connectionsByLevel.put(id + "_" + l, new ArrayList<>());
        }
        
        String[] entryPoints = findEntryPoints();
        
        for (int l = maxLayers - 1; l >= 0; l--) {
            if (l > level) continue;
            
            if (entryPoints != null && entryPoints.length > 0) {
                for (String ep : entryPoints) {
                    if (ep != null) {
                        connectionsByLevel.get(id + "_" + l).add(ep);
                    }
                }
            }
        }
        
        for (int l = 0; l <= level && l < maxLayers; l++) {
            graph.computeIfAbsent(l, k -> new ArrayList<>());
            List<GraphNode> nodes = graph.get(l);
            synchronized (nodes) {
                nodes.add(new GraphNode(id, 0.0));
                
                for (String neighborId : connectionsByLevel.get(id + "_" + l)) {
                    if (neighborId.equals(id)) continue;
                    double d = distance(vectors.get(id), vectors.get(neighborId));
                    nodes.add(new GraphNode(neighborId, d));
                }
                
                for (String neighborId : connectionsByLevel.get(id + "_" + l)) {
                    if (neighborId.equals(id)) continue;
                    
                    List<GraphNode> neighborList = graph.computeIfAbsent(l, k -> new ArrayList<>());
                    synchronized (neighborList) {
                        double d = distance(vectors.get(id), vectors.get(neighborId));
                        neighborList.add(new GraphNode(id, d));
                        
                        for (GraphNode existing : new ArrayList<>(neighborList)) {
                            if (existing.id.equals(neighborId)) {
                                double existingD = distance(vectors.get(neighborId), vectors.get(existing.id));
                                if (existingD < d) {
                                    neighborList.remove(existing);
                                    neighborList.add(new GraphNode(existing.id, existingD));
                                }
                            }
                        }
                    }
                }
            }
        }
        
        size++;
    }

    public void remove(String id) {
        if (!vectors.containsKey(id)) return;
        
        for (int l = 0; l < maxLayers; l++) {
            List<GraphNode> nodes = graph.get(l);
            if (nodes == null) continue;
            
            synchronized (nodes) {
                nodes.removeIf(n -> n.id.equals(id));
            }
        }
        
        vectors.remove(id);
        size--;
    }

    public List<String> search(float[] queryVector, int k) {
        return search(queryVector, k, 1.0f);
    }

    public List<String> search(float[] queryVector, int k, float accuracy) {
        if (queryVector.length != dimensions) {
            throw new IllegalArgumentException("Query vector dimension must be " + dimensions);
        }
        
        int ef = Math.max(k, (int) (efConstruction * accuracy));
        
        String[] entryPoints = findEntryPoints();
        if (entryPoints == null || entryPoints.length == 0) return Collections.emptyList();
        
        int topLevel = 0;
        for (int l = maxLayers - 1; l >= 0; l--) {
            List<GraphNode> nodes = graph.get(l);
            if (nodes != null && !nodes.isEmpty()) {
                topLevel = l;
                break;
            }
        }
        
        for (int l = topLevel; l >= 1; l--) {
            entryPoints = searchLayer(queryVector, entryPoints, 1, l);
            if (entryPoints == null) entryPoints = new String[0];
        }
        
        String[] results = searchLayer(queryVector, entryPoints, ef, 0);
        
        List<String> resultList = Arrays.stream(results)
                .limit(k)
                .collect(Collectors.toList());
        
        return resultList;
    }

    public List<String> searchEuclidean(float[] queryVector, int k) {
        return searchEuclidean(queryVector, k, 1.0f);
    }

    public List<String> searchEuclidean(float[] queryVector, int k, float accuracy) {
        if (queryVector.length != dimensions) {
            throw new IllegalArgumentException("Query vector dimension must be " + dimensions);
        }
        
        int ef = Math.max(k, (int) (efConstruction * accuracy));
        
        String[] entryPoints = findEntryPoints();
        if (entryPoints == null || entryPoints.length == 0) return Collections.emptyList();
        
        int topLevel = 0;
        for (int l = maxLayers - 1; l >= 0; l--) {
            List<GraphNode> nodes = graph.get(l);
            if (nodes != null && !nodes.isEmpty()) {
                topLevel = l;
                break;
            }
        }
        
        for (int l = topLevel; l >= 1; l--) {
            entryPoints = searchLayerEuclidean(queryVector, entryPoints, ef, l);
            if (entryPoints == null) entryPoints = new String[0];
        }
        
        String[] results = searchLayerEuclidean(queryVector, entryPoints, ef, 0);
        
        List<String> resultList = Arrays.stream(results)
                .limit(k)
                .collect(Collectors.toList());
        
        return resultList;
    }

    private String[] findEntryPoints() {
        for (int l = maxLayers - 1; l >= 0; l--) {
            List<GraphNode> nodes = graph.get(l);
            if (nodes != null && !nodes.isEmpty()) {
                return new String[]{nodes.get(0).id};
            }
        }
        return null;
    }

    private String[] searchLayer(float[] query, String[] entryPoints, int ef, int layer) {
        PriorityQueue<GraphNode> candidates = new PriorityQueue<>(
                Comparator.comparingDouble((GraphNode n) -> -n.distance)
        );
        Set<String> visited = new HashSet<>();
        PriorityQueue<GraphNode> topResults = new PriorityQueue<>(
                Comparator.comparingDouble(n -> n.distance)
        );
        
        for (String ep : entryPoints) {
            if (ep == null) continue;
            float[] epVector = vectors.get(ep);
            if (epVector == null) continue;
            GraphNode node = new GraphNode(ep, distance(query, epVector));
            candidates.add(node);
            topResults.add(node);
            visited.add(ep);
        }
        
        double lowerBound = topResults.isEmpty() ? Double.MAX_VALUE : topResults.peek().distance;
        
        while (!candidates.isEmpty()) {
            GraphNode current = candidates.poll();
            
            if (current.distance > lowerBound && topResults.size() >= ef) {
                break;
            }
            
            List<GraphNode> neighbors = graph.get(layer);
            if (neighbors == null) continue;
            
            List<GraphNode> localNeighbors;
            synchronized (neighbors) {
                localNeighbors = new ArrayList<>(neighbors);
            }
            
            for (GraphNode neighbor : localNeighbors) {
                if (!visited.contains(neighbor.id)) {
                    visited.add(neighbor.id);
                    float[] neighborVector = vectors.get(neighbor.id);
                    if (neighborVector == null) continue;
                    double d = distance(query, neighborVector);
                    GraphNode neighborNode = new GraphNode(neighbor.id, d);
                    
                    if (topResults.size() < ef || d < lowerBound) {
                        topResults.add(neighborNode);
                        candidates.add(neighborNode);
                        
                        if (topResults.size() > ef) {
                            topResults.poll();
                        }
                        
                        if (!topResults.isEmpty()) {
                            lowerBound = topResults.peek().distance;
                        }
                    }
                }
            }
        }
        
        String[] results = new String[topResults.size()];
        int i = 0;
        for (GraphNode node : topResults) {
            results[i++] = node.id;
        }
        return results;
    }

    private String[] searchLayer(float[] query, String[] entryPoints, int ef, int layer, float[] queryVectorForLayer) {
        PriorityQueue<GraphNode> candidates = new PriorityQueue<>(
                Comparator.comparingDouble((GraphNode n) -> -n.distance)
        );
        Set<String> visited = new HashSet<>();
        PriorityQueue<GraphNode> topResults = new PriorityQueue<>(
                Comparator.comparingDouble(n -> n.distance)
        );
        
        for (String ep : entryPoints) {
            if (ep == null) continue;
            float[] epVector = vectors.get(ep);
            if (epVector == null) continue;
            GraphNode node = new GraphNode(ep, distance(queryVectorForLayer, epVector));
            candidates.add(node);
            topResults.add(node);
            visited.add(ep);
        }
        
        double lowerBound = topResults.isEmpty() ? Double.MAX_VALUE : topResults.peek().distance;
        
        while (!candidates.isEmpty()) {
            GraphNode current = candidates.poll();
            
            if (current.distance > lowerBound && topResults.size() >= ef) {
                break;
            }
            
            List<GraphNode> neighbors = graph.get(layer);
            if (neighbors == null) continue;
            
            List<GraphNode> localNeighbors;
            synchronized (neighbors) {
                localNeighbors = new ArrayList<>(neighbors);
            }
            
            for (GraphNode neighbor : localNeighbors) {
                if (!visited.contains(neighbor.id)) {
                    visited.add(neighbor.id);
                    float[] neighborVector = vectors.get(neighbor.id);
                    if (neighborVector == null) continue;
                    double d = distance(queryVectorForLayer, neighborVector);
                    GraphNode neighborNode = new GraphNode(neighbor.id, d);
                    
                    if (topResults.size() < ef || d < lowerBound) {
                        topResults.add(neighborNode);
                        candidates.add(neighborNode);
                        
                        if (topResults.size() > ef) {
                            topResults.poll();
                        }
                        
                        if (!topResults.isEmpty()) {
                            lowerBound = topResults.peek().distance;
                        }
                    }
                }
            }
        }
        
        String[] results = new String[topResults.size()];
        int i = 0;
        for (GraphNode node : topResults) {
            results[i++] = node.id;
        }
        return results;
    }

    private String[] searchLayerEuclidean(float[] query, String[] entryPoints, int ef, int layer) {
        PriorityQueue<GraphNode> candidates = new PriorityQueue<>(
                Comparator.comparingDouble((GraphNode n) -> -n.distance)
        );
        Set<String> visited = new HashSet<>();
        PriorityQueue<GraphNode> topResults = new PriorityQueue<>(
                Comparator.comparingDouble(n -> n.distance)
        );
        
        for (String ep : entryPoints) {
            if (ep == null) continue;
            float[] epVector = vectors.get(ep);
            if (epVector == null) continue;
            GraphNode node = new GraphNode(ep, euclideanDistance(query, epVector));
            candidates.add(node);
            topResults.add(node);
            visited.add(ep);
        }
        
        double lowerBound = topResults.isEmpty() ? Double.MAX_VALUE : topResults.peek().distance;
        
        while (!candidates.isEmpty()) {
            GraphNode current = candidates.poll();
            
            if (current.distance > lowerBound && topResults.size() >= ef) {
                break;
            }
            
            List<GraphNode> neighbors = graph.get(layer);
            if (neighbors == null) continue;
            
            List<GraphNode> localNeighbors;
            synchronized (neighbors) {
                localNeighbors = new ArrayList<>(neighbors);
            }
            
            for (GraphNode neighbor : localNeighbors) {
                if (!visited.contains(neighbor.id)) {
                    visited.add(neighbor.id);
                    float[] neighborVector = vectors.get(neighbor.id);
                    if (neighborVector == null) continue;
                    double d = euclideanDistance(query, neighborVector);
                    GraphNode neighborNode = new GraphNode(neighbor.id, d);
                    
                    if (topResults.size() < ef || d < lowerBound) {
                        topResults.add(neighborNode);
                        candidates.add(neighborNode);
                        
                        if (topResults.size() > ef) {
                            topResults.poll();
                        }
                        
                        if (!topResults.isEmpty()) {
                            lowerBound = topResults.peek().distance;
                        }
                    }
                }
            }
        }
        
        String[] results = new String[topResults.size()];
        int i = 0;
        for (GraphNode node : topResults) {
            results[i++] = node.id;
        }
        return results;
    }

    private int sampleLevel() {
        double r = ThreadLocalRandom.current().nextDouble();
        int level = (int) (-Math.log(r) * levelLambda);
        return Math.min(level, maxLayers - 1);
    }

    public double distance(float[] a, float[] b) {
        return 1.0 - cosineSimilarity(a, b);
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dotProduct = 0;
        double normA = 0;
        double normB = 0;
        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB) + 1e-10);
    }

    private double euclideanDistance(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            double diff = a[i] - b[i];
            sum += diff * diff;
        }
        return Math.sqrt(sum);
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int dimensions() {
        return dimensions;
    }

    public Map<String, Object> stats() {
        return Map.of(
            "dimensions", dimensions,
            "size", size,
            "maxLayers", maxLayers,
            "m", m,
            "efConstruction", efConstruction,
            "type", "hnsw-vector"
        );
    }

    /**
     * R-52: rebuild an index from {@link #toJson()} output. The HNSW graph is
     * not serialized — vectors and constructor parameters are — and the graph
     * is deterministically rebuilt by re-adding every vector in stored order.
     * The result answers the same searches, though link structure may differ
     * from the pre-restart graph (same recall characteristics, not byte-identical
     * layout).
     *
     * @throws IllegalArgumentException when the JSON is structurally invalid or
     *         a stored vector's width disagrees with the stored dimensionality.
     */
    public static HNSWIndex fromJson(String json) {
        Map<String, Object> root = org.junify.db.core.util.JsonSerde.fromJson(json, Map.class);
        if (root == null || !(root.get("dimensions") instanceof Number dims)) {
            throw new IllegalArgumentException("Invalid vector index JSON: missing dimensions");
        }
        HNSWIndex index = new HNSWIndex(dims.intValue());
        if (!(root.get("vectors") instanceof Map<?, ?> stored)) {
            return index; // no vectors yet
        }
        for (Map.Entry<?, ?> entry : stored.entrySet()) {
            if (!(entry.getValue() instanceof List<?> raw)) {
                throw new IllegalArgumentException("Invalid vector entry for id '" + entry.getKey() + "'");
            }
            float[] vector = new float[raw.size()];
            for (int i = 0; i < raw.size(); i++) {
                if (!(raw.get(i) instanceof Number n)) {
                    throw new IllegalArgumentException("Non-numeric vector component in '" + entry.getKey() + "'");
                }
                vector[i] = n.floatValue();
            }
            index.add(entry.getKey().toString(), vector);
        }
        return index;
    }

    public String toJson() {
        var sb = new StringBuilder();
        sb.append("{\"dimensions\":").append(dimensions).append(",");
        sb.append("\"maxLayers\":").append(maxLayers).append(",");
        sb.append("\"m\":").append(m).append(",");
        sb.append("\"efConstruction\":").append(efConstruction).append(",");
        sb.append("\"vectors\":{");
        var first = true;
        for (var entry : vectors.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(entry.getKey()).append("\":[");
            var firstDim = true;
            for (float f : entry.getValue()) {
                if (!firstDim) sb.append(",");
                firstDim = false;
                sb.append(f);
            }
            sb.append("]");
        }
        sb.append("}}");
        return sb.toString();
    }

    public int graphLayers() {
        return graph.size();
    }

    private static class GraphNode {
        final String id;
        final double distance;

        GraphNode(String id, double distance) {
            this.id = id;
            this.distance = distance;
        }
    }
}

package org.embeddedjnosql.db.benchmark;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig.StorageEngineType;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.kv.KeyValueBucket;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class BenchmarkRunner {

    public static void main(String[] args) throws Exception {
        Options options;
        try {
            options = parseArgs(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Run with --help to list supported options.");
            System.exit(2);
            return;
        }
        if (options.helpRequested) {
            printUsage();
            return;
        }
        
        System.out.println("=".repeat(60));
        System.out.println("EmbedJNoSQL Benchmark Runner");
        System.out.println("=".repeat(60));
        
        var results = new BenchmarkResults();
        
        if (options.workload.contains("document") || options.workload.contains("all")) {
            runDocumentBenchmark(options, results);
        }
        
        if (options.workload.contains("kv") || options.workload.contains("all")) {
            runKeyValueBenchmark(options, results);
        }
        
        if (options.workload.contains("mixed") || options.workload.contains("all")) {
            runMixedBenchmark(options, results);
        }
        
        results.printSummary();
    }

    private static void runDocumentBenchmark(Options options, BenchmarkResults results) {
        System.out.println("\n--- Document Benchmark ---");
        
        var config = EmbedJNoSQL.embed()
            .storageEngine(options.engine)
            .autoFlush(false)
            .buildConfig();
        
        try (var db = EmbedJNoSQL.create(config)) {
            var collection = db.documentCollection("benchmark_docs");
            
            var writeTime = benchmarkWrites(options, collection);
            results.record("Document Write", options.ops, writeTime);
            
            var readTime = benchmarkReads(options, collection);
            results.record("Document Read", options.ops, readTime);
            
            collection.clear();
        }
    }

    private static void runKeyValueBenchmark(Options options, BenchmarkResults results) {
        System.out.println("\n--- Key-Value Benchmark ---");
        
        var config = EmbedJNoSQL.embed()
            .storageEngine(options.engine)
            .autoFlush(false)
            .buildConfig();
        
        try (var db = EmbedJNoSQL.create(config)) {
            var bucket = db.keyValueBucket("benchmark_kv");
            
            var writeTime = benchmarkKVWrites(options, bucket);
            results.record("KV Write", options.ops, writeTime);
            
            var readTime = benchmarkKVReads(options, bucket);
            results.record("KV Read", options.ops, readTime);
            
            bucket.clear();
        }
    }

    private static void runMixedBenchmark(Options options, BenchmarkResults results) {
        System.out.println("\n--- Mixed Benchmark ---");
        
        var config = EmbedJNoSQL.embed()
            .storageEngine(options.engine)
            .autoFlush(false)
            .buildConfig();
        
        try (var db = EmbedJNoSQL.create(config)) {
            var collection = db.documentCollection("mixed_docs");
            var bucket = db.keyValueBucket("mixed_kv");
            
            var executor = Executors.newFixedThreadPool(options.threads);
            var barrier = new CyclicBarrier(2);
            
            var start = System.nanoTime();
            
            for (int i = 0; i < options.threads; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        barrier.await();
                        for (int j = 0; j < options.ops / options.threads; j++) {
                            var doc = new Document();
                            doc.id("doc-" + threadId + "-" + j);
                            doc.add("data", UUID.randomUUID().toString());
                            collection.insert(doc);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
            
            executor.shutdown();
            try {
                executor.awaitTermination(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            var elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            results.record("Mixed Write", options.ops, elapsed);
            results.record("Mixed Throughput", options.ops, 
                (options.ops * 1000L) / Math.max(elapsed, 1));
        }
    }

    private static long benchmarkWrites(Options options, DocumentCollection collection) {
        var docs = new ArrayList<Document>(options.ops);
        for (int i = 0; i < options.ops; i++) {
            var doc = new Document();
            doc.id("doc-" + i);
            doc.add("index", i);
            doc.add("data", "data-" + i);
            doc.add("timestamp", System.currentTimeMillis());
            docs.add(doc);
        }
        
        var start = System.nanoTime();
        for (var doc : docs) {
            collection.insert(doc);
        }
        
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private static long benchmarkReads(Options options, DocumentCollection collection) {
        var start = System.nanoTime();
        for (int i = 0; i < options.ops; i++) {
            collection.findById("doc-" + i);
        }
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private static long benchmarkKVWrites(Options options, KeyValueBucket bucket) {
        var start = System.nanoTime();
        for (int i = 0; i < options.ops; i++) {
            bucket.put("key-" + i, "value-" + i);
        }
        
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    private static long benchmarkKVReads(Options options, KeyValueBucket bucket) {
        var start = System.nanoTime();
        for (int i = 0; i < options.ops; i++) {
            bucket.get("key-" + i);
        }
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    }

    /** Reads the value following {@code option}, failing fast when it is absent. */
    static String value(String option, String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index];
    }

    /** Reads a numeric value following {@code option}, failing fast when it is not a number. */
    static int intValue(String option, String[] args, int index) {
        String raw = value(option, args, index);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number for " + option + ": " + raw);
        }
    }

    /**
     * Validates a {@code --workload} value. Before this guard a typo silently
     * matched no workload and the runner exited reporting zero benchmarks.
     *
     * @throws IllegalArgumentException when any token is not a known workload
     */
    static String workloadValue(String raw) {
        for (String token : raw.split(",")) {
            String normalized = token.trim().toLowerCase();
            if (!Set.of("all", "document", "kv", "mixed").contains(normalized)) {
                throw new IllegalArgumentException("Unknown workload: " + token.trim()
                        + " (expected all, document, kv or mixed)");
            }
        }
        return raw.toLowerCase();
    }

    static void printUsage() {
        System.out.println("Usage: embed-jnosql-benchmark [options]");
        System.out.println("Options:");
        System.out.println("  --ops <n>            Operations per workload (default: 10000)");
        System.out.println("  --engine <type>      FILE, IN_MEMORY, LSM_TREE, B_TREE (default: IN_MEMORY)");
        System.out.println("  --threads <n>        Concurrent threads (default: 1)");
        System.out.println("  --workload <name>    all, document, kv, mixed (default: all)");
        System.out.println("  --help               Show this help");
    }

    static Options parseArgs(String[] args) {
        var options = new Options();
        
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--ops" -> options.ops = intValue("--ops", args, ++i);
                case "--engine" -> options.engine = switch (args[++i].toUpperCase()) {
                    case "FILE" -> StorageEngineType.FILE;
                    case "LSM_TREE" -> StorageEngineType.LSM_TREE;
                    case "B_TREE" -> StorageEngineType.B_TREE;
                    case "IN_MEMORY" -> StorageEngineType.IN_MEMORY;
                    default -> throw new IllegalArgumentException("Unsupported engine: " + args[i]
                            + " (expected FILE, IN_MEMORY, LSM_TREE or B_TREE)");
                };
                case "--threads" -> options.threads = intValue("--threads", args, ++i);
                case "--workload" -> options.workload = workloadValue(value("--workload", args, ++i));
                case "--help" -> options.helpRequested = true;
                default -> throw new IllegalArgumentException("Unknown option: " + args[i]);
            }
        }
        
        return options;
    }

    static class Options {
        int ops = 10000;
        StorageEngineType engine = StorageEngineType.IN_MEMORY;
        int threads = 1;
        String workload = "all";
        boolean helpRequested = false;
    }

    static class BenchmarkResults {
        private final Map<String, Result> results = new LinkedHashMap<>();

        void record(String name, int ops, long timeMs) {
            var throughput = (ops * 1000L) / Math.max(timeMs, 1);
            var latencyMs = (double) timeMs / ops * 1000;
            results.put(name, new Result(ops, timeMs, throughput, latencyMs));
        }

        void printSummary() {
            System.out.println("\n" + "=".repeat(60));
            System.out.println("BENCHMARK RESULTS");
            System.out.println("=".repeat(60));
            
            for (var entry : results.entrySet()) {
                var r = entry.getValue();
                System.out.printf("%-25s %,10d ops in %,6d ms (%,8d ops/s) lat: %.3f µs%n",
                    entry.getKey(), r.ops(), r.timeMs(), r.throughput(), r.latencyUs());
            }
        }

        record Result(int ops, long timeMs, long throughput, double latencyUs) {
            public int ops() { return ops; }
            public long timeMs() { return timeMs; }
            public long throughput() { return throughput; }
            public double latencyUs() { return latencyUs; }
        }
    }
}
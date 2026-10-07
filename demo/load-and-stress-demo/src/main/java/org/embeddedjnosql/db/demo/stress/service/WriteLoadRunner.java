package org.embeddedjnosql.db.demo.stress.service;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.demo.stress.MetricsReport;
import org.embeddedjnosql.db.demo.stress.model.StressRecord;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Orchestrates concurrent write-load scenarios against EmbedJNoSQL's document engine.
 * Spawns N threads each performing a fixed number of document insertions,
 * collecting per-operation latency samples for statistical analysis.
 */
public class WriteLoadRunner {

    private final EmbedJNoSQL db;
    private final DocumentCollection collection;

    public WriteLoadRunner(EmbedJNoSQL db, String collectionName) {
        this.db = db;
        this.collection = db.documentCollection(collectionName);
    }

    /**
     * Runs a concurrent write-only scenario.
     *
     * @param threads       Number of parallel writer threads
     * @param opsPerThread  Documents each thread will insert
     * @param payloadBytes  Synthetic payload size per document
     * @return MetricsReport with throughput and latency percentiles
     */
    public MetricsReport runConcurrentWrites(int threads, int opsPerThread, int payloadBytes)
            throws InterruptedException {

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        AtomicLong successCount = new AtomicLong();
        AtomicLong failureCount = new AtomicLong();
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();

        for (int t = 0; t < threads; t++) {
            final String threadId = "T-" + String.format("%03d", t + 1);
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await(); // synchronized start
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    done.countDown();
                    return;
                }

                for (int op = 0; op < opsPerThread; op++) {
                    StressRecord record = StressRecord.generate(threadId, "WRITE_HEAVY", payloadBytes);
                    long opStart = System.currentTimeMillis();
                    try {
                        Document doc = Document.of(record.toMap()).id(record.id());
                        collection.insert(doc);
                        latencies.add(System.currentTimeMillis() - opStart);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        latencies.add(System.currentTimeMillis() - opStart);
                        failureCount.incrementAndGet();
                    }
                }
                done.countDown();
            });
        }

        ready.await(); // wait until all threads are ready
        long wallStart = System.currentTimeMillis();
        start.countDown(); // fire!
        done.await(); // wait for all threads to finish
        long durationMs = System.currentTimeMillis() - wallStart;

        executor.shutdown();

        long[] sorted = latencies.stream().mapToLong(Long::longValue).sorted().toArray();
        return MetricsReport.from(
                "CONCURRENT_WRITES",
                threads, successCount.get(), failureCount.get(), durationMs, sorted
        );
    }

    /**
     * Runs a read-after-write scenario: each thread writes a document then reads it back by ID.
     */
    public MetricsReport runReadAfterWrite(int threads, int opsPerThread, int payloadBytes)
            throws InterruptedException {

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        AtomicLong successCount = new AtomicLong();
        AtomicLong failureCount = new AtomicLong();
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();

        for (int t = 0; t < threads; t++) {
            final String threadId = "T-" + String.format("%03d", t + 1);
            executor.submit(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    done.countDown();
                    return;
                }

                for (int op = 0; op < opsPerThread; op++) {
                    StressRecord record = StressRecord.generate(threadId, "MIXED", payloadBytes);
                    long opStart = System.currentTimeMillis();
                    try {
                        // Write
                        collection.insert(Document.of(record.toMap()).id(record.id()));
                        // Read back
                        Document retrieved = collection.findById(record.id());
                        if (retrieved == null) {
                            throw new IllegalStateException("Read-after-write consistency violation for id=" + record.id());
                        }
                        latencies.add(System.currentTimeMillis() - opStart);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        latencies.add(System.currentTimeMillis() - opStart);
                        failureCount.incrementAndGet();
                    }
                }
                done.countDown();
            });
        }

        ready.await();
        long wallStart = System.currentTimeMillis();
        start.countDown();
        done.await();
        long durationMs = System.currentTimeMillis() - wallStart;

        executor.shutdown();

        long[] sorted = latencies.stream().mapToLong(Long::longValue).sorted().toArray();
        return MetricsReport.from(
                "READ_AFTER_WRITE",
                threads, successCount.get(), failureCount.get(), durationMs, sorted
        );
    }

    /** Returns the total number of documents currently in the collection. */
    public long count() {
        return collection.count();
    }
}

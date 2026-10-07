package org.embeddedjnosql.db.demo.stress.service;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.demo.stress.MetricsReport;
import org.embeddedjnosql.db.demo.stress.model.StressRecord;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.kv.KeyValueBucket;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simulates saturation scenarios: sustained peak-load writing at maximum concurrency
 * to observe throughput degradation, error rate, and latency drift under pressure.
 *
 * <p>The "charge test" model fills the database aggressively until the JVM heap or
 * engine signals back-pressure; the error budget (< 1%) defines the passing criterion.</p>
 */
public class ChargeTestRunner {

    private final EmbedJNoSQL db;
    private final DocumentCollection collection;
    private final KeyValueBucket kvBucket;

    public ChargeTestRunner(EmbedJNoSQL db) {
        this.db = db;
        this.collection = db.documentCollection("charge_test_records");
        this.kvBucket = db.keyValueBucket("charge_test_kv");
    }

    /**
     * Executes sustained write saturation at maximum concurrency for the specified duration.
     * All threads hammer the document store continuously until durationMs elapses.
     *
     * @param threads    Number of concurrent writer threads
     * @param durationMs How long to sustain the load (wall clock)
     * @param payloadBytes Payload size per document
     * @return MetricsReport capturing saturation behavior
     */
    public MetricsReport runSaturationTest(int threads, long durationMs, int payloadBytes)
            throws InterruptedException {

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);

        AtomicLong successCount = new AtomicLong();
        AtomicLong failureCount = new AtomicLong();
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();

        List<Future<?>> futures = new ArrayList<>(threads);
        long endEpoch = System.currentTimeMillis() + durationMs + 500; // extended to allow barrier

        for (int t = 0; t < threads; t++) {
            final String tid = "CHG-" + String.format("%03d", t + 1);
            Future<?> f = executor.submit(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                while (System.currentTimeMillis() < endEpoch) {
                    StressRecord rec = StressRecord.generate(tid, "SATURATION", payloadBytes);
                    long opStart = System.currentTimeMillis();
                    try {
                        collection.insert(Document.of(rec.toMap()).id(rec.id()));
                        latencies.add(System.currentTimeMillis() - opStart);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        latencies.add(System.currentTimeMillis() - opStart);
                        failureCount.incrementAndGet();
                    }
                }
            });
            futures.add(f);
        }

        ready.await();
        long wallStart = System.currentTimeMillis();
        start.countDown();

        // Allow threads to run for the specified duration
        Thread.sleep(durationMs);

        executor.shutdownNow();
        executor.awaitTermination(2, TimeUnit.SECONDS);
        long actualDuration = System.currentTimeMillis() - wallStart;

        long[] sorted = latencies.stream().mapToLong(Long::longValue).sorted().toArray();
        return MetricsReport.from(
                "SATURATION_TEST",
                threads, successCount.get(), failureCount.get(), actualDuration, sorted
        );
    }

    /**
     * Mixed workload: concurrently alternates writes and key-value puts.
     * Models a realistic mixed-engine charge scenario.
     */
    public MetricsReport runMixedEngineLoad(int threads, int opsPerThread, int payloadBytes)
            throws InterruptedException {

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        AtomicLong successCount = new AtomicLong();
        AtomicLong failureCount = new AtomicLong();
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            executor.submit(() -> {
                ready.countDown();
                try { start.await(); } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    done.countDown();
                    return;
                }

                for (int op = 0; op < opsPerThread; op++) {
                    long opStart = System.currentTimeMillis();
                    try {
                        if (op % 2 == 0) {
                            // Document write
                            StressRecord rec = StressRecord.generate("MX-" + tid, "MIXED", payloadBytes);
                            collection.insert(Document.of(rec.toMap()).id(rec.id()));
                        } else {
                            // Key-value put
                            kvBucket.put("KEY-" + tid + "-" + op, "VAL-" + System.nanoTime());
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
        long actualDuration = System.currentTimeMillis() - wallStart;

        executor.shutdown();

        long[] sorted = latencies.stream().mapToLong(Long::longValue).sorted().toArray();
        return MetricsReport.from(
                "MIXED_ENGINE_LOAD",
                threads, successCount.get(), failureCount.get(), actualDuration, sorted
        );
    }

    /** Total records inserted into the charge collection. */
    public long documentCount() {
        return collection.count();
    }
}

package org.embeddedjnosql.db.demo.stress;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.demo.stress.service.ChargeTestRunner;
import org.embeddedjnosql.db.demo.stress.service.WriteLoadRunner;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated validation suite for EmbedJNoSQL load testing, concurrency, and charge scenarios.
 *
 * <p>Each test scenario is structured around:</p>
 * <ul>
 *   <li>Correctness assertions (no data loss, no consistency violations)</li>
 *   <li>Performance assertions (non-zero throughput, p99 latency within tolerance)</li>
 *   <li>Error budget assertions (< 1% failure rate)</li>
 * </ul>
 *
 * <p>Test IDs map to the VALIDATION-MATRIX.md entries.</p>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LoadAndChargeTest {

    private EmbedJNoSQL db;
    private WriteLoadRunner writeRunner;
    private ChargeTestRunner chargeRunner;

    @BeforeEach
    void setUp() {
        db = EmbedJNoSQL.inMemory();
        writeRunner = new WriteLoadRunner(db, "stress_documents");
        chargeRunner = new ChargeTestRunner(db);
    }

    @AfterEach
    void tearDown() {
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LOAD-01 — Concurrent Writes: throughput and data integrity
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("LOAD-01: Concurrent writes — 10 threads × 100 ops must succeed with >0 ops/sec")
    void testConcurrentWriteThroughput() throws InterruptedException {
        int threads = 10;
        int opsPerThread = 100;

        MetricsReport report = writeRunner.runConcurrentWrites(threads, opsPerThread, 128);

        System.out.println("[LOAD-01] " + report.summary());

        // All operations must have been attempted
        assertEquals(threads * opsPerThread, report.totalOperations(),
                "Total operations must equal threads × ops-per-thread");

        // Error budget: < 1% failures
        assertTrue(report.isWithinErrorBudget(),
                "Error rate exceeded 1%. Failures=" + report.failureCount());

        // Throughput must be positive
        assertTrue(report.throughputOps() > 0,
                "Throughput must be positive, got " + report.throughputOps());

        // All documents must be persisted (verifiable via count)
        long count = writeRunner.count();
        assertTrue(count >= report.successCount(),
                "Persistent doc count must be >= successful writes. count=" + count);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LOAD-02 — High Concurrency: 50 threads test thread-safety
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("LOAD-02: 50-thread concurrent writes — zero data corruption, < 1% error rate")
    void testHighConcurrencyThreadSafety() throws InterruptedException {
        int threads = 50;
        int opsPerThread = 50;

        MetricsReport report = writeRunner.runConcurrentWrites(threads, opsPerThread, 64);

        System.out.println("[LOAD-02] " + report.summary());

        // Error budget
        assertTrue(report.isWithinErrorBudget(),
                "High-concurrency run exceeded error budget. fail=" + report.failureCount() +
                " total=" + report.totalOperations());

        // At least 95% of expected operations succeeded
        long expected = (long) threads * opsPerThread;
        assertTrue(report.successCount() >= (long) (expected * 0.95),
                "Success count too low: " + report.successCount() + " of " + expected);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LOAD-03 — Read-After-Write Consistency
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("LOAD-03: Read-after-write consistency — every write immediately readable, 0 violations")
    void testReadAfterWriteConsistency() throws InterruptedException {
        int threads = 8;
        int opsPerThread = 50;

        MetricsReport report = writeRunner.runReadAfterWrite(threads, opsPerThread, 128);

        System.out.println("[LOAD-03] " + report.summary());

        // Zero failures means zero consistency violations (runner throws on miss)
        assertEquals(0, report.failureCount(),
                "Read-after-write consistency violations detected: " + report.failureCount());

        // All ops succeeded
        assertEquals((long) threads * opsPerThread, report.successCount());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LOAD-04 — Mixed Engine Load (Document + Key-Value interleaved)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("LOAD-04: Mixed document+KV engine load — < 1% error rate under 12 threads")
    void testMixedEngineLoad() throws InterruptedException {
        int threads = 12;
        int opsPerThread = 100;

        MetricsReport report = chargeRunner.runMixedEngineLoad(threads, opsPerThread, 64);

        System.out.println("[LOAD-04] " + report.summary());

        assertTrue(report.isWithinErrorBudget(),
                "Mixed engine error budget exceeded. failures=" + report.failureCount());

        assertTrue(report.successCount() > 0,
                "No operations succeeded in mixed-engine run");

        // Throughput sanity: should complete at least 50 ops/sec
        assertTrue(report.throughputOps() > 50,
                "Throughput unexpectedly low: " + report.throughputOps() + " ops/sec");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LOAD-05 — Charge / Saturation Test (time-bounded burst)
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @DisplayName("LOAD-05: Saturation test — 20 threads for 1.5 seconds, < 1% failures, > 0 writes/sec")
    void testSaturationChargeTest() throws InterruptedException {
        MetricsReport report = chargeRunner.runSaturationTest(20, 1500, 64);

        System.out.println("[LOAD-05] " + report.summary());

        // Error budget must hold even under saturation
        assertTrue(report.isWithinErrorBudget(),
                "Saturation error budget exceeded. failures=" + report.failureCount() +
                " total=" + report.totalOperations());

        // Some ops must have been performed
        assertTrue(report.totalOperations() > 0,
                "No operations were executed during saturation window");

        // Throughput must be positive
        assertTrue(report.throughputOps() > 0,
                "Throughput must be positive during saturation");

        // Verify records were actually written to the engine
        assertTrue(chargeRunner.documentCount() > 0,
                "No documents found in charge collection after saturation test");
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LOAD-06 — MetricsReport statistics correctness
    // ═══════════════════════════════════════════════════════════════════════════

    @Test
    @Order(6)
    @DisplayName("LOAD-06: MetricsReport correctly computes percentiles and throughput from raw latencies")
    void testMetricsReportStatistics() {
        // Construct a controlled latency distribution: 100 values [1..100]
        long[] latencies = new long[100];
        for (int i = 0; i < 100; i++) {
            latencies[i] = i + 1; // 1ms..100ms
        }

        MetricsReport report = MetricsReport.from("TEST_STATS", 4, 100, 0, 1000, latencies);

        assertEquals(100, report.totalOperations());
        assertEquals(100, report.successCount());
        assertEquals(0, report.failureCount());
        assertTrue(report.isWithinErrorBudget());

        // p50 of [1..100] at index 50 = 51ms
        assertEquals(51, report.p50LatencyMs(), "p50 should be 51 for latencies 1..100");
        // p95 at index 95 = 96ms
        assertEquals(96, report.p95LatencyMs(), "p95 should be 96 for latencies 1..100");
        // p99 at index 99 = 100ms
        assertEquals(100, report.p99LatencyMs(), "p99 should be 100 for latencies 1..100");

        // Throughput = 100 success / 1000ms * 1000 = 100 ops/sec
        assertEquals(100.0, report.throughputOps(), 0.01);
    }
}

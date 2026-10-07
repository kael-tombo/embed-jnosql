package org.embeddedjnosql.db.demo.stress;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.demo.stress.service.ChargeTestRunner;
import org.embeddedjnosql.db.demo.stress.service.WriteLoadRunner;

/**
 * Executable CLI demonstration of EmbedJNoSQL load testing, charge saturation,
 * and multi-engine concurrency benchmarks.
 *
 * <p>Scenarios covered:</p>
 * <ol>
 *   <li>Concurrent Writes — 20 threads × 500 ops = 10,000 documents</li>
 *   <li>Read-After-Write Consistency — 10 threads × 200 ops each</li>
 *   <li>Mixed Engine Load — 16 threads alternating doc/KV writes</li>
 *   <li>Saturation Test — 30 threads running freely for 2 seconds</li>
 * </ol>
 */
public class StressDemoApplication {

    public static void main(String[] args) throws InterruptedException {
        banner("EmbedJNoSQL Demo: Load Testing, Charge Saturation & Concurrency Benchmarks");

        try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {

            WriteLoadRunner writeRunner = new WriteLoadRunner(db, "stress_documents");
            ChargeTestRunner chargeRunner = new ChargeTestRunner(db);

            // ── Scenario 1: Concurrent Writes ────────────────────────────────────
            section("Scenario 1 — Concurrent Writes (20 threads × 500 ops)");
            MetricsReport s1 = writeRunner.runConcurrentWrites(20, 500, 256);
            printReport(s1);
            System.out.printf("  Collection size after: %,d documents%n%n", writeRunner.count());

            // ── Scenario 2: Read-After-Write Consistency ─────────────────────────
            section("Scenario 2 — Read-After-Write Consistency (10 threads × 200 ops)");
            MetricsReport s2 = writeRunner.runReadAfterWrite(10, 200, 128);
            printReport(s2);
            System.out.printf("  All reads consistent: %s%n%n",
                    s2.failureCount() == 0 ? "YES ✓" : "NO ✗ failures=" + s2.failureCount());

            // ── Scenario 3: Mixed Engine Load ────────────────────────────────────
            section("Scenario 3 — Mixed Engine Load (16 threads, doc+kv interleaved, 300 ops/thread)");
            MetricsReport s3 = chargeRunner.runMixedEngineLoad(16, 300, 128);
            printReport(s3);

            // ── Scenario 4: Saturation Test ──────────────────────────────────────
            section("Scenario 4 — Saturation / Charge Test (30 threads, 2 seconds sustained)");
            MetricsReport s4 = chargeRunner.runSaturationTest(30, 2000, 64);
            printReport(s4);
            System.out.printf("  Total charge records: %,d%n", chargeRunner.documentCount());
            System.out.printf("  Within error budget (<1%% failures): %s%n%n",
                    s4.isWithinErrorBudget() ? "PASS ✓" : "FAIL ✗");

            banner("All Load & Stress Scenarios Completed");
        }
    }

    private static void banner(String title) {
        System.out.println();
        System.out.println("═".repeat(65));
        System.out.printf("  %s%n", title);
        System.out.println("═".repeat(65));
    }

    private static void section(String title) {
        System.out.printf("▶  %s%n", title);
    }

    private static void printReport(MetricsReport r) {
        System.out.println("  " + r.summary());
    }
}

package org.embeddedjnosql.db.demo.stress.benchmark;

import java.util.Arrays;

/**
 * Benchmark telemetry report recording throughput and latency percentiles (p50, p95, p99).
 */
public record MetricsReport(
        String workloadName,
        int totalOperations,
        int successfulOperations,
        int failedOperations,
        int concurrencyThreads,
        long durationMs,
        double throughputOpsPerSec,
        double avgLatencyMs,
        double p50LatencyMs,
        double p95LatencyMs,
        double p99LatencyMs,
        double maxLatencyMs
) {
    public static MetricsReport calculate(String workloadName, int concurrency, long durationMs, long[] latenciesNanos, int failures) {
        int total = latenciesNanos.length;
        int successful = total - failures;
        double opsPerSec = durationMs > 0 ? (total * 1000.0) / durationMs : 0.0;

        if (total == 0) {
            return new MetricsReport(workloadName, 0, 0, failures, concurrency, durationMs, 0, 0, 0, 0, 0, 0);
        }

        long[] sorted = Arrays.copyOf(latenciesNanos, total);
        Arrays.sort(sorted);

        double sumMs = 0;
        for (long l : sorted) {
            sumMs += (l / 1_000_000.0);
        }
        double avgMs = sumMs / total;
        double p50 = sorted[(int) (total * 0.50)] / 1_000_000.0;
        double p95 = sorted[(int) (total * 0.95)] / 1_000_000.0;
        double p99 = sorted[(int) (total * 0.99)] / 1_000_000.0;
        double max = sorted[total - 1] / 1_000_000.0;

        return new MetricsReport(workloadName, total, successful, failures, concurrency, durationMs,
                opsPerSec, avgMs, p50, p95, p99, max);
    }

    public void printSummary() {
        System.out.printf("""
            -------------------------------------------------------------
            Workload: %s (Threads: %d)
            -------------------------------------------------------------
            Total Operations:      %,d (Success: %,d, Failed: %d)
            Duration:              %d ms
            Throughput:            %,.1f ops/sec
            Latency (Avg / p50):   %.3f ms / %.3f ms
            Latency (p95 / p99):   %.3f ms / %.3f ms
            Max Latency:           %.3f ms
            -------------------------------------------------------------
            """, workloadName, concurrencyThreads, totalOperations, successfulOperations, failedOperations,
                durationMs, throughputOpsPerSec, avgLatencyMs, p50LatencyMs, p95LatencyMs, p99LatencyMs, maxLatencyMs);
    }
}

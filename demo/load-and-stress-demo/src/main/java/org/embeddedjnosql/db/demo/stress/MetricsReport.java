package org.embeddedjnosql.db.demo.stress;

/**
 * Immutable performance metrics report produced by a stress/load test run.
 *
 * @param scenarioName      Human-readable name of the benchmark scenario
 * @param threads           Number of concurrent threads used
 * @param totalOperations   Total number of operations attempted
 * @param successCount      Number of operations that completed without error
 * @param failureCount      Number of failed or rejected operations
 * @param durationMs        Wall-clock duration of the scenario in milliseconds
 * @param p50LatencyMs      Median (50th percentile) operation latency in ms
 * @param p95LatencyMs      95th-percentile operation latency in ms
 * @param p99LatencyMs      99th-percentile operation latency in ms
 * @param throughputOps     Operations per second achieved
 */
public record MetricsReport(
        String scenarioName,
        int threads,
        long totalOperations,
        long successCount,
        long failureCount,
        long durationMs,
        long p50LatencyMs,
        long p95LatencyMs,
        long p99LatencyMs,
        double throughputOps
) {

    /** Builds a MetricsReport from raw latency samples. */
    public static MetricsReport from(
            String scenarioName,
            int threads,
            long successCount,
            long failureCount,
            long durationMs,
            long[] sortedLatencies) {

        long n = sortedLatencies.length;
        long p50 = n > 0 ? sortedLatencies[(int) (n * 0.50)] : 0;
        long p95 = n > 0 ? sortedLatencies[(int) (n * 0.95)] : 0;
        long p99 = n > 0 ? sortedLatencies[(int) (n * 0.99)] : 0;
        double throughput = durationMs > 0 ? (successCount * 1000.0) / durationMs : 0;

        return new MetricsReport(
                scenarioName,
                threads,
                successCount + failureCount,
                successCount,
                failureCount,
                durationMs,
                p50, p95, p99,
                throughput
        );
    }

    /** Returns true if the error rate is within the acceptable threshold (< 1%). */
    public boolean isWithinErrorBudget() {
        if (totalOperations == 0) return true;
        return ((double) failureCount / totalOperations) < 0.01;
    }

    /** Formatted summary for console output. */
    public String summary() {
        return String.format(
                "[%s] threads=%d ops=%,d success=%,d fail=%,d duration=%dms " +
                "p50=%dms p95=%dms p99=%dms throughput=%.1f ops/sec",
                scenarioName, threads, totalOperations, successCount, failureCount,
                durationMs, p50LatencyMs, p95LatencyMs, p99LatencyMs, throughputOps
        );
    }
}

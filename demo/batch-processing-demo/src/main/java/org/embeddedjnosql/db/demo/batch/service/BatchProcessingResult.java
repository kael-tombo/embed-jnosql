package org.embeddedjnosql.db.demo.batch.service;

/**
 * Encapsulates performance telemetry and outcome of a batch execution.
 */
public record BatchProcessingResult(
        int totalProcessed,
        int successCount,
        int failureCount,
        long durationMs,
        double throughputOpsPerSec,
        boolean rolledBack,
        String message
) {
    public static BatchProcessingResult success(int count, long durationMs, String message) {
        double opsPerSec = durationMs > 0 ? (count * 1000.0) / durationMs : count * 1000.0;
        return new BatchProcessingResult(count, count, 0, durationMs, opsPerSec, false, message);
    }

    public static BatchProcessingResult failure(int attempted, long durationMs, String message) {
        return new BatchProcessingResult(attempted, 0, attempted, durationMs, 0.0, true, message);
    }
}

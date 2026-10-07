package org.embeddedjnosql.db.core.util;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Retry with Exponential Backoff
 *
 * Implements retry logic with:
 * - Exponential backoff between attempts
 * - Optional jitter to prevent thundering herd
 * - Configurable max attempts and delay
 *
 * @param <T> return type of operation
 */
public class RetryWithBackoff<T> {

    private final int maxAttempts;
    private final long initialDelayMs;
    private final long maxDelayMs;
    private final double multiplier;
    private final boolean jitter;

    public RetryWithBackoff() {
        this(3, 1000, 30000, 2.0, true);
    }

    public RetryWithBackoff(int maxAttempts, long initialDelayMs, long maxDelayMs,
                            double multiplier, boolean jitter) {
        this.maxAttempts = maxAttempts;
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.multiplier = multiplier;
        this.jitter = jitter;
    }

    /**
     * Execute operation with retry and exponential backoff.
     * @param operation the operation to execute
     * @return result of operation
     * @throws RuntimeException if all attempts fail
     */
    public T execute(Callable<T> operation) {
        return execute(operation, null);
    }

    /**
     * Execute operation with retry, exponential backoff, and optional error handler.
     * @param operation the operation to execute
     * @param onError callback invoked with each failure (attempt number, exception)
     * @return result of operation
     * @throws RuntimeException if all attempts fail
     */
    public T execute(Callable<T> operation, Consumer<RetryContext> onError) {
        long delay = initialDelayMs;
        int attempt = 0;

        while (true) {
            attempt++;
            try {
                return operation.call();
            } catch (Exception e) {
                if (onError != null) {
                    onError.accept(new RetryContext(attempt, maxAttempts, delay, e));
                }

                if (attempt >= maxAttempts) {
                    throw new RetryExhaustedException("All " + maxAttempts + " attempts failed", e);
                }

                // Wait with exponential backoff
                try {
                    long sleepMs = calculateSleep(delay);
                    TimeUnit.MILLISECONDS.sleep(sleepMs);
                    delay = Math.min((long) (delay * multiplier), maxDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RetryExhaustedException("Retry interrupted", ie);
                }
            }
        }
    }

    /**
     * Execute runnable operation with retry and exponential backoff.
     * @param operation the operation to execute
     * @throws RuntimeException if all attempts fail
     */
    public void executeRunnable(Runnable operation) {
        executeRunnable(operation, null);
    }

    /**
     * Execute runnable operation with retry, exponential backoff, and optional error handler.
     * @param operation the operation to execute
     * @param onError callback invoked with each failure (attempt number, exception)
     * @throws RuntimeException if all attempts fail
     */
    public void executeRunnable(Runnable operation, Consumer<RetryContext> onError) {
        long delay = initialDelayMs;
        int attempt = 0;

        while (true) {
            attempt++;
            try {
                operation.run();
                return;
            } catch (Exception e) {
                if (onError != null) {
                    onError.accept(new RetryContext(attempt, maxAttempts, delay, e));
                }

                if (attempt >= maxAttempts) {
                    throw new RetryExhaustedException("All " + maxAttempts + " attempts failed", e);
                }

                // Wait with exponential backoff
                try {
                    long sleepMs = calculateSleep(delay);
                    TimeUnit.MILLISECONDS.sleep(sleepMs);
                    delay = Math.min((long) (delay * multiplier), maxDelayMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RetryExhaustedException("Retry interrupted", ie);
                }
            }
        }
    }

    /**
     * Calculate sleep duration with optional jitter.
     */
    private long calculateSleep(long baseDelay) {
        if (jitter) {
            // Add random jitter up to 25% of delay
            double jitterFactor = 0.75 + (Math.random() * 0.5);
            return (long) (baseDelay * jitterFactor);
        }
        return baseDelay;
    }

    /**
     * Context passed to error handler.
     */
    public record RetryContext(int attempt, int maxAttempts, long nextDelayMs, Exception exception) {}

    /**
     * Exception thrown when all retry attempts are exhausted.
     */
    public static class RetryExhaustedException extends RuntimeException {
        public RetryExhaustedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

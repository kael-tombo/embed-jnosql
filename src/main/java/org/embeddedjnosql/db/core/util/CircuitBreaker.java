package org.embeddedjnosql.db.core.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Circuit Breaker Pattern Implementation
 *
 * States:
 * - CLOSED: Normal operation, requests pass through
 * - OPEN: Circuit tripped, requests fail immediately
 * - HALF_OPEN: Testing if service recovered, limited requests allowed
 *
 * @param <T> return type of protected operations
 */
public class CircuitBreaker<T> {

    public enum State {
        CLOSED, OPEN, HALF_OPEN
    }

    private final String name;
    private final int failureThreshold;
    private final int successThreshold;
    private final long openTimeoutMs;
    private final long halfOpenMaxCalls;

    private final AtomicInteger failureCount;
    private final AtomicInteger successCount;
    private final AtomicLong lastFailureTime;
    private final AtomicLong lastStateChange;
    private volatile State state;

    public CircuitBreaker(String name) {
        this(name, 5, 2, 30000, 3);
    }

    public CircuitBreaker(String name, int failureThreshold, int successThreshold,
                          long openTimeoutMs, long halfOpenMaxCalls) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.successThreshold = successThreshold;
        this.openTimeoutMs = openTimeoutMs;
        this.halfOpenMaxCalls = halfOpenMaxCalls;
        this.state = State.CLOSED;
        this.failureCount = new AtomicInteger(0);
        this.successCount = new AtomicInteger(0);
        this.lastFailureTime = new AtomicLong(0);
        this.lastStateChange = new AtomicLong(System.currentTimeMillis());
    }

    /**
     * Execute operation with circuit breaker protection.
     * @param operation the operation to execute
     * @return result of operation
     * @throws CircuitBreakerOpenException if circuit is open
     * @throws RuntimeException if operation fails
     */
    public T execute(Supplier<T> operation) {
        if (!allowRequest()) {
            throw new CircuitBreakerOpenException(name);
        }

        try {
            T result = operation.get();
            onSuccess();
            return result;
        } catch (Exception e) {
            onFailure();
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
    }

    /**
     * Execute runnable operation with circuit breaker protection.
     * @param operation the operation to execute
     * @throws CircuitBreakerOpenException if circuit is open
     * @throws RuntimeException if operation fails
     */
    public void executeRunnable(Runnable operation) {
        if (!allowRequest()) {
            throw new CircuitBreakerOpenException(name);
        }

        try {
            operation.run();
            onSuccess();
        } catch (Exception e) {
            onFailure();
            throw e instanceof RuntimeException re ? re : new RuntimeException(e);
        }
    }

    /**
     * Check if request should be allowed.
     */
    private synchronized boolean allowRequest() {
        long now = System.currentTimeMillis();

        switch (state) {
            case CLOSED:
                return true;
            case OPEN:
                if (now - lastFailureTime.get() > openTimeoutMs) {
                    transitionToHalfOpen();
                    return true;
                }
                return false;
            case HALF_OPEN:
                // Allow limited requests in half-open state
                return true;
            default:
                return false;
        }
    }

    /**
     * Record successful operation.
     */
    private synchronized void onSuccess() {
        failureCount.set(0);

        if (state == State.HALF_OPEN) {
            successCount.incrementAndGet();
            if (successCount.get() >= successThreshold) {
                transitionToClosed();
            }
        }
    }

    /**
     * Record failed operation.
     */
    private synchronized void onFailure() {
        failureCount.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());

        if (state == State.HALF_OPEN) {
            transitionToOpen();
        } else if (state == State.CLOSED && failureCount.get() >= failureThreshold) {
            transitionToOpen();
        }
    }

    private void transitionToOpen() {
        state = State.OPEN;
        lastStateChange.set(System.currentTimeMillis());
        successCount.set(0);
        System.err.println("[CircuitBreaker] " + name + " transitioned to OPEN");
    }

    private void transitionToHalfOpen() {
        state = State.HALF_OPEN;
        lastStateChange.set(System.currentTimeMillis());
        successCount.set(0);
        System.err.println("[CircuitBreaker] " + name + " transitioned to HALF_OPEN");
    }

    private void transitionToClosed() {
        state = State.CLOSED;
        lastStateChange.set(System.currentTimeMillis());
        failureCount.set(0);
        successCount.set(0);
        System.err.println("[CircuitBreaker] " + name + " transitioned to CLOSED");
    }

    /**
     * Get current state.
     */
    public State getState() {
        return state;
    }

    /**
     * Get statistics.
     */
    public java.util.Map<String, Object> getStats() {
        return java.util.Map.of(
            "name", name,
            "state", state.name(),
            "failureCount", failureCount.get(),
            "successCount", successCount.get(),
            "failureThreshold", failureThreshold,
            "successThreshold", successThreshold,
            "openTimeoutMs", openTimeoutMs,
            "lastFailureTime", lastFailureTime.get(),
            "lastStateChange", lastStateChange.get()
        );
    }

    /**
     * Reset circuit breaker to closed state.
     */
    public synchronized void reset() {
        state = State.CLOSED;
        failureCount.set(0);
        successCount.set(0);
        lastFailureTime.set(0);
        lastStateChange.set(System.currentTimeMillis());
    }

    /**
     * Exception thrown when circuit is open.
     */
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String circuitName) {
            super("Circuit breaker '" + circuitName + "' is OPEN - request rejected");
        }
    }
}

package org.embeddedjnosql.db.core.pool;

import org.embeddedjnosql.db.Experimental;
import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * EXPERIMENTAL: pools {@link EmbedJNoSQL} instances behind a borrow/return lifecycle.
 *
 * <p>Labeled by product decision (release review round 3): this class has no production caller,
 * no functional test coverage, and no documentation beyond this notice. It ships in the jar, but
 * the API may change or be removed in a future release without notice. Do not build anything
 * critical on it yet.</p>
 */
@Experimental("no production caller; no functional coverage; API may change or be removed without notice")
public class EmbedJNoSQLPool {

    private final Supplier<EmbedJNoSQL> factory;
    private final List<PooledConnection> available;
    private final ConcurrentHashMap<EmbedJNoSQL, PooledConnection> inUse;
    private final Semaphore semaphore;
    private final AtomicInteger totalCreated;
    private final int maxSize;
    private final boolean autoShutdown;

    private volatile boolean closed;

    public EmbedJNoSQLPool(Supplier<EmbedJNoSQL> factory, int maxSize) {
        this(factory, maxSize, true);
    }

    public EmbedJNoSQLPool(Supplier<EmbedJNoSQL> factory, int maxSize, boolean autoShutdown) {
        this.factory = factory;
        this.maxSize = maxSize;
        this.autoShutdown = autoShutdown;
        this.available = new ArrayList<>();
        this.inUse = new ConcurrentHashMap<>();
        this.semaphore = new Semaphore(maxSize);
        this.totalCreated = new AtomicInteger(0);
        this.closed = false;
    }

    public static EmbedJNoSQLPoolBuilder builder() {
        return new EmbedJNoSQLPoolBuilder();
    }

    public EmbedJNoSQL borrow() {
        checkOpen();

        try {
            semaphore.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for connection", e);
        }

        PooledConnection pooled = available.isEmpty() ? createConnection() : available.remove(0);
        inUse.put(pooled.db, pooled);
        pooled.borrowedAt = System.currentTimeMillis();
        
        return pooled.db;
    }

    public void release(EmbedJNoSQL db) {
        if (db == null) return;

        PooledConnection pooled = inUse.remove(db);
        if (pooled == null) {
            return;
        }

        if (pooled.isHealthy()) {
            pooled.lastUsed = System.currentTimeMillis();
            available.add(pooled);
        } else {
            pooled.close();
            totalCreated.decrementAndGet();
        }

        semaphore.release();
    }

    public <T> T execute(PoolFunction<T> function) {
        EmbedJNoSQL db = borrow();
        try {
            return function.apply(db);
        } finally {
            release(db);
        }
    }

    public void close() {
        if (closed) return;
        closed = true;

        for (var pooled : available) {
            pooled.close();
        }
        available.clear();

        for (var entry : inUse.entrySet()) {
            entry.getValue().close();
        }
        inUse.clear();
    }

    public PoolStats stats() {
        return new PoolStats(
            available.size(),
            inUse.size(),
            maxSize,
            totalCreated.get()
        );
    }

    private PooledConnection createConnection() {
        if (totalCreated.get() < maxSize) {
            synchronized (this) {
                if (totalCreated.get() < maxSize) {
                    var db = factory.get();
                    totalCreated.incrementAndGet();
                    return new PooledConnection(db);
                }
            }
        }

        waitForAvailable();
        return available.remove(0);
    }

    private void waitForAvailable() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("Pool is closed");
        }
    }

    public interface PoolFunction<T> {
        T apply(EmbedJNoSQL db);
    }

    private static class PooledConnection {
        final EmbedJNoSQL db;
        long borrowedAt;
        long lastUsed;

        PooledConnection(EmbedJNoSQL db) {
            this.db = db;
            this.borrowedAt = System.currentTimeMillis();
            this.lastUsed = System.currentTimeMillis();
        }

        boolean isHealthy() {
            return db != null && db.isOpen();
        }

        void close() {
            if (db != null && db.isOpen()) {
                try {
                    db.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static class PoolStats {
        private final int available;
        private final int inUse;
        private final int maxSize;
        private final int totalCreated;

        PoolStats(int available, int inUse, int maxSize, int totalCreated) {
            this.available = available;
            this.inUse = inUse;
            this.maxSize = maxSize;
            this.totalCreated = totalCreated;
        }

        public int available() { return available; }
        public int inUse() { return inUse; }
        public int maxSize() { return maxSize; }
        public int totalCreated() { return totalCreated; }

        @Override
        public String toString() {
            return String.format("PoolStats{available=%d, inUse=%d, maxSize=%d, totalCreated=%d}",
                available, inUse, maxSize, totalCreated);
        }
    }

    public static class EmbedJNoSQLPoolBuilder {
        private Supplier<EmbedJNoSQL> factory;
        private int maxSize = 10;
        private boolean autoShutdown = true;

        public EmbedJNoSQLPoolBuilder factory(Supplier<EmbedJNoSQL> factory) {
            this.factory = factory;
            return this;
        }

        public EmbedJNoSQLPoolBuilder maxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public EmbedJNoSQLPoolBuilder autoShutdown(boolean autoShutdown) {
            this.autoShutdown = autoShutdown;
            return this;
        }

        public EmbedJNoSQLPool build() {
            if (factory == null) {
                factory = () -> EmbedJNoSQL.embed().build();
            }
            return new EmbedJNoSQLPool(factory, maxSize, autoShutdown);
        }
    }
}

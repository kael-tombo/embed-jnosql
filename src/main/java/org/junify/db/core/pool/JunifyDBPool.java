package org.junify.db.core.pool;

import org.junify.db.Experimental;
import org.junify.db.JunifyDB;
import org.junify.db.config.JunifyDBConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * EXPERIMENTAL: pools {@link JunifyDB} instances behind a borrow/return lifecycle.
 *
 * <p>Labeled by product decision (release review round 3): this class has no production caller,
 * no functional test coverage, and no documentation beyond this notice. It ships in the jar, but
 * the API may change or be removed in a future release without notice. Do not build anything
 * critical on it yet.</p>
 */
@Experimental("no production caller; no functional coverage; API may change or be removed without notice")
public class JunifyDBPool {

    private final Supplier<JunifyDB> factory;
    private final List<PooledConnection> available;
    private final ConcurrentHashMap<JunifyDB, PooledConnection> inUse;
    private final Semaphore semaphore;
    private final AtomicInteger totalCreated;
    private final int maxSize;
    private final boolean autoShutdown;

    private volatile boolean closed;

    public JunifyDBPool(Supplier<JunifyDB> factory, int maxSize) {
        this(factory, maxSize, true);
    }

    public JunifyDBPool(Supplier<JunifyDB> factory, int maxSize, boolean autoShutdown) {
        this.factory = factory;
        this.maxSize = maxSize;
        this.autoShutdown = autoShutdown;
        this.available = new ArrayList<>();
        this.inUse = new ConcurrentHashMap<>();
        this.semaphore = new Semaphore(maxSize);
        this.totalCreated = new AtomicInteger(0);
        this.closed = false;
    }

    public static JunifyDBPoolBuilder builder() {
        return new JunifyDBPoolBuilder();
    }

    public JunifyDB borrow() {
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

    public void release(JunifyDB db) {
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
        JunifyDB db = borrow();
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
        T apply(JunifyDB db);
    }

    private static class PooledConnection {
        final JunifyDB db;
        long borrowedAt;
        long lastUsed;

        PooledConnection(JunifyDB db) {
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

    public static class JunifyDBPoolBuilder {
        private Supplier<JunifyDB> factory;
        private int maxSize = 10;
        private boolean autoShutdown = true;

        public JunifyDBPoolBuilder factory(Supplier<JunifyDB> factory) {
            this.factory = factory;
            return this;
        }

        public JunifyDBPoolBuilder maxSize(int maxSize) {
            this.maxSize = maxSize;
            return this;
        }

        public JunifyDBPoolBuilder autoShutdown(boolean autoShutdown) {
            this.autoShutdown = autoShutdown;
            return this;
        }

        public JunifyDBPool build() {
            if (factory == null) {
                factory = () -> JunifyDB.embed().build();
            }
            return new JunifyDBPool(factory, maxSize, autoShutdown);
        }
    }
}

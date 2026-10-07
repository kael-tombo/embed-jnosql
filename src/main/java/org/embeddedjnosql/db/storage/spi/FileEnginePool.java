package org.embeddedjnosql.db.storage.spi;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class FileEnginePool implements Closeable {

    private final Path dataDir;
    private final BlockingQueue<FileEngine> available;
    private final Set<FileEngine> inUse;
    private final ExecutorService cleaner;
    private final int maxSize;
    private final long flushIntervalMs;
    private final boolean autoFlush;
    private volatile boolean closed;
    private final AtomicInteger totalCreated;

    public FileEnginePool(Path dataDir) {
        this(dataDir, 4, 1000, true);
    }

    public FileEnginePool(Path dataDir, int poolSize, long flushIntervalMs, boolean autoFlush) {
        this.dataDir = dataDir;
        this.maxSize = poolSize;
        this.flushIntervalMs = flushIntervalMs;
        this.autoFlush = autoFlush;
        this.available = new LinkedBlockingQueue<>(poolSize);
        this.inUse = ConcurrentHashMap.newKeySet();
        this.cleaner = Executors.newSingleThreadExecutor();
        this.totalCreated = new AtomicInteger(0);
        this.closed = false;

        for (int i = 0; i < poolSize; i++) {
            available.offer(createEngine());
        }

        cleaner.submit(this::cleanerLoop);
    }

    public FileEngine acquire() throws InterruptedException, TimeoutException {
        if (closed) {
            throw new IllegalStateException("Pool is closed");
        }

        try {
            var engine = available.poll(10, TimeUnit.SECONDS);
            if (engine == null) {
                throw new TimeoutException("No engine available in pool");
            }

            inUse.add(engine);
            return engine;
        } catch (InterruptedException e) {
            throw e;
        }
    }

    public void release(FileEngine engine) {
        if (engine == null) return;

        inUse.remove(engine);
        
        if (!closed && totalCreated.get() <= maxSize) {
            available.offer(engine);
        } else {
            engine.close();
            totalCreated.decrementAndGet();
        }
    }

    public int availableCount() {
        return available.size();
    }

    public int inUseCount() {
        return inUse.size();
    }

    public int totalCount() {
        return totalCreated.get();
    }

    private FileEngine createEngine() {
        totalCreated.incrementAndGet();
        return new FileEngine(dataDir, flushIntervalMs, autoFlush);
    }

    private void cleanerLoop() {
        while (!closed) {
            try {
                Thread.sleep(flushIntervalMs * 2);
                
                for (FileEngine engine : inUse) {
                    engine.flush();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;

        cleaner.shutdown();
        try {
            cleaner.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        for (FileEngine engine : available) {
            engine.close();
        }
        for (FileEngine engine : inUse) {
            engine.close();
        }

        available.clear();
        inUse.clear();
    }
}

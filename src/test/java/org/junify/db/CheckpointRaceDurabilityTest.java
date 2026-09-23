package org.junify.db;

import org.junify.db.storage.spi.BTreeEngine;
import org.junify.db.storage.spi.FileEngine;
import org.junify.db.storage.spi.LSMTreeEngine;
import org.junify.db.storage.spi.StorageEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for R-70: a write-ahead checkpoint must never discard a record whose value
 * the snapshot does not yet contain.
 *
 * <p>Measured on B_TREE through the contract gate: 12 documents accepted with HTTP 2xx, the server
 * killed, and <b>3</b> readable afterwards. The log record was written outside the critical section
 * that applies the value, and {@code flush()} persisted and then truncated the log in a separate
 * section — so a checkpoint landing between the two truncated a record whose effect was not yet in
 * the snapshot. The write was acknowledged, absent from disk, and gone from the log.</p>
 *
 * <p>These tests drive {@code flush()} from another thread while writers run, then abandon the
 * engine without {@code close()} and read the data through a fresh engine — which is the state a
 * killed process leaves behind. Every key that was acknowledged must be there.</p>
 */
@DisplayName("a checkpoint racing concurrent writes loses nothing (R-70)")
class CheckpointRaceDurabilityTest {

    private static final String COLLECTION = "hammer";
    private static final int WRITERS = 4;
    private static final int PER_WRITER = 100;

    /**
     * Writes {@code WRITERS * PER_WRITER} keys while a second thread flushes continuously, then
     * abandons the engine and checks every acknowledged key through a fresh one.
     */
    private void hammerThenCrash(Path dir, StorageEngine engine, Function<Path, StorageEngine> reopen)
            throws Exception {
        var acked = ConcurrentHashMap.<String>newKeySet();
        var stop = new AtomicBoolean(false);
        var start = new CountDownLatch(1);

        var flusher = new Thread(() -> {
            try {
                start.await();
                while (!stop.get()) {
                    engine.flush();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "checkpoint-racer");
        flusher.setDaemon(true);
        flusher.start();

        ExecutorService pool = Executors.newFixedThreadPool(WRITERS);
        List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < WRITERS; t++) {
            final int thread = t;
            futures.add(pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < PER_WRITER; i++) {
                        String key = "w" + thread + "-" + i;
                        engine.put(COLLECTION, key, "{\"id\":\"" + key + "\"}");
                        // Acknowledged once put() returns.
                        acked.add(key);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }));
        }

        start.countDown();
        for (var f : futures) {
            f.get(60, TimeUnit.SECONDS);
        }
        pool.shutdown();
        stop.set(true);
        flusher.join(10_000);

        // Every key above is acknowledged and still only in this engine's memory as far as the
        // caller knows: no close() is called before reading through the reopened engine.
        var reopened = reopen.apply(dir);
        try {
            Set<String> missing = ConcurrentHashMap.newKeySet();
            for (String key : acked) {
                if (reopened.get(COLLECTION, key) == null) missing.add(key);
            }
            assertTrue(missing.isEmpty(),
                    acked.size() + " writes were acknowledged while checkpoints ran, but "
                            + missing.size() + " are gone after an unclean stop (e.g. "
                            + new ArrayList<>(missing).subList(0, Math.min(5, missing.size()))
                            + "): a checkpoint must not release a log record whose value the "
                            + "snapshot does not hold");
        } finally {
            // Teardown only — the assertions above ran while the first engine was still open.
            reopened.close();
            engine.close();
        }
    }

    @Test
    @DisplayName("FILE: no acknowledged write is lost to a racing checkpoint")
    void fileEngineLosesNothing(@TempDir Path dir) throws Exception {
        hammerThenCrash(dir,
                new FileEngine(dir, 3_600_000, false), // no background flusher: the test drives them
                d -> new FileEngine(d, 3_600_000, false));
    }

    @Test
    @DisplayName("LSM_TREE: no acknowledged write is lost to a racing checkpoint")
    void lsmTreeEngineLosesNothing(@TempDir Path dir) throws Exception {
        hammerThenCrash(dir, new LSMTreeEngine(dir), LSMTreeEngine::new);
    }

    @Test
    @DisplayName("B_TREE: no acknowledged write is lost to a racing checkpoint")
    void bTreeEngineLosesNothing(@TempDir Path dir) throws Exception {
        hammerThenCrash(dir,
                new BTreeEngine(dir, 1000, 0), // no background flusher: the test drives them
                d -> new BTreeEngine(d, 1000, 0));
    }
}

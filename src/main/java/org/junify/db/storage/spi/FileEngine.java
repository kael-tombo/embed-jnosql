package org.junify.db.storage.spi;

import org.junify.db.core.util.JsonSerde;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class FileEngine implements StorageEngine {

    private final Path dataDir;
    private final ConcurrentMap<String, ConcurrentMap<String, String>> store;
    private final ExecutorService writer;
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduler;
    private final boolean asyncEnabled;
    private final WriteAheadLog wal;
    /**
     * R-70: held across (store update + log record) by the write path and across
     * (snapshot + WAL checkpoint) by {@link #flush()}, so a checkpoint can never truncate a
     * record whose value the snapshot does not yet contain.
     */
    private final Object writeBarrier = new Object();

    public FileEngine(Path dataDir) {
        this(dataDir, 1000, true);
    }

    public FileEngine(Path dataDir, long flushIntervalMs) {
        this(dataDir, flushIntervalMs, true);
    }

    public FileEngine(Path dataDir, long flushIntervalMs, boolean asyncEnabled) {
        this.dataDir = dataDir;
        this.store = new ConcurrentHashMap<>();
        this.asyncEnabled = asyncEnabled;
        
        try {
            this.wal = new WriteAheadLog(dataDir);
        } catch (IOException e) {
            throw new org.junify.db.core.exception.StorageException("Failed to initialize WAL", e);
        }
        
        if (asyncEnabled) {
            this.writer = Executors.newSingleThreadExecutor();
            this.scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(this::asyncFlush, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
        } else {
            this.writer = null;
            this.scheduler = null;
        }
        
        try {
            Files.createDirectories(dataDir);
            loadAll();
            replayWal();
            recordPersistedCollections();
        } catch (IOException e) {
            throw new org.junify.db.core.exception.StorageException("Failed to initialize file engine", e);
        }
    }

    @Override
    public String name() {
        return "FILE";
    }

    @Override
    public void put(String collection, String key, String value) {
        // R-70: the log record and the store update share one critical section with the flush's
        // (snapshot + checkpoint). Logged outside it, a write could be truncating away by a
        // checkpoint that ran between the log and the apply, and then be lost on an unclean stop.
        synchronized (writeBarrier) {
            store.computeIfAbsent(collection, k -> new ConcurrentHashMap<>()).put(key, value);
            wal.log("PUT", collection, key, value);
        }

        if (asyncEnabled) {
            dirty.set(true);
        }
    }

    @Override
    public void putAll(String collection, Map<String, String> entries) {
        synchronized (writeBarrier) {
            var col = store.computeIfAbsent(collection, k -> new ConcurrentHashMap<>());
            col.putAll(entries);

            for (var entry : entries.entrySet()) {
                wal.log("PUT", collection, entry.getKey(), entry.getValue());
            }
        }

        if (asyncEnabled) {
            dirty.set(true);
        }
    }

    @Override
    public String get(String collection, String key) {
        var col = store.get(collection);
        return col != null ? col.get(key) : null;
    }

    @Override
    public List<String> getAll(String collection, List<String> keys) {
        var col = store.get(collection);
        if (col == null) return List.of();
        
        var results = new ArrayList<String>();
        for (var key : keys) {
            results.add(col.get(key));
        }
        return results;
    }

    @Override
    public void delete(String collection, String key) {
        synchronized (writeBarrier) {
            var col = store.get(collection);
            if (col != null) {
                col.remove(key);
                wal.log("DELETE", collection, key, null);
            }
        }

        if (asyncEnabled) {
            dirty.set(true);
        }
    }

    @Override
    public void deleteAll(String collection, List<String> keys) {
        synchronized (writeBarrier) {
            var col = store.get(collection);
            if (col != null) {
                for (var key : keys) {
                    col.remove(key);
                    wal.log("DELETE", collection, key, null);
                }
            }
        }

        if (asyncEnabled) {
            dirty.set(true);
        }
    }

    @Override
    public boolean exists(String collection, String key) {
        var col = store.get(collection);
        return col != null && col.containsKey(key);
    }

    @Override
    public List<String> scan(String collection) {
        var col = store.get(collection);
        return col != null ? List.copyOf(col.values()) : List.of();
    }

    @Override
    public List<String> scan(String collection, Predicate<String> filter) {
        return scan(collection).stream().filter(filter).collect(Collectors.toList());
    }

    @Override
    public Set<String> keys(String collection) {
        var col = store.get(collection);
        return col != null ? Set.copyOf(col.keySet()) : Set.of();
    }

    /**
     * R-62: an empty collection gets its own (empty) snapshot file, so
     * {@code CREATE TABLE t (id INT)} survives a restart instead of vanishing.
     *
     * <p>The engine writes one {@code <collection>.json} per collection in {@code store},
     * and {@code loadAll()} rediscovers collections from those files — so registering the
     * collection here is what makes an empty table durable. {@code dirty} is set in async
     * mode because the periodic flusher returns early when nothing is dirty, which is
     * exactly how an empty collection would otherwise be skipped.</p>
     */
    @Override
    public boolean ensureCollection(String collection) {
        if (collection == null || collection.isBlank()) return false;
        store.computeIfAbsent(collection, k -> new ConcurrentHashMap<>());
        if (asyncEnabled) {
            dirty.set(true);
        }
        return true;
    }

    @Override
    public void flush() {
        // R-70: the snapshot and the checkpoint that releases the log happen inside the same
        // barrier the write path holds across (apply + log). A checkpoint taken while a write was
        // between its log record and its apply would truncate a record the snapshot does not
        // contain — the write is acknowledged, absent from disk, and gone from the log.
        synchronized (writeBarrier) {
            if (asyncEnabled) {
                asyncFlush();
                sync();
            } else {
                syncFlush();
            }

            try {
                wal.checkpoint();
            } catch (IOException e) {
                System.err.println("WAL checkpoint failed: " + e.getMessage());
            }
        }
    }

    public void sync() {
        if (!asyncEnabled) return;
        var latch = new java.util.concurrent.CountDownLatch(1);
        writer.execute(() -> latch.countDown());
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private synchronized void asyncFlush() {
        recordPersistedCollections();
        if (!dirty.getAndSet(false)) return;
        
        writer.execute(() -> {
            for (var entry : store.entrySet()) {
                writeSnapshotAtomically(entry.getKey(), entry.getValue());
            }
        });
    }

    private synchronized void syncFlush() {
        recordPersistedCollections();
        for (var entry : store.entrySet()) {
            writeSnapshotAtomically(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Writes a collection snapshot via {@code tmp file + atomic move}, so a crash
     * mid-flush can never leave a torn {@code <collection>.json} behind: readers
     * either see the previous complete snapshot or the new one.
     */
    private void writeSnapshotAtomically(String collection, ConcurrentMap<String, String> data) {
        var target = dataDir.resolve(collection + ".json");
        var tmp = dataDir.resolve(collection + ".json.tmp");
        try {
            Files.writeString(tmp, JsonSerde.toJson(data));
            try {
                Files.move(tmp, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(tmp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new org.junify.db.core.exception.StorageException("Failed to flush: " + collection, e);
        }
    }

    @Override
    public void close() {
        flush();

        if (asyncEnabled) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            writer.shutdown();
            try {
                writer.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        try {
            wal.close();
        } catch (IOException e) {
            System.err.println("WAL close failed: " + e.getMessage());
        }
        
        store.clear();
    }

    @Override
    public int size() {
        return store.values().stream().mapToInt(ConcurrentMap::size).sum();
    }

    @Override
    public Map<String, Object> stats() {
        return Map.of(
            "engine", name(),
            "collections", store.size(),
            "totalEntries", size(),
            "dataDir", dataDir.toString(),
            "asyncEnabled", asyncEnabled,
            "type", "file-persistent"
        );
    }

    @SuppressWarnings("unchecked")
    private void loadAll() throws IOException {
        try (var stream = Files.list(dataDir)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                    .forEach(file -> {
                        try {
                            var content = Files.readString(file);
                            var map = JsonSerde.fromJson(content, ConcurrentHashMap.class);
                            var name = file.getFileName().toString().replace(".json", "");
                            store.put(name, new ConcurrentHashMap<>(map));
                        } catch (Exception e) {
                            // A single corrupt snapshot must not prevent startup:
                            // quarantine it and continue. WAL replay (below) may still
                            // recover the newest writes for that collection.
                            quarantine(file, e);
                        }
                    });
        }
    }

    /**
     * Moves an unreadable snapshot file into {@code .quarantine/} inside the data
     * directory and logs the reason, so one corrupt file no longer blocks the
     * whole database from opening (audit finding 18-F-02 / R-21).
     */
    private void quarantine(Path file, Exception cause) {
        try {
            var quarantineDir = dataDir.resolve(".quarantine");
            Files.createDirectories(quarantineDir);
            var stamp = System.currentTimeMillis();
            var target = quarantineDir.resolve(file.getFileName() + "." + stamp + ".corrupt");
            Files.move(file, target);
            System.err.println("FileEngine: quarantined corrupt snapshot " + file.getFileName()
                    + " -> " + quarantineDir.resolve(file.getFileName() + "." + stamp + ".corrupt")
                    + " (" + cause.getMessage() + ")");
        } catch (IOException moveFailure) {
            System.err.println("FileEngine: failed to quarantine " + file + ": " + moveFailure.getMessage());
        }
    }

    /**
     * Collections discovered on disk during startup. The File engine persists one
     * JSON file per collection, so these names are authoritative for data that
     * exists on disk.
     */
    private java.util.Set<String> persistedCollections = java.util.Collections.emptySet();

    @Override
    public java.util.Set<String> collectionNames() {
        return java.util.Collections.unmodifiableSet(persistedCollections);
    }

    /**
     * Live collection set (includes collections written since the last flush), so a
     * backup taken between flushes still captures every collection rather than only the
     * ones already snapshotted to disk.
     */
    @Override
    public java.util.Set<String> collections() {
        return java.util.Set.copyOf(store.keySet());
    }

    /**
     * Records the set of collections found on disk so the facade can re-expose
     * previously persisted collections after a restart.
     */
    private void recordPersistedCollections() {
        this.persistedCollections = java.util.Set.copyOf(store.keySet());
    }

    /**
     * Replays the write-ahead log on startup.
     *
     * <p>The WAL records every {@code put}/{@code delete} at write time, but the
     * JSON snapshot files are written asynchronously (or only on explicit
     * {@link #flush()}). Entries newer than the last {@code CHECKPOINT} marker
     * were never persisted to the JSON files, so without replay they are lost on
     * an unclean shutdown — the engine would silently violate its durability
     * contract. This method applies those entries to the in-memory store and
     * flushes them out.
     */
    private void replayWal() {
        try {
            // R-66: read every segment, not just wal.log. Rotation moves older records into
            // .wal/archive/*.log.gz (or a rotated .wal/wal-<ts>.log), and reading only the live
            // log made those records unrecoverable — the pre-fix measurement recovered 0 of 40.
            List<String> lines = WriteAheadLog.readRecoverableLines(dataDir);
            if (lines.isEmpty()) return;

            // Entries up to the last CHECKPOINT marker are already reflected in
            // the JSON snapshot files; only newer entries need replay.
            long lastCheckpointSeq = -1;
            for (String line : lines) {
                if (line.startsWith("CHECKPOINT:")) {
                    try {
                        lastCheckpointSeq = Long.parseLong(line.substring(11));
                    } catch (NumberFormatException ignored) {
                        // tolerate a torn checkpoint line
                    }
                }
            }

            int recovered = 0;
            for (String line : lines) {
                // LogEntry format: sequence|timestamp|TYPE|collection|key|value
                String[] parts = line.split("\\|", 6);
                if (parts.length < 5) continue;

                String type = parts[2];
                if (!"PUT".equals(type) && !"DELETE".equals(type)) continue;

                long seq;
                try {
                    seq = Long.parseLong(parts[0]);
                } catch (NumberFormatException ignored) {
                    continue; // tolerate a torn/corrupt line
                }
                if (lastCheckpointSeq >= 0 && seq <= lastCheckpointSeq) continue;

                String collection = parts[3];
                String key = parts[4];
                String value = parts.length > 5 ? parts[5] : null;

                var col = store.computeIfAbsent(collection, k -> new ConcurrentHashMap<>());
                if ("PUT".equals(type)) {
                    col.put(key, value);
                } else {
                    col.remove(key);
                }
                recovered++;
            }

            if (recovered > 0) {
                recordPersistedCollections();
                if (asyncEnabled) {
                    dirty.set(true);
                } else {
                    syncFlush();
                }
                System.out.println("FileEngine: recovered " + recovered + " operations from WAL");
            }
        } catch (IOException e) {
            System.err.println("FileEngine WAL recovery failed: " + e.getMessage());
        }
    }
}

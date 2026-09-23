package org.junify.db.storage.spi;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class BTreeEngine implements StorageEngine {

    private final Path dataDir;
    private final Path indexDir;
    private final int maxNodeSize;
    private final ConcurrentHashMap<String, String> ramIndex;
    private final ReentrantReadWriteLock indexLock;
    private final Set<String> dirtyKeys;
    /** R-62: persisted identity for collections that hold no records yet. */
    private final CollectionRegistry collectionRegistry;
    private final boolean asyncEnabled;
    /**
     * R-68: the periodic flusher. Before this existed, {@code B_TREE} wrote its index
     * <b>only</b> when something called {@code flush()} — and in the server path nothing
     * did except a graceful close, so a terminated server lost every record written since
     * the last explicit flush. Measured by the contract gate's restart block: a document
     * readable before the restart was gone after it.
     */
    private final ScheduledExecutorService scheduler;
    private final long flushIntervalMs;
    /**
     * R-69: the write-ahead log, the same one FILE and LSM_TREE use.
     *
     * <p>Without it this engine acknowledged writes it had not persisted: {@code put()} only
     * touched {@code ramIndex}, and durability came from the periodic flush, so an unclean stop
     * lost everything since the last one. Measured by the contract gate: 12 documents accepted
     * with HTTP 2xx, a forced stop, and <b>3</b> readable afterwards.</p>
     */
    private final WriteAheadLog wal;
    private volatile boolean closed;

    /** Default interval, matching the FILE engine's scheduler. */
    private static final long DEFAULT_FLUSH_INTERVAL_MS = 1000;

    public BTreeEngine(Path dataDir) {
        this(dataDir, 1000, DEFAULT_FLUSH_INTERVAL_MS);
    }

    public BTreeEngine(Path dataDir, int maxNodeSize) {
        this(dataDir, maxNodeSize, DEFAULT_FLUSH_INTERVAL_MS);
    }

    /**
     * @param flushIntervalMs interval for the background flusher, or {@code <= 0} to write
     *                        the index only on an explicit {@link #flush()} (tests) or on
     *                        {@link #close()}
     */
    public BTreeEngine(Path dataDir, int maxNodeSize, long flushIntervalMs) {
        this.dataDir = dataDir;
        this.indexDir = dataDir.resolve(".btree");
        this.maxNodeSize = maxNodeSize;
        this.ramIndex = new ConcurrentHashMap<>();
        this.indexLock = new ReentrantReadWriteLock();
        this.dirtyKeys = ConcurrentHashMap.newKeySet();
        this.collectionRegistry = new CollectionRegistry(dataDir);
        this.asyncEnabled = true;
        this.flushIntervalMs = flushIntervalMs;
        this.closed = false;

        try {
            Files.createDirectories(indexDir);
            loadIndex();
            // After the index, not before: the log holds only what the index does not (records
            // written since the last checkpoint), and it must win where they overlap.
            this.wal = new WriteAheadLog(dataDir);
            recoverFromWal();
        } catch (IOException e) {
            throw new org.junify.db.core.exception.StorageException("Failed to initialize B-Tree engine", e);
        }

        if (flushIntervalMs > 0) {
            this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                var thread = new Thread(r, "btree-flusher");
                thread.setDaemon(true);
                return thread;
            });
            scheduler.scheduleAtFixedRate(this::flushQuietly, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
        } else {
            this.scheduler = null;
        }
    }

    /** Never lets a flush failure escape onto the scheduler thread. */
    private void flushQuietly() {
        try {
            flush();
        } catch (Exception e) {
            System.err.println("Periodic B-Tree flush failed: " + e.getMessage());
        }
    }

    @Override
    public String name() {
        return "B_TREE";
    }

    @Override
    public void put(String collection, String key, String value) {
        checkOpen();
        String compositeKey = compositeKey(collection, key);

        // R-69/R-70: write-ahead, then apply, both inside the same critical section the flush
        // uses for (snapshot + checkpoint). Logging outside it let a checkpoint truncate a record
        // whose value had not been applied yet: acknowledged, absent from the index, gone from the
        // log. The contract gate measured 3 of 12 documents lost that way.
        indexLock.writeLock().lock();
        try {
            wal.log("PUT", collection, key, value);
            ramIndex.put(compositeKey, value);
            dirtyKeys.add(compositeKey);
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    @Override
    public void putAll(String collection, Map<String, String> entries) {
        checkOpen();
        indexLock.writeLock().lock();
        try {
            for (var entry : entries.entrySet()) {
                wal.log("PUT", collection, entry.getKey(), entry.getValue());
            }
            for (var entry : entries.entrySet()) {
                String compositeKey = compositeKey(collection, entry.getKey());
                ramIndex.put(compositeKey, entry.getValue());
                dirtyKeys.add(compositeKey);
            }
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    @Override
    public String get(String collection, String key) {
        checkOpen();
        String compositeKey = compositeKey(collection, key);
        
        indexLock.readLock().lock();
        try {
            return ramIndex.get(compositeKey);
        } finally {
            indexLock.readLock().unlock();
        }
    }

    @Override
    public List<String> getAll(String collection, List<String> keys) {
        return keys.stream().map(k -> get(collection, k)).collect(Collectors.toList());
    }

    @Override
    public void delete(String collection, String key) {
        checkOpen();
        String compositeKey = compositeKey(collection, key);

        indexLock.writeLock().lock();
        try {
            wal.log("DELETE", collection, key, null);
            ramIndex.remove(compositeKey);
            dirtyKeys.add(compositeKey);
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    @Override
    public void deleteAll(String collection, List<String> keys) {
        for (String key : keys) {
            delete(collection, key);
        }
    }

    @Override
    public boolean exists(String collection, String key) {
        return get(collection, key) != null;
    }

    @Override
    public List<String> scan(String collection) {
        checkOpen();
        String prefix = collection + ":";
        List<String> results = new ArrayList<>();
        
        indexLock.readLock().lock();
        try {
            for (var entry : ramIndex.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    results.add(entry.getValue());
                }
            }
        } finally {
            indexLock.readLock().unlock();
        }
        
        return results;
    }

    @Override
    public List<String> scan(String collection, Predicate<String> filter) {
        return scan(collection).stream().filter(filter).collect(Collectors.toList());
    }

    @Override
    public Set<String> keys(String collection) {
        checkOpen();
        String prefix = collection + ":";
        Set<String> keys = new HashSet<>();
        
        indexLock.readLock().lock();
        try {
            for (var entry : ramIndex.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    keys.add(extractKey(entry.getKey()));
                }
            }
        } finally {
            indexLock.readLock().unlock();
        }
        
        return keys;
    }

    @Override
    public void flush() {
        // R-70: persist and checkpoint under the same lock the write path uses for
        // (log + apply). A checkpoint outside it can truncate a record whose value has not been
        // applied yet, which loses an acknowledged write.
        indexLock.writeLock().lock();
        try {
            boolean persisted = dirtyKeys.isEmpty();
            if (!dirtyKeys.isEmpty()) {
                try {
                    persistIndex();
                    persisted = true;
                } catch (IOException e) {
                    System.err.println("Failed to persist B-Tree index: " + e.getMessage());
                }
                dirtyKeys.clear();
            }

            // Only release the log once the index really holds what the log holds: a checkpoint
            // truncates, and a failed persist must not discard the only copy.
            if (persisted) {
                try {
                    wal.checkpoint();
                } catch (IOException e) {
                    System.err.println("WAL checkpoint failed: " + e.getMessage());
                }
            }
        } finally {
            indexLock.writeLock().unlock();
        }
    }

    @Override
    public void close() {
        if (closed) return;
        flush();
        closed = true;
        try {
            wal.close();
        } catch (IOException e) {
            System.err.println("WAL close failed: " + e.getMessage());
        }
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                scheduler.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public List<String> rangeScan(String collection, String startKey, String endKey) {
        checkOpen();
        String start = collection + ":" + startKey;
        String end = collection + ":" + endKey;
        List<String> results = new ArrayList<>();
        
        indexLock.readLock().lock();
        try {
            var sortedKeys = ramIndex.keySet().stream()
                    .filter(k -> k.compareTo(start) >= 0 && k.compareTo(end) <= 0)
                    .sorted()
                    .toList();
            for (var k : sortedKeys) {
                results.add(ramIndex.get(k));
            }
        } finally {
            indexLock.readLock().unlock();
        }
        
        return results;
    }

    public List<String> prefixScan(String collection, String prefix) {
        checkOpen();
        String compositePrefix = collection + ":" + prefix;
        List<String> results = new ArrayList<>();
        
        indexLock.readLock().lock();
        try {
            for (var entry : ramIndex.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(compositePrefix) || key.substring(key.indexOf(':') + 1).startsWith(prefix)) {
                    results.add(entry.getValue());
                }
            }
        } finally {
            indexLock.readLock().unlock();
        }
        
        return results;
    }

    /**
     * Writes the whole index through a temporary file and one atomic move.
     *
     * <p><b>R-71:</b> this used to truncate {@code btree_index.dat} and rewrite it in place. A
     * process killed during that window left a half-written index on disk, while the WAL —
     * truncated at the previous checkpoint — only covered writes made since then. Everything
     * older lived solely in the file being rewritten, so it was gone. Measured live through the
     * contract gate: 12 documents accepted, a forced stop, and 5 readable afterwards.</p>
     *
     * <p>On success the previous complete index is still on disk until the move succeeds, so a
     * reader — including recovery after a crash — sees either the old index or the new one, never
     * a partial one.</p>
     */
    private void persistIndex() throws IOException {
        Path indexFile = indexDir.resolve("btree_index.dat");
        Path tmp = indexDir.resolve("btree_index.dat.tmp");

        var sortedEntries = ramIndex.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();

        try (var channel = FileChannel.open(tmp,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {

            for (var entry : sortedEntries) {
                byte[] keyBytes = entry.getKey().getBytes(StandardCharsets.UTF_8);
                byte[] valueBytes = entry.getValue().getBytes(StandardCharsets.UTF_8);

                // Sized per entry, not a fixed 1 MB buffer: a document larger than the buffer
                // made put() throw BufferOverflowException out of a flush that had already
                // truncated the file, and the index writer would then be dead.
                ByteBuffer buffer = ByteBuffer.allocate(8 + keyBytes.length + valueBytes.length);
                buffer.putInt(keyBytes.length);
                buffer.put(keyBytes);
                buffer.putInt(valueBytes.length);
                buffer.put(valueBytes);
                buffer.flip();
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
            }
            // The rename is what publishes the index, so the bytes must be on disk before it:
            // otherwise a crash can publish a name whose contents did not survive.
            channel.force(true);
        }

        try {
            Files.move(tmp, indexFile,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(tmp, indexFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * R-69: applies the records the log holds on top of the index loaded from disk, oldest first,
     * so the newest version of each key wins.
     */
    private void recoverFromWal() throws IOException {
        var lines = WriteAheadLog.readRecoverableLines(dataDir);
        if (lines.isEmpty()) return;

        int recovered = 0;
        for (String line : lines) {
            var entry = WriteAheadLog.LogEntry.fromString(line);
            if (entry == null) continue;
            String compositeKey = compositeKey(entry.collection(), entry.key());
            if ("PUT".equals(entry.type())) {
                ramIndex.put(compositeKey, entry.value());
                dirtyKeys.add(compositeKey);
                recovered++;
            } else if ("DELETE".equals(entry.type())) {
                ramIndex.remove(compositeKey);
                dirtyKeys.add(compositeKey);
                recovered++;
            }
        }
        if (recovered > 0) {
            System.out.println("BTreeEngine: recovered " + recovered + " operations from WAL");
        }
    }

    /**
     * Reads the index as a stream of {@code keyLen|key|valueLen|value} records.
     *
     * <p><b>R-72:</b> this used to fill a 1 MB {@link ByteBuffer} and parse records out of it.
     * A record straddling the 1 MB boundary left the tail of that record unread, the loop broke,
     * and the next buffer started in the middle of the record — the rest of the file was then
     * parsed from misaligned bytes and silently produced a handful of arbitrary entries. With
     * 256 KB documents that happened on the fifth record of every file: 12 documents written,
     * 3 readable after a restart, though the file on disk held all 12.</p>
     *
     * <p>A stream has no boundary to straddle. It also refuses to guess: a record that cannot be
     * read in full is reported as corruption rather than skipped, so a damaged index fails loudly
     * instead of answering reads with a plausible-looking subset.</p>
     */
    private void loadIndex() throws IOException {
        Path indexFile = indexDir.resolve("btree_index.dat");
        if (!Files.exists(indexFile)) return;

        long records = 0;
        try (var in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(indexFile), 1 << 16))) {
            while (true) {
                int keyLen;
                try {
                    keyLen = in.readInt();
                } catch (EOFException endOfIndex) {
                    break; // clean end of file
                }
                if (keyLen < 0) {
                    throw new IOException("B-Tree index is corrupt: negative key length " + keyLen
                            + " after " + records + " records (" + indexFile + ")");
                }
                byte[] keyBytes = in.readNBytes(keyLen);
                if (keyBytes.length < keyLen) {
                    throw new IOException("B-Tree index is truncated: key of " + keyLen
                            + " bytes after " + records + " records (" + indexFile + ")");
                }
                int valueLen = in.readInt();
                if (valueLen < 0) {
                    throw new IOException("B-Tree index is corrupt: negative value length " + valueLen
                            + " after " + records + " records (" + indexFile + ")");
                }
                byte[] valueBytes = in.readNBytes(valueLen);
                if (valueBytes.length < valueLen) {
                    throw new IOException("B-Tree index is truncated: value of " + valueLen
                            + " bytes after " + records + " records (" + indexFile + ")");
                }
                ramIndex.put(new String(keyBytes, StandardCharsets.UTF_8),
                        new String(valueBytes, StandardCharsets.UTF_8));
                records++;
            }
        }
    }

    private String compositeKey(String collection, String key) {
        return collection + ":" + key;
    }

    @Override
    public Set<String> collections() {
        checkOpen();
        Set<String> collections = new HashSet<>();
        indexLock.readLock().lock();
        try {
            for (String composite : ramIndex.keySet()) {
                int idx = composite.indexOf(':');
                collections.add(idx > 0 ? composite.substring(0, idx) : composite);
            }
        } finally {
            indexLock.readLock().unlock();
        }
        // R-62: a collection created with no records has no composite key to derive a name
        // from, so its existence is recorded separately and unioned in here.
        collections.addAll(collectionRegistry.names());
        return collections;
    }

    /**
     * R-62: records the collection's existence so an empty collection survives a restart.
     * B_TREE addresses data by {@code collection:key}, so a collection with no records is
     * otherwise invisible to {@link #collections()} after the process exits.
     */
    @Override
    public boolean ensureCollection(String collection) {
        if (collection == null || collection.isBlank()) return false;
        collectionRegistry.mark(collection);
        return true;
    }

    private String extractKey(String compositeKey) {
        int idx = compositeKey.indexOf(':');
        return idx > 0 ? compositeKey.substring(idx + 1) : compositeKey;
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("B-Tree engine is closed");
        }
    }

    @Override
    public int size() {
        return ramIndex.size();
    }

    @Override
    public Map<String, Object> stats() {
        return Map.of(
            "engine", name(),
            "totalEntries", ramIndex.size(),
            "indexDir", indexDir.toString(),
            "maxNodeSize", maxNodeSize,
            "dirtyKeys", dirtyKeys.size(),
            "flushIntervalMs", flushIntervalMs,
            "type", "btree-persistent"
        );
    }
}

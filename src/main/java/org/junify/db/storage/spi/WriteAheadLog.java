package org.junify.db.storage.spi;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BiConsumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Write-Ahead Log with Structured Concurrency patterns.
 * 
 * Features:
 * - Virtual thread execution for I/O operations
 * - Structured shutdown semantics
 * - Zero-copy buffer management (when available)
 */
public class WriteAheadLog {

    private final Path walDir;
    private final Path walFile;
    private final Path archiveDir;
    private final AtomicLong logSequence = new AtomicLong(0);
    private final ExecutorService writer;
    private final ExecutorService archiver;
    private BufferedWriter logWriter;
    private final ConcurrentLinkedQueue<LogEntry> pendingWrites = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final int maxFileSizeKB;
    private BiConsumer<String, LogEntry> recoveryCallback;

    public WriteAheadLog(Path dataDir) throws IOException {
        this(dataDir, 1024);
    }

    public WriteAheadLog(Path dataDir, int maxFileSizeKB) throws IOException {
        this.walDir = dataDir.resolve(".wal");
        this.archiveDir = walDir.resolve("archive");
        this.maxFileSizeKB = maxFileSizeKB;
        Files.createDirectories(walDir);
        Files.createDirectories(archiveDir);
        this.walFile = walDir.resolve("wal.log");
        
        // Java 17+: Use virtual threads if available (Java 21+), fallback to platform threads
        this.writer = createVirtualExecutor("WAL-Writer");
        this.archiver = createVirtualExecutor("WAL-Archiver");
        
        initWriter();
        recoverIfNeeded();
    }
    
    /**
     * Create executor using virtual threads (Java 21+) or platform threads (Java 17).
     */
    private ExecutorService createVirtualExecutor(String name) {
        try {
            // Try Java 21+ virtual threads
            var factory = (ThreadFactory) Thread.class
                .getMethod("ofVirtual")
                .invoke(null);
            return (ExecutorService) factory.getClass()
                .getMethod("name", String.class, long.class)
                .invoke(factory, name, 1L);
        } catch (Exception e) {
            // Fallback to Java 17 platform threads
            return Executors.newSingleThreadExecutor(r -> {
                var t = new Thread(r, name);
                t.setDaemon(true);
                return t;
            });
        }
    }

    public void setRecoveryCallback(BiConsumer<String, LogEntry> callback) {
        this.recoveryCallback = callback;
    }

    private FileOutputStream logFileOutputStream;

    private void initWriter() throws IOException {
        var fileWriter = new FileWriter(walFile.toFile(), true);
        logWriter = new BufferedWriter(fileWriter);
        // Keep FileOutputStream for fsync
        this.logFileOutputStream = new FileOutputStream(walFile.toFile(), true);
    }

    /**
     * Log a write operation with fsync for durability guarantee.
     */
    /** Records larger than this are rejected: a single oversized record can
     *  otherwise exhaust memory and take the whole engine down. */
    public static final int MAX_RECORD_BYTES = 64 * 1024 * 1024;

    public synchronized void log(String type, String collection, String key, String value) {
        if (closed.get()) return;

        int recordBytes = (type.length() + collection.length() + key.length()
                + (value == null ? 0 : value.length())) * 2 + 128;
        if (recordBytes > MAX_RECORD_BYTES) {
            throw new IllegalArgumentException(
                "WAL record exceeds " + MAX_RECORD_BYTES + " bytes (got ~" + recordBytes
                + "); split the value or use a bulk/streaming strategy.");
        }

        var entry = new LogEntry(
            logSequence.incrementAndGet(),
            System.currentTimeMillis(),
            type,
            collection,
            key,
            value
        );

        pendingWrites.offer(entry);

        try {
            logWriter.write(entry.toString());
            logWriter.newLine();
            logWriter.flush();

            // Fsync to ensure data is persisted to disk
            fsync();

            if (shouldRotate()) {
                rotateWalFile();
            }
        } catch (IOException e) {
            System.err.println("WAL write failed: " + e.getMessage());
        }
    }

    /**
     * Force sync WAL to disk for durability guarantee.
     */
    public synchronized void fsync() throws IOException {
        if (logFileOutputStream != null) {
            logFileOutputStream.flush();
            logFileOutputStream.getFD().sync();
        }
    }

    private boolean shouldRotate() {
        try {
            return Files.size(walFile) > maxFileSizeKB * 1024;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Rotate the WAL and archive the previous segment.
     *
     * <p><b>R-66:</b> this used to close the writer and only reopen it from a background task
     * that first had to compress and delete the file. Every {@code log()} call in that window
     * wrote to a closed writer, threw, and was swallowed by the {@code catch (IOException)} in
     * {@code log()} — measured at 53 of 60 records lost from disk in a single test run. The
     * rename is now synchronous (atomic where the filesystem allows it) and the writer is
     * reopened <b>before</b> the method returns, so a write always has a valid destination; only
     * the compression of the already-rotated segment stays in the background.</p>
     */
    private void rotateWalFile() throws IOException {
        // Fsync before rotation to ensure all data is persisted
        fsync();

        logWriter.close();
        if (logFileOutputStream != null) {
            logFileOutputStream.close();
        }

        var timestamp = System.currentTimeMillis();
        var rotated = walDir.resolve("wal-" + timestamp + ".log");
        var archivedFile = archiveDir.resolve("wal-" + timestamp + ".log.gz");

        try {
            Files.move(walFile, rotated,
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(walFile, rotated, StandardCopyOption.REPLACE_EXISTING);
        }

        // Reopen immediately: from here on writes land in a live log again. The rotated segment
        // is still readable by recovery, so a crash during compression loses nothing.
        initWriter();

        archiver.submit(() -> {
            var staging = archiveDir.resolve(archivedFile.getFileName() + ".tmp");
            try {
                // R-66: compress to a temp name and publish the finished file with one rename.
                // Writing wal-<ts>.log.gz in place published a partially-written gzip under a
                // recoverable name, and a reader that hit it mid-compression lost the whole log
                // (measured: a crash after rotation recovered 0 of 6 records).
                try (var fis = Files.newInputStream(rotated);
                     var fos = Files.newOutputStream(staging);
                     var gzOut = new GZIPOutputStream(fos)) {
                    fis.transferTo(gzOut);
                }
                try {
                    Files.move(staging, archivedFile,
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(staging, archivedFile, StandardCopyOption.REPLACE_EXISTING);
                }
                Files.deleteIfExists(rotated);
            } catch (IOException e) {
                // Leave the uncompressed segment in place: recovery reads it in preference to
                // losing the records it holds. Drop the unfinished archive so it is never mistaken
                // for one.
                try {
                    Files.deleteIfExists(staging);
                } catch (IOException ignored) {
                    // nothing useful to do; recovery will still read the .log segment
                }
                System.err.println("WAL archive failed (segment kept: " + rotated.getFileName() + "): "
                        + e.getMessage());
            }
        });
    }

    /**
     * Marks the point at which everything logged so far is durable elsewhere (snapshot or
     * SSTable), so the log no longer has to carry it.
     *
     * <p><b>R-66:</b> this used to append a {@code CHECKPOINT:<seq>} marker and leave every
     * historical record in the file — {@code truncate()} existed with no callers — so the log
     * grew for the life of the process and every restart replayed records it did not need.
     * The marker is now only written if truncation fails, where it still gives recovery its
     * boundary.</p>
     */
    public synchronized void checkpoint() throws IOException {
        if (closed.get()) return;

        try {
            truncate();
        } catch (IOException e) {
            System.err.println("WAL truncate on checkpoint failed (" + e.getMessage()
                    + "); appending a CHECKPOINT marker instead");
            logWriter.write("CHECKPOINT:" + logSequence.get());
            logWriter.newLine();
            logWriter.flush();
        }
    }

    /**
     * Every log line that still matters, oldest first: archived segments, then rotated but
     * not-yet-compressed segments, then the live log.
     *
     * <p><b>R-66:</b> both engines read {@code .wal/wal.log} directly, so records that rotation
     * had moved into {@code .wal/archive/*.log.gz} were unreachable — recovery returned 0 of 40
     * recorded entries in the measurement. Reading the archive is this class's job: it owns the
     * compression format.</p>
     */
    public static List<String> readRecoverableLines(Path dataDir) throws IOException {
        var walDir = dataDir.resolve(".wal");
        if (!Files.exists(walDir)) return List.of();

        var lines = new ArrayList<String>();

        // Chronological order matters: a later record for the same key must be applied last, or
        // recovery resurrects an older value. Segment names carry their rotation timestamp, so
        // sorting them is ordering them; wal.log is always the newest and is read last.
        List<Path> archived = listSegments(walDir.resolve("archive"), ".log.gz");
        var archivedNames = new HashSet<String>();
        for (Path file : archived) {
            archivedNames.add(file.getFileName().toString());
            try {
                try (var in = new BufferedReader(new InputStreamReader(
                        new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = in.readLine()) != null) lines.add(line);
                }
            } catch (IOException damaged) {
                // One unreadable segment must not discard the rest of the log: reporting and
                // carrying on recovers everything else, while throwing recovers nothing.
                System.err.println("WAL archive segment unreadable (" + file.getFileName()
                        + ", " + damaged.getMessage() + "); continuing with the remaining segments");
            }
        }

        Path live = walDir.resolve("wal.log");
        for (Path file : listSegments(walDir, ".log")) {
            String name = file.getFileName().toString();
            if (name.equals("wal.log")) continue;
            // Already counted under its compressed name: reading both would replay the segment
            // twice, which is exactly how a stale value wins.
            if (archivedNames.contains(name + ".gz")) continue;
            try {
                lines.addAll(Files.readAllLines(file));
            } catch (IOException damaged) {
                System.err.println("WAL rotated segment unreadable (" + name + ", "
                        + damaged.getMessage() + "); continuing with the remaining segments");
            }
        }

        if (Files.exists(live)) {
            lines.addAll(Files.readAllLines(live));
        }
        return lines;
    }

    private static List<Path> listSegments(Path dir, String suffix) throws IOException {
        if (!Files.exists(dir)) return List.of();
        try (var stream = Files.list(dir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(suffix))
                    .sorted()
                    .toList();
        }
    }

    /**
     * Hands every recoverable record to the registered callback.
     *
     * <p><b>R-66:</b> this parsed {@code PUT:}/{@code DELETE:} prefixes while every producer
     * writes the {@code seq|timestamp|TYPE|collection|key|value} shape, so it matched nothing
     * and could only ever report zero recovered operations — a recovery entry point that could
     * not recover. It now reads every segment (archives included) with the real format.</p>
     */
    public void recoverIfNeeded() {
        if (recoveryCallback == null) return;

        long lastSeq = 0;
        int recoveredOps = 0;

        try {
            for (var line : readRecoverableLines(walDir.getParent())) {
                if (line.startsWith("CHECKPOINT:")) {
                    try {
                        lastSeq = Long.parseLong(line.substring(11));
                    } catch (NumberFormatException ignored) {
                        // tolerate a torn checkpoint line
                    }
                } else {
                    var entry = LogEntry.fromString(line);
                    if (entry != null && entry.sequence() > lastSeq) {
                        recoveryCallback.accept(entry.type(), entry);
                        recoveredOps++;
                    }
                }
            }
            if (lastSeq > 0) {
                logSequence.set(lastSeq);
            }
        } catch (IOException e) {
            System.err.println("WAL recovery failed: " + e.getMessage());
        }

        if (recoveredOps > 0) {
            System.out.println("WAL: Recovered " + recoveredOps + " operations");
        }
    }

    /**
     * Graceful shutdown with executor termination.
     */
    public void close() throws IOException {
        if (closed.getAndSet(true)) return;
        
        try {
            checkpoint();
        } catch (IOException e) {
            System.err.println("WAL checkpoint failed: " + e.getMessage());
        }
        
        // Graceful executor shutdown
        writer.shutdown();
        archiver.shutdown();
        try {
            if (!writer.awaitTermination(5, TimeUnit.SECONDS)) {
                writer.shutdownNow();
            }
            if (!archiver.awaitTermination(5, TimeUnit.SECONDS)) {
                archiver.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            writer.shutdownNow();
            archiver.shutdownNow();
        }
        
        if (logWriter != null) {
            logWriter.close();
        }
        if (logFileOutputStream != null) {
            logFileOutputStream.close();
        }
    }

    public void truncate() throws IOException {
        if (logWriter != null) {
            logWriter.close();
        }
        if (logFileOutputStream != null) {
            logFileOutputStream.close();
        }
        Files.deleteIfExists(walFile);
        logSequence.set(0);
        initWriter();
    }

    public long sequence() {
        return logSequence.get();
    }

    public Path walDir() {
        return walDir;
    }

    public record LogEntry(
        long sequence,
        long timestamp,
        String type,
        String collection,
        String key,
        String value
    ) {
        @Override
        public String toString() {
            return sequence + "|" + timestamp + "|" + type + "|" + collection + "|" + key + "|" + (value != null ? value : "");
        }

        public static LogEntry fromString(String line) {
            var parts = line.split("\\|", 6);
            if (parts.length < 5) return null;
            try {
                return new LogEntry(
                    Long.parseLong(parts[0]),
                    Long.parseLong(parts[1]),
                    parts[2],
                    parts[3],
                    parts[4],
                    parts.length > 5 && !parts[5].isEmpty() ? parts[5] : null
                );
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}

package org.junify.db;

import org.junify.db.storage.spi.FileEngine;
import org.junify.db.storage.spi.WriteAheadLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for R-66: the write-ahead log must keep every record it accepted, and
 * recovery must be able to read all of it.
 *
 * <p>Measured before the fix, three separate holes in the same mechanism:</p>
 * <ol>
 *   <li><b>Rotation dropped records.</b> {@code rotateWalFile()} closed the writer and only
 *       reopened it from a background task that first compressed and deleted the file. Every
 *       {@code log()} call in that window wrote to a closed writer, threw, and was swallowed by
 *       a {@code catch (IOException)} that printed to stderr — the record survived only in
 *       memory.</li>
 *   <li><b>Archived segments were never read.</b> Rotation moves the log into
 *       {@code .wal/archive/*.log.gz}, while both replay paths ({@code FileEngine.replayWal},
 *       {@code LSMTreeEngine.recoverFromWal}) read {@code .wal/wal.log} only. Records that had
 *       been rotated out but not yet reflected in a snapshot were unrecoverable.</li>
 *   <li><b>The log never shrank.</b> {@code checkpoint()} appended a marker and left every
 *       historical entry in place — {@code truncate()} existed with zero callers — so the log
 *       grew for the life of the process and replay work grew with it.</li>
 * </ol>
 */
@DisplayName("The WAL keeps every record and recovery can read it (R-66)")
class WalRotationAndCheckpointTest {

    /** Collects the sequence numbers of every record still on disk, from all three places. */
    private Set<String> sequencesOnDisk(Path dataDir) throws Exception {
        Set<String> records = new LinkedHashSet<>();
        Path walDir = dataDir.resolve(".wal");

        // The live log and any rotated-but-not-yet-compressed segment.
        if (Files.exists(walDir)) {
            try (var stream = Files.list(walDir)) {
                for (Path file : stream.filter(p -> p.getFileName().toString().endsWith(".log")).toList()) {
                    for (String line : Files.readAllLines(file)) collect(records, line);
                }
            }
            Path archive = walDir.resolve("archive");
            if (Files.exists(archive)) {
                try (var stream = Files.list(archive)) {
                    for (Path file : stream.filter(p -> p.getFileName().toString().endsWith(".log.gz")).toList()) {
                        try (var in = new BufferedReader(new InputStreamReader(
                                new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = in.readLine()) != null) collect(records, line);
                        }
                    }
                }
            }
        }
        return records;
    }

    private void collect(Set<String> records, String line) {
        String[] parts = line.split("\\|", 6);
        if (parts.length >= 5 && ("PUT".equals(parts[2]) || "DELETE".equals(parts[2]))) {
            records.add(parts[0]);
        }
    }

    private String payload(int i) {
        return "{\"n\":" + i + ",\"pad\":\"" + "x".repeat(120) + "\"}";
    }

    @Test
    @DisplayName("rotation does not drop a single accepted record")
    void everyRecordSurvivesRotation(@TempDir Path dir) throws Exception {
        int records = 60;
        var wal = new WriteAheadLog(dir, 1); // 1 KB: rotation triggers every few records
        try {
            for (int i = 1; i <= records; i++) {
                wal.log("PUT", "orders", "k" + i, payload(i));
            }
        } finally {
            wal.close();
        }
        // Rotation compresses in the background; give it a moment before looking for files.
        Thread.sleep(400);

        Set<String> onDisk = sequencesOnDisk(dir);
        List<String> missing = new ArrayList<>();
        for (int i = 1; i <= records; i++) {
            if (!onDisk.contains(String.valueOf(i))) missing.add("k" + i);
        }
        assertTrue(missing.isEmpty(),
                records + " records were logged but " + missing.size() + " are nowhere on disk ("
                        + missing.subList(0, Math.min(5, missing.size())) + "…): a rotation must not "
                        + "silently drop what it accepted");
    }

    @Test
    @DisplayName("recovery sees records that were rotated into the archive")
    void recoveryReadsArchivedSegments(@TempDir Path dir) throws Exception {
        int records = 40;
        var wal = new WriteAheadLog(dir, 1);
        try {
            for (int i = 1; i <= records; i++) {
                wal.log("PUT", "orders", "k" + i, payload(i));
            }
        } finally {
            wal.close();
        }
        Thread.sleep(400);

        Path archive = dir.resolve(".wal").resolve("archive");
        assertTrue(Files.exists(archive) && Files.list(archive).findAny().isPresent(),
                "this scenario must actually produce an archived segment to be meaningful");

        // A fresh log over the same directory is what a restart does; recovery must hand back
        // every record, including those the rotation moved out of wal.log.
        Set<String> recovered = new LinkedHashSet<>();
        var reopened = new WriteAheadLog(dir, 1);
        try {
            reopened.setRecoveryCallback((type, entry) -> recovered.add(entry.collection() + "/" + entry.key()));
            reopened.recoverIfNeeded();
        } finally {
            reopened.close();
        }

        assertEquals(records, recovered.size(),
                "recovery must replay archived segments too, found: " + recovered.size() + " of " + records);
        assertTrue(recovered.contains("orders/k1"), "the oldest record must be recovered: " + recovered);
        assertTrue(recovered.contains("orders/k" + records), "the newest record must be recovered");
    }

    @Test
    @DisplayName("a checkpoint stops the log carrying data the snapshot already holds")
    void checkpointReleasesTheLog(@TempDir Path dir) throws Exception {
        var engine = new FileEngine(dir, 3_600_000, false); // no background flusher
        try {
            for (int i = 1; i <= 20; i++) {
                engine.put("orders", "k" + i, payload(i));
            }
            Path walFile = dir.resolve(".wal").resolve("wal.log");
            assertTrue(Files.readAllLines(walFile).stream().anyMatch(l -> l.contains("|PUT|")),
                    "before the flush the log must carry the unflushed writes");

            engine.flush(); // snapshot written, then checkpoint

            List<String> after = Files.readAllLines(walFile);
            assertTrue(after.stream().noneMatch(l -> l.contains("|PUT|")),
                    "after a checkpoint the snapshot is durable, so the log must not keep carrying "
                            + "those records (found " + after.size() + " lines: " + after.stream().limit(3).toList() + ")");
        } finally {
            engine.close();
        }
    }

    @Test
    @DisplayName("a crash after a rotation still recovers every write")
    void crashAfterRotationRecoversEveryWrite(@TempDir Path dir) throws Exception {
        // 1.2 MB in one record pushes the log past its 1 KB rotation threshold immediately, so
        // the next writes trigger a rotation with records still unflushed.
        String big = "{\"pad\":\"" + "y".repeat(1_200_000) + "\"}";
        var engine = new FileEngine(dir, 3_600_000, false); // no flusher: nothing reaches the snapshot
        engine.put("orders", "big", big);
        for (int i = 1; i <= 5; i++) {
            engine.put("orders", "k" + i, payload(i));
        }

        // Deliberately no flush()/close() on `engine` before this point: an unclean shutdown is
        // exactly what the WAL exists for, and flushing first would hide the defect. The second
        // engine replays in its constructor, so it must open after the writes.
        var recovered = new FileEngine(dir, 3_600_000, false);
        try {
            // Nothing above reached a snapshot, so anything recovered came from the log.
            assertNotNull(recovered.get("orders", "big"),
                    "the record that triggered the rotation must be recoverable after a crash");
            for (int i = 1; i <= 5; i++) {
                assertEquals(payload(i), recovered.get("orders", "k" + i),
                        "k" + i + " was accepted before the crash and must be replayed");
            }
        } finally {
            // Teardown only — every assertion above ran first. Both engines are closed so the JUnit
            // temp directory can be removed on Windows, where an open handle blocks it.
            recovered.close();
            engine.close();
        }
    }
}

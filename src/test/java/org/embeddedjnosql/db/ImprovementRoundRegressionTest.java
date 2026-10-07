package org.embeddedjnosql.db;

import org.embeddedjnosql.db.core.cdc.CDCEvent;
import org.embeddedjnosql.db.nosql.document.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for the post-audit improvement round: CDC write-path wiring
 * (audit R-15 / 23-OB-01) and FileEngine corrupt-snapshot quarantine
 * (audit R-21 / 18-F-02).
 */
class ImprovementRoundRegressionTest {

    // ------------------------------------------------------------------
    // R-15: CDC now has a real producer (document write path -> CDC feed)
    // ------------------------------------------------------------------

    @Test
    void cdcRecordsDocumentInsertsUpdatesAndDeletes() {
        try (var db = EmbedJNoSQL.inMemory()) {
            var col = db.documentCollection("cdc_test");

            col.insert(Document.of("name", "alice").id("u1"));
            col.insert(Document.of("name", "bob").id("u2"));
            col.update(Document.of("name", "alice-2").id("u1"));
            col.deleteById("u2");

            var events = db.cdcManager().processor().getEventLog();
            assertTrue(events.stream().anyMatch(e -> e.eventType() == org.embeddedjnosql.db.core.cdc.CDCEvent.EventType.INSERT
                    && "cdc_test".equals(e.collection()) && "u1".equals(e.key())),
                    "insert event for u1 must be recorded");
            assertTrue(events.stream().anyMatch(e -> e.eventType() == org.embeddedjnosql.db.core.cdc.CDCEvent.EventType.INSERT
                    && "u2".equals(e.key())), "insert event for u2 must be recorded");
            assertTrue(events.stream().anyMatch(e -> e.eventType() == org.embeddedjnosql.db.core.cdc.CDCEvent.EventType.UPDATE
                    && "u1".equals(e.key())), "update event for u1 must be recorded");
            assertTrue(events.stream().anyMatch(e -> e.eventType() == org.embeddedjnosql.db.core.cdc.CDCEvent.EventType.DELETE
                    && "u2".equals(e.key())), "delete event for u2 must be recorded");
        }
    }

    @Test
    void cdcEventPayloadsCarryDocumentJson() {
        try (var db = EmbedJNoSQL.inMemory()) {
            var col = db.documentCollection("cdc_payload");
            col.insert(Document.of("city", "Toamasina").id("c1"));

            var events = db.cdcManager().processor().getEventLog();
            var insertEvent = events.stream()
                    .filter(e -> "cdc_payload".equals(e.collection()) && "c1".equals(e.key()))
                    .findFirst().orElseThrow();
            assertNotNull(insertEvent.newValue(), "insert event must carry the document payload");
            assertTrue(insertEvent.newValue().contains("Toamasina"));
        }
    }

    // ------------------------------------------------------------------
    // R-21: corrupt snapshot quarantined instead of blocking startup
    // ------------------------------------------------------------------

    @Test
    void corruptSnapshotIsQuarantinedAndStartupContinues(@TempDir Path dir) throws Exception {
        // One good collection and one corrupt one.
        Files.writeString(dir.resolve("good.json"), "{\"k\":\"v\"}", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("broken.json"), "{ this is not json ]]", StandardCharsets.UTF_8);

        var engine = new org.embeddedjnosql.db.storage.spi.FileEngine(dir, 3_600_000, true);
        try {
            // The good collection loads and the engine is usable.
            assertEquals("v", engine.get("good", "k"));
            engine.put("good", "k2", "v2");
        } finally {
            engine.close();
        }

        // The corrupt file was moved aside.
        try (var files = Files.list(dir.resolve(".quarantine"))) {
            List<Path> quarantined = files.filter(p -> p.getFileName().toString().startsWith("broken.json")).toList();
            assertEquals(1, quarantined.size(), "corrupt snapshot must be quarantined");
        }
        assertFalse(Files.exists(dir.resolve("broken.json")), "corrupt file must no longer block the data dir");
    }

    // ------------------------------------------------------------------
    // R-19b: oversized WAL records are rejected up front
    // ------------------------------------------------------------------

    @Test
    void walRejectsOversizedRecords(@TempDir Path dir) throws Exception {
        var wal = new org.embeddedjnosql.db.storage.spi.WriteAheadLog(dir);
        try {
            var huge = "x".repeat(org.embeddedjnosql.db.storage.spi.WriteAheadLog.MAX_RECORD_BYTES + 1);
            assertThrows(IllegalArgumentException.class,
                    () -> wal.log("PUT", "c", "k", huge),
                    "records beyond MAX_RECORD_BYTES must be rejected");
        } finally {
            wal.close();
        }
    }

    // ------------------------------------------------------------------
    // R-21b: snapshot writes are atomic (tmp + move), no torn files
    // ------------------------------------------------------------------

    @Test
    void snapshotWritesLeaveNoTmpFilesBehind(@TempDir Path dir) throws Exception {
        var engine = new org.embeddedjnosql.db.storage.spi.FileEngine(dir, 3_600_000, true);
        try {
            engine.put("atomic", "k1", "v1");
            engine.flush();
            assertTrue(Files.exists(dir.resolve("atomic.json")), "snapshot must exist after flush");

            // Repeated flushes keep replacing the snapshot atomically.
            engine.put("atomic", "k2", "v2");
            engine.flush();

            try (var files = Files.list(dir)) {
                long tmpFiles = files.filter(p -> p.getFileName().toString().endsWith(".tmp")).count();
                assertEquals(0, tmpFiles, "no .tmp files may survive a completed flush");
            }
            assertEquals("v1", engine.get("atomic", "k1"));
        } finally {
            engine.close();
        }
    }
}

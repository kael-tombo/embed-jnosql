package org.junify.db;

import org.junify.db.storage.spi.BTreeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for R-72: the B_TREE index must be read back exactly as written, whatever
 * the record sizes and however many records there are.
 *
 * <p>Measured before the fix: twelve 256 KB documents were written and served, the process was
 * killed, and only <b>3</b> were readable after the restart — while the index file on disk still
 * held all twelve. The reader filled a 1 MB buffer and parsed records out of it, breaking when a
 * record straddled the boundary; the next buffer then started mid-record, so the remainder of the
 * file was parsed from misaligned bytes. Records were dropped and arbitrary keys were invented.</p>
 *
 * <p>These tests use records that straddle 1 MB deliberately, since that is the case a fixed-size
 * read buffer gets wrong, plus one entry larger than that buffer, which the writer used to reject
 * with {@code BufferOverflowException} from inside a flush.</p>
 */
@DisplayName("the B_TREE index round-trips every record (R-72)")
class BTreeIndexRoundTripTest {

    /** ~256 KB, so the index crosses 1 MB after four records and records straddle the boundary. */
    private String document(int i) {
        return "{\"id\":\"doc-" + i + "\",\"pad\":\"" + "x".repeat(262_144) + "\"}";
    }

    private void writeDocuments(BTreeEngine engine, int count) {
        for (int i = 1; i <= count; i++) {
            engine.put("docs", "doc-" + i, document(i));
        }
    }

    @Test
    @DisplayName("records straddling the old 1 MB read boundary all come back")
    void recordsStraddlingTheReadBufferRoundTrip(@TempDir Path dir) {
        int count = 12;
        var engine = new BTreeEngine(dir, 1000, 0); // no background flusher: flush() is explicit
        engine.close();                            // a no-op for content, keeps the resource tidy

        var writer = new BTreeEngine(dir, 1000, 0);
        try {
            writeDocuments(writer, count);
            writer.flush();
        } finally {
            writer.close();
        }

        var reopened = new BTreeEngine(dir, 1000, 0);
        try {
            List<String> missing = new ArrayList<>();
            for (int i = 1; i <= count; i++) {
                if (reopened.get("docs", "doc-" + i) == null) missing.add("doc-" + i);
            }
            assertTrue(missing.isEmpty(),
                    "the index file holds all " + count + " records, but " + missing.size()
                            + " were unreadable after reopening it: " + missing
                            + " (a record straddling a read boundary used to desynchronise the parse)");
            assertEquals(count, reopened.keys("docs").size(),
                    "every written key must be listed exactly once");
            for (int i = 1; i <= count; i++) {
                assertEquals(document(i), reopened.get("docs", "doc-" + i),
                        "doc-" + i + " must round-trip unchanged, not as a misparsed neighbour");
            }
        } finally {
            reopened.close();
            engine.close();
        }
    }

    @Test
    @DisplayName("an entry larger than the old 1 MB write buffer is written, not rejected")
    void entryLargerThanTheWriteBufferIsPersisted(@TempDir Path dir) {
        String big = "{\"id\":\"big\",\"pad\":\"" + "y".repeat(2_000_000) + "\"}";
        var writer = new BTreeEngine(dir, 1000, 0);
        try {
            // The fixed-size write buffer threw BufferOverflowException here — from inside flush(),
            // after the index file had already been truncated.
            writer.put("docs", "big", big);
            writer.flush();
        } finally {
            writer.close();
        }

        var reopened = new BTreeEngine(dir, 1000, 0);
        try {
            assertEquals(big, reopened.get("docs", "big"),
                    "a 2 MB document must be persisted and read back unchanged");
        } finally {
            reopened.close();
        }
    }

    @Test
    @DisplayName("a truncated index is reported, not silently half-read")
    void truncatedIndexFailsLoudly(@TempDir Path dir) throws Exception {
        var writer = new BTreeEngine(dir, 1000, 0);
        try {
            writer.put("docs", "kept", document(1));
            writer.put("docs", "also-kept", document(2));
            writer.flush();
        } finally {
            writer.close();
        }

        Path indexFile = dir.resolve(".btree").resolve("btree_index.dat");
        byte[] whole = Files.readAllBytes(indexFile);
        // Chop the file mid-record: the shape a non-atomic writer leaves behind.
        Files.write(indexFile, java.util.Arrays.copyOf(whole, whole.length - 1000));

        // Refusing to start beats starting with a plausible-looking subset of the data.
        var ex = assertThrows(Exception.class, () -> new BTreeEngine(dir, 1000, 0),
                "a torn index must be reported, not read as if it were complete");
        String message = String.valueOf(ex.getMessage()) + String.valueOf(ex.getCause());
        assertTrue(message.contains("index") || message.contains("truncated"),
                "the failure must name the index, got: " + message);
    }
}

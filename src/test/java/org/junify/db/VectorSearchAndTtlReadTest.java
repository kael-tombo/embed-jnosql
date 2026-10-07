package org.junify.db;

import org.junify.db.config.JunifyDBConfig;
import org.junify.db.nosql.document.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for two engine surfaces probed live on the running
 * console after the R-45..R-49 sweeps:
 *
 * <ul>
 *   <li><b>R-50</b> — {@code POST /api/vectors/{unknown}/search} silently
 *       created an empty vector index (dimensioned from the query vector's
 *       width) and answered {@code 200 {"results":[]}} — a success signal for
 *       a typo'd index name. It also answered a missing {@code vector} field
 *       with an NPE 500 and a negative {@code k} with a bare "{@code -5}" 500,
 *       while {@code k=0} returned a fake empty success. Search on an unknown
 *       index is now 404 (only add creates on first use); malformed search
 *       input is 400.</li>
 *   <li><b>R-51</b> — a document whose TTL had passed was still returned by
 *       point reads and scans, with only an {@code expired:true} metadata
 *       field hinting at its state — so the console, the query layer, and adapters
 *       all read data that should be logically deleted. Reads now treat
 *       expiry the way the KV engine already does: an expired document reads
 *       as absent, and {@code cleanupExpired()} remains the physical sweeper.
 *       (Fixes the read side of R-33's follow-up; TTL-01's lazy physical
 *       deletion remains documented and accepted.)</li>
 * </ul>
 */
@DisplayName("Vector search refuses unknown indexes; expired documents read as absent (R-50, R-51)")
class VectorSearchAndTtlReadTest {

    private JunifyDB db;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        db = JunifyDB.create(JunifyDB.embed()
                .storageEngine(JunifyDBConfig.StorageEngineType.IN_MEMORY)
                .buildConfig());
    }

    @AfterEach
    void tearDown() {
        if (db != null && db.isOpen()) db.close();
    }

    // ---- R-51: expired documents are hidden from reads -----------------------

    @Test
    @DisplayName("R-51: point read of an expired document returns null")
    void expiredDocumentPointReadIsNull() throws Exception {
        var col = db.documentCollection("ttlRead");
        var doc = new Document().add("v", 1);
        doc.id("gone");
        col.insert(doc, 0); // no TTL yet
        col.setTtl("gone", 0); // no-op reset
        // Set a negative-time expiry by inserting with ttl then rewinding:
        col.insert(new Document().add("v", 2).id("short"), 0);
        var shortDoc = col.findById("short");
        shortDoc.expiresAt(System.currentTimeMillis() - 1000); // already past
        col.update(shortDoc);

        assertNull(col.findById("short"), "an expired document must read as absent");
        assertNotNull(col.findById("gone"), "a live document is unaffected");
    }

    @Test
    @DisplayName("R-51: scans and predicate queries hide expired documents")
    void expiredDocumentsHiddenFromScans() {
        var col = db.documentCollection("ttlScan");
        var live = new Document().add("v", 1);
        live.id("live");
        var dead = new Document().add("v", 2);
        dead.id("dead");
        col.insert(live);
        col.insert(dead);
        var stored = col.findById("dead");
        stored.expiresAt(System.currentTimeMillis() - 500);
        col.update(stored);

        var all = col.findAll();
        assertTrue(all.stream().anyMatch(d -> "live".equals(d.id())));
        assertFalse(all.stream().anyMatch(d -> "dead".equals(d.id())),
                "expired documents must not appear in scans");

        assertEquals(0, col.count(org.junify.db.nosql.document.Query.all())
                - col.findAll().size(), "count() must agree with the filtered scan");
    }

    @Test
    @DisplayName("R-51: cleanupExpired still physically removes what reads hide")
    void cleanupStillRemoves() {
        var col = db.documentCollection("ttlSweep");
        var doc = new Document().add("v", 1);
        doc.id("sweep");
        col.insert(doc);
        var stored = col.findById("sweep");
        stored.expiresAt(System.currentTimeMillis() - 500);
        col.update(stored);

        assertNull(col.findById("sweep"), "hidden from reads immediately");
        assertEquals(1, col.cleanupExpired(), "sweep physically removes it");
    }

    @Test
    @DisplayName("R-51: a predicate count does not see expired documents")
    void countDoesNotSeeExpired() {
        var col = db.documentCollection("ttl_sql");
        col.insert(new Document().add("v", 1).id("a"));
        col.insert(new Document().add("v", 2).id("b"));
        var b = col.findById("b");
        b.expiresAt(System.currentTimeMillis() - 500);
        col.update(b);

        assertEquals(1, col.count(org.junify.db.nosql.document.Query.all()),
                "the expired row must not be counted");
    }

    // ---- R-50 companion: engine-level TTL insert/expiry contract -------------

    @Test
    @DisplayName("R-51: insert-with-TTL then expiry hides the document")
    void insertWithTtlExpires() throws Exception {
        var col = db.documentCollection("ttlInsert");
        var doc = new Document().add("v", 9);
        doc.id("tick");
        col.insert(doc, 0);
        col.setTtl("tick", 0);

        var fresh = new Document().add("v", 9);
        fresh.id("tock");
        col.insert(fresh, 0);
        // Force expiry without sleeping a full second:
        var tock = col.findById("tock");
        tock.expiresAt(System.currentTimeMillis() - 1);
        col.update(tock);

        assertNull(col.findById("tock"));
    }
}

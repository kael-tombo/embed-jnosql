package org.embeddedjnosql.db;

import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.embeddedjnosql.db.storage.spi.BTreeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for R-68: {@code B_TREE} must reach disk on its own.
 *
 * <p>Measured before the fix by the contract gate's restart block: a document written through the
 * console was readable before a server restart and <b>gone</b> afterwards, with
 * {@code .btree/} empty on disk. The engine wrote its index only when something called
 * {@code flush()} — and in the server path nothing did except a graceful close, so a terminated
 * server (the normal way to stop one, including this project's own documented
 * {@code Stop-Process -Force}) lost every record written since the last flush. FILE and LSM_TREE
 * each run a background flusher; {@code B_TREE} had none, despite advertising itself as
 * {@code "type":"btree-persistent"} and printing {@code Flush mode: sync} at startup.</p>
 *
 * <p>These tests never call {@code flush()} themselves — that is the point.</p>
 */
@DisplayName("B_TREE persists without an explicit flush (R-68)")
class BTreeAutoFlushPersistenceTest {

    private static final int INTERVAL_MS = 150;

    /** Waits for the periodic flusher to run at least twice. */
    private void waitForAutoFlush() throws InterruptedException {
        Thread.sleep(INTERVAL_MS * 4);
    }

    @Test
    @DisplayName("a written record reaches the index file with no flush call, and survives a restart")
    void recordReachesDiskWithoutAnExplicitFlush(@TempDir Path dir) throws Exception {
        var engine = new BTreeEngine(dir, 1000, INTERVAL_MS);
        engine.put("orders", "k1", "{\"id\":\"k1\"}");

        waitForAutoFlush();

        Path indexFile = dir.resolve(".btree").resolve("btree_index.dat");
        assertTrue(Files.exists(indexFile),
                "the background flusher must write the index on its own; .btree contains "
                        + (Files.exists(indexFile.getParent()) ? Files.list(indexFile.getParent()).map(p -> p.getFileName().toString()).toList() : "nothing"));

        // The engine is deliberately NOT closed: the file must already be complete, which is
        // what a restart after a hard termination will read.
        var reopened = new BTreeEngine(dir, 1000, 0L);
        try {
            assertEquals(1, reopened.keys("orders").size(),
                    "a record written before the flush interval elapsed must be on disk: "
                            + reopened.scan("orders"));
            assertEquals("{\"id\":\"k1\"}", reopened.get("orders", "k1"));
        } finally {
            reopened.close();
            engine.close();
        }
    }

    @Test
    @DisplayName("a whole session's rows survive a restart of the database")
    void rowsSurviveADatabaseRestart(@TempDir Path dir) throws Exception {
        try (EmbedJNoSQL db = EmbedJNoSQL.embed()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.B_TREE)
                .persistTo(dir.toString())
                .flushIntervalMs(INTERVAL_MS)
                .build()) {
            db.documentCollection("invoices").insert(
                    org.embeddedjnosql.db.nosql.document.Document.of("sku", "B-1"));
            db.documentCollection("ledger").insert(
                    org.embeddedjnosql.db.nosql.document.Document.of("note", "first").id("1"));
            waitForAutoFlush();
        }

        try (EmbedJNoSQL reopened = EmbedJNoSQL.embed()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.B_TREE)
                .persistTo(dir.toString())
                .build()) {
            assertEquals(1, reopened.documentCollection("invoices").count(),
                    "document rows must survive a B_TREE restart");
            assertEquals(1, reopened.documentCollection("ledger").count(),
                    "a second collection's rows must survive a B_TREE restart");
        }
    }

    @Test
    @DisplayName("the engine reports its flush interval instead of hiding it")
    void statsReportTheFlushInterval(@TempDir Path dir) {
        var engine = new BTreeEngine(dir, 1000, INTERVAL_MS);
        try {
            assertEquals(INTERVAL_MS, ((Number) engine.stats().get("flushIntervalMs")).intValue(),
                    "operators must be able to see how much a hard kill could cost: " + engine.stats());
        } finally {
            engine.close();
        }
    }
}

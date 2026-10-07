package org.embeddedjnosql.db;

import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.storage.spi.FileEngine;
import org.embeddedjnosql.db.storage.spi.LSMTreeEngine;
import org.embeddedjnosql.db.transaction.mvcc.MVCCManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression tests for defects found and fixed during the public-release audit.
 * Each test fails against the pre-fix implementation.
 * See docs/release-audit/53-defect-register.md (defects R-01 .. R-04).
 *
 * Crash-state tests write the on-disk state directly (stale snapshot + WAL tail),
 * which is exactly what an unclean shutdown leaves behind — no reliance on
 * close()-time flushing, which would mask the defect being tested.
 */
class ReleaseAuditRegressionTest {

    // ------------------------------------------------------------------
    // DEFECT R-01: write-write conflict detection could never trigger
    // ------------------------------------------------------------------

    @Test
    void mvccCommitRejectsConcurrentWriteToSameKey() {
        var mvcc = new MVCCManager();

        // Both transactions take their snapshot BEFORE the first commit:
        // the conflict window for T1 is (t1Start, t1Commit].
        long t1Start = mvcc.assignTimestamp();
        long t2Start = mvcc.assignTimestamp();

        // T2 stages and commits first — it wins the key.
        mvcc.stageWrite("tx2", "col:acct", Document.of("v", "tx2"));
        long t2Commit = mvcc.assignTimestamp();
        assertTrue(mvcc.commit("tx2", t2Commit, t2Start), "first committer must win");

        // T1 staged the same key before T2 committed — its commit must be rejected.
        mvcc.stageWrite("tx1", "col:acct", Document.of("v", "tx1"));
        long t1Commit = mvcc.assignTimestamp();
        assertFalse(mvcc.commit("tx1", t1Commit, t1Start),
                "write-write conflict must be detected: T1 snapshot predates T2's commit");

        // The winner's value must remain visible to a fresh snapshot.
        var visible = mvcc.read("col:acct", mvcc.assignTimestamp(), null);
        assertNotNull(visible);
        assertTrue(visible.toJson().contains("tx2"), "losing commit must not overwrite the winner");
    }

    @Test
    void mvccCommitStillSucceedsWithoutConflict() {
        var mvcc = new MVCCManager();
        long start = mvcc.assignTimestamp();
        mvcc.stageWrite("tx-a", "col:k1", Document.of("v", 1));
        mvcc.stageWrite("tx-a", "col:k2", Document.of("v", 2));
        long commit = mvcc.assignTimestamp();
        assertTrue(mvcc.commit("tx-a", commit, start));
        assertEquals(2, (int) mvcc.stats().get("keys"));
    }

    @Test
    void transactionCommitSurfacesConflictAsException() {
        try (var db = EmbedJNoSQL.inMemory()) {
            db.documentCollection("acct").insert(Document.of("balance", 100).id("a1"));

            // T1 and T2 both take their snapshot before either commits, then both write.
            var t1 = db.beginTransaction();
            var t2 = db.beginTransaction();
            t1.write("acct", "a1", "{\"balance\":150}");
            t2.write("acct", "a1", "{\"balance\":900}");

            t2.commit(); // first committer wins
            assertThrows(IllegalStateException.class, t1::commit,
                    "second committer on the same key must be aborted");
        }
    }

    // ------------------------------------------------------------------
    // DEFECT R-02: FileEngine never replayed its WAL (silent data loss)
    // ------------------------------------------------------------------

    @Test
    void fileEngineRecoversUnflushedWritesFromWal(@TempDir Path dir) throws Exception {
        // Stale snapshot (simulates the last successful JSON flush) ...
        var colDir = Files.createDirectories(dir);
        Files.writeString(colDir.resolve("orders.json"),
                "{\"o0\":\"{\\\"total\\\":0}\"}", StandardCharsets.UTF_8);

        // ... plus WAL tail entries that were never flushed (unclean shutdown).
        String wal = "1|1700000000000|CHECKPOINT|__meta__||\n" // torn/nonstandard marker line, ignored
                + "2|1700000000001|PUT|orders|o1|{\"total\":42}\n"
                + "3|1700000000002|PUT|orders|o2|{\"total\":7}\n";
        Files.createDirectories(dir.resolve(".wal"));
        Files.writeString(dir.resolve(".wal").resolve("wal.log"), wal, StandardCharsets.UTF_8);

        var engine = new FileEngine(dir, 3_600_000, true);
        try {
            assertEquals("{\"total\":42}", engine.get("orders", "o1"),
                    "WAL replay must recover unflushed puts");
            assertEquals("{\"total\":7}", engine.get("orders", "o2"));
            assertEquals("{\"total\":0}", engine.get("orders", "o0"),
                    "snapshot data must survive alongside replayed data");
        } finally {
            engine.close();
        }
    }

    @Test
    void fileEngineReplaysDeletesNewerThanCheckpoint(@TempDir Path dir) throws Exception {
        // Snapshot: k0 and k1 present, k2 already at its newest value.
        Files.writeString(dir.resolve("c.json"),
                "{\"k0\":\"v0\",\"k1\":\"v1\",\"k2\":\"v2new\"}", StandardCharsets.UTF_8);
        // Real WAL shape: PUT lines, then CHECKPOINT:N (written at flush time),
        // then post-flush operations from the unclean final session.
        String wal = "1|1700000000000|PUT|c|k2|v2old\n"
                + "2|1700000000001|PUT|c|k1|v1\n"
                + "CHECKPOINT:2\n"
                + "3|1700000000002|DELETE|c|k1|\n";
        Files.createDirectories(dir.resolve(".wal"));
        Files.writeString(dir.resolve(".wal").resolve("wal.log"), wal, StandardCharsets.UTF_8);

        var engine = new FileEngine(dir, 3_600_000, true);
        try {
            assertEquals("v0", engine.get("c", "k0"));
            assertNull(engine.get("c", "k1"),
                    "deletes newer than the checkpoint must be replayed");
            assertEquals("v2new", engine.get("c", "k2"),
                    "pre-checkpoint WAL entries must not overwrite the newer snapshot value");
        } finally {
            engine.close();
        }
    }

    @Test
    void fileEngineCollectionNamesTrackFlushedCollections(@TempDir Path dir) {
        var engine = new FileEngine(dir, 3_600_000, true);
        try {
            assertTrue(engine.collectionNames().isEmpty());
            engine.put("alpha", "k", "v");
            assertTrue(engine.collectionNames().isEmpty(), "not yet flushed");
            engine.flush();
            assertTrue(engine.collectionNames().contains("alpha"));
        } finally {
            engine.close();
        }
    }

    // ------------------------------------------------------------------
    // DEFECT R-03: LSM skipped WAL replay whenever SSTables existed
    // ------------------------------------------------------------------

    @Test
    void lsmRecoversPostFlushWritesFromWal(@TempDir Path dir) throws Exception {
        // Session 1: flush a key to an SSTable, close cleanly.
        var lsm = new LSMTreeEngine(dir);
        try {
            lsm.put("c", "flushed", "1");
            lsm.flush();
        } finally {
            lsm.close();
        }
        try (var sstFiles = Files.list(dir.resolve(".sst"))) {
            assertTrue(sstFiles.findAny().isPresent(),
                    "an SSTable must exist for this scenario");
        }

        // Simulated unclean crash: a WAL entry was written after the last flush
        // and the process died before the memtable reached an SSTable.
        Files.writeString(dir.resolve(".wal").resolve("wal.log"),
                "2|1700000000001|PUT|c|after-flush|2\n",
                StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);

        var lsm2 = new LSMTreeEngine(dir);
        try {
            assertEquals("1", lsm2.get("c", "flushed"),
                    "SSTable data must survive the restart");
            assertEquals("2", lsm2.get("c", "after-flush"),
                    "LSM must replay WAL entries even when SSTables exist");
        } finally {
            lsm2.close();
        }
    }

    // ------------------------------------------------------------------
    // DEFECT R-04: persisted collections invisible after restart
    // ------------------------------------------------------------------

    @Test
    void persistedCollectionsAreRediscoveredAfterRestart(@TempDir Path dir) throws Exception {
        try (var db = EmbedJNoSQL.create(EmbedJNoSQLConfig.builder()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.FILE)
                .dataDir(dir)
                .buildConfig())) {
            db.documentCollection("products").insert(
                    org.embeddedjnosql.db.nosql.document.Document.of("name", "Keyboard")
                            .add("price", 75.0).id("p1"));
            db.flush();
        }

        // Restart on the same data directory.
        try (var db2 = EmbedJNoSQL.create(EmbedJNoSQLConfig.builder()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.FILE)
                .dataDir(dir)
                .buildConfig())) {
            assertTrue(db2.getCollectionNames().contains("products"),
                    "collections persisted by a previous run must be listed after restart");
            assertEquals(1, db2.documentCollection("products").count());
            assertEquals(1, db2.documentCollection("products").findAll().size());
        }
    }
}

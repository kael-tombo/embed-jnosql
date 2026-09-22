package org.junify.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junify.db.config.JunifyDBConfig;
import org.junify.db.core.backup.BackupManager;
import org.junify.db.nosql.document.Document;
import org.junify.db.storage.spi.FileEngine;
import org.junify.db.storage.spi.InMemoryEngine;
import org.junify.db.storage.spi.StorageEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for a backup that reported success while capturing nothing.
 *
 * <p>The previous implementation enumerated {@code engine.keys("")} — the keys of a
 * collection literally named {@code ""} — so every snapshot was the two bytes {@code {}}.
 * The original round-trip test still passed, because it restored into a database whose
 * documents were already on disk from the original write, so the count assertion held
 * whether or not the restore did anything. These tests assert on backup <em>content</em>
 * and on the raw bytes of the snapshot instead.
 */
class BackupIntegrityTest {

    private StorageEngine fileEngine(Path dir) {
        return JunifyDBConfig.StorageEngineType.FILE.create(dir, false, 1000);
    }

    @Test
    void fileEngineBackupCapturesLiveDocuments(@TempDir Path dir) throws IOException {
        try (var db = JunifyDB.embed()
                .storageEngine(JunifyDBConfig.StorageEngineType.FILE)
                .persistTo(dir.toString())
                .build()) {
            db.documentCollection("users").insertAll(java.util.List.of(
                    Document.of("name", "Alice").add("age", 30),
                    Document.of("name", "Bob").add("age", 25)));
            db.documentCollection("orders").insert(Document.of("total", 99));
            db.keyValueBucket("sessions").put("s1", "token-1");

            var manager = new BackupManager(db.storageEngine());
            var file = manager.backup(dir.resolve("backups"));

            assertTrue(Files.size(file) > 2, "Snapshot must hold more than an empty JSON object");

            var counts = manager.lastBackupCounts();
            assertEquals(2, counts.get("users"), "both users must be captured");
            assertEquals(1, counts.get("orders"));
            assertEquals(1, counts.get("sessions"), "non-document collections must be captured too");

            var raw = read(file);
            assertTrue(raw.contains("Alice") && raw.contains("Bob"), "snapshot must contain the document values, got: " + raw);
            assertTrue(raw.contains("sessions"));
        }
    }

    @Test
    void backupRestoresIntoCleanEngine(@TempDir Path dir) throws IOException {
        var source = fileEngine(dir.resolve("source"));
        source.put("users", "user:1", "{\"id\":\"user:1\",\"name\":\"Alice\"}");
        source.put("users", "user:2", "{\"id\":\"user:2\",\"name\":\"Bob\"}");

        var file = new BackupManager(source).backup(dir.resolve("snap"));
        source.close();

        // Restore into a *different*, empty engine: success here cannot be explained by
        // pre-existing on-disk data, which is what made the old test vacuous.
        var target = fileEngine(dir.resolve("target"));
        new BackupManager(target).restore(file);

        assertEquals(java.util.Set.of("user:1", "user:2"), target.keys("users"));
        assertTrue(target.get("users", "user:2").contains("Bob"));
        target.close();
    }

    @Test
    void inMemoryEngineBackupCapturesDocuments(@TempDir Path dir) throws IOException {
        var engine = new InMemoryEngine();
        engine.put("events", "e1", "{\"id\":\"e1\",\"type\":\"login\"}");
        engine.put("events", "e2", "{\"id\":\"e2\",\"type\":\"logout\"}");

        var manager = new BackupManager(engine);
        var file = manager.backup(dir);

        assertEquals(2, manager.lastBackupCounts().get("events"));
        assertTrue(read(file).contains("login"));
    }

    @Test
    void backupRefusesToReportSuccessWhenEngineCannotBeEnumerated(@TempDir Path dir) {
        // An engine that holds entries but reports no collections must fail loudly rather
        // than hand back an empty snapshot the user would trust.
        var opaque = new InMemoryEngine() {
            @Override
            public java.util.Set<String> collections() {
                return java.util.Set.of();
            }
        };
        opaque.put("users", "u1", "{\"id\":\"u1\"}");

        var error = assertThrows(IOException.class, () -> new BackupManager(opaque).backup(dir));
        assertTrue(error.getMessage().contains("holds 1 entries"), error.getMessage());
    }

    @Test
    void emptyDatabaseProducesValidEmptySnapshot(@TempDir Path dir) throws IOException {
        var engine = new InMemoryEngine();
        var manager = new BackupManager(engine);
        var file = manager.backup(dir);

        assertEquals(java.util.Map.of(), manager.lastBackupCounts());
        assertEquals("{}", read(file).trim());
    }

    @Test
    void fileEngineCollectionsIncludeUnflushedWrites(@TempDir Path dir) {
        // collectionNames() only reports what was snapshotted to disk; a backup must not
        // depend on a flush having happened.
        var engine = new FileEngine(dir, 1000, true);
        engine.put("late", "k1", "{\"id\":\"k1\"}");
        assertTrue(engine.collections().contains("late"));
        engine.close();
    }

    @Test
    void lsmTreeCollectionsSpanMemtableAndSSTables(@TempDir Path dir) throws IOException {
        try (var db = JunifyDB.embed()
                .storageEngine(JunifyDBConfig.StorageEngineType.LSM_TREE)
                .persistTo(dir.toString())
                .build()) {
            db.documentCollection("events").insert(Document.of("type", "login"));
            // Flush moves the entry out of the memtable into an SSTable; the collection
            // must still be discovered afterwards.
            db.storageEngine().flush();
            db.documentCollection("later").insert(Document.of("type", "logout"));

            assertTrue(db.storageEngine().collections().containsAll(java.util.Set.of("events", "later")));

            var manager = new BackupManager(db.storageEngine());
            manager.backup(dir.resolve("snap"));
            assertEquals(1, manager.lastBackupCounts().get("events"), "flushed entries must be captured");
            assertEquals(1, manager.lastBackupCounts().get("later"));
        }
    }

    @Test
    void bTreeCollectionsAreDiscoverable(@TempDir Path dir) throws IOException {
        var engine = new org.junify.db.storage.spi.BTreeEngine(dir);
        engine.put("users", "u1", "{\"id\":\"u1\",\"name\":\"Alice\"}");
        engine.put("audit", "a1", "{\"id\":\"a1\"}");

        assertEquals(java.util.Set.of("users", "audit"), engine.collections());

        var manager = new BackupManager(engine);
        manager.backup(dir.resolve("snap"));
        assertEquals(1, manager.lastBackupCounts().get("users"));
        engine.close();
    }

    private String read(Path file) throws IOException {
        try (var in = new GZIPInputStream(Files.newInputStream(file))) {
            return new String(in.readAllBytes());
        }
    }
}

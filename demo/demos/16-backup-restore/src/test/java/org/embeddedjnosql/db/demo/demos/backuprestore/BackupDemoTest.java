package org.embeddedjnosql.db.demo.demos.backuprestore;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class BackupDemoTest {

    private EmbedJNoSQL db;

    @BeforeEach
    void setUp() { db = EmbedJNoSQL.inMemory(); }

    @AfterEach
    void tearDown() { if (db != null) db.close(); }

    @Test
    void roundTripPreservesAllDocuments(@TempDir Path dir) throws Exception {
        var r = BackupDemo.runDemo(db, dir);
        assertEquals(5, r.backedUpDocs());
        assertEquals(5, r.restoredDocs());
        assertTrue(r.contentMatches());
        assertTrue(r.backupFile().getFileName().toString().endsWith(".json.gz"));
    }

    @Test
    void restoreRefusesMissingFile() {
        var mgr = new org.embeddedjnosql.db.core.backup.BackupManager(db.storageEngine());
        assertThrows(java.io.IOException.class,
                () -> mgr.restore(Path.of("definitely-missing-backup.json.gz")));
    }
}

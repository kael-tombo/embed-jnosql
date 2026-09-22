package org.junify.db.integration;

import org.junify.db.JunifyDB;
import org.junify.db.core.backup.BackupManager;
import org.junify.db.nosql.column.ColumnFamily;
import org.junify.db.config.JunifyDBConfig;
import org.junify.db.nosql.document.Document;
import org.junify.db.nosql.document.DocumentCollection;
import org.junify.db.nosql.document.Query;
import org.junify.db.nosql.kv.KeyValueBucket;
import org.junify.db.core.metrics.DatabaseMetrics;
import org.junify.db.storage.spi.FileEngine;
import org.junify.db.transaction.mvcc.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FullIntegrationTest {

    private JunifyDB db;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("junify-integration");
        db = JunifyDB.embed()
                .storageEngine(JunifyDBConfig.StorageEngineType.FILE)
                .persistTo(tempDir.toString())
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (db != null && db.isOpen()) db.close();
        Files.walk(tempDir)
                .sorted((a, b) -> -a.compareTo(b))
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
    }

    @Test
    void endToEndDocumentWorkflow() {
        var users = db.documentCollection("users");
        var orders = db.documentCollection("orders");

        var alice = users.insert(Document.of("name", "Alice").add("email", "alice@test.com").add("active", true));
        var bob = users.insert(Document.of("name", "Bob").add("email", "bob@test.com").add("active", false));
        var charlie = users.insert(Document.of("name", "Charlie").add("email", "charlie@test.com").add("active", true));

        assertEquals(3, users.count());

        var activeUsers = users.find(Query.eq("active", true));
        assertEquals(2, activeUsers.size());

        var order1 = orders.insert(Document.of("userId", alice.id()).add("item", "Widget").add("amount", 29.99));
        var order2 = orders.insert(Document.of("userId", alice.id()).add("item", "Gadget").add("amount", 49.99));
        var order3 = orders.insert(Document.of("userId", bob.id()).add("item", "Thing").add("amount", 9.99));

        assertEquals(3, orders.count());

        var aliceOrders = orders.find(Query.eq("userId", alice.id()));
        assertEquals(2, aliceOrders.size());

        bob.add("email", "bob.updated@test.com");
        users.update(bob);
        var updatedBob = users.findById(bob.id());
        assertEquals("bob.updated@test.com", updatedBob.get("email"));

        users.deleteById(charlie.id());
        assertEquals(2, users.count());

        var stats = users.stats();
        assertEquals(2L, stats.get("count"));
    }

    @Test
    void endToEndKeyValueWorkflow() {
        var cache = db.keyValueBucket("session-cache");
        var counters = db.keyValueBucket("counters");

        cache.put("session:abc", "user-123", Duration.ofHours(1));
        assertEquals("user-123", cache.get("session:abc"));

        for (int i = 0; i < 100; i++) {
            counters.increment("page-views");
        }
        assertEquals("100", counters.get("page-views"));

        counters.decrement("page-views");
        assertEquals("99", counters.get("page-views"));

        cache.delete("session:abc");
        assertNull(cache.get("session:abc"));
    }

    @Test
    void endToEndTransactionWorkflow() {
        var accounts = db.documentCollection("accounts");

        try (var tx = db.beginTransaction()) {
            var col = tx.documentCollection("accounts");
            col.insert(Document.of("user", "Alice").add("balance", 1000));
            col.insert(Document.of("user", "Bob").add("balance", 500));
            tx.commit();
        }

        assertEquals(2, accounts.count());

        try (var tx = db.beginTransaction()) {
            var col = tx.documentCollection("accounts");
            col.insert(Document.of("user", "Charlie").add("balance", 750));
            tx.rollback();
        }

        assertEquals(2, accounts.count());
    }

    @Test
    void endToEndColumnFamilyWorkflow() {
        var users = db.columnFamily("users");

        users.put("user:1", "name", "Alice");
        users.put("user:1", "email", "alice@test.com");
        users.put("user:1", "age", 30);

        users.put("user:2", "name", "Bob");
        users.put("user:2", "email", "bob@test.com");

        assertEquals("Alice", users.get("user:1", "name"));
        assertEquals(30, users.get("user:1", "age"));
        assertEquals(2, users.countRows());

        users.deleteColumn("user:1", "age");
        assertNull(users.get("user:1", "age"));
        assertEquals("Alice", users.get("user:1", "name"));
    }

    @Test
    void endToEndBackupRestore() throws IOException {
        var users = db.documentCollection("users");
        users.insertAll(List.of(
                Document.of("name", "Alice").add("age", 30),
                Document.of("name", "Bob").add("age", 25)
        ));

        var backupDir = Files.createTempDirectory("junify-backup");
        var backupManager = new BackupManager(new FileEngine(db.config().dataDir(), 1000, false));
        var backupFile = backupManager.backup(backupDir);

        assertTrue(Files.exists(backupFile));
        // Assert on content, not just existence: an empty snapshot is 2 bytes of "{}",
        // so a size check alone passed while backups captured nothing at all.
        assertTrue(Files.size(backupFile) > 2, "snapshot must contain documents, not an empty JSON object");
        try (var in = new java.util.zip.GZIPInputStream(Files.newInputStream(backupFile))) {
            var snapshot = new String(in.readAllBytes());
            assertTrue(snapshot.contains("Alice") && snapshot.contains("Bob"),
                    "snapshot must hold the seeded documents, got: " + snapshot);
        }

        db.close();
        db = JunifyDB.embed()
                .storageEngine(JunifyDBConfig.StorageEngineType.FILE)
                .persistTo(tempDir.toString())
                .build();

        var restoreManager = new BackupManager(new FileEngine(db.config().dataDir(), 1000, false));
        restoreManager.restore(backupFile);

        var restored = db.documentCollection("users");
        assertEquals(2, restored.count());
    }

    @Test
    void endToEndMetrics() {
        var metrics = new DatabaseMetrics();
        var users = db.documentCollection("users");

        metrics.recordInsert();
        users.insert(Document.of("name", "Alice"));
        metrics.recordInsert();
        users.insert(Document.of("name", "Bob"));
        metrics.recordQuery();
        users.find(Query.eq("name", "Alice"));

        var snapshot = metrics.snapshot();
        assertEquals(2L, snapshot.get("inserts"));
        assertEquals(1L, snapshot.get("queries"));
        assertTrue((double) snapshot.get("opsPerSecond") >= 0);
    }

    @Test
    void endToEndPersistenceAcrossRestarts() {
        var users = db.documentCollection("users");
        users.insert(Document.of("name", "Alice").add("age", 30));
        db.close();

        db = JunifyDB.embed()
                .storageEngine(JunifyDBConfig.StorageEngineType.FILE)
                .persistTo(tempDir.toString())
                .build();
        users = db.documentCollection("users");

        assertEquals(1, users.count());
        var found = users.findOne(Query.eq("name", "Alice"));
        assertNotNull(found);
        assertEquals("Alice", found.get("name"));
        assertEquals(30, (int) found.get("age"));
    }
}

package org.embeddedjnosql.db.demo.demos.backuprestore;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.core.backup.BackupManager;
import org.embeddedjnosql.db.nosql.document.Document;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Demo 16 — Backup &amp; restore: a gzip JSON snapshot of every collection,
 * written from the live engine, restored into a fresh database instance.
 * The round-trip must preserve every document id and field.
 */
public class BackupDemo {

    public record Result(Path backupFile, int backedUpDocs, int restoredDocs,
                         boolean contentMatches) {}

    public static Result runDemo(EmbedJNoSQL db, Path targetDir) throws Exception {
        var products = db.documentCollection("products");
        for (int i = 1; i <= 5; i++) {
            products.insert(new Document().id("p-" + i).add("name", "Product " + i).add("price", 10.0 * i));
        }

        // 1. Snapshot the live engine
        BackupManager backups = new BackupManager(db.storageEngine());
        Path file = backups.backup(targetDir);
        var counts = backups.lastBackupCounts();

        // 2. Restore into a fresh in-memory database
        try (EmbedJNoSQL restored = EmbedJNoSQL.inMemory()) {
            var target = restored.documentCollection("products");
            new BackupManager(restored.storageEngine()).restore(file);

            var recovered = restored.documentCollection("products").findAll();
            boolean match = recovered.size() == 5
                    && recovered.stream().allMatch(d -> d.get("name") != null)
                    && target.findById("p-3") != null;

            Result r = new Result(file, counts.getOrDefault("products", 0), recovered.size(), match);
            if (r.backedUpDocs() != 5 || r.restoredDocs() != 5 || !r.contentMatches()) {
                throw new AssertionError("Unexpected backup results: " + r);
            }
            return r;
        } finally {
            Files.deleteIfExists(file);
        }
    }

    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempDirectory("embedjnosql-backup-demo");
        try (EmbedJNoSQL db = EmbedJNoSQL.inMemory()) {
            Result r = runDemo(db, tmp);
            System.out.println("== Backup/restore demo ==");
            System.out.println("backup file    : " + r.backupFile().getFileName());
            System.out.println("backed up docs : " + r.backedUpDocs());
            System.out.println("restored docs  : " + r.restoredDocs());
            System.out.println("content matches: " + r.contentMatches());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}

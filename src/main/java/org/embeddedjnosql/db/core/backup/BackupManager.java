package org.embeddedjnosql.db.core.backup;

import org.embeddedjnosql.db.storage.spi.StorageEngine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class BackupManager {

    private final StorageEngine engine;
    private java.util.Map<String, Integer> lastCounts = java.util.Map.of();

    public BackupManager(StorageEngine engine) {
        this.engine = engine;
    }

    /**
     * Documents captured per collection by the most recent {@link #backup(Path)} call.
     * Lets callers report what a backup actually contains instead of assuming success.
     */
    public java.util.Map<String, Integer> lastBackupCounts() {
        return lastCounts;
    }

    /**
     * Writes a gzip-compressed JSON snapshot of every collection in the engine.
     *
     * <p>Collections are enumerated via {@link StorageEngine#collections()}. Refuses to
     * write an empty snapshot while the engine still holds entries: reporting a successful
     * backup of nothing is worse than failing, because the caller would trust it.
     */
    public Path backup(Path targetDir) throws IOException {
        Files.createDirectories(targetDir);
        var backupFile = targetDir.resolve("embeddedjnosql-backup-" + System.currentTimeMillis() + ".json.gz");

        var data = new java.util.LinkedHashMap<String, Object>();
        for (var collection : engine.collections()) {
            var values = engine.scan(collection);
            if (!values.isEmpty()) {
                data.put(collection, values);
            }
        }

        if (data.isEmpty() && engine.size() > 0) {
            throw new IOException("Backup aborted: engine " + engine.name()
                    + " holds " + engine.size() + " entries but reports no collections to back up");
        }

        try (var out = new GZIPOutputStream(Files.newOutputStream(backupFile))) {
            out.write(org.embeddedjnosql.db.core.util.JsonSerde.toJson(data).getBytes());
        }

        var counts = new java.util.LinkedHashMap<String, Integer>();
        for (var entry : data.entrySet()) {
            @SuppressWarnings("unchecked")
            var values = (java.util.List<String>) entry.getValue();
            counts.put(entry.getKey(), values.size());
        }
        this.lastCounts = java.util.Map.copyOf(counts);

        return backupFile;
    }

    public void restore(Path backupFile) throws IOException {
        if (!Files.exists(backupFile)) {
            throw new IOException("Backup file not found: " + backupFile);
        }

        try (var in = new GZIPInputStream(Files.newInputStream(backupFile))) {
            var content = new String(in.readAllBytes());
            @SuppressWarnings("unchecked")
            var data = (java.util.Map<String, java.util.List<String>>) org.embeddedjnosql.db.core.util.JsonSerde.fromJson(content, java.util.Map.class);

            for (var entry : data.entrySet()) {
                var collection = entry.getKey();
                var values = entry.getValue();
                for (var value : values) {
                    try {
                        var doc = org.embeddedjnosql.db.nosql.document.Document.fromJson(value);
                        engine.put(collection, doc.id(), value);
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }
}

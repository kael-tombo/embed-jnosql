package org.embeddedjnosql.db.storage.spi;

import org.embeddedjnosql.db.core.util.JsonSerde;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class DatabaseCompactor {

    private final Path dataDir;
    private final Path backupDir;

    public DatabaseCompactor(Path dataDir) {
        this(dataDir, dataDir.resolve(".backup"));
    }

    public DatabaseCompactor(Path dataDir, Path backupDir) {
        this.dataDir = dataDir;
        this.backupDir = backupDir;
    }

    public CompactionResult compact() throws IOException {
        var originalTotal = new AtomicLong(0);
        var compactedTotal = new AtomicLong(0);
        var removed = new AtomicLong(0);
        var collections = new ArrayList<String>();

        Files.createDirectories(backupDir);

        try (var stream = Files.list(dataDir)) {
            var files = stream.filter(p -> p.toString().endsWith(".json")).toList();
            for (var file : files) {
                var result = compactFile(file);
                originalTotal.addAndGet(result.originalBytes());
                compactedTotal.addAndGet(result.compactedBytes());
                removed.addAndGet(result.removedEntries());
                collections.add(file.getFileName().toString().replace(".json", ""));
            }
        }

        return new CompactionResult(
                originalTotal.get(),
                compactedTotal.get(),
                removed.get(),
                collections.size(),
                collections
        );
    }

    private CompactionResult compactFile(Path file) throws IOException {
        var content = Files.readString(file);
        var rawMap = JsonSerde.fromJson(content, Map.class);
        Map<?, ?> map = rawMap;
        
        long originalSize = map.size();
        Map<String, Object> cleaned = new HashMap<>();
        
        for (var entry : map.entrySet()) {
            String key = entry.getKey().toString();
            Object value = entry.getValue();
            
            if (value instanceof String s && !s.isEmpty()) {
                cleaned.put(key, value);
            } else if (value instanceof Map || value instanceof List) {
                cleaned.put(key, value);
            }
        }
        
        var backupFile = backupDir.resolve(file.getFileName());
        Files.writeString(backupFile, content);
        
        var json = JsonSerde.toJson(cleaned);
        Files.writeString(file, json);
        
        long removedCount = originalSize - cleaned.size();
        return new CompactionResult(originalSize * 100, cleaned.size() * 100, removedCount, 1, List.of());
    }

    public record CompactionResult(
            long originalBytes,
            long compactedBytes,
            long removedEntries,
            int collectionsProcessed,
            List<String> collections
    ) {
        public double compressionRatio() {
            return originalBytes > 0 ? (double) compactedBytes / originalBytes : 1.0;
        }
    }

    public void restore() throws IOException {
        if (!Files.exists(backupDir)) {
            throw new IllegalStateException("No backup directory found");
        }

        try (var stream = Files.list(backupDir)) {
            var files = stream.filter(p -> p.toString().endsWith(".json")).toList();
            for (var backup : files) {
                var original = dataDir.resolve(backup.getFileName());
                Files.copy(backup, original, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    public void cleanupBackup() throws IOException {
        if (Files.exists(backupDir)) {
            try (var stream = Files.list(backupDir)) {
                var files = stream.filter(p -> p.toString().endsWith(".json")).toList();
                for (var file : files) {
                    Files.delete(file);
                }
            }
            Files.deleteIfExists(backupDir);
        }
    }

    public DiskUsage getDiskUsage() throws IOException {
        long dataSize = 0;
        int fileCount = 0;

        try (var stream = Files.list(dataDir)) {
            var files = stream.filter(p -> p.toString().endsWith(".json")).toList();
            for (var file : files) {
                dataSize += Files.size(file);
                fileCount++;
            }
        }

        long walSize = 0;
        var walDir = dataDir.resolve(".wal");
        if (Files.exists(walDir)) {
            try (var stream = Files.list(walDir)) {
                var files = stream.toList();
                for (var file : files) {
                    walSize += Files.size(file);
                }
            }
        }

        long backupSize = 0;
        if (Files.exists(backupDir)) {
            try (var stream = Files.list(backupDir)) {
                var files = stream.toList();
                for (var file : files) {
                    backupSize += Files.size(file);
                }
            }
        }

        long totalSize = dataSize + walSize + backupSize;

        return new DiskUsage(totalSize, dataSize, walSize, backupSize, fileCount);
    }

    public record DiskUsage(
            long totalBytes,
            long dataBytes,
            long walBytes,
            long backupBytes,
            int fileCount
    ) {
        public long availableBytes() {
            return Runtime.getRuntime().freeMemory();
        }
    }
}

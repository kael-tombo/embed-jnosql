package org.junify.db.config;

import org.junify.db.JunifyDB;
import org.junify.db.storage.spi.StorageEngine;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

public record JunifyDBConfig(
        StorageEngineType storageEngine,
        Path dataDir,
        boolean autoFlush,
        int flushIntervalMs,
        ConsoleConfig consoleConfig,
        SecurityConfig securityConfig
) {
    public JunifyDBConfig {
        Objects.requireNonNull(storageEngine, "storageEngine cannot be null");
        Objects.requireNonNull(dataDir, "dataDir cannot be null");
        if (consoleConfig == null) {
            consoleConfig = ConsoleConfig.disabled();
        }
        if (securityConfig == null) {
            securityConfig = SecurityConfig.disabled();
        }
    }

    /**
     * Backward-compatible 4-argument constructor.
     */
    public JunifyDBConfig(StorageEngineType storageEngine, Path dataDir, boolean autoFlush, int flushIntervalMs) {
        this(storageEngine, dataDir, autoFlush, flushIntervalMs, ConsoleConfig.disabled(), SecurityConfig.disabled());
    }

    public enum StorageEngineType {
        IN_MEMORY,
        FILE,
        LSM_TREE,
        B_TREE;

        public StorageEngine create(Path dataDir, boolean autoFlush, int flushIntervalMs) {
            return switch (this) {
                case IN_MEMORY -> new org.junify.db.storage.spi.InMemoryEngine();
                case FILE -> new org.junify.db.storage.spi.FileEngine(dataDir, flushIntervalMs, autoFlush);
                case LSM_TREE -> new org.junify.db.storage.spi.LSMTreeEngine(dataDir, 1024 * 1024, 64 * 1024 * 1024);
                // R-68: B_TREE previously ignored the flush settings entirely and only ever
                // wrote its index on an explicit flush()/close(), so a terminated server lost
                // every record written since the last flush. It now runs the same periodic
                // flusher as FILE. The engine has no WAL, so a hard kill can still lose up to
                // one interval of writes — documented in 15/18, not hidden.
                case B_TREE -> new org.junify.db.storage.spi.BTreeEngine(dataDir, 1000, flushIntervalMs);
            };
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public JunifyDB build() {
        return JunifyDB.create(this);
    }

    public static class Builder {
        private StorageEngineType storageEngine = StorageEngineType.IN_MEMORY;
        private Path dataDir = Paths.get("data");
        private boolean autoFlush = true;
        private int flushIntervalMs = 1000;
        private ConsoleConfig consoleConfig = ConsoleConfig.disabled();
        private SecurityConfig securityConfig = SecurityConfig.disabled();

        public Builder storageEngine(StorageEngineType engine) {
            this.storageEngine = engine;
            return this;
        }

        @Deprecated(forRemoval = true)
        public Builder storageEngine(StorageEngine engine) {
            throw new UnsupportedOperationException(
                "Passing a raw StorageEngine instance is no longer supported. " +
                "Use storageEngine(StorageEngineType) instead."
            );
        }

        public Builder persistTo(String path) {
            this.dataDir = Paths.get(path);
            return this;
        }

        public Builder dataDir(Path path) {
            this.dataDir = path;
            return this;
        }

        public Builder dataDir(String path) {
            this.dataDir = Paths.get(path);
            return this;
        }

        public Builder autoFlush(boolean autoFlush) {
            this.autoFlush = autoFlush;
            return this;
        }

        public Builder flushIntervalMs(int ms) {
            this.flushIntervalMs = ms;
            return this;
        }

        public Builder console(ConsoleConfig consoleConfig) {
            this.consoleConfig = consoleConfig != null ? consoleConfig : ConsoleConfig.disabled();
            return this;
        }

        public Builder enableConsole(int port) {
            this.consoleConfig = ConsoleConfig.builder().enabled(true).port(port).build();
            return this;
        }

        public Builder security(SecurityConfig securityConfig) {
            this.securityConfig = securityConfig != null ? securityConfig : SecurityConfig.disabled();
            return this;
        }

        public JunifyDBConfig buildConfig() {
            return new JunifyDBConfig(storageEngine, dataDir, autoFlush, flushIntervalMs, consoleConfig, securityConfig);
        }

        public JunifyDB build() {
            return buildConfig().build();
        }
    }
}
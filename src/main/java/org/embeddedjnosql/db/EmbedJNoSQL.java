package org.embeddedjnosql.db;

import org.embeddedjnosql.db.nosql.column.ColumnFamily;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.embeddedjnosql.db.console.http.EmbedJNoSQLServer;
import org.embeddedjnosql.db.core.cdc.CDCManager;
import org.embeddedjnosql.db.core.event.EventBus;
import org.embeddedjnosql.db.core.metrics.DatabaseMetrics;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.kv.HashBucket;
import org.embeddedjnosql.db.nosql.kv.KeyValueBucket;
import org.embeddedjnosql.db.nosql.kv.ListBucket;
import org.embeddedjnosql.db.nosql.kv.SetBucket;

import org.embeddedjnosql.db.storage.spi.StorageEngine;
import org.embeddedjnosql.db.transaction.mvcc.MVCCManager;
import org.embeddedjnosql.db.transaction.mvcc.Transaction;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class EmbedJNoSQL implements Closeable {

    private final EmbedJNoSQLConfig config;
    private final StorageEngine engine;
    private final MVCCManager mvcc;
    private final ConcurrentMap<String, DocumentCollection> collections;
    private final ConcurrentMap<String, KeyValueBucket> buckets;
    private final ConcurrentMap<String, ListBucket> listBuckets;
    private final ConcurrentMap<String, SetBucket> setBuckets;
    private final ConcurrentMap<String, HashBucket> hashBuckets;
    private final ConcurrentMap<String, ColumnFamily> columnFamilies;
    private final EventBus eventBus;
    private final DatabaseMetrics metrics;
    private final CDCManager cdcManager;
    private volatile boolean closed;
    private EmbedJNoSQLServer server;

    private EmbedJNoSQL(EmbedJNoSQLConfig config) {
        this.config = config;
        this.engine = config.storageEngine().create(config.dataDir(), config.autoFlush(), config.flushIntervalMs());
        this.mvcc = new MVCCManager();
        this.collections = new ConcurrentHashMap<>();
        this.buckets = new ConcurrentHashMap<>();
        this.listBuckets = new ConcurrentHashMap<>();
        this.setBuckets = new ConcurrentHashMap<>();
        this.hashBuckets = new ConcurrentHashMap<>();
        this.columnFamilies = new ConcurrentHashMap<>();
        this.eventBus = new EventBus();
        this.metrics = new DatabaseMetrics();
        this.cdcManager = new CDCManager();
        // Feed document change events into CDC so the change feed has a real
        // producer (previously the CDC subsystem had no write-path wiring).
        var cdcListener = cdcManager.changeListener();
        // System-listener channel: survives user `eventBus.clear()` and keeps
        // internal subsystems out of user-visible listener counts.
        this.eventBus.onSystem(EventBus.EventType.AFTER_INSERT, cdcListener);
        this.eventBus.onSystem(EventBus.EventType.AFTER_UPDATE, cdcListener);
        this.eventBus.onSystem(EventBus.EventType.AFTER_DELETE, cdcListener);
        this.closed = false;
    }

    public static EmbedJNoSQLConfig.Builder embed() {
        return EmbedJNoSQLConfig.builder();
    }

    /**
     * Creates an ultra-fast, zero-configuration in-memory embedded database.
     * Ideal for unit tests, rapid prototyping, and ephemeral services.
     */
    public static EmbedJNoSQL inMemory() {
        return EmbedJNoSQLConfig.builder()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY)
                .build();
    }

    /**
     * Creates a temporary file-based embedded database in java.io.tmpdir
     * with an automated JVM shutdown hook for directory cleanup.
     */
    public static EmbedJNoSQL openTemp() {
        try {
            var tempDir = java.nio.file.Files.createTempDirectory("embedjnosql-temp-");
            var db = EmbedJNoSQLConfig.builder()
                    .storageEngine(EmbedJNoSQLConfig.StorageEngineType.FILE)
                    .dataDir(tempDir.toString())
                    .build();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    db.close();
                    deleteDirectoryRecursively(tempDir.toFile());
                } catch (Exception ignored) {}
            }));
            return db;
        } catch (java.io.IOException e) {
            throw new org.embeddedjnosql.db.core.exception.StorageException("Failed to create temporary EmbedJNoSQL: " + e.getMessage(), e);
        }
    }

    private static void deleteDirectoryRecursively(java.io.File dir) {
        if (dir == null || !dir.exists()) return;
        var files = dir.listFiles();
        if (files != null) {
            for (var f : files) {
                if (f.isDirectory()) deleteDirectoryRecursively(f);
                else f.delete();
            }
        }
        dir.delete();
    }

    public static EmbedJNoSQL create(EmbedJNoSQLConfig config) {
        var db = new EmbedJNoSQL(config);
        db.materializePersistedCollections();
        if (config.consoleConfig() != null && config.consoleConfig().enabled()) {
            try {
                db.startConsoleServer(config.consoleConfig(), config.securityConfig());
            } catch (java.io.IOException e) {
                throw new org.embeddedjnosql.db.core.exception.StorageException("Failed to start EmbedJNoSQL console server: " + e.getMessage(), e);
            }
        }
        return db;
    }

    public DocumentCollection documentCollection(String name) {
        checkOpen();
        var col = collections.computeIfAbsent(name, n -> {
            eventBus.emit(EventBus.EventType.COLLECTION_CREATED, n);
            var collection = new DocumentCollection(n, engine, eventBus, metrics, null, config.dataDir());
            collection.loadIndexes();
            // A collection's existence is durable from the moment it is created, so an
            // empty collection is still there after a restart even though it holds no
            // documents. Engines with no empty-collection identity (see the SPI contract) are
            // reported by ensureCollection() returning false, which costs the collection its
            // durability but is stated in the defect register rather than hidden.
            try {
                engine.ensureCollection(n);
            } catch (Exception e) {
                System.err.println("[EmbedJNoSQL] could not persist the existence of collection '"
                        + n + "': " + e.getMessage());
            }
            return collection;
        });
        metrics.updateCollectionSize(name, col.count());
        return col;
    }

    public java.util.Set<String> getCollectionNames() {
        checkOpen();
        return java.util.Collections.unmodifiableSet(collections.keySet());
    }

    /**
     * Re-exposes collections that exist in the storage engine from a previous
     * run. Without this, data persisted by an earlier process (via any model)
     * is invisible to {@link #documentCollection(String)} callers after a
     * restart until they happen to request the collection by name.
     *
     * <p><b>R-53 (2026-09-22):</b> this used to enumerate
     * {@code engine.collectionNames()} — an SPI method whose default returns an
     * empty set and which only {@code FileEngine} overrides. On LSM_TREE and
     * B_TREE the data survived a restart on disk (WAL/SSTables, index file) but
     * was <b>unreachable through every listing API</b>: {@code /api/collections}
     * showed an empty catalog, backups and the console saw nothing, and the data came
     * back only if a client happened to request the exact collection name.
     * Enumerate {@code collections()} instead — the live-and-persisted set that
     * every engine implements — with {@code collectionNames()} as a fallback
     * union for engines that report persisted identity only there.</p>
     */
    private void materializePersistedCollections() {
        java.util.LinkedHashSet<String> names = new java.util.LinkedHashSet<>();
        try {
            names.addAll(engine.collections());
        } catch (Exception e) {
            System.err.println("[EmbedJNoSQL] collection discovery failed (continuing): " + e.getMessage());
        }
        try {
            names.addAll(engine.collectionNames());
        } catch (Exception e) {
            // collectionNames() may be unsupported; collections() already covered it.
        }
        for (String name : names) {
            if (!collections.containsKey(name)) {
                documentCollection(name);
            }
        }
    }

    /**
     * Starts a fluent, type-safe entity query builder for the given entity class.
     *
     * <p>Predicates are evaluated by the native document engine — there is no
     * separate query engine behind this API.</p>
     */
    public <T> org.embeddedjnosql.db.api.EntityQuery<T> from(Class<T> entityClass) {
        checkOpen();
        return new org.embeddedjnosql.db.api.EntityQuery<>(this, entityClass);
    }

    /**
     * Eagerly registers one or more entity classes, creating collections
     * and secondary indexes based on annotations.
     */
    public void registerEntity(Class<?>... entityClasses) {
        checkOpen();
        if (entityClasses == null) return;
        for (Class<?> clazz : entityClasses) {
            if (clazz == null) continue;
            String colName = org.embeddedjnosql.db.adapter.jnosql.EntityMapper.getCollectionName(clazz);
            var col = documentCollection(colName);
            for (var field : clazz.getDeclaredFields()) {
                if (org.embeddedjnosql.db.adapter.jnosql.EntityMapper.isNaturalId(field)) {
                    String colFieldName = org.embeddedjnosql.db.adapter.jnosql.EntityMapper.resolveColumnName(field);
                    col.createIndex(colFieldName);
                }
            }
        }
    }

    /**
     * The live storage engine backing this database. Admin surfaces (backup, diagnostics)
     * must use this instance rather than constructing a new engine: a fresh engine reports
     * none of the live collections, which silently turns a backup into an empty file.
     */
    public StorageEngine storageEngine() {
        return engine;
    }

    public KeyValueBucket keyValueBucket(String name) {
        checkOpen();
        return buckets.computeIfAbsent(name, n -> {
            eventBus.emit(EventBus.EventType.BUCKET_CREATED, n);
            return new KeyValueBucket(n, engine, eventBus, metrics);
        });
    }

    public ColumnFamily columnFamily(String name) {
        checkOpen();
        return columnFamilies.computeIfAbsent(name, n -> new ColumnFamily(n, engine));
    }

    public ListBucket listBucket(String name) {
        checkOpen();
        return listBuckets.computeIfAbsent(name, n -> {
            eventBus.emit(EventBus.EventType.BUCKET_CREATED, n);
            return new ListBucket(n, engine, eventBus, metrics);
        });
    }

    public SetBucket setBucket(String name) {
        checkOpen();
        return setBuckets.computeIfAbsent(name, n -> {
            eventBus.emit(EventBus.EventType.BUCKET_CREATED, n);
            return new SetBucket(n, engine, eventBus, metrics);
        });
    }

    public HashBucket hashBucket(String name) {
        checkOpen();
        return hashBuckets.computeIfAbsent(name, n -> {
            eventBus.emit(EventBus.EventType.BUCKET_CREATED, n);
            return new HashBucket(n, engine, eventBus, metrics);
        });
    }

    public Transaction beginTransaction() {
        checkOpen();
        metrics.recordTransaction();
        // Transactions write straight to the engine, so the catalog never learned about
        // the collections they touched and getCollectionNames() omitted them — which then made
        // committed data invisible to backups, listings, and the console. The transaction
        // reports its collections on successful commit.
        return new Transaction(engine, eventBus, metrics, mvcc).onCommit(this::registerCommittedCollection);
    }

    /**
     * Registers a collection that a committed transaction has written data into (R-59).
     * Resolving through {@link #documentCollection(String)} is deliberate: it creates the
     * database-level wrapper (and loads its indexes) exactly as a normal write path would.
     */
    private void registerCommittedCollection(String name) {
        if (!collections.containsKey(name)) {
            documentCollection(name);
        }
    }

    public MVCCManager mvcc() {
        return mvcc;
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public DatabaseMetrics metrics() {
        return metrics;
    }

    public CDCManager cdcManager() {
        return cdcManager;
    }

    public EmbedJNoSQLServer startServer(int port) throws IOException {
        checkOpen();
        if (server != null) {
            server.stop();
        }
        server = new EmbedJNoSQLServer(this);
        // R-61: always apply the security config, not only when auth is enabled.
        // Applying it only for authenticated servers left the unauthenticated default
        // path on EmbedJNoSQLServer's field defaults (corsEnabled=true, origins="*"),
        // contradicting SecurityConfig.disabled()'s documented "secure default: CORS
        // disabled" and making the embedjnosql.security.cors-enabled setting unreachable
        // without an API key.
        server.applySecurityConfig(config.securityConfig() != null
                ? config.securityConfig()
                : org.embeddedjnosql.db.config.SecurityConfig.disabled());
        server.start(port);
        return server;
    }

    public EmbedJNoSQLServer startConsoleServer(org.embeddedjnosql.db.config.ConsoleConfig consoleConfig, org.embeddedjnosql.db.config.SecurityConfig securityConfig) throws IOException {
        checkOpen();
        if (server != null) {
            server.stop();
        }
        server = new EmbedJNoSQLServer(this);
        if (securityConfig != null) {
            server.applySecurityConfig(securityConfig);
        }
        server.startIntelligent(consoleConfig != null ? consoleConfig : org.embeddedjnosql.db.config.ConsoleConfig.builder().enabled(true).build());
        return server;
    }

    public EmbedJNoSQLServer server() {
        return server;
    }

    public EmbedJNoSQLServer consoleServer() {
        return server;
    }

    public String consoleUrl() {
        return server != null ? server.getConsoleUrl() : null;
    }

    public int consolePort() {
        return server != null ? server.port() : -1;
    }

    public EmbedJNoSQLConfig config() {
        return config;
    }

    public boolean isOpen() {
        return !closed;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        if (!closed) {
            if (server != null) server.stop();
            engine.flush();
            engine.close();
            closed = true;
        }
    }

    public void flush() {
        engine.flush();
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("EmbedJNoSQL database is closed");
        }
    }

    public static void main(String[] args) {
        try {
            launch(args);
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Run with --help to list supported options.");
            System.exit(2);
        }
    }

    /**
     * Rejects an unrecognized command-line option instead of silently ignoring it.
     * A silently dropped flag (for example {@code --storage} or {@code --password})
     * would leave the server running with defaults the operator did not choose.
     *
     * @throws IllegalArgumentException when the option is not supported
     */
    static void validateOption(String option) {
        switch (option) {
            case "--port", "--data-dir", "--engine", "--sync", "--async", "--flush-interval",
                 "--api-key", "--ssl-port", "--ssl-keystore", "--ssl-keypass", "--help" -> { }
            default -> throw new IllegalArgumentException(
                    "Unknown option: " + option);
        }
    }

    /** Reads the value following {@code option}, failing fast when it is absent. */
    static String value(String option, String[] args, int index) {
        if (index >= args.length) {
            throw new IllegalArgumentException("Missing value for " + option);
        }
        return args[index];
    }

    /** Reads a numeric value following {@code option}, failing fast when it is not a number. */
    static int intValue(String option, String[] args, int index) {
        String raw = value(option, args, index);
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid number for " + option + ": " + raw);
        }
    }

    /** Normalizes the {@code --engine} value, rejecting unsupported engine names. */
    static String engineValue(String option, String[] args, int index) {
        String raw = value(option, args, index);
        return switch (raw.toUpperCase()) {
            case "FILE" -> "FILE";
            case "IN_MEMORY" -> "IN_MEMORY";
            case "LSM_TREE" -> "LSM_TREE";
            case "B_TREE" -> "B_TREE";
            default -> throw new IllegalArgumentException(
                    "Unsupported engine: " + raw + " (expected FILE, IN_MEMORY, LSM_TREE or B_TREE)");
        };
    }

    /** Standalone server entry point body. */
    private static void launch(String[] args) {
        int port = 8080;
        String dataDir = "data";
        String engineType = "FILE";
        boolean autoFlush = true;
        int flushInterval = 1000;
        String apiKey = null;
        int sslPort = -1;
        String sslKeystorePath = null;
        String sslKeystorePassword = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--port" -> port = intValue("--port", args, ++i);
                case "--data-dir" -> dataDir = value("--data-dir", args, ++i);
                case "--engine" -> engineType = engineValue("--engine", args, ++i);
                case "--sync" -> autoFlush = true;
                case "--async" -> autoFlush = false;
                case "--flush-interval" -> flushInterval = intValue("--flush-interval", args, ++i);
                case "--api-key" -> apiKey = value("--api-key", args, ++i);
                case "--ssl-port" -> sslPort = intValue("--ssl-port", args, ++i);
                case "--ssl-keystore" -> sslKeystorePath = value("--ssl-keystore", args, ++i);
                case "--ssl-keypass" -> sslKeystorePassword = value("--ssl-keypass", args, ++i);
                default -> validateOption(args[i]);
                case "--help" -> {
                    System.out.println("Usage: java -jar embed-jnosql-core.jar [options]");
                    System.out.println("Options:");
                    System.out.println("  --port <port>          Server port (default: 8080)");
                    System.out.println("  --data-dir <dir>       Data directory (default: data)");
                    System.out.println("  --engine <type>         Storage engine: FILE, IN_MEMORY, LSM_TREE, B_TREE (default: FILE)");
                    System.out.println("  --sync                 Enable synchronous flush (default)");
                    System.out.println("  --async                Enable asynchronous flush");
                    System.out.println("  --flush-interval <ms>  Flush interval in ms (default: 1000)");
                    System.out.println("  --api-key <key>        API key for authentication (optional)");
                    System.out.println("  --ssl-port <port>      SSL/TLS port (optional, requires --ssl-keystore)");
                    System.out.println("  --ssl-keystore <path>  Path to JKS keystore file (optional)");
                    System.out.println("  --ssl-keypass <pass>   Keystore password (optional)");
                    System.out.println("  --help                 Show this help");
                    return;
                }
            }
        }

        var config = EmbedJNoSQL.embed()
                .storageEngine(switch (engineType.toUpperCase()) {
                    case "FILE" -> EmbedJNoSQLConfig.StorageEngineType.FILE;
                    case "LSM_TREE" -> EmbedJNoSQLConfig.StorageEngineType.LSM_TREE;
                    case "B_TREE" -> EmbedJNoSQLConfig.StorageEngineType.B_TREE;
                    default -> EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY;
                })
                .persistTo(dataDir)
                .autoFlush(autoFlush)
                .flushIntervalMs(flushInterval)
                .buildConfig();

        var db = EmbedJNoSQL.create(config);
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down...");
            db.close();
        }));

        try {
            System.out.println("Starting EmbedJNoSQL server on port " + port + "...");
            System.out.println("Data directory: " + dataDir);
            System.out.println("Storage engine: " + engineType);
            System.out.println("Flush mode: " + (autoFlush ? "sync" : "async"));
            var server = db.startServer(port);
            if (apiKey != null && !apiKey.isEmpty()) {
                server.setApiKey(apiKey);
                System.out.println("API authentication enabled");
            }
            // Configure SSL if specified
            if (sslPort > 0 && sslKeystorePath != null) {
                server.configureSsl(sslPort, sslKeystorePath, sslKeystorePassword != null ? sslKeystorePassword : "");
                System.out.println("SSL/TLS enabled on port " + sslPort);
            }
            System.out.println("Server started successfully!");
            System.out.println("API available at http://localhost:" + port + "/api");
            System.out.println("Health check at http://localhost:" + port + "/api/health");
            
            Thread.currentThread().join();
        } catch (IOException e) {
            System.err.println("Failed to start server: " + e.getMessage());
            db.close();
            System.exit(1);
        } catch (InterruptedException e) {
            System.out.println("Server interrupted, shutting down...");
            db.close();
        }
    }
}
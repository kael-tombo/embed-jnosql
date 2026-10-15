package org.embeddedjnosql.db.console;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.config.ConsoleConfig;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.embeddedjnosql.db.config.SecurityConfig;
import org.embeddedjnosql.db.nosql.column.ColumnFamily;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.kv.HashBucket;
import org.embeddedjnosql.db.nosql.kv.KeyValueBucket;
import org.embeddedjnosql.db.nosql.kv.ListBucket;
import org.embeddedjnosql.db.nosql.kv.SetBucket;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Standalone runner for live Console UI validation and browser testing.
 * Binds on a caller-selected loopback port with an isolated data directory,
 * and seeds initial multi-model data across Document, KV, and Column data models.
 */
public class ConsoleInteractiveTestServer {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Expected arguments: <port> <data-directory>");
        }
        int port = Integer.parseInt(args[0]);
        Path dataDirectory = Path.of(args[1]).toAbsolutePath().normalize();
        Files.createDirectories(dataDirectory);

        String adminUser = requiredEnvironmentVariable("E2E_ADMIN_USER");
        String adminPassword = requiredEnvironmentVariable("E2E_ADMIN_PASSWORD");
        String apiKey = requiredEnvironmentVariable("E2E_API_KEY");

        System.out.println("Starting EmbedJNoSQL Console Interactive Test Server...");

        EmbedJNoSQL db = EmbedJNoSQL.embed()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.FILE)
                .dataDir(dataDirectory)
                .console(ConsoleConfig.builder()
                        .enabled(true)
                        .port(port)
                        .host("127.0.0.1")
                        .intelligentPort(false)
                        .build())
                .security(SecurityConfig.builder()
                        .authEnabled(true)
                        .adminUsername(adminUser)
                        .adminPassword(adminPassword)
                        .apiKey(apiKey)
                        .csrfEnabled(true)
                        .rateLimitEnabled(false)
                        .bruteForceProtectionEnabled(true)
                        .maxFailedLoginAttempts(3)
                        .lockoutDurationMs(4000L) // 4-second lockout for rapid testing
                        .securityHeadersEnabled(true)
                        .build())
                .build();

        int actualPort = db.consolePort();
        System.out.println("EmbedJNoSQL Console active at http://127.0.0.1:" + actualPort + "/");

        // Seed Document collections
        DocumentCollection products = db.documentCollection("products");
        products.createIndex("category");
        products.createIndex("price");

        Document p1 = new Document()
                .id("prod-101")
                .add("name", "Quantum Wireless Headphones")
                .add("category", "Electronics")
                .add("price", 199.99)
                .add("stock", 45)
                .add("rating", 4.8);
        Document p2 = new Document()
                .id("prod-102")
                .add("name", "Ergonomic Mechanical Keyboard")
                .add("category", "Electronics")
                .add("price", 129.50)
                .add("stock", 80)
                .add("rating", 4.9);
        Document p3 = new Document()
                .id("prod-103")
                .add("name", "Titanium Smart Watch")
                .add("category", "Wearables")
                .add("price", 349.00)
                .add("stock", 25)
                .add("rating", 4.7);

        products.insert(p1);
        products.insert(p2);
        products.insert(p3);

        DocumentCollection users = db.documentCollection("users");
        users.insert(new Document().id("user-1").add("name", "Alice Martin").add("role", "Administrator").add("active", true));
        users.insert(new Document().id("user-2").add("name", "Bob Vance").add("role", "Auditor").add("active", true));

        // Seed Key-Value buckets
        KeyValueBucket kv = db.keyValueBucket("cache_store");
        kv.put("app.name", "EmbedJNoSQL Enterprise Edition");
        kv.put("system.status", "Operational");
        kv.put("license.tier", "Commercial-Pro");

        HashBucket userHash = db.hashBucket("user_profile_hash");
        userHash.hset("user_101", "name", "Alice");
        userHash.hset("user_101", "department", "Engineering");
        userHash.hset("user_101", "location", "Paris HQ");

        ListBucket list = db.listBucket("event_stream_list");
        list.rpush("event_queue", "AUTH_LOGIN_SUCCESS");
        list.rpush("event_queue", "COLLECTION_CREATED");
        list.rpush("event_queue", "METRICS_COLLECTED");

        SetBucket tagSet = db.setBucket("system_tags_set");
        tagSet.sadd("cluster_tags", "production", "eu-west", "high-availability");

        // Seed Column Family
        ColumnFamily cf = db.columnFamily("server_telemetry");
        cf.put("host-srv-01", "cpu_load", "14.2%");
        cf.put("host-srv-01", "ram_used_mb", "4096");
        cf.put("host-srv-01", "os_kernel", "Linux 6.8-generic");

        // Seed Document Collection
        try {
            var inventory = db.documentCollection("inventory");
            inventory.insert(org.embeddedjnosql.db.nosql.document.Document.of("item_name", "High-Speed NVMe Drive")
                    .add("qty", 150).add("unit_price", 89.99).id("INV-01"));
            inventory.insert(org.embeddedjnosql.db.nosql.document.Document.of("item_name", "DDR5 32GB RAM Stick")
                    .add("qty", 300).add("unit_price", 119.50).id("INV-02"));
            inventory.insert(org.embeddedjnosql.db.nosql.document.Document.of("item_name", "4K UHD IPS Monitor")
                    .add("qty", 75).add("unit_price", 299.00).id("INV-03"));
        } catch (Exception e) {
            System.err.println("Document seed note: " + e.getMessage());
        }

        System.out.println("E2E_SERVER_READY http://127.0.0.1:" + actualPort + "/");

        AtomicBoolean closed = new AtomicBoolean();
        Runnable closeDatabase = () -> {
            if (closed.compareAndSet(false, true)) {
                try { db.close(); } catch (Exception ignored) {}
            }
        };
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down test server...");
            closeDatabase.run();
        }));

        Thread shutdownSignal = new Thread(() -> {
            try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in))) {
                if ("STOP".equals(input.readLine())) {
                    closeDatabase.run();
                    System.exit(0);
                }
            } catch (Exception ignored) {}
        });
        shutdownSignal.setDaemon(true);
        shutdownSignal.start();

        while (true) {
            Thread.sleep(1000);
        }
    }

    private static String requiredEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required environment variable: " + name);
        }
        return value;
    }
}

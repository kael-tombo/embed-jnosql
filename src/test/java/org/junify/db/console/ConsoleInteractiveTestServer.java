package org.junify.db.console;

import org.junify.db.JunifyDB;
import org.junify.db.config.ConsoleConfig;
import org.junify.db.config.JunifyDBConfig;
import org.junify.db.config.SecurityConfig;
import org.junify.db.nosql.column.ColumnFamily;
import org.junify.db.nosql.document.Document;
import org.junify.db.nosql.document.DocumentCollection;
import org.junify.db.nosql.kv.HashBucket;
import org.junify.db.nosql.kv.KeyValueBucket;
import org.junify.db.nosql.kv.ListBucket;
import org.junify.db.nosql.kv.SetBucket;

import java.io.File;
import java.io.FileWriter;
import java.util.Map;

/**
 * Standalone runner for live Console UI validation and browser testing.
 * Binds on port 9095 (or fallback) with security and console enabled,
 * and seeds initial multi-model data across Document, KV, Column, and Vector data models.
 */
public class ConsoleInteractiveTestServer {

    public static final int PREFERRED_PORT = 9095;
    public static final String ADMIN_USER = "admin";
    public static final String ADMIN_PASS = "Admin123456!#";
    public static final String API_KEY = "junify-console-api-key-2026";

    public static void main(String[] args) throws Exception {
        System.out.println("Starting JunifyDB Console Interactive Test Server...");

        JunifyDB db = JunifyDB.embed()
                .storageEngine(JunifyDBConfig.StorageEngineType.IN_MEMORY)
                .console(ConsoleConfig.builder()
                        .enabled(true)
                        .port(PREFERRED_PORT)
                        .host("127.0.0.1")
                        .intelligentPort(true)
                        .build())
                .security(SecurityConfig.builder()
                        .authEnabled(true)
                        .adminUsername(ADMIN_USER)
                        .adminPassword(ADMIN_PASS)
                        .apiKey(API_KEY)
                        .csrfEnabled(true)
                        .rateLimitEnabled(false)
                        .bruteForceProtectionEnabled(true)
                        .maxFailedLoginAttempts(3)
                        .lockoutDurationMs(4000L) // 4-second lockout for rapid testing
                        .securityHeadersEnabled(true)
                        .build())
                .build();

        int actualPort = db.consolePort();
        System.out.println("JunifyDB Console active at http://127.0.0.1:" + actualPort + "/");

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
        kv.put("app.name", "JunifyDB Enterprise Edition");
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
            inventory.insert(org.junify.db.nosql.document.Document.of("item_name", "High-Speed NVMe Drive")
                    .add("qty", 150).add("unit_price", 89.99).id("INV-01"));
            inventory.insert(org.junify.db.nosql.document.Document.of("item_name", "DDR5 32GB RAM Stick")
                    .add("qty", 300).add("unit_price", 119.50).id("INV-02"));
            inventory.insert(org.junify.db.nosql.document.Document.of("item_name", "4K UHD IPS Monitor")
                    .add("qty", 75).add("unit_price", 299.00).id("INV-03"));
        } catch (Exception e) {
            System.err.println("Document seed note: " + e.getMessage());
        }

        // Record server details to a marker file
        File marker = new File("target/console-test-server.ready");
        try (FileWriter fw = new FileWriter(marker)) {
            fw.write("port=" + actualPort + "\n");
            fw.write("username=" + ADMIN_USER + "\n");
            fw.write("password=" + ADMIN_PASS + "\n");
            fw.write("apiKey=" + API_KEY + "\n");
        }

        System.out.println("SERVER_READY on port " + actualPort);

        // Keep alive until interrupted or shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down test server...");
            try { db.close(); } catch (Exception ignored) {}
            if (marker.exists()) marker.delete();
        }));

        while (true) {
            Thread.sleep(1000);
        }
    }
}

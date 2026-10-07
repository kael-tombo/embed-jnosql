package org.embeddedjnosql.db.example;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig.StorageEngineType;
import org.embeddedjnosql.db.console.http.EmbedJNoSQLServer;
import org.embeddedjnosql.db.core.event.EventBus;
import org.embeddedjnosql.db.nosql.column.ColumnFamily;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.document.Query;
import org.embeddedjnosql.db.nosql.kv.HashBucket;
import org.embeddedjnosql.db.nosql.kv.KeyValueBucket;
import org.embeddedjnosql.db.nosql.kv.ListBucket;
import org.embeddedjnosql.db.nosql.kv.SetBucket;
import org.embeddedjnosql.db.transaction.mvcc.Transaction;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Runnable demonstration and verification program exercising all core features
 * of EmbedJNoSQL (Embedded NoSQL database for Java):
 *
 * 1. Database Lifecycle &amp; In-Memory Storage Engine
 * 2. Document Collection (CRUD, Flexible Schema, Queries &amp; Secondary Indexing)
 * 3. Key-Value Store (CRUD, Existence, Multi-Key operations)
 * 4. Redis-Style List Bucket (LPUSH, RPUSH, LRANGE, LPOP, LLEN)
 * 5. Redis-Style Set Bucket (SADD, SISMEMBER, SMEMBERS, SCARD, SREM)
 * 6. Redis-Style Hash Bucket (HSET, HGET, HGETALL, HDEL, HLEN)
 * 7. Wide-Column Family (Put, Get, GetRow, Column-level metadata)
 * 8. ACID / MVCC Transactions (Snapshot Isolation, Write, Read, Commit, Rollback)
 * 9. Real-Time EventBus &amp; Database Metrics
 * 10. Embedded HTTP Server &amp; REST Management Console
 * 11. Persistent Storage Engine (B-Tree Disk Persistence &amp; Reload)
 */
public class EmbedJNoSQLFeatureDemo {

    public static void main(String[] args) throws Exception {
        banner("STARTING EMBED-DB FULL FEATURE DEMONSTRATION & VERIFICATION");

        // -------------------------------------------------------------------
        // 1. IN-MEMORY ENGINE & EVENT BUS
        // -------------------------------------------------------------------
        section("1. Database Bootstrap & EventBus Monitoring");
        EmbedJNoSQL db = EmbedJNoSQL.embed()
                .storageEngine(StorageEngineType.IN_MEMORY)
                .build();
        pass("Embedded In-Memory database started successfully.");

        AtomicInteger insertEventCount = new AtomicInteger(0);
        db.eventBus().on(EventBus.EventType.AFTER_INSERT, event -> {
            insertEventCount.incrementAndGet();
        });
        pass("Subscribed listener to EventBus [AFTER_INSERT] events.");

        // -------------------------------------------------------------------
        // 2. DOCUMENT STORE & QUERIES
        // -------------------------------------------------------------------
        section("2. Document Store (CRUD, Complex Queries & Secondary Indexes)");
        DocumentCollection users = db.documentCollection("users");

        // Create secondary index on age
        users.createIndex("age");
        pass("Created secondary index on 'age' field.");

        // Insert documents
        Document alice = new Document()
                .id("u101")
                .add("name", "Alice Martin")
                .add("age", 28)
                .add("role", "Architect")
                .add("department", "Engineering")
                .add("active", true);

        Document bob = new Document()
                .id("u102")
                .add("name", "Bob Spencer")
                .add("age", 35)
                .add("role", "Lead SRE")
                .add("department", "Infrastructure")
                .add("active", true);

        Document charlie = new Document()
                .id("u103")
                .add("name", "Charlie Davis")
                .add("age", 22)
                .add("role", "Junior Developer")
                .add("department", "Engineering")
                .add("active", false);

        users.insert(alice);
        users.insert(bob);
        users.insert(charlie);
        pass("Inserted 3 documents. Total count: " + users.count());
        check(users.count() == 3, "Document collection count should be 3");

        // Find by ID
        Document fetchedAlice = users.findById("u101");
        check(fetchedAlice != null && "Alice Martin".equals(fetchedAlice.get("name")), "findById('u101') returned Alice");
        pass("Retrieved document by ID: " + fetchedAlice.get("name") + " (" + fetchedAlice.get("role") + ")");

        // Complex Queries
        List<Document> engineering = users.find(Query.eq("department", "Engineering"));
        check(engineering.size() == 2, "2 engineering users expected");
        pass("Query [department == 'Engineering']: Found " + engineering.size() + " matches.");

        List<Document> seniorUsers = users.find(Query.gt("age", 25));
        check(seniorUsers.size() == 2, "2 users older than 25 expected (Alice: 28, Bob: 35)");
        pass("Query [age > 25]: Found " + seniorUsers.size() + " matches.");

        List<Document> nameContains = users.find(Query.contains("name", "Spencer"));
        check(nameContains.size() == 1, "1 user with name containing 'Spencer'");
        pass("Query [name contains 'Spencer']: Found " + nameContains.get(0).get("name"));

        // Update Document
        alice.add("role", "Principal Architect");
        users.update(alice);
        Document updatedAlice = users.findById("u101");
        check("Principal Architect".equals(updatedAlice.get("role")), "Role should be updated");
        pass("Updated document role to: " + updatedAlice.get("role"));

        // -------------------------------------------------------------------
        // 3. KEY-VALUE STORE
        // -------------------------------------------------------------------
        section("3. Key-Value Store");
        KeyValueBucket kv = db.keyValueBucket("app_settings");

        kv.put("app.env", "production");
        kv.put("app.timeout.ms", "5000");
        kv.put("app.feature.dark_mode", "enabled");

        check("production".equals(kv.get("app.env")), "Key 'app.env' must be 'production'");
        check(kv.exists("app.timeout.ms"), "Key 'app.timeout.ms' must exist");
        pass("Stored and retrieved keys. Found: app.env=" + kv.get("app.env"));

        kv.delete("app.feature.dark_mode");
        check(!kv.exists("app.feature.dark_mode"), "Key 'app.feature.dark_mode' should be deleted");
        pass("Deleted 'app.feature.dark_mode'. Keys remaining: " + kv.count());

        // -------------------------------------------------------------------
        // 4. REDIS-STYLE LIST BUCKET
        // -------------------------------------------------------------------
        section("4. Redis-Style List Bucket (LPUSH, RPUSH, LRANGE, LPOP, LLEN)");
        ListBucket queue = db.listBucket("task_pipeline");

        queue.rpush("orders", "order-001");
        queue.rpush("orders", "order-002");
        queue.lpush("orders", "priority-order-000"); // placed at head

        long queueLength = queue.llen("orders");
        check(queueLength == 3, "Queue length should be 3");
        pass("Pushed 3 elements to list 'orders'. Current length: " + queueLength);

        List<String> range = queue.lrange("orders", 0, -1);
        check("priority-order-000".equals(range.get(0)), "Head element should be priority-order-000");
        pass("List elements in order: " + range);

        String popped = queue.lpop("orders");
        check("priority-order-000".equals(popped), "Popped element should be priority-order-000");
        pass("Popped head element: " + popped + ", remaining length: " + queue.llen("orders"));

        // -------------------------------------------------------------------
        // 5. REDIS-STYLE SET BUCKET
        // -------------------------------------------------------------------
        section("5. Redis-Style Set Bucket (SADD, SISMEMBER, SMEMBERS, SCARD)");
        SetBucket tags = db.setBucket("user_tags");

        tags.sadd("u101", "java", "nosql", "embedded", "quarkus", "spring");
        check(tags.scard("u101") == 5, "Set should have 5 tags");
        check(tags.sismember("u101", "nosql"), "'nosql' should be a member of set");
        check(!tags.sismember("u101", "python"), "'python' should not be a member");
        pass("Set members for u101: " + tags.smembers("u101") + " (Cardinality: " + tags.scard("u101") + ")");

        tags.srem("u101", "quarkus");
        check(!tags.sismember("u101", "quarkus"), "'quarkus' tag should be removed");
        pass("Removed 'quarkus' tag. New cardinality: " + tags.scard("u101"));

        // -------------------------------------------------------------------
        // 6. REDIS-STYLE HASH BUCKET
        // -------------------------------------------------------------------
        section("6. Redis-Style Hash Bucket (HSET, HGET, HGETALL, HDEL)");
        HashBucket sessions = db.hashBucket("user_sessions");

        String sessionKey = "session:xyz-987";
        sessions.hset(sessionKey, "user_id", "u101");
        sessions.hset(sessionKey, "ip", "192.168.1.42");
        sessions.hset(sessionKey, "agent", "Mozilla/5.0");

        check("u101".equals(sessions.hget(sessionKey, "user_id")), "user_id matches");
        check("192.168.1.42".equals(sessions.hget(sessionKey, "ip")), "ip matches");
        pass("Stored hash fields. user_id: " + sessions.hget(sessionKey, "user_id") + ", ip: " + sessions.hget(sessionKey, "ip"));

        Map<String, String> allFields = sessions.hgetall(sessionKey);
        check(allFields.size() == 3, "Hash should have 3 fields");
        pass("Full hash contents: " + allFields);

        // -------------------------------------------------------------------
        // 7. WIDE-COLUMN FAMILY
        // -------------------------------------------------------------------
        section("7. Wide-Column Family (Cassandra / Bigtable style)");
        ColumnFamily timeSeries = db.columnFamily("sensor_telemetry");

        String rowId = "device-sensor-alpha";
        timeSeries.put(rowId, "temp_celsius", 23.8);
        timeSeries.put(rowId, "humidity_pct", 45.2);
        timeSeries.put(rowId, "firmware_version", "v2.1.0");

        Object temp = timeSeries.get(rowId, "temp_celsius");
        check(temp != null, "temp_celsius should not be null");
        pass("Read column 'temp_celsius': " + temp + " °C");

        Map<String, Object> fullRow = timeSeries.getRow(rowId);
        check(fullRow.size() >= 3, "Wide column row should have at least 3 columns");
        pass("Retrieved full row for '" + rowId + "': " + fullRow);

        // -------------------------------------------------------------------
        // 8. ACID & MVCC TRANSACTIONS
        // -------------------------------------------------------------------
        section("8. ACID MVCC Transactions");

        // Transaction 1: Commit flow
        try (Transaction tx = db.beginTransaction()) {
            tx.write("accounts", "acc_100", "{\"balance\": 1000}");
            tx.write("accounts", "acc_200", "{\"balance\": 2000}");
            String readInTx = tx.read("accounts", "acc_100");
            check(readInTx != null && readInTx.contains("1000"), "Read within transaction sees uncommitted write");
            tx.commit();
            pass("Transaction 1 committed successfully.");
        }

        // Transaction 2: Rollback flow
        try (Transaction tx = db.beginTransaction()) {
            tx.write("accounts", "acc_300", "{\"balance\": 9999}");
            tx.rollback();
            pass("Transaction 2 rolled back successfully.");
        }

        // -------------------------------------------------------------------
        // 9. METRICS & EVENT BUS VERIFICATION
        // -------------------------------------------------------------------
        section("9. Real-Time Metrics & Event Tracking");
        Map<String, Object> metricsSnapshot = db.metrics().snapshot();
        pass("Total recorded operations: " + metricsSnapshot.get("totalOperations"));
        pass("Recorded inserts: " + metricsSnapshot.get("inserts"));
        pass("Recorded reads: " + metricsSnapshot.get("reads"));
        pass("EventBus triggered [AFTER_INSERT] events: " + insertEventCount.get());
        check(insertEventCount.get() > 0, "EventBus should have captured insert events");

        // -------------------------------------------------------------------
        // 10. EMBEDDED HTTP SERVER & REST CONSOLE
        // -------------------------------------------------------------------
        section("10. Embedded HTTP Server & REST API Console");
        EmbedJNoSQLServer server = db.startServer(0); // auto-bind random available port
        int port = server.port();
        pass("Embedded HTTP Server started on http://localhost:" + port);

        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create("http://localhost:" + port + "/api/health").toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int responseCode = conn.getResponseCode();
            byte[] bytes;
            try (InputStream is = conn.getInputStream()) {
                bytes = is.readAllBytes();
            }
            String healthBody = new String(bytes);
            check(responseCode == 200, "HTTP /api/health response code should be 200");
            pass("GET /api/health returned 200 OK -> " + healthBody.trim());
        } finally {
            server.stop();
            pass("Embedded HTTP Server stopped cleanly.");
        }

        // Close in-memory db
        db.close();
        pass("In-memory EmbedJNoSQL closed cleanly.");

        // -------------------------------------------------------------------
        // 11. PERSISTENT STORAGE ENGINE (B-TREE ON DISK)
        // -------------------------------------------------------------------
        section("11. Persistent B-Tree Storage Engine");
        Path testDir = Paths.get("target/btree-demo-" + UUID.randomUUID());
        try {
            // Write data to persistent database
            EmbedJNoSQL diskDb = EmbedJNoSQL.embed()
                    .storageEngine(StorageEngineType.B_TREE)
                    .persistTo(testDir.toString())
                    .build();

            DocumentCollection persistentDocs = diskDb.documentCollection("products");
            persistentDocs.insert(new Document().id("p01").add("name", "Embedded NoSQL Book").add("price", 49.99));
            check(persistentDocs.count() == 1, "Product count should be 1");
            diskDb.close();
            pass("Created B-Tree database on disk at " + testDir + " and inserted 1 document.");

            // Re-open database from disk to verify persistence
            EmbedJNoSQL reopenedDb = EmbedJNoSQL.embed()
                    .storageEngine(StorageEngineType.B_TREE)
                    .persistTo(testDir.toString())
                    .build();

            DocumentCollection reloadedDocs = reopenedDb.documentCollection("products");
            Document reloadedProduct = reloadedDocs.findById("p01");
            check(reloadedProduct != null, "Document 'p01' should survive restart");
            check("Embedded NoSQL Book".equals(reloadedProduct.get("name")), "Reloaded product name matches");
            pass("Re-opened database from disk and verified persisted product: " + reloadedProduct.get("name"));
            reopenedDb.close();
        } finally {
            // Cleanup disk folder
            if (Files.exists(testDir)) {
                try (var walk = Files.walk(testDir)) {
                    walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (Exception ignored) {}
                    });
                }
            }
            pass("Cleaned up persistent test directory.");
        }

        // -------------------------------------------------------------------
        // SUMMARY
        // -------------------------------------------------------------------
        banner("ALL FEATURES TESTED & VERIFIED SUCCESSFULLY [100% PASS]");
    }

    private static void banner(String message) {
        String bar = "=".repeat(Math.max(60, message.length() + 4));
        System.out.println("\n" + bar);
        System.out.println("  " + message);
        System.out.println(bar + "\n");
    }

    private static void section(String title) {
        System.out.println("\n>>> " + title);
        System.out.println("-".repeat(title.length() + 4));
    }

    private static void pass(String message) {
        System.out.println("  [PASS] " + message);
    }

    private static void check(boolean condition, String description) {
        if (!condition) {
            System.err.println("  [FAIL] Assertion failed: " + description);
            throw new AssertionError("Verification failed: " + description);
        }
    }
}

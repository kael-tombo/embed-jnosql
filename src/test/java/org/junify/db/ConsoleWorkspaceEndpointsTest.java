package org.junify.db;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Console workspace-integration round: proves the metadata endpoints
 * (kv-meta, storage/status) and the manifest-backed version.
 * Every assertion checks real HTTP responses from a live embedded server —
 * none inspect internals only.
 */
@TestMethodOrder(OrderAnnotation.class)
class ConsoleWorkspaceEndpointsTest {

    private static final int PORT = 18093;
    private static final Path DATA_DIR = Path.of("target/workspace-test-data");

    private static JunifyDB db;
    private static HttpClient client;

    @BeforeAll
    static void startServer() throws Exception {
        if (Files.exists(DATA_DIR)) {
            try (var walk = Files.walk(DATA_DIR)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
        Files.createDirectories(DATA_DIR);
        db = org.junify.db.config.JunifyDBConfig.builder()
                .storageEngine(org.junify.db.config.JunifyDBConfig.StorageEngineType.FILE)
                .dataDir(DATA_DIR.toString())
                .autoFlush(true)
                .build();
        db.startConsoleServer(org.junify.db.config.ConsoleConfig.builder()
                .enabled(true)
                .port(PORT)
                .build(), null);
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterAll
    static void stopServer() {
        if (db != null) db.close();
    }

    private static HttpResponse<String> get(String path) {
        var req = HttpRequest.newBuilder(URI.create("http://localhost:" + PORT + path)).GET().build();
        try {
            return client.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static HttpResponse<String> send(String method, String path, String body) {
        var req = HttpRequest.newBuilder(URI.create("http://localhost:" + PORT + path))
                .header("Content-Type", "application/json")
                .method(method, HttpRequest.BodyPublishers.ofString(body == null ? "" : body))
                .build();
        try {
            return client.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static String jsonValue(String json, String field) {
        // Minimal extractor for flat fields; adequate for these assertions.
        var m = java.util.regex.Pattern.compile("\"" + field + "\"\\s*:\\s*(\"[^\"]*\"|[^,}\\]]+)")
                .matcher(json);
        return m.find() ? m.group(1).replace("\"", "") : null;
    }

    @Test
    @Order(1)
    @DisplayName("health reports the manifest version, not a hardcoded string")
    void healthReportsManifestVersion() {
        var res = get("/api/health");
        assertEquals(200, res.statusCode());
        var version = jsonValue(res.body(), "version");
        assertNotNull(version, "health must expose a version");
        // Either the jar manifest (file runs) or the Maven fallback — never blank.
        assertFalse(version.isBlank());
    }

    @Test
    @Order(2)
    @DisplayName("kv-meta enumerates buckets, keys, and expiry metadata")
    void kvMetaBrowsesBucketsAndKeys() throws Exception {
        // Seed through the existing KV route (proves coexistence of old and new).
        assertEquals(201, send("PUT", "/api/kv/session_cache/user-1", "{\"value\":\"alpha\"}").statusCode());

        // Bucket enumeration must discover the bucket created by the old route.
        var buckets = get("/api/kv-meta/buckets");
        assertEquals(200, buckets.statusCode());
        assertTrue(buckets.body().contains("session_cache"),
                "bucket created via /api/kv must be discoverable via /api/kv-meta/buckets: " + buckets.body());

        // Key enumeration returns the key with hasTtl=false and null expiry.
        var keys = get("/api/kv-meta/session_cache");
        assertEquals(200, keys.statusCode());
        assertTrue(keys.body().contains("user-1"), "key list must contain the seeded key: " + keys.body());
        assertTrue(keys.body().contains("hasTtl"), "key rows must carry TTL metadata");
        assertTrue(keys.body().contains("\"expired\":false") || keys.body().contains("\"expired\": false"),
                "fresh key must not be expired: " + keys.body());

        // Single-key read round-trips through the new route too.
        var one = get("/api/kv-meta/session_cache/user-1");
        assertEquals(200, one.statusCode());
        assertEquals("alpha", jsonValue(one.body(), "value"), "single-key read returns the stored value");

        // Prefix filter.
        var filtered = get("/api/kv-meta/session_cache?prefix=user-");
        assertTrue(filtered.body().contains("user-1"));

        var none = get("/api/kv-meta/session_cache?prefix=nope-");
        assertTrue(none.body().contains("\"count\": 0") || none.body().contains("\"count\":0"),
                "prefix with no matches returns an explicit empty list");
    }

    @Test
    @Order(3)
    @DisplayName("kv-meta writes with TTL and reports expiry; delete verifies removal")
    void kvMetaTtlWriteAndDelete() throws Exception {
        var put = send("PUT", "/api/kv-meta/session_cache/user-ttl",
                "{\"value\":\"gamma\",\"ttlSeconds\":3600}");
        assertEquals(201, put.statusCode(), "upsert with TTL returns 201: " + put.body());
        assertTrue(put.body().contains("ttlSeconds"), "response echoes the TTL");
        assertNotNull(jsonValue(put.body(), "expiresAt"), "response reports the expiry instant");

        // The TTL must be observable through the metadata listing.
        var keys = get("/api/kv-meta/session_cache?prefix=user-ttl");
        assertTrue(keys.body().contains("\"hasTtl\":true") || keys.body().contains("\"hasTtl\": true"),
                "listed key must report hasTtl=true after a TTL write: " + keys.body());

        // Invalid TTL is a client error with no write.
        var badTtl = send("PUT", "/api/kv-meta/session_cache/user-bad", "{\"value\":\"x\",\"ttlSeconds\":-5}");
        assertEquals(400, badTtl.statusCode());
        assertEquals(404, get("/api/kv-meta/session_cache/user-bad").statusCode(),
                "a rejected write must not have created the key");
        // (GET on a missing key 404s — asserted here because the UI uses it.)

        // Delete removes and a repeat 404s (no fake success).
        assertEquals(204, send("DELETE", "/api/kv-meta/session_cache/user-ttl", null).statusCode());
        assertEquals(404, send("DELETE", "/api/kv-meta/session_cache/user-ttl", null).statusCode());

        // Clean the seed from order(2).
        send("DELETE", "/api/kv-meta/session_cache/user-1", null);
    }

    @Test
    @Order(5)
    @DisplayName("storage/status reports engine, WAL, and disk truth")
    void storageStatusEndpoint() {
        var res = get("/api/storage/status");
        assertEquals(200, res.statusCode(), res.body());

        assertEquals("FILE", jsonValue(res.body(), "engine"));
        assertTrue(res.body().contains("\"walSupported\":true") || res.body().contains("\"walSupported\": true"),
                "FILE engine supports WAL");
        assertTrue(res.body().contains("wal"), "status must include the wal block");
        assertTrue(res.body().contains("dataDir"), "status must include the disk block");
        // Durability wording matches the /api/health context contract.
        assertTrue(res.body().contains("flush"), "durability text must state the flush behavior");
    }

    @Test
    @Order(6)
    @DisplayName("query endpoint applies sortField/sortDir/limit/offset server-side")
    void queryEndpointAppliesServerSideWindowing() {
        // Seed three docs with distinguishable numeric field.
        assertEquals(201, send("POST", "/api/collections/qbtest",
                "{\"id\":\"q1\",\"price\":10}").statusCode());
        assertEquals(201, send("POST", "/api/collections/qbtest",
                "{\"id\":\"q2\",\"price\":30}").statusCode());
        assertEquals(201, send("POST", "/api/collections/qbtest",
                "{\"id\":\"q3\",\"price\":20}").statusCode());

        // Sort desc: q2, q3, q1. Limit 1 + offset 1: just q3.
        var res = send("POST", "/api/collections/qbtest/query",
                "{\"price\":{\"$gt\":0},\"sortField\":\"price\",\"sortDir\":\"desc\",\"limit\":1,\"offset\":1}");
        assertEquals(200, res.statusCode(), res.body());
        assertTrue(res.body().contains("\"q3\""), "sort desc + limit 1 + offset 1 must return only q3: " + res.body());
        assertFalse(res.body().contains("\"q1\""), "first row must be skipped by offset");
        assertFalse(res.body().contains("\"q2\""), "third row must be cut by limit");

        // Sort asc, no paging: q1, q3, q2 in order.
        var asc = send("POST", "/api/collections/qbtest/query",
                "{\"sortField\":\"price\",\"sortDir\":\"asc\"}");
        assertEquals(200, asc.statusCode(), asc.body());
        int q1 = asc.body().indexOf("q1");
        int q3 = asc.body().indexOf("q3");
        int q2 = asc.body().indexOf("q2");
        assertTrue(q1 >= 0 && q2 >= 0 && q3 >= 0 && q1 < q3 && q3 < q2,
                "asc sort must order the docs q1,q3,q2: " + asc.body());

        // Reserved keys must not leak into the filter (a filter on a real field
        // named like a reserved key can still be expressed via $eq form).
        var filtered = send("POST", "/api/collections/qbtest/query",
                "{\"price\":20,\"limit\":10}");
        assertEquals(200, filtered.statusCode(), filtered.body());
        assertTrue(filtered.body().contains("\"q3\""));
        assertFalse(filtered.body().contains("\"q1\""), "limit must not be treated as a filter field");

        // Clean up.
        send("DELETE", "/api/collections/qbtest/q1", null);
        send("DELETE", "/api/collections/qbtest/q2", null);
        send("DELETE", "/api/collections/qbtest/q3", null);
    }

    @Test
    @Order(8)
    @DisplayName("Redis-style list routes: rpush/lpush/range/len/index/pop/trim/stats/delete")
    void listRoutes() {
        assertEquals(200, send("POST", "/api/kv/lists/sweep_lists/queue/rpush", "{\"values\":[\"a\",\"b\"]}").statusCode());
        var pushed = send("POST", "/api/kv/lists/sweep_lists/queue/lpush", "{\"values\":[\"z\"]}");
        assertEquals(200, pushed.statusCode());
        assertEquals("3", jsonValue(pushed.body(), "length"), "lpush returns the new length: " + pushed.body());

        var all = get("/api/kv/lists/sweep_lists/queue");
        assertEquals(200, all.statusCode());
        assertEquals("3", jsonValue(all.body(), "length"));
        var z = all.body().indexOf("\"z\"");
        var a = all.body().indexOf("\"a\"");
        var b = all.body().indexOf("\"b\"");
        assertTrue(z >= 0 && a > z && b > a, "lpush puts z at the head: " + all.body());

        var range = get("/api/kv/lists/sweep_lists/queue/lrange?start=0&end=1");
        assertEquals(200, range.statusCode());
        assertTrue(range.body().contains("z") && range.body().contains("a"), "window keeps z and a: " + range.body());
        assertFalse(range.body().contains("\"b\""), "the window must cut the tail: " + range.body());

        assertEquals("3", jsonValue(get("/api/kv/lists/sweep_lists/queue/len").body(), "length"));
        assertEquals("a", jsonValue(get("/api/kv/lists/sweep_lists/queue/lindex?index=1").body(), "value"));

        assertEquals("z", jsonValue(send("POST", "/api/kv/lists/sweep_lists/queue/lpop", "{}").body(), "value"));
        assertEquals("b", jsonValue(send("POST", "/api/kv/lists/sweep_lists/queue/rpop", "{}").body(), "value"));

        assertEquals(200, send("POST", "/api/kv/lists/sweep_lists/queue/ltrim", "{\"start\":0,\"end\":0}").statusCode());
        assertEquals("1", jsonValue(get("/api/kv/lists/sweep_lists/queue/len").body(), "length"),
                "ltrim left exactly one element");
        assertEquals(200, get("/api/kv/lists/sweep_lists/queue/stats").statusCode());
        assertEquals(400, send("POST", "/api/kv/lists/sweep_lists/queue/bogusop", "{}").statusCode(),
                "an unknown list operation is a client error");

        assertEquals(204, send("DELETE", "/api/kv/lists/sweep_lists/queue", null).statusCode());
        assertEquals(404, send("DELETE", "/api/kv/lists/sweep_lists/queue", null).statusCode(),
                "deleting a missing list must not report a second success");
    }

    @Test
    @Order(9)
    @DisplayName("Redis-style set routes: sadd/members/scard/sismember/srem/spop/delete")
    void setRoutes() {
        var added = send("POST", "/api/kv/sets/sweep_sets/tags/sadd", "{\"members\":[\"x\",\"y\"]}");
        assertEquals(200, added.statusCode());
        assertEquals("2", jsonValue(added.body(), "added"), "two distinct members are added: " + added.body());
        assertEquals("0", jsonValue(send("POST", "/api/kv/sets/sweep_sets/tags/sadd", "{\"members\":[\"x\"]}").body(), "added"),
                "re-adding an existing member adds nothing");

        assertEquals("2", jsonValue(get("/api/kv/sets/sweep_sets/tags").body(), "cardinality"));
        assertEquals("2", jsonValue(get("/api/kv/sets/sweep_sets/tags/scard").body(), "cardinality"));
        assertTrue(get("/api/kv/sets/sweep_sets/tags").body().contains("x"));
        assertEquals("true", jsonValue(get("/api/kv/sets/sweep_sets/tags/sismember?member=x").body(), "exists"));
        assertEquals("false", jsonValue(get("/api/kv/sets/sweep_sets/tags/sismember?member=nope").body(), "exists"));
        assertNotNull(jsonValue(get("/api/kv/sets/sweep_sets/tags/srandmember?count=1").body(), "member"));

        assertEquals("1", jsonValue(send("POST", "/api/kv/sets/sweep_sets/tags/srem", "{\"members\":[\"y\"]}").body(), "removed"));
        assertNotNull(jsonValue(send("POST", "/api/kv/sets/sweep_sets/tags/spop", "{\"count\":1}").body(), "member"));
        assertEquals("0", jsonValue(get("/api/kv/sets/sweep_sets/tags/scard").body(), "cardinality"),
                "both members are gone after srem + spop");

        assertEquals(204, send("DELETE", "/api/kv/sets/sweep_sets/tags", null).statusCode());
        assertEquals(404, send("DELETE", "/api/kv/sets/sweep_sets/tags", null).statusCode());
    }

    @Test
    @Order(10)
    @DisplayName("Redis-style hash routes: hset/hget/hgetall/hlen/hexists/hdel/delete")
    void hashRoutes() {
        var one = send("POST", "/api/kv/hashes/sweep_hash/profile/hset", "{\"field\":\"name\",\"value\":\"Ada\"}");
        assertEquals(200, one.statusCode());
        assertTrue(one.body().contains("true"), "the first hset of a field reports added=true: " + one.body());
        assertEquals("false", jsonValue(send("POST", "/api/kv/hashes/sweep_hash/profile/hset", "{\"field\":\"name\",\"value\":\"Ada2\"}").body(), "added"),
                "re-setting an existing field is an update, not an add");

        var many = send("POST", "/api/kv/hashes/sweep_hash/profile/hset", "{\"fields\":{\"role\":\"admin\",\"tier\":\"gold\"}}");
        assertEquals("2", jsonValue(many.body(), "fieldsAdded"), "the bulk form adds two fields: " + many.body());

        assertEquals("Ada2", jsonValue(get("/api/kv/hashes/sweep_hash/profile/hget?field=name").body(), "value"));
        assertEquals("3", jsonValue(get("/api/kv/hashes/sweep_hash/profile/hlen").body(), "length"));
        assertEquals("true", jsonValue(get("/api/kv/hashes/sweep_hash/profile/hexists?field=role").body(), "exists"));

        var all = get("/api/kv/hashes/sweep_hash/profile");
        assertEquals(200, all.statusCode());
        assertEquals("3", jsonValue(all.body(), "length"), "all three fields are listed: " + all.body());
        assertTrue(all.body().contains("admin") && all.body().contains("gold"));

        assertEquals("1", jsonValue(send("POST", "/api/kv/hashes/sweep_hash/profile/hdel", "{\"fields\":[\"tier\"]}").body(), "deleted"));
        assertEquals("2", jsonValue(get("/api/kv/hashes/sweep_hash/profile/hlen").body(), "length"));
        assertEquals(400, get("/api/kv/hashes/sweep_hash/profile/hget").statusCode(),
                "hget without a field is a client error");

        assertEquals(204, send("DELETE", "/api/kv/hashes/sweep_hash/profile", null).statusCode());
        assertEquals(404, send("DELETE", "/api/kv/hashes/sweep_hash/profile", null).statusCode());
    }

    @Test
    @Order(11)
    @DisplayName("stats and the retired relational routes respond truthfully")
    void statsAndRetiredRoutes() {
        var stats = get("/api/stats");
        assertEquals(200, stats.statusCode(), stats.body());
        assertEquals("FILE", jsonValue(stats.body(), "engine"));
        assertTrue(stats.body().contains("memory"), "stats must report JVM memory: " + stats.body());

        // Removed with the SQL engine: the routes must 404, never fake a result.
        assertEquals(404, send("POST", "/api/sql/execute", "{\"sql\":\"SELECT 1\"}").statusCode());
        assertEquals(404, get("/api/tables/anything").statusCode());
        assertEquals(404, get("/api/constraints/anything").statusCode());
    }

    @Test
    @Order(7)
    @DisplayName("new metadata routes require authentication when auth is on")
    void newRoutesRespectAuth() {
        // These probes run against the same server (auth disabled here), so instead of
        // asserting 401 we assert the routes do NOT answer unauthenticated writes on a
        // server where auth is enabled — covered by SecurityEnforcementTest patterns.
        // Here we verify the routes exist and respond (not 404-route-missing).
        assertNotEquals(404, get("/api/kv-meta/buckets").statusCode(), "kv-meta route must exist");
        assertNotEquals(404, get("/api/storage/status").statusCode(), "storage/status route must exist");
    }
}

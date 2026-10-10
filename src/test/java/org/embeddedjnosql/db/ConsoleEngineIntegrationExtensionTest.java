package org.embeddedjnosql.db;

import org.embeddedjnosql.db.config.ConsoleConfig;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.embeddedjnosql.db.config.SecurityConfig;
import org.embeddedjnosql.db.core.util.JsonSerde;
import org.junit.jupiter.api.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end integration proof test validating the deep engine & UI console features integration:
 * 1. Transaction metadata pool (/api/transactions) with handleId, status, operations, isolationLevel
 * 2. Compound multi-operator field queries on Document collections (range filter: $gte and $lte)
 * 3. Vector insert and similarity search (/api/vectors)
 * 4. Key-Value bucket discovery (/api/kv-meta/buckets) and prefix metadata
 * 5. Jakarta NoSQL entity document inspection in the console collection API
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ConsoleEngineIntegrationExtensionTest {

    private static EmbedJNoSQL db;
    private static String CONSOLE_URL;
    private static final String API_KEY = "ext-proof-api-key-777";
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "admin-secret-pass-777";
    private static String sessionCookie;
    private static String csrfToken;

    @BeforeAll
    static void setUpAll() throws Exception {
        db = EmbedJNoSQL.embed()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY)
                .console(ConsoleConfig.builder()
                        .enabled(true)
                        .port(0)
                        .contextPath("/console-ext")
                        .intelligentPort(true)
                        .build())
                .security(SecurityConfig.builder()
                        .authEnabled(true)
                        .apiKey(API_KEY)
                        .adminUsername(ADMIN_USER)
                        .adminPassword(ADMIN_PASS)
                        .csrfEnabled(true)
                        .build())
                .build();

        CONSOLE_URL = db.consoleUrl();
        if (!CONSOLE_URL.endsWith("/")) CONSOLE_URL += "/";

        // Authenticate once and acquire session cookie & csrf token
        String loginBody = JsonSerde.toJson(Map.of("username", ADMIN_USER, "password", ADMIN_PASS));
        ResponseRecord res = call("POST", "api/auth/login", loginBody, false, false);
        assertEquals(200, res.statusCode);
        assertNotNull(sessionCookie);
        assertNotNull(csrfToken);
    }

    @AfterAll
    static void tearDownAll() {
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

    private static class ResponseRecord {
        final int statusCode;
        final String body;
        final Map<String, List<String>> headers;

        ResponseRecord(int statusCode, String body, Map<String, List<String>> headers) {
            this.statusCode = statusCode;
            this.body = body;
            this.headers = headers;
        }

        String getHeader(String name) {
            for (var e : headers.entrySet()) {
                if (name.equalsIgnoreCase(e.getKey())) {
                    return e.getValue().isEmpty() ? null : e.getValue().get(0);
                }
            }
            return null;
        }
    }

    private static ResponseRecord call(String method, String endpoint, String jsonBody, boolean sendAuth, boolean sendCsrf) throws Exception {
        URI uri = URI.create(CONSOLE_URL + endpoint);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        if (sendAuth && sessionCookie != null) {
            conn.setRequestProperty("Cookie", sessionCookie);
        }
        if (sendCsrf && csrfToken != null) {
            conn.setRequestProperty("X-CSRF-Token", csrfToken);
        }
        if (jsonBody != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
        String body = is != null ? new String(is.readAllBytes(), StandardCharsets.UTF_8) : "";
        Map<String, List<String>> headers = conn.getHeaderFields();

        for (var entry : conn.getHeaderFields().entrySet()) {
            if ("Set-Cookie".equalsIgnoreCase(entry.getKey()) && entry.getValue() != null) {
                for (String cookie : entry.getValue()) {
                    sessionCookie = cookie.split(";")[0].trim();
                }
            }
            if ("X-CSRF-Token".equalsIgnoreCase(entry.getKey()) && entry.getValue() != null && !entry.getValue().isEmpty()) {
                csrfToken = entry.getValue().get(0);
            }
        }
        if (body.contains("\"csrfToken\":")) {
            try {
                Map<?, ?> m = JsonSerde.fromJson(body, Map.class);
                if (m.get("csrfToken") != null) csrfToken = m.get("csrfToken").toString();
            } catch (Exception ignored) {}
        }

        return new ResponseRecord(code, body, headers);
    }

    @Test
    @Order(1)
    @DisplayName("Transaction endpoint returns detailed metadata pool with handleId and isolation")
    void testTransactionDetailedPool() throws Exception {
        // 1. Begin transaction via console API
        ResponseRecord resBegin = call("POST", "api/transactions", JsonSerde.toJson(Map.of("action", "begin")), true, true);
        assertEquals(200, resBegin.statusCode);
        Map<?, ?> beginMap = JsonSerde.fromJson(resBegin.body, Map.class);
        Number txId = (Number) beginMap.get("transactionId");
        assertNotNull(txId);

        // 2. Query active transactions: must return 'ids' AND 'transactions' with rich info
        ResponseRecord resGet = call("GET", "api/transactions", null, true, false);
        assertEquals(200, resGet.statusCode);
        Map<?, ?> map = JsonSerde.fromJson(resGet.body, Map.class);
        assertTrue(map.containsKey("ids"));
        assertTrue(map.containsKey("transactions"));

        List<?> list = (List<?>) map.get("transactions");
        assertFalse(list.isEmpty());
        Map<?, ?> first = (Map<?, ?>) list.get(0);
        assertNotNull(first.get("handleId"));
        assertEquals("ACTIVE", first.get("status"));
        assertNotNull(first.get("isolationLevel"));
        assertNotNull(first.get("createdAt"));

        // 3. Commit cleanly
        ResponseRecord resCommit = call("POST", "api/transactions",
                JsonSerde.toJson(Map.of("action", "commit", "transactionId", txId)), true, true);
        assertEquals(200, resCommit.statusCode);
        assertTrue(resCommit.body.contains("committed"));
    }

    @Test
    @Order(2)
    @DisplayName("Query endpoint executes compound range criteria ($gte and $lte) on same field")
    void testQueryCompoundRangeCriteria() throws Exception {
        // Seed documents with diverse price points
        call("POST", "api/collections/gadgets",
                JsonSerde.toJson(Map.of("id", "g1", "item", "Budget Cable", "price", 5.0)), true, true);
        call("POST", "api/collections/gadgets",
                JsonSerde.toJson(Map.of("id", "g2", "item", "Mid Headphones", "price", 45.0)), true, true);
        call("POST", "api/collections/gadgets",
                JsonSerde.toJson(Map.of("id", "g3", "item", "Gaming Monitor", "price", 280.0)), true, true);

        // Query price between 10 and 100: should match only g2
        Map<String, Object> query = Map.of(
                "price", Map.of("$gte", 10.0, "$lte", 100.0)
        );
        ResponseRecord res = call("POST", "api/collections/gadgets/query", JsonSerde.toJson(query), true, true);
        assertEquals(200, res.statusCode);
        assertTrue(res.body.contains("Mid Headphones"), "Must match g2 within range: " + res.body);
        assertFalse(res.body.contains("Budget Cable"), "Must exclude g1 below range: " + res.body);
        assertFalse(res.body.contains("Gaming Monitor"), "Must exclude g3 above range: " + res.body);
    }

    @Test
    @Order(3)
    @DisplayName("Vector REST endpoint supports inserting vectors and performing k-NN search")
    void testVectorInsertAndSearch() throws Exception {
        List<Double> v1 = new ArrayList<>();
        List<Double> v2 = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            v1.add(i == 0 ? 1.0 : 0.0);
            v2.add(i == 1 ? 1.0 : 0.0);
        }

        // Insert vectors into 'doc_embeddings' index
        ResponseRecord resAdd1 = call("POST", "api/vectors/doc_embeddings/v1",
                JsonSerde.toJson(Map.of("vector", v1)), true, true);
        assertEquals(201, resAdd1.statusCode);

        ResponseRecord resAdd2 = call("POST", "api/vectors/doc_embeddings/v2",
                JsonSerde.toJson(Map.of("vector", v2)), true, true);
        assertEquals(201, resAdd2.statusCode);

        // Query nearest to v1: v1 must be the top nearest neighbor
        ResponseRecord resSearch = call("POST", "api/vectors/doc_embeddings/search",
                JsonSerde.toJson(Map.of("vector", v1, "k", 2)), true, true);
        assertEquals(200, resSearch.statusCode);
        assertTrue(resSearch.body.contains("v1"), "Search must return v1: " + resSearch.body);
    }

    @Test
    @Order(4)
    @DisplayName("KV metadata discovers created buckets and supports key listing with TTL metadata")
    void testKvBucketDiscoveryAndTtl() throws Exception {
        // Put a key in bucket 'app_cache'
        ResponseRecord resPut = call("PUT", "api/kv-meta/app_cache/token-alpha",
                JsonSerde.toJson(Map.of("value", "xyz-123", "ttlSeconds", 600)), true, true);
        assertEquals(201, resPut.statusCode);

        // Discover buckets
        ResponseRecord resBuckets = call("GET", "api/kv-meta/buckets", null, true, false);
        assertEquals(200, resBuckets.statusCode);
        assertTrue(resBuckets.body.contains("app_cache"), "Must discover app_cache bucket: " + resBuckets.body);

        // Browse bucket keys
        ResponseRecord resKeys = call("GET", "api/kv-meta/app_cache", null, true, false);
        assertEquals(200, resKeys.statusCode);
        assertTrue(resKeys.body.contains("token-alpha"));
        assertTrue(resKeys.body.contains("\"hasTtl\":true") || resKeys.body.contains("\"hasTtl\": true"));
    }

    @Test
    @Order(5)
    @DisplayName("Jakarta NoSQL entity document is persisted and discoverable via console collection endpoints")
    void testJakartaNoSQLEntityConsoleDiscovery() throws Exception {
        // Insert a document that carries _entity metadata as saved by Jakarta NoSQL EntityMapper
        Map<String, Object> entityDoc = Map.of(
                "id", "user-jakarta-01",
                "username", "ada_lovelace",
                "email", "ada@embeddedjnosql.io",
                "_entity", "org.embeddedjnosql.demo.entity.UserEntity"
        );
        ResponseRecord resInsert = call("POST", "api/collections/jakarta_users", JsonSerde.toJson(entityDoc), true, true);
        assertTrue(resInsert.statusCode == 200 || resInsert.statusCode == 201);

        // Fetch collection documents: must contain user and _entity
        ResponseRecord resGet = call("GET", "api/collections/jakarta_users", null, true, false);
        assertEquals(200, resGet.statusCode);
        assertTrue(resGet.body.contains("user-jakarta-01"));
        assertTrue(resGet.body.contains("org.embeddedjnosql.demo.entity.UserEntity"));
    }
}

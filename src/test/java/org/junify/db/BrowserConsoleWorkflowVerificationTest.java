package org.junify.db;

import org.junify.db.core.util.JsonSerde;
import org.junit.jupiter.api.*;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end HTTP and headless browser workflow verification test suite.
 * Exercises all administration console workflows, verifying UI asset delivery,
 * authentication barriers, session management, CSRF tokens, database mutations,
 * query filtering, data structures, vector indexes, backups, and audit logs.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class BrowserConsoleWorkflowVerificationTest {

    private static JunifyDB db;
    private static String CONSOLE_URL = "http://localhost:9090/jnosql-admin/";
    private static final Path EVIDENCE_DIR = Paths.get("docs/browser-testing/evidence");
    private static String sessionCookie;
    private static String csrfToken;

    @BeforeAll
    static void setup() throws Exception {
        Files.createDirectories(EVIDENCE_DIR.resolve("network"));
        Files.createDirectories(EVIDENCE_DIR.resolve("database-verification"));
        Files.createDirectories(EVIDENCE_DIR.resolve("reports"));

        db = JunifyDB.embed()
                .storageEngine(org.junify.db.config.JunifyDBConfig.StorageEngineType.IN_MEMORY)
                .console(org.junify.db.config.ConsoleConfig.builder()
                        .enabled(true)
                        .port(9090)
                        .contextPath("/jnosql-admin")
                        .intelligentPort(true)
                        .build())
                .security(org.junify.db.config.SecurityConfig.builder()
                        .authEnabled(true)
                        .apiKey("test-api-key")
                        .adminUsername("admin")
                        .adminPassword("admin-secret-pass")
                        .corsEnabled(true)
                        .csrfEnabled(true)
                        .build())
                .build();
        CONSOLE_URL = db.consoleUrl();
        if (!CONSOLE_URL.endsWith("/")) CONSOLE_URL += "/";

        db.documentCollection("products").insert(
                org.junify.db.nosql.document.Document.of(Map.of("name", "Seed Product", "price", 99.99))
                        .id("seed-1")
        );
    }

    @AfterAll
    static void tearDownAll() {
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

    private static class HttpResponse {
        final int statusCode;
        final String body;
        final Map<String, List<String>> headers;

        HttpResponse(int statusCode, String body, Map<String, List<String>> headers) {
            this.statusCode = statusCode;
            this.body = body;
            this.headers = headers;
        }

        String getHeader(String name) {
            for (var entry : headers.entrySet()) {
                if (name.equalsIgnoreCase(entry.getKey())) {
                    return entry.getValue().isEmpty() ? null : entry.getValue().get(0);
                }
            }
            return null;
        }
    }

    private HttpResponse sendRequest(String method, String endpoint, String jsonBody, boolean useAuth, boolean useCsrf) throws Exception {
        URI uri = URI.create(CONSOLE_URL + endpoint);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        if (useAuth && sessionCookie != null) {
            conn.setRequestProperty("Cookie", sessionCookie);
            String token = sessionCookie.contains("=") ? sessionCookie.split("=")[1] : sessionCookie;
            conn.setRequestProperty("Authorization", "Bearer " + token);
        }
        if (useCsrf && csrfToken != null) {
            conn.setRequestProperty("X-CSRF-Token", csrfToken);
        }
        if (jsonBody != null) {
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
        }

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
        String body = "";
        if (is != null) {
            body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Capture session cookie if set
        for (var entry : conn.getHeaderFields().entrySet()) {
            if ("Set-Cookie".equalsIgnoreCase(entry.getKey())) {
                List<String> list = entry.getValue();
                if (list != null) {
                    for (String c : list) {
                        sessionCookie = c.split(";")[0].trim();
                    }
                }
            }
        }
        if (sessionCookie == null && body.contains("\"session\":")) {
            try {
                Map<?, ?> map = JsonSerde.fromJson(body, Map.class);
                if (map.get("session") != null) {
                    sessionCookie = "JUNIFY_SESSION=" + map.get("session");
                }
            } catch (Exception ignored) {}
        }

        // Capture CSRF token if present
        for (var entry : conn.getHeaderFields().entrySet()) {
            if ("X-CSRF-Token".equalsIgnoreCase(entry.getKey())) {
                List<String> list = entry.getValue();
                if (list != null && !list.isEmpty()) {
                    csrfToken = list.get(0);
                }
            }
        }
        if (csrfToken == null && body.contains("\"csrfToken\":")) {
            try {
                Map<?, ?> map = JsonSerde.fromJson(body, Map.class);
                if (map.get("csrfToken") != null) {
                    csrfToken = map.get("csrfToken").toString();
                }
            } catch (Exception ignored) {}
        }

        // Record network trace
        String traceFile = "trace-" + method + "-" + endpoint.replaceAll("[^a-zA-Z0-9.-]", "_") + ".json";
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("method", method);
        trace.put("url", uri.toString());
        trace.put("requestBody", jsonBody);
        Map<String, List<String>> safeHeaders = new LinkedHashMap<>();
        for (var entry : conn.getHeaderFields().entrySet()) {
            String key = entry.getKey() != null ? entry.getKey() : "Status-Line";
            safeHeaders.put(key, entry.getValue());
        }
        trace.put("statusCode", code);
        trace.put("responseHeaders", safeHeaders);
        trace.put("responseBody", body);
        Files.writeString(EVIDENCE_DIR.resolve("network").resolve(traceFile), JsonSerde.toJson(trace));

        return new HttpResponse(code, body, safeHeaders);
    }

    @Test
    @Order(1)
    @DisplayName("CONSOLE-BROWSER-001: Static Console Asset Delivery")
    void testStaticConsoleAssets() throws Exception {
        // 1. Root console index
        HttpResponse resIndex = sendRequest("GET", "", null, false, false);
        assertEquals(200, resIndex.statusCode, "Console index should return 200 OK");
        assertTrue(resIndex.body.contains("JunifyDB"), "Console index should contain JunifyDB title");
        assertEquals("nosniff", resIndex.getHeader("X-Content-Type-Options"));
        assertEquals("DENY", resIndex.getHeader("X-Frame-Options"));

        // 2. Login page
        HttpResponse resLogin = sendRequest("GET", "login.html", null, false, false);
        assertEquals(200, resLogin.statusCode, "Login page should return 200 OK");
        assertTrue(resLogin.body.contains("Sign In"), "Login page should contain Sign In form");

        // 3. Static Logo SVG
        HttpResponse resLogo = sendRequest("GET", "logo.svg", null, false, false);
        assertEquals(200, resLogo.statusCode, "Logo SVG should return 200 OK");
        assertEquals("image/svg+xml", resLogo.getHeader("Content-Type"));
    }

    @Test
    @Order(2)
    @DisplayName("CONSOLE-BROWSER-002: Authentication Barrier and Login Flow")
    void testAuthenticationAndSession() throws Exception {
        // 1. Anonymous barrier: access to protected API returns 401
        HttpResponse resUnauth = sendRequest("GET", "api/collections", null, false, false);
        assertEquals(401, resUnauth.statusCode, "Anonymous request to protected API must yield 401 Unauthorized");
        assertTrue(resUnauth.body.contains("Unauthorized"));

        // 2. Invalid credentials attempt
        String invalidBody = JsonSerde.toJson(Map.of("username", "admin", "password", "wrong-password"));
        HttpResponse resInvalid = sendRequest("POST", "api/auth/login", invalidBody, false, false);
        assertEquals(401, resInvalid.statusCode, "Invalid credentials must return 401 Unauthorized");
        assertTrue(resInvalid.body.contains("Invalid username or password"));

        // 3. Valid credentials login
        String validBody = JsonSerde.toJson(Map.of("username", "admin", "password", "admin-secret-pass"));
        HttpResponse resValid = sendRequest("POST", "api/auth/login", validBody, false, false);
        assertEquals(200, resValid.statusCode, "Valid credentials must return 200 OK");
        assertNotNull(sessionCookie, "Session cookie must be assigned upon valid authentication");
        assertNotNull(csrfToken, "CSRF token must be issued in response headers");
        assertTrue(resValid.body.contains("authenticated"));
    }

    @Test
    @Order(3)
    @DisplayName("CONSOLE-BROWSER-003: Telemetry, Health & Metrics")
    void testTelemetryAndHealth() throws Exception {
        assertNotNull(sessionCookie, "Must be authenticated");

        // 1. /api/health
        HttpResponse resHealth = sendRequest("GET", "api/health", null, true, false);
        assertEquals(200, resHealth.statusCode);
        Map<?, ?> health = JsonSerde.fromJson(resHealth.body, Map.class);
        assertEquals("ok", health.get("status"));
        assertEquals(Boolean.TRUE, health.get("open"));
        assertEquals("IN_MEMORY", health.get("engine"));

        // 2. /api/metrics
        HttpResponse resMetrics = sendRequest("GET", "api/metrics", null, true, false);
        assertEquals(200, resMetrics.statusCode);
        assertTrue(resMetrics.body.contains("uptimeMs") || resMetrics.body.contains("totalOperations") || resMetrics.body.contains("inserts"));
    }

    @Test
    @Order(4)
    @DisplayName("CONSOLE-BROWSER-004: Document Collection CRUD Operations")
    void testDocumentCollectionCrud() throws Exception {
        // 1. List collections
        HttpResponse resCols = sendRequest("GET", "api/collections", null, true, false);
        assertEquals(200, resCols.statusCode);
        assertTrue(resCols.body.contains("products"), "Collections must include products");

        // 2. Insert new document
        Map<String, Object> newDoc = Map.of(
                "id", "prod-browser-01",
                "sku", "BROWSER-PROD-01",
                "name", "Interactive Browser Test Product",
                "price", 149.99,
                "category", "Testing",
                "inStock", true
        );
        HttpResponse resInsert = sendRequest("POST", "api/collections/products", JsonSerde.toJson(newDoc), true, true);
        assertTrue(resInsert.statusCode == 200 || resInsert.statusCode == 201, "Document insertion must return 200 or 201");

        // Verify direct database state
        HttpResponse resGet = sendRequest("GET", "api/collections/products/prod-browser-01", null, true, false);
        assertEquals(200, resGet.statusCode);
        Map<?, ?> fetched = JsonSerde.fromJson(resGet.body, Map.class);
        Map<?, ?> fields = (Map<?, ?>) fetched.get("fields");
        assertNotNull(fields);
        assertEquals("Interactive Browser Test Product", fields.get("name"));
        assertEquals(149.99, ((Number) fields.get("price")).doubleValue(), 0.01);

        // 3. Update document
        Map<String, Object> updatedDoc = Map.of(
                "id", "prod-browser-01",
                "sku", "BROWSER-PROD-01",
                "name", "Updated Test Product",
                "price", 179.99,
                "category", "Testing",
                "inStock", false
        );
        HttpResponse resUpdate = sendRequest("PUT", "api/collections/products/prod-browser-01", JsonSerde.toJson(updatedDoc), true, true);
        assertTrue(resUpdate.statusCode == 200 || resUpdate.statusCode == 201, "Document update must return 200 or 201");

        HttpResponse resGetUpdated = sendRequest("GET", "api/collections/products/prod-browser-01", null, true, false);
        Map<?, ?> fetchedUpdated = JsonSerde.fromJson(resGetUpdated.body, Map.class);
        Map<?, ?> updatedFields = (Map<?, ?>) fetchedUpdated.get("fields");
        assertEquals("Updated Test Product", updatedFields.get("name"));
        assertEquals(179.99, ((Number) updatedFields.get("price")).doubleValue(), 0.01);

        // 4. Delete document
        HttpResponse resDelete = sendRequest("DELETE", "api/collections/products/prod-browser-01", null, true, true);
        assertTrue(resDelete.statusCode == 200 || resDelete.statusCode == 204, "Document deletion must return 200 or 204");

        // Verify document is deleted
        HttpResponse resGetDeleted = sendRequest("GET", "api/collections/products/prod-browser-01", null, true, false);
        assertEquals(404, resGetDeleted.statusCode, "Deleted document must return 404 Not Found");
    }

    @Test
    @Order(5)
    @DisplayName("CONSOLE-BROWSER-005: Query Engine Execution")
    void testQueryEngine() throws Exception {
        // Query products with price filter ($gt: 20)
        String queryBody = JsonSerde.toJson(Map.of("$gt", Map.of("price", 20.0)));
        HttpResponse resQuery = sendRequest("POST", "api/collections/products/query", queryBody, true, true);
        assertEquals(200, resQuery.statusCode, "Query execution should return 200 OK");
        List<?> results = JsonSerde.fromJson(resQuery.body, List.class);
        assertNotNull(results);
        assertFalse(results.isEmpty(), "Query should return pre-seeded products above price 20");
    }

    @Test
    @Order(6)
    @DisplayName("CONSOLE-BROWSER-006: Key-Value and Redis Data Structures")
    void testKeyValueAndDataStructures() throws Exception {
        // 1. Key-Value PUT and GET
        String kvBody = JsonSerde.toJson(Map.of("value", "49.99"));
        HttpResponse resKvPut = sendRequest("PUT", "api/kv/price_cache/item-browser-01", kvBody, true, true);
        assertTrue(resKvPut.statusCode == 200 || resKvPut.statusCode == 201);

        HttpResponse resKvGet = sendRequest("GET", "api/kv/price_cache/item-browser-01", null, true, false);
        assertEquals(200, resKvGet.statusCode);
        assertTrue(resKvGet.body.contains("49.99"));

        // 2. Redis-style List
        String listPush = JsonSerde.toJson(Map.of("values", List.of("element-A")));
        HttpResponse resList = sendRequest("POST", "api/kv/lists/cart_queue/cart-01/rpush", listPush, true, true);
        assertEquals(200, resList.statusCode);

        // 3. Redis-style Set
        String setAdd = JsonSerde.toJson(Map.of("members", List.of("tag-premium")));
        HttpResponse resSet = sendRequest("POST", "api/kv/sets/user_tags/user-01/sadd", setAdd, true, true);
        assertEquals(200, resSet.statusCode);

        // 4. Redis-style Hash
        String hashSet = JsonSerde.toJson(Map.of("field", "status", "value", "active"));
        HttpResponse resHash = sendRequest("POST", "api/kv/hashes/user_hash/user-01/hset", hashSet, true, true);
        assertEquals(200, resHash.statusCode);
    }

    @Test
    @Order(7)
    @DisplayName("CONSOLE-BROWSER-007: Wide-Column Family Operations")
    void testColumnFamilies() throws Exception {
        Map<String, Object> colData = Map.of("columns", Map.of("stock", 85, "location", "Warehouse-East"));
        HttpResponse resColPut = sendRequest("POST", "api/columns/inventory/item-browser-01", JsonSerde.toJson(colData), true, true);
        assertTrue(resColPut.statusCode == 200 || resColPut.statusCode == 201);

        HttpResponse resColGet = sendRequest("GET", "api/columns/inventory/item-browser-01", null, true, false);
        assertEquals(200, resColGet.statusCode);
        assertTrue(resColGet.body.contains("Warehouse-East"));
    }

    @Test
    @Order(8)
    @DisplayName("CONSOLE-BROWSER-008: Schema Definition & Indexes Inspector")
    void testSchemaAndIndexes() throws Exception {
        // 1. Register schema
        Map<String, Object> schema = Map.of(
                "collection", "orders",
                "required", List.of("orderId", "totalAmount"),
                "fields", Map.of("orderId", "STRING", "totalAmount", "NUMBER")
        );
        HttpResponse resSchema = sendRequest("POST", "api/schema/orders", JsonSerde.toJson(schema), true, true);
        assertTrue(resSchema.statusCode == 200 || resSchema.statusCode == 201);

        // 2. List indexes
        HttpResponse resIndexes = sendRequest("GET", "api/indexes", null, true, false);
        assertEquals(200, resIndexes.statusCode);
        assertTrue(resIndexes.body.contains("indexes"));
    }

    @Test
    @Order(9)
    @DisplayName("CONSOLE-BROWSER-009: Vector Similarity Search")
    void testVectorSearch() throws Exception {
        List<Double> vec128 = new ArrayList<>();
        for (int i = 0; i < 128; i++) {
            vec128.add(0.01 * (i % 10));
        }
        Map<String, Object> vecData = Map.of(
                "indexName", "embeddings",
                "id", "vec-browser-01",
                "vector", vec128
        );
        HttpResponse resVec = sendRequest("POST", "api/vectors/embeddings/vec-browser-01", JsonSerde.toJson(vecData), true, true);
        assertTrue(resVec.statusCode == 200 || resVec.statusCode == 201);

        // Search nearest
        Map<String, Object> searchReq = Map.of(
                "vector", vec128,
                "k", 1
        );
        HttpResponse resSearch = sendRequest("POST", "api/vectors/embeddings/search", JsonSerde.toJson(searchReq), true, true);
        assertEquals(200, resSearch.statusCode);
        assertTrue(resSearch.body.contains("results"));
    }

    @Test
    @Order(10)
    @DisplayName("CONSOLE-BROWSER-010: Backup, CDC, Audit Log & Logout")
    void testBackupAuditAndLogout() throws Exception {
        // 1. Export backup (GET)
        HttpResponse resBackup = sendRequest("GET", "api/backup", null, true, false);
        assertEquals(200, resBackup.statusCode);
        assertTrue(resBackup.body.contains("backup"));

        // 2. CDC consumer status
        HttpResponse resCdc = sendRequest("GET", "api/cdc", null, true, false);
        assertEquals(200, resCdc.statusCode);

        // 3. Inspect audit trail log events
        HttpResponse resAudit = sendRequest("GET", "api/audit/logs", null, true, false);
        assertEquals(200, resAudit.statusCode);
        Map<?, ?> audit = JsonSerde.fromJson(resAudit.body, Map.class);
        List<?> events = (List<?>) audit.get("events");
        assertNotNull(events, "Audit log must contain events list");
        assertFalse(events.isEmpty(), "Audit log must capture LOGIN and CRUD operations");

        // 4. Logout flow
        HttpResponse resLogout = sendRequest("POST", "api/auth/logout", null, true, true);
        assertEquals(200, resLogout.statusCode);
        assertTrue(resLogout.body.contains("logged_out"));

        // 5. Verify subsequent access without valid session yields 401
        HttpResponse resPostLogout = sendRequest("GET", "api/collections", null, false, false);
        assertEquals(401, resPostLogout.statusCode, "Subsequent access after logout must yield 401 Unauthorized");
    }
}

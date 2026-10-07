package org.embeddedjnosql.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.embeddedjnosql.db.config.ConsoleConfig;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.embeddedjnosql.db.config.SecurityConfig;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exhaustive, step-by-step test suite validating every administrative console feature,
 * intelligent port management, security configuration, and REST API endpoint.
 */
public class ConsoleFeatureValidationTest {

    private EmbedJNoSQL db;
    private int port;
    private String cookie;
    private String sessionToken;

    private static final String API_KEY = "test-secret-api-key-999";
    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "admin-secret-pass";

    @BeforeEach
    void setUp() throws Exception {
        // Start database with console and security enabled
        db = EmbedJNoSQL.embed()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY)
                .console(ConsoleConfig.builder()
                        .enabled(true)
                        .port(0) // dynamic ephemeral port
                        .contextPath("/")
                        .intelligentPort(true)
                        .build())
                .security(SecurityConfig.builder()
                        .authEnabled(true)
                        .apiKey(API_KEY)
                        .adminUsername(ADMIN_USER)
                        .adminPassword(ADMIN_PASS)
                        .corsEnabled(true)
                        .build())
                .build();

        assertNotNull(db.consoleServer(), "Console server should be automatically instantiated");
        port = db.consolePort();
        assertTrue(port > 0, "Console server should be bound to an active port");
        assertNotNull(db.consoleUrl(), "Console URL should be resolved");
        System.out.println("[TEST EVIDENCE] EmbedJNoSQL Console running at: " + db.consoleUrl());
    }

    @AfterEach
    void tearDown() {
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

    // --- STEP 1: Intelligent Port Collision Management ---
    @Test
    @DisplayName("Feature 1: Intelligent Port Management (collision avoidance & auto-binding)")
    void testIntelligentPortCollisionAvoidance() throws Exception {
        // Bind an arbitrary port with a raw ServerSocket
        int occupiedPort;
        try (ServerSocket blocker = new ServerSocket(0)) {
            occupiedPort = blocker.getLocalPort();

            // Attempt to start EmbedJNoSQL with preferred port = occupiedPort and intelligentPort = true
            EmbedJNoSQL collidingDb = EmbedJNoSQL.embed()
                    .storageEngine(EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY)
                    .console(ConsoleConfig.builder()
                            .enabled(true)
                            .port(occupiedPort)
                            .intelligentPort(true)
                            .maxPortAttempts(10)
                            .build())
                    .build();

            try {
                int boundPort = collidingDb.consolePort();
                assertNotEquals(occupiedPort, boundPort, "Server should avoid the occupied port");
                assertTrue(boundPort > 0, "Server should bind an alternate port");
                assertNotNull(collidingDb.consoleUrl());
                System.out.println("[TEST EVIDENCE] Intelligent port management resolved occupied port "
                        + occupiedPort + " to available port " + boundPort + " (URL: " + collidingDb.consoleUrl() + ")");
            } finally {
                collidingDb.close();
            }
        }
    }

    // --- STEP 2: Security & Unauthenticated Access Enforcement ---
    @Test
    @DisplayName("Feature 2: Security Enforcement - Rejection of unauthenticated requests")
    void testSecurityEnforcement() throws Exception {
        // Unauthenticated request to protected endpoint should yield 401
        Response resp = execute("GET", "/api/collections", null, null, null);
        assertEquals(401, resp.code, "Unauthenticated request must return 401 Unauthorized");
        assertTrue(resp.body.contains("Unauthorized"), "Response should explain unauthorized error");

        // Request with invalid API key should also yield 401
        Response badKeyResp = execute("GET", "/api/collections", null, "bad-key", null);
        assertEquals(401, badKeyResp.code, "Request with invalid API key must return 401");
        System.out.println("[TEST EVIDENCE] Security barrier verified: 401 returned for unauthenticated and bad-key requests");
    }

    // --- STEP 3 & 4: Authentication via Credentials and API Key ---
    @Test
    @DisplayName("Feature 3 & 4: Authentication via Username/Password and API Key")
    void testAuthenticationFlows() throws Exception {
        // 3. Login with bad password -> 401
        String badLogin = "{\"username\":\"admin\",\"password\":\"wrong-pass\"}";
        Response badLoginResp = execute("POST", "/api/auth/login", badLogin, null, null);
        assertEquals(401, badLoginResp.code, "Invalid credentials must return 401");

        // 4. Login with valid credentials -> 200, returns token and cookie
        String goodLogin = "{\"username\":\"admin\",\"password\":\"" + ADMIN_PASS + "\"}";
        Response goodLoginResp = execute("POST", "/api/auth/login", goodLogin, null, null);
        assertEquals(200, goodLoginResp.code, "Valid credentials must return 200 OK");
        assertTrue(goodLoginResp.body.contains("authenticated"));
        assertTrue(goodLoginResp.body.contains("session"));
        assertNotNull(goodLoginResp.cookieHeader, "Login response must set session cookie");

        this.cookie = extractCookie(goodLoginResp.cookieHeader);
        assertNotNull(this.cookie, "Session cookie must be extracted");
        System.out.println("[TEST EVIDENCE] Login authenticated successfully; cookie acquired: " + this.cookie);

        // 5. Query protected endpoint using the session cookie
        Response cookieAuthResp = execute("GET", "/api/collections", null, null, this.cookie);
        assertEquals(200, cookieAuthResp.code, "Session cookie must authorize access");

        // 6. Query protected endpoint using X-API-Key header
        Response apiKeyResp = execute("GET", "/api/collections", null, API_KEY, null);
        assertEquals(200, apiKeyResp.code, "X-API-Key header must authorize access");
    }

    // --- STEP 5: Static Web Console UI Asset Serving ---
    @Test
    @DisplayName("Feature 5: Static Web Console Asset Serving (HTML, CSS, JS, SVG)")
    void testStaticConsoleServing() throws Exception {
        // Root / should serve index.html
        Response rootResp = execute("GET", "/", null, null, null);
        assertEquals(200, rootResp.code);
        assertTrue(rootResp.body.contains("EmbedJNoSQL"), "Root must serve EmbedJNoSQL Web Console");

        // /index.html
        Response indexResp = execute("GET", "/index.html", null, null, null);
        assertEquals(200, indexResp.code);
        assertTrue(indexResp.body.contains("<!DOCTYPE html>"));

        // /login.html
        Response loginResp = execute("GET", "/login.html", null, null, null);
        assertEquals(200, loginResp.code);
        assertTrue(loginResp.body.contains("EmbedJNoSQL"));

        // /css/enhancements.css
        Response cssResp = execute("GET", "/css/enhancements.css", null, null, null);
        assertEquals(200, cssResp.code);

        // /js/enhancements.js
        Response jsResp = execute("GET", "/js/enhancements.js", null, null, null);
        assertEquals(200, jsResp.code);

        // /logo.svg
        Response logoResp = execute("GET", "/logo.svg", null, null, null);
        assertEquals(200, logoResp.code);

        System.out.println("[TEST EVIDENCE] All static console UI assets successfully served with HTTP 200 OK");
    }

    // --- STEP 6: Overview, Health & Metrics Telemetry ---
    @Test
    @DisplayName("Feature 6: Overview & Database Health / Metrics Telemetry")
    void testHealthAndMetrics() throws Exception {
        loginAdmin();

        // /api/health
        Response healthResp = execute("GET", "/api/health", null, null, cookie);
        assertEquals(200, healthResp.code);
        assertTrue(healthResp.body.contains("ok") || healthResp.body.contains("UP"));

        // /api/metrics
        Response metricsResp = execute("GET", "/api/metrics", null, null, cookie);
        assertEquals(200, metricsResp.code);
        assertTrue(metricsResp.body.contains("heapUsed") || metricsResp.body.contains("totalOperations"));

        System.out.println("[TEST EVIDENCE] Health and Metrics telemetry verified: " + healthResp.body);
    }

    // --- STEP 7: Document Collections CRUD ---
    @Test
    @DisplayName("Feature 7: Document Collections CRUD (Create, Read, Update, Delete)")
    void testDocumentCollectionsCrud() throws Exception {
        loginAdmin();

        // 1. List initial collections
        Response listResp = execute("GET", "/api/collections", null, null, cookie);
        assertEquals(200, listResp.code);

        // 2. Insert new document
        String docJson = "{\"id\":\"prod-console-1\",\"name\":\"Mechanical Keyboard\",\"price\":149.99,\"category\":\"Accessories\"}";
        Response insertResp = execute("POST", "/api/collections/products", docJson, null, cookie);
        assertTrue(insertResp.code == 200 || insertResp.code == 201, "Insert should succeed with 200 or 201");
        assertTrue(insertResp.body.contains("prod-console-1"));

        // 3. Read back document
        Response getResp = execute("GET", "/api/collections/products/prod-console-1", null, null, cookie);
        assertEquals(200, getResp.code);
        assertTrue(getResp.body.contains("Mechanical Keyboard"));

        // 4. Update document
        String updateJson = "{\"id\":\"prod-console-1\",\"name\":\"Ergonomic Mechanical Keyboard\",\"price\":179.99,\"category\":\"Accessories\"}";
        Response updateResp = execute("PUT", "/api/collections/products/prod-console-1", updateJson, null, cookie);
        assertTrue(updateResp.code == 200 || updateResp.code == 201, "Update should return 200 or 201");

        // Verify update
        Response verifyUpdateResp = execute("GET", "/api/collections/products/prod-console-1", null, null, cookie);
        assertEquals(200, verifyUpdateResp.code);
        assertTrue(verifyUpdateResp.body.contains("Ergonomic Mechanical Keyboard"));

        // 5. Delete document
        Response deleteResp = execute("DELETE", "/api/collections/products/prod-console-1", null, null, cookie);
        assertTrue(deleteResp.code == 200 || deleteResp.code == 204, "Delete should return 200 or 204");

        // Verify deletion
        Response verifyDeleteResp = execute("GET", "/api/collections/products/prod-console-1", null, null, cookie);
        assertEquals(404, verifyDeleteResp.code, "Deleted document should return 404 Not Found");

        System.out.println("[TEST EVIDENCE] Document CRUD full lifecycle successfully verified");
    }

    // --- STEP 8: Query Engine ---
    @Test
    @DisplayName("Feature 8: Query Engine (Filtering & Selection)")
    void testQueryEngine() throws Exception {
        loginAdmin();

        // Seed 2 documents
        execute("POST", "/api/collections/items", "{\"id\":\"item-q1\",\"name\":\"Laptop\",\"category\":\"Electronics\"}", null, cookie);
        execute("POST", "/api/collections/items", "{\"id\":\"item-q2\",\"name\":\"Novel\",\"category\":\"Books\"}", null, cookie);

        // Query by equality: category = Electronics
        String queryJson = "{\"$eq\":{\"category\":\"Electronics\"}}";
        Response queryResp = execute("POST", "/api/collections/items/query", queryJson, null, cookie);
        assertEquals(200, queryResp.code);
        assertTrue(queryResp.body.contains("item-q1"), "Query should match Laptop");
        assertFalse(queryResp.body.contains("item-q2"), "Query should not match Novel");

        System.out.println("[TEST EVIDENCE] Query runner returned exact filtered match: " + queryResp.body);
    }

    // --- STEP 9: Key-Value Storage Buckets ---
    @Test
    @DisplayName("Feature 9: Key-Value Buckets (Put, Get, Delete)")
    void testKeyValueBuckets() throws Exception {
        loginAdmin();

        // Put key
        String kvPut = "{\"value\":\"dark-mode-activated\"}";
        Response putResp = execute("POST", "/api/kv/ui_settings/theme", kvPut, null, cookie);
        assertTrue(putResp.code == 200 || putResp.code == 201, "KV Put should return 200 or 201");

        // Get key
        Response getResp = execute("GET", "/api/kv/ui_settings/theme", null, null, cookie);
        assertEquals(200, getResp.code);
        assertTrue(getResp.body.contains("dark-mode-activated"));

        // Delete key
        Response delResp = execute("DELETE", "/api/kv/ui_settings/theme", null, null, cookie);
        assertTrue(delResp.code == 200 || delResp.code == 204, "KV Delete should return 200 or 204");

        // Get deleted key
        Response verifyDelResp = execute("GET", "/api/kv/ui_settings/theme", null, null, cookie);
        assertTrue(verifyDelResp.code == 404 || verifyDelResp.body.contains("null") || verifyDelResp.body.isEmpty());

        System.out.println("[TEST EVIDENCE] Key-Value operations verified");
    }

    // --- STEP 10: Column Families (Wide-Column) ---
    @Test
    @DisplayName("Feature 10: Column Families (Wide-Column Operations)")
    void testColumnFamilies() throws Exception {
        loginAdmin();

        // Put column family row
        String cfPut = "{\"columns\":{\"status\":\"active\",\"tier\":\"gold\"}}";
        Response putResp = execute("POST", "/api/columns/profiles/user-99", cfPut, null, cookie);
        assertTrue(putResp.code == 200 || putResp.code == 201, "Column family put should return 200 or 201");

        // Get column family row
        Response getResp = execute("GET", "/api/columns/profiles/user-99", null, null, cookie);
        assertEquals(200, getResp.code);
        assertTrue(getResp.body.contains("gold"));

        System.out.println("[TEST EVIDENCE] Wide-Column family verified: " + getResp.body);
    }

    // --- STEP 11: Schema Definition & Validation ---
    @Test
    @DisplayName("Feature 11: Schema Definition Management")
    void testSchemaDefinition() throws Exception {
        loginAdmin();

        String schemaJson = "{\"fields\":{\"title\":{\"type\":\"STRING\",\"required\":true}}}";
        Response putSchema = execute("POST", "/api/schema/articles", schemaJson, null, cookie);
        assertTrue(putSchema.code == 200 || putSchema.code == 201, "Schema put should return 200 or 201");

        Response getSchema = execute("GET", "/api/schema/articles", null, null, cookie);
        assertEquals(200, getSchema.code);
        assertTrue(getSchema.body.contains("String") || getSchema.body.contains("STRING"));

        System.out.println("[TEST EVIDENCE] Schema definition verified: " + getSchema.body);
    }

    // --- STEP 12: Transactions Inspector ---
    @Test
    @DisplayName("Feature 12: Transactions Inspector & MVCC Status")
    void testTransactionsInspector() throws Exception {
        loginAdmin();

        Response txResp = execute("GET", "/api/transactions", null, null, cookie);
        assertEquals(200, txResp.code);
        System.out.println("[TEST EVIDENCE] Transactions inspector returned status: " + txResp.body);
    }

    // --- STEP 13: Indexes Inspector ---
    @Test
    @DisplayName("Feature 13: Indexes Inspector")
    void testIndexesInspector() throws Exception {
        loginAdmin();

        // R-64: listing indexes no longer creates the collection it is asked about, so seed
        // a real collection. Asking about a name that does not exist is now a 404; before the
        // fix this call auto-created `products` and returned 200 {}, which is why the test
        // passed without one.
        db.documentCollection("products").insert(
                org.embeddedjnosql.db.nosql.document.Document.of("name", "Keyboard"));

        Response idxResp = execute("GET", "/api/indexes/products", null, null, cookie);
        assertEquals(200, idxResp.code);
        System.out.println("[TEST EVIDENCE] Indexes inspector verified: " + idxResp.body);
    }

    // --- STEP 14: Backup & Restore ---
    @Test
    @DisplayName("Feature 14: Backup Export & Restore")
    void testBackupAndRestore() throws Exception {
        loginAdmin();

        // Seed some data first
        execute("POST", "/api/collections/backup_test", "{\"id\":\"b1\",\"value\":\"original\"}", null, cookie);

        // Export backup
        Response backupResp = execute("GET", "/api/backup", null, null, cookie);
        assertEquals(200, backupResp.code);
        assertTrue(backupResp.body.contains("backup_test"));

        // Restore backup
        Response restoreResp = execute("POST", "/api/backup", backupResp.body, null, cookie);
        assertEquals(200, restoreResp.code);

        System.out.println("[TEST EVIDENCE] Database backup export and restore verified");
    }

    // --- STEP 15: Audit Trail Logs ---
    @Test
    @DisplayName("Feature 15: Audit Trail Logs Verification")
    void testAuditTrailLogs() throws Exception {
        loginAdmin();

        // Perform an insert to trigger audit event
        execute("POST", "/api/collections/audit_test", "{\"id\":\"a1\",\"val\":1}", null, cookie);

        // Query audit logs
        Response auditResp = execute("GET", "/api/audit/logs", null, null, cookie);
        assertEquals(200, auditResp.code);
        assertTrue(auditResp.body.contains("events"), "Audit logs must contain events list");
        assertTrue(auditResp.body.contains("LOGIN") || auditResp.body.contains("INSERT"),
                "Audit logs must record performed operations");

        System.out.println("[TEST EVIDENCE] Audit log trail records verified: " + auditResp.body);
    }

    // --- STEP 16: Logout & Session Invalidation ---
    @Test
    @DisplayName("Feature 16: Logout and Session Invalidation")
    void testLogoutAndSessionInvalidation() throws Exception {
        loginAdmin();

        // Verify session is active
        Response authCheck = execute("GET", "/api/collections", null, null, cookie);
        assertEquals(200, authCheck.code);

        // Call logout
        Response logoutResp = execute("POST", "/api/auth/logout", null, null, cookie);
        assertEquals(200, logoutResp.code);
        assertTrue(logoutResp.body.contains("logged_out"));

        // Attempt accessing protected endpoint with invalidated cookie -> 401
        Response postLogout = execute("GET", "/api/collections", null, null, cookie);
        assertEquals(401, postLogout.code, "Invalidated session must return 401 Unauthorized");

        System.out.println("[TEST EVIDENCE] Logout cleanly invalidated session; subsequent requests rejected with 401");
    }

    // --- STEP 17: Vector Similarity Search ---
    @Test
    @DisplayName("Feature 17: Vector Similarity Search & HNSW Indexing")
    void testVectorSimilaritySearch() throws Exception {
        loginAdmin();

        // 1. Create a 128-dimensional vector
        StringBuilder vecStr = new StringBuilder("[");
        for (int i = 0; i < 128; i++) {
            if (i > 0) vecStr.append(",");
            vecStr.append(i == 0 ? "1.0" : "0.0");
        }
        vecStr.append("]");

        String insertVecJson = "{\"id\":\"vec-doc-1\",\"vector\":" + vecStr + "}";
        Response insertResp = execute("POST", "/api/vectors/embeddings/vec-doc-1", insertVecJson, null, cookie);
        assertTrue(insertResp.code == 200 || insertResp.code == 201, "Vector insert should succeed with 200 or 201");

        // 2. Perform vector search with k=1
        String searchJson = "{\"vector\":" + vecStr + ",\"k\":1}";
        Response searchResp = execute("POST", "/api/vectors/embeddings/search", searchJson, null, cookie);
        assertEquals(200, searchResp.code);
        assertTrue(searchResp.body.contains("vec-doc-1"), "Search should retrieve nearest neighbor vec-doc-1");

        System.out.println("[TEST EVIDENCE] Vector similarity search returned nearest neighbor: " + searchResp.body);
    }

    // --- Helper Methods ---

    private void loginAdmin() throws Exception {
        String loginJson = "{\"username\":\"admin\",\"password\":\"" + ADMIN_PASS + "\"}";
        Response resp = execute("POST", "/api/auth/login", loginJson, null, null);
        assertEquals(200, resp.code);
        this.cookie = extractCookie(resp.cookieHeader);
        assertNotNull(this.cookie);
    }

    private String extractCookie(String setCookieHeader) {
        if (setCookieHeader == null) return null;
        for (String part : setCookieHeader.split(";")) {
            String trimmed = part.trim();
            if (trimmed.toUpperCase(java.util.Locale.ROOT).startsWith("EMBED_SESSION=")) {
                return trimmed;
            }
        }
        return null;
    }

    private static class Response {
        final int code;
        final String body;
        final String cookieHeader;

        Response(int code, String body, String cookieHeader) {
            this.code = code;
            this.body = body;
            this.cookieHeader = cookieHeader;
        }
    }

    private Response execute(String method, String path, String body, String apiKeyHeader, String cookieHeader) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("http://localhost:" + port + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            if (apiKeyHeader != null) {
                conn.setRequestProperty("X-API-Key", apiKeyHeader);
            }
            if (cookieHeader != null) {
                conn.setRequestProperty("Cookie", cookieHeader);
            }
            if (body != null) {
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(body.getBytes(StandardCharsets.UTF_8));
                }
            }

            int code = conn.getResponseCode();
            String setCookie = conn.getHeaderField("Set-Cookie");
            var stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String responseBody = "";
            if (stream != null) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    responseBody = sb.toString();
                }
            }
            return new Response(code, responseBody, setCookie);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }
}

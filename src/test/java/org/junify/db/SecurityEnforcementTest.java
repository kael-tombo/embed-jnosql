package org.junify.db;

import org.junify.db.config.ConsoleConfig;
import org.junify.db.config.JunifyDBConfig;
import org.junify.db.config.SecurityConfig;
import org.junit.jupiter.api.*;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Administration Console Security Enforcement & Hardening Tests")
class SecurityEnforcementTest {

    private static final String ADMIN_USER = "admin";
    private static final String ADMIN_PASS = "super-secret-password-123";
    private static final String API_KEY = "test-security-api-key";

    private JunifyDB db;
    private int port;
    private String baseUrl;

    @BeforeEach
    void setUp() throws Exception {
        db = JunifyDB.embed()
                .storageEngine(JunifyDBConfig.StorageEngineType.IN_MEMORY)
                .console(ConsoleConfig.builder()
                        .enabled(true)
                        .port(0)
                        .host("127.0.0.1")
                        .intelligentPort(true)
                        .build())
                .security(SecurityConfig.builder()
                        .authEnabled(true)
                        .adminUsername(ADMIN_USER)
                        .adminPassword(ADMIN_PASS)
                        .apiKey(API_KEY)
                        .csrfEnabled(true) // Explicitly enable CSRF for security validation
                        .rateLimitEnabled(true)
                        .rateLimitRequestsPerMinute(50)
                        .bruteForceProtectionEnabled(true)
                        .maxFailedLoginAttempts(3)
                        .lockoutDurationMs(60_000L)
                        .securityHeadersEnabled(true)
                        .build())
                .build();

        port = db.consolePort();
        baseUrl = "http://127.0.0.1:" + port;
    }

    @AfterEach
    void tearDown() {
        if (db != null && db.isOpen()) {
            db.close();
        }
    }

    @Test
    @DisplayName("Security 1: Anonymous request to protected endpoint is blocked (401)")
    void testAnonymousAccessBlocked() throws Exception {
        Response resp = execute("GET", "/api/collections", null, null, null, null);
        assertEquals(401, resp.code);
        assertTrue(resp.body.contains("Unauthorized"));
    }

    @Test
    @DisplayName("Security 2: Security headers are present on all responses")
    void testSecurityHeadersPresent() throws Exception {
        Response resp = execute("GET", "/index.html", null, null, null, null);
        assertEquals(200, resp.code);

        assertEquals("nosniff", resp.headers.get("x-content-type-options"));
        assertEquals("DENY", resp.headers.get("x-frame-options"));
        assertEquals("1; mode=block", resp.headers.get("x-xss-protection"));
        assertEquals("strict-origin-when-cross-origin", resp.headers.get("referrer-policy"));
        assertNotNull(resp.headers.get("content-security-policy"));
    }

    @Test
    @DisplayName("Security 3: Brute force lockout after N failed login attempts (429)")
    void testBruteForceLockout() throws Exception {
        String badCreds = "{\"username\":\"admin\",\"password\":\"wrong-password\"}";

        // Attempt 1, 2, 3 -> 401 Unauthorized
        for (int i = 1; i <= 3; i++) {
            Response resp = execute("POST", "/api/auth/login", badCreds, null, null, null);
            assertEquals(401, resp.code, "Failed attempt " + i + " should return 401");
        }

        // Attempt 4 -> 429 Too Many Requests (Account / IP locked out)
        Response lockedResp = execute("POST", "/api/auth/login", badCreds, null, null, null);
        assertEquals(429, lockedResp.code, "4th attempt must be blocked with 429 Too Many Requests");
        assertTrue(lockedResp.body.contains("Too many failed attempts"), "Response body must explain lockout");
    }

    @Test
    @DisplayName("Security 4: CSRF token validation on mutating endpoints")
    void testCsrfProtection() throws Exception {
        // 1. Login with valid credentials
        String goodCreds = "{\"username\":\"" + ADMIN_USER + "\",\"password\":\"" + ADMIN_PASS + "\"}";
        Response loginResp = execute("POST", "/api/auth/login", goodCreds, null, null, null);
        assertEquals(200, loginResp.code);
        String cookie = extractCookie(loginResp.cookieHeader);
        assertNotNull(cookie, "Session cookie must be set");

        String csrfToken = loginResp.headers.get("x-csrf-token");
        assertNotNull(csrfToken, "X-CSRF-Token must be issued upon login");

        // 2. Mutating request with cookie but WITHOUT CSRF token -> 403 Forbidden
        String docJson = "{\"name\":\"Test Item\",\"price\":99.99}";
        Response noCsrfResp = execute("POST", "/api/collections/items", docJson, null, cookie, null);
        assertEquals(403, noCsrfResp.code, "Mutating request without CSRF token must return 403 Forbidden");
        assertTrue(noCsrfResp.body.contains("CSRF"), "Response body must explain CSRF error");

        // 3. Mutating request with cookie and invalid CSRF token -> 403 Forbidden
        Response badCsrfResp = execute("POST", "/api/collections/items", docJson, null, cookie, "invalid-token");
        assertEquals(403, badCsrfResp.code, "Mutating request with invalid CSRF token must return 403 Forbidden");

        // 4. Mutating request with cookie AND valid CSRF token -> 200/201 OK
        Response validCsrfResp = execute("POST", "/api/collections/items", docJson, null, cookie, csrfToken);
        assertTrue(validCsrfResp.code == 200 || validCsrfResp.code == 201, "Mutating request with valid CSRF token should succeed");
    }

    @Test
    @DisplayName("Security 5: Logout invalidates session cookie and CSRF tokens")
    void testLogoutInvalidation() throws Exception {
        // Login
        String goodCreds = "{\"username\":\"" + ADMIN_USER + "\",\"password\":\"" + ADMIN_PASS + "\"}";
        Response loginResp = execute("POST", "/api/auth/login", goodCreds, null, null, null);
        assertEquals(200, loginResp.code);
        String cookie = extractCookie(loginResp.cookieHeader);

        // Verify authorized access
        Response accessBefore = execute("GET", "/api/collections", null, null, cookie, null);
        assertEquals(200, accessBefore.code);

        // Logout
        Response logoutResp = execute("POST", "/api/auth/logout", null, null, cookie, null);
        assertEquals(200, logoutResp.code);

        // Access after logout -> 401 Unauthorized
        Response accessAfter = execute("GET", "/api/collections", null, null, cookie, null);
        assertEquals(401, accessAfter.code, "Session must be invalidated after logout");
    }

    // Helper method
    private Response execute(String method, String endpoint, String body, String apiKey, String cookie, String csrfToken) throws Exception {
        URL url = URI.create(baseUrl + endpoint).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setInstanceFollowRedirects(false);

        if (apiKey != null) {
            conn.setRequestProperty("X-API-Key", apiKey);
        }
        if (cookie != null) {
            conn.setRequestProperty("Cookie", cookie);
        }
        if (csrfToken != null) {
            conn.setRequestProperty("X-CSRF-Token", csrfToken);
        }

        if (body != null) {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }

        int code = conn.getResponseCode();
        String cookieHeader = conn.getHeaderField("Set-Cookie");

        var headers = new java.util.HashMap<String, String>();
        for (Map.Entry<String, List<String>> entry : conn.getHeaderFields().entrySet()) {
            if (entry.getKey() != null && !entry.getValue().isEmpty()) {
                headers.put(entry.getKey().toLowerCase(), entry.getValue().get(0));
            }
        }

        InputStream stream = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
        String respBody = "";
        if (stream != null) {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                respBody = sb.toString();
            }
        }

        return new Response(code, respBody, cookieHeader, headers);
    }

    private String extractCookie(String setCookieHeader) {
        if (setCookieHeader == null) return null;
        String[] parts = setCookieHeader.split(";");
        return parts[0].trim();
    }

    private record Response(int code, String body, String cookieHeader, Map<String, String> headers) {}
}

package org.embeddedjnosql.db;

import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.embeddedjnosql.db.config.SecurityConfig;
import org.embeddedjnosql.db.console.http.EmbedJNoSQLServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;

import static org.junit.jupiter.api.Assertions.*;

/**
 * R-61 — the standalone server must obey the CORS policy the configuration system
 * documents, on every path that can start it.
 *
 * <p>The defect these tests pin down: {@code EmbedJNoSQL.startServer(port)} applied the
 * security config <em>only when {@code authEnabled}</em>, so the default no-auth server
 * (and the {@code --api-key} CLI path, which enables auth after the server starts)
 * kept {@code EmbedJNoSQLServer}'s field defaults — {@code corsEnabled=true} with
 * {@code allowedOrigins="*"} — while {@code SecurityConfig.disabled()} documents
 * "secure default: CORS disabled" and {@code AdminConsoleConfigTest} asserts it. The
 * config object was correct; the running server was not.
 *
 * <p>Severity: with auth disabled and {@code Access-Control-Allow-Origin: *}, any web
 * page the operator visits can read the whole database cross-origin, because a
 * wildcard origin makes the response body readable without credentials.
 *
 * <p>Also covered: {@code /api/metrics/stream} used to emit
 * {@code Access-Control-Allow-Origin: *} unconditionally, bypassing both the flag and
 * the allowlist.
 */
@DisplayName("R-61 — CORS policy is honoured on standalone server paths")
class CorsPolicyConsistencyTest {

    private static final String FOREIGN_ORIGIN = "https://evil.example";

    private EmbedJNoSQL db;
    private EmbedJNoSQLServer server;

    @AfterEach
    void tearDown() throws Exception {
        System.clearProperty("embedjnosql.security.cors-enabled");
        System.clearProperty("embedjnosql.security.allowed-origins");
        if (db != null) {
            db.close();
        }
    }

    /** A default embedded database with no console and no security configuration. */
    private EmbedJNoSQL inMemoryDb() {
        return EmbedJNoSQLConfig.builder()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY)
                .build();
    }

    private HttpURLConnection request(String url, String method) throws IOException {
        var conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setRequestProperty("Origin", FOREIGN_ORIGIN);
        conn.setConnectTimeout(5_000);
        conn.setReadTimeout(5_000);
        return conn;
    }

    @Test
    @DisplayName("no-auth server answers without advertising wildcard CORS")
    void defaultServerDoesNotAdvertiseCors() throws Exception {
        db = inMemoryDb();
        server = db.startServer(0);
        var url = "http://127.0.0.1:" + server.port() + "/api/health";

        var conn = request(url, "GET");
        try {
            assertEquals(200, conn.getResponseCode(), "health check should still be reachable");
            assertNull(conn.getHeaderField("Access-Control-Allow-Origin"),
                    "CORS must be off by default: SecurityConfig documents 'secure default: CORS disabled'");
        } finally {
            conn.disconnect();
        }
    }

    @Test
    @DisplayName("preflight on a data endpoint is not answered with CORS headers when disabled")
    void preflightDoesNotAdvertiseCorsWhenDisabled() throws Exception {
        db = inMemoryDb();
        server = db.startServer(0);
        var url = "http://127.0.0.1:" + server.port() + "/api/health";

        var conn = request(url, "OPTIONS");
        conn.setRequestProperty("Access-Control-Request-Method", "GET");
        try {
            conn.getResponseCode();
            assertNull(conn.getHeaderField("Access-Control-Allow-Origin"),
                    "a disabled CORS policy must not answer preflights with an allow-origin");
        } finally {
            conn.disconnect();
        }
    }

    @Test
    @DisplayName("the metrics SSE stream honours the CORS policy instead of forcing a wildcard")
    void metricsStreamDoesNotAdvertiseCors() throws Exception {
        db = inMemoryDb();
        server = db.startServer(0);
        var url = "http://127.0.0.1:" + server.port() + "/api/metrics/stream";

        var conn = request(url, "GET");
        try {
            conn.getResponseCode();
            assertNull(conn.getHeaderField("Access-Control-Allow-Origin"),
                    "/api/metrics/stream must not bypass the CORS policy with a forced wildcard");
        } finally {
            conn.disconnect(); // long-lived stream: headers are all this assertion needs
        }
    }

    @Test
    @DisplayName("an explicit origin allowlist is echoed, with credentials, when CORS is enabled")
    void enabledCorsEchoesExplicitAllowlist() throws Exception {
        db = inMemoryDb();
        server = db.startServer(0);
        server.applySecurityConfig(SecurityConfig.builder()
                .corsEnabled(true)
                .allowedOrigins("https://app.example")
                .build());
        var url = "http://127.0.0.1:" + server.port() + "/api/health";

        var conn = request(url, "GET");
        try {
            conn.getResponseCode();
            assertEquals("https://app.example", conn.getHeaderField("Access-Control-Allow-Origin"),
                    "an enabled CORS policy must echo the configured origin");
            assertEquals("true", conn.getHeaderField("Access-Control-Allow-Credentials"),
                    "credentialed CORS is valid against an explicit origin");
        } finally {
            conn.disconnect();
        }
    }

    @Test
    @DisplayName("an enabled wildcard policy never claims credential support")
    void enabledWildcardCorsDoesNotClaimCredentials() throws Exception {
        db = inMemoryDb();
        server = db.startServer(0);
        server.applySecurityConfig(SecurityConfig.builder()
                .corsEnabled(true)
                .allowedOrigins("*")
                .build());
        var url = "http://127.0.0.1:" + server.port() + "/api/health";

        var conn = request(url, "GET");
        try {
            conn.getResponseCode();
            assertEquals("*", conn.getHeaderField("Access-Control-Allow-Origin"));
            assertNull(conn.getHeaderField("Access-Control-Allow-Credentials"),
                    "browsers reject wildcard + credentials; the server must not claim it");
        } finally {
            conn.disconnect();
        }
    }

    @Test
    @DisplayName("CORS is configurable without an API key, via the documented property")
    void corsIsReachableWithoutApiKey() throws Exception {
        System.setProperty("embedjnosql.security.cors-enabled", "true");
        System.setProperty("embedjnosql.security.allowed-origins", "https://app.example");

        db = inMemoryDb();
        server = db.startServer(0);
        var url = "http://127.0.0.1:" + server.port() + "/api/health";

        var conn = request(url, "GET");
        try {
            conn.getResponseCode();
            assertEquals("https://app.example", conn.getHeaderField("Access-Control-Allow-Origin"),
                    "embedjnosql.security.cors-enabled must reach an unauthenticated server; "
                            + "before R-61 the security config was only applied when auth was on");
        } finally {
            conn.disconnect();
        }
    }
}

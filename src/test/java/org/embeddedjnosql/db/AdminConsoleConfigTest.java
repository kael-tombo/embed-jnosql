package org.embeddedjnosql.db;

import org.embeddedjnosql.db.config.ConfigurationResolver;
import org.embeddedjnosql.db.config.ConsoleConfig;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.config.SecurityConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Admin Console Configuration & Precedence Tests")
class AdminConsoleConfigTest {

    @Test
    @DisplayName("Verify secure defaults for ConsoleConfig")
    void testConsoleConfigDefaults() {
        ConsoleConfig config = ConsoleConfig.builder().build();

        assertEquals(9090, config.port());
        assertEquals("127.0.0.1", config.host());
        assertEquals("/", config.contextPath());
        assertEquals("http", config.scheme());
        assertTrue(config.intelligentPort());
        assertTrue(config.localhostOnly());
        assertFalse(config.failIfPreferredPortUnavailable());
        assertEquals(10_000L, config.startupTimeoutMs());
        assertEquals(50, config.maxPortAttempts());
        assertTrue(config.minPort() <= config.maxPort());
    }

    @Test
    @DisplayName("Verify context path normalization")
    void testContextPathNormalization() {
        ConsoleConfig c1 = ConsoleConfig.builder().contextPath("admin").build();
        assertEquals("/admin", c1.contextPath());

        ConsoleConfig c2 = ConsoleConfig.builder().contextPath("/admin/").build();
        assertEquals("/admin", c2.contextPath());

        ConsoleConfig c3 = ConsoleConfig.builder().contextPath("").build();
        assertEquals("/", c3.contextPath());

        ConsoleConfig c4 = ConsoleConfig.builder().contextPath(null).build();
        assertEquals("/", c4.contextPath());
    }

    @Test
    @DisplayName("Verify port range and bounds validation")
    void testPortRangeValidation() {
        // Valid bounds
        assertDoesNotThrow(() -> ConsoleConfig.builder().port(8080).portRange(8000, 9000).build());
        assertDoesNotThrow(() -> ConsoleConfig.builder().port(0).build()); // ephemeral port

        // Invalid port < 0
        assertThrows(IllegalArgumentException.class, () -> ConsoleConfig.builder().port(-1).build());

        // Invalid port > 65535
        assertThrows(IllegalArgumentException.class, () -> ConsoleConfig.builder().port(70000).build());

        // minPort > maxPort
        assertThrows(IllegalArgumentException.class, () -> ConsoleConfig.builder().portRange(9000, 8000).build());

        // Invalid scheme
        assertThrows(IllegalArgumentException.class, () -> ConsoleConfig.builder().scheme("ftp").build());
    }

    @Test
    @DisplayName("Console is opt-in: the core starts no HTTP server by default")
    void testNoHttpServerByDefault() {
        // Runtime level: opening an embedded database binds no socket and still serves data.
        EmbedJNoSQL db = EmbedJNoSQL.inMemory();
        try {
            // Config level: the default configuration must not enable the console.
            assertFalse(db.config().consoleConfig().enabled(),
                    "the embedded console must be explicitly enabled");
            assertNull(db.consoleServer(), "in-memory embedding must not start an HTTP server");
            assertNull(db.consoleUrl());
            assertEquals(-1, db.consolePort());

            db.documentCollection("no_console_probe")
                    .insert(Document.of("name", "value").id("probe-1"));
            assertEquals(1, db.documentCollection("no_console_probe").count(),
                    "core read/write must work without any console");
        } finally {
            db.close();
        }
    }

    @Test
    @DisplayName("Verify secure defaults for SecurityConfig")
    void testSecurityConfigDefaults() {
        SecurityConfig config = SecurityConfig.builder().build();

        assertFalse(config.authEnabled());
        assertFalse(config.corsEnabled(), "CORS should be disabled by default for security");
        assertNull(config.allowedOrigins());
        assertFalse(config.csrfEnabled(), "CSRF is opt-in by default to prevent breaking legacy clients");
        assertTrue(config.rateLimitEnabled());
        assertEquals(120, config.rateLimitRequestsPerMinute());
        assertTrue(config.bruteForceProtectionEnabled());
        assertEquals(5, config.maxFailedLoginAttempts());
        assertTrue(config.securityHeadersEnabled());
        assertEquals("TLSv1.2", config.minTlsVersion());
    }

    @Test
    @DisplayName("Verify ConfigurationResolver overrides via System Properties")
    void testSystemPropertyResolution() {
        String originalPort = System.getProperty("embedjnosql.console.port");
        String originalHost = System.getProperty("embedjnosql.console.host");
        String originalAuth = System.getProperty("embedjnosql.security.auth-enabled");

        try {
            System.setProperty("embedjnosql.console.port", "9876");
            System.setProperty("embedjnosql.console.host", "127.0.0.1");
            System.setProperty("embedjnosql.security.auth-enabled", "true");

            ConsoleConfig baseConsole = ConsoleConfig.builder().port(8080).build();
            ConsoleConfig resolvedConsole = ConfigurationResolver.resolveConsoleConfig(baseConsole);
            assertEquals(9876, resolvedConsole.port(), "System property should override base console port");

            SecurityConfig baseSecurity = SecurityConfig.builder().authEnabled(false).build();
            SecurityConfig resolvedSecurity = ConfigurationResolver.resolveSecurityConfig(baseSecurity);
            assertTrue(resolvedSecurity.authEnabled(), "System property should override base security auth");
        } finally {
            if (originalPort != null) System.setProperty("embedjnosql.console.port", originalPort);
            else System.clearProperty("embedjnosql.console.port");

            if (originalHost != null) System.setProperty("embedjnosql.console.host", originalHost);
            else System.clearProperty("embedjnosql.console.host");

            if (originalAuth != null) System.setProperty("embedjnosql.security.auth-enabled", originalAuth);
            else System.clearProperty("embedjnosql.security.auth-enabled");
        }
    }
}

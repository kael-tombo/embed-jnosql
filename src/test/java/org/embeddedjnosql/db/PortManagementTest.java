package org.embeddedjnosql.db;

import org.embeddedjnosql.db.config.ConsoleConfig;
import org.embeddedjnosql.db.console.http.PortConflictException;
import org.embeddedjnosql.db.console.http.PortManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Administration Console Port Management & Collision Tests")
class PortManagementTest {

    @Test
    @DisplayName("Ephemeral port 0 binds successfully and returns active port")
    void testEphemeralPortBinding() throws IOException {
        ConsoleConfig config = ConsoleConfig.builder()
                .port(0)
                .host("127.0.0.1")
                .build();

        PortManager.BindingResult result = PortManager.bindServer(config);
        try {
            assertNotNull(result.server());
            assertTrue(result.port() > 0, "Bound port must be > 0");
            assertEquals("127.0.0.1", result.host());
        } finally {
            result.server().stop(0);
        }
    }

    @Test
    @DisplayName("Preferred port available binds to requested port")
    void testPreferredPortAvailable() throws IOException {
        // Find a free port first using a temporary socket
        int freePort;
        try (ServerSocket probe = new ServerSocket(0)) {
            freePort = probe.getLocalPort();
        }

        ConsoleConfig config = ConsoleConfig.builder()
                .port(freePort)
                .host("127.0.0.1")
                .build();

        PortManager.BindingResult result = PortManager.bindServer(config);
        try {
            assertEquals(freePort, result.port(), "Server should bind exactly to the preferred port");
        } finally {
            result.server().stop(0);
        }
    }

    @Test
    @DisplayName("Preferred port occupied with intelligent fallback binds to alternative port in range")
    void testIntelligentFallbackWhenOccupied() throws IOException {
        try (ServerSocket blocker = new ServerSocket(0)) {
            int occupiedPort = blocker.getLocalPort();

            ConsoleConfig config = ConsoleConfig.builder()
                    .port(occupiedPort)
                    .portRange(occupiedPort, occupiedPort + 10)
                    .intelligentPort(true)
                    .failIfPreferredPortUnavailable(false)
                    .host("127.0.0.1")
                    .build();

            PortManager.BindingResult result = PortManager.bindServer(config);
            try {
                assertNotEquals(occupiedPort, result.port(), "Server must avoid the occupied port");
                assertTrue(result.port() > occupiedPort, "Server should bind an alternate port within range");
                assertTrue(result.port() <= occupiedPort + 10);
            } finally {
                result.server().stop(0);
            }
        }
    }

    @Test
    @DisplayName("Preferred port occupied with failIfPreferredPortUnavailable=true throws PortConflictException")
    void testFailIfPreferredPortUnavailable() throws IOException {
        try (ServerSocket blocker = new ServerSocket(0)) {
            int occupiedPort = blocker.getLocalPort();

            ConsoleConfig config = ConsoleConfig.builder()
                    .port(occupiedPort)
                    .intelligentPort(true)
                    .failIfPreferredPortUnavailable(true)
                    .host("127.0.0.1")
                    .build();

            PortConflictException ex = assertThrows(PortConflictException.class, () -> PortManager.bindServer(config));
            assertEquals(occupiedPort, ex.getRequestedPort());
        }
    }

    @Test
    @DisplayName("Port range exhaustion throws deterministic PortConflictException")
    void testRangeExhaustion() throws IOException {
        // Occupy 2 consecutive ports
        try (ServerSocket b1 = new ServerSocket(0)) {
            int p1 = b1.getLocalPort();

            // Configure range of only 1 port (p1)
            ConsoleConfig config = ConsoleConfig.builder()
                    .port(p1)
                    .portRange(p1, p1)
                    .intelligentPort(true)
                    .failIfPreferredPortUnavailable(false)
                    .host("127.0.0.1")
                    .build();

            assertThrows(PortConflictException.class, () -> PortManager.bindServer(config));
        }
    }

    @Test
    @DisplayName("Clean server shutdown releases port for reuse")
    void testCleanShutdownReleasesPort() throws Exception {
        int freePort;
        try (ServerSocket probe = new ServerSocket(0)) {
            freePort = probe.getLocalPort();
        }

        ConsoleConfig config = ConsoleConfig.builder()
                .port(freePort)
                .host("127.0.0.1")
                .build();

        // Start and stop instance
        PortManager.BindingResult r1 = PortManager.bindServer(config);
        assertEquals(freePort, r1.port());
        r1.server().start();
        r1.server().stop(0);

        // Allow OS up to 2 seconds to release socket descriptor
        boolean released = false;
        for (int i = 0; i < 20; i++) {
            if (PortManager.isPortAvailable("127.0.0.1", freePort)) {
                released = true;
                break;
            }
            Thread.sleep(100);
        }

        assertTrue(released, "Port should be available after server stop");
    }
}

package org.embeddedjnosql.db.console.http;

import com.sun.net.httpserver.HttpServer;
import org.embeddedjnosql.db.config.ConsoleConfig;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.logging.Logger;

/**
 * Manages port binding, conflict detection, and intelligent port resolution
 * for the administration console.
 */
public final class PortManager {

    private static final Logger logger = Logger.getLogger(PortManager.class.getName());

    private PortManager() {}

    public record BindingResult(HttpServer server, int port, String host) {}

    /**
     * Binds an {@link HttpServer} based on the rules in the given {@link ConsoleConfig}:
     * 1. Checks the preferred port on the specified host.
     * 2. If occupied and failIfPreferredPortUnavailable=true, throws {@link PortConflictException}.
     * 3. If occupied and intelligentPort=false, throws {@link PortConflictException}.
     * 4. If occupied and intelligentPort=true, iterates through the configured range [minPort, maxPort].
     * 5. If all ports in range are occupied, throws {@link PortConflictException}.
     */
    public static BindingResult bindServer(ConsoleConfig config) throws IOException {
        if (config == null) {
            config = ConsoleConfig.disabled();
        }

        String host = config.host() != null && !config.host().isBlank() ? config.host() : ConsoleConfig.DEFAULT_HOST;
        int preferredPort = config.port();
        int minPort = config.minPort();
        int maxPort = config.maxPort();
        boolean intelligent = config.intelligentPort();
        boolean failOnUnavailable = config.failIfPreferredPortUnavailable();

        // Ephemeral port request
        if (preferredPort <= 0) {
            HttpServer server = createHttpServer(host, 0);
            int boundPort = server.getAddress().getPort();
            logger.info(String.format("[PortManager] Bound successfully to dynamic ephemeral port %d on %s", boundPort, host));
            return new BindingResult(server, boundPort, host);
        }

        // Attempt 1: Try preferred port directly
        try {
            HttpServer server = createHttpServer(host, preferredPort);
            int boundPort = server.getAddress().getPort();
            logger.info(String.format("[PortManager] Bound successfully to preferred port %d on %s", boundPort, host));
            return new BindingResult(server, boundPort, host);
        } catch (SocketException e) {
            logger.warning(String.format("[PortManager] Preferred port %d on %s is occupied", preferredPort, host));
            if (failOnUnavailable || !intelligent) {
                throw new PortConflictException(
                        String.format("Preferred port %d on %s is already bound, and automatic port search is disabled", preferredPort, host),
                        preferredPort, minPort, maxPort
                );
            }
        }

        // Attempt 2: Search range [minPort, maxPort]
        for (int p = minPort; p <= maxPort; p++) {
            if (p == preferredPort) {
                continue; // Already tried
            }
            try {
                HttpServer server = createHttpServer(host, p);
                int boundPort = server.getAddress().getPort();
                logger.info(String.format("[PortManager] Intelligent fallback: bound to port %d on %s", boundPort, host));
                return new BindingResult(server, boundPort, host);
            } catch (SocketException ignored) {
                // Port occupied, continue
            }
        }

        // All configured ports exhausted
        throw new PortConflictException(
                String.format("Exhausted all ports in range [%d, %d] on %s. No port is available.", minPort, maxPort, host),
                preferredPort, minPort, maxPort
        );
    }

    private static HttpServer createHttpServer(String host, int port) throws IOException {
        InetSocketAddress address = new InetSocketAddress(host, port);
        return HttpServer.create(address, 0);
    }

    /**
     * Checks whether a port is currently available for binding on the specified host.
     */
    public static boolean isPortAvailable(String host, int port) {
        if (port < 1 || port > 65535) {
            return false;
        }
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(host != null ? host : "127.0.0.1", port));
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}

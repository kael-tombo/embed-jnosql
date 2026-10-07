package org.embeddedjnosql.db.config;

import java.util.Objects;

/**
 * Configuration for the embedded EmbedJNoSQL Administration Web Console and REST Server.
 * Supports intelligent port management (automatic collision avoidance), configurable URL paths,
 * port range constraints, scheme, and localhost-only network security binding.
 */
public record ConsoleConfig(
        boolean enabled,
        int port,
        String contextPath,
        boolean intelligentPort,
        int maxPortAttempts,
        String host,
        String scheme,
        int minPort,
        int maxPort,
        boolean failIfPreferredPortUnavailable,
        long startupTimeoutMs,
        boolean localhostOnly
) {
    public static final int DEFAULT_PORT = 9090;
    public static final String DEFAULT_CONTEXT_PATH = "/";
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_MAX_ATTEMPTS = 50;
    public static final String DEFAULT_SCHEME = "http";
    public static final long DEFAULT_STARTUP_TIMEOUT_MS = 10_000L;

    public ConsoleConfig {
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535, got: " + port);
        }
        if (minPort < 0 || minPort > 65535) {
            throw new IllegalArgumentException("minPort must be between 0 and 65535, got: " + minPort);
        }
        if (maxPort < 0 || maxPort > 65535) {
            throw new IllegalArgumentException("maxPort must be between 0 and 65535, got: " + maxPort);
        }
        if (minPort > maxPort) {
            throw new IllegalArgumentException("minPort (" + minPort + ") cannot be greater than maxPort (" + maxPort + ")");
        }
        if (port > 0 && (port < minPort || port > maxPort)) {
            // Normalize range if default range was auto-computed
            if (minPort == port && maxPort < port) {
                maxPort = port;
            }
        }
        scheme = scheme != null ? scheme.toLowerCase().trim() : DEFAULT_SCHEME;
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Scheme must be 'http' or 'https', got: " + scheme);
        }
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath.trim())) {
            contextPath = "/";
        } else {
            String cp = contextPath.trim();
            if (!cp.startsWith("/")) {
                cp = "/" + cp;
            }
            if (cp.endsWith("/") && cp.length() > 1) {
                cp = cp.substring(0, cp.length() - 1);
            }
            contextPath = cp;
        }
        if (host == null || host.isBlank()) {
            host = DEFAULT_HOST;
        } else {
            host = host.trim();
        }
        if (localhostOnly && "0.0.0.0".equals(host)) {
            host = DEFAULT_HOST;
        }
    }

    /**
     * Backward-compatible 6-argument constructor.
     */
    public ConsoleConfig(
            boolean enabled,
            int port,
            String contextPath,
            boolean intelligentPort,
            int maxPortAttempts,
            String host
    ) {
        this(
                enabled,
                port,
                contextPath,
                intelligentPort,
                maxPortAttempts,
                host,
                DEFAULT_SCHEME,
                port,
                Math.min(65535, port + Math.max(1, maxPortAttempts)),
                false,
                DEFAULT_STARTUP_TIMEOUT_MS,
                "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host)
        );
    }

    public static ConsoleConfig disabled() {
        return new ConsoleConfig(
                false,
                DEFAULT_PORT,
                DEFAULT_CONTEXT_PATH,
                true,
                DEFAULT_MAX_ATTEMPTS,
                DEFAULT_HOST,
                DEFAULT_SCHEME,
                DEFAULT_PORT,
                DEFAULT_PORT + DEFAULT_MAX_ATTEMPTS,
                false,
                DEFAULT_STARTUP_TIMEOUT_MS,
                true
        );
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean enabled = false;
        private int port = DEFAULT_PORT;
        private String contextPath = DEFAULT_CONTEXT_PATH;
        private boolean intelligentPort = true;
        private int maxPortAttempts = DEFAULT_MAX_ATTEMPTS;
        private String host = DEFAULT_HOST;
        private String scheme = DEFAULT_SCHEME;
        private int minPort = -1;
        private int maxPort = -1;
        private boolean failIfPreferredPortUnavailable = false;
        private long startupTimeoutMs = DEFAULT_STARTUP_TIMEOUT_MS;
        private boolean localhostOnly = true;

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder contextPath(String contextPath) {
            this.contextPath = contextPath;
            return this;
        }

        public Builder intelligentPort(boolean intelligentPort) {
            this.intelligentPort = intelligentPort;
            return this;
        }

        public Builder maxPortAttempts(int maxPortAttempts) {
            this.maxPortAttempts = maxPortAttempts;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder scheme(String scheme) {
            this.scheme = scheme;
            return this;
        }

        public Builder portRange(int minPort, int maxPort) {
            this.minPort = minPort;
            this.maxPort = maxPort;
            return this;
        }

        public Builder minPort(int minPort) {
            this.minPort = minPort;
            return this;
        }

        public Builder maxPort(int maxPort) {
            this.maxPort = maxPort;
            return this;
        }

        public Builder failIfPreferredPortUnavailable(boolean failIfPreferredPortUnavailable) {
            this.failIfPreferredPortUnavailable = failIfPreferredPortUnavailable;
            return this;
        }

        public Builder startupTimeoutMs(long startupTimeoutMs) {
            this.startupTimeoutMs = startupTimeoutMs;
            return this;
        }

        public Builder localhostOnly(boolean localhostOnly) {
            this.localhostOnly = localhostOnly;
            if (localhostOnly) {
                this.host = DEFAULT_HOST;
            }
            return this;
        }

        public ConsoleConfig build() {
            int effectiveMin = minPort >= 0 ? minPort : (port > 0 ? port : 1);
            int effectiveMax = maxPort >= 0 ? maxPort : (port > 0 ? Math.min(65535, port + Math.max(1, maxPortAttempts)) : 65535);
            return new ConsoleConfig(
                    enabled,
                    port,
                    contextPath,
                    intelligentPort,
                    maxPortAttempts,
                    host,
                    scheme,
                    effectiveMin,
                    effectiveMax,
                    failIfPreferredPortUnavailable,
                    startupTimeoutMs,
                    localhostOnly
            );
        }
    }
}

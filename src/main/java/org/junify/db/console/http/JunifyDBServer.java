package org.junify.db.console.http;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import org.junify.db.JunifyDB;
import org.junify.db.nosql.document.Document;
import org.junify.db.nosql.document.DocumentCollection;
import org.junify.db.nosql.document.Query;
import org.junify.db.nosql.document.QueryParser;
import org.junify.db.core.util.JsonSerde;
import org.junify.db.nosql.kv.HashBucket;
import org.junify.db.nosql.kv.ListBucket;
import org.junify.db.nosql.kv.SetBucket;


import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpPrincipal;
import org.junify.db.config.ConsoleConfig;
import org.junify.db.config.SecurityConfig;
import org.junify.db.config.ConfigurationResolver;
import org.junify.db.security.CsrfTokenManager;
import java.io.IOException;
import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

public class JunifyDBServer {

    /** Maximum number of audit events retained in memory before oldest are evicted. */
    private static final int AUDIT_LOG_MAX_SIZE = 10_000;

    private final JunifyDB db;
    private HttpsServer httpsServer;
    private int sslPort = -1;
    private String sslKeystorePath = null;
    private String sslKeystorePassword = null;
    private HttpServer server;
    private java.util.concurrent.ExecutorService executorService;
    private long startTime;
    /**
     * API key for request authentication.
     * null = authentication disabled (only permitted via explicit disableAuthentication()).
     * No default key is provided — callers must set one via setApiKey() or leave auth disabled.
     */
    private String apiKey = null;

    // In-memory session storage for authentication
    private final Map<String, SessionInfo> sessions = new java.util.concurrent.ConcurrentHashMap<>();
    private record SessionInfo(String username, long expiresAt) {}
    private static final long SESSION_TTL_MS = 30 * 60 * 1000; // 30 minutes
    /**
     * Authentication is DISABLED by default when no API key has been set.
     * Call setApiKey() to enable it, or disableAuthentication() to explicitly opt out.
     */
    private boolean authEnabled = false;
    /**
     * Secure default, matching {@link org.junify.db.config.SecurityConfig}:
     * a server that is never handed a security config must not answer with
     * wildcard CORS. The console SPA is served from this same origin, so it
     * never needs CORS; opt in explicitly via
     * {@code junifydb.security.cors-enabled} / {@code JUNIFYDB_SECURITY_CORS_ENABLED}
     * for a cross-origin frontend.
     */
    private boolean corsEnabled = false;
    private boolean compressionEnabled = true;
    private int rateLimit = 1000;
    private long maxRequestSizeBytes = 10 * 1024 * 1024; // 10MB default max request size
    private int queryTimeoutSeconds = 30; // Default query timeout
    private Map<String, RateLimitEntry> rateLimitMap = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(JunifyDBServer.class);
    /**
     * Bounded audit log. Older events are evicted when {@link #AUDIT_LOG_MAX_SIZE} is reached.
     * Uses a synchronized LinkedList as a ring buffer to avoid unbounded memory growth.
     */
    private final java.util.Deque<AuditEvent> auditLog = new java.util.ArrayDeque<>(AUDIT_LOG_MAX_SIZE + 1);
    
    private static class RateLimitEntry {
        AtomicInteger count = new AtomicInteger(0);
        long windowStart = System.currentTimeMillis();
    }

    public record AuditEvent(long timestamp, String operation, String resource, String documentId,
                             String status, String clientIp, String details) {}

    private ConsoleConfig consoleConfig = ConsoleConfig.disabled();
    private SecurityConfig securityConfig = SecurityConfig.disabled();
    private String consoleUrl = null;
    private String adminUsername = SecurityConfig.DEFAULT_ADMIN_USERNAME;
    private String adminPassword = null;
    private String allowedOrigins = "*";

    public JunifyDBServer(JunifyDB db) {
        this.db = db;
    }

    public void applySecurityConfig(SecurityConfig securityConfig) {
        if (securityConfig == null) return;
        this.securityConfig = ConfigurationResolver.resolveSecurityConfig(securityConfig);
        this.authEnabled = this.securityConfig.authEnabled();
        this.apiKey = this.securityConfig.apiKey();
        this.adminUsername = this.securityConfig.adminUsername() != null ? this.securityConfig.adminUsername() : SecurityConfig.DEFAULT_ADMIN_USERNAME;
        this.adminPassword = this.securityConfig.adminPassword();
        this.corsEnabled = this.securityConfig.corsEnabled();
        this.allowedOrigins = this.securityConfig.allowedOrigins() != null ? this.securityConfig.allowedOrigins() : "*";
        this.sessionManager = new SecureSessionManager(this.securityConfig.sessionTtlMs());
        if (this.securityConfig.rateLimitRequestsPerMinute() > 0) {
            this.rateLimit = this.securityConfig.rateLimitRequestsPerMinute();
        }
        if (this.securityConfig.sslPort() > 0 && this.securityConfig.sslKeystorePath() != null) {
            configureSsl(this.securityConfig.sslPort(), this.securityConfig.sslKeystorePath(), this.securityConfig.sslKeystorePassword());
        }
    }

    public SecurityConfig getSecurityConfig() {
        return securityConfig;
    }

    public ConsoleConfig getConsoleConfig() {
        return consoleConfig;
    }

    public String getConsoleUrl() {
        if (consoleUrl != null) return consoleUrl;
        if (server != null) {
            int p = server.getAddress().getPort();
            return "http://localhost:" + p + "/";
        }
        return null;
    }

    public void setApiKey(String apiKey) {
        if (apiKey != null && !apiKey.isEmpty()) {
            this.apiKey = apiKey;
            this.authEnabled = true;
        } else {
            // Explicitly disable auth if null/empty is passed (not recommended)
            this.authEnabled = false;
            this.apiKey = null;
            logger.warn("[JunifyDBServer] setApiKey called with null/empty key — authentication disabled!");
        }
    }

    /**
     * Disable authentication (NOT RECOMMENDED for production).
     * Only use in trusted environments.
     */
    public void disableAuthentication() {
        this.authEnabled = false;
        System.err.println("WARNING: Authentication disabled. This is unsafe in production!");
    }

    /**
     * Set maximum request size in bytes.
     * Default is 10MB to prevent OOM attacks.
     */
    public void setMaxRequestSize(long bytes) {
        this.maxRequestSizeBytes = bytes;
    }

    /**
     * Set query timeout in seconds.
     * Default is 30 seconds to prevent hanging queries.
     */
    public void setQueryTimeout(int seconds) {
        this.queryTimeoutSeconds = seconds;
    }
    /**
     * Configure SSL/HTTPS support.
     * @param port SSL port number
     * @param keystorePath Path to JKS keystore file
     * @param keystorePassword Keystore password
     */
    public void configureSsl(int port, String keystorePath, String keystorePassword) {
        this.sslPort = port;
        this.sslKeystorePath = keystorePath;
        this.sslKeystorePassword = keystorePassword;
    }

    public int getSslPort() {
        return sslPort;
    }

    public String getSslKeystorePath() {
        return sslKeystorePath;
    }

    private void logAuditEvent(String operation, String resource, String documentId, String status,
                               String clientIp, String details) {
        var event = new AuditEvent(System.currentTimeMillis(), operation, resource, documentId, status, clientIp, details);
        synchronized (auditLog) {
            auditLog.addLast(event);
            // Evict oldest entry when the cap is exceeded
            if (auditLog.size() > AUDIT_LOG_MAX_SIZE) {
                auditLog.pollFirst();
            }
        }
        appendAuditToDisk(event);
        logger.info("[AUDIT] {} {} {} - {} - {} - {}", operation, resource,
                    documentId != null ? documentId : "", status, clientIp, details);
    }

    /**
     * Appends an audit event to {@code <dataDir>/audit.log} as JSONL when the
     * underlying engine persists to disk (audit R-24 / 22-SEC-03). The ring
     * buffer remains the fast in-memory view; the file survives restarts.
     *
     * <p>Fail-open by design: an audit write failure is logged but never
     * blocks the data operation being audited. A single shared writer keeps
     * appends serialized; the file is flushed per event but not fsynced —
     * audit is evidentiary, not a durability mechanism.
     */
    private java.io.BufferedWriter auditWriter;
    private java.nio.file.Path auditFile;

    private void appendAuditToDisk(AuditEvent event) {
        String dataDir;
        try {
            dataDir = db.config().dataDir() != null ? db.config().dataDir().toString() : null;
        } catch (Exception e) {
            dataDir = null;
        }
        if (dataDir == null || db.config().storageEngine() == org.junify.db.config.JunifyDBConfig.StorageEngineType.IN_MEMORY) return;
        try {
            if (auditWriter == null) {
                auditFile = java.nio.file.Path.of(dataDir, "audit.log");
                java.nio.file.Files.createDirectories(auditFile.getParent());
                auditWriter = new java.io.BufferedWriter(new java.io.FileWriter(auditFile.toFile(), true));
            }
            auditWriter.write(JsonSerde.toJson(event));
            auditWriter.newLine();
            auditWriter.flush();
        } catch (Exception e) {
            System.err.println("[AUDIT] disk append failed (continuing): " + e.getMessage());
        }
    }

    private void logCrudEvent(String operation, String collection, String documentId, String clientIp) {
        logAuditEvent(operation, collection, documentId, "SUCCESS", clientIp, "CRUD operation");
    }

    private java.util.Map<String, String> parseQueryParams(String query) {
        var params = new java.util.LinkedHashMap<String, String>();
        if (query != null) {
            for (var param : query.split("&")) {
                var kv = param.split("=", 2);
                if (kv.length == 2) {
                    params.put(kv[0], java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8));
                } else if (kv.length == 1) {
                    params.put(kv[0], "");
                }
            }
        }
        return params;
    }


    private SecureSessionManager sessionManager = new SecureSessionManager();
    private final CsrfTokenManager csrfTokenManager = new CsrfTokenManager();

    private static class FailedLoginTracker {
        final AtomicInteger count = new AtomicInteger(0);
        volatile long lockoutUntil = 0L;
    }
    private final ConcurrentHashMap<String, FailedLoginTracker> failedLogins = new ConcurrentHashMap<>();

    private boolean isIpLockedOut(String clientIp) {
        if (securityConfig == null || !securityConfig.bruteForceProtectionEnabled()) {
            return false;
        }
        FailedLoginTracker tracker = failedLogins.get(clientIp);
        if (tracker == null) return false;
        long now = System.currentTimeMillis();
        if (now < tracker.lockoutUntil) {
            return true;
        }
        if (tracker.lockoutUntil > 0 && now >= tracker.lockoutUntil) {
            tracker.count.set(0);
            tracker.lockoutUntil = 0L;
        }
        return false;
    }

    private void recordFailedLogin(String clientIp) {
        if (securityConfig == null || !securityConfig.bruteForceProtectionEnabled()) return;
        FailedLoginTracker tracker = failedLogins.computeIfAbsent(clientIp, k -> new FailedLoginTracker());
        int attempts = tracker.count.incrementAndGet();
        if (attempts >= securityConfig.maxFailedLoginAttempts()) {
            tracker.lockoutUntil = System.currentTimeMillis() + securityConfig.lockoutDurationMs();
            logger.warn("[JunifyDBServer] IP {} locked out until {} due to {} failed login attempts",
                    clientIp, tracker.lockoutUntil, attempts);
        }
    }

    /** Seconds until the client's login lockout lifts (0 when not locked). */
    private long retryAfterSeconds(String clientIp) {
        if (securityConfig == null || !securityConfig.bruteForceProtectionEnabled()) return 0;
        FailedLoginTracker tracker = failedLogins.get(clientIp);
        if (tracker == null || tracker.lockoutUntil <= 0) return 0;
        return Math.max(1, (tracker.lockoutUntil - System.currentTimeMillis() + 999) / 1000);
    }

    private void recordSuccessfulLogin(String clientIp) {
        failedLogins.remove(clientIp);
    }

    private boolean isCsrfValid(HttpExchange exchange) {
        if (!authEnabled || securityConfig == null || !securityConfig.csrfEnabled()) {
            return true;
        }
        String method = exchange.getRequestMethod().toUpperCase();
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return true;
        }
        // Machine-to-machine API key auth bypasses CSRF
        var authHeader = exchange.getRequestHeaders().getFirst("X-API-Key");
        if (apiKey != null && apiKey.equals(authHeader)) {
            return true;
        }
        var bearerHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (bearerHeader != null && bearerHeader.startsWith("Bearer ")) {
            String token = bearerHeader.substring(7);
            if (apiKey != null && apiKey.equals(token)) {
                return true;
            }
        }
        // If authenticated via cookie or bearer session, check CSRF token
        String sessionId = sessionManager.getSessionIdFromCookie(exchange);
        if (sessionId == null && bearerHeader != null && bearerHeader.startsWith("Bearer ")) {
            sessionId = bearerHeader.substring(7);
        }
        if (sessionId != null) {
            String csrfHeader = exchange.getRequestHeaders().getFirst("X-CSRF-Token");
            if (csrfHeader == null || csrfHeader.isBlank()) {
                return false;
            }
            return csrfTokenManager.validateToken(csrfHeader, sessionId);
        }
        return true;
    }

    private void sendCsrfError(HttpExchange exchange) throws IOException {
        sendJson(exchange, 403, Map.of("error", "Forbidden", "message", "Invalid or missing CSRF token"));
    }

    private boolean isAuthValid(HttpExchange exchange) {
        if (!authEnabled) return true;
        var authHeader = exchange.getRequestHeaders().getFirst("X-API-Key");
        if (apiKey != null && apiKey.equals(authHeader)) {
            return true;
        }
        var bearerHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (bearerHeader != null && bearerHeader.startsWith("Bearer ")) {
            String token = bearerHeader.substring(7);
            if (apiKey != null && apiKey.equals(token)) {
                return true;
            }
            SessionInfo session = sessions.get(token);
            if (session != null && session.expiresAt() > System.currentTimeMillis()) {
                return true;
            }
        }
        String sessionId = sessionManager.getSessionIdFromCookie(exchange);
        if (sessionId != null) {
            SessionInfo session = sessions.get(sessionId);
            if (session != null && session.expiresAt() > System.currentTimeMillis()) {
                return true;
            }
        }
        return false;
    }

    private void sendAuthError(HttpExchange exchange) throws IOException {
        sendJson(exchange, 401, Map.of("error", "Unauthorized", "message", "Invalid or missing API key"));
    }

    private boolean isRateLimited(HttpExchange exchange) {
        if (securityConfig != null && !securityConfig.rateLimitEnabled()) {
            return false;
        }
        var clientIp = getClientIp(exchange);
        var now = System.currentTimeMillis();
        var entry = rateLimitMap.computeIfAbsent(clientIp, k -> new RateLimitEntry());
        
        if (now - entry.windowStart > 60000) {
            entry.windowStart = now;
            entry.count.set(0);
        }
        
        int limit = (securityConfig != null && securityConfig.rateLimitRequestsPerMinute() > 0)
                ? securityConfig.rateLimitRequestsPerMinute()
                : rateLimit;
        return entry.count.incrementAndGet() > limit;
    }

    private void sendRateLimitError(HttpExchange exchange) throws IOException {
        sendJson(exchange, 429, Map.of("error", "Too Many Requests", "message", "Rate limit exceeded. Try again later."));
    }

    private String getClientIp(HttpExchange exchange) {
        var forwarded = exchange.getRequestHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null) return forwarded.split(",")[0].trim();
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    private void addCorsHeaders(HttpExchange exchange) {
        if (corsEnabled) {
            boolean wildcard = allowedOrigins == null || "*".equals(allowedOrigins);
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", wildcard ? "*" : allowedOrigins);
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, X-API-Key, Authorization, Cookie, X-CSRF-Token");
            // Credential-bearing CORS requires an explicit origin: browsers reject
            // the wildcard + credentials combination, so only send it when a
            // concrete allowlist is configured.
            if (!wildcard) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
            }
        }
    }

    private void addSecurityHeaders(HttpExchange exchange) {
        if (securityConfig != null && securityConfig.securityHeadersEnabled()) {
            var headers = exchange.getResponseHeaders();
            headers.set("X-Content-Type-Options", "nosniff");
            headers.set("X-Frame-Options", "DENY");
            headers.set("X-XSS-Protection", "1; mode=block");
            headers.set("Referrer-Policy", "strict-origin-when-cross-origin");
            headers.set("Content-Security-Policy", "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'");
            if (sslPort > 0) {
                headers.set("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
            }
        }
    }

    public void start(int port) throws IOException {
        String host = (consoleConfig != null && consoleConfig.host() != null)
                ? consoleConfig.host()
                : ConsoleConfig.DEFAULT_HOST;
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        startTime = System.currentTimeMillis();
        int actualPort = server.getAddress().getPort();
        String scheme = (consoleConfig != null && consoleConfig.scheme() != null) ? consoleConfig.scheme() : "http";
        String displayHost = ("0.0.0.0".equals(host) || "127.0.0.1".equals(host)) ? "localhost" : host;
        String ctx = (consoleConfig != null && consoleConfig.contextPath() != null) ? consoleConfig.contextPath() : "/";
        if (!ctx.startsWith("/")) ctx = "/" + ctx;
        if (!ctx.endsWith("/")) ctx = ctx + "/";
        if (this.consoleUrl == null) {
            this.consoleUrl = scheme + "://" + displayHost + ":" + actualPort + (ctx.equals("/") ? "/" : ctx);
        }

        // Log security configuration
        if (authEnabled) {
            logger.info("[JunifyDBServer] Authentication ENABLED (admin: '{}', apiKey: {})",
                    adminUsername, apiKey != null ? "configured" : "none");
            if (apiKey != null && !apiKey.isEmpty()) {
                String maskedKey = apiKey.length() > 8 ? apiKey.substring(0, 8) + "..." : "***";
                logger.info("[JunifyDBServer] API key prefix: {}", maskedKey);
            }
        } else {
            logger.warn("[JunifyDBServer] WARNING: Authentication DISABLED — all API endpoints are publicly accessible!");
        }

        registerHandlers(server);
        server.setExecutor(getOrCreateExecutor());
        restoreVectorIndexes();
        server.start();

        // Start HTTPS server if SSL is configured
        if (sslPort > 0 && sslKeystorePath != null) {
            startHttpsServer();
        }
    }

    /**
     * Start server with intelligent port management:
     * Validates port range, handles collisions according to configuration,
     * and binds securely.
     */
    public int startIntelligent(ConsoleConfig config) throws IOException {
        this.consoleConfig = ConfigurationResolver.resolveConsoleConfig(config != null ? config : ConsoleConfig.disabled());
        String host = consoleConfig.host() != null ? consoleConfig.host() : ConsoleConfig.DEFAULT_HOST;

        PortManager.BindingResult bindingResult = PortManager.bindServer(consoleConfig);
        this.server = bindingResult.server();
        this.startTime = System.currentTimeMillis();
        int boundPort = bindingResult.port();

        registerHandlers(server);
        server.setExecutor(getOrCreateExecutor());
        restoreVectorIndexes();
        server.start();

        if (sslPort > 0 && sslKeystorePath != null) {
            startHttpsServer();
        }

        String scheme = consoleConfig.scheme() != null ? consoleConfig.scheme() : "http";
        String displayHost = ("0.0.0.0".equals(host) || "127.0.0.1".equals(host)) ? "localhost" : host;
        String ctx = consoleConfig.contextPath();
        if (!ctx.startsWith("/")) ctx = "/" + ctx;
        if (!ctx.endsWith("/")) ctx = ctx + "/";
        this.consoleUrl = scheme + "://" + displayHost + ":" + boundPort + (ctx.equals("/") ? "/" : ctx);
        logger.info("[JunifyDBServer] Administration Console available at: {}", this.consoleUrl);
        return boundPort;
    }
    
    private class CorsPreflightHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            exchange.getResponseHeaders().set("Access-Control-Max-Age", "3600");
            exchange.sendResponseHeaders(204, -1);
        }
    }

    



    private void startHttpsServer() {
        try {
            // Load keystore explicitly — avoids exposing password via JVM system properties.
            var ks = java.security.KeyStore.getInstance("JKS");
            char[] keystorePassword = sslKeystorePassword != null ? sslKeystorePassword.toCharArray() : new char[0];
            try (var fis = new java.io.FileInputStream(sslKeystorePath)) {
                ks.load(fis, keystorePassword);
            }

            var kmf = javax.net.ssl.KeyManagerFactory.getInstance(javax.net.ssl.KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, keystorePassword);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);

            httpsServer = HttpsServer.create(new InetSocketAddress(sslPort), 0);
            httpsServer.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                @Override
                public void configure(HttpsParameters params) {
                    try {
                        SSLContext context = getSSLContext();
                        params.setNeedClientAuth(false);
                        params.setProtocols(new String[]{"TLSv1.2", "TLSv1.3"});
                        params.setSSLParameters(context.getDefaultSSLParameters());
                    } catch (Exception e) {
                        logger.error("[JunifyDBServer] SSL configuration error: {}", e.getMessage(), e);
                    }
                }
            });

            registerHandlers(httpsServer);
            httpsServer.setExecutor(getOrCreateExecutor());
            httpsServer.start();
            logger.info("[JunifyDBServer] HTTPS server started on port {}", sslPort);
        } catch (Exception e) {
            logger.error("[JunifyDBServer] SSL initialization error: {}", e.getMessage(), e);
        }
    }

    /**
     * Delegating HttpExchange wrapper that strips the configured context-path prefix
     * from getRequestURI() so all downstream handlers operate seamlessly regardless of
     * whether the server is mounted on "/" or a sub-path like "/jnosql-admin/".
     */
    private static class ContextAwareExchange extends HttpExchange {
        private final HttpExchange delegate;
        private final String prefix;
        private URI adjustedUri;

        public ContextAwareExchange(HttpExchange delegate, String prefix) {
            this.delegate = delegate;
            this.prefix = prefix;
        }

        @Override
        public URI getRequestURI() {
            if (adjustedUri == null) {
                URI orig = delegate.getRequestURI();
                String path = orig.getPath();
                if (path != null && path.startsWith(prefix)) {
                    String newPath = path.substring(prefix.length());
                    if (!newPath.startsWith("/")) {
                        newPath = "/" + newPath;
                    }
                    try {
                        adjustedUri = new URI(orig.getScheme(), orig.getUserInfo(), orig.getHost(),
                                orig.getPort(), newPath, orig.getQuery(), orig.getFragment());
                    } catch (Exception e) {
                        adjustedUri = orig;
                    }
                } else {
                    adjustedUri = orig;
                }
            }
            return adjustedUri;
        }

        @Override public Headers getRequestHeaders() { return delegate.getRequestHeaders(); }
        @Override public Headers getResponseHeaders() { return delegate.getResponseHeaders(); }
        @Override public String getRequestMethod() { return delegate.getRequestMethod(); }
        @Override public HttpContext getHttpContext() { return delegate.getHttpContext(); }
        @Override public void close() { delegate.close(); }
        @Override public InputStream getRequestBody() { return delegate.getRequestBody(); }
        @Override public OutputStream getResponseBody() { return delegate.getResponseBody(); }
        @Override public void sendResponseHeaders(int rCode, long responseLength) throws IOException {
            delegate.sendResponseHeaders(rCode, responseLength);
        }
        @Override public InetSocketAddress getRemoteAddress() { return delegate.getRemoteAddress(); }
        @Override public int getResponseCode() { return delegate.getResponseCode(); }
        @Override public InetSocketAddress getLocalAddress() { return delegate.getLocalAddress(); }
        @Override public String getProtocol() { return delegate.getProtocol(); }
        @Override public Object getAttribute(String name) { return delegate.getAttribute(name); }
        @Override public void setAttribute(String name, Object value) { delegate.setAttribute(name, value); }
        @Override public void setStreams(InputStream i, OutputStream o) { delegate.setStreams(i, o); }
        @Override public HttpPrincipal getPrincipal() { return delegate.getPrincipal(); }
    }

    /**
     * Register all HTTP handler contexts on the given server instance.
     * Used by both the plain HTTP server and the HTTPS server so that
     * handler registrations are never duplicated or out-of-sync.
     */
    private void registerHandlers(HttpServer httpServer) {
        String rawCtx = (consoleConfig != null && consoleConfig.contextPath() != null) ? consoleConfig.contextPath() : "/";
        if (!rawCtx.startsWith("/")) rawCtx = "/" + rawCtx;
        while (rawCtx.length() > 1 && rawCtx.endsWith("/")) {
            rawCtx = rawCtx.substring(0, rawCtx.length() - 1);
        }
        final String prefix = rawCtx.equals("/") ? "" : rawCtx;

        if (!prefix.isEmpty()) {
            // Register UI static handler under prefix and prefix/
            httpServer.createContext(prefix + "/", wrapHandler(new StaticHandler(), prefix));
            httpServer.createContext(prefix, wrapHandler(new StaticHandler(), prefix));

            // Root redirect: when contextPath is not root, visiting "/" redirects to "${prefix}/"
            httpServer.createContext("/", exchange -> {
                String reqPath = exchange.getRequestURI().getPath();
                if (reqPath.equals("/") || reqPath.isEmpty()) {
                    addSecurityHeaders(exchange);
                    exchange.getResponseHeaders().set("Location", prefix + "/");
                    exchange.sendResponseHeaders(302, -1);
                } else {
                    exchange.sendResponseHeaders(404, -1);
                }
            });
        } else {
            httpServer.createContext("/", new StaticHandler());
        }

        registerEndpoint(httpServer, prefix, "/api/collections", new CollectionsHandler());
        registerEndpoint(httpServer, prefix, "/api/auth/login", new AuthLoginHandler());
        registerEndpoint(httpServer, prefix, "/api/auth/logout", new AuthLogoutHandler());
        registerEndpoint(httpServer, prefix, "/api/kv", new KeyValueHandler());
        registerEndpoint(httpServer, prefix, "/api/kv/lists", new ListHandler());
        registerEndpoint(httpServer, prefix, "/api/kv/sets", new SetHandler());
        registerEndpoint(httpServer, prefix, "/api/kv/hashes", new HashHandler());
        registerEndpoint(httpServer, prefix, "/api/columns", new ColumnHandler());
        registerEndpoint(httpServer, prefix, "/api/health", new HealthHandler());
        registerEndpoint(httpServer, prefix, "/api/metrics", new MetricsHandler());
        registerEndpoint(httpServer, prefix, "/api/metrics/stream", new MetricsStreamHandler());
        registerEndpoint(httpServer, prefix, "/api/stats", new StatsHandler());
        registerEndpoint(httpServer, prefix, "/api/backup", new BackupHandler());
        registerEndpoint(httpServer, prefix, "/api/indexes", new IndexHandler());
        registerEndpoint(httpServer, prefix, "/api/transactions", new TransactionHandler());
        registerEndpoint(httpServer, prefix, "/api/schema", new SchemaHandler());
        registerEndpoint(httpServer, prefix, "/api/vectors", new VectorHandler());
        registerEndpoint(httpServer, prefix, "/api/bulk", new BulkHandler());
        registerEndpoint(httpServer, prefix, "/api/cdc", new CDCHandler());
        registerEndpoint(httpServer, prefix, "/api/audit/logs", new AuditLogHandler());
        registerEndpoint(httpServer, prefix, "/api/sql", new SqlHandler());
        if (corsEnabled) {
            registerEndpoint(httpServer, prefix, "/api/cors", new CorsPreflightHandler());
        }
    }

    private void registerEndpoint(HttpServer server, String prefix, String path, HttpHandler handler) {
        if (!prefix.isEmpty()) {
            server.createContext(prefix + path, wrapHandler(handler, prefix));
        }
        if (prefix.isEmpty() || !"/".equals(path)) {
            server.createContext(path, handler);
        }
    }

    private HttpHandler wrapHandler(HttpHandler handler, String prefix) {
        if (prefix.isEmpty()) return handler;
        return exchange -> handler.handle(new ContextAwareExchange(exchange, prefix));
    }
    private synchronized java.util.concurrent.ExecutorService getOrCreateExecutor() {
        if (executorService == null || executorService.isShutdown()) {
            executorService = java.util.concurrent.Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "junifydb-http-worker");
                t.setDaemon(true);
                return t;
            });
        }
        return executorService;
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
        if (httpsServer != null) {
            httpsServer.stop(0);
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
        if (auditWriter != null) {
            try {
                auditWriter.flush();
                auditWriter.close();
            } catch (Exception e) {
                System.err.println("[AUDIT] failed to close audit writer: " + e.getMessage());
            } finally {
                auditWriter = null;
            }
        }
    }

    public int port() {
        return server.getAddress().getPort();
    }

    
    private class AuditLogHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthValid(exchange)) { sendAuthError(exchange); return; }

            if ("GET".equals(exchange.getRequestMethod())) {
                var query = exchange.getRequestURI().getQuery();
                var params = parseQueryParams(query);

                String operation = params.get("operation");
                String resource = params.get("resource");
                String since = params.get("since");
                int limit = params.containsKey("limit") ? Integer.parseInt(params.get("limit")) : 100;

                // Take a snapshot to avoid holding the lock during streaming
                java.util.List<AuditEvent> snapshot;
                synchronized (auditLog) {
                    snapshot = new java.util.ArrayList<>(auditLog);
                }
                var filtered = snapshot.stream();

                if (operation != null && !operation.isEmpty()) {
                    filtered = filtered.filter(e -> e.operation().equals(operation));
                }
                if (resource != null && !resource.isEmpty()) {
                    filtered = filtered.filter(e -> e.resource().equals(resource));
                }
                if (since != null && !since.isEmpty()) {
                    try {
                        long sinceTs = Long.parseLong(since);
                        filtered = filtered.filter(e -> e.timestamp() >= sinceTs);
                    } catch (NumberFormatException ex) {
                        // Ignore invalid since parameter
                    }
                }

                var result = filtered.limit(limit).toList();
                var eventMaps = new java.util.ArrayList<Map<String, Object>>();
                for (var e : result) {
                    var m = new java.util.LinkedHashMap<String, Object>();
                    m.put("timestamp", e.timestamp());
                    m.put("operation", e.operation() != null ? e.operation() : "");
                    m.put("resource", e.resource() != null ? e.resource() : "");
                    m.put("documentId", e.documentId() != null ? e.documentId() : "");
                    m.put("status", e.status() != null ? e.status() : "");
                    m.put("clientIp", e.clientIp() != null ? e.clientIp() : "");
                    m.put("details", e.details() != null ? e.details() : "");
                    eventMaps.add(m);
                }
                sendJson(exchange, 200, java.util.Map.of(
                    "count", result.size(),
                    "events", eventMaps
                ));
            } else {
                sendJson(exchange, 405, java.util.Map.of("error", "Method not allowed"));
            }
        }
    }

    private class AuthLoginHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            addSecurityHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                return;
            }
            try {
                String clientIp = getClientIp(exchange);
                if (isIpLockedOut(clientIp)) {
                    long retryAfterSec = retryAfterSeconds(clientIp);
                    logAuditEvent("LOGIN", "auth", null, "LOCKED_OUT", clientIp,
                            "Client IP temporarily locked out (" + retryAfterSec + "s remaining)");
                    exchange.getResponseHeaders().set("Retry-After", String.valueOf(retryAfterSec));
                    sendJson(exchange, 429, Map.of(
                            "error", "Too Many Requests",
                            "message", "Too many failed attempts. Try again in " + retryAfterSec + " seconds."));
                    return;
                }

                var body = readBody(exchange);
                @SuppressWarnings("unchecked")
                var req = (body == null || body.trim().isEmpty())
                        ? Map.of()
                        : JsonSerde.fromJson(body, Map.class);
                String user = req.get("username") != null ? req.get("username").toString() : null;
                String pass = req.get("password") != null ? req.get("password").toString() : null;
                String key = req.get("apiKey") != null ? req.get("apiKey").toString() : null;

                if (authEnabled) {
                    boolean authenticated = false;
                    // Check API Key
                    if (apiKey != null && !apiKey.isEmpty()) {
                        if (apiKey.equals(key) || apiKey.equals(pass)) {
                            authenticated = true;
                            if (user == null) user = "api-user";
                        }
                    }
                    // Check Username & Password
                    if (!authenticated && adminPassword != null && !adminPassword.isEmpty()) {
                        String expectedUser = adminUsername != null ? adminUsername : SecurityConfig.DEFAULT_ADMIN_USERNAME;
                        if (expectedUser.equals(user) && adminPassword.equals(pass)) {
                            authenticated = true;
                        }
                    }
                    if (!authenticated) {
                        // Missing credentials are a client error, not an attack:
                        // reject them WITHOUT burning a brute-force strike.
                        boolean missingCredentials =
                                (pass == null || pass.isEmpty()) && (key == null || key.isEmpty());
                        if (missingCredentials) {
                            logAuditEvent("LOGIN", "auth", null, "REJECTED", clientIp, "Missing credentials");
                            sendJson(exchange, 400, Map.of(
                                    "error", "Bad Request",
                                    "message", "Enter your password or API key to sign in."));
                            return;
                        }
                        recordFailedLogin(clientIp);
                        logAuditEvent("LOGIN", "auth", null, "FAILED", clientIp, "Invalid username or password");
                        sendJson(exchange, 401, Map.of("error", "Unauthorized", "message", "Invalid username or password"));
                        return;
                    }
                }

                recordSuccessfulLogin(clientIp);
                if (user == null) user = adminUsername != null ? adminUsername : "admin";
                String sessionId = sessionManager.generateSessionId();
                long ttl = (securityConfig != null && securityConfig.sessionTtlMs() > 0)
                        ? securityConfig.sessionTtlMs()
                        : SESSION_TTL_MS;
                sessions.put(sessionId, new SessionInfo(user, System.currentTimeMillis() + ttl));
                sessionManager.setSessionCookie(exchange, sessionId, sslPort > 0);
                String csrfToken = csrfTokenManager.generateToken(sessionId);
                exchange.getResponseHeaders().set("X-CSRF-Token", csrfToken);
                logAuditEvent("LOGIN", "auth", null, "SUCCESS", clientIp, "User: " + user);

                sendJson(exchange, 200, Map.of(
                    "status", "authenticated",
                    "session", sessionId,
                    "token", sessionId,
                    "csrfToken", csrfToken,
                    "username", user
                ));
            } catch (Exception e) {
                sendJson(exchange, 500, Map.of("error", "Authentication error", "message", e.getMessage() != null ? e.getMessage() : "Unknown"));
            }
        }
    }

    private class AuthLogoutHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            addSecurityHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            String sessionId = sessionManager.getSessionIdFromCookie(exchange);
            if (sessionId != null) {
                sessions.remove(sessionId);
                csrfTokenManager.invalidateAllSessionTokens(sessionId);
            }
            sessionManager.clearSessionCookie(exchange);
            sendJson(exchange, 200, Map.of("status", "logged_out"));
        }
    }

    private class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addSecurityHeaders(exchange);
            addCorsHeaders(exchange);
            var path = exchange.getRequestURI().getPath();
            if (path.equals("/")) {
                path = "/index.html";
            }
            var resourcePath = "/static" + path;
            try (var is = JunifyDBServer.class.getResourceAsStream(resourcePath)) {
                if (is == null) {
                    exchange.sendResponseHeaders(404, -1);
                    return;
                }
                var bytes = is.readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", getContentType(path));
                exchange.sendResponseHeaders(200, bytes.length);
                try (var os = exchange.getResponseBody()) {
                    os.write(bytes);
                }
            }
        }

        private String getContentType(String path) {
            if (path.endsWith(".html")) return "text/html";
            if (path.endsWith(".css")) return "text/css";
            if (path.endsWith(".js")) return "application/javascript";
            if (path.endsWith(".json")) return "application/json";
            if (path.endsWith(".svg")) return "image/svg+xml";
            if (path.endsWith(".png")) return "image/png";
            if (path.endsWith(".ico")) return "image/x-icon";
            return "text/plain";
        }
    }

    private class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthValid(exchange)) { sendAuthError(exchange); return; }
            var runtime = Runtime.getRuntime();
            var totalMem = runtime.totalMemory();
            var freeMem = runtime.freeMemory();
            var config = db.config();
            var engineType = config.storageEngine();
            boolean inMemory = engineType == org.junify.db.config.JunifyDBConfig.StorageEngineType.IN_MEMORY;

            var health = Map.of(
                "status", "ok",
                "open", db.isOpen(),
                "version", "1.0.0",
                "engine", engineType.name(),
                "uptime", System.currentTimeMillis() - startTime,
                "timestamp", System.currentTimeMillis(),
                "memory", Map.of(
                    "used", totalMem - freeMem,
                    "total", totalMem,
                    "max", runtime.maxMemory(),
                    "free", freeMem
                ),
                "threads", Map.of(
                    "active", Thread.activeCount(),
                    "daemon", Thread.activeCount()
                ),
                // Console orientation context: what the UI must always be able to show.
                // Everything here is read from the live configuration, never guessed by
                // the front end. `durability` spells out what a crash can cost, so the
                // Console can state data-safety honestly instead of implying "saved".
                "context", buildContext(exchange, engineType, inMemory, config)
            );
            sendJson(exchange, 200, health);
        }
    }

    /** Ordered orientation context for the Console (engine, storage, database, identity). */
    private Map<String, Object> buildContext(HttpExchange exchange,
            org.junify.db.config.JunifyDBConfig.StorageEngineType engineType,
            boolean inMemory,
            org.junify.db.config.JunifyDBConfig config) {
        var context = new LinkedHashMap<String, Object>();
        context.put("engine", engineType.name());
        context.put("relationalEngine", "JUNIFYDB-RDBMS");
        context.put("nosqlEngine", "JUNIFYDB-NOSQL");
        context.put("storageMode", inMemory ? "in-memory" : (config.autoFlush() ? "sync" : "async"));
        context.put("durability", inMemory
                ? "no durability - data is lost when the process exits"
                : (config.autoFlush()
                    ? "periodic flush every " + config.flushIntervalMs() + " ms"
                    : "explicit flush / flush on close only - a hard kill can lose writes"));
        context.put("database", inMemory ? "memory" : String.valueOf(config.dataDir()));
        context.put("dataDir", inMemory ? "" : String.valueOf(config.dataDir().toAbsolutePath()));
        context.put("authEnabled", authEnabled);
        context.put("user", currentUser(exchange));
        context.put("activeTransactions", activeTransactions.size());
        context.put("transactionalConsoleWrites", false);
        context.put("transactionScope", "Console writes bypass transactions; use the Java API "
                + "(db.beginTransaction()) to put writes inside one");
        return context;
    }

    /**
     * Identity behind the current request, for the Console's security-context display.
     * Returns {@code api-key} for key auth, {@code anonymous} when auth is disabled, and
     * the signed-in username for a session.
     */
    private String currentUser(HttpExchange exchange) {
        if (!authEnabled) return "anonymous";
        var apiKeyHeader = exchange.getRequestHeaders().getFirst("X-API-Key");
        if (apiKey != null && apiKey.equals(apiKeyHeader)) return "api-key";
        var bearerHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (bearerHeader != null && bearerHeader.startsWith("Bearer ")) {
            String token = bearerHeader.substring(7);
            if (apiKey != null && apiKey.equals(token)) return "api-key";
            SessionInfo session = sessions.get(token);
            if (session != null && session.expiresAt() > System.currentTimeMillis()) {
                return session.username();
            }
        }
        String sessionId = sessionManager.getSessionIdFromCookie(exchange);
        if (sessionId != null) {
            SessionInfo session = sessions.get(sessionId);
            if (session != null && session.expiresAt() > System.currentTimeMillis()) {
                return session.username();
            }
        }
        return "anonymous";
    }

    private class CollectionsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthValid(exchange)) { sendAuthError(exchange); return; }
            if (!isCsrfValid(exchange)) { sendCsrfError(exchange); return; }
            var path = exchange.getRequestURI().getPath();
            var parts = path.split("/");
            
            // /api/collections with no additional path - list collections
            if (parts.length < 4 || parts[3].isEmpty()) {
                if ("GET".equals(exchange.getRequestMethod())) {
                    var cols = new java.util.ArrayList<Map<String, Object>>();
                    for (String colName : db.getCollectionNames()) {
                        cols.add(Map.of("name", colName, "count", (long) db.documentCollection(colName).count()));
                    }
                    sendJson(exchange, 200, Map.of("collections", cols));
                } else {
                    sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                }
                return;
            }
            
            // /api/collections/{name} - delegate to collection logic
            var name = parts[3];

            // R-55: resolving a collection must not be a write. `documentCollection(name)`
            // auto-creates, so before this guard *any* method on a typo'd name created the
            // collection — `GET /api/collections/typo` returned 200 [] and left the typo in
            // the catalog forever, and `DELETE /api/collections/typo` answered 405 while
            // creating the very resource it was asked to remove. Only document-write
            // requests (POST/PUT) may auto-create, which is the documented schemaless
            // workflow INSERT and the repositories rely on; reads, DELETE, and the typed
            // sub-resources are 404 for an unknown collection.
            boolean reservedSub = parts.length >= 5
                    && ("stats".equals(parts[4]) || "set-ttl".equals(parts[4])
                        || "cleanup".equals(parts[4]) || "query".equals(parts[4]));
            boolean documentWrite = !reservedSub
                    && ("POST".equals(exchange.getRequestMethod())
                        || "PUT".equals(exchange.getRequestMethod()));
            if (!documentWrite && !db.getCollectionNames().contains(name)) {
                sendJson(exchange, 404, Map.of("error", "Collection not found: " + name));
                return;
            }

            var collection = db.documentCollection(name);

            if (parts.length == 4) {
                if ("GET".equals(exchange.getRequestMethod())) {
                    sendJson(exchange, 200, collection.findAll());
                } else if ("POST".equals(exchange.getRequestMethod())) {
                    try {
                        var body = readBody(exchange);
                        var doc = Document.fromJson(body);
                        
                        if (schemaValidator.hasSchema(name)) {
                            var validation = schemaValidator.validate(name, doc.getFields());
                            if (!validation.isValid()) {
                                sendJson(exchange, 400, Map.of(
                                    "error", "Schema validation failed",
                                    "errors", validation.getErrors()
                                ));
                                return;
                            }
                        }
                        
                        var saved = collection.insert(doc);
                        logCrudEvent("INSERT", name, saved.getId(), getClientIp(exchange));
                        sendJson(exchange, 201, saved);
                    } catch (Exception e) {
                        System.err.println("[CollectionsHandler] POST error: " + e.getMessage());
                        e.printStackTrace();
                        try {
                            sendJson(exchange, 500, Map.of("error", "Internal server error", "message", e.getMessage()));
                        } catch (Exception ex) {
                            // Response already sent or connection closed
                        }
                    }
                } else {
                    // Honest 405: there is no collection-drop capability in the engine, and
                    // the UI offers no such control, so name the limitation instead of
                    // emitting a bare "Method not allowed".
                    sendJson(exchange, 405, Map.of(
                            "error", "Method not allowed",
                            "message", "Deleting a whole collection is not supported; "
                                    + "delete its documents via /api/collections/" + name + "/{id}"));
                }
            } else if (parts.length >= 5) {
                // Check for /api/collections/{name}/stats endpoint
                if ("stats".equals(parts[4])) {
                    if ("GET".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 200, collection.stats());
                    } else {
                        sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                    }
                    return;
                }

                // Check for /api/collections/{name}/set-ttl endpoint
                if ("set-ttl".equals(parts[4])) {
                    if ("POST".equals(exchange.getRequestMethod())) {
                        try {
                            var body = readBody(exchange);
                            var data = JsonSerde.fromJson(body, Map.class);
                            var documentId = data.get("documentId").toString();
                            var ttlSeconds = ((Number) data.get("ttlSeconds")).longValue();
                            var updated = collection.setTtl(documentId, ttlSeconds);
                            sendJson(exchange, 200, Map.of(
                                    "success", updated > 0,
                                    "updated", updated
                            ));
                        } catch (Exception e) {
                            System.err.println("[CollectionsHandler] Set-TTL error: " + e.getMessage());
                            e.printStackTrace();
                            sendJson(exchange, 500, Map.of("error", "Set TTL failed", "message", e.getMessage()));
                        }
                    } else {
                        sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                    }
                    return;
                }

                // Check for /api/collections/{name}/cleanup endpoint
                if ("cleanup".equals(parts[4])) {
                    if ("POST".equals(exchange.getRequestMethod())) {
                        try {
                            var deleted = collection.cleanupExpired();
                            sendJson(exchange, 200, Map.of("deleted", deleted));
                        } catch (Exception e) {
                            System.err.println("[CollectionsHandler] Cleanup error: " + e.getMessage());
                            e.printStackTrace();
                            sendJson(exchange, 500, Map.of("error", "Cleanup failed", "message", e.getMessage()));
                        }
                    } else {
                        sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                    }
                    return;
                }

                // Check for /api/collections/{name}/query endpoint
                if ("query".equals(parts[4])) {
                    if ("POST".equals(exchange.getRequestMethod())) {
                        try {
                            var body = readBody(exchange);
                            var data = JsonSerde.fromJson(body, Map.class);

                            // Support the legacy top-level $gt/$lt/$eq payloads used by the UI
                            // and the shared QueryParser format for richer queries.
                            org.junify.db.nosql.document.Query query = org.junify.db.nosql.document.Query.all();

                            if (data.containsKey("$gt")) {
                                var gtData = (Map<String, Object>) data.get("$gt");
                                for (var entry : gtData.entrySet()) {
                                    query = org.junify.db.nosql.document.Query.gt(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                                }
                            } else if (data.containsKey("$lt")) {
                                var ltData = (Map<String, Object>) data.get("$lt");
                                for (var entry : ltData.entrySet()) {
                                    query = org.junify.db.nosql.document.Query.lt(entry.getKey(), ((Number) entry.getValue()).doubleValue());
                                }
                            } else if (data.containsKey("$eq")) {
                                var eqData = (Map<String, Object>) data.get("$eq");
                                for (Object entryObj : eqData.entrySet()) {
                                    var entry = (java.util.Map.Entry<String, Object>) entryObj;
                                    query = org.junify.db.nosql.document.Query.eq(entry.getKey(), entry.getValue());
                                }
                            } else {
                                query = org.junify.db.nosql.document.QueryParser.parse(data);
                            }

                            var results = collection.find(query);
                            sendJson(exchange, 200, results.stream()
                                .map(doc -> {
                                    var map = new java.util.LinkedHashMap<String, Object>();
                                    if (doc.getId() != null) {
                                        map.put("id", doc.getId());
                                    }
                                    map.putAll(doc.getFields());
                                    return map;
                                })
                                .collect(java.util.stream.Collectors.toList()));                        } catch (org.junify.db.nosql.document.QueryParser.QueryFormatException e) {
                            // R-45/R-46/R-47: malformed queries are client errors, not
                            // server errors — a 500 here made every silent-wrong-result
                            // bug invisible as "query failed".
                            System.err.println("[CollectionsHandler] Bad query: " + e.getMessage());
                            sendJson(exchange, 400, Map.of("error", "Invalid query", "message", e.getMessage()));
                        } catch (Exception e) {
                            System.err.println("[CollectionsHandler] Query error: " + e.getMessage());
                            e.printStackTrace();
                            sendJson(exchange, 500, Map.of("error", "Query failed", "message", e.getMessage()));
                        }
                    } else {
                        sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                    }
                    return;
                }
                
                var id = parts[4];
                System.out.println("[CollectionsHandler] " + exchange.getRequestMethod() + " /api/collections/" + name + "/" + id);
                if ("GET".equals(exchange.getRequestMethod())) {
                    var doc = collection.findById(id);
                    System.out.println("[CollectionsHandler] GET result: " + (doc != null ? "found" : "not found"));
                    if (doc != null) sendJson(exchange, 200, doc);
                    else sendJson(exchange, 404, Map.of("error", "Not found"));
                } else if ("PUT".equals(exchange.getRequestMethod()) || "POST".equals(exchange.getRequestMethod())) {
                    try {
                        var body = readBody(exchange);
                        var data = JsonSerde.fromJson(body, Map.class);
                        // R-31: update-by-id must merge onto the stored document. Building
                        // from an empty Document silently dropped every field the body
                        // omitted and wrote a synthetic "name":"temp" into each row.
                        var existingDoc = collection.findById(id);
                        var doc = existingDoc != null
                                ? Document.fromMap(id, new java.util.LinkedHashMap<>(existingDoc.getFields()))
                                : new Document();
                        doc.id(id);
                        for (var entry : data.entrySet()) {
                            var e = (java.util.Map.Entry<?, ?>) entry;
                            doc.add(e.getKey().toString(), e.getValue());
                        }
                        
                        if (schemaValidator.hasSchema(name)) {
                            var validation = schemaValidator.validate(name, doc.getFields());
                            if (!validation.isValid()) {
                                sendJson(exchange, 400, Map.of(
                                    "error", "Schema validation failed",
                                    "errors", validation.getErrors()
                                ));
                                return;
                            }
                        }
                        
                        var saved = collection.insert(doc);
                        logCrudEvent("UPDATE", name, id, getClientIp(exchange));
                        sendJson(exchange, 201, saved);
                    } catch (Exception e) {
                        System.err.println("[CollectionsHandler] PUT/POST error: " + e.getMessage());
                        e.printStackTrace();
                        try {
                            sendJson(exchange, 500, Map.of("error", "Internal server error", "message", e.getMessage()));
                        } catch (Exception ex) {
                            // Response already sent or connection closed
                        }
                    }
                } else if ("DELETE".equals(exchange.getRequestMethod())) {
                    try {
                        boolean deleted = collection.deleteById(id);
                        if (deleted) {
                            logCrudEvent("DELETE", name, id, getClientIp(exchange));
                            sendJson(exchange, 204, null);
                        } else {
                            sendJson(exchange, 404, Map.of("error", "Not found", "id", id));
                        }
                    } catch (Exception e) {
                        System.err.println("[CollectionsHandler] DELETE error: " + e.getMessage());
                        e.printStackTrace();
                        try {
                            sendJson(exchange, 500, Map.of("error", "Delete failed", "message", e.getMessage()));
                        } catch (Exception ex) {
                            // Response already sent or connection closed
                        }
                    }
                } else {
                    sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                }
            }
        }
    }

    private class KeyValueHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthValid(exchange)) { sendAuthError(exchange); return; }
            if (!isCsrfValid(exchange)) { sendCsrfError(exchange); return; }
            var path = exchange.getRequestURI().getPath();
            var parts = path.split("/");
            if (parts.length < 4) {
                sendJson(exchange, 400, Map.of("error", "Usage: /api/kv/{bucket}[/{key}]"));
                return;
            }
            var bucketName = parts[3];
            var bucket = db.keyValueBucket(bucketName);

            if (parts.length >= 5) {
                var key = parts[4];
                if ("GET".equals(exchange.getRequestMethod())) {
                    var value = bucket.get(key);
                    if (value != null) sendJson(exchange, 200, Map.of("key", key, "value", value));
                    else sendJson(exchange, 404, Map.of("error", "Not found"));
                } else if ("PUT".equals(exchange.getRequestMethod()) || "POST".equals(exchange.getRequestMethod())) {
                    var body = readBody(exchange);
                    var data = JsonSerde.fromJson(body, Map.class);
                    var value = data.getOrDefault("value", "").toString();
                    bucket.put(key, value);
                    sendJson(exchange, 201, Map.of("key", key, "value", value, "status", "created"));
                } else if ("DELETE".equals(exchange.getRequestMethod())) {
                    bucket.delete(key);
                    logCrudEvent("DELETE", bucketName + "/" + key, key, getClientIp(exchange));
                    sendJson(exchange, 204, null);
                }
            } else {
                sendJson(exchange, 400, Map.of("error", "Usage: /api/kv/{bucket}/{key}"));
            }
        }
    }

    private class ListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthValid(exchange)) { sendAuthError(exchange); return; }
            if (!isCsrfValid(exchange)) { sendCsrfError(exchange); return; }
            var path = exchange.getRequestURI().getPath();
            var parts = path.split("/");
            
            // Full path: /api/kv/lists/{bucket}/{key}[/{operation}]
            // parts[0]="", [1]="api", [2]="kv", [3]="lists", [4]="bucket", [5]="key", [6]="operation"
            if (parts.length < 6) {
                sendJson(exchange, 400, Map.of("error", "Usage: /api/kv/lists/{bucket}/{key}[/{operation}]"));
                return;
            }
            
            var bucketName = parts[4];
            var bucket = db.listBucket(bucketName);
            var key = parts[5];
            
            // If only bucket and key (no operation), return full list
            if (parts.length == 6) {
                if ("GET".equals(exchange.getRequestMethod())) {
                    var result = bucket.lrange(key, 0, -1);
                    sendJson(exchange, 200, Map.of("key", key, "values", result, "length", result.size()));
                } else if ("DELETE".equals(exchange.getRequestMethod())) {
                    boolean deleted = bucket.delete(key);
                    sendJson(exchange, deleted ? 204 : 404, Map.of("deleted", deleted));
                } else {
                    sendJson(exchange, 400, Map.of("error", "Usage: GET or DELETE /api/kv/lists/{bucket}/{key}"));
                }
                return;
            }
            
            // /api/kv/lists/{bucket}/{key}/{operation}
            var operation = parts[6];
            switch (operation.toLowerCase()) {
                case "lpush" -> {
                    if (!"POST".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "POST required"));
                        return;
                    }
                    var body = readBody(exchange);
                    var data = JsonSerde.fromJson(body, Map.class);
                    var values = parseStringArray(data.get("values"));
                    long len = bucket.lpush(key, values);
                    sendJson(exchange, 200, Map.of("key", key, "operation", "lpush", "length", len));
                }
                case "rpush" -> {
                    if (!"POST".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "POST required"));
                        return;
                    }
                    var body = readBody(exchange);
                    var data = JsonSerde.fromJson(body, Map.class);
                    var values = parseStringArray(data.get("values"));
                    long len = bucket.rpush(key, values);
                    sendJson(exchange, 200, Map.of("key", key, "operation", "rpush", "length", len));
                }
                case "lpop" -> {
                    if (!"POST".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "POST required"));
                        return;
                    }
                    String value = bucket.lpop(key);
                    var lpopResult = new java.util.HashMap<String, Object>(); lpopResult.put("key", key); lpopResult.put("operation", "lpop"); lpopResult.put("value", value);
                    sendJson(exchange, 200, lpopResult);
                }
                case "rpop" -> {
                    if (!"POST".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "POST required"));
                        return;
                    }
                    String value = bucket.rpop(key);
                    var rpopResult = new java.util.HashMap<String, Object>(); rpopResult.put("key", key); rpopResult.put("operation", "rpop"); rpopResult.put("value", value);
                    sendJson(exchange, 200, rpopResult);
                }
                case "range", "lrange" -> {
                    if (!"GET".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "GET required"));
                        return;
                    }
                    var query = exchange.getRequestURI().getQuery();
                    int start = 0, end = -1;
                    if (query != null) {
                        for (String param : query.split("&")) {
                            var kv = param.split("=");
                            if (kv.length == 2) {
                                if ("start".equals(kv[0])) start = Integer.parseInt(kv[1]);
                                if ("end".equals(kv[0])) end = Integer.parseInt(kv[1]);
                            }
                        }
                    }
                    var result = bucket.lrange(key, start, end);
                    sendJson(exchange, 200, Map.of("key", key, "operation", "lrange", "start", start, "end", end, "values", result));
                }
                case "len", "llen" -> {
                    if (!"GET".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "GET required"));
                        return;
                    }
                    long len = bucket.llen(key);
                    sendJson(exchange, 200, Map.of("key", key, "operation", "llen", "length", len));
                }
                case "lrem" -> {
                    if (!"POST".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "POST required"));
                        return;
                    }
                    var body = readBody(exchange);
                    var data = JsonSerde.fromJson(body, Map.class);
                    long count = data.containsKey("count") ? ((Number) data.get("count")).longValue() : 0;
                    String value = data.get("value").toString();
                    long removed = bucket.lrem(key, count, value);
                    sendJson(exchange, 200, Map.of("key", key, "operation", "lrem", "removed", removed));
                }
                case "lindex" -> {
                    if (!"GET".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "GET required"));
                        return;
                    }
                    var query = exchange.getRequestURI().getQuery();
                    int index = 0;
                    if (query != null) {
                        for (String param : query.split("&")) {
                            var kv = param.split("=");
                            if (kv.length == 2 && "index".equals(kv[0])) {
                                index = Integer.parseInt(kv[1]);
                            }
                        }
                    }
                    String value = bucket.lindex(key, index);
                    sendJson(exchange, 200, Map.of("key", key, "operation", "lindex", "index", index, "value", value));
                }
                case "ltrim" -> {
                    if (!"POST".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "POST required"));
                        return;
                    }
                    var body = readBody(exchange);
                    var data = JsonSerde.fromJson(body, Map.class);
                    int start = ((Number) data.get("start")).intValue();
                    int end = ((Number) data.get("end")).intValue();
                    bucket.ltrim(key, start, end);
                    sendJson(exchange, 200, Map.of("key", key, "operation", "ltrim", "start", start, "end", end));
                }
                case "stats" -> {
                    if (!"GET".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "GET required"));
                        return;
                    }
                    sendJson(exchange, 200, bucket.stats());
                }
                default -> sendJson(exchange, 400, Map.of("error", "Unknown operation: " + operation, 
                    "supported", "lpush, rpush, lpop, rpop, range, len, lrem, lindex, ltrim, stats"));
            }
        }
    }

    private class SetHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthValid(exchange)) { sendAuthError(exchange); return; }
            if (!isCsrfValid(exchange)) { sendCsrfError(exchange); return; }
            var path = exchange.getRequestURI().getPath();
            var parts = path.split("/");
            
            // Full path: /api/kv/sets/{bucket}/{key}[/{operation}]
            // parts[0]="", [1]="api", [2]="kv", [3]="sets", [4]="bucket", [5]="key", [6]="operation"
            if (parts.length < 6) {
                sendJson(exchange, 400, Map.of("error", "Usage: /api/kv/sets/{bucket}/{key}[/{operation}]"));
                return;
            }
            
            var bucketName = parts[4];
            var bucket = db.setBucket(bucketName);
            var key = parts[5];
            
            // If only bucket and key (no operation), return all members
            if (parts.length == 6) {
                if ("GET".equals(exchange.getRequestMethod())) {
                    var members = bucket.smembers(key);
                    sendJson(exchange, 200, Map.of("key", key, "members", members, "cardinality", members.size()));
                } else if ("DELETE".equals(exchange.getRequestMethod())) {
                    boolean deleted = bucket.delete(key);
                    sendJson(exchange, deleted ? 204 : 404, Map.of("deleted", deleted));
                } else {
                    sendJson(exchange, 400, Map.of("error", "Usage: GET or DELETE /api/kv/sets/{bucket}/{key}"));
                }
                return;
            }
            
            // /api/kv/sets/{bucket}/{key}/{operation}
            var operation = parts[6];
            switch (operation.toLowerCase()) {
                case "sadd" -> {
                    if (!"POST".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "POST required"));
                        return;
                    }
                    var body = readBody(exchange);
                    var data = JsonSerde.fromJson(body, Map.class);
                    var members = parseStringArray(data.get("members"));
                    long added = bucket.sadd(key, members);
                    sendJson(exchange, 200, Map.of("key", key, "operation", "sadd", "added", added));
                }
                case "srem" -> {
                    if (!"POST".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "POST required"));
                        return;
                    }
                    var body = readBody(exchange);
                    var data = JsonSerde.fromJson(body, Map.class);
                    var members = parseStringArray(data.get("members"));
                    long removed = bucket.srem(key, members);
                    sendJson(exchange, 200, Map.of("key", key, "operation", "srem", "removed", removed));
                }
                case "smembers" -> {
                    if (!"GET".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "GET required"));
                        return;
                    }
                    var members = bucket.smembers(key);
                    sendJson(exchange, 200, Map.of("key", key, "operation", "smembers", "members", members));
                }
                case "sismember", "contains" -> {
                    if (!"GET".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "GET required"));
                        return;
                    }
                    var query = exchange.getRequestURI().getQuery();
                    String member = null;
                    if (query != null) {
                        for (String param : query.split("&")) {
                            var kv = param.split("=");
                            if (kv.length == 2 && "member".equals(kv[0])) {
                                member = java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                            }
                        }
                    }
                    if (member == null) {
                        sendJson(exchange, 400, Map.of("error", "Missing 'member' query parameter"));
                        return;
                    }
                    boolean exists = bucket.sismember(key, member);
                    sendJson(exchange, 200, Map.of("key", key, "operation", "sismember", "member", member, "exists", exists));
                }
                case "scard", "card" -> {
                    if (!"GET".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "GET required"));
                        return;
                    }
                    long card = bucket.scard(key);
                    sendJson(exchange, 200, Map.of("key", key, "operation", "scard", "cardinality", card));
                }
                case "spop" -> {
                    if (!"POST".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "POST required"));
                        return;
                    }
                    var body = readBody(exchange);
                    var data = JsonSerde.fromJson(body, Map.class);
                    int count = data.containsKey("count") ? ((Number) data.get("count")).intValue() : 1;
                    if (count == 1) {
                        String member = bucket.spop(key);
                        sendJson(exchange, 200, Map.of("key", key, "operation", "spop", "member", member));
                    } else {
                        var members = bucket.spop(key, count);
                        sendJson(exchange, 200, Map.of("key", key, "operation", "spop", "members", members, "count", members.size()));
                    }
                }
                case "srandmember", "randmember" -> {
                    if (!"GET".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "GET required"));
                        return;
                    }
                    var query = exchange.getRequestURI().getQuery();
                    int count = 1;
                    if (query != null) {
                        for (String param : query.split("&")) {
                            var kv = param.split("=");
                            if (kv.length == 2 && "count".equals(kv[0])) {
                                count = Integer.parseInt(kv[1]);
                            }
                        }
                    }
                    if (count == 1) {
                        String member = bucket.srandmember(key);
                        sendJson(exchange, 200, Map.of("key", key, "operation", "srandmember", "member", member));
                    } else {
                        var members = bucket.srandmember(key, count);
                        sendJson(exchange, 200, Map.of("key", key, "operation", "srandmember", "members", members));
                    }
                }
                case "sinter", "inter" -> {
                    if (!"POST".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "POST required"));
                        return;
                    }
                    var body = readBody(exchange);
                    var data = JsonSerde.fromJson(body, Map.class);
                    var keys = parseStringArray(data.get("keys"));
                    var result = bucket.sinter(keys);
                    sendJson(exchange, 200, Map.of("operation", "sinter", "keys", java.util.Arrays.toString(keys), "intersection", result));
                }
                case "sunion", "union" -> {
                    if (!"POST".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "POST required"));
                        return;
                    }
                    var body = readBody(exchange);
                    var data = JsonSerde.fromJson(body, Map.class);
                    var keys = parseStringArray(data.get("keys"));
                    var result = bucket.sunion(keys);
                    sendJson(exchange, 200, Map.of("operation", "sunion", "keys", java.util.Arrays.toString(keys), "union", result));
                }
                case "sdiff", "diff" -> {
                    if (!"POST".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "POST required"));
                        return;
                    }
                    var body = readBody(exchange);
                    var data = JsonSerde.fromJson(body, Map.class);
                    var keys = parseStringArray(data.get("keys"));
                    var result = bucket.sdiff(keys);
                    sendJson(exchange, 200, Map.of("operation", "sdiff", "keys", java.util.Arrays.toString(keys), "difference", result));
                }
                case "stats" -> {
                    if (!"GET".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "GET required"));
                        return;
                    }
                    sendJson(exchange, 200, bucket.stats());
                }
                default -> sendJson(exchange, 400, Map.of("error", "Unknown operation: " + operation,
                    "supported", "sadd, srem, smembers, sismember, scard, spop, srandmember, sinter, sunion, sdiff, stats"));
            }
        }
    }

    private class HashHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthValid(exchange)) { sendAuthError(exchange); return; }
            if (!isCsrfValid(exchange)) { sendCsrfError(exchange); return; }
            var path = exchange.getRequestURI().getPath();
            var parts = path.split("/");
            
            // Full path: /api/kv/hashes/{bucket}/{key}[/{operation}]
            // parts[0]="", [1]="api", [2]="kv", [3]="hashes", [4]="bucket", [5]="key", [6]="operation"
            if (parts.length < 6) {
                sendJson(exchange, 400, Map.of("error", "Usage: /api/kv/hashes/{bucket}/{key}[/{operation}]"));
                return;
            }
            
            var bucketName = parts[4];
            var bucket = db.hashBucket(bucketName);
            var key = parts[5];
            
            // If only bucket and key (no operation), return all fields
            if (parts.length == 6) {
                if ("GET".equals(exchange.getRequestMethod())) {
                    var fields = bucket.hgetall(key);
                    sendJson(exchange, 200, Map.of("key", key, "fields", fields, "length", fields.size()));
                } else if ("DELETE".equals(exchange.getRequestMethod())) {
                    boolean deleted = bucket.delete(key);
                    sendJson(exchange, deleted ? 204 : 404, Map.of("deleted", deleted));
                } else {
                    sendJson(exchange, 400, Map.of("error", "Usage: GET or DELETE /api/kv/hashes/{bucket}/{key}"));
                }
                return;
            }
            
            // /api/kv/hashes/{bucket}/{key}/{operation}
            var operation = parts[6];
            switch (operation.toLowerCase()) {
                case "hset", "set" -> {
                    if (!"POST".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "POST required"));
                        return;
                    }
                    var body = readBody(exchange);
                    var data = JsonSerde.fromJson(body, Map.class);
                    if (data.containsKey("field") && data.containsKey("value")) {
                        int result = bucket.hset(key, data.get("field").toString(), data.get("value").toString());
                        sendJson(exchange, 200, Map.of("key", key, "operation", "hset", "field", data.get("field"), "added", result == 1));
                    } else if (data.containsKey("fields")) {
                        @SuppressWarnings("unchecked")
                        var fields = (Map<String, String>) data.get("fields");
                        int added = bucket.hset(key, fields);
                        sendJson(exchange, 200, Map.of("key", key, "operation", "hset", "fieldsAdded", added));
                    } else {
                        sendJson(exchange, 400, Map.of("error", "Missing 'field'/'value' or 'fields' in body"));
                    }
                }
                case "hget", "get" -> {
                    if (!"GET".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "GET required"));
                        return;
                    }
                    var query = exchange.getRequestURI().getQuery();
                    String field = null;
                    if (query != null) {
                        for (String param : query.split("&")) {
                            var kv = param.split("=");
                            if (kv.length == 2 && "field".equals(kv[0])) {
                                field = java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                            }
                        }
                    }
                    if (field == null) {
                        sendJson(exchange, 400, Map.of("error", "Missing 'field' query parameter"));
                        return;
                    }
                    String value = bucket.hget(key, field);
                    sendJson(exchange, 200, Map.of("key", key, "operation", "hget", "field", field, "value", value));
                }
                case "hgetall", "getall" -> {
                    if (!"GET".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "GET required"));
                        return;
                    }
                    var fields = bucket.hgetall(key);
                    sendJson(exchange, 200, Map.of("key", key, "operation", "hgetall", "fields", fields));
                }
                case "hdel", "del" -> {
                    if (!"POST".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "POST required"));
                        return;
                    }
                    var body = readBody(exchange);
                    var data = JsonSerde.fromJson(body, Map.class);
                    var fields = parseStringArray(data.get("fields"));
                    int deleted = bucket.hdel(key, fields);
                    sendJson(exchange, 200, Map.of("key", key, "operation", "hdel", "deleted", deleted));
                }
                case "hlen", "len" -> {
                    if (!"GET".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "GET required"));
                        return;
                    }
                    long len = bucket.hlen(key);
                    sendJson(exchange, 200, Map.of("key", key, "operation", "hlen", "length", len));
                }
                case "hexists", "exists" -> {
                    if (!"GET".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "GET required"));
                        return;
                    }
                    var query = exchange.getRequestURI().getQuery();
                    String field = null;
                    if (query != null) {
                        for (String param : query.split("&")) {
                            var kv = param.split("=");
                            if (kv.length == 2 && "field".equals(kv[0])) {
                                field = java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                            }
                        }
                    }
                    if (field == null) {
                        sendJson(exchange, 400, Map.of("error", "Missing 'field' query parameter"));
                        return;
                    }
                    boolean exists = bucket.hexists(key, field);
                    sendJson(exchange, 200, Map.of("key", key, "operation", "hexists", "field", field, "exists", exists));
                }
                case "hkeys", "keys" -> {
                    if (!"GET".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "GET required"));
                        return;
                    }
                    var fields = bucket.hkeys(key);
                    sendJson(exchange, 200, Map.of("key", key, "operation", "hkeys", "fields", fields));
                }
                case "hvals", "vals" -> {
                    if (!"GET".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "GET required"));
                        return;
                    }
                    var values = bucket.hvals(key);
                    sendJson(exchange, 200, Map.of("key", key, "operation", "hvals", "values", values));
                }
                case "hmget", "mget" -> {
                    if (!"POST".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "POST required"));
                        return;
                    }
                    var body = readBody(exchange);
                    var data = JsonSerde.fromJson(body, Map.class);
                    var fields = parseStringArray(data.get("fields"));
                    var result = bucket.hmget(key, fields);
                    sendJson(exchange, 200, Map.of("key", key, "operation", "hmget", "fields", result));
                }
                case "hincrby", "incrby" -> {
                    if (!"POST".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "POST required"));
                        return;
                    }
                    var body = readBody(exchange);
                    var data = JsonSerde.fromJson(body, Map.class);
                    String field = data.get("field").toString();
                    long delta = data.containsKey("delta") ? ((Number) data.get("delta")).longValue() : 1;
                    long newValue = bucket.hincrby(key, field, delta);
                    sendJson(exchange, 200, Map.of("key", key, "operation", "hincrby", "field", field, "newValue", newValue));
                }
                case "hincrbyfloat", "incrbyfloat" -> {
                    if (!"POST".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "POST required"));
                        return;
                    }
                    var body = readBody(exchange);
                    var data = JsonSerde.fromJson(body, Map.class);
                    String field = data.get("field").toString();
                    double delta = data.containsKey("delta") ? ((Number) data.get("delta")).doubleValue() : 1.0;
                    String newValue = bucket.hincrbyfloat(key, field, delta);
                    sendJson(exchange, 200, Map.of("key", key, "operation", "hincrbyfloat", "field", field, "newValue", newValue));
                }
                case "stats" -> {
                    if (!"GET".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 405, Map.of("error", "GET required"));
                        return;
                    }
                    sendJson(exchange, 200, bucket.stats());
                }
                default -> sendJson(exchange, 400, Map.of("error", "Unknown operation: " + operation,
                    "supported", "hset, hget, hgetall, hdel, hlen, hexists, hkeys, hvals, hmget, hincrby, hincrbyfloat, stats"));
            }
        }
    }

    private String[] parseStringArray(Object obj) {
        if (obj == null) return new String[0];
        if (obj instanceof String str) {
            return new String[]{str};
        }
        if (obj instanceof java.util.List<?> list) {
            return list.stream().map(Object::toString).toArray(String[]::new);
        }
        return new String[0];
    }

    private class ColumnHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthValid(exchange)) { sendAuthError(exchange); return; }
            if (!isCsrfValid(exchange)) { sendCsrfError(exchange); return; }
            var path = exchange.getRequestURI().getPath();
            var parts = path.split("/");
            var query = exchange.getRequestURI().getQuery();

            // /api/columns/{family}
            if (parts.length < 4 || parts[3].isEmpty()) {
                sendJson(exchange, 400, Map.of("error", "Usage: /api/columns/{family}[/{row}][/operation]"));
                return;
            }

            var familyName = parts[3];
            var cf = db.columnFamily(familyName);

            // /api/columns/{family}/stats
            if (parts.length == 4 && "stats".equals(parts[3])) {
                // This case won't happen due to above check, handled below
            }

            // /api/columns/{family}/stats - Family-level stats
            if (parts.length >= 5 && "stats".equals(parts[4])) {
                if ("GET".equals(exchange.getRequestMethod())) {
                    sendJson(exchange, 200, cf.getColumnStats());
                } else {
                    sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                }
                return;
            }

            // /api/columns/{family}/cleanup - Cleanup expired columns
            if (parts.length >= 5 && "cleanup".equals(parts[4])) {
                if ("POST".equals(exchange.getRequestMethod())) {
                    var deleted = cf.cleanupAllExpired();
                    sendJson(exchange, 200, Map.of("deleted", deleted));
                } else {
                    sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                }
                return;
            }

            // /api/columns/{family}/{row}
            if (parts.length >= 5) {
                var rowKey = parts[4];

                // /api/columns/{family}/{row}/stats - Row-level stats
                if (parts.length >= 6 && "stats".equals(parts[5])) {
                    if ("GET".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 200, cf.getRowStats(rowKey));
                    } else {
                        sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                    }
                    return;
                }

                // /api/columns/{family}/{row}/get-range - Paginated row retrieval
                if (parts.length >= 6 && "get-range".equals(parts[5])) {
                    if ("GET".equals(exchange.getRequestMethod())) {
                        var params = parseQueryParams(query);
                        int limit = params.containsKey("limit") ? Integer.parseInt(params.get("limit")) : 100;
                        int offset = params.containsKey("offset") ? Integer.parseInt(params.get("offset")) : 0;
                        var columnsParam = params.get("columns");
                        Set<String> columns = null;
                        if (columnsParam != null && !columnsParam.isEmpty()) {
                            columns = new HashSet<>(Arrays.asList(columnsParam.split(",")));
                        }
                        var data = cf.getRow(rowKey, limit, offset, columns);
                        sendJson(exchange, 200, Map.of(
                            "rowKey", rowKey,
                            "limit", limit,
                            "offset", offset,
                            "columnsReturned", data.size(),
                            "data", data
                        ));
                    } else {
                        sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                    }
                    return;
                }

                // /api/columns/{family}/{row}/filter - Filter columns by name
                if (parts.length >= 6 && "filter".equals(parts[5])) {
                    if ("GET".equals(exchange.getRequestMethod())) {
                        var params = parseQueryParams(query);
                        var columnsParam = params.get("columns");
                        if (columnsParam != null && !columnsParam.isEmpty()) {
                            var columns = new HashSet<String>(Arrays.asList(columnsParam.split(",")));
                            var data = cf.getRow(rowKey, columns);
                            sendJson(exchange, 200, Map.of("rowKey", rowKey, "data", data));
                        } else {
                            sendJson(exchange, 400, Map.of("error", "Missing 'columns' query parameter"));
                        }
                    } else {
                        sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                    }
                    return;
                }

                // /api/columns/{family}/{row}/filter-pattern - Filter by regex pattern
                if (parts.length >= 6 && "filter-pattern".equals(parts[5])) {
                    if ("GET".equals(exchange.getRequestMethod())) {
                        var params = parseQueryParams(query);
                        var pattern = params.get("pattern");
                        if (pattern != null && !pattern.isEmpty()) {
                            var data = cf.getRowByPattern(rowKey, pattern);
                            sendJson(exchange, 200, Map.of("rowKey", rowKey, "pattern", pattern, "data", data));
                        } else {
                            sendJson(exchange, 400, Map.of("error", "Missing 'pattern' query parameter"));
                        }
                    } else {
                        sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                    }
                    return;
                }

                // /api/columns/{family}/{row}/filter-prefix - Filter by prefix
                if (parts.length >= 6 && "filter-prefix".equals(parts[5])) {
                    if ("GET".equals(exchange.getRequestMethod())) {
                        var params = parseQueryParams(query);
                        var prefix = params.get("prefix");
                        if (prefix != null && !prefix.isEmpty()) {
                            var data = cf.getRowByPrefix(rowKey, prefix);
                            sendJson(exchange, 200, Map.of("rowKey", rowKey, "prefix", prefix, "data", data));
                        } else {
                            sendJson(exchange, 400, Map.of("error", "Missing 'prefix' query parameter"));
                        }
                    } else {
                        sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                    }
                    return;
                }

                // /api/columns/{family}/{row}/ttl/{column} - Get TTL for a column
                if (parts.length >= 7 && "ttl".equals(parts[5])) {
                    var column = parts[6];
                    if ("GET".equals(exchange.getRequestMethod())) {
                        var remainingTtl = cf.getRemainingTtl(rowKey, column);
                        var columnData = cf.getColumnData(rowKey, column);
                        if (columnData == null) {
                            sendJson(exchange, 404, Map.of("error", "Column not found"));
                        } else {
                            sendJson(exchange, 200, Map.of(
                                "rowKey", rowKey,
                                "column", column,
                                "remainingTtlSeconds", remainingTtl,
                                "hasTtl", columnData.hasTtl(),
                                "expiresAt", columnData.getExpiresAt()
                            ));
                        }
                    } else {
                        sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                    }
                    return;
                }

                // /api/columns/{family}/{row}/column/{column} - Single column operations
                if (parts.length >= 7 && "column".equals(parts[5])) {
                    var column = parts[6];
                    if ("GET".equals(exchange.getRequestMethod())) {
                        var value = cf.get(rowKey, column);
                        if (value != null) {
                            var columnData = cf.getColumnData(rowKey, column);
                            sendJson(exchange, 200, Map.of(
                                "rowKey", rowKey,
                                "column", column,
                                "value", value,
                                "hasTtl", columnData != null && columnData.hasTtl(),
                                "remainingTtlSeconds", columnData != null ? columnData.getRemainingTtlSeconds() : -1
                            ));
                        } else {
                            sendJson(exchange, 404, Map.of("error", "Column not found"));
                        }
                    } else if ("PUT".equals(exchange.getRequestMethod()) || "POST".equals(exchange.getRequestMethod())) {
                        var body = readBody(exchange);
                        var data = JsonSerde.fromJson(body, Map.class);
                        var value = data.get("value");
                        Integer ttl = data.containsKey("ttlSeconds") ? ((Number) data.get("ttlSeconds")).intValue() : null;
                        cf.put(rowKey, column, value, ttl);
                        sendJson(exchange, 201, Map.of("rowKey", rowKey, "column", column, "status", "created"));
                    } else if ("DELETE".equals(exchange.getRequestMethod())) {
                        cf.deleteColumn(rowKey, column);
                        sendJson(exchange, 204, null);
                    } else {
                        sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                    }
                    return;
                }

                // /api/columns/{family}/{row}/cleanup - Cleanup expired columns in row
                if (parts.length >= 6 && "cleanup".equals(parts[5])) {
                    if ("POST".equals(exchange.getRequestMethod())) {
                        var deleted = cf.cleanupExpiredColumns(rowKey);
                        sendJson(exchange, 200, Map.of("deleted", deleted));
                    } else {
                        sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                    }
                    return;
                }

                // Default: Full row operations
                if ("GET".equals(exchange.getRequestMethod())) {
                    var row = cf.getRow(rowKey);
                    if (row != null && !row.isEmpty()) {
                        sendJson(exchange, 200, Map.of("rowKey", rowKey, "columns", row));
                    } else {
                        sendJson(exchange, 404, Map.of("error", "Row not found"));
                    }
                } else if ("PUT".equals(exchange.getRequestMethod()) || "POST".equals(exchange.getRequestMethod())) {
                    var body = readBody(exchange);
                    var data = JsonSerde.fromJson(body, Map.class);
                    Map<?, ?> colData = data;
                    if (data.containsKey("columns") && data.get("columns") instanceof Map<?, ?> inner) {
                        colData = inner;
                    }
                    for (Object o : colData.entrySet()) {
                        var entry = (java.util.Map.Entry<?, ?>) o;
                        var value = entry.getValue();
                        Integer ttl = null;
                        // Support nested TTL format: {"column": {"value": "x", "ttlSeconds": 60}}
                        if (value instanceof Map<?, ?> valueMap && valueMap.containsKey("value")) {
                            value = valueMap.get("value");
                            if (valueMap.containsKey("ttlSeconds")) {
                                ttl = ((Number) valueMap.get("ttlSeconds")).intValue();
                            }
                        }
                        cf.put(rowKey, entry.getKey().toString(), value, ttl);
                    }
                    sendJson(exchange, 201, Map.of("rowKey", rowKey, "status", "created"));
                } else if ("DELETE".equals(exchange.getRequestMethod())) {
                    cf.deleteRow(rowKey);
                    sendJson(exchange, 204, null);
                } else {
                    sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                }
            } else {
                // /api/columns/{family} - List all row keys
                if ("GET".equals(exchange.getRequestMethod())) {
                    var rowKeys = cf.getRowKeys();
                    sendJson(exchange, 200, Map.of(
                        "family", familyName,
                        "rowCount", rowKeys.size(),
                        "rowKeys", rowKeys
                    ));
                } else {
                    sendJson(exchange, 400, Map.of("error", "Usage: /api/columns/{family}[/{row}][/operation]"));
                }
            }
        }

        private Map<String, String> parseQueryParams(String query) {
            var params = new LinkedHashMap<String, String>();
            if (query != null) {
                for (var param : query.split("&")) {
                    var kv = param.split("=", 2);
                    if (kv.length == 2) {
                        params.put(kv[0], java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8));
                    } else if (kv.length == 1) {
                        params.put(kv[0], "");
                    }
                }
            }
            return params;
        }
    }

    private class BackupHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthValid(exchange)) { sendAuthError(exchange); return; }
            if (!isCsrfValid(exchange)) { sendCsrfError(exchange); return; }
            var path = exchange.getRequestURI().getPath();
            var parts = path.split("/");
            
            if ("GET".equals(exchange.getRequestMethod()) && parts.length == 3) {
                var metrics = db.metrics().snapshot();
                var collections = metrics.containsKey("collections") ? metrics.get("collections") : Map.of();
                sendJson(exchange, 200, Map.of(
                    "backup", Map.of(
                        "description", "Use POST /api/backup to create backup",
                        "restore", "Use POST /api/backup/restore with JSON body containing 'backupFile' path",
                        "directory", backupDir().toString()
                    ),
                    "database", Map.of("engine", db.storageEngine().name()),
                    "diskUsage", getDiskUsage(),
                    "collections", collections,
                    "backups", listBackups()
                ));
                return;
            }
            
            // Both POST /api/backup and POST /api/backup/restore are supported: the console
            // UI and the GET self-description above both advertise the /restore form.
            var restorePath = parts.length == 4 && "restore".equals(parts[3]);
            if ("POST".equals(exchange.getRequestMethod()) && (parts.length == 3 || restorePath)) {
                var body = readBody(exchange);
                Map<String, Object> data;
                if (body == null || body.isBlank()) {
                    data = Map.of();
                } else {
                    try {
                        data = JsonSerde.fromJson(body, Map.class);
                    } catch (Exception e) {
                        // Without this the exception escaped the handler and the server dropped
                        // the connection with no response at all, which a client cannot diagnose.
                        sendJson(exchange, 400, Map.of("error", "Invalid JSON body: " + e.getMessage()));
                        return;
                    }
                }

                if (restorePath && !data.containsKey("backupFile")) {
                    sendJson(exchange, 400, Map.of("error", "backupFile is required, e.g. {\"backupFile\":\"<path to .json.gz>\"}"));
                    return;
                }
                
                if (data.containsKey("backupFile")) {
                    var backupFile = java.nio.file.Paths.get(data.get("backupFile").toString());
                    if (!java.nio.file.Files.exists(backupFile)) {
                        sendJson(exchange, 404, Map.of("error", "Backup file not found: " + backupFile));
                        return;
                    }
                    try {
                        var restoreManager = new org.junify.db.core.backup.BackupManager(db.storageEngine());
                        restoreManager.restore(backupFile);
                        sendJson(exchange, 200, Map.of(
                            "status", "restored",
                            "file", backupFile.toString(),
                            "note", "Restored into the live engine; panels that cache data (Collections, SQL Studio) may need a reload"
                        ));
                    } catch (Exception e) {
                        sendJson(exchange, 500, Map.of("error", "Restore failed: " + e.getMessage()));
                    }
                } else {
                    // Back up the LIVE engine, not a fresh one: a new engine over an empty
                    // directory reports no collections and would write an empty snapshot.
                    try {
                        var targetDir = data.containsKey("targetDir")
                            ? java.nio.file.Files.createDirectories(java.nio.file.Paths.get(data.get("targetDir").toString()))
                            : java.nio.file.Files.createDirectories(backupDir());
                        var backupManager = new org.junify.db.core.backup.BackupManager(db.storageEngine());
                        var backupFile = backupManager.backup(targetDir);
                        sendJson(exchange, 200, Map.of(
                            "status", "backup created",
                            "file", backupFile.toString(),
                            "size", java.nio.file.Files.size(backupFile),
                            "documents", backupManager.lastBackupCounts().values().stream().mapToInt(Integer::intValue).sum(),
                            "collections", backupManager.lastBackupCounts()
                        ));
                    } catch (Exception e) {
                        sendJson(exchange, 500, Map.of("error", "Backup failed: " + e.getMessage()));
                    }
                }
                return;
            }
            
            sendJson(exchange, 400, Map.of("error", "Usage: GET /api/backup or POST /api/backup"));
        }
        
        private Map<String, Object> getDiskUsage() {
            try {
                var dataDir = db.config().dataDir();
                long totalSize = 0;
                int fileCount = 0;
                
                if (java.nio.file.Files.exists(dataDir)) {
                    try (var stream = java.nio.file.Files.list(dataDir)) {
                        var files = stream.filter(p -> p.toString().endsWith(".json")).toList();
                        for (var file : files) {
                            totalSize += java.nio.file.Files.size(file);
                            fileCount++;
                        }
                    }
                }
                
                return Map.of(
                    "dataDir", dataDir.toString(),
                    "totalBytes", totalSize,
                    "fileCount", fileCount,
                    "totalMB", String.format("%.2f MB", totalSize / 1024.0 / 1024.0)
                );
            } catch (IOException e) {
                return Map.of("error", e.getMessage());
            }
        }

        /**
         * Durable location for console-created backups. Deliberately inside the data
         * directory rather than a temp dir: an OS cleanup of the temp dir would destroy
         * the only copy of a user's data.
         */
        private java.nio.file.Path backupDir() {
            return db.config().dataDir().resolve("backups").toAbsolutePath();
        }

        private java.util.List<Map<String, Object>> listBackups() {
            var dir = backupDir();
            if (!java.nio.file.Files.exists(dir)) {
                return java.util.List.of();
            }
            try (var stream = java.nio.file.Files.list(dir)) {
                return stream.filter(p -> p.toString().endsWith(".json.gz"))
                    .sorted(java.util.Comparator.comparing((java.nio.file.Path p) -> p.getFileName().toString()).reversed())
                    .limit(10)
                    .map(p -> {
                        try {
                            return Map.<String, Object>of(
                                "file", p.toAbsolutePath().toString(),
                                "name", p.getFileName().toString(),
                                "size", java.nio.file.Files.size(p),
                                "modified", java.nio.file.Files.getLastModifiedTime(p).toMillis()
                            );
                        } catch (IOException e) {
                            return Map.<String, Object>of("file", p.toString(), "error", String.valueOf(e.getMessage()));
                        }
                    })
                    .toList();
            } catch (IOException e) {
                return java.util.List.of();
            }
        }
    }

    private class IndexHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthValid(exchange)) { sendAuthError(exchange); return; }
            if (!isCsrfValid(exchange)) { sendCsrfError(exchange); return; }
            var path = exchange.getRequestURI().getPath();
            var parts = path.split("/");
            if (parts.length < 4 || parts[3].isEmpty()) {
                if ("GET".equals(exchange.getRequestMethod())) {
                    sendJson(exchange, 200, Map.of("indexes", "use /api/indexes/{collection}", "status", "ok"));
                } else { sendJson(exchange, 405, Map.of("error", "Method not allowed")); }
                return;
            }
            var collectionName = parts[3];

            // R-64: `documentCollection(name)` auto-creates, so listing or dropping an index
            // on a mistyped name created the collection — `GET /api/indexes/typo` returned
            // 200 {} and left `typo` in the catalog for good. That is R-55's rule (resolving
            // a collection is not a write) applied to the index routes, and it matters more
            // now that a created collection's existence is durable (R-62). Adding an index
            // (POST) remains a write and may still auto-create, matching the documented
            // schemaless workflow.
            boolean addsIndex = "POST".equals(exchange.getRequestMethod());
            if (!addsIndex && !db.getCollectionNames().contains(collectionName)) {
                sendJson(exchange, 404, Map.of("error", "Collection not found: " + collectionName));
                return;
            }

            var collection = db.documentCollection(collectionName);
            
            if ("GET".equals(exchange.getRequestMethod())) {
                var indexes = collection.getIndexes();
                var result = new java.util.HashMap<String, Object>();
                result.put("collection", collectionName);
                result.put("indexes", indexes);
                sendJson(exchange, 200, result);
            } else if ("POST".equals(exchange.getRequestMethod())) {
                var body = readBody(exchange);
                var data = JsonSerde.fromJson(body, Map.class);
                var field = data.get("field").toString();
                var index = collection.createIndex(field);
                sendJson(exchange, 201, Map.of(
                    "status", "created",
                    "collection", collectionName,
                    "field", field
                ));
            } else if ("DELETE".equals(exchange.getRequestMethod())) {
                // Drop one index. This previously called collection.clear(), which deletes
                // every document in the collection while reporting "indexes cleared" — a
                // data-loss trap for any client hitting the documented index route.
                var params = parseQueryParams(exchange.getRequestURI().getQuery());
                var field = params.get("field");
                if (field == null || field.isBlank()) {
                    sendJson(exchange, 400, Map.of(
                        "error", "Specify the index to drop: DELETE /api/indexes/" + collectionName + "?field=<field>",
                        "indexes", collection.getIndexes().keySet()
                    ));
                    return;
                }
                if (!collection.dropIndex(field)) {
                    sendJson(exchange, 404, Map.of(
                        "error", "No index on field '" + field + "'",
                        "indexes", collection.getIndexes().keySet()
                    ));
                    return;
                }
                sendJson(exchange, 200, Map.of(
                    "status", "index dropped",
                    "collection", collectionName,
                    "field", field
                ));
            }
        }
    }

    private class TransactionHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthValid(exchange)) { sendAuthError(exchange); return; }
            if (!isCsrfValid(exchange)) { sendCsrfError(exchange); return; }
            if ("POST".equals(exchange.getRequestMethod())) {
                var body = readBody(exchange);
                var data = JsonSerde.fromJson(body, Map.class);
                var action = data.containsKey("action") ? data.get("action").toString() : "begin";
                if ("commit".equals(action) || "rollback".equals(action)) {
                    var txId = data.containsKey("transactionId") ? ((Number) data.get("transactionId")).intValue() : -1;
                    var tx = activeTransactions.remove(txId);
                    // R-42: an unknown or already-finished transactionId used to return
                    // 200 "committed"/"rollbackted" without doing anything — a commit
                    // that cannot fail tells the client nothing about their data.
                    if (tx == null) {
                        sendJson(exchange, 404, Map.of(
                            "error", "Unknown transaction",
                            "message", "No active transaction with id " + txId + ". It may have already been committed, rolled back, or never existed.",
                            "transactionId", txId,
                            "activeTransactions", activeTransactions.keySet()
                        ));
                        return;
                    }
                    try {
                        if ("commit".equals(action)) tx.commit(); else tx.rollback();
                    } catch (IllegalStateException e) {
                        // e.g. double commit: Transaction.commit throws once the tx is
                        // already finished. Report it instead of a blanket 200.
                        sendJson(exchange, 409, Map.of(
                            "error", "Transaction already finished",
                            "message", e.getMessage(),
                            "transactionId", txId
                        ));
                        return;
                    }
                    sendJson(exchange, 200, Map.of(
                        "status", "commit".equals(action) ? "committed" : "rolled_back",
                        "transactionId", txId
                    ));
                } else {
                    var tx = db.beginTransaction();
                    var txId = tx.hashCode();
                    activeTransactions.put(txId, tx);
                    sendJson(exchange, 200, Map.of("transactionId", txId, "status", "started"));
                }
            } else if ("GET".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 200, Map.of("activeTransactions", activeTransactions.size(), "ids", activeTransactions.keySet()));
            } else {
                sendJson(exchange, 405, Map.of("error", "POST or GET only"));
            }
        }
    }

    private class SchemaHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            try {
                if (!isAuthValid(exchange)) { sendAuthError(exchange); return; }
                if (!isCsrfValid(exchange)) { sendCsrfError(exchange); return; }
                var path = exchange.getRequestURI().getPath();
                var parts = path.split("/");
                
                // /api/schema with no collection - return list of all registered schemas
                if (parts.length < 4 || parts[3].isEmpty()) {
                    if ("GET".equals(exchange.getRequestMethod())) {
                        sendJson(exchange, 200, Map.of("schemas", schemaValidator.getSchemaNames()));
                    } else {
                        sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                    }
                    return;
                }
                
                var collectionName = parts[3];

                if ("GET".equals(exchange.getRequestMethod())) {
                    if (schemaValidator.hasSchema(collectionName)) {
                        var schema = schemaValidator.getSchema(collectionName);
                        var fieldsList = new java.util.ArrayList<Map<String, Object>>();
                        for (Object f : schema.getFields()) {
                            try {
                                var nameF = f.getClass().getDeclaredField("name");
                                nameF.setAccessible(true);
                                var typeF = f.getClass().getDeclaredField("type");
                                typeF.setAccessible(true);
                                var reqF  = f.getClass().getDeclaredField("required");
                                reqF.setAccessible(true);
                                
                                fieldsList.add(Map.of(
                                    "name", nameF.get(f),
                                    "type", ((Class<?>) typeF.get(f)).getSimpleName(),
                                    "required", reqF.get(f)
                                ));
                            } catch (Exception e) {
                                logger.error("Failed to parse schema field via reflection", e);
                            }
                        }
                        sendJson(exchange, 200, Map.of(
                            "collectionName", schema.getCollectionName(),
                            "strict", schema.isStrict(),
                            "fields", fieldsList
                        ));
                    } else {
                        sendJson(exchange, 404, Map.of("error", "No schema found for collection: " + collectionName));
                    }
                } else if ("POST".equals(exchange.getRequestMethod())) {
                    try {
                        var body = readBody(exchange);
                        var data = JsonSerde.fromJson(body, Map.class);
                        var schema = org.junify.db.core.schema.SchemaValidator.builder(collectionName);
                        
                        if (data.containsKey("fields")) {
                            Object fieldsObj = data.get("fields");
                            if (fieldsObj instanceof java.util.List<?> fieldsList) {
                                for (Object f : fieldsList) {
                                    if (f instanceof Map<?, ?> fMap) {
                                        String name = (String) fMap.get("name");
                                        String typeStr = (String) fMap.get("type");
                                        boolean required = Boolean.TRUE.equals(fMap.get("required"));
                                        Class<?> type = parseFieldType(typeStr);
                                        if (name != null) {
                                            schema.field(name, type, required);
                                        }
                                    }
                                }
                            } else if (fieldsObj instanceof Map<?, ?> fieldsMap) {
                                for (var entry : fieldsMap.entrySet()) {
                                    String name = entry.getKey().toString();
                                    Object val = entry.getValue();
                                    String typeStr = "String";
                                    boolean required = false;
                                    if (val instanceof Map<?, ?> valMap) {
                                        typeStr = valMap.containsKey("type") && valMap.get("type") != null ? valMap.get("type").toString() : "String";
                                        required = Boolean.TRUE.equals(valMap.get("required"));
                                    } else if (val instanceof String s) {
                                        typeStr = s;
                                    }
                                    Class<?> type = parseFieldType(typeStr);
                                    schema.field(name, type, required);
                                }
                            }
                        }
                        
                        if (Boolean.TRUE.equals(data.get("strict"))) {
                            try {
                                var strictField = schema.getClass().getDeclaredField("strict");
                                strictField.setAccessible(true);
                                strictField.set(schema, true);
                            } catch (Exception e) {
                                logger.error("Failed to set strict mode on schema via reflection", e);
                            }
                        }
                        
                        schemaValidator.registerSchema(collectionName, schema);
                        sendJson(exchange, 201, Map.of(
                            "status", "schema registered",
                            "collection", collectionName
                        ));
                    } catch (Exception e) {
                        logger.error("Failed to register schema", e);
                        sendJson(exchange, 500, Map.of("error", "Schema registration failed", "message", e.getMessage()));
                    }
                } else if ("DELETE".equals(exchange.getRequestMethod())) {
                    schemaValidator.dropSchema(collectionName);
                    sendJson(exchange, 200, Map.of(
                        "status", "schema dropped",
                        "collection", collectionName
                    ));
                } else {
                    sendJson(exchange, 405, Map.of("error", "Method not allowed"));
                }
            } catch (Throwable t) {
                logger.error("Uncaught error in SchemaHandler", t);
                try {
                    sendJson(exchange, 500, Map.of("error", "Server error", "message", t.getMessage()));
                } catch (Exception ignored) {}
            }
        }

        private Class<?> parseFieldType(String typeStr) {
            if (typeStr == null) return String.class;
            if ("Integer".equalsIgnoreCase(typeStr) || "int".equalsIgnoreCase(typeStr)) return Integer.class;
            if ("Long".equalsIgnoreCase(typeStr)) return Long.class;
            if ("Double".equalsIgnoreCase(typeStr) || "float".equalsIgnoreCase(typeStr) || "number".equalsIgnoreCase(typeStr)) return Double.class;
            if ("Boolean".equalsIgnoreCase(typeStr) || "bool".equalsIgnoreCase(typeStr)) return Boolean.class;
            if ("Map".equalsIgnoreCase(typeStr) || "object".equalsIgnoreCase(typeStr)) return Map.class;
            if ("List".equalsIgnoreCase(typeStr) || "array".equalsIgnoreCase(typeStr)) return java.util.List.class;
            return String.class;
        }
    }

    private java.util.Map<Integer, org.junify.db.transaction.mvcc.Transaction> activeTransactions = new java.util.concurrent.ConcurrentHashMap<>();
    private org.junify.db.core.schema.SchemaValidator schemaValidator = new org.junify.db.core.schema.SchemaValidator();
    private java.util.Map<String, org.junify.db.index.hnsw.HNSWIndex> vectorIndexes = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * R-52: vector indexes used to live only in this server instance's memory —
     * every restart silently lost all vectors and dimensions. Persisted as one
     * JSON document per index under {@code <dataDir>/vectors/} on every
     * mutation (best-effort, mirroring the audit writer: a failure never fails
     * the request) and restored at server start. Skipped entirely for the
     * IN_MEMORY engine, which promises no durability.
     */
    private void persistVectorIndexes() {
        java.nio.file.Path dir;
        try {
            var dataDir = db.config().dataDir();
            if (dataDir == null
                    || db.config().storageEngine() == org.junify.db.config.JunifyDBConfig.StorageEngineType.IN_MEMORY) {
                return;
            }
            dir = dataDir.resolve("vectors");
        } catch (Exception e) {
            return;
        }
        try {
            java.nio.file.Files.createDirectories(dir);
            for (var entry : vectorIndexes.entrySet()) {
                java.nio.file.Path file = dir.resolve(entry.getKey() + ".json");
                java.nio.file.Files.writeString(file, entry.getValue().toJson());
            }
        } catch (Exception e) {
            System.err.println("[VECTORS] disk persist failed (continuing): " + e.getMessage());
        }
    }

    private void restoreVectorIndexes() {
        java.nio.file.Path dir;
        try {
            var dataDir = db.config().dataDir();
            if (dataDir == null
                    || db.config().storageEngine() == org.junify.db.config.JunifyDBConfig.StorageEngineType.IN_MEMORY) {
                return;
            }
            dir = dataDir.resolve("vectors");
        } catch (Exception e) {
            return;
        }
        if (!java.nio.file.Files.isDirectory(dir)) return;
        try (var files = java.nio.file.Files.list(dir)) {
            files.filter(p -> p.getFileName().toString().endsWith(".json")).forEach(p -> {
                String name = p.getFileName().toString().replace(".json", "");
                try {
                    vectorIndexes.put(name, org.junify.db.index.hnsw.HNSWIndex.fromJson(
                            java.nio.file.Files.readString(p)));
                    logger.info("[JunifyDBServer] Restored vector index '{}' from disk", name);
                } catch (Exception e) {
                    // A corrupt index file must not prevent the server from
                    // starting — it is renamed aside and reported.
                    System.err.println("[VECTORS] restore failed for '" + name + "': " + e.getMessage());
                    try {
                        java.nio.file.Files.move(p, p.resolveSibling(name + ".json.corrupt-" + System.currentTimeMillis()));
                    } catch (Exception ignored) {}
                }
            });
        } catch (Exception e) {
            System.err.println("[VECTORS] restore scan failed (continuing): " + e.getMessage());
        }
    }

    private class VectorHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthValid(exchange)) { sendAuthError(exchange); return; }
            if (!isCsrfValid(exchange)) { sendCsrfError(exchange); return; }
            var path = exchange.getRequestURI().getPath();
            var parts = path.split("/");
            if (parts.length < 5) {
                sendJson(exchange, 400, Map.of("error", "Usage: /api/vectors/{index}[/id]"));
                return;
            }
            var indexName = parts[3];
            var hnsw = vectorIndexes.get(indexName);
            
            var id = parts[4];

            // The request body can only be read once. Read it here for all POST
            // paths so index creation (which needs the vector) and the operation
            // itself can share it.
            Map<String, Object> bodyData = null;
            if ("POST".equals(exchange.getRequestMethod()) || "PUT".equals(exchange.getRequestMethod())) {
                try {
                    var raw = readBody(exchange);
                    if (raw != null && !raw.isBlank()) {
                        bodyData = JsonSerde.fromJson(raw, Map.class);
                    }
                } catch (Exception e) {
                    sendJson(exchange, 400, Map.of("error", "Invalid JSON body", "message", e.getMessage()));
                    return;
                }
            }

            // Create-on-first-use with client-derived dimensions (audit R-19 /
            // 12-IX-02): the first add/search on an index fixes its dimensionality
            // from the vector the client actually sends (or an explicit "dims"
            // body field), instead of a hardcoded 128 that rejects every other
            // embedding size.
            if (hnsw == null) {
                if (bodyData == null) {
                    sendJson(exchange, 404, Map.of("error", "Vector index not found", "index", indexName));
                    return;
                }
                // R-50: a search against an unknown index used to silently create
                // an empty index dimensioned from the query vector and answer
                // 200 results:[] — a success signal for a typo'd index name.
                // Only add/insert (POST to /{index}/{id}) may create-on-first-use.
                if ("search".equals(id)) {
                    sendJson(exchange, 404, Map.of("error", "Vector index not found", "index", indexName));
                    return;
                }
                int dims = bodyData.containsKey("dims") ? ((Number) bodyData.get("dims")).intValue()
                        : (bodyData.get("vector") instanceof java.util.List<?> v ? v.size() : 0);
                if (dims <= 0) {
                    sendJson(exchange, 400, Map.of("error",
                            "New vector index \"" + indexName + "\" requires a vector or a \"dims\" field to fix dimensionality"));
                    return;
                }
                hnsw = vectorIndexes.computeIfAbsent(indexName, k -> new org.junify.db.index.hnsw.HNSWIndex(dims));
            }

            // /api/vectors/{index}/search — POST search
            if ("search".equals(id) && "POST".equals(exchange.getRequestMethod())) {
                try {
                    if (!(bodyData.get("vector") instanceof java.util.List<?> rawVector)) {
                        // R-50: a missing "vector" used to reach parseVector(null)
                        // and surface as an NPE 500.
                        sendJson(exchange, 400, Map.of("error",
                                "Search requires a \"vector\" array field"));
                        return;
                    }
                    var vector = parseVector(rawVector);
                    var k = bodyData.containsKey("k") ? ((Number) bodyData.get("k")).intValue() : 5;
                    if (k <= 0) {
                        // R-50: k=0 silently returned an empty "success" and k<0
                        // surfaced as a bare "-5" 500 from the engine.
                        sendJson(exchange, 400, Map.of("error", "k must be a positive integer"));
                        return;
                    }
                    var results = hnsw.search(vector, k);
                    sendJson(exchange, 200, Map.of("results", results, "k", k));
                } catch (IllegalArgumentException e) {
                    // dimension mismatch and similar caller mistakes
                    sendJson(exchange, 400, Map.of("error", "Search failed", "message", e.getMessage()));
                } catch (Exception e) {
                    sendJson(exchange, 500, Map.of("error", "Search failed", "message", e.getMessage()));
                }
                return;
            }

            // /api/vectors/{index}/{id} — GET info (index-level when id missing)
            if (parts.length == 4 || id.isEmpty()) {
                sendJson(exchange, 200, Map.of("index", indexName, "dimensions", hnsw.dimensions(), "size", hnsw.size()));
                return;
            }

            if ("GET".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 200, Map.of("id", id, "index", indexName, "size", hnsw.size(), "dimensions", hnsw.dimensions()));
            } else if ("POST".equals(exchange.getRequestMethod())) {
                try {
                    // Support {id, vector, metadata} or just {vector}
                    String vecId = bodyData.containsKey("id") ? bodyData.get("id").toString() : id;
                    var vector = parseVector((java.util.List<?>) bodyData.get("vector"));
                    hnsw.add(vecId, vector);
                    persistVectorIndexes();
                    sendJson(exchange, 201, Map.of("id", vecId, "status", "added"));
                } catch (Exception e) {
                    sendJson(exchange, 400, Map.of("error", "Insert failed", "message", e.getMessage()));
                }
            } else if ("DELETE".equals(exchange.getRequestMethod())) {
                try { hnsw.remove(id); persistVectorIndexes(); sendJson(exchange, 204, null); }
                catch (Exception e) { sendJson(exchange, 500, Map.of("error", e.getMessage())); }
            }
        }        
        private float[] parseVector(java.util.List<?> list) {
            float[] vector = new float[list.size()];
            for (int i = 0; i < list.size(); i++) {
                vector[i] = ((Number) list.get(i)).floatValue();
            }
            return vector;
        }
    }

    private void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        addSecurityHeaders(exchange);
        // Handle 204 No Content separately
        if (status == 204) {
            addCorsHeaders(exchange);
            exchange.getResponseHeaders().set("Content-Length", "0");
            exchange.sendResponseHeaders(204, -1);
            return;
        }
        
        addCorsHeaders(exchange);
        exchange.getResponseHeaders().set("Content-Type", "application/json");

        // Every response carries a correlation id, and every error body embeds it, so a
        // user can quote one value and an operator can find the matching server log line.
        // A caller-supplied X-Correlation-Id is honoured so a request can be traced end to end.
        String correlationId = exchange.getRequestHeaders().getFirst("X-Correlation-Id");
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = java.util.UUID.randomUUID().toString().substring(0, 8);
        }
        exchange.getResponseHeaders().set("X-Correlation-Id", correlationId);
        if (status >= 400) {
            body = withCorrelationId(body, correlationId);
        }

        var json = body != null ? JsonSerde.toJson(body) : "";
        var bytes = json.getBytes(StandardCharsets.UTF_8);

        var acceptEncoding = exchange.getRequestHeaders().getFirst("Accept-Encoding");
        boolean useGzip = compressionEnabled && acceptEncoding != null && acceptEncoding.contains("gzip");

        if (useGzip && bytes.length > 1024) {
            exchange.getResponseHeaders().set("Content-Encoding", "gzip");
            var baos = new java.io.ByteArrayOutputStream();
            try (var gzos = new GZIPOutputStream(baos)) {
                gzos.write(bytes);
            }
            bytes = baos.toByteArray();
        }

        exchange.getResponseHeaders().set("Content-Length", String.valueOf(bytes.length));
        exchange.sendResponseHeaders(status, bytes.length);
        try (var os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * Copies an error payload into a mutable map and stamps {@code correlationId} on it.
     * Non-map bodies (lists, strings) are wrapped so the id is never silently dropped.
     */
    private static Object withCorrelationId(Object body, String correlationId) {
        if (body instanceof Map<?, ?> map) {
            if (map.containsKey("correlationId")) return body;
            var copy = new LinkedHashMap<String, Object>();
            for (var entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            copy.put("correlationId", correlationId);
            return copy;
        }
        var wrapper = new LinkedHashMap<String, Object>();
        wrapper.put("error", body == null ? "Request failed" : String.valueOf(body));
        wrapper.put("correlationId", correlationId);
        return wrapper;
    }

    private class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthValid(exchange)) { sendAuthError(exchange); return; }
            if ("GET".equals(exchange.getRequestMethod())) {
                sendJson(exchange, 200, db.metrics().snapshot());
            }
        }
    }

    private class MetricsStreamHandler implements HttpHandler {
        private volatile boolean running = true;

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthValid(exchange)) { sendAuthError(exchange); return; }
            
            // SSE headers
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.getResponseHeaders().set("Connection", "keep-alive");
            // R-61: honour the operator's CORS policy here too. This stream used to
            // advertise wildcard CORS unconditionally, bypassing both the
            // corsEnabled flag and any configured origin allowlist.
            addCorsHeaders(exchange);
            
            exchange.sendResponseHeaders(200, 0);
            
            try (var os = exchange.getResponseBody()) {
                while (running && !Thread.currentThread().isInterrupted()) {
                    try {
                        var metrics = db.metrics().snapshot();
                        var event = "data: " + JsonSerde.toJson(metrics) + "\n\n";
                        os.write(event.getBytes(StandardCharsets.UTF_8));
                        os.flush();
                        Thread.sleep(1000); // Stream metrics every second
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (IOException e) {
                        break;
                    }
                }
            } catch (IOException ignored) {}
        }
    }

    private class StatsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthValid(exchange)) { sendAuthError(exchange); return; }
            if ("GET".equals(exchange.getRequestMethod())) {
                var runtime = Runtime.getRuntime();
                var memory = Map.of(
                        "totalMemory", runtime.totalMemory(),
                        "freeMemory", runtime.freeMemory(),
                        "usedMemory", runtime.totalMemory() - runtime.freeMemory(),
                        "maxMemory", runtime.maxMemory(),
                        "availableProcessors", runtime.availableProcessors()
                );
                sendJson(exchange, 200, Map.of(
                        "database", Map.of("open", db.isOpen(), "engine", db.config().storageEngine().name()),
                        "memory", memory,
                        "threads", Map.of("activeCount", Thread.activeCount())
                ));
            }
        }
    }

    private String readBody(HttpExchange exchange) throws IOException {
        // Check Content-Length header first
        var contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
        if (contentLength != null) {
            var length = Long.parseLong(contentLength);
            if (length > maxRequestSizeBytes) {
                throw new IOException("Request size " + length + " exceeds maximum allowed size " + maxRequestSizeBytes);
            }
        }
        
        // Read body with size limit enforcement
        try (InputStream is = exchange.getRequestBody()) {
            var bytes = is.readAllBytes();
            if (bytes.length > maxRequestSizeBytes) {
                throw new IOException("Request body size " + bytes.length + " exceeds maximum allowed size " + maxRequestSizeBytes);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private class BulkHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthValid(exchange)) { sendAuthError(exchange); return; }
            if (!isCsrfValid(exchange)) { sendCsrfError(exchange); return; }
            var path = exchange.getRequestURI().getPath();
            var parts = path.split("/");
            if (parts.length < 4) {
                sendJson(exchange, 400, Map.of("error", "Usage: /api/bulk/{collection}"));
                return;
            }
            var collectionName = parts[3];
            var collection = db.documentCollection(collectionName);
            
            if ("POST".equals(exchange.getRequestMethod())) {
                var body = readBody(exchange);
                java.util.List<?> docs;
                try {
                    docs = JsonSerde.fromJson(body, java.util.List.class);
                } catch (Exception e) {
                    sendJson(exchange, 400, Map.of("error", "Invalid JSON body: expected an array of documents"));
                    return;
                }
                var count = 0;
                var skipped = 0;
                if (docs instanceof java.util.List) {
                    for (Object doc : (java.util.List<?>) docs) {
                        if (doc instanceof java.util.Map) {
                            var docMap = (java.util.Map<?, ?>) doc;
                            var docEntity = new org.junify.db.nosql.document.Document();
                            // R-44: honour a client-supplied id, matching the single-document
                            // POST. The old code always generated a UUID, silently demoting the
                            // caller's "id" to an ordinary field — so a bulk insert followed by
                            // a GET-by-id returned 404 for every id the client had just sent.
                            var idValue = docMap.get("id");
                            docEntity.id(idValue != null ? idValue.toString() : java.util.UUID.randomUUID().toString());
                            var fields = new java.util.LinkedHashMap<String, Object>();
                            for (var entry : docMap.entrySet()) {
                                var key = String.valueOf(entry.getKey());
                                if ("id".equals(key) && idValue != null) {
                                    continue; // id is the identity, not a duplicated field
                                }
                                fields.put(key, entry.getValue());
                            }
                            docEntity.getFields().putAll(fields);
                            collection.insert(docEntity);
                            count++;
                        } else {
                            skipped++;
                        }
                    }
                }
                sendJson(exchange, 201, Map.of(
                    "status", "success",
                    "collection", collectionName,
                    "inserted", count,
                    "skipped", skipped
                ));
            } else if ("DELETE".equals(exchange.getRequestMethod())) {
                var count = 0;
                for (var doc : collection.findAll()) {
                    collection.deleteById(doc.getId());
                    count++;
                }
                sendJson(exchange, 200, Map.of(
                    "status", "success",
                    "collection", collectionName,
                    "deleted", count
                ));
            } else {
                sendJson(exchange, 405, Map.of("error", "Only POST or DELETE allowed"));
            }
        }
    }

    private class CDCHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!isAuthValid(exchange)) { sendAuthError(exchange); return; }
            if (!isCsrfValid(exchange)) { sendCsrfError(exchange); return; }
            var path = exchange.getRequestURI().getPath();
            var parts = path.split("/");
            
            if (parts.length == 3) {
                if ("GET".equals(exchange.getRequestMethod())) {
                    var status = db.cdcManager().getStatus();
                    sendJson(exchange, 200, status);
                    return;
                }
            }
            
            if (parts.length >= 4) {
                var action = parts[3];
                
                if ("connectors".equals(action) && parts.length >= 5) {
                    var connectorName = parts[4];
                    
                    if ("POST".equals(exchange.getRequestMethod())) {
                        var body = readBody(exchange);
                        Map<String, Object> data;
                        try {
                            data = body == null || body.isBlank() ? Map.of() : JsonSerde.fromJson(body, Map.class);
                        } catch (Exception e) {
                            sendJson(exchange, 400, Map.of("error", "Invalid JSON body: " + e.getMessage()));
                            return;
                        }
                        var type = data.get("type");
                        // Validate before dereferencing: missing fields used to throw out of the
                        // handler, which dropped the connection instead of explaining the problem.
                        if (type == null) {
                            sendJson(exchange, 400, Map.of(
                                "error", "'type' is required",
                                "file", Map.of("type", "file", "outputDir", "<directory>"),
                                "kafka", Map.of("type", "kafka", "bootstrapServers", "host:9092", "topic", "<topic>")
                            ));
                            return;
                        }
                        if ("file".equals(type.toString())) {
                            var outputDirValue = data.get("outputDir");
                            if (outputDirValue == null) {
                                sendJson(exchange, 400, Map.of("error", "'outputDir' is required for a file connector"));
                                return;
                            }
                            try {
                                var outputDir = java.nio.file.Paths.get(outputDirValue.toString());
                                db.cdcManager().addFileConnector(connectorName, outputDir);
                                sendJson(exchange, 201, Map.of("status", "connected", "type", "file", "name", connectorName));
                            } catch (Exception e) {
                                sendJson(exchange, 400, Map.of("error", "Could not create file connector: " + e.getMessage()));
                            }
                        } else if ("kafka".equals(type.toString())) {
                            var bootstrapServers = data.get("bootstrapServers");
                            var topic = data.get("topic");
                            if (bootstrapServers == null || topic == null) {
                                sendJson(exchange, 400, Map.of("error", "'bootstrapServers' and 'topic' are required for a kafka connector"));
                                return;
                            }
                            db.cdcManager().addKafkaConnector(connectorName, bootstrapServers.toString(), topic.toString());
                            sendJson(exchange, 201, Map.of(
                                "status", "connected",
                                "type", "kafka",
                                "name", connectorName,
                                "note", "delivery requires a Kafka client on the classpath; without one this connector only queues events in memory"
                            ));
                        } else {
                            sendJson(exchange, 400, Map.of("error", "Unknown connector type: " + type));
                        }
                        return;
                    } else if ("DELETE".equals(exchange.getRequestMethod())) {
                        // Only report a disconnect for a connector that actually exists; the
                        // old response said "disconnected" for any name at all.
                        if (!db.cdcManager().hasConnector(connectorName)) {
                            sendJson(exchange, 404, Map.of("error", "No connector named '" + connectorName + "'"));
                            return;
                        }
                        db.cdcManager().removeFileConnector(connectorName);
                        db.cdcManager().removeKafkaConnector(connectorName);
                        sendJson(exchange, 200, Map.of("status", "disconnected", "name", connectorName));
                        return;
                    }
                }
                
                if ("events".equals(action)) {
                    var since = exchange.getRequestHeaders().getFirst("Since");
                    var events = since != null 
                        ? db.cdcManager().processor().getEventsSince(Long.parseLong(since))
                        : db.cdcManager().processor().getEventLog();
                    sendJson(exchange, 200, Map.of("events", events));
                    return;
                }
                
                if ("enable".equals(action)) {
                    db.cdcManager().processor().enable();
                    sendJson(exchange, 200, Map.of("status", "enabled"));
                    return;
                }
                
                if ("disable".equals(action)) {
                    db.cdcManager().processor().disable();
                    sendJson(exchange, 200, Map.of("status", "disabled"));
                    return;
                }
            }
            
            sendJson(exchange, 400, Map.of("error", "Usage: GET /api/cdc, POST/DELETE /api/cdc/connectors/{name}, GET /api/cdc/events"));
        }
    }

    private class SqlHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            addCorsHeaders(exchange);
            addSecurityHeaders(exchange);
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!isAuthValid(exchange)) { sendAuthError(exchange); return; }
            if (!isCsrfValid(exchange)) { sendCsrfError(exchange); return; }

            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    String body = readBody(exchange);
                    Map<?, ?> payload = JsonSerde.fromJson(body, Map.class);
                    String sql = (String) payload.get("query");
                    if (sql == null || sql.isBlank()) {
                        sendJson(exchange, 400, Map.of("error", "Query must not be empty"));
                        return;
                    }

                    List<?> rawParams = (List<?>) payload.get("params");
                    Object[] params = rawParams != null ? rawParams.toArray() : new Object[0];

                    long start = System.currentTimeMillis();
                    var rs = db.sql(sql, params);
                    long duration = System.currentTimeMillis() - start;

                    // R-28: mutations issued through the SQL console path must be
                    // audited exactly like REST CRUD. Parsing is intentionally
                    // lexical-only (no engine round-trip), so it cannot widen the
                    // engine's attack surface; audit is evidentiary, not a filter.
                    auditSqlStatements(sql, getClientIp(exchange));

                    List<Map<String, Object>> rows = new ArrayList<>();
                    for (var r : rs.getRows()) {
                        rows.add(r.asMap());
                    }

                    sendJson(exchange, 200, Map.of(
                            "columns", rs.getColumnNames(),
                            "rows", rows,
                            "rowCount", rs.size(),
                            "executionTimeMs", duration,
                            "status", "success"
                    ));
                } catch (org.junify.db.sql.SqlUnknownTableException e) {
                    // R-48/R-49: a statement that referenced a table which does
                    // not exist is a client error about state, not a syntax
                    // problem — 404 distinguishes it from 400 parse errors and
                    // from the old fake rowCount:0 success.
                    sendJson(exchange, 404, Map.of(
                            "error", "Table does not exist",
                            "message", e.getMessage() != null ? e.getMessage() : e.toString()
                    ));
                } catch (Exception e) {
                    sendJson(exchange, 400, Map.of(
                            "error", "SQL Execution Error",
                            "message", e.getMessage() != null ? e.getMessage() : e.toString()
                    ));
                }
            } else {
                sendJson(exchange, 405, Map.of("error", "Method not allowed. Use POST with JSON payload."));
            }
        }

        /**
         * R-28: audits every mutation statement in a console SQL batch so the
         * SQL Studio path leaves the same evidentiary trail as REST CRUD
         * (logAuditEvent keeps the in-memory ring and the JSONL disk log in
         * step). Lexical split on semicolons outside quotes; reads are not
         * audited. Best-effort: a parsing failure never fails the SQL call.
         */
        private void auditSqlStatements(String sql, String clientIp) {
            try {
                for (String raw : splitSqlStatements(sql)) {
                    String stmt = raw.strip();
                    if (stmt.isEmpty()) continue;
                    String upper = stmt.toUpperCase();
                    String op;
                    if (upper.startsWith("INSERT")) op = "INSERT";
                    else if (upper.startsWith("UPDATE")) op = "UPDATE";
                    else if (upper.startsWith("DELETE")) op = "DELETE";
                    else if (upper.startsWith("CREATE")) op = "CREATE";
                    else if (upper.startsWith("DROP")) op = "DROP";
                    else continue; // reads and unsupported statements are not audited
                    String resource = extractSqlTarget(stmt);
                    logAuditEvent(op, resource, null, "SUCCESS", clientIp, "SQL console execution");
                }
            } catch (Exception e) {
                logger.warn("[AUDIT] SQL statement audit skipped (fail-open): {}", e.toString());
            }
        }
    }

    /** Package-visible for testing: lexical split of a SQL batch on semicolons
     *  that are not inside single quotes, double quotes, or comments. */
    static java.util.List<String> splitSqlStatements(String sql) {
        var statements = new java.util.ArrayList<String>();
        if (sql == null || sql.isBlank()) return statements;
        StringBuilder current = new StringBuilder();
        boolean inSingle = false, inDouble = false, inLineComment = false, inBlockComment = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (inLineComment) {
                current.append(c);
                if (c == '\n') inLineComment = false;
                continue;
            }
            if (inBlockComment) {
                current.append(c);
                if (c == '*' && i + 1 < sql.length() && sql.charAt(i + 1) == '/') {
                    current.append(sql.charAt(++i));
                    inBlockComment = false;
                }
                continue;
            }
            if (inSingle) {
                current.append(c);
                if (c == '\'') {
                    if (i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                        current.append(sql.charAt(++i)); // escaped quote
                    } else {
                        inSingle = false;
                    }
                }
                continue;
            }
            if (inDouble) {
                current.append(c);
                if (c == '"') inDouble = false;
                continue;
            }
            if (c == '\'' ) { inSingle = true; current.append(c); continue; }
            if (c == '"') { inDouble = true; current.append(c); continue; }
            if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-') {
                inLineComment = true; current.append(c); continue;
            }
            if (c == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*') {
                inBlockComment = true; current.append(c); continue;
            }
            if (c == ';') {
                statements.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        if (!current.toString().isBlank()) statements.add(current.toString());
        return statements;
    }

    /** Package-visible for testing: best-effort extraction of the collection or
     *  table a SQL statement targets (INSERT/UPDATE/DELETE FROM/CREATE/DROP). */
    static String extractSqlTarget(String stmt) {
        var m = java.util.regex.Pattern
                .compile("(?i)\\b(?:INSERT\\s+INTO|UPDATE|DELETE\\s+FROM|CREATE\\s+TABLE|DROP\\s+TABLE)\\s+([\\w\".]+)")
                .matcher(stmt);
        return m.find() ? m.group(1) : "sql";
    }
}

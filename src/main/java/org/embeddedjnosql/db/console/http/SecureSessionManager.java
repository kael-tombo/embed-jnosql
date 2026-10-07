package org.embeddedjnosql.db.console.http;

import com.sun.net.httpserver.HttpExchange;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Secure Session Manager
 * 
 * Implements OWASP session management best practices:
 * - Secure random session ID generation (256-bit)
 * - HttpOnly cookies (XSS protection)
 * - Secure flag (HTTPS only)
 * - SameSite=Strict/Lax
 * - Configurable TTL with absolute maximum
 */
public class SecureSessionManager {
    
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int SESSION_ID_BYTES = 32;  // 256 bits
    
    // Session configuration defaults
    public static final long DEFAULT_SESSION_TTL_MS = 30 * 60 * 1000L;   // 30 minutes
    public static final long DEFAULT_ABSOLUTE_TTL_MS = 8 * 60 * 60 * 1000L;  // 8 hours max
    private static final int MAX_SESSIONS_PER_USER = 5;

    private final long sessionTtlMs;
    private final long absoluteTtlMs;

    public SecureSessionManager() {
        this(DEFAULT_SESSION_TTL_MS, DEFAULT_ABSOLUTE_TTL_MS);
    }

    public SecureSessionManager(long sessionTtlMs) {
        this(sessionTtlMs, Math.max(sessionTtlMs, DEFAULT_ABSOLUTE_TTL_MS));
    }

    public SecureSessionManager(long sessionTtlMs, long absoluteTtlMs) {
        this.sessionTtlMs = sessionTtlMs > 0 ? sessionTtlMs : DEFAULT_SESSION_TTL_MS;
        this.absoluteTtlMs = absoluteTtlMs > 0 ? absoluteTtlMs : DEFAULT_ABSOLUTE_TTL_MS;
    }
    
    /**
     * Generate a cryptographically secure session ID
     */
    public String generateSessionId() {
        byte[] bytes = new byte[SESSION_ID_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    /**
     * Set secure session cookie
     */
    public void setSessionCookie(HttpExchange exchange, String sessionId, boolean secure) {
        long maxAgeSec = sessionTtlMs / 1000L;
        String setCookieHeader = String.format(
            "EMBED_SESSION=%s; Path=/; Max-Age=%d; HttpOnly%s; SameSite=Lax",
            sessionId,
            maxAgeSec,
            secure ? "; Secure" : ""
        );
        exchange.getResponseHeaders().add("Set-Cookie", setCookieHeader);
    }
    
    /**
     * Clear session cookie (logout)
     */
    public void clearSessionCookie(HttpExchange exchange) {
        String setCookieHeader = "EMBED_SESSION=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax";
        exchange.getResponseHeaders().add("Set-Cookie", setCookieHeader);
    }
    
    /**
     * Get session ID from cookie
     */
    public String getSessionIdFromCookie(HttpExchange exchange) {
        var cookies = exchange.getRequestHeaders().get("Cookie");
        if (cookies == null) return null;
        
        for (String cookieHeader : cookies) {
            for (String cookie : cookieHeader.split(";")) {
                String[] parts = cookie.trim().split("=", 2);
                if (parts.length == 2 && "EMBED_SESSION".equalsIgnoreCase(parts[0].trim())) {
                    String val = parts[1].trim();
                    if (val.startsWith("\"") && val.endsWith("\"") && val.length() >= 2) {
                        val = val.substring(1, val.length() - 1);
                    }
                    return val;
                }
            }
        }
        return null;
    }
    
    /**
     * Get session TTL in milliseconds
     */
    public long getSessionTtlMs() {
        return sessionTtlMs;
    }
    
    /**
     * Get absolute session TTL
     */
    public long getAbsoluteTtlMs() {
        return absoluteTtlMs;
    }
    
    /**
     * Get max sessions per user
     */
    public int getMaxSessionsPerUser() {
        return MAX_SESSIONS_PER_USER;
    }
}

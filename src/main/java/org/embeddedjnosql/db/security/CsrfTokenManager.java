package org.embeddedjnosql.db.security;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CSRF Token Manager
 * 
 * Implements synchronizer token pattern for CSRF protection:
 * - Cryptographically random tokens (256-bit)
 * - Per-session tokens
 * - Automatic expiration (1 hour)
 * - Token invalidation on use
 */
public class CsrfTokenManager {
    
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;  // 256 bits
    private static final long TOKEN_TTL_MS = 60 * 60 * 1000;  // 1 hour
    
    private final ConcurrentHashMap<String, CsrfToken> tokens = new ConcurrentHashMap<>();
    
    /**
     * Generate a new CSRF token for a session
     */
    public String generateToken(String sessionId) {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        
        tokens.put(token, new CsrfToken(sessionId, System.currentTimeMillis() + TOKEN_TTL_MS));
        cleanupExpiredTokens();
        
        return token;
    }
    
    /**
     * Validate a CSRF token
     */
    public boolean validateToken(String token, String sessionId) {
        CsrfToken csrfToken = tokens.get(token);
        if (csrfToken == null) return false;
        
        if (!csrfToken.sessionId.equals(sessionId)) return false;
        if (System.currentTimeMillis() > csrfToken.expiresAt) {
            tokens.remove(token);
            return false;
        }
        
        return true;
    }
    
    /**
     * Invalidate a token (single-use)
     */
    public void invalidateToken(String token) {
        tokens.remove(token);
    }
    
    /**
     * Invalidate all tokens for a session (logout)
     */
    public void invalidateAllSessionTokens(String sessionId) {
        tokens.entrySet().removeIf(e -> e.getValue().sessionId.equals(sessionId));
    }
    
    /**
     * Cleanup expired tokens
     */
    private void cleanupExpiredTokens() {
        long now = System.currentTimeMillis();
        tokens.entrySet().removeIf(e -> e.getValue().expiresAt < now);
    }
    
    /**
     * Get number of active tokens (for monitoring)
     */
    public int getActiveTokenCount() {
        cleanupExpiredTokens();
        return tokens.size();
    }
    
    private record CsrfToken(String sessionId, long expiresAt) {}
}

# Administration Console Security Model & Hardening

## 1. Overview

The EmbedJNoSQL Administration Console security model is designed according to **OWASP Top 10** guidelines and defense-in-depth principles:

1. **Authentication Barrier**: Dual support for HTTP Basic / Username-Password credentials (session-based) and machine-to-machine API Keys (`X-API-Key` or Bearer token).
2. **Session Hardening**: 256-bit cryptographically secure session identifiers generated via `SecureRandom`. Encapsulated in `HttpOnly`, `SameSite=Lax`, and optional `Secure` cookies with configurable inactivity TTL.
3. **Cross-Site Request Forgery (CSRF) Protection**: Synchronizer Token Pattern implemented by `CsrfTokenManager`. Requires valid, per-session tokens via `X-CSRF-Token` on all state-mutating requests (POST, PUT, DELETE) when using browser cookies.
4. **Brute-Force & Credential Stuffing Defense**: Real-time IP-based failed login attempt tracking. Automatically locks out offending client addresses for a configurable duration (default 15 minutes) after exceeding failed attempt limits.
5. **Security Response Headers**: Injected automatically into every HTTP response:
   - `X-Content-Type-Options: nosniff`
   - `X-Frame-Options: DENY`
   - `X-XSS-Protection: 1; mode=block`
   - `Referrer-Policy: strict-origin-when-cross-origin`
   - `Content-Security-Policy: default-src 'self'; ...`
   - `Strict-Transport-Security` (when SSL/TLS is active)
6. **Rate Limiting**: Sliding-window IP-based rate limiting to prevent denial-of-service and resource exhaustion.
7. **Localhost-Only Default Binding**: Console binds to `127.0.0.1` by default, eliminating exposure to public network interfaces unless intentionally configured.

## 2. Security Configuration Reference

```yaml
embedjnosql:
  security:
    auth-enabled: true
    admin-username: admin
    admin-password: ${JNOSQL_ADMIN_PASSWORD}
    api-key: ${JNOSQL_API_KEY:}
    cors-enabled: false
    csrf-enabled: true
    rate-limit-enabled: true
    rate-limit-requests-per-minute: 120
    brute-force-protection-enabled: true
    max-failed-login-attempts: 5
    lockout-duration-ms: 900000
    security-headers-enabled: true
    min-tls-version: TLSv1.2
```

## 3. Verification Evidence

All security features are rigorously proven by automated integration tests:
- `SecurityEnforcementTest.testAnonymousAccessBlocked` — 401 on unauthenticated access
- `SecurityEnforcementTest.testSecurityHeadersPresent` — OWASP headers confirmed on responses
- `SecurityEnforcementTest.testBruteForceLockout` — 429 after 3 failed attempts
- `SecurityEnforcementTest.testCsrfProtection` — 403 on missing/invalid CSRF, 201 with valid CSRF
- `SecurityEnforcementTest.testLogoutInvalidation` — 401 after session logout

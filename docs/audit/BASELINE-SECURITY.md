# EmbedJNoSQL — Baseline Security Audit

**Audit Date**: September 9, 2026  
**Auditor**: Security Engineer  

---

## 1. Security Baseline & Defenses

1. **Authentication & Session Security**:
   - `SecureSessionManager`: 256-bit cryptographically secure random session IDs.
   - Cookies formatted with `HttpOnly`, `SameSite=Strict`, and `Secure` flags.
   - Fixed 30-minute session TTL with 8-hour absolute maximum TTL.
2. **Denial of Service (DoS) Controls**:
   - Request size limits (`maxRequestSizeBytes = 10MB` default) to prevent JVM heap exhaustion.
   - Configurable query execution timeout (`queryTimeoutSeconds = 30s` default).
   - Per-client IP rate limiting (`rateLimit = 1000 req/min`).
3. **Audit Logging**:
   - Fixed-size in-memory ring buffer (`AUDIT_LOG_MAX_SIZE = 10,000`) recording operation, resource, document ID, client IP, timestamp, and status.
   - Protected endpoint: `/api/audit/logs`.
4. **Transport Security**:
   - Native HTTPS / TLS 1.2+ server support via `configureSsl(port, keystorePath, password)`.

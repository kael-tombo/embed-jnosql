# Sub-Assessment: Administration Console Security & Network Configuration

## 1. Purpose and User Outcome
Ensure the embedded administration console is safely bounded to local interfaces by default, prevents unauthorized access, resists brute-force authentication attacks, enforces cross-site request forgery (CSRF) protections, and secures browser execution via tight Content Security Policy (CSP).

## 2. Relevant Code & Configuration
- `org.embeddedjnosql.db.console.http.EmbedJNoSQLServer`
- `org.embeddedjnosql.db.console.http.PortManager`
- `org.embeddedjnosql.db.security.CsrfTokenManager`
- Configuration: `SecurityConfig`, `ConsoleConfig`.

## 3. Security Subsystems & Verification

### A. Network Interface & Port Binding
- **Default Bind Address**: Localhost only (`127.0.0.1` / `::1`). Never silently binds to `0.0.0.0` or wildcard interfaces unless explicitly configured.
- **Port Collision Policy**: Implemented in `PortManager`. If default port (8090) is occupied, sequentially probes subsequent ports (up to 10 attempts) or uses an ephemeral port, logging the exact bound port and avoiding server startup crashes.
- **Verification**: Tested in `PortManagementTest` and `PortCollisionManagement` trace.

### B. Authentication & Session Management
- **Credentials**: Supports username/password credentials and API Key header (`X-API-Key`).
- **Session Tokens**: Upon successful login, server issues an in-memory session token (`EMBED_SESSION` cookie / `session` token in JSON response) with a 30-minute rolling expiration (`SESSION_TTL_MS`).
- **Logout**: Clears session token server-side and invalidates client session state.
- **Verification**: Tested in `SecurityEnforcementTest`.

### C. Brute-Force Rate Limiting & Lockout
- **Lockout Rule**: 3 failed login attempts within 60 seconds triggers a temporary IP lockout (HTTP 429 Too Many Requests).
- **Retry-After**: Server returns `Retry-After: 60` header, and login frontend dynamically disables submit button with a live countdown timer.
- **Verification**: Verified via HTTP trace `trace-POST-api_auth_login.json` returning status 429 on strike 3.

### D. CSRF Barrier Enforcement
- **Token Mechanism**: Managed via `CsrfTokenManager`. Mutating HTTP requests (`POST`, `PUT`, `DELETE`) require `X-CSRF-Token` header.
- **Enforcement**: Requests with missing or invalid CSRF tokens are rejected with `HTTP 403 Forbidden`.
- **Verification**: Enforced across all mutating console routes in `SecurityEnforcementTest`.

### E. Content Security Policy (CSP) & Security Headers
- **Headers Sent**:
  - `Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'`
  - `X-Content-Type-Options: nosniff`
  - `X-Frame-Options: DENY`
  - `X-XSS-Protection: 1; mode=block`
  - `Referrer-Policy: strict-origin-when-cross-origin`
- **Strict CSP Compliance**:
  - In commit `5b2977b`, inline JavaScript in `login.html` was extracted to external `js/login.js`.
  - `'unsafe-inline'` was removed from `script-src` in `EmbedJNoSQLServer.java`, blocking any injected script execution.
  - Verified by network traces and full test suite run.

### F. Audit Logging
- Bounded ring buffer (`AUDIT_LOG_MAX_SIZE = 10_000`) records login events, CRUD mutations, backups, and lockouts.
- Available via GET `/api/audit/logs`.

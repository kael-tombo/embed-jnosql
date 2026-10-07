# CONSOLE-002: Effective URL Construction & Intelligent Port Management

## 1. Feature Specification
- **Feature ID**: CONSOLE-002
- **Component**: `PortManager`, `ConsoleConfig`
- **Goal**: Automatically detect port collision and bind next available port in range.

## 2. Test Evidence
- **Test File**: [PortManagementTest.java](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/src/test/java/org/embeddedjnosql/db/PortManagementTest.java)
- **Test Method**: `testIntelligentFallbackWhenOccupied`
- **Result**: Port collision resolved cleanly from blocked port `P` to free alternate port in range `[P..P+10]`.
- **Verdict**: **VERIFIED**

---

# CONSOLE-003: Authentication Barrier & Credential Enforcement

## 1. Feature Specification
- **Feature ID**: CONSOLE-003
- **Component**: `EmbedJNoSQLServer#isAuthValid`, `AuthLoginHandler`
- **Goal**: Reject unauthenticated requests with HTTP 401; authorize requests via session cookie or API key.

## 2. Test Evidence
- **Test File**: [SecurityEnforcementTest.java](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/src/test/java/org/embeddedjnosql/db/SecurityEnforcementTest.java)
- **Test Methods**: `testAnonymousAccessBlocked`, `testLogoutInvalidation`
- **Result**: 401 Unauthorized returned for anonymous access; session invalidated on logout.
- **Verdict**: **VERIFIED**

---

# CONSOLE-004: Brute-Force Lockout Protection

## 1. Feature Specification
- **Feature ID**: CONSOLE-004
- **Component**: `EmbedJNoSQLServer$FailedLoginTracker`
- **Goal**: Track failed login attempts per client IP and lock out clients after N consecutive failures with HTTP 429.

## 2. Test Evidence
- **Test File**: [SecurityEnforcementTest.java](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/src/test/java/org/embeddedjnosql/db/SecurityEnforcementTest.java)
- **Test Method**: `testBruteForceLockout`
- **Result**: 3 failed attempts return 401; 4th attempt returns 429 Too Many Requests explaining lockout.
- **Verdict**: **VERIFIED**

---

# CONSOLE-005: CSRF Synchronizer Token Protection

## 1. Feature Specification
- **Feature ID**: CONSOLE-005
- **Component**: `CsrfTokenManager`, `EmbedJNoSQLServer#isCsrfValid`
- **Goal**: Enforce `X-CSRF-Token` header on all mutating operations (POST, PUT, DELETE) when using browser session cookies.

## 2. Test Evidence
- **Test File**: [SecurityEnforcementTest.java](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/src/test/java/org/embeddedjnosql/db/SecurityEnforcementTest.java)
- **Test Method**: `testCsrfProtection`
- **Result**: Mutating POST without CSRF token yields 403 Forbidden; POST with valid CSRF token yields 200/201 Success.
- **Verdict**: **VERIFIED**

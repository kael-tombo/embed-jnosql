# UI Feature Assessment: Session Logout & State Purge

## Feature ID
`UI-020`

## Assessment Date
September 9, 2026

## Repository Commit
`e6bbecb`

## Related Vision Goal
Clean session lifecycle and cookie revocation.

## User Problem
Users need to securely terminate active administrative sessions and clear local credentials.

## User Capability
Click "Logout" button in header, clear session cookies, and return to login screen.

## Expected User Journey
User clicks "Logout" in header menu; session cookie is cleared by server, local storage is purged, browser redirects to `/login.html`.

## Entry Point
Logout button in header navigation.

## Route
`/index.html` $\longrightarrow$ `/login.html`

## Page or Screen
Header Action Dropdown

## Components Involved
`#logoutBtn`, `#headerUserDropdown`

## UI Actions
Click user profile dropdown, click "Log Out".

## Frontend State
`localStorage.removeItem('apiKey'); window.location.href = '/login.html'`

## API Client
`POST /api/auth/logout`

## HTTP Method
`POST`

## Endpoint
`/api/auth/logout`

## Request Parameters
None

## Request Payload
None

## Required Headers
`Accept: application/json`

## Authentication Requirements
Open endpoint to clear cookies.

## Backend Service
`AuthLogoutHandler` & `SecureSessionManager`

## JNOSQL-EMBED Library API
`SecureSessionManager.clearSessionCookie()`

## Storage Engine
In-memory session registry.

## Expected Database State
Session ID removed from active sessions map; Set-Cookie header sends `Max-Age=0`.

## Response Contract
`{ status: "logged_out" }`

## UI Rendering Contract
Clears local storage and redirects immediately to `/login.html`.

## Acceptance Criteria

### Functional Criteria
Revokes session on server, invalidates cookie in browser, purges local storage.

### Integration Criteria
Subsequent requests without re-authentication return 401 Unauthorized.

### Error Criteria
Redirects to login screen even if server is offline.

### Loading Criteria
Button disabled during logout flight.

### Empty-State Criteria
N/A

### Validation Criteria
Valid JSON response.

### Security Criteria
Cookie header set with `Max-Age=0` and `Path=/`.

### Accessibility Criteria
Logout item accessible via keyboard in header dropdown.

### Responsive Criteria
Logout button accessible in mobile navigation drawer.

### Performance Criteria
Logout completes in < 5ms.

## Existing Implementation Assessment
Verified in `AuthLogoutHandler` and UI logout handler.

## Existing Test Assessment
Verified via `EmbedJNoSQLServerTest.authLoginAndLogout()`.

## Missing Tests
None.

## Hardcoded or Mocked Behavior
None.

## Integration Defects
None.

## Backend Defects
None.

## Database Defects
None.

## UI/UX Defects
None.

## Fixes Applied
Added `AuthLogoutHandler` clearing session cookies and map entries.

## Regression Tests Added
`EmbedJNoSQLServerTest.authLoginAndLogout()`

## Exact Test Commands
`mvn test -Dtest=EmbedJNoSQLServerTest`

## Test Output
Tests run: 9, Failures: 0, Errors: 0.

## Evidence Artifacts
`docs/backend/API-PROOF-MATRIX.md`

## Manual Reproduction Steps
1. Click Logout in header.
2. Verify redirect to `/login.html`.
3. Try accessing `/index.html` and verify 401 redirect when auth enabled.

## Remaining Problems
None.

## Final Status
**PASS**

# EmbedJNoSQL — UI Reassessment Round 1

**Audit Date**: September 9, 2026  
**Auditor**: UI QA Specialist  

---

## 1. Initial Assessment Findings

- **Discovery**: Web Console static assets loaded properly, but authentication endpoints were missing from the backend server dispatch (`/api/auth/login`, `/api/auth/logout`), and collection enumeration defaulted to a static help string instead of querying active collections.
- **Defects Identified**:
  - `UI-DEF-01`: Missing backend dispatch for `/api/auth/login` and `/api/auth/logout`.
  - `UI-DEF-02`: Missing dynamic collection enumeration in `/api/collections` (returned `{ "collections": "use /api/collections/{name}" }` rather than an array of collections with counts).
- **Remediation Plan**:
  1. Add `getCollectionNames()` to `EmbedJNoSQL.java`.
  2. Implement `AuthLoginHandler` and `AuthLogoutHandler` with `SecureSessionManager` in `EmbedJNoSQLServer.java`.
  3. Register `/api/collections` as root context returning dynamic collection metadata.

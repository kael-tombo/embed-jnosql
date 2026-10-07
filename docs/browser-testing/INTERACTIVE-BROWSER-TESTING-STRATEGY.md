# Interactive Browser Testing Strategy

## 1. Overview & Objective

The objective of this browser testing suite is to validate the JNOSQL-EMBED administration console using interactive browser automation via `browser_subagent`.

Testing verifies the complete pipeline:
```text
User Interaction
→ Browser DOM / Events
→ JavaScript Client & State
→ HTTP API Requests
→ EmbedJNoSQL Embedded Server
→ Core Database Engine & Storage
→ Response Serialization
→ UI Visual Update
```

## 2. Test Environments & Endpoints

- **Console Base URL**: `http://localhost:9090/jnosql-admin/`
- **Authentication**: Form-based session authentication with cookie and CSRF synchronizer token
- **Credentials**: `admin` / `admin-secret-pass`
- **Storage Engine**: `IN_MEMORY`

## 3. Workflows Covered

1. **Authentication Flow**: Protected barrier, invalid credential error display, successful login, cookie assignment, session persistence.
2. **Dashboard & Metrics**: Cluster health indicator, real-time memory usage, thread counters, uptime gauge.
3. **Document Collections**: Collection navigation, schema validation, document insertion, document editing, JSON viewer/editor, document deletion.
4. **Query Runner**: Filter execution (`price > 50`), projections, sorting, query error handling.
5. **Key-Value & Redis-style Structures**: Key-value bucket manipulation, Lists, Sets, and Hashes.
6. **Column Families**: Wide-column row insertion, column mutations, family scanning.
7. **Schema & Indexes**: Schema registration, constraint verification, index management.
8. **Vector Similarity**: Embeddings insertion and nearest neighbor cosine search.
9. **Backup, CDC & Audit**: Snapshot triggering, CDC consumer status, audit trail log viewing.
10. **Session Termination**: Logout flow, session revocation, redirect to sign-in screen.

## 4. Evidence Criteria

Every feature assessed requires:
- Recorded browser workflow (`.webp` video artifact).
- DOM element verification before and after action.
- Network API trace inspection.
- Direct database state verification.
- Authoritative feature assessment document.

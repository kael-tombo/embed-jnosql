# UI Feature Assessment: Storage Engine Inspection & Selection

## Feature ID
`UI-004`

## Assessment Date
September 9, 2026

## Repository Commit
`e6bbecb`

## Related Vision Goal
Pluggable storage engine transparency (`IN_MEMORY`, `FILE`, `B_TREE`, `LSM_TREE`).

## User Problem
Users need to know which storage engine is currently powering their database instance and where data is persisted on disk.

## User Capability
Inspect the active engine type, storage directory path, and estimated disk utilization.

## Expected User Journey
User views Storage Engine card on `#overview` tab; engine badge updates dynamically to match configuration.

## Entry Point
`#overview` tab Storage Engine Card.

## Route
`/index.html#overview`

## Page or Screen
Overview Dashboard

## Components Involved
`#engineBadge`, `#dbEngine`, `#dataDir`, `#diskUsage`

## UI Actions
View active engine details on load.

## Frontend State
Engine metadata stored in `window.dbConfig`.

## API Client
`GET /api/stats` and `GET /api/health`

## HTTP Method
`GET`

## Endpoint
`/api/stats`

## Request Parameters
None

## Request Payload
None

## Required Headers
`Accept: application/json`

## Authentication Requirements
Inherited from session cookie.

## Backend Service
`StatsHandler` in `EmbedJNoSQLServer`

## JNOSQL-EMBED Library API
`EmbedJNoSQL.config().storageEngine()`

## Storage Engine
Reflects active engine (`IN_MEMORY`, `FILE`, `B_TREE`, `LSM_TREE`).

## Expected Database State
Read-only metadata query.

## Response Contract
`{ database: { open: true, engine: "IN_MEMORY" }, memory: { ... }, threads: { ... } }`

## UI Rendering Contract
Displays pill badge `#engineBadge` and text `#dbEngine`.

## Acceptance Criteria

### Functional Criteria
Displays accurate engine name matching JVM startup config.

### Integration Criteria
Reflects dynamic configuration changes across engine restarts.

### Error Criteria
Displays "Disconnected" if server unreachable.

### Loading Criteria
Displays placeholder skeleton while fetching stats.

### Empty-State Criteria
N/A

### Validation Criteria
Valid engine enum string.

### Security Criteria
Sanitizes path rendering to avoid XSS.

### Accessibility Criteria
Text contrast conforms to WCAG AA.

### Responsive Criteria
Path string truncates with ellipsis on narrow screens.

### Performance Criteria
Stats returned in < 2ms.

## Existing Implementation Assessment
Verified in `StatsHandler` and UI dashboard rendering.

## Existing Test Assessment
Verified via `JNoSQLServerTest.healthEndpoint()`.

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
Added engine name badge rendering in header and overview card.

## Regression Tests Added
`JNoSQLServerTest.healthEndpoint()`

## Exact Test Commands
`mvn test -Dtest=EmbedJNoSQLServerTest`

## Test Output
Tests run: 9, Failures: 0, Errors: 0.

## Evidence Artifacts
`docs/audit/BASELINE-UI-RESULTS.md`

## Manual Reproduction Steps
1. Start database with `StorageEngine.B_TREE`.
2. Open console and verify badge shows `B_TREE`.

## Remaining Problems
None.

## Final Status
**PASS**

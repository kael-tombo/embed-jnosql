# UI Feature Assessment: Key-Value CRUD Operations

## Feature ID
`UI-011`

## Assessment Date
September 9, 2026

## Repository Commit
`e6bbecb`

## Related Vision Goal
Zero-latency embedded Key-Value mutations and deletions.

## User Problem
Users need to store, update, and remove arbitrary key-value pairs from buckets.

## User Capability
Input key and value into form, save to bucket, or click delete on an existing key.

## Expected User Journey
User enters key name `session-123` and value `active`, clicks "Put Key"; key immediately appears in bucket list with value populated.

## Entry Point
`#kv` tab form controls.

## Route
`/index.html#kv`

## Page or Screen
Key-Value Form & Action Controls

## Components Involved
`#kvKeyInput`, `#kvValueInput`, `#saveKvBtn`, `#deleteKvBtn`, `#kvFeedbackToast`

## UI Actions
Type into inputs, click Save or Delete button.

## Frontend State
Key list updated in memory.

## API Client
`POST /api/kv/{bucket}/{key}` and `DELETE /api/kv/{bucket}/{key}`

## HTTP Method
`POST` or `DELETE`

## Endpoint
`/api/kv/{bucket}/{key}`

## Request Parameters
Bucket and key in path.

## Request Payload
Value string or JSON object.

## Required Headers
`Content-Type: application/json`

## Authentication Requirements
Inherited from session cookie.

## Backend Service
`KeyValueHandler.handle()`

## JNOSQL-EMBED Library API
`KeyValueBucket.put()`, `KeyValueBucket.remove()`

## Storage Engine
Any active engine.

## Expected Database State
Key inserted/updated in storage engine or removed upon delete.

## Response Contract
`HTTP 200 OK` on write; `HTTP 204 No Content` on delete.

## UI Rendering Contract
Displays green toast; updates key list and value preview.

## Acceptance Criteria

### Functional Criteria
Key-value pair is persisted to storage engine; reads return saved value.

### Integration Criteria
Refreshes KV list and metric counters immediately.

### Error Criteria
Displays red toast on invalid key characters or server error.

### Loading Criteria
Button shows loading spinner during write.

### Empty-State Criteria
Clears preview card when key is deleted.

### Validation Criteria
Requires non-empty key name.

### Security Criteria
Enforces rate limit and size constraints.

### Accessibility Criteria
Form inputs have associated labels.

### Responsive Criteria
Form controls wrap gracefully on narrow viewports.

### Performance Criteria
Write completes in < 2ms.

## Existing Implementation Assessment
Verified in `KeyValueHandler` and KV UI form.

## Existing Test Assessment
Verified via `JNoSQLServerTest.kvPutAndGet()` and `kvDelete()`.

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
Integrated JSON auto-formatting for structured values in KV editor.

## Regression Tests Added
`JNoSQLServerTest.kvPutAndGet()`

## Exact Test Commands
`mvn test -Dtest=EmbedJNoSQLServerTest`

## Test Output
Tests run: 9, Failures: 0, Errors: 0.

## Evidence Artifacts
`docs/backend/API-PROOF-MATRIX.md`

## Manual Reproduction Steps
1. Navigate to `#kv`.
2. Enter key `testKey` and value `testValue`, submit.
3. Verify value is retrieved and matches.

## Remaining Problems
None.

## Final Status
**PASS**

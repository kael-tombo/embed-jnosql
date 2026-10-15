# EmbedJNoSQL Console User Stories

**As of:** 2026-10-15
**Source of truth:** [feature-inventory.md](feature-inventory.md) and the current implementation sources cited there. Stories describe the shipped behavior and separately call out gaps; they do not recast future design goals as implemented facts.

## How to read this catalog

- IDs `CONSOLE-001` through `CONSOLE-040` are unique and sequential. The inventory links each feature/panel/API family to its owning story.
- Every independently verifiable acceptance criterion is labelled `CONSOLE-###-AC##`; its prefix is the owning story ID. A test of an API endpoint does not count as a UI interaction test unless the criterion says so.
- Shared precondition for protected operations: the console is enabled and the actor has a valid session, bearer session, or configured API key. When CSRF is enabled, session-authenticated writes need the session's `X-CSRF-Token`; API-key auth bypasses CSRF. Specific exceptions (login/logout) are called out.
- Actor is an authenticated database administrator unless the story says “application integrator.” This is the configured role in the fixture, not evidence of role-based authorization granularity.
- Baseline coverage counts are in `baseline.md`: the selected console backend run passed 110 tests and exercised HTTP/static assertions; it did not run browser UI automation.

## Stories and acceptance criteria

### CONSOLE-001 — Sign in with configured credentials or API key

**Actor / preconditions / initial state:** Administrator; console enabled; sign-in page open; no usable session. Username/password or API key is configured server-side.
**Main scenario / UI and API effect:** Enter credentials and submit. The page posts JSON to `/api/auth/login`; on success it stores returned session and CSRF tokens in session storage, stores a supplied API key in local storage, then returns to a safe `next` path or the console. The handler creates a session cookie and CSRF token.
**Alternatives and recovery:** Empty password and key are rejected locally without a request. Invalid credentials show an alert. A 429 displays a countdown using `Retry-After` where numeric; editing fields clears the old error. A network failure displays an unreachable-server message and does not redirect. Correct credentials or wait for lockout expiry and retry.
**Evidence / tests:** Source: `login.html`, `login.js`, server `AuthLoginHandler`; HTTP evidence: `ConsoleFeatureValidationTest` labels 2–4 and `SecurityEnforcementTest`. Fixture has test credentials but values are intentionally omitted. Browser interaction remains unverified.
**Acceptance criteria:**
- **CONSOLE-001-AC01:** Given a valid configured username/password or API key, when the actor submits, then the browser receives an authenticated session and returns to the safe requested destination or default console.
- **CONSOLE-001-AC02:** Given both password and key are empty, when submitted, then no login request is made and a visible, announced prompt focuses the password input.
- **CONSOLE-001-AC03:** Given invalid credentials, rate lockout, or network failure, when login is attempted, then each state is distinguishable and the actor can correct credentials, wait, or retry without a false success redirect.

### CONSOLE-002 — Enforce session authentication and CSRF

**Actor / preconditions / initial state:** Unauthenticated visitor or authenticated administrator; protected API route available; server auth/CSRF configuration determines enforcement.
**Main scenario / UI and API effect:** Protected handlers reject missing/expired credentials with 401. With CSRF enabled, session-authenticated mutations require a valid `X-CSRF-Token` (403 otherwise); safe `GET`/`HEAD`/`OPTIONS` requests and requests authenticated with a configured API key bypass the CSRF check. The console API helper adds stored auth/CSRF headers.
**Alternatives and recovery:** 401 from a non-auth request stops pollers and redirects once to sign-in with the current path/hash; 403 is an operation error; sign in again to resume.
**Evidence / tests:** `EmbedJNoSQLServer.isAuthValid/isCsrfValid`; `ConsoleFeatureValidationTest` labels 2–4 and `SecurityEnforcementTest` verify HTTP rejection/authentication and session mutation CSRF handling. `SecurityEnforcementTest` does not cover the safe-method exemption branches (`GET`/`HEAD`/`OPTIONS`) or configured API-key CSRF bypass; browser wiring is also untested.
**Acceptance criteria:**
- **CONSOLE-002-AC01:** Given auth is enabled and credentials are absent/invalid, when a protected endpoint is requested, then it returns 401 and does not disclose protected data.
- **CONSOLE-002-AC02:** Given CSRF is enabled and the actor uses a session cookie, when a mutating request has a missing/invalid token, then it returns 403 without performing the mutation.
- **CONSOLE-002-AC03:** Given CSRF is enabled, when an authenticated request uses `GET`, `HEAD`, or `OPTIONS`, then it is not rejected for a missing CSRF token; when it uses a configured API key for a mutation, then the CSRF check is bypassed without exposing or logging the key in the test evidence.
- **CONSOLE-002-AC04:** Given a protected UI request returns 401, when the session expires, then the user is redirected once to sign-in with a safe deep link and polling is stopped.

### CONSOLE-003 — Sign out and recover an expired session

**Actor / preconditions / initial state:** Signed-in administrator at any console panel.
**Main scenario / UI and API effect:** Select Sign out. Browser makes best-effort `POST /api/auth/logout`, clears local API key/session/CSRF storage, and navigates to sign-in. Server removes the session identified by cookie and clears that cookie. Expiry is handled by story 002.
**Alternatives and recovery:** If logout request fails, client still clears its storage and navigates; server-side revocation is not assured unless request reached handler with session cookie. Sign in again if a protected request is rejected.
**Evidence / tests:** `ConsoleFeatureValidationTest` label 16 checks HTTP session invalidation; source implements button flow. Browser redirect/storage clearing not tested.
**Acceptance criteria:**
- **CONSOLE-003-AC01:** Given an active cookie session, when Sign out is selected, then the console attempts logout, clears browser-stored credentials/tokens, and returns to sign-in.
- **CONSOLE-003-AC02:** Given server logout completed, when the old session is reused against a protected endpoint, then it is rejected.
- **CONSOLE-003-AC03:** Given logout transport fails, when the client continues, then it does not present the browser as signed in and does not claim server revocation was confirmed.

### CONSOLE-004 — Serve console assets and deep-link targets

**Actor / preconditions / initial state:** Visitor opens the configured console URL or a deep link.
**Main scenario / UI and API effect:** Static handler serves packaged index/login/CSS/JS/SVG files; `/` resolves to the index. A login `next` parameter is accepted only when it is a local absolute path and not protocol-relative.
**Alternatives and recovery:** Missing packaged resource returns 404; unsafe/missing next destination falls back to the console index. No custom missing-asset recovery UI is established.
**Evidence / tests:** `ConsoleFeatureValidationTest` label 5 verifies HTTP asset serving and removed assets return 404; no browser render assertion.
**Acceptance criteria:**
- **CONSOLE-004-AC01:** Given the console is enabled, when its root/index/login and referenced assets are requested, then existing assets are served with the expected content type and absent assets return 404.
- **CONSOLE-004-AC02:** Given an unsafe or absent login return path, when sign-in succeeds, then navigation uses the default console rather than an external/protocol-relative destination.

### CONSOLE-005 — Navigate, filter, and deep-link to panels

**Actor / preconditions / initial state:** Signed-in administrator; console booted at any URL fragment.
**Main scenario / UI and API effect:** Select a sidebar button, filter nav entries, use panel shortcuts 1–9/0, or open a valid `#panel` fragment; browser hash/back-forward remains in sync. Overview cards provide Open links to Collections and Audit. No write API is called; panel entry invokes its read/refresh actions.
**Alternatives and recovery:** Invalid startup fragment opens Overview. Escape in nav search clears and blurs. Shortcuts are ignored while input/select/textarea is focused or a modifier key is active.
**Evidence / tests:** `PANELS` and `GROUPS` source list 12 panels; there is no browser navigation test in baseline.
**Acceptance criteria:**
- **CONSOLE-005-AC01:** Given any of the 12 registered panel IDs, when selected by button, supported shortcut, or valid fragment, then the matching section becomes active and the URL reflects it.
- **CONSOLE-005-AC02:** Given a filter query, when typed, then only matching navigation entries and nonempty groups remain; Escape clears the filter.
- **CONSOLE-005-AC03:** Given browser Back/Forward changes a valid fragment, when the event fires, then the active panel follows without creating an invalid panel state.

### CONSOLE-006 — Switch and retain theme

**Actor / preconditions / initial state:** Visitor/administrator with absent, saved, invalid, or inaccessible local storage.
**Main scenario / UI and API effect:** Console boot reads `embedjnosql.theme`; toggle changes root `data-theme` and saves the selected value. No API call.
**Alternatives and recovery:** Storage exceptions are caught and page can continue; invalid stored values are not validated. Current source default is **dark**, and sign-in page uses independent hard-coded dark styles; these are observed gaps against the approved light-first design, not future behavior. The required fallback is light for absent, invalid, or unreadable storage.
**Evidence / tests:** Source only; no theme browser test in baseline. Fallback-to-light and preference parity across sign-in/console are not implemented or tested.
**Acceptance criteria:**
- **CONSOLE-006-AC01:** Given local storage contains a supported theme, when either sign-in or the console loads, then the same saved theme is applied; when toggled in either page, then the selection is saved and remains consistent across reloads and navigation between sign-in and console.
- **CONSOLE-006-AC02:** Given the saved preference is absent, invalid, or unreadable because storage access fails, when sign-in or the console loads, then both render in light theme without an uncaught failure; current source defaults to dark and does not meet this criterion.
- **CONSOLE-006-AC03:** Given either theme, when all panels, dialogs, focus, disabled, error, empty and table states are inspected, then text and controls remain readable at desktop/mobile widths. This requires browser verification.

### CONSOLE-007 — Refresh active data and recover connectivity

**Actor / preconditions / initial state:** Signed-in administrator; active panel and network status may change.
**Main scenario / UI and API effect:** Click Refresh all to refresh active panel and status; health polls every 10 seconds, Overview every 5 seconds. Browser `offline` updates health chip/toast; `online` announces connection restoration and refreshes active panel/status. Each API call has a 20-second client timeout.
**Alternatives and recovery:** Network/backend errors map to explicit `ApiError` states, though periodic polling often catches errors. Some panels show a toast, some error content, some suppress errors. Button title advertises R, but inspected key binding does not implement R.
**Evidence / tests:** `/health` and metrics HTTP tests in label 6; no browser/offline test.
**Acceptance criteria:**
- **CONSOLE-007-AC01:** Given a panel is active, when Refresh all is activated, then the panel's read operation and status refresh run and communicate failure rather than silently claiming success.
- **CONSOLE-007-AC02:** Given connectivity is lost and restored, when offline/online events occur, then the health indicator changes accordingly and an online event triggers refresh without a full-page reload.
- **CONSOLE-007-AC03:** Given a request exceeds its configured timeout, when it aborts, then timeout is distinguishable from a network failure and a potentially applied write is not blindly retried.
- **CONSOLE-007-AC04:** Given the UI title promises an R shortcut, when keyboard verification runs, then the shortcut either works outside editable controls or the title is corrected; current binding is an explicit gap.

### CONSOLE-008 — Open and dismiss Help

**Actor / preconditions / initial state:** Signed-in administrator; console version health chip may or may not be populated.
**Main scenario / UI and API effect:** Open `?` dialog to read shortcuts, models, safety boundary, docs reference and version; close by button, Escape, or clicking the backdrop. No request is made by opening Help.
**Alternatives and recovery:** Unknown version displays “unknown”; button, Escape, or backdrop click dismisses the dialog.
**Evidence / tests:** Dialog markup and JS only; no UI test.
**Acceptance criteria:**
- **CONSOLE-008-AC01:** Given Help is closed, when opened, then a labelled modal appears and focus moves into it.
- **CONSOLE-008-AC02:** Given Help is open, when Close or Escape is activated, then it closes and focus returns to the Help trigger.
- **CONSOLE-008-AC03:** Given keyboard-only use, when focus moves through Help, then it must not escape into obscured page content; source currently has no focus trap, so this criterion is not verified/met.

### CONSOLE-009 — Review and cancel destructive confirmations

**Actor / preconditions / initial state:** Administrator invoked one of the confirmed destructive actions.
**Main scenario / UI and API effect:** Confirmation dialog shows target, impact, undo/reversibility message and optional hint before the caller sends a write.
**Alternatives and recovery:** Cancel button, Escape, or backdrop click resolves false; callers display cancellation and do not issue the write. Accept continues to the action-specific handler.
**Evidence / tests:** `confirmAction()` and modal markup; no browser test.
**Acceptance criteria:**
- **CONSOLE-009-AC01:** Given a destructive action, when confirmation opens, then target and impact are named and no engine mutation has yet been issued.
- **CONSOLE-009-AC02:** Given the actor cancels with Cancel, Escape, or backdrop, when the dialog closes, then no mutation is issued and focus returns to the invoker.
- **CONSOLE-009-AC03:** Given the actor accepts, when the dialog closes, then only the selected action proceeds; keyboard/screen-reader interaction remains a verification gap because there is no focus trap.

### CONSOLE-010 — Inspect Overview health, metrics, and recent activity

**Actor / preconditions / initial state:** Signed-in administrator on Overview.
**Main scenario / UI and API effect:** Overview displays health/engine metadata, counters, throughput sparkline, collections, and up to eight recent audit entries using health/metrics/collections/audit reads.
**Alternatives and recovery:** Empty collections/audit have explicit text. Audit request errors are converted to empty events, so the UI cannot distinguish empty from unavailable; investigate.
**Evidence / tests:** Label 6 verifies health/metrics data by HTTP; `ConsoleTaskSuccessTest` may assert static affordances. Not a rendered Overview test.
**Acceptance criteria:**
- **CONSOLE-010-AC01:** Given valid engine reads, when Overview loads/refreshes, then health, engine, uptime, counters, collection counts and recent audit data match API results.
- **CONSOLE-010-AC02:** Given there are no collections or audit events, when Overview renders, then it presents explicit empty-state text.
- **CONSOLE-010-AC03:** Given one read fails, when remaining reads succeed, then the UI distinguishes unavailable from empty; current audit fallback conflates them and is not verified.

### CONSOLE-011 — Inspect storage and WAL status

**Actor / preconditions / initial state:** Signed-in administrator; storage-status endpoint available; Overview card initially shows loading.
**Main scenario / UI and API effect:** Click refresh or enter Overview to read `/api/storage/status` and display storage/WAL status.
**Alternatives and recovery:** Failure reports a toast; unsupported engine/WAL status rendering is not established by the fixture. Retry when service recovers.
**Evidence / tests:** `StorageStatusHandler`, `ConsoleWorkspaceEndpointsTest`/`ConsoleTaskSuccessTest` where selected; no browser card test.
**Acceptance criteria:**
- **CONSOLE-011-AC01:** Given storage status is available, when card loads or Refresh is selected, then current engine/storage/WAL values are displayed.
- **CONSOLE-011-AC02:** Given endpoint failure or unsupported data, when refresh completes, then the card communicates unavailable/error distinctly from a valid empty value.

### CONSOLE-012 — List collections and select a collection

**Actor / preconditions / initial state:** Signed-in administrator at Collections; collection may be unknown, known, empty, or populated.
**Main scenario / UI and API effect:** Load/Refresh typed name, select a live collection chip, or refresh picker. Reads collection list and docs from `/api/collections`.
**Alternatives and recovery:** Empty name gives toast on explicit load; initial empty selector has guidance; picker failure says unavailable; empty docs render table empty state. Retry after service recovery.
**Evidence / tests:** Label 7 and `ConsoleCollectionResolutionTest` (HTTP); fixture includes sample collections. No UI test.
**Acceptance criteria:**
- **CONSOLE-012-AC01:** Given a known collection, when selected/loaded, then row count and document rows correspond to the engine.
- **CONSOLE-012-AC02:** Given no collections or an empty collection, when loaded, then the UI distinguishes “no collection loaded” from “loaded collection contains no documents.”
- **CONSOLE-012-AC03:** Given a collection is created after picker load, when picker refresh is activated, then its chip becomes selectable.

### CONSOLE-013 — Insert or replace a document

**Actor / preconditions / initial state:** Signed-in administrator; collection name and valid document JSON with engine-accepted identity.
**Main scenario / UI and API effect:** Insert sends `POST /api/collections/{collection}`. Source labels operation “Insert” but handler/engine may replace by ID; verify semantics against engine. Success updates status/toast and refreshes matching collection and picker.
**Alternatives and recovery:** Missing name/invalid JSON is client-rejected; request failure displays status/toast and re-enables button. Correct input/retry after reload.
**Evidence / tests:** Label 7 HTTP CRUD and endpoint tests; UI not verified.
**Acceptance criteria:**
- **CONSOLE-013-AC01:** Given valid JSON and collection, when Insert is selected, then one engine write occurs and the saved document is visible after reload.
- **CONSOLE-013-AC02:** Given malformed JSON or missing collection name, when submitted, then no API write is sent and a useful validation message appears.
- **CONSOLE-013-AC03:** Given server failure, when response is rendered, then controls are usable again and the UI does not claim success.

### CONSOLE-014 — Inspect, copy, and edit a document

**Actor / preconditions / initial state:** Signed-in administrator; a document is loaded.
**Main scenario / UI and API effect:** Select a document ID to show JSON or expandable tree; switch mode; Copy JSON to clipboard; Load into editor to prefill collection/document JSON. Viewing does not mutate engine.
**Alternatives and recovery:** Missing document displays not found. Clipboard failure is not caught in source; retry/permission recovery is unverified.
**Evidence / tests:** Source only; backend GET coverage in label 7 does not verify renderer/clipboard.
**Acceptance criteria:**
- **CONSOLE-014-AC01:** Given a loaded nested document, when JSON/Tree is selected, then equivalent values remain visible and `aria-pressed` reflects selected view.
- **CONSOLE-014-AC02:** Given clipboard permission, when Copy JSON is selected, then the selected document's JSON is copied and success is announced.
- **CONSOLE-014-AC03:** Given Load into editor is selected, then the existing insert form is populated without making an engine write.

### CONSOLE-015 — Delete one document

**Actor / preconditions / initial state:** Signed-in administrator; document detail visible.
**Main scenario / UI and API effect:** Confirm target, then DELETE the document resource; list reloads and detail clears.
**Alternatives and recovery:** Cancel sends no DELETE. Failure toast retains/reloads state; confirm data before retry if timeout.
**Evidence / tests:** Label 7 verifies HTTP delete; not browser confirmation.
**Acceptance criteria:**
- **CONSOLE-015-AC01:** Given a selected document, when Delete is accepted, then only that document is removed and the refreshed collection omits it.
- **CONSOLE-015-AC02:** Given cancellation, then no delete request is sent.
- **CONSOLE-015-AC03:** Given failure/timeout, then UI communicates uncertain outcome as appropriate and permits refresh before retry.

### CONSOLE-016 — Clean expired documents

**Actor / preconditions / initial state:** Signed-in administrator; collection name loaded.
**Main scenario / UI and API effect:** Click Clean expired to `POST /api/collections/{collection}/cleanup`; show deleted count and refresh collection/picker.
**Alternatives and recovery:** Missing collection prevents request; no-expired result is reported as zero removed. Endpoint error reaches toast.
**Evidence / tests:** Source and workspace/collection endpoint tests where applicable; no `ConsoleFeatureValidationTest` label dedicated to TTL cleanup.
**Acceptance criteria:**
- **CONSOLE-016-AC01:** Given expired documents, when cleanup succeeds, then only expired entries are removed and count is reported.
- **CONSOLE-016-AC02:** Given no collection or failed request, then no success is claimed and recovery is possible.

### CONSOLE-017 — Delete all documents in a collection

**Actor / preconditions / initial state:** Signed-in administrator; collection loaded and nonempty.
**Main scenario / UI and API effect:** UI reads docs to count, names collection/count and impact in confirmation, then serially DELETEs each document, rereads remainder and refreshes picker/list. The collection itself remains.
**Alternatives and recovery:** Empty collection reports no changes; cancellation sends no deletes; partial failures show deleted/failed counts and first error. After timeout/partial failure, reload before retry.
**Evidence / tests:** HTTP CRUD evidence exists, but no UI loop/partial-failure test.
**Acceptance criteria:**
- **CONSOLE-017-AC01:** Given a nonempty collection, when confirmation is accepted, then each currently listed document is attempted for deletion while the collection remains.
- **CONSOLE-017-AC02:** Given zero documents or cancel, then no document deletion is sent.
- **CONSOLE-017-AC03:** Given some deletes fail, then reported deleted/failed/remaining counts reflect re-read state rather than an unqualified success.

### CONSOLE-018 — Build, run, and page a document query

**Actor / preconditions / initial state:** Signed-in administrator; a collection is loaded.
**Main scenario / UI and API effect:** Build filter conditions with supported operators, set optional sort/limit/offset, inspect preview, run `POST /api/collections/{collection}/query`, render rows and pager. Prev/Next adjusts offset; Clear resets builder/output.
**Alternatives and recovery:** No collection shows instruction; no matches suggests relaxing filter; rejected request shows failure badge/banner. Limit+1 probe estimates has-more; displayed total is only rows seen so far.
**Evidence / tests:** Label 8, `ConsoleQueryEndpointTest` verify HTTP query semantics, not UI controls/paging.
**Acceptance criteria:**
- **CONSOLE-018-AC01:** Given supported filter inputs, when preview changes, then it matches the submitted filter/sort/limit/offset payload.
- **CONSOLE-018-AC02:** Given a matching query, when Run filter is selected, then results and match count reflect engine response.
- **CONSOLE-018-AC03:** Given no matches or invalid query, then empty/rejected outcomes are distinct and actionable.
- **CONSOLE-018-AC04:** Given multiple pages, when Next/Prev is available and selected, then offset changes by limit and disabled states match page boundaries.

### CONSOLE-019 — Browse simple KV keys and write with TTL

**Actor / preconditions / initial state:** Signed-in administrator; bucket chosen or entered.
**Main scenario / UI and API effect:** Browse keys via KV metadata routes with optional prefix, fetch values, and use inline New key to save a value plus optional TTL seconds. Picker enumerates buckets.
**Alternatives and recovery:** Empty bucket/prefix has guidance; loading shown; list/value errors become banner or “unreadable”; first 200 keys shown with prefix narrowing guidance. Empty key name is blocked. Invalid TTL handling belongs to server.
**Evidence / tests:** `ConsoleFeatureValidationTest` label 9 tests `/api/kv` CRUD, not this `/api/kv-meta` UI. Workspace endpoint suite may cover route separately. Fixture seeds simple KV.
**Acceptance criteria:**
- **CONSOLE-019-AC01:** Given a bucket and optional prefix, when Browse is selected, then matching keys and available values/TTL/expired indicators are listed.
- **CONSOLE-019-AC02:** Given a new key and valid value/optional TTL, when Save succeeds, then the key appears after refresh with value and expiry metadata.
- **CONSOLE-019-AC03:** Given an empty bucket/prefix or more than 200 keys, then the UI communicates empty state or truncation and how to narrow results.
- **CONSOLE-019-AC04:** Given a metadata read failure, then the UI distinguishes unavailable from a valid empty bucket.

### CONSOLE-020 — Delete a KV browser key

**Actor / preconditions / initial state:** Signed-in administrator; key is listed in a selected bucket.
**Main scenario / UI and API effect:** Confirm target and impact; then DELETE `/api/kv-meta/{bucket}/{key}` and refresh.
**Alternatives and recovery:** Cancel makes no change; errors toast and preserve current view until refresh.
**Evidence / tests:** UI source only; no browser confirmation/deletion test in baseline.
**Acceptance criteria:**
- **CONSOLE-020-AC01:** Given confirmation accepted, then only the selected bucket/key is deleted and omitted on refresh.
- **CONSOLE-020-AC02:** Given cancel, then no delete request occurs.

### CONSOLE-021 — Operate a list bucket

**Actor / preconditions / initial state:** Signed-in administrator; Lists tab selected.
**Main scenario / UI and API effect:** Push comma-separated values with RPUSH, pop with RPOP, or inspect LRANGE through `/api/kv/lists/{bucket}/{key}/...`.
**Alternatives and recovery:** Missing bucket/key is locally rejected; endpoint errors show toast/error badge; empty list behavior is server-defined and must be verified.
**Evidence / tests:** Source and registered List handler; no dedicated label in `ConsoleFeatureValidationTest`.
**Acceptance criteria:**
- **CONSOLE-021-AC01:** Given bucket/key and values, when Push is selected, then values are sent in order and visible in a subsequent range.
- **CONSOLE-021-AC02:** Given a nonempty list, when Pop is selected, then returned tail value and remaining range match engine state.
- **CONSOLE-021-AC03:** Given an empty list or missing required input, then empty/error and validation outcomes are explicit.

### CONSOLE-022 — Operate a set bucket

**Actor / preconditions / initial state:** Signed-in administrator; Sets tab selected.
**Main scenario / UI and API effect:** Add/remove comma-separated members or read members through `/api/kv/sets/{bucket}/{key}/...`.
**Alternatives and recovery:** Missing bucket/key blocks locally; failures show error; duplicate/nonexistent membership semantics must be established by handler/engine test.
**Evidence / tests:** Source and Set handler registration; no feature-label test for set UI.
**Acceptance criteria:**
- **CONSOLE-022-AC01:** Given members, when Add is selected, then subsequent Members includes them.
- **CONSOLE-022-AC02:** Given existing members, when Remove is selected, then subsequent Members omits them.
- **CONSOLE-022-AC03:** Given an empty set, then the UI differentiates an empty response from request failure.

### CONSOLE-023 — Operate a hash bucket

**Actor / preconditions / initial state:** Signed-in administrator; Hashes tab selected.
**Main scenario / UI and API effect:** HSet field/value, HGet one field, or HGetAll through `/api/kv/hashes/{bucket}/{key}/...`.
**Alternatives and recovery:** Bucket/key and relevant field required; server errors are shown; missing field behavior needs endpoint verification.
**Evidence / tests:** Source and Hash handler registration; no specific feature-label test.
**Acceptance criteria:**
- **CONSOLE-023-AC01:** Given bucket/key/field/value, when HSet succeeds, then HGet and HGetAll return the stored value.
- **CONSOLE-023-AC02:** Given a missing required field or missing key, then validation/not-found/empty semantics are not conflated.

### CONSOLE-024 — Read, write, and inspect a wide-column row

**Actor / preconditions / initial state:** Signed-in administrator; family and row key known.
**Main scenario / UI and API effect:** Fetch a row, query family stats, or Write valid JSON columns using `/api/columns/{family}/{row}` and `/stats`.
**Alternatives and recovery:** Missing family/row blocks write; malformed JSON is rejected locally; write button is disabled while saving, then row is refetched. Fetch error is text; stats error may be JSON.
**Evidence / tests:** Label 10 and column endpoint tests are HTTP evidence; fixture seeds telemetry family.
**Acceptance criteria:**
- **CONSOLE-024-AC01:** Given an existing row, when Fetch row is selected, then its columns are displayed.
- **CONSOLE-024-AC02:** Given valid JSON columns and row key, when Write succeeds, then a subsequent fetch returns the written values.
- **CONSOLE-024-AC03:** Given invalid JSON or incomplete identifiers, then no write is made and a validation error appears.
- **CONSOLE-024-AC04:** Given no row, then the result is an explicit empty/not-found state distinct from backend failure.

### CONSOLE-025 — Inspect vector index status (experimental)

**Actor / preconditions / initial state:** Signed-in administrator; experimental vector panel; index name defaults to `embeddings`.
**Main scenario / UI and API effect:** Info queries `/api/vectors/{index}/_` and displays vector count/dimensions. Source and UI explicitly mark vectors experimental and API may change.
**Alternatives and recovery:** Empty index 404 is rendered as `0 vecs (empty)`; other errors become warning status.
**Evidence / tests:** Label 17 and `ConsoleVectorSearchTest` are HTTP/engine evidence; no browser interaction test.
**Acceptance criteria:**
- **CONSOLE-025-AC01:** Given an existing index, when Info is selected, then count/dimension values match the handler response and the panel remains labelled experimental.
- **CONSOLE-025-AC02:** Given a not-yet-created index, then the UI reports an empty state rather than implying index creation or a successful search.

### CONSOLE-026 — Insert an experimental vector

**Actor / preconditions / initial state:** Signed-in administrator; experimental index, vector ID and nonempty JSON array. The panel says fixed 128 dimensions, while current `VectorHandler` derives a new index's dimension from the first vector; the UI/source contract conflicts.
**Main scenario / UI and API effect:** Parse vector JSON and POST `/api/vectors/{index}/{id}`. The handler creates an index from the first vector's length when needed (the UI sends only `{vector}`). For an existing index, a different vector length fails in `HNSWIndex.add` and the handler returns HTTP 400 with `Insert failed`.
**Alternatives and recovery:** Missing inputs, invalid JSON, nonarray, or empty array are blocked locally. Save shows progress then success/error. The outcome of inserting a duplicate ID is not asserted: record the response status/body and inspect the resulting index/search state; do not assume replacement or preservation semantics.
**Evidence / tests:** Label 17/`ConsoleVectorSearchTest` covers HTTP vector search errors, not duplicate-ID insertion. Source implements vector insertion but the feature remains experimental; UI unverified.
**Acceptance criteria:**
- **CONSOLE-026-AC01:** Given an engine-accepted vector, when inserted, then the ID becomes retrievable/searchable by the vector handler.
- **CONSOLE-026-AC02:** Given invalid JSON/non-array/empty array, then no request is sent and validation is visible.
- **CONSOLE-026-AC03:** Given a new index and a vector of length N, when the UI inserts it, then the index is created with N dimensions; given an existing index of N dimensions and an inserted vector of a different length, then the handler returns HTTP 400 with `Insert failed`. Given a duplicate ID, record the complete response status/body and inspect post-state (index info and search results) without asserting whether the existing vector was replaced or preserved; duplicate-ID behavior is unverified by current tests.

### CONSOLE-027 — Search experimental vectors

**Actor / preconditions / initial state:** Signed-in administrator; experimental index populated with searchable vectors; query array and positive `k`. The panel copy claims fixed 128 dimensions; handler source uses the index's established dimension.
**Main scenario / UI and API effect:** Search sends query to `/api/vectors/{index}/search` and renders nearest-neighbor results.
**Alternatives and recovery:** Empty index renders “No results”; unknown index returns 404; handler returns 400 for missing/non-array vector, dimension mismatch, or nonpositive `k`. The client parses JSON but coerces a falsey `k` to 5 and displays other request errors. Reconcile dimension copy with handler.
**Evidence / tests:** Label 17/`ConsoleVectorSearchTest` verify backend capability only; no UI flow test. Feature remains experimental.
**Acceptance criteria:**
- **CONSOLE-027-AC01:** Given a valid query and populated index, when Search completes, then ranked results correspond to server response and requested `k`.
- **CONSOLE-027-AC02:** Given empty index, malformed input, or API failure, then the UI shows distinct empty, validation, and error states and permits correction/retry.

### CONSOLE-028 — Register and inspect a document schema

**Actor / preconditions / initial state:** Signed-in administrator; collection and fields JSON; optional strict mode.
**Main scenario / UI and API effect:** Refresh schemas via GET `/api/schema`; Register posts schema/strict setting to `/api/schema/{collection}`. Schema is described as insert-time validation.
**Alternatives and recovery:** Initial loading; malformed schema/unsupported field type/duplicate registration outcome depends on handler and engine; failures toast.
**Evidence / tests:** Label 11 and schema endpoint tests are HTTP-level; `ConsoleComprehensiveFeatureProofTest.step15_schemaValidation` records schema validation, but does not establish the strict extra-field comparison through the console endpoint; fixture not proof of UI.
**Acceptance criteria:**
- **CONSOLE-028-AC01:** Given a valid schema, when registered, then a subsequent GET returns its field definitions and the selected `strict` value; this verifies persistence/reporting only, not the strict flag's effect on inserts.
- **CONSOLE-028-AC02:** Given invalid schema input, then registration is rejected with an actionable error and list remains truthful.
- **CONSOLE-028-AC03:** **Deferred verification (bounded):** Register otherwise identical schemas for two otherwise equivalent collections, one with `strict:true` and one with `strict:false`; for each, submit the same valid document containing all declared fields plus one undeclared field through the document-insert API. Capture each response status/body and inspect each collection afterward. Compare whether the strict collection rejects the extra field while the non-strict collection accepts it; do not claim the engine effect until this exact comparison is run. The source validator's strict branch rejects unknown fields, but current endpoint tests do not establish that insert enforcement path.

### CONSOLE-029 — Begin and inspect transaction lifecycle

**Actor / preconditions / initial state:** Signed-in administrator; engine transaction support available.
**Main scenario / UI and API effect:** List active transactions with GET `/api/transactions`; Begin posts `action: begin`, then list refreshes.
**Alternatives and recovery:** No active items shows empty table state; unsupported/backend errors appear as toast. Source explicitly warns console CRUD/bulk/KV writes do not participate in these UI-started transactions; Java API is required to transact writes.
**Evidence / tests:** Label 12 and `ConsoleTransactionAndBulkTest` are HTTP coverage; list/begin UI unverified.
**Acceptance criteria:**
- **CONSOLE-029-AC01:** Given transaction support, when Begin succeeds, then a new transaction ID/status appears in the active table.
- **CONSOLE-029-AC02:** Given none are active, then the panel reports an empty state.
- **CONSOLE-029-AC03:** Given a UI-started transaction, when a separate console CRUD write is made, then UI does not claim that write is part of the transaction.

### CONSOLE-030 — Commit or roll back a transaction

**Actor / preconditions / initial state:** Signed-in administrator; active transaction row with identifier.
**Main scenario / UI and API effect:** Select Commit or Rollback; POST action and transaction ID to `/api/transactions`; success toast and refresh. No confirmation prompt is implemented.
**Alternatives and recovery:** Failure toast; list may remain stale until manually refreshed. Invalid/finished ID outcome comes from handler.
**Evidence / tests:** `ConsoleTransactionAndBulkTest` and label 12 provide server evidence; no UI finalize test.
**Acceptance criteria:**
- **CONSOLE-030-AC01:** Given an active transaction, when Commit is selected, then server receives its ID and subsequent list no longer reports it active.
- **CONSOLE-030-AC02:** Given an active transaction, when Rollback is selected, then server receives its ID and subsequent list no longer reports it active.
- **CONSOLE-030-AC03:** Given an invalid/finished ID or request failure, then failure is visible and actor can refresh before retrying.

### CONSOLE-031 — Inspect and create field indexes

**Actor / preconditions / initial state:** Signed-in administrator; existing collection and field name.
**Main scenario / UI and API effect:** List indexes with GET `/api/indexes/{collection}`; Create posts field to same resource and reloads listing.
**Alternatives and recovery:** No entries show “No indexes”; missing input blocks create; nonexisting collection is not auto-created (route test documents 404); errors toast.
**Evidence / tests:** Label 13, `ConsoleIndexRouteExistenceTest`, `ConsoleIndexAndCdcEndpointTest` HTTP; no browser test.
**Acceptance criteria:**
- **CONSOLE-031-AC01:** Given a real collection, when List is selected, then index fields/type/counts reflect engine state.
- **CONSOLE-031-AC02:** Given valid field and collection, when Create index succeeds, then it appears on refresh.
- **CONSOLE-031-AC03:** Given missing field or nonexistent collection, then no misleading success or implicit collection creation occurs.

### CONSOLE-032 — Inspect and create backups

**Actor / preconditions / initial state:** Signed-in administrator; backup handler configured.
**Main scenario / UI and API effect:** Refresh GET `/api/backup` for status, then Create backup POSTs `/api/backup` and reports file, document count and collection count.
**Alternatives and recovery:** Empty database is a valid zero-document backup and gets a warning distinct from error. Request failure produces error toast. Fixture is in-memory; it does not establish production durability.
**Evidence / tests:** Label 14, `ConsoleBackupEndpointTest` HTTP.
**Acceptance criteria:**
- **CONSOLE-032-AC01:** Given a populated database, when backup is created, then returned snapshot details/count are displayed and status refreshes.
- **CONSOLE-032-AC02:** Given an empty database, then successful zero-document backup is not presented as populated.
- **CONSOLE-032-AC03:** Given backup failure, then no success state is shown and actor can refresh/retry after checking service state.

### CONSOLE-033 — Restore a backup after confirmation

**Actor / preconditions / initial state:** Signed-in administrator; path to valid backup file; understand current data may be replaced.
**Main scenario / UI and API effect:** Confirmation describes whole-database impact; accepted action POSTs `/api/backup/restore`; success refreshes health/backup status.
**Alternatives and recovery:** Empty path rejected; cancellation sends no request; failure shows badge/error banner. Timeout leaves outcome uncertain; reload/reconcile before retry.
**Evidence / tests:** Label 14 / `ConsoleBackupEndpointTest` verify server endpoint, not UI warning/restore path validation.
**Acceptance criteria:**
- **CONSOLE-033-AC01:** Given a valid snapshot and accepted warning, when restore succeeds, then restored engine state and file are reported after refresh.
- **CONSOLE-033-AC02:** Given cancellation or missing path, then no restore request occurs.
- **CONSOLE-033-AC03:** Given invalid path, permission failure, server error or timeout, then the actor sees failure/uncertainty and is directed to verify state before retry.

### CONSOLE-034 — Inspect and toggle CDC

**Actor / preconditions / initial state:** Signed-in administrator; CDC support is enabled/configured by engine.
**Main scenario / UI and API effect:** GET `/api/cdc` shows enabled state, subscribers, event count, file/Kafka connectors. Toggle reads current state then POSTs enable/disable and refreshes.
**Alternatives and recovery:** Toggle is disabled while calls are in flight, restored in `finally`; failures toast. No confirmation.
**Evidence / tests:** `ConsoleIndexAndCdcEndpointTest` and source; HTTP, not UI.
**Acceptance criteria:**
- **CONSOLE-034-AC01:** Given CDC state, when panel refreshes, then enabled/disabled and counts match handler state.
- **CONSOLE-034-AC02:** Given enabled or disabled CDC, when Toggle succeeds, then the opposite state is displayed after refresh.
- **CONSOLE-034-AC03:** Given mutation failure, then control re-enables and error is visible without asserting a state change.

### CONSOLE-035 — Manage CDC connectors

**Actor / preconditions / initial state:** Signed-in administrator; connector type selected (`file` or `kafka`), name and type-specific parameter supplied.
**Main scenario / UI and API effect:** Connect POSTs connector configuration to `/api/cdc/connectors/{name}`; Remove DELETEs that resource; successful changes refresh list.
**Alternatives and recovery:** Empty name blocked; Connect disabled in flight then restored; server errors toast. Remove sends DELETE without a confirmation dialog; parameter validation/connection success is not established by UI code. Lack of confirmation is a UX gap, not an unresolved behavior decision.
**Evidence / tests:** `ConsoleIndexAndCdcEndpointTest` covers connector lifecycle and unknown-connector DELETE failure; fixture does not prove external Kafka availability; UI unverified.
**Acceptance criteria:**
- **CONSOLE-035-AC01:** Given valid file connector configuration, when Connect succeeds, then connector appears as file type.
- **CONSOLE-035-AC02:** Given valid Kafka configuration and reachable broker, when Connect succeeds, then connector appears as Kafka type; broker-unavailable recovery is verified separately.
- **CONSOLE-035-AC03:** Given a listed connector, when Remove is selected and DELETE succeeds, then the refreshed list omits it; given a failed DELETE (including an unknown connector), then the failure is visible and the list does not claim successful removal. Current UI issues DELETE without confirmation; treat this as a separately documented UX gap, not a blocker to verifying the current action.
- **CONSOLE-035-AC04:** Given missing/invalid parameters or endpoint failure, then no success is claimed and the actor can correct/retry.

### CONSOLE-036 — Load CDC events

**Actor / preconditions / initial state:** Signed-in administrator; event log may be empty or unavailable.
**Main scenario / UI and API effect:** Load events GETs `/api/cdc/events` and renders timestamp, type, collection and key.
**Alternatives and recovery:** Current code maps a failed request to empty events; therefore empty and unavailable are indistinguishable. Refresh after error is possible, but error diagnosis is a gap.
**Evidence / tests:** `ConsoleIndexAndCdcEndpointTest` HTTP and source; no browser test.
**Acceptance criteria:**
- **CONSOLE-036-AC01:** Given events exist, when Load events is selected, then returned events are displayed.
- **CONSOLE-036-AC02:** Given no events, then an explicit empty state is rendered.
- **CONSOLE-036-AC03:** Given endpoint failure, then error is distinguishable from no events; current source does not meet this criterion.

### CONSOLE-037 — Review the audit trail

**Actor / preconditions / initial state:** Signed-in administrator; audit log has bounded in-memory events or is empty.
**Main scenario / UI and API effect:** Audit Refresh GETs `/api/audit/logs` and displays time, operation, resource, document, status and client; Overview shows recent entries.
**Alternatives and recovery:** Empty log shows no-data; panel failure toast. Overview silently maps failure to empty. Log is bounded/in-memory according to UI text; persistence is unverified.
**Evidence / tests:** Label 15, `ConsoleFeatureValidationTest`, `ConsoleTaskSuccessTest` HTTP/static; no browser table test.
**Acceptance criteria:**
- **CONSOLE-037-AC01:** Given audit events, when refreshed, then displayed rows reflect returned event fields and order.
- **CONSOLE-037-AC02:** Given no events, then an explicit empty state appears.
- **CONSOLE-037-AC03:** Given audit endpoint error, then unavailable is not represented as an empty successful log; Overview fallback is a known gap.

### CONSOLE-038 — Inspect server health and JVM metrics

**Actor / preconditions / initial state:** Signed-in administrator on Server.
**Main scenario / UI and API effect:** Refresh requests `/api/health` and `/api/metrics` in parallel and renders JSON responses for health and metrics.
**Alternatives and recovery:** One endpoint error is rendered as JSON error while other result remains available; retry Refresh. No separate automatic interval on Server panel is established beyond topbar poll.
**Evidence / tests:** Label 6, `ConsoleFeatureValidationTest`, `ConsoleTaskSuccessTest` HTTP/static; UI unverified.
**Acceptance criteria:**
- **CONSOLE-038-AC01:** Given both endpoints respond, when Refresh is selected, then health and JVM metrics values are rendered.
- **CONSOLE-038-AC02:** Given either endpoint fails, then that failure is visible while the other endpoint result remains available.
- **CONSOLE-038-AC03:** Given large values/long JSON at mobile width, then content remains readable without document-wide horizontal overflow.

### CONSOLE-039 — Bind the console to an available port

**Actor / preconditions / initial state:** Application integrator enables console and configures preferred port/intelligent fallback.
**Main scenario / UI and API effect:** Server binds preferred port; on collision with intelligent selection enabled, PortManager tries alternate available ports and exposes actual port/URL to host app. No console UI interaction.
**Alternatives and recovery:** If bind attempts fail, startup fails; caller must select another port/configuration.
**Evidence / tests:** Label 1 `ConsoleFeatureValidationTest`, `PortManagementTest` (startup/HTTP); browser not required for this server configuration story.
**Acceptance criteria:**
- **CONSOLE-039-AC01:** Given preferred port is occupied and intelligent port selection enabled, when console starts, then it binds a different available port and reports that actual port/URL.
- **CONSOLE-039-AC02:** Given no port can be bound, then startup failure is explicit and no unreachable URL is reported as active.

### CONSOLE-040 — Use the console with keyboard and small viewports

**Actor / preconditions / initial state:** Visitor on sign-in or signed-in administrator using any panel; keyboard-only or narrow viewport, zoom and reduced motion.
**Main scenario / UI and API effect:** Navigate, open/close dialogs, edit/run panel controls and read status with keyboard/screen reader; resize layout without clipping essential actions. No engine call is the outcome.
**Alternatives and recovery:** If focus is lost or content overflows, user cannot reliably recover; exact supported breakpoints are not specified by the plan/source. Login has a reduced-motion rule; console behavior must be independently checked.
**Evidence / tests:** HTML/CSS provide partial semantics and responsive rules. No browser/a11y suite in baseline. The current evidence does not establish WCAG conformance or all-panel mobile usability.
**Acceptance criteria:**
- **CONSOLE-040-AC01:** Given keyboard-only use, then every control can be focused/activated, focus remains visible, dialogs do not strand focus, and status/errors are announced.
- **CONSOLE-040-AC02:** Given representative desktop/tablet/mobile widths and browser zoom, then no essential panel/control is clipped or hidden and no unintended page-wide horizontal scroll occurs.
- **CONSOLE-040-AC03:** Given reduced-motion preference, then motion is reduced consistently on sign-in and console.

## Coverage cross-check result

- The source `PANELS` registry has 12 IDs; all 12 have a matching `panel-*` section and are linked in the inventory cross-check table.
- The complete console UI action families and all parameterized API families in `console.js` are mapped to stories `CONSOLE-001`–`CONSOLE-038`; startup-only port allocation maps to `CONSOLE-039`; cross-cutting keyboard/responsive quality maps to `CONSOLE-040`.
- All `ConsoleFeatureValidationTest` feature labels 1–17 (with label 3 & 4 combined as in source) map to one or more stories and are explicitly separated from UI evidence in the inventory.
- Registered backend-only `/api/kv`, `/api/metrics/stream`, `/api/stats`, `/api/bulk`, and conditional `/api/cors` have no wired control/API call in inspected `console.js`; they are disclosed as backend routes, not falsely cataloged as UI affordances.
- Result: **no unexplained panel, wired UI action/API family, or feature-validation label is missing.** Remaining gaps are explicitly listed in the inventory and per-story criteria.

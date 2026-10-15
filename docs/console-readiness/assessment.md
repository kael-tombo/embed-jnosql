# EmbedJNoSQL Console Readiness Assessment

**Assessment date:** 2026-10-15
**Scope:** Findings from the committed [backend baseline](baseline.md), [feature inventory](feature-inventory.md), [user stories](user-stories.md), and cited repository sources. This is a prioritized evidence record, not a claim that all Console workflows have been exercised.

## Evidence boundary and severity

The focused Maven baseline reports **110 passed, 0 failed, 0 errors, 0 skipped** across 15 selected classes. Its evidence includes embedded-server HTTP tests and static asset assertions; it does **not** establish rendered-browser interactions, full-suite status, or production-engine persistence behavior. The inventory cross-check maps all 12 navigation panels, UI action/API families, and `ConsoleFeatureValidationTest` feature labels to `CONSOLE-001`–`CONSOLE-040`.

Severity is readiness priority, not an assertion of exploitability: **P1** blocks a core readiness claim or risks incorrect operation; **P2** is a material functional/accessibility/verification gap; **P3** is a lower-priority recommendation. “Observed” means directly established by source or recorded execution; “unverified” does not assert failure.

## Observed issues and gaps

### OBS-01 — Theme fallback does not meet the light-first story

- **Stories:** [CONSOLE-006](user-stories.md#console-006--switch-and-retain-theme), [CONSOLE-040](user-stories.md#console-040--use-the-console-with-keyboard-and-small-viewports)
- **Severity:** P1
- **Exact reproduction:** In a fresh browser profile, clear `localStorage` for the Console origin and load the console. `initTheme()` initializes to `dark` and uses `dark` again when the preference is absent or storage access throws. Load `/login.html`: its palette is independently hard-coded rather than applying the saved Console preference. The expected light fallback and sign-in/console parity in `CONSOLE-006-AC01/02` are therefore not met by the inspected source.
- **Root cause:** `src/main/resources/static/js/console.js` (`initTheme`, `setTheme`) defaults to `dark` and accepts an unvalidated stored value. `src/main/resources/static/login.html` defines its own dark palette in inline CSS (for example, fixed white text around lines 58–111); it does not read `embedjnosql.theme`.
- **Proposed fix:** Use one validated theme preference contract on login and console; apply light for absent, invalid, or inaccessible storage, and retain explicit user selection across both pages.
- **Verification:** Add browser tests for missing/invalid/unavailable storage, saved dark/light preference, toggling on either page, and navigation/reload parity; verify contrast and visible controls in both themes.
- **Status:** Open; source-confirmed mismatch, no browser verification in baseline.

### OBS-02 — Vector panel claims a fixed dimension that the handler does not enforce

- **Stories:** [CONSOLE-025](user-stories.md#console-025--inspect-vector-index-status-experimental), [CONSOLE-026](user-stories.md#console-026--insert-an-experimental-vector), [CONSOLE-027](user-stories.md#console-027--search-experimental-vectors)
- **Severity:** P2
- **Exact reproduction:** Open Vectors and read the fixed-128 copy in the panel. On an empty index, insert a non-empty vector of a different length (for example, three values) under a new index name, then select Info. The handler creates a new index from the first vector’s length; the returned dimensions can consequently be `3`, contradicting the displayed “fixed 128 dimensions” claim.
- **Root cause:** `src/main/resources/static/index.html` labels the feature and search input as fixed 128 dimensions (around lines 256 and 283). In contrast, `src/main/java/org/embeddedjnosql/db/console/http/EmbedJNoSQLServer.java` documents “client-derived dimensions” and derives `dims` from the submitted vector around lines 2748–2773.
- **Proposed fix:** Align the panel copy and input guidance with the actual variable-dimension contract, or deliberately enforce 128 in the API and test that contract. Keep the experimental notice.
- **Verification:** Browser-test insertion and search using both the chosen supported dimension and a mismatch; assert the UI copy, info dimensions, and HTTP error/success state agree.
- **Status:** Open; source-contract mismatch confirmed; browser flow unverified.

### OBS-03 — CDC event-load failures are rendered as an empty event list

- **Stories:** [CONSOLE-036](user-stories.md#console-036--load-cdc-events)
- **Severity:** P2
- **Exact reproduction:** Open CDC, make `GET /api/cdc/events` fail (for example, block that request in browser DevTools), and click **Load events**. The failure is converted to `{ events: [] }`, so the table is rendered exactly as it is for a successful empty response.
- **Root cause:** `src/main/resources/static/js/console.js`, `cdcEvents()` around lines 1478–1483, catches every `api('/cdc/events')` error and substitutes an empty event array before rendering. The handler’s successful empty response also has an empty `events` list.
- **Proposed fix:** Preserve and render the request error separately from a successful empty result; offer retry without discarding the distinction.
- **Verification:** Browser/API test one successful empty response and one failed response; assert different visible, announced states and a functioning retry.
- **Status:** Open; source behavior confirmed; rendered-state test absent.

### OBS-04 — Refresh control advertises an R shortcut that is not wired

- **Stories:** [CONSOLE-007](user-stories.md#console-007--refresh-active-data-and-recover-connectivity), [CONSOLE-040](user-stories.md#console-040--use-the-console-with-keyboard-and-small-viewports)
- **Severity:** P3
- **Exact reproduction:** With the page body focused (not an input, textarea, or select), press `R`. The topbar Refresh button’s title says “Refresh data (R)” (`src/main/resources/static/index.html`, around line 57), but the global key handler only matches panel keys from `PANELS` and calls `goto`; it has no refresh branch (`src/main/resources/static/js/console.js`, around lines 1735–1743).
- **Root cause:** The advertised shortcut is absent from the keydown handler.
- **Proposed fix:** Either implement and document `R` refresh outside editable controls, or remove the shortcut claim from the button title and related help copy.
- **Verification:** Browser-test the chosen behavior with body focus, editable-control focus, and modifier keys; ensure it does not trigger while typing.
- **Status:** Open; source-confirmed inconsistency, browser test absent.

### OBS-05 — Help and confirmation dialogs do not contain keyboard focus

- **Stories:** [CONSOLE-008](user-stories.md#console-008--open-and-dismiss-help), [CONSOLE-009](user-stories.md#console-009--review-and-cancel-destructive-confirmations), [CONSOLE-040](user-stories.md#console-040--use-the-console-with-keyboard-and-small-viewports)
- **Severity:** P2
- **Exact reproduction:** Open Help or a destructive confirmation and press Tab repeatedly through its controls. The inspected handlers focus an initial control and restore focus on close, but do not cycle focus within the open dialog; focus can move to the obscured page. This fails the focus-containment expectation in `CONSOLE-008-AC03` and `CONSOLE-040-AC01`.
- **Root cause:** `src/main/resources/static/js/console.js` focuses the confirmation cancel control and restores prior focus (`confirmAction`, around lines 288–304), and the Help handler focuses its close control and restores the trigger (around lines 1735–1755). Neither handler traps Tab/Shift+Tab.
- **Proposed fix:** Implement a tested modal focus-management pattern (including Tab wrap, Escape, initial focus, and restoration) or use a native dialog implementation with equivalent behavior.
- **Verification:** Browser-driven keyboard test for both dialogs, forward and reverse tab wrap, Escape/cancel, and focus restoration; include a screen-reader/manual accessibility review.
- **Status:** Open; missing trap is source-confirmed; actual keyboard traversal has not been recorded in the baseline.

### OBS-06 — Previously found narrow-width overflow is fixed and manually re-verified

- **Stories:** [CONSOLE-040](user-stories.md#console-040--use-the-console-with-keyboard-and-small-viewports)
- **Severity:** P2 historical issue; **closed for the recorded viewports**
- **Exact reproduction:** The earlier interactive-fixture browser check found document horizontal overflow at 760px and 999px. After the CSS change, the same viewport check at **760px, 999px, and 1440px** showed document width equal to viewport width; the 12 panels were also visited at 999px without a visible alert. This is manual evidence, not an automated viewport suite.
- **Root cause:** The original observation record does not identify a single overflowing element. The current responsive CSS constrains the grid’s flexible track with `minmax(0, 1fr)`, allows the topbar to wrap below 1100px, and sets `min-width: 0` on the topbar/main (`src/main/resources/static/css/console.css`, around lines 108–115, 220–225, 334–341, and 618–627).
- **Proposed fix:** No further change for the verified widths. Preserve this CSS fix; add automated viewport coverage before generalizing beyond these checks.
- **Verification:** Repeat the browser width check at 760px, 999px, and 1440px and assert `document.documentElement.scrollWidth <= window.innerWidth`; exercise all panels at the narrow widths.
- **Status:** Verified fixed manually at 760/999/1440 in the prior browser record; broader viewport/zoom coverage remains open.

## Unverified behavior and coverage gaps

### GAP-01 — Passing backend tests do not verify Console browser workflows

- **Stories:** [CONSOLE-001](user-stories.md#console-001--sign-in-with-configured-credentials-or-api-key)–[CONSOLE-038](user-stories.md#console-038--inspect-server-health-and-jvm-metrics), and [CONSOLE-040](user-stories.md#console-040--use-the-console-with-keyboard-and-small-viewports)
- **Severity:** P1
- **Exact reproduction:** Run the exact Maven command in [baseline.md](baseline.md). It passes 110 selected tests; the baseline explicitly classifies the evidence as embedded-server HTTP and static asset assertions and says browser/live UI was not exercised. The stories and inventory likewise identify browser interaction as unverified; no Playwright project/test suite was present in the inspected baseline.
- **Root cause:** The selected baseline is a Java backend/static-asset test run, not a browser acceptance suite. There is no browser harness/evidence in the committed baseline artifacts.
- **Proposed fix:** Establish browser-driven acceptance coverage against the isolated interactive fixture, prioritizing sign-in/session expiry, navigation and theme, destructive confirmation, collection/query mutations, backup/restore, and empty/error/recovery states. Keep HTTP tests as a separate evidence layer.
- **Verification:** Run the browser suite against the fixture and retain command, browser/runtime, test counts, and failures. Map each test to story acceptance IDs; do not promote HTTP-only evidence to UI verification.
- **Status:** Unverified; baseline tests pass within their stated scope. No browser test result is claimed here.

### GAP-02 — CSRF exemption and API-key bypass branches lack focused test evidence

- **Stories:** [CONSOLE-002](user-stories.md#console-002--enforce-session-authentication-and-csrf)
- **Severity:** P1
- **Exact reproduction:** Run the focused baseline command and inspect `SecurityEnforcementTest`. Its CSRF cases establish rejection for missing/invalid session CSRF and acceptance for a valid token, but do not exercise the documented `GET`/`HEAD`/`OPTIONS` exemptions or a configured API-key-authenticated mutation without a CSRF token.
- **Root cause:** The selected security tests do not cover those branches; this is an evidence gap, not evidence that the implementation is incorrect. The inventory identifies the expected exemptions and bypass as source behavior.
- **Proposed fix:** Add isolated tests for each safe method and for an API-key-authenticated mutation with CSRF enabled. Assert that rejected/session and accepted/API-key cases produce the intended response and mutation effect.
- **Verification:** Run the added tests and the existing `SecurityEnforcementTest`; confirm all cases use test-only credentials and expose no secrets in output.
- **Status:** Unverified; no failure observed or compatibility conclusion made.

### GAP-03 — Backup/restore success against durable production storage is not established

- **Stories:** [CONSOLE-032](user-stories.md#console-032--inspect-and-create-backups), [CONSOLE-033](user-stories.md#console-033--restore-a-backup-after-confirmation)
- **Severity:** P1
- **Exact reproduction:** The baseline includes `ConsoleBackupEndpointTest`, but the inventory records the exercised fixture as in-memory. Re-run the recorded baseline and inspect that test fixture: a passing response does not demonstrate backup persistence across process restart, restore from a durable file, or correct recovery after an interrupted/timed-out restore.
- **Root cause:** The selected evidence uses an in-memory engine/fixture; production storage and failure/recovery outcomes are not exercised. The cause of any production-specific issue is **unknown** until such a test is run.
- **Proposed fix:** Add a disposable, filesystem-backed integration scenario that creates representative data, takes a backup, restarts/reopens the engine, restores, and verifies persisted state; separately exercise invalid/missing backup and interrupted-operation handling.
- **Verification:** Run the test against the supported durable engine and assert data before/after restart and restore, HTTP/UI outcomes, and filesystem cleanup. Do not retry an indeterminate restore without checking resulting state.
- **Status:** Unverified; endpoint tests pass within the in-memory scope reported by the inventory.

### GAP-04 — Framework compatibility statements are verification leads only

- **Stories:** Not a Console workflow; relevant to the repository’s framework-support/release claims. Console branding is tracked separately under [CONSOLE-008](user-stories.md#console-008--open-and-dismiss-help).
- **Severity:** P2
- **Exact reproduction:** Read the “Framework Integrations & Standards” table in `README.md` (around lines 151–159), then note the repository contains `spring-boot-starter/`, `quarkus-extension/`, `micronaut-integration/`, and demos under `demo/` including Spring Boot, Quarkus, Micronaut, and Vert.x. These claims and directories identify candidates for a verification queue; merely finding a module, test count, version, or README statement does not establish compatibility.
- **Root cause:** **Unknown.** This assessment did not build/run framework integrations or validate supported version ranges, native execution, lifecycle, or the embedded Console in each framework.
- **Proposed fix:** Track Spring Boot, Quarkus, Micronaut, and Vert.x as separate unverified leads. For each, collect pinned environment/version, clean build and test output, runnable application startup, representative database operation, and Console HTTP/browser evidence before publishing a compatibility conclusion. Treat any website/README claim the same way until corroborated.
- **Verification:** Execute the framework-specific validation plan and report the exact versions, commands, results, artifacts, and limits per framework. Do not infer a pass from documentation or module presence.
- **Status:** Unverified lead; no framework compatibility conclusion is made by this assessment.

## Recommendations

### REC-01 — Make evidence status traceable at story-acceptance level

- **Stories:** [CONSOLE-001](user-stories.md#console-001--sign-in-with-configured-credentials-or-api-key)–[CONSOLE-040](user-stories.md#console-040--use-the-console-with-keyboard-and-small-viewports)
- **Severity:** P2
- **Exact reproduction:** Compare each story’s `CONSOLE-###-AC##` criteria with the selected test list in [baseline.md](baseline.md). Backend coverage is represented by selected test classes; browser outcomes and some configuration-dependent branches remain unverified.
- **Root cause:** The baseline/inventory distinguish evidence levels, but no per-acceptance execution record currently establishes which acceptance IDs pass at UI, HTTP, or persistence level. No additional cause is assumed.
- **Proposed fix:** Maintain a compact evidence matrix with one row per acceptance ID, evidence level (source, static, HTTP, browser, persisted state), exact test/artifact reference, date, and status. Keep “implemented,” “tested,” and “verified” as separate claims.
- **Verification:** Review the matrix against all 40 stories and the baseline after each validation run; ensure every “verified” entry links to reproducible output and no source-only entry is marked as a pass.
- **Status:** Recommended; not implemented in this task.

## Assessment outcome

The selected backend baseline is green within its documented scope, the feature/story mapping is complete, and the earlier responsive overflow has a recorded manual fix at 760/999/1440. The source also contains the open issues above. Browser interaction, accessibility, durable backup/restore, selected CSRF branches, and framework compatibility remain evidence gaps—not inferred failures or passes. This assessment creates no product-code or compatibility change.

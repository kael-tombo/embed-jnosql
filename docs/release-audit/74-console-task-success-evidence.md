# 74 · Console Task-Success Layer — Evidence Record (R-77)

**Date:** 2026-09-23
**Slice:** Console orientation context, explicit action states, safe destructive actions, error reporting
**Status:** DONE — `VERIFIED`, with stub-free limits listed in §7
**Related:** `07-console-audit.md`, `71-constraint-enforcement-evidence.md`, `72-referential-constraint-evidence.md`,
`73-jdbc-driver-evidence.md`, `../product/USER_STORY_MAP.md` (E7 / US-138…145)

---

## 1. Why this slice

The Console was assessed for visual consistency but never against **task success** as a
measurable contract. Three requirements were unmet in ways that had real consequences:

1. **Orientation context was absent.** Nothing in the UI stated the active database, the
   storage mode, or whether the data was durable. An in-memory database looked identical to a
   file-backed one. The single most dangerous Console failure — believing a write is persisted
   when it is not — was invisible.
2. **Destructive actions used `window.confirm`.** A browser prompt cannot name a target or
   quantify an impact, so `DELETE FROM products` and `DELETE FROM products WHERE id='p1'`
   produced the same indistinguishable prompt, and the cancellation path was a silent no-op.
3. **Errors were not reportable.** No correlation id existed anywhere, so a user reporting a
   failure could identify neither the request nor the server event, and the UI never answered
   "did my data change?".

Every claim below is backed by an executed command in this session.

---

## 2. What was built

### 2.1 Orientation context (`GET /api/health` → `context`)

A `context` block is read from the **live configuration**, never guessed by the front end:

| Field | Source | Example (FILE engine) |
|---|---|---|
| `engine` | `config.storageEngine()` | `FILE` |
| `relationalEngine` / `nosqlEngine` | engine identity | `JUNIFYDB-RDBMS` / `JUNIFYDB-NOSQL` |
| `storageMode` | engine type + `autoFlush()` | `sync` |
| `durability` | engine type + flush interval | `periodic flush every 1000 ms` |
| `database` / `dataDir` | `config.dataDir()` | `target/preview-data` |
| `authEnabled` | live auth state | `false` |
| `user` | resolved identity | `anonymous` / `api-key` / signed-in username |
| `activeTransactions` | live transaction map | `0` |
| `transactionalConsoleWrites` | constant, honestly `false` | `false` |
| `transactionScope` | explanatory text | `Console writes bypass transactions; …` |

An `IN_MEMORY` database reports `storageMode: "in-memory"` and
`durability: "no durability - data is lost when the process exits"`. The Console is therefore
structurally incapable of implying persistence it does not have.

### 2.2 Always-visible status bar

`#statusbar` renders engine · storage (mode + durability) · database · connection · tx ·
user, plus a live "active context" label (collection, index, or SQL result). It is fed by the
same `/api/health` read as the topbar chips, so the two cannot disagree.

### 2.3 Explicit action states

`STATES` / `setBadge` / `successOrEmpty` in `console.js` give every action exactly one stated
outcome. A **0-row result set is `empty`, not `success`** — the requirement's central
distinction. A 20-second client ceiling converts a stall into `timeout` with its own
data-safety wording, and an aborted request becomes a distinguishable `cancelled` state.

### 2.4 Safe destructive actions

`confirmAction()` replaces `window.confirm` for the collection-wide delete, backup restore, and
destructive SQL. It shows **Target / Impact / Undo**, uses `role="dialog"` with `aria-modal`,
focuses **Cancel** first, and closes on Escape or backdrop click. `destructiveReason()` detects
`DROP`, and `DELETE`/`UPDATE` without `WHERE` — on the uppercased copy for keyword matching and
the **original** text for the target, so the dialog echoes the user's own spelling.

### 2.5 Error reporting

`sendJson` stamps `X-Correlation-Id` on **every** response (honouring a caller-supplied id) and
embeds `correlationId` in every error body. The UI error banner states: what failed, that it was
a validation / permission / conflict / rate / timeout / recovery / backend failure, **whether
data changed**, how to fix it, where to learn more (the dialect's supported surface), and the
correlation id to quote.

---

## 3. Evidence

### 3.1 New test — `ConsoleTaskSuccessTest` (7/7)

| Test | Claim proven |
|---|---|
| `healthExposesOrientationContext` | all 10 required context fields present and server-sourced; in-memory states "no durability"; identity is the signed-in user |
| `fileEngineReportsDurabilityAndDataDir` | a FILE database reports `sync`, the **actual** configured flush interval (1500 ms), and its data dir |
| `activeTransactionsAppearInContext` | a begun transaction moves `activeTransactions` 0 → 1 |
| `correlationIdOnSuccessAndFailure` | header present on success; on a 404 the body id **equals** the header id |
| `requestedCorrelationIdIsHonoured` | a caller-supplied id is echoed (end-to-end tracing) |
| `failuresAreClassifiable` | 401 permission, 400 syntax, 400 empty, and 400 duplicate-primary-key are distinguishable, and the rejected write leaves the table unchanged |
| `staticAssetsExposeRequiredAffordances` | status-bar items, SQL cancel/selection/export/destructive controls, a `role="dialog"` confirmation, **no** bare `confirm(...)` call, the full state vocabulary, and the "Did data change?" / "Correlation ID" strings all ship |

### 3.2 Full suite and footprint

| Check | Result |
|---|---|
| Full suite | **879/879 green**, 0 failures, 0 errors, 0 skipped (`BUILD SUCCESS`) |
| Delta | 872 → 879 (+7) |
| Coverage gate | `mvn verify -Pcoverage-check` → **"All coverage checks have been met."** |
| Line coverage | **75.4%** (6,579 of 8,728 lines) over the **181 instrumented classes**; branch 57.6% |
| Shaded jar | **3,174,147 bytes (3.17 MB)** — still under the 5 MB gate |

> **Measurement note (honesty, not a claim).** Earlier rounds published line coverage as 76.5%
> computed over a much larger denominator (27,791/36,821 lines). This round's figure is
> calculated from the current `target/site/jacoco/jacoco.csv` (181 classes, 8,728 measured
> lines) after `prepare-agent` instrumented **246** compiled classes and the report exclusions
> were applied. The two percentages are therefore **not directly comparable**, and the earlier
> denominator should be re-derived before any future round quotes a trend. What is
> gate-backed this round is the only thing asserted here: the 70% LINE gate passes.

### 3.3 Falsification against the pre-change build

Run against the **pre-change server still listening on 8081** (built before this slice):

```
=== PRE-CHANGE health ===  NO context block present
=== PRE-CHANGE error ===   HTTP/1.1 404 Not Found
                           (no X-Correlation-Id header, no correlationId in body)
```

The same probes against the rebuilt server in this session:

```
context: {"engine":"FILE","storageMode":"sync","durability":"periodic flush every 1000 ms",
          "database":"…\\target\\preview-data","user":"anonymous","activeTransactions":0,…}
X-correlation-id: 173fdac6
{"message":"Table 'no_such_table' does not exist","error":"Table does not exist",
 "correlationId":"173fdac6"}
```

The header id and the body id match, which is the property the test asserts.

### 3.4 Live browser journey (real clicks, real state)

Registered preview `http://localhost:8081` (rebuilt jar), then drove it as a user:

| Step | Observed |
|---|---|
| Load Console | accessibility tree exposes `status "Active database context"` with all six items rendered, e.g. `engine FILE`, `storage sync · periodic flush every 1000 ms`, `connection online`, `tx none active · console writes bypass tx`, `user anonymous (auth disabled)` |
| Type `DELETE FROM products` | warning appears **before** Run: badge `⚠ destructive DELETE`, tooltip `Applies to EVERY row in products — the statement has no WHERE clause.` |
| Press Run | dialog opens titled *Confirm destructive DELETE*, Target `products (Relational SQL Engine)`, Impact naming the missing WHERE clause, Undo stated as not reversible, primary action `Run DELETE`, focus on **Cancel** |
| Press Cancel | `confirmBackdrop.hidden = true`, status reads `idle · cancelled by user`, and `GET /api/collections/products` still returns **4** documents — cancellation is a provable no-op |
| Run `SELECT * FROM no_such_table` | state `validation error`; panel answers what failed, **"Did data change? No data was changed — the engine rejected the request."**, how to fix, where to learn more, and `Correlation ID: b0ae7ff4` |

---

## 4. Requirement coverage

| Requirement | State | Note |
|---|---|---|
| Always show engine, database, schema/collection, storage mode, connection, transaction, security context | **MET** | §2.1–2.2; `schema` is shown as the active-context label, not a separate field |
| Explicit states: idle, loading, success, empty, validation, backend, timeout, permission, conflict, recovery | **MET** | §2.3; `rate limited` added as an eleventh |
| Destructive actions: target, impact, confirmation, rollback, backup, progress, final result, audit | **PARTIAL** | target/impact/confirmation/reversibility/final result and audit (via the Audit Trail panel) are wired; **rollback** and **backup** are *stated* rather than automated, and a per-item progress bar is replaced by a live `deleting… n/total` counter |
| SQL workflow: highlighting, autocomplete, formatting, history, saved queries, tabs, run selection, cancellation, duration, rows, error location, explain, export, destructive warning | **PARTIAL** | present: history, run selection, cancellation, duration, rows returned, result export, destructive warning. **Absent: syntax highlighting, autocomplete, formatting, named saved queries, multiple tabs, `EXPLAIN`** → US-145 |
| NoSQL workflow: nested search, TTL info, index info, JSON editor, validation feedback | **PARTIAL** | collection navigation, JSON editing, TTL expiry column, index panel and validation feedback exist; **document tree view, form view and a structured query builder do not** |
| Errors explain what/why/data-changed/rollback/how-to-fix/learn-more/correlation-id | **MET** | §2.5; "rollback" is answered inside the data-changed sentence |
| Keyboard, focus, contrast, screen-reader, responsive, reduced motion, cancelable long operations, no fake controls | **PARTIAL** | dialog focus + Escape, `role="dialog"`, `role="alert"`, `:focus-visible`, `prefers-reduced-motion`, and responsive breakpoints are in place; no screen-reader pass (NVDA/VoiceOver) or automated axe contrast audit was run |
| Full task path validated to persisted state | **MET for the destructive and error journeys** | §3.4 verifies the store after cancellation; the remaining panels were validated in earlier rounds |

---

## 5. Defects found and fixed

| ID | Defect | Fix |
|---|---|---|
| **R-77** | The Console had no orientation context: engine/database/storage/durability/transaction/identity were unavailable to the UI, so an in-memory database was indistinguishable from a durable one | `/api/health` `context` block + persistent status bar (§2.1–2.2) |
| **R-78** | Destructive actions used `window.confirm`, which cannot name a target or an impact; cancellation was a silent no-op | `confirmAction` dialog + `destructiveReason` guard + explicit cancelled state (§2.4) |
| **R-79** | No correlation id existed; failures were neither traceable nor answerable as to data impact | `X-Correlation-Id` on all responses, `correlationId` in error bodies, error banner (§2.5) |
| **R-80** | A 0-row result set rendered as a success badge, and a hung request produced no state at all | `successOrEmpty` + 20 s timeout state (§2.3) |
| **R-81** | (Regression I introduced during this slice) the destructive dialog echoed the uppercased table name, e.g. `PRODUCTS` | target extracted from the original text, case-insensitively (§2.4) |

---

## 6. Files changed

| File | Change |
|---|---|
| `console/http/JunifyDBServer.java` | `context` block, `buildContext`, `currentUser`, correlation id in `sendJson`, `withCorrelationId` |
| `static/index.html` | `#statusbar`, confirmation dialog, SQL selection/cancel/export/destructive controls |
| `static/css/console.css` | grid row for the status bar, `.statusbar`, `.state-banner`, `.modal` |
| `static/js/console.js` | `STATES`/`ApiError`/`errorBanner`, `confirmAction`, `destructiveReason`, `pollStatus`, `exportSql`, cancel + run-selection |
| `src/test/java/org/junify/db/ConsoleTaskSuccessTest.java` | new, 7 tests |

---

## 7. Honest limits

1. **US-145 remains `NOT IMPLEMENTED`.** The SQL editor has no syntax highlighting, autocomplete,
   formatting, named saved-query library, multiple editor tabs, or `EXPLAIN`. The history strip
   is localStorage-only and is not a saved-query library.
2. **No screen-reader or axe audit.** Keyboard and ARIA behaviour was reviewed and is asserted
   for the dialog (`role="dialog"`, `aria-modal`, Escape, focus return), but no VoiceOver/NVDA
   session and no automated contrast audit was performed. This must not be claimed as a WCAG
   conformance result.
3. **Destructive-action rollback and backup are advisory.** The dialog states that an action is
   not reversible and points at the Backup panel; it does not take an automatic pre-image.
4. **NoSQL document editing is JSON-only.** No tree view, form view, or visual query builder.
5. **Progress reporting for bulk deletion is a counter**, not a cancelable progress bar. Bulk
   deletion makes one HTTP call per document and reports partial success honestly rather than
   as an unqualified success.
6. **`transactionalConsoleWrites` is `false` by design**, and the Console says so in the status
   bar and the Transactions panel, rather than implying Console writes join a transaction.

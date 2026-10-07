# Current Console tests (baseline, 2026-09-23)

## Test files guarding the Console

| Test | Count | What it proves |
|---|---|---|
| `ConsoleTaskSuccessTest` | 7 | Orientation context (10 fields, in-memory "no durability"), file-engine durability text, tx visibility, correlation-id equality (header/body), caller-supplied id echo, classifiable failures (401/400/404/duplicate-PK), shipped-asset affordances (status-bar ids, SQL controls, dialog, state vocabulary, "Did data change?", destructiveReason, no `window.confirm`) |
| `BrowserConsoleWorkflowVerificationTest` | 10 | 200s + security headers on `/`, `login.html`, `logo.svg`; auth barrier; telemetry; collection CRUD round-trip; query engine; KV + structures; column families; schema + indexes |
| `ConsoleFeatureValidationTest` | per CONSOLE-001…025 | Startup, port management, auth flows, CSRF, brute force, headers, static serving, health/metrics, collections CRUD, query, KV, structures, column families, schema, indexes, vectors, bulk, backup, audit, CDC |
| `SecurityEnforcementTest` | 5 | 401 anonymous, CSRF 403, brute-force 429, logout invalidation, security headers |
| `ConsoleBackupEndpointTest`, `ConsoleCollectionResolutionTest`, `ConsoleIndexAndCdcEndpointTest`, `ConsoleIndexRouteExistenceTest`, `ConsoleQueryEndpointTest`, `ConsoleTransactionAndBulkTest`, `ConsoleVectorSearchTest`, `ConsoleComprehensiveFeatureProofTest`, `SqlAuditTrailTest` | — | endpoint-level behavior incl. R-48/R-55/R-62/R-64 regressions |
| `AdminConsoleConfigTest` | — | ConsoleConfig binding |
| `console/` subpackage tests | — | port management |

## Gates

- Full suite green: **832/832** (2026-09-23 re-verification round; 879 quoted in doc 74 was an
  earlier counting basis — 832 is the current tracked figure in `docs/release-audit/baseline/TEST-BASELINE.txt`).
- Contract gate `scripts/console-contract-gate.sh` PASS on FILE, LSM_TREE, B_TREE.
- Auth gate `scripts/console-auth-gate.sh` PASS.
- Coverage gate: 70% line (75.5% measured). Shaded jar < 5 MB (3,122,887 bytes).

## Constraints this redesign must respect

1. Every element id and string asserted by `staticAssetsExposeRequiredAffordances` must remain
   present (or the test must be consciously extended alongside the redesign — never silently weakened).
2. `/`, `/login.html`, `/logo.svg` must keep serving 200 with the asserted content types.
3. Legacy `/css/enhancements.css` + `/js/enhancements.js` must keep returning 200.
4. No `window.confirm(` / bare `confirm(` calls may appear in `console.js`.
5. The state vocabulary strings (`'loading'`, `'success'`, `'empty'`, `'validation'`, `'backend'`,
   `'timeout'`, `'permission'`, `'conflict'`, `'recovery_required'`) must remain present.
6. Status-bar ids `sbEngine, sbStorage, sbDatabase, sbConnection, sbTx, sbUser, sbContext, statusbar`
   and SQL ids `sqlCancel, sqlRunSel, sqlExportCsv, sqlExportJson, sqlDestructive`, `confirmDialog`
   with `role="dialog"` must remain in `index.html`.

# Current frontend components (baseline, 2026-09-23)

All logic lives in `console.js` as plain functions over a static `index.html`; there is no
framework and no build step (intentional — CSP-safe, zero-dependency embedded asset).

## Shell

| Component | Ids | Behavior |
|---|---|---|
| Brand header | `.brand` | canonical logo tile + "EmbedJNoSQL Console" |
| Topbar | `panelTitle`, `panelSub`, `chipHealth`, `chipEngine`, `chipUptime`, `btnTheme`, `btnRefreshAll`, `btnLogout` | health chip, engine name, uptime, theme toggle, refresh, sign out |
| Status bar | `statusbar` + `sbEngine/sbStorage/sbDatabase/sbConnection/sbTx/sbUser/sbContext` | always-visible orientation, `/api/health`-sourced, refreshed every 10s |
| Sidebar | `nav` | grouped buttons, icons, number-key hints, hash routing, rail collapse < 900px |
| Toasts | `#toasts` | role=status/alert, kinds ok/err/warn/info, dismissible |
| Confirm dialog | `confirmBackdrop/confirmDialog/confirmTitle/confirmBody/confirmHint/confirmCancel/confirmAccept` | Target/Impact/Undo; Escape + backdrop close; focus Cancel; focus restore |

## Panel behaviors (one section per panel in index.html)

| Panel | Refresh fn | Notable behaviors |
|---|---|---|
| Overview | `refreshOverview` | KPIs from health+metrics, ops/s sparkline (60 samples), engine info, collections list, recent audit; 5s auto-refresh while active |
| SQL Studio | — | textarea editor, run/run-selection, cancel (AbortController), destructive live badge, confirmation flow, result table, CSV/JSON export from last result, history strip (localStorage 30), examples strip fallback |
| Collections | `refreshCollections` | blind name+Load, docs table (id + ≤8 unioned fields + expiry), detail JSON, load-into-editor, insert (JSON validated), cleanup expired, drop-all with per-doc progress + partial-failure honesty |
| Key-Value | `bindKvTab` | 4 sub-tabs (kv/list/set/hash) each a hand-built form, JSON output only — no browsing |
| Column Family | — | fetch row / family stats / put row (JSON columns) |
| Vectors | — | index info (GET /{index}/_), k-NN search, experimental warning |
| Schema | `schRefresh` | registered schema list w/ per-collection fetch, register fields JSON + strict checkbox |
| Transactions | `txRefresh` | active list, begin, commit/rollback per id, scope warning card |
| Indexes | — | create field index, list with type/unique/entries |
| Backup | `bkRefresh` | status KPIs, backup dir + snapshot count, create (honest empty-warning), restore w/ dialog |
| CDC | `cdcRefresh` | status, enable/disable, add file/kafka connector, remove, events table |
| Audit | `audRefresh` | mutation ring table |
| Server | `srvRefresh` | raw health + metrics JSON |

## Shared helpers

`api()` (timeout, abort, correlation-id capture, 401 single-redirect, ApiError with data-safety
`impact`), `STATE_UI`/`setBadge`/`successOrEmpty`, `errorBanner`/`fixHint`, `confirmAction`,
`destructiveReason`, `renderJson` (highlighter), `tbl`, `fmtBytes`, `fmtUptime`, `setActiveContext`,
`pollStatus`, `buildNav`, `goto`, `REFRESH` registry, theme persistence.

## Accessibility state (baseline)

Present: `:focus-visible` outline, `prefers-reduced-motion` kill, dialog `role="dialog"` +
`aria-modal` + labelled/describedby, toast `role` semantics, status bar `role="status"` +
`aria-label`, form labels (`label.fld`), tooltips via `title`, empty states with brand glyph.
Absent: skip-link, `nav`/`main` landmarks, `aria-current` on nav items, `role="tablist"` on the
KV tabs, `aria-live` region for async completion announcements, table captions.

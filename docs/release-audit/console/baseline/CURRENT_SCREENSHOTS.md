# Current screenshots (baseline, 2026-09-23)

Screenshot capture is available in this environment via the browser tools (unlike the earlier
audit round that could not composite frames). Baseline captures of the pre-redesign Console are
stored here for before/after comparison.

## Captures

| File | Panel | Notes |
|---|---|---|
| `baseline-overview.png` | Overview | dark theme, KPIs + sparkline + engine info |
| `baseline-sql.png` | SQL Studio | empty editor, examples strip, idle badge |
| `baseline-collections.png` | Collections | blind name entry, empty-state table |
| `baseline-kv.png` | Key-Value | KV sub-tab form |
| `baseline-light.png` | Overview | light theme for token comparison |

Baseline capture note: the pre-redesign server was not re-driven to populate every panel before
capture; panels that require typed input (Collections, KV, Columns, Vectors, Indexes) are shown
in their entry state — which is itself the point: the baseline entry states are where the
discoverability defects (CD-03, CD-08) are visible.

Evidence for the redesigned Console is captured under `docs/release-audit/evidence/console/`.

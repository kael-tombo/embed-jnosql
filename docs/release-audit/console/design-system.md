# 2. Design System

> **SUPERSEDED - pre-refactor document (SQL / dual-engine).**
> EmbedJNoSQL is now NoSQL-only. The relational engine, SQL parser/planning, JDBC driver, JPA
> `EntityManager`, `/api/sql` routes, and the SQL Studio console screen were removed from the
> product. Statements in this file that describe SQL, JDBC, SQL schemas, or an engine selector no
> longer describe shipped behavior.
>
> - Current product definition: ../refocus/PRODUCT-CONSTITUTION.md
> - Removal inventory and evidence: ../refocus/SQL-REMOVAL-MANIFEST.md
>
> Retained as historical record only - not part of the current release contract.

**Status:** DELIVERED · **Defects closed:** CD-11 (login brand parity — verified conformant)

## Tokens (`static/css/console.css`)

| Token | Light | Dark | Role |
|---|---|---|---|
| `--brand-navy` | `#0b2a52` | same | mark body, chip backing |
| `--accent` | `#fcd34d` | `#b45309` | primary action — Volt amber (site-canonical) |
| `--accent-strong` | `#f59e0b` | `#92400e` | hover / emphasis |
| `--accent-dim` | `rgba(252,211,77,.12)` | `rgba(180,83,9,.10)` | selected chips, focus halos |
| `--ok` / `--err` / `--warn` | semantic | semantic | state badges |

Typography is system-stack; the reduced-motion block is the final CSS section (all new selectors were inserted before it).

## Component vocabulary

- **Badges** (`STATE_UI`): one class per state — idle/loading/success/empty/validation/backend/timeout/permission/conflict/recovery_required — shared by every panel; `setBadge()` is the only writer.
- **State banners** (`.state-banner ok|warn|err|info`): multi-line outcomes that answer what failed / "Did data change?" / how to fix.
- **Buttons**: `.btn` with `sm`, `primary`, `danger` variants; destructive actions always open `#confirmDialog`.
- **Tables** (`.tbl` in `.tbl-wrap`): single helper `tbl(headers, rows)` — now tolerant of pre-wrapped cells (see browser-validation doc, §global fix).
- **Chips** (`.chip-row .chip`): engine-derived pickers; `.active` + `aria-pressed` for selection.
- **Dialogs**: `#confirmDialog`, `prompt2()`, `#helpDialog` — all `role=dialog`, `aria-modal`, focus-safe.
- **Trees**: `.tbl-node/.tbl-col` for schema, `.json-tree` for documents, with `.col-flags` (PK/NN/U/FK) and `.tbl-check`.

## New this round

`.sql-layout`, `.sql-explorer`, `.sql-editor-wrap` (+ highlight overlay, `.tok-*` tokens), `.sql-suggest`, `.saved-row`, `.qb-row`, `.chip-row.active`, `.json-tree*`, `.help-keys`, `.view-toggle`, `.sr-only`, `.skip-link`, `.crumbs*`, `.nav-search*`.

## Login (CD-11)

`login.html` already ships the brand system (Volt amber on `#0b2a52` navy, glass card, favicon) with honest error/lockout states — inspected and documented rather than restyled; see `../evidence/console/BROWSER-JOURNEY-SHELL-OVERVIEW.md` §9.

Brand assets: `baseline/CURRENT_BRAND_ASSETS.md`.

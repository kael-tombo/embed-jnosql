# 4. Navigation

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

**Status:** DELIVERED · **Defects closed:** CD-09

## Structures

- **Router**: hash-based (`#overview` … `#tx`), `goto()` re-renders breadcrumbs, sets `aria-current` on the active nav item, fires `onFirstOpen(id)` for lazy data loads (collections, sql, backup, overview).
- **Breadcrumbs** (`#crumbs`): `Console / {engine} / {panel}` — e.g. `Console / Non-Relational NoSQL Engine / Collections`. Verified live.
- **Sidebar search** (`#navSearch`): substring filter over nav labels; typing `back` isolates `Backup`; clearing restores. Labels, not hidden hacks — filtering is by visible text.
- **KV sub-tabs**: `role=tablist` with `aria-selected`; keyboard focusable.
- **Skip link**: first focusable element, jumps to main content.
- **Help** (`#btnHelp` → `#helpDialog`): shortcuts (Ctrl+Enter, Ctrl+Shift+Enter, Tab), state vocabulary, engine scope notes; Escape/backdrop/`#helpClose` all close; focus on open goes to `#helpClose`, restored on close.

## Keyboard map

| Keys | Action |
|---|---|
| Ctrl+Enter | run SQL |
| Ctrl+Shift+Enter | run selected SQL |
| Tab (in editor, suggestion visible) | accept keyword |
| Escape | close dialog |
| Ctrl+S | export last result (documented in help) |

## Negative paths

- Unknown hash → falls back to Overview (router default), no blank screen.
- Nav search with no match → "no matches" hint; the list is restorable by clearing input.
- Back/forward: hash routing keeps browser history honest; deep links restore the exact panel.

Evidence: `../evidence/console/BROWSER-JOURNEY-SHELL-OVERVIEW.md` §1–4.

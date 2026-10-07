# 12. Responsive Design

> **SUPERSEDED - pre-refactor document (SQL / dual-engine).**
> JunifyDB is now NoSQL-only. The relational engine, SQL parser/planning, JDBC driver, JPA
> `EntityManager`, `/api/sql` routes, and the SQL Studio console screen were removed from the
> product. Statements in this file that describe SQL, JDBC, SQL schemas, or an engine selector no
> longer describe shipped behavior.
>
> - Current product definition: ../refocus/PRODUCT-CONSTITUTION.md
> - Removal inventory and evidence: ../refocus/SQL-REMOVAL-MANIFEST.md
>
> Retained as historical record only - not part of the current release contract.

**Status:** IMPROVED · **Stories:** US-096

## Layout system

- Sidebar + main shell; `.sql-layout` is a CSS grid that stacks to one column under the panel breakpoint, keeping the explorer reachable above the editor rather than hiding it.
- `.tbl-wrap` gives every table horizontal scroll instead of squeezing columns; sticky table headers keep context while scrolling long result sets.
- Chips wrap (`flex-wrap`), toolbar rows wrap with gaps — no fixed-width toolbars remain from this round's additions.

## Breakpoint behaviour (by construction + spot checks)

| Range | Behaviour |
|---|---|
| ≥ 1200 px | full two-column SQL layout, sidebar persistent |
| 768–1199 px | sidebar persists; SQL stacks; tables scroll |
| < 768 px | sidebar collapses to icons/hidden per existing shell rules; all primary controls remain in flow; dialogs cap to viewport with internal scroll |

## Constraints honestly noted

This session's preview compositor blocked reliable multi-viewport screenshot capture (see evidence note), so the breakpoint table is construction-based + DOM spot-checks, not a full screenshot matrix. The prior baseline screenshots (`baseline/CURRENT_SCREENSHOTS.md`) document the pre-round behaviour this work extends.

No primary control is position:absolute or hidden at any breakpoint as a result of this round's additions (all new controls live inside wrapping flex/grid containers).

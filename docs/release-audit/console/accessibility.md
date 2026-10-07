# 11. Accessibility

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

**Status:** DELIVERED (major gaps closed; full WCAG audit still future work) · **Stories:** US-099 · **Defects closed:** CD-12

## Closed this round

| Gap | Implementation | Verified |
|---|---|---|
| Status not announced | Global `#liveRegion` (`aria-live=polite`, `role=status`, `.sr-only`), fed by every `setBadge` with `data-scope` label | live text `SQL: success · 25 ms` observed |
| No skip link | `.skip-link` first focusable, jumps to `#main` | present in DOM |
| Tab order / current state | `aria-current` on nav; `role=tablist` + `aria-selected` on KV tabs | 3 nav items current; 4 tabs |
| Icon-only buttons | aria-labels (`Delete key user-2`, `Delete saved query …`) | in rendered rows |
| Dialog focus | confirm/prompt/help: focus to safe default (Cancel) on open, restored on close; Escape closes | `focus: confirmCancel` observed twice |
| Collection chips ambiguous to AT | `aria-pressed` toggling with `.active` | observed |
| Live result counts | `#kvBrowser`, `#sqlOut` containers labelled/announced via badges | — |

## Already in place (prior round, re-verified)

Semantic landmarks (`header/nav/main`), `:focus-visible` styles, reduced-motion media block, labels on every form field (`<label class="fld" for=…>`), contrast-checked Volt-on-navy tokens in both themes, `.sr-only` utility now defined.

## Honest remaining limits (for the next round)

- One automated axe/axe-core run has not been performed in CI yet.
- The SQL highlight overlay is decorative (`aria-hidden`) — correct, but an "editor description" for AT users could still improve onboarding.
- Table caption elements are not yet emitted by the `tbl()` helper.

Test plan: `BrowserConsoleWorkflowVerificationTest` (page loads), `ConsoleTaskSuccessTest.staticAssetsExposeRequiredAffordances` (dialog + no `window.confirm`), live journeys in `../evidence/console/BROWSER-JOURNEY-SHELL-OVERVIEW.md` §3–8.

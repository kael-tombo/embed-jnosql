# 16. Feature Traceability

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

**Status:** DELIVERED · Maps stories (US-078…099, US-138…145) and baseline defects (CD-01…CD-13) to implementation, tests, and evidence.

## Stories

| Story | Outcome this round | Implementation | Test/Evidence |
|---|---|---|---|
| US-078 Start console | unchanged, re-verified | `JunifyDBServer`, `PortManager` | `BrowserConsoleWorkflowVerificationTest` |
| US-079 Overview | + Storage & WAL card, version chip | `StorageStatusHandler`, `#ovStorage`, `#chipVersion` | `ConsoleWorkspaceEndpointsTest.storageStatus…`; EVID §6 |
| US-080 Collections CRUD | + chips w/ state, JSON cells, tree detail | `loadColPicker/loadCollection/jsonTreeHtml` | EVID-COLL §1–3 |
| US-081 KV browsing w/ TTL | **new** browser + TTL create/delete | `KvMetaHandler`, `kvBrowse/kvNewKeyForm` | 6 endpoint tests; EVID-KV |
| US-083/087/088 structures/indexes/vectors | re-verified, table fix applies | `tbl()` tolerance | EVID-KV (global fix note) |
| US-084/085 SQL run/render | + explorer, highlighting, format, cap | `.sql-layout`, `SqlSchemaHandler` | EVID-SQL §1–3,7,8 |
| US-086 NoSQL query workspace | **query builder** with server-side sort/limit/offset | `qb*` fns; endpoint strips reserved keys → `Query.sortBy/limit/offset` | `…queryEndpointAppliesServerSideWindowing`; EVID-COLL §4 |
| US-089 Schema inspector | new endpoint + tree UI | `SqlEngine.schemaCatalog()` | 2 endpoint tests |
| US-090…092 metrics/CDC/audit | re-verified; tables aligned by global fix | — | EVID-KV note |
| US-093/141 errors + correlation id | re-verified on new endpoints | 404/400 bodies w/ ids | EVID-COLL §5 |
| US-094 no fake success | delete→204 only after confirm; TTL sweep honesty | — | EVID-KV §4 |
| US-095 meaningful empty states | kept; `#sqlSaved` + KV empty states | — | EVID-KV §1 |
| US-096 responsive | sql-layout stacks; wrapping toolbars | CSS | responsive-design.md |
| US-097/098 auth/CSRF | unchanged; new endpoints inherit | `KvMetaHandler` under same guards | security-ux.md |
| US-099 accessible console | **live region, skip link, aria-current/pressed, tablist** | `announce()`, `.sr-only` | EVID-SHELL §3,7,8 |
| US-138 context | + version chip server-sourced | `resolveVersion()` | EVID-SHELL §5 |
| US-139 explicit states | re-verified; now announced | `setBadge` → `#liveRegion` | EVID-SQL §3 |
| US-140 destructive confirm | re-verified incl. KV + saved queries | `confirmAction` | EVID-SQL §4,6; EVID-KV §4 |
| US-142/143/144 run-selection/cancel/export | re-verified; export unaffected by cap | — | EVID-SQL §4–5,8 |
| US-145 SQL assistance | highlighting + autocomplete + format + saved queries delivered; **tabs + EXPLAIN intentionally not** (engine has no planner) | — | sql-workspace.md |

## Baseline defects (CD-xx) — all closed or dispositioned

| Defect | Status | Where |
|---|---|---|
| CD-01 highlight/autocomplete/format | closed | EVID-SQL §1–2 |
| CD-02 saved queries | closed (incl. `await` bug) | EVID-SQL §6 |
| CD-03 KV browse + TTL | closed | EVID-KV |
| CD-04 TTL create | closed | EVID-KV §2 |
| CD-05 schema explorer | closed | EVID-SQL §7 |
| CD-06 storage/WAL card | closed | EVID-SHELL §6 |
| CD-07 doc tree view | closed | EVID-COLL §3 |
| CD-08 chips + query builder | closed (contract fixed, then upgraded to server-side windowing) | EVID-COLL §1,4 |
| CD-09 breadcrumbs/search/help | closed | EVID-SHELL §1–4 |
| CD-10 version chip | closed | EVID-SHELL §5 |
| CD-11 login styling | verified conformant | design-system.md §Login |
| CD-12 a11y | closed for known gaps (axe run still future) | accessibility.md |
| CD-13 large results | closed (cap 500, export full) | EVID-SQL §8 |

*EVID-* = `../evidence/console/BROWSER-JOURNEY-*.md`. Backend tests: `src/test/java/org/junify/db/ConsoleWorkspaceEndpointsTest.java` (6). Suite: 885/885.

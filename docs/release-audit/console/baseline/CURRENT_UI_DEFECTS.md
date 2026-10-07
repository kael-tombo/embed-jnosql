# Current Console defects (baseline, 2026-09-23)

Defects observed by reading the shipped assets and comparing against the documented task-success
bar (doc 74 §7 "Honest limits") and US-145. Severity: how badly the gap misleads a real operator.

| ID | Severity | Where | Defect |
|---|---|---|---|
| CD-01 | High | SQL Studio | No syntax highlighting, no autocomplete, no formatting — a plain `<textarea>` for a product whose primary relational surface is SQL (US-145). |
| CD-02 | High | SQL Studio | No saved-query library; history is an ephemeral localStorage strip capped at 30 with no names, no dedupe UI, no re-run management. |
| CD-03 | High | KV panel | No key enumeration: the operator must *already know* bucket + key names. The engine has `keys()` but no route exposes it — a KV "browser" without browsing. |
| CD-04 | Medium | KV panel | No TTL create/edit even though the engine supports `put(key, value, Duration)`; expiration is display-only on documents, absent on KV. |
| CD-05 | Medium | SQL Studio | No table/column explorer despite `SqlSchemaCatalog` holding PK/FK/UNIQUE/NOT NULL/CHECK metadata server-side; constraint errors are surfaced only as raw 400 messages. |
| CD-06 | Medium | Overview | WAL/storage status absent: the status bar says "periodic flush every N ms" but nothing shows WAL sequence, checkpoint state, or backup recency at a glance. |
| CD-07 | Medium | Collections | Document browser is a flat `id + fields` table with no tree/JSON viewer for nested values (nested round-trips are VERIFIED at the API level but invisible in the UI). |
| CD-08 | Medium | Collections | NoSQL query builder is a raw JSON textarea; the QueryParser surface ($eq/$ne/$gt/$gte/$lt/$lte/$in/$nin/$regex/$exists/$and/$or + sort/limit/offset) is undocumented in-UI. |
| CD-09 | Low | Shell | No breadcrumbs, no help/docs entry point, no sidebar search; navigation relies on scrolling 13 items. |
| CD-10 | Low | Shell | Version string hardcoded `"1.0.0"` server-side and not displayed anywhere in the UI header. |
| CD-11 | Low | login.html | References `/css/enhancements.css` (legacy 8-line shim) instead of the canonical design system; duplicated styling inside the file. |
| CD-12 | Low | Accessibility | No skip-link, no ARIA landmarks on nav/main, in-panel tabs lack `role="tablist"` semantics, no live-region announcement for async refresh completion. |
| CD-13 | Low | Performance | Result tables render every row without a cap; a 10k-row SQL result renders 10k DOM rows (no client-side limit or warning). |

## Known non-defects (documented limitations, intentionally not "fixed" in the UI)

- No SQL EXPLAIN — the engine has no planner (US-044). The UI must not fake one.
- No SQL procedures/functions/triggers — US-039/040/041.
- Console writes cannot join a transaction begun in the Transactions panel — stated honestly in
  the status bar (`transactionalConsoleWrites: false`); panel-level scope warning is correct behavior.
- Vectors are experimental with fixed 128 dimensions — the warning badge is the correct treatment.
- B_TREE lacks WAL (D-02) — durability messaging is engine-sourced, so this surfaces correctly.

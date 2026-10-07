# Website ↔ Console alignment evidence — 2026-09-21

Companion to `docs/release-audit/45-website-console-consistency.md`.
All checks were run against the **rendered** site and the **running** Console, not source alone.

## Surfaces verified live

| Surface | Method | Result |
|---|---|---|
| Website (local) | served `docs/` at `http://localhost:8093`, driven in the thread preview browser | all 3 brand images 200; nav/footer/sticky-header verified |
| Website (remote) | `https://kael-tombo.github.io/EmbedJNoSQL/?v=2ebcc14` (cache-buster) after gh-pages push `2ebcc14` | 9× "Relational SQL Engine", 3× canonical mark, 0× BM25, 0× 85k claim |
| Console login | preview browser on rebuilt jar (port 8091) | badge "Dual-Engine • Relational SQL + Non-Relational NoSQL"; aria-labels on all inputs |
| Console shell | logged in, DOM snapshot of `#overview` | nav groups: General / **Relational SQL Engine** / **Non-Relational NoSQL Engine** / Data Model / Both Engines |
| Console SQL Studio | typed `SELEC broken FROM x`, ran | `role=alert` error card: "✖ SQL error — Unsupported or invalid SQL statement" + dialect boundary note |
| Console Vectors panel | `#vectors` deep link | "⚠ Experimental — fixed 128 dimensions, API may change" hint |
| Console engine tags | CSS computed in dark + light | SQL tag amber (`#fcd34d`/`#b45309`), NoSQL tag green, text+glyph (not color-only) |
| Console empty states | Collections/SQL/Vector/Overview | brand-glyph (`.empty-logo`) + actionable copy |
| Console loading states | initial overview render | `.loading-row` spinner + labeled "Loading …" |
| Console console log | preview_logs | no JS errors from app code |
| Regression tests | `BrowserConsoleWorkflowVerificationTest` (10) + `SecurityEnforcementTest` (5) | 15/15 green on rebuilt jar |

## Claim audit (grep + rendered DOM)

| Claim | Live site before | Live site after | Basis |
|---|---|---|---|
| "ANSI SQL" | 0 (fixed earlier round) | 0 | doc 05/08: built-in dialect |
| "tamper-evident" | 0 | 0 | doc 22: memory ring + JSONL copy |
| `org.embeddedjnosql:` wrong coords | 0 | 0 | pom.xml: `org.embeddedjnosql.db:embed-jnosql-core` |
| "85k+ ops/sec" (twitter) | **1 — removed this round** | 0 | doc 43: indicative only |
| "BM25 full-text indexing" | **1 — removed this round** | 0 | `TextIndex.java` = inverted index, no BM25 ranking |
| "zero-loss crash recovery" | **1 — replaced** | 0 ("writes replay from the WAL") | doc 15: conditional pass, WAL replay proven, narrow rotation window |
| "Crash-Safe WAL" badge | **1 — replaced** | 0 ("WAL + Checkpoints") | same |
| Simulated playground latency labeled | unlabeled random ms | "⚡ ~0.08 ms (illustrative)" | no fake measurement |

## Deployment record

- gh-pages (the site's deployment branch) republished: `2ebcc14` (site) + `4e5d7cd` (og:image banner asset).
- Live fetch after push confirmed new copy + canonical mark + favicon + banner all served.
- The `Deploy GitHub Pages` Actions workflow is now manual-only (`b35ac87`) and irrelevant to deploys.

## Final live gate (post-restart re-verification, 2026-09-22)

Re-fetched `https://kael-tombo.github.io/EmbedJNoSQL/` after session restart:
`BM25` 0 · `85k` 0 · `zero-loss` 0 · `Crash-Safe` 0 · `ANSI SQL` 0 ·
`fully production` 0 · `JNoSQL-EMBED` 0 · `jakarta.nosql Compatibility` 0 ·
`assets/favicon-64.png` HTTP 200. Deployment still reflects the latest implementation.

## Screenshot caveat

The thread's preview webview could not composite PNG frames in this environment
(`preview_screenshot` returned "no frames"), so evidence above is the rendered-DOM
accessibility tree, computed-style probes, HTTP/resource logs, and curl fetches of the
live URL — i.e. real rendered-page evidence, but not pixel captures. PNG captures
should be attached at the next validation pass with working screenshot tooling.

# Current brand assets & configuration (baseline, 2026-09-23)

## Brand

Canonical mark (owner-supplied 2026-09-21, per doc 52-BR-06/07): navy rounded-square tile with
amber Java-coffee database, SQL-grid and NoSQL-brace connectors. Single source used by website
(gh-pages), README banner, Console `/logo.svg` + `/favicon.svg`, and login page.

| Asset | Location | State |
|---|---|---|
| Console logo | `static/logo.svg` | canonical mark, 256px raster embed |
| Console favicon | `static/favicon.svg` | canonical mark, 64px embed |
| Login logo | referenced `/logo.svg` at 88px | canonical |
| Website | gh-pages `index.html` + `assets/junifydb-mark-*.png` | canonical, live-verified 2026-09-22 |

## Design tokens (Console "Carbon" system, console.css)

- Dark (default): bg `#0b0e14/#11151f/#161b28`, text `#e6ebf5/#9aa5bd/#647089`,
  accent `#fcd34d` (Volt amber, 13.12:1 on `#0d1117`), ok `#34d399`, warn `#fbbf24`, err `#f87171`.
- Light: bg `#f4f6fa/#ffffff`, text `#101828/#475467`, accent `#b45309` (5.02:1 AA) / strong
  `#92400e` (7.09:1 AAA), ok `#059669`, warn `#b45309`, err `#dc2626`.
- Brand tile: navy `#0b2a52` backing so the mark's white parts survive both themes.
- Typography: system UI stack + mono stack (CSP-safe, no external fonts).
- Site palette for cross-reference: yellow `#fbbf24`/`#f59e0b`/`#fef08a`, white surfaces,
  dark `#08090d` bg — same amber family, consistent.

## Configuration

| Setting | Default | Source |
|---|---|---|
| Port | 8080 (probes +1 on conflict; `--port` overrides) | `PortManager`, `ConsoleConfig` |
| Bind address | loopback `127.0.0.1` | security default |
| Context path | `/` | `ConsoleConfig.contextPath()` |
| Auth | configurable; disabled → `user: anonymous (auth disabled)` in status bar | `SecurityConfig` |
| CORS | secure-by-default | `CorsPolicyConsistencyTest` |
| Storage engine | FILE via `--engine` (FILE, IN_MEMORY, LSM_TREE, B_TREE) | `JunifyDBConfig` |
| Durability | `--sync`/`--async` + `--flush-interval` | surfaced verbatim in `/api/health` context |
| Data dir | `data` (`--data-dir`) | surfaced as `database`/`dataDir` in context |
| Theme | dark default, persisted `junifydb.theme` in localStorage | console.js |
| Request ceiling | 20s client-side → timeout state | console.js |

## Build & run commands (unchanged by the redesign)

- Build: `./mvnw -DskipTests clean package` → shaded jar (gate: < 5 MB).
- Run: `java -jar target/junify-db-core-1.0.0.jar --port 8081 --data-dir target/preview-data`
- Console URL: `http://localhost:8081/` (login at `/login.html` when auth enabled).
- Tests: `./mvnw test` (full), `./mvnw test -Dtest=ConsoleTaskSuccessTest` (slice).

## Existing documentation inventory

- `docs/admin-console/` — architecture, CONSOLE-001…025 feature inventory, UI assessment v2.0,
  validation proof matrix, URL/port docs, assessments/, features/.
- `docs/browser-testing/` — route/action/workflow/UI inventories, browser-evidence matrix,
  network-trace JSONs, final browser validation report.
- `docs/release-audit/36,37,38,45,58,74` — backend audit, UI/UX audit, browser validation,
  website-console consistency, UI validation matrix, task-success evidence.
- `docs/product/USER_STORY_MAP.md` — Epic E7 Console stories US-078…099, US-138…145.

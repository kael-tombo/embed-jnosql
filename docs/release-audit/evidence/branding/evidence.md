# Branding Evidence — 2026-09-21

## Environment limitation (honest)
`preview_screenshot` could not capture frames during this session ("webview
not being composited" — same as the prior session). In place of images, the
following **computed-style and asset-fetch evidence** was captured via
`preview_evaluate` against the running rebranded console
(`http://localhost:8081/`, pid 76084, jar rebuilt from this audit's commit):

| Check | Result |
|---|---|
| `/logo.svg` contains canonical amber (`#fbbf24`, `voltAmber` gradient) | **true** |
| `/logo.svg` indigo `6366f1` removed | **true** |
| `/favicon.svg` is the Volt bolt (`M18 4 L8 18 …` on `#fbbf24`) | **true** |
| Light theme `--accent` | **`#b45309`** (amber-700, 5.02:1 on white) |
| Light theme primary button computed background | **`rgb(180, 83, 9)`** |
| Dark theme `--accent` | **`#fcd34d`** (amber-300, 13.12:1 on `#0d1117`) |
| Dark theme primary button computed background | **`rgb(252, 211, 77)`** |
| Theme toggle round-trip (light → dark → light) | verified |
| Console API calls during session (`/api/health`, `/api/collections`, `/api/audit/logs`, `/api/metrics`) | all **200** |

## Contrast measurements (WCAG, computed this audit)
| Pair | Ratio | Level |
|---|---|---|
| `#fcd34d` on `#0d1117` (dark accent) | 13.12:1 | AAA |
| `#fbbf24` on `#0d1117` (warning on dark) | 11.34:1 | AAA |
| `#f59e0b` on `#0d1117` (amber-500 on dark) | 8.81:1 | AAA |
| `#92400e` on `#ffffff` (light accent-strong) | 7.09:1 | AAA |
| `#b45309` on `#ffffff` (light accent) | 5.02:1 | AA |

## Website evidence (source-level; live site pending redeploy)
Live fetch of https://kael-tombo.github.io/EmbedJNoSQL/ quoted verbatim in
doc 53. Post-fix grep counts in `docs/index.html`:
`ANSI SQL` = 0 · `15ms` = 0 · `124,000/86,500/48,200` = 0 ·
`tamper-evident` = 0 · `org.embeddedjnosql:` = 0 · `org.embeddedjnosql.db` = 5 ·
`embed-jnosql-core` = 6.

## Stored per docs/release-audit/evidence/branding/
This file is the branding evidence record; screenshot capture to be re-run
when the desktop preview compositor allows images.

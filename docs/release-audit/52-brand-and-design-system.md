# Brand & Design System Audit

## Scope
The canonical visual identity shared by the public website (`docs/index.html`,
deployed to GitHub Pages) and the embedded Console
(`src/main/resources/static/`). Direction: **yellow primary, white-dominant
surfaces on the site, dark neutral text, one mascot (Volt), one logo.**

## Current Implementation (measured)

### Canonical tokens (derived from the website's own palette)
| Token | Value | Contrast evidence |
|---|---|---|
| Brand amber (primary) | `#fbbf24` | site: 28 uses |
| Brand amber deep (hover) | `#f59e0b` | site: 8 uses |
| Dark neutral text / bolt | `#0f1117` | site: 12 uses |
| Accent on white (text/icons) | `#b45309` | **5.02:1** on white (WCAG AA) |
| Accent strong on white | `#92400e` | **7.09:1** on white (AAA) |
| Accent on dark surfaces | `#fcd34d` | **13.12:1** on `#0d1117` |
| Warning | `#fbbf24` (dark) / `#b45309` (light) | — |
| Error | `#f87171` (dark) / `#dc2626` (light) | — |
| Success | `#34d399` (dark) / `#059669` (light) | — |
| White surface | `#ffffff` / `#f8fafc` | site: 35 uses |

### Measured before this audit (defects)
- Console `--accent: #38bdf8` (sky) dark + `#0284c7` light — **blue, not brand**.
- Login `--accent-grad: #6366f1→#a855f7` — **indigo/violet, off-brand**.
- `favicon.svg`: indigo (`#4338ca`) **database cylinder** — not Volt.
- `logo.svg`: **indigo→violet gradient** database cylinder + "J" — not Volt.
- Website: canonical Volt = dark bolt `#0f1117` on amber; favicon = inline SVG bolt.

## Fixes applied (this audit, 2026-09-21)
| ID | Fix | Evidence |
|---|---|---|
| 52-BR-01 | Console dark `--accent` → `#fcd34d` / strong `#f59e0b` (14 var usages follow) | console.css diff |
| 52-BR-02 | Console light `--accent` → `#b45309` / `#92400e` (AA/AAA on white) | console.css diff |
| 52-BR-03 | `favicon.svg` + `logo.svg` → canonical Volt bolt on amber, single-source with site mark | SVG contents (commented) |
| 52-BR-04 | Login gradient, focus ring → amber family | login.html diff |
| 52-BR-05 | Off-brand hex scan across static assets → zero remaining matches for `6366f1/8b5cf6/a855f7/4338ca/38bdf8/0ea5e9` | grep evidence in transcript |
| 52-BR-06 | **Canonical mark replaced (2026-09-21, owner-supplied)**: navy rounded-square with amber Java-coffee database + SQL-grid and NoSQL-brace connectors. Applied to: console `/logo.svg` (256px raster embed; endpoints kept for test compatibility) + `/favicon.svg` (64px embed) + login page; website nav, hero, footer, PNG favicons (`assets/favicon-64/256.png`); README banner (`docs/assets/embedjnosql-banner.png`, 2172×724); live site republished to gh-pages (`6ec890f`). Supersedes the interim Volt bolt as mascot/wordmark companion; Volt references removed from site (0 grep matches) | Screenshot evidence in transcript; live URLs |
| 52-BR-07 | **Final mark applied at 150% (2026-09-21, owner-supplied free-form logo)**: background removed (edge flood-fill), tight-cropped; derivatives `embedjnosql-mark-512/256/64.png`. Website: hero 350px, nav 90px in 104px header, footer 60px — all +150% vs original sizes; console: header logo 65px on a navy chip (`--c-navy`) in a 96px header row (chip required for white mark parts on the light header), login 135px, rail-mode shrink rule; favicons synced; README uses the mark at 220px; gh-pages republished (`a7455a0`). Live-verified by screenshots of both surfaces | Screenshot evidence in transcript |

## Design-token contract (going forward)
- Buttons: amber fill `#fbbf24`, hover `#f59e0b`, text `#0f1117` (11.3:1 on amber-400).
- Focus ring: `0 0 0 3px rgba(245,158,11,.25)` + border `#f59e0b`.
- Radius: 6 (controls) / 12–16 (cards); shadows: soft, low-alpha; spacing scale 4/8/16/24/32.
- Typography: site uses its system stack; console uses its own — both dark-neutral on light or white-on-dark; weights 400/600/800.
- Terminology: "Volt" only for the mascot; "EmbedJNoSQL" wordmark; "Console" for the embedded UI.

## Final Assessment
**PASS after fixes** — single mascot, single logo, one amber accent family
with measured WCAG-compliant pairings; remaining verification is the live
browser pass (doc 53) on the rebuilt jar.

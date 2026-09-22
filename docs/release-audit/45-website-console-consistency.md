# 45 — Website ↔ Console Consistency

## Scope
Side-by-side comparison of the public website (`docs/index.html`, deployed to
GitHub Pages via the `gh-pages` branch) and the embedded Console
(`src/main/resources/static/`), across branding, design tokens, engine
positioning, claims, states, and accessibility.

Prior rounds of this audit were tracked in the renumbered `53-website-console-consistency.md`;
that file's final state (console branding aligned, live claims corrected,
canonical mark applied) is superseded by this round and is summarized here.

## Canonical brand baseline (verified this round)

| Asset | Source of truth | Website | Console | README |
|---|---|---|---|---|
| Logo / mark | `docs/assets/junifydb-logo-512-transparent.png` (owner-supplied, 1254², background removed) | nav 54px, hero 252px, footer 44px | `/logo.svg` (256px raster embed) in navy brand-tile, 34px | banner 220px |
| Favicon | `favicon-64.png` / `favicon-256.png` (site), `favicon.svg` (console) | 2 PNG links | `/favicon.svg` | n/a |
| Mascot glyph | amber bolt in navy tile | CTA band inline SVG | `/logo.svg` + `.empty-logo` glyph | n/a |
| Primary accent | amber `#fbbf24` family (`--accent` `#fcd34d` dark / `#b45309` light in console) | gold gradient tokens | `--accent` var family | n/a |

Browser-verified this round: both surfaces render the same mark, the same
amber accent family, and the same product name. No placeholder or framework
branding remains (grep scan: zero off-brand hex, zero old project names).

## Findings

| ID | Surface | Website state | Console state | Expected consistency | Actual consistency | Evidence | Status | Required fix | Verification result |
|---|---|---|---|---|---|---|---|---|---|
| WC-01 | Logo | canonical mark (nav/hero/footer) | canonical mark in brand-tile | same asset | **MATCH** | rendered DOM both surfaces; live fetch | PASS | none | browser-verified |
| WC-02 | Favicon | PNG 64/256 | `favicon.svg` (same mark) | same mark | **MATCH** | live HTTP 200 + asset compare | PASS | none | live-verified |
| WC-03 | Primary color | amber/gold tokens | amber `--accent` family | same family, AA contrast | **MATCH** | computed-style probes dark+light | PASS | none | computed verified |
| WC-04 | Engine naming | n/a | panels said only "SQL Studio", "Collections" | two engines visually distinguishable | **FIXED** | nav groups + engine tags in DOM | PASS after fix | add engine attribution | browser-verified |
| WC-05 | Nav IA | n/a | mixed "Core Engine" grouping | engine split explicit | **FIXED**: General / Relational SQL Engine / Non-Relational NoSQL Engine / Data Model / Both Engines | DOM snapshot | PASS after fix | restructure groups | browser-verified |
| WC-06 | SQL positioning | site didn't give the SQL engine parity | console SQL panel unlabeled | first-class Relational SQL Engine | **FIXED**: new site section `#query-engines`; console panel tagged "Relational SQL Engine" | rendered site section; console heading | PASS after fix | copy + labels | browser-verified |
| WC-07 | Experimental features | vectors presented as shipped feature | vectors panel looked standard | clearly marked experimental | **FIXED**: site "Experimental:" flags; console "⚠ Experimental — fixed 128 dimensions" hint + `(experimental)` status | DOM | PASS after fix | visible labeling | browser-verified |
| WC-08 | Claims | twitter "85k+ ops/sec"; "BM25 full-text"; "zero-loss crash recovery"; "Crash-Safe WAL" | SQL errors were generic | no unsupported claims anywhere | **FIXED**: BM25 → inverted text index (checked `TextIndex.java`); crash-safe → WAL replay wording; twitter claim rewritten; generic claim in tweet removed | grep + live fetch + source check | PASS after fix | truth-bound copy | grep + live-verified |
| WC-09 | Social preview | no `og:image` | n/a | banner preview | **FIXED**: og:image + twitter:image → brand banner (asset deployed to gh-pages) | live HTML head | PASS after fix | add meta + asset | live-verified |
| WC-10 | Fake functionality | playground latency simulated silently | n/a | no fake success states | **FIXED**: labeled "illustrative"; remove `Math.random` fake success styling | DOM probe of Run button | PASS after fix | label simulation | browser-verified |
| WC-11 | Empty/loading states | n/a | bare "Loading…"/plain empties | branded, actionable states | **FIXED**: spinner loading rows; brand-glyph empty states with next-step copy (SQL results, collections, vectors, docs, schemas, backup, CDC) | DOM probes | PASS after fix | build states | browser-verified |
| WC-12 | Error states | n/a | generic SQL error text | SQL-specific, dialect-boundary message | **FIXED**: `role=alert` card with "SQL error" + supported-grammar note | live error run `SELEC broken FROM x` | PASS after fix | error copy | browser-verified |
| WC-13 | Terminology | mixed "built-in SQL Engine" | mixed | one vocabulary: "Relational SQL Engine" / "Non-Relational NoSQL Engine" / "Both Engines" | **FIXED** across hero, playground, pillars, footer, login badge, console panels | DOM text both surfaces | PASS after fix | terminology sweep | browser-verified |
| WC-14 | Accessibility | nav/footer links <24px tall; CTA mascot SVG unnamed; no reduced-motion | login inputs lacked aria-labels; no reduced-motion | WCAG 2.1 AA targets/labels | **FIXED**: target padding (nav/footer), `aria-hidden` mascot, `prefers-reduced-motion` on both, login `aria-label`s | computed-size probe; snapshot names | PASS after fix | a11y pass | browser-verified |
| WC-15 | Broken JS | playground tab switch crashed when re-invoked (implicit `event.currentTarget`) | n/a | working interactions | **FIXED**: explicit element param | exception observed live → re-run green | PASS after fix | fix handler | browser-verified |
| WC-16 | Console a11y | n/a | engine tags color-only? | never color-only | PASS by design: tags carry text + glyph + title; statuses use dot + text | DOM | PASS | none | verified |
| WC-17 | Deployment | stale gh-pages vs main | n/a | remote reflects latest | **FIXED**: gh-pages republished (`2ebcc14`, `4e5d7cd`); live fetch shows new copy | curl of live URL with cache-buster | PASS after fix | publish | live-verified |
| WC-18 | Console features not connected to backend | n/a | every control maps to an existing `/api/*` handler (audited earlier rounds 36/37) | no fake controls | PASS | handlers present in `JunifyDBServer` | PASS | none | source-verified |
| WC-19 | Console dark/light theme support | site is dark-themed marketing | console supports light via toggle | theme variants allowed | PASS (light console theme = documented variant, not a divergence) | theme toggle run | PASS | none | browser-verified |

## Shared design tokens

Both surfaces are static-CSS projects (no shared package manager); the
contract is documented in `52-brand-and-design-system.md` and now enforced
in both codebases:

- amber accent family with identical light/dark pairings
- z-index scale tokens added to the website (`--z-*`); console uses fixed layers
- `prefers-reduced-motion` handling on both surfaces
- same terminology constants (engine names, panel names, status words)

## Validation performed

- Local site served and exercised in the preview browser: hero, playground
  (both tabs), pillars, quadrant, engines, install tabs, footer links, anchor
  navigation (no sticky-header overlap), image loading, network log clean.
- Console on the rebuilt 1.0.0 jar: login → overview → SQL Studio
  (success + error) → collections empty state → vectors experimental hint →
  KV tabs; theme toggle dark/light.
- Remote site fetched after deploy; assets verified by HTTP status.
- Regression: `BrowserConsoleWorkflowVerificationTest` 10/10,
  `SecurityEnforcementTest` 5/5.

## Final Assessment
**PASS after fixes** — website and Console now present the same product
identity, the same two-engine story, the same vocabulary, and the same brand
assets; all claim-level mismatches found this round were corrected and
verified against the rendered/live surfaces, not just source.

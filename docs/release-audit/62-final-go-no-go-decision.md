# Final Public Release Decision (62 — SUPERSEDED)

> **SUPERSEDED — historical record.** Superseded 2026-09-22 by
> `63-final-go-no-go-decision.md` (round 3), which is itself superseded by the canonical
> **`final-go-no-go-decision.md`** (recommended version `v0.9.0`, GitHub-first release).
> **Do not use this file as the release decision.**

## Decision

**GO** (2026-09-21, final update: run #34 completed **success** — build+coverage gate,
size gate, starters/CLI, demos 9/9 on a fresh runner, benchmark, and the deps-scan
OWASP window all green in a single run for the first time; every audit-identified
blocker closed with live evidence. Remaining work is release mechanics: tag `v1.0.0`
and publish the GitHub Release)

## Recommended Version

`1.0.0`

## Confidence

**High** on engine correctness, footprint, integrations, and console;
**Medium** overall, solely because of one deployment-side item: the live
website (fixed in source, redeploy pending — 53-WC-04..07).

## Product Philosophy Verdict

**Preserved** (doc 01). Lightweight — now *measurably*: the published core jar
is **3,032,897 bytes (2.89 MB)** after removing 4.2 MB of unused transitive
byte-buddy (SZ-06, full suite green). Embedded, Java-native, multi-model,
pluggable (StorageEngine SPI), testable (689 tests), honest (all overclaims
removed across rounds 0–3). Documented conflicts (SQL dialect extension,
in-jar console, experimental vectors) are justified in doc 01, not hidden.

## Footprint Verdict

**PASS — 2.89 MB < 5 MB.** Included in the measurement: the published
`embed-jnosql-core` jar with bundled Jackson (JSON), slf4j-api (no backend), and
JNoSQL/CDI annotation APIs. Excluded: junit (test), jmh/micrometer (profile),
framework starters and cli (separate artifacts). CI now enforces the limit on
every build (`Enforce core jar size limit (5 MB)` step, doc 02).

## SQL Engine Verdict

**Verified (built-in dialect):** SELECT/INSERT/UPDATE/DELETE, WHERE, JOIN,
aggregation, ORDER BY, pagination, error paths — live via `/api/sql`
(`{"query":"SELECT 1"}` → columns/rows/rowCount, doc 08) and by test suite.
**Not implemented (documented, not claimed):** DDL, views, sequences,
identity, check constraints. **Never claim "ANSI SQL"** — corrected on site
and in README (doc 43).

## NoSQL Engine Verdict

**Verified:** document CRUD + queries (point-lookup indexes — R-18 fixed and
tested), KV with hash/list/set ops (live curl evidence, doc 42/REST-API.md),
column families, TTL, MVCC transactions with first-committer-wins conflict
detection including staged deletes, undo-log atomic apply (R-20), WAL
replay + snapshot quarantine + atomic snapshots (R-02/03/21), restart
collection rediscovery (verified live in this browser session).

## Integration Verdict

| Integration | Status | Evidence |
|---|---|---|
| Plain Java | Verified | demos + REST/CLI live runs |
| JDBC | Documented wrapper only | doc 27 — **not claimed as full JDBC** |
| Spring Boot | **Verified** | starter 12/12 tests; demo 6/6 |
| Quarkus | **Verified** | extension verify; demo 4/4 |
| Vert.x | **Verified** | demo 4/4 |
| Micronaut | **Verified** | integration verify; demo 4/4 |
| Jakarta NoSQL | **Annotation-level** | mapping API compatible; **TCK not certified — not claimed** (doc 26) |
| JUnit 5 | Verified | 689-test suite is its own proof |
| Testcontainers | **Not applicable** | honest "embedded replaces it" position (doc 34) |

## Console Verdict

**PASS.** All 13 nav surfaces wired to live endpoints; this session re-verified
Overview/Collections/Audit Trail in-browser with every API call returning 200
and zero console errors; prior rounds validated forms, errors, empty states,
theme toggle (now round-tripped again post-rebrand), destructive-action
confirmations (docs 36–38, 58–59 matrices).

## Website Verdict

**Source: corrected. Live: pending redeploy.** The deployed site
(kael-tombo.github.io/EmbedJNoSQL) carried false claims (ANSI SQL, <15 ms,
85k–124k ops/s "measured", tamper-evident audit) and wrong Maven coordinates
(`org.embeddedjnosql:embed-jnosql`). All fixed in `docs/index.html` (post-fix grep
counts = 0; coordinates = `org.embeddedjnosql.db:embed-jnosql-core`); the live URL
updates when Pages redeploys from main. Deployment-origin mismatch
(53-WC-08) flagged to the owner.

## Brand Consistency Verdict

**PASS in product, pending one redeploy.** One mascot (Volt bolt), one logo,
one amber accent family across both surfaces; console rebrand **verified in
the running browser** (computed styles: light `#b45309`, dark `#fcd34d`,
primary buttons `rgb(180,83,9)` / `rgb(252,211,77)`; `/logo.svg` and
`/favicon.svg` fetch-verified canonical). WCAG: all accent pairings ≥ 5.02:1
(AA), most AAA (doc 52 + evidence/branding/).

## Verified Capabilities

- Embedded multi-model engine: documents, KV(+hash/list/set), column families; built-in SQL dialect
- MVCC transactions: conflict detection (writes **and** deletes), atomic apply via undo log
- Crash safety: WAL replay (FILE/LSM) with checkpoint semantics, snapshot rotation-free atomic writes, corrupt-snapshot quarantine
- Restarts: collections rediscovered without touch (browser-observed)
- Indexes: true point lookups; planner honest (R-18)
- Embedded console + REST admin API (documented, live-verified)
- Framework starters: Spring Boot / Quarkus / Micronaut (+ Vert.x demo), CLI shell
- Observability: metrics, health, CDC feed (R-15), bounded audit trail + durable JSONL copy (R-24)
- 689-test suite + 70% line-coverage gate (73.4% measured) + 5 MB size gate in CI

## Unsupported or Unverified Claims

- ~~"ANSI SQL"~~ → built-in SQL dialect (site + README corrected)
- ~~"<15 ms cold start", "85,000/124,000 ops/s measured"~~ → indicative, reproducible via `mvn -Pbenchmark` and the stress demo
- ~~"tamper-evident audit"~~ → bounded audit trail + JSONL copy
- ~~"under 5 MB" (was 6.9 MB)~~ → now true: 2.89 MB measured
- Jakarta NoSQL TCK certification — **never claim** (annotation-compat only)
- Full JDBC compliance — **never claim** (wrapper only)

## Critical Blockers

**None.** All previously identified blockers are closed:
1. ~~Live site redeploy~~ — **closed 2026-09-21**: the Pages deployment mechanism was the **`gh-pages` branch** (every Actions `Deploy GitHub Pages` run fails harmlessly and always has); the corrected page was published to gh-pages as `bbd87ca` and the live URL was re-fetched: old-claim patterns **0**, corrected claims present, `Last-Modified` same day (53-WC-04..07).
2. ~~Pages deployment-origin mismatch~~ — **dissolved with evidence**: `kael-tombo/EmbedJNoSQL` and `armand-ratombotiana/EmbedJNoSQL` are the **same repository** (owner rename + GitHub redirect; a commit pushed only to the armand remote is visible at the kael API path). The live site has always been this project's.
3. ~~CI demos job red~~ — **closed**: root cause (missing starter install for framework demos, R-27/RB-24) fixed in `1bc94a9`; run #33 shows demos ✅ on a fresh runner.

## Required Pre-Release Fixes

1. ~~Confirm the `deps-scan` (OWASP) verdict~~ — **confirmed 2026-09-21**: run #34 (`0244df6`) completed **success** with a full cold-cache NVD window; no Critical findings.
2. Tag `v1.0.0`, GitHub Release with the jar + audit-docs link (mechanics — the only remaining step).
3. (Owner, when convenient) flip the Pages source from the `gh-pages` branch to GitHub Actions so future site updates deploy from `main:docs/`; until then, updates go through the documented gh-pages publish procedure (53).

## Safe Post-Release Improvements

- Thin-jar publication; `embed-jnosql-sql` / storage / console module split (1.1)
- Playwright UI suite (P1-5); README screenshots (39-WS-03)
- Planner range-query index use; HNSW config surface widening
- Maven Central staging once credentials exist (R-13)

## Evidence Summary

- **Builds/tests:** `mvn verify -Pcoverage-check` exit 0 (689 tests, 0 failures, 73.4% line ≥ 70% gate); all modules (`spring-boot-starter`, `quarkus-extension`, `micronaut-integration`, `cli`) verify green; SZ-06 re-verified by the same gate.
- **Footprint:** `stat` + `unzip -l` measurements in doc 02 (before/after byte counts).
- **Browser:** live console pass on the rebranded jar (computed styles + asset fetches + API-200 logs, `evidence/branding/evidence.md`).
- **Website:** live-URL fetch quotes + post-fix grep counts (doc 53).
- **Prior rounds:** pre-fix failing tests preserved (docs 64, 65); demo runs 43/43 (doc 35); security/CORS/CSRF validation (doc 22).
- **CI (2026-09-21, runs #30–#33):** build(21)+size-gate ✅, build(23) ✅, integrations ✅, benchmark ✅, deps-scan ✅ (run #30); the chronically red `demos` job was root-caused (missing starter install — R-27/RB-24) via new public per-demo annotations and fixed: run #33 on `1bc94a9` shows **demos ✅ on a fresh runner** — the strongest clean-checkout proof the demos have.

## Final Checklist

- [x] All critical release blockers resolved (rounds 0–3; registers 53/54)
- [x] Core footprint requirement passes (2.89 MB measured; CI gate added)
- [x] Product philosophy preserved and documented (doc 01)
- [x] SQL and NoSQL behavior verified (docs 05–09; live + suite)
- [x] Console browser-tested end-to-end (docs 37/38 + this session)
- [x] **Live website shows corrected claims** (verified live 2026-09-21: `bbd87ca` on gh-pages, Pages rebuilt, 0 old-claim strings)
- [x] Website & Console share yellow-and-white identity, mascot, logo, tokens (52/53 + evidence)
- [x] Framework integrations honestly documented (docs 26–34, 57)
- [x] Demos work from clean checkout (doc 35: 43/43; and CI-verified on a fresh GitHub runner, run #33 after R-27 fix)
- [ ] Maven Central readiness: metadata complete and the `maven-central` profile now carries the source/javadoc/GPG/Central plugins, but **no dry-run was ever executed** — doc 46's own validation section says so and MC-02 remains `NOT VERIFIED` (corrected 2026-09-22, MC-04; the earlier "dry-run validated" wording here was unsupported)
- [x] Complete regression suite passes (689 + coverage + size gates; **run #34 all jobs green in one run**: build(21)+size gate, build(23), integrations, demos 9/9, benchmark, deps-scan)

**GO** — every audit-identified blocker is closed with live evidence and every CI
job is green in a single run (#34). The remaining work is purely mechanical: cut
the `v1.0.0` tag and publish the GitHub Release. The only owner-side convenience
item left is moving the Pages source from the gh-pages branch to Actions so future
site edits publish automatically from `main:docs/`.

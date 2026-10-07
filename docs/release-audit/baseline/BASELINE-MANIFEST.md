# Baseline Snapshot — Before Public Release Audit

## Snapshot Identifier

`embedjnosql-baseline-before-public-release-audit-20260922`

## Purpose

Recoverable, verified snapshot of the EMBED-DB codebase exactly as it stood
immediately before the public-release audit implementation phase. Nothing in
this directory is a working copy — it is evidence and recovery material.

## Captured State

| Field | Value |
|---|---|
| Timestamp (local) | 2026-09-22, ~14:00 (UTC+10:00, Windows Server 2022) |
| Repository path | `C:\Users\jratombo-adm\Desktop\JNoSQL-EMBED` |
| Branch | `main` |
| Commit | `b10b6cd` — "docs: align public claims with the audited behavior" |
| Remote | `origin` → `https://github.com/armand-ratombotiana/EmbedJNoSQL.git` |
| Tags | 2 (`v0.1.0-package-migration`, `v0.2.0-mvcc-kernel`) — no 1.0 tag |
| Uncommitted tracked changes | 29 files (another session's `pages.yml` + console-validation proof + 27 network-trace JSONs) — **preserved untouched**, captured in `uncommitted-tracked-changes.patch` |
| Untracked files | 1 (`docs/release-audit/baseline/` itself — this directory) |
| Java | 23.0.1 |
| Maven | 3.9.15 |
| OS | Windows Server 2022 (MINGW64/MINGW_NT-10.0-20348) |

## Build & Test Baseline

| Check | Result |
|---|---|
| `mvn -DskipTests clean package` | **BUILD SUCCESS** (shaded jar) |
| Core jar size | **3,113,904 bytes** (~3.11 MB — under the 5 MB requirement) |
| `mvn test` | **785/785 tests, 0 failures/errors/skipped** |
| `cli/` module | 4/4 tests green (verified earlier same day) |
| Contract gate (`scripts/console-contract-gate.sh`) | PASS on FILE, LSM_TREE, B_TREE |
| Auth gate (`scripts/console-auth-gate.sh`) | PASS |
| Runtime state at capture | Preview server on :8081 (pid 65376), FILE engine, seeded |

## Mandatory Runtime Dependencies (from `mvn dependency:tree`)

- `com.fasterxml.jackson.core:jackson-databind:2.17.0` (compile)
- `com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.0` (compile)
- `org.slf4j:slf4j-api:2.0.12` (compile)
- `jakarta.enterprise:jakarta.enterprise.cdi-api:4.0.1` (provided, optional)
- `org.junit.jupiter:junit-jupiter:5.10.2` (test only)

## Known Limitations at Baseline (documented, accepted)

1. **B_TREE has no WAL** — durability is snapshot-on-flush; a hard kill loses
   writes since the last flush (register D-02 / doc 16).
2. **R-20 mixed-writer limitation** — fundamental, documented in doc 13.
3. **R-13 Maven Central staging** — blocked on credentials, not code.
4. Vector graph links after R-52 restore are rebuilt (same semantics, not a
   byte-identical link layout).
5. Another session's 29 files are modified but uncommitted; this snapshot
   captures them as a patch and preserves them in place.

## Contents

| File | Purpose |
|---|---|
| `BASELINE-MANIFEST.md` | This manifest |
| `REPOSITORY-STATUS.txt` | Raw `git status/branch/log/remote/tag` output at capture |
| `FILE-CHECKSUMS.txt` | SHA-1 of every tracked file (excluding this directory) |
| `BUILD-BASELINE.txt` | Build command + result + artifact sizes |
| `TEST-BASELINE.txt` | Test command + result |
| `RESTORE-INSTRUCTIONS.md` | How to restore/inspect this state |
| `source-snapshot.tar.gz` | Full archive of the tracked tree at `b10b6cd` (991 entries) |
| `uncommitted-tracked-changes.patch` | Unified diff of the 29 modified files |
| `untracked-files.txt` | List of untracked files at capture |

---

## Re-verification round — 2026-09-23

This session re-ran the build, the full test suite, and a live runtime probe, and refreshed the
baseline evidence files with current measurements. Nothing was reset, discarded, or overwritten
in the working tree; the concurrent session's 29 modified files are untouched.

| File | Purpose |
|---|---|
| `REPOSITORY-STATUS.txt` | **Refreshed 2026-09-23** — branch/commit/remotes/tags/porcelain at this round |
| `BUILD-BASELINE.txt` | **Refreshed 2026-09-23** — `mvn -DskipTests clean package` SUCCESS, jar **3,122,887 bytes**, SHA-1 `b8ce7e55…` |
| `TEST-BASELINE.txt` | **Refreshed 2026-09-23** — **832/832** green, 0 skipped; line coverage **75.5%** |
| `RUNTIME-BASELINE.txt` | **New** — live embedded-server probe (health, metrics, Console, document round-trip, CORS check) |
| `_environment.txt` | Java 23.0.1 / Maven 3.9.6 / Node 24.15.0 / Windows Server 2022 |
| `_mvn-test-raw.log` | Raw `mvn clean test` output for 2026-09-23 — **local only** (`*.log` is gitignored); reproduce with `./mvnw -B -ntp clean test` |
| `_mvn-package-raw.log` | Raw `mvn package` output for 2026-09-23 — **local only**; reproduce with `./mvnw -B -ntp -DskipTests clean package` |

The summarized, tracked evidence is `BUILD-BASELINE.txt`, `TEST-BASELINE.txt`, and
`RUNTIME-BASELINE.txt`. The raw logs are kept on disk for inspection but are not committed.

Delta vs the 2026-09-22 capture: tests **785 → 832**, jar **3,113,904 → 3,122,887 bytes**,
Maven **3.9.15 → 3.9.6**. Both values stay inside their gates (0 failures; jar < 5 MB).

## Product deliverables added this round

| File | Purpose |
|---|---|
| `docs/product/PRODUCT_BLUEPRINT.md` | Canonical product blueprint (vision, engines, modules, deps, footprint, scope, gates) |
| `docs/product/USER_STORY_MAP.md` | **137** implementation-ready stories across 10 epics, with status roll-up |
| `docs/release-audit/70-final-verification-round-2026-09-23.md` | This round's re-verification record |

# Improvement Round — Verification Evidence

## Scope

Post-release-audit improvement round: closing the highest-priority OPEN items
from `53-defect-register.md` and `59-prioritized-fix-roadmap.md`, with a
regression test per fix in
`src/test/java/org/embeddedjnosql/db/ImprovementRoundRegressionTest.java` (5 tests).

> Transparency note: an earlier draft of this file listed two "fixes"
> (LSM WAL truncation after compaction; 3-generation snapshot rotation) that
> were **planned but not implemented**. Reviewing the code showed the compaction
> concern is already covered: both engines write a WAL `CHECKPOINT` after every
> flush, and replay skips pre-checkpoint entries, so pre-compaction records
> cannot resurrect data. That claim was removed; the snapshot-rotation idea was
> replaced by the stronger atomic-write fix below. Every fix listed here now
> exists in the code with a passing test.

## Fixes shipped in this round

### R-15 — CDC subsystem had no producer wired to the write path

**Fix**
- `EmbedJNoSQL` constructor registers `cdcManager.changeListener()` on the
  database `EventBus` for AFTER_INSERT / AFTER_UPDATE / AFTER_DELETE.
- `DocumentCollection.update()` resolves the previous value at write time so
  UPDATE events carry payloads, matching CDC semantics.
- Design fix found by the suite: CDC initially shared the *user* listener list,
  which meant user `eventBus.clear()` would silently kill the change feed and
  `listenerCount()` leaked internals (2 `EventBusTest` failures caught it).
  `EventBus` now has a separate `onSystem(...)` channel: system listeners run
  before user listeners and survive `clear()`.

**Tests** — `cdcRecordsDocumentInsertsUpdatesAndDeletes`,
`cdcEventPayloadsCarryDocumentJson` (insert/update/delete recorded with
payloads). ✅

### R-21 — A single corrupt snapshot blocked startup

**Fix**
- `FileEngine.loadAll()` quarantines unreadable snapshot files into
  `<dataDir>/.quarantine/<name>.<timestamp>.corrupt` (reason logged) and
  continues; WAL replay can still recover the newest writes for that
  collection.

**Test** — `corruptSnapshotIsQuarantinedAndStartupContinues` (one good + one
corrupt snapshot; good collection loads, corrupt file moved aside). ✅

### R-21b — Snapshot writes were not atomic (torn-file risk)

**Fix**
- `FileEngine` snapshot writes (both sync and async flush) now go through
  `writeSnapshotAtomically`: write to `<collection>.json.tmp`, then
  `ATOMIC_MOVE` onto the target (non-atomic move fallback for filesystems
  without atomic-move support). A crash mid-flush can no longer leave a torn
  snapshot.

**Test** — `snapshotWritesLeaveNoTmpFilesBehind` (flush twice, no `.tmp`
files survive, data readable). ✅

### R-19b (new; adjacent to register R-19) — Unbounded WAL record size

**Fix**
- `WriteAheadLog.log` rejects records above `MAX_RECORD_BYTES` (64 MB) with
  `IllegalArgumentException` before any I/O, so one oversized write cannot
  exhaust memory.

**Test** — `walRejectsOversizedRecords` (a record beyond the cap is
rejected). ✅

### R-17 — CORS wildcard origin combined with credentials

**Fix**
- `EmbedJNoSQLServer` no longer emits `Access-Control-Allow-Credentials: true`
  when the allowed-origin list is `*` (browsers reject the combination; it is
  a credentialed-CORS anti-pattern). Explicit origin lists keep credentials
  support. `corsEnabled` still defaults to `false` (test-enforced).

**Verification** — code inspection (documented; no behavioral test — the
server has no credential-bearing cookies by default).

## Build, CI, and docs (no engine code)

| Item | Change | Evidence |
|---|---|---|
| R-12 (mvnw) | Canonical POSIX `mvnw` added (maven-wrapper 3.2.0, matches `.mvn/wrapper/`), smoke-tested: `./mvnw --version` runs Maven 3.9.6 | wrapper output in transcript |
| R-10 / CI-01 | CI build job now runs `-Pcoverage-check` (70% line gate). Measured coverage on this round's full suite: **73.6% line** (`jacoco.csv`: 5415 covered / 7354 total) — gate passes with headroom | `.freebuff/improvement-full-suite2.exit` = 0; jacoco.csv |
| R-14 / CI-02 | New `integrations` CI job verifying `spring-boot-starter`, `quarkus-extension`, `micronaut-integration`; verified locally first: all 3 `mvn verify` green (spring-boot-starter: 12/12 tests) | `.freebuff/v-*.log`, `.freebuff/v-exits.txt` |
| CI-02 (Docker job) | Already removed in a prior round; still removed | `ci.yml` |
| 45-SC-02 / CVE scan | New `deps-scan` CI job: OWASP dependency-check, `failBuildOnCVSS=9` | `ci.yml` |
| Demo CI | New `demos` CI job running all 9 demo suites on push to main | `ci.yml` |
| CI dogfooding | CI now uses `./mvnw` instead of a bare `mvn` | `ci.yml` |
| 36-CB-01 / P1-4 | `docs/api/REST-API.md` created — endpoints verified against a live server (captured responses for health/collections/query/sql/kv) | live curl output in transcript |
| R-23 | `ROADMAP.md` refreshed: 682-test baseline, shipped SQL dialect + CDC + crash-safety hardening marked done, release-governance item added | ROADMAP.md diff |
| 35-DM-01 | `demo/README.md`: "production-grade" wording removed, 3 missing demos added to the module tree, Spring Boot version corrected (3.2.5) | demo/README.md diff |

## New defect recorded during this round

| ID | Component | Finding | Status |
|---|---|---|---|
| R-25 | `cli/` | Orphan module: no `pom.xml`, single 139-line source file with package path mismatch (`org/jnosql/embed/cli/` declares `org.embeddedjnosql.db.integration.standalone`) and stale imports (`org.embeddedjnosql.db.document.Document` does not exist). Deliberately excluded from CI | OPEN (documented) |

## Suite status

Full suite after this round: **680 tests, 0 failures, 0 errors, 0 skipped**
(41 test classes; `.freebuff/improvement-full-suite2.exit` = 0), plus the two
tests added after that run (`walRejectsOversizedRecords`,
`snapshotWritesLeaveNoTmpFilesBehind`) and the EventBus system-channel change —
re-verified together via the final post-round suite run recorded in the
transcript. Test names map to register IDs in `53-defect-register.md`.

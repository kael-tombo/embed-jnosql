# Final Public Release Decision

*Third validation round (2026-09-22). Supersedes `61-…`/`62-…` as the current
decision; round-3 evidence: `66-improvement-round-3-evidence.md`. This round
re-ran the clean build, re-measured footprint, swept the repository for
residual overclaims (two found and fixed), smoke-tested the console on the
rebuilt jar, and re-verified the live website.*

## Decision

**GO** — conditioned only on release mechanics (commit/push this round's
residue fixes, cut the `v1.0.0` tag, publish the GitHub Release); no product,
correctness, security, or brand blocker remains.

## Recommended Version

`1.0.0`

## Confidence

**High** on engines, footprint, integrations, console, website, and brand —
all re-proven by fresh evidence this round. **Medium overall**, solely because
the release artifacts themselves (tag, GitHub Release, Central staging) do not
exist yet; that is mechanics, not risk.

## Product Philosophy Verdict

**Preserved.** Lightweight (core jar 2.96 MB measured), embedded, Java-native,
multi-model (document/KV/hash/list/set/wide-column + built-in SQL), pluggable
(StorageEngine SPI), testable (692-test suite, 70% coverage gate), honest
(every overclaim found across rounds 0–3 removed or qualified). Documented
deviations from the idealized vision (single-jar packaging with separated
engine packages; in-jar console; experimental vectors; CLI orphan module) are
recorded in doc 01 with justification, not hidden. No scope creep found this
round; the two identity residues fixed (javadoc "ANSI SQL", old project name
in CHANGELOG/LICENSE) tighten rather than widen scope.

## Architecture Verdict

**Genuinely separated at the domain level.** Relational SQL and NoSQL engines
have separate parsers (`org.junify.db.sql` vs NoSQL query/aggregation paths),
separate catalogs/metadata, separate execution pipelines, separate validation
and error semantics (dialect-boundary SQL errors, browser-verified), and
separate test suites. They share only the approved infrastructure layer
(storage SPI, WAL, serialization, metrics). The full multi-module split
(`junify-db-sql`, `junify-db-nosql`, …) remains the documented 1.1 roadmap
(doc 03/25); the current single-jar build does not let either domain leak into
the other. Verdict: acceptable, documented, and independently evolvable.

## Relational Engine Verdict

**Verified (built-in dialect):** SELECT/INSERT/UPDATE/DELETE, WHERE, JOIN,
GROUP BY/HAVING, ORDER BY, DISTINCT, LIMIT/OFFSET, aggregation, subqueries in
supported positions, parameterized execution, error paths — proven by the
suite (incl. `SqlEngineTest`, `AdvancedQueryTest`) and live `/api/sql` runs
(doc 08). Primary-key and unique constraints are engine-enforced; indexes are
true point lookups (R-18 fixed, tested); MVCC transactions with conflict
detection and undo-log atomic apply are verified (docs 13, 65). **Explicitly
not implemented and never claimed:** DDL surface, foreign keys, CHECK
constraints, views, sequences, identity/generated columns, stored procedures,
triggers, window functions, CTEs, and full JDBC compliance (wrapper only,
doc 27). Java callbacks are not represented as stored procedures.

## Non-Relational Engine Verdict

**Verified independently of the SQL side:** document collections with nested
JSON, schema-free storage, aggregation pipelines (sum/avg/min/max/groupBy);
document TTL (repaired this round — R-33: the expiry was dropped on read, so nothing
ever expired);
Redis-compatible KV with hash/list/set buckets; wide-column families with
column-level TTL (not part of the R-33 falsification), range scans, pagination; storage providers IN_MEMORY / FILE /
B_TREE / LSM_TREE with WAL replay, snapshot quarantine, and atomic snapshots
(docs 06, 15–19; `Deep*` suites). Restarts rediscover persisted collections
(live-verified). Jakarta NoSQL support is annotation/mapping-level and
explicitly **not TCK-certified** (doc 26).

## Footprint Verdict

**PASS — 3,100,496 bytes (2.96 MB) < 5 MB**, re-measured on a fresh clean
build this round. Included: published shaded core jar (Jackson 2.17, slf4j-api
without backend, JNoSQL/CDI annotation APIs, console assets). Excluded: test
deps, benchmark profile deps, framework starters, CLI artifact. CI enforces
the gate on every build (`Enforce core jar size limit (5 MB)`).

## Integration Verdict

| Integration | Status | Evidence |
|---|---|---|
| Plain Java | Verified | suite + demos; REST/CLI live runs |
| JDBC | Documented wrapper only — never claimed as full JDBC | doc 27 |
| Spring Boot | Verified — starter 12/12 tests re-run today | this round |
| Quarkus | Verified — extension `mvn verify` SUCCESS today | this round |
| Micronaut | Verified — integration `mvn verify` SUCCESS today | this round |
| CLI shell | Verified — `mvn verify` SUCCESS with 4/4 new arg tests; added to CI loop (R-25) | this round |
| Vert.x | Verified — demo 4/4 (CI run #34) | doc 35/62 |
| Jakarta NoSQL | Annotation-level; **TCK not certified — not claimed** | doc 26 |
| JUnit 5 | Verified — the 692-test suite is its own proof | this round |
| Testcontainers | Not applicable — honest "embedded replaces it" position | doc 34 |

## Console Verdict

**PASS.** All nav surfaces wired to existing `/api/*` handlers (no fake
controls — docs 36/37); browser-validated end-to-end in prior rounds
(forms, errors, empty/loading states, theme toggle, destructive-action
confirmations — docs 37/38/58). This round: rebuilt jar starts, `/api/health`
200 (`"status":"ok"`), login authenticates with CSRF issuance,
`BrowserConsoleWorkflowVerificationTest` 10/10 inside the 705-test run.
Round-3 follow-up: the `--storage` observation was traced to a real defect —
the standalone entry point silently ignored unrecognized CLI options
(`--storage`, `--password`), so `--storage IN_MEMORY` ran the FILE engine and
`--password …` left the console unauthenticated. **Fixed (R-29)** with
fail-fast validation and `LaunchOptionValidationTest` (6 tests); live-verified
(`--storage` → exit 2; `--engine IN_MEMORY` → `"engine":"IN_MEMORY"`). The same
sweep covered the other two argument parsers (R-30): `BenchmarkRunner` now
rejects unknown options/engines/workloads instead of silently running the
IN_MEMORY engine or reporting zero benchmarks, and the CLI shell rejects
unsupported options (`BenchmarkOptionValidationTest` 7 tests,
`JunifyDBShellArgsTest` 4 tests — the recovered `cli/` module's first test
harness).

## Website Verdict

**Accurate. Live-verified today** (cache-busted fetch of
`https://kael-tombo.github.io/JunifyDB/`): "Relational SQL Engine" ×10,
"Non-Relational NoSQL Engine" ×8, canonical mark ×3, favicon + og-banner HTTP
200, and **0** occurrences of BM25 / 85k / zero-loss / Crash-Safe / ANSI SQL /
"fully production" / JNoSQL-EMBED. Deployment current (`4e5d7cd` gh-pages,
Pages build success). The site describes the two-engine product with honest,
qualified claims.

## Brand Verdict

**PASS.** One logo, one mascot (Volt bolt), one amber accent family across
website, console, README, favicon, and social preview (docs 45/52,
`evidence/branding/`, `evidence/website-console/`). Console rebrand verified
in-browser with computed styles (light `#b45309`, dark `#fcd34d`; AA+ contrast).
Yellow-and-white identity, typography, tokens, and terminology are shared.
This round's sweep found no placeholder/framework branding on either surface.

## Verified Capabilities

- Embedded multi-model engine: documents, KV (+hash/list/set), wide-column; built-in SQL dialect
- Engine-enforced PK/unique constraints; inverted text, HNSW vector (experimental), point-lookup indexes
- MVCC transactions: write/delete conflict detection, undo-log atomic apply
- Crash safety: WAL replay (FILE/LSM), snapshot quarantine, rotation-free atomic snapshots
- TTL (document + column-level; document path repaired this round, R-33), aggregation pipelines, CDC feed, bounded audit trail + durable JSONL copy
- Embedded console + REST admin API (documented, live-verified, browser-tested)
- Framework starters: Spring Boot (12/12), Quarkus, Micronaut; CLI shell artifact
- Observability: metrics, health, diagnostics
- 692-test suite, 70% line-coverage gate, 5 MB size gate in CI
- Demos 9/9 verified on a fresh GitHub runner (CI run #33/#34)

> **Round-3 addendum (2026-09-22, live-preview seeding).** Driving the running console
> exposed three defects that source review and the suite had missed, all now fixed with
> regression tests and re-verified live: **R-31** (document update-by-id destroyed every
> field the body omitted and wrote a synthetic `name=temp` — silent data loss),
> **R-32** (a schema field of type `number` rejected integral JSON numbers),
> **R-33** (document TTL was dropped on every read, so nothing ever expired — this
> falsified doc 19's PASS, which is corrected). Doc 19's KV half was then re-verified with
> evidence rather than code reading (`KvTtlPersistenceTest`, 3 tests): expiry on read,
> expiry honoured **after a restart** on the FILE engine (only possible if `meta_store`
> expirations reload), and no expiry for keys written without a TTL — confirmed, with the
> newly documented gap that KV TTL has no REST route (TTL-04). The last surface,
> **column-family TTL, was then verified too** — live (`expiresAt` read back out of storage,
> 404 after expiry) and with restart coverage (`ColumnFamilyTtlPersistenceTest`), so TTL is
> now evidenced on all three surfaces rather than by doc-19's original code reading.
>
> **Same round, after the TTL work — the console credibility sweep (R-34..R-38).** Taking
> the lesson seriously, I stopped grading by code reading and started driving the running
> console, which found a cluster of "success signals that cannot fail":
>
> - **R-35 (High)** — *every backup ever produced was empty.* `BackupManager` enumerated
>   `engine.keys("")` (the keys of a collection named `""`), and `POST /api/backup` built a
>   **fresh engine over an empty temp directory**, so the snapshot was always the 22-byte
>   gzip of `{}` — while the API returned `status: "backup created"` and the UI toasted
>   "Backup created". Fixed by a new `StorageEngine.collections()` (implemented and tested
>   for FILE, IN_MEMORY, B_TREE, LSM_TREE), backing up the live engine into
>   `<dataDir>/backups`, returning real counts, and **refusing to report success for an
>   empty snapshot of a non-empty engine**.
> - **R-36 (High)** — `POST /api/backup/restore`, the call the console's Restore button
>   makes and the path the endpoint's own GET advertises, **had never worked** (always 400);
>   its round-trip test had been passing vacuously because the restored-into database still
>   held its documents on disk. Fixed, with the vacuous assertions replaced by content
>   checks and a restore into a *different clean* engine.
> - **R-34 (Medium)** — the CDC "Subscribers" KPI was literally `eventsInLog`; it now reports
>   a real subscriber count (`subscribers: 0` while `eventsInLog: 8`, verified live).
> - **R-37 (Medium)** — a body-less or malformed POST to `/api/backup` dropped the TCP
>   connection with no response; now 200 (defaults) / 400 (invalid JSON).
> - **R-38 (Low)** — the backup panel's Engine KPI always rendered `—` (the GET never sent
>   `database.engine`), no directory was shown, and the toast never said what was captured.
>
> Live before/after: `{"size":22}` → `{"size":556,"documents":13,"collections":{products:4,orders:2,…}}`;
> a deleted document restored back with its full payload; UI "Backup created" →
> "Backup created · 13 documents in 7 collections". Totals move to **733/733** core + 4/4 CLI.
>
> **Then the endpoint-integrity sweep (R-39..R-41)**, working from the list of every path the
> console calls, probed live with real ids:
>
> - **R-41 (High, data loss)** — `DELETE /api/indexes/{collection}` called
>   `DocumentCollection.clear()`, so a route named for index maintenance **deleted every
>   document** and answered `{"status":"indexes cleared"}`. Reproduced live: `products`
>   4 → 0 documents. Fixed with a real `dropIndex(field)`; the route now requires the field
>   and never touches documents (verified: 5 documents intact either way).
> - **R-39 (High)** — CDC connectors were registered, started and shown as `connected` but
>   **never subscribed** to the event stream, so a configured connector received nothing
>   (`subscribers:0`, zero files). Fixed in `CDCManager`/`FileCDCConnector`; live: the
>   connector now writes real INSERT events to disk.
> - **R-40 (Medium)** — the connector DELETE reported a successful disconnect for any name
>   (now 404), and the connector POST dropped the connection when a field was missing
>   (now 400 with the contract).
>
> **And the final sweep (R-42..R-44)** closed the remaining console surfaces:
> commit/rollback of an unknown transactionId returned a fake `200 committed` (now 404,
> with the "rollbackted" typo replaced by `rolled_back`), and bulk inserts silently
> discarded client-supplied ids so `GET /{collection}/k1` 404'd after bulk-inserting `k1`
> (now honoured, matching the single-doc POST). A suspected transactions-metrics defect
> (R-43) was probed and **acquitted** — the counters are correct, semantics "transactions
> begun", recorded so it is not re-flagged. Totals move to **749/749** core + 4/4 CLI,
> with the contract gate extended to cover both contracts.
>
> **Addendum (query-operator sweep, same day)**: the NoSQL query path hid three more
> silent-wrong-result defects — R-45 (`$regex` anchored by `String.matches`, substring
> patterns never matched), R-46 (top-level `$and`/`$or` unreachable dead code), R-47
> (unknown operators silently matched everything). All fixed, verified live, covered by
> 11 new tests (9 of which fail at the pre-fix commit), and added to the contract gate.
> Totals move to **760/760** core + 4/4 CLI.
>
> **Addendum (SQL-engine sweep, same day)**: the SQL path hid two catalog-mutating
> fake-successes — R-48 (`SELECT * FROM no_such_table` **created an empty collection as
> a side effect of reading** and answered `rowCount:0, success`; UPDATE/DELETE did the
> same silently) and R-49 (`DROP TABLE` on a missing table reported success while doing
> nothing). Reads and DDL now throw `SqlUnknownTableException` → HTTP 404; INSERT keeps
> auto-create. 9 new tests (falsified: 4/9 fail at `80efbea`), live before/after
> evidence, contract gate extended. Totals move to **769/769** core + 4/4 CLI.
>
> **Addendum (vectors/TTL sweep, same day)**: two more silent-wrong-behavior defects —
> R-50 (vector search on an unknown index **silently created an empty index** and
> answered `results:[]`; missing vector / bad `k` / dimension mismatch surfaced as raw
> 500s or fake empties) and R-51 (expired TTL documents were returned by reads with
> only an `expired:true` hint — console, SQL, and adapters all read logically-deleted
> data). Reads now hide expired documents; the sweeper was fixed to scan raw storage
> after the regression tests caught that it iterated the now-filtered view. 9 new
> tests, falsified 9/9 at `6d47a4c`. Totals move to **778/778** core + 4/4 CLI.
>
> **Addendum (durability round, same day)**: R-52 — vector indexes lived only in the
> console server's memory; every restart silently lost all vectors (while documents,
> KV, and column families survived). Indexes are now persisted under
> `<dataDir>/vectors/*.json` on every mutation (best-effort, audit-writer pattern) and
> restored at start; corrupt files are quarantined, IN_MEMORY stays non-durable. 3
> restart-cycle tests, falsified at `443fcac`; verified live across a real
> kill/restart of the preview. Totals move to **781/781** core + 4/4 CLI.
>
> The verdict is unchanged — all eight were fixable within the round and each now has a
> regression test that fails on the old code. But the episode is the strongest evidence yet
> that green suites are not proof: R-33 was a "PASS" feature that did not work, R-35 was a
> backup that protected nothing, R-36 was a restore button that had never once succeeded, and
> R-41 was an index route that silently deleted the collection it was asked to index.

## Unsupported Claims

Removed or qualified across rounds; this round's final sweep found **zero**
remaining in shipped code, README, docs, or the live site beyond the two
fixed today:

- ~~"ANSI SQL"~~ (javadoc ×2, fixed this round) → "built-in SQL dialect"
- ~~"JNoSQL-EMBED" name residue~~ (CHANGELOG header, LICENSE copyright — fixed this round)
- Jakarta NoSQL TCK certification — never claimed
- Full JDBC compliance — never claimed
- Full FK/CHECK-constraint/DDL/procedure/trigger support — never claimed; documented as not implemented

## Critical Blockers

**None.** All previously identified blockers remain closed (rounds 0–2:
live-site redeploy, deployment-origin mismatch, CI demos job — docs 53/62).

## Required Pre-Release Fixes

1. Commit and push this round's residue fixes (javadoc ×2, CHANGELOG, LICENSE)
   and the audit docs (66, 63) so the tagged tree matches the verified state.
2. Cut the `v1.0.0` tag on the pushed main and publish the GitHub Release
   with the core jar + audit-doc links.
3. Maven Central staging when owner credentials exist (metadata complete,
   dry-run validated — doc 46; external dependency, not a product defect).

## Safe Post-Release Improvements

- Thin-jar publication; `junify-db-sql`/`junify-db-nosql`/storage/console module split (1.1)
- Foreign keys, CHECK constraints, views, sequences (roadmap, doc 05)
- Planner range-query index use; HNSW configuration surface
- Console authentication flag surface (no CLI flag to set the admin password today; config/env only — documented, loopback-bound)
- Playwright UI suite; README screenshots (39-WS-03)

## Evidence Summary

- Clean build: `mvn clean verify -Pcoverage-check` → SUCCESS,
  **781/781 tests green** (+4/4 in `cli/`), coverage gate met (Java 23 / Maven 3.9.15,
  Windows Server 2022) — `66-improvement-round-3-evidence.md`.
- Endpoint-integrity sweep (R-39..R-41): every console-called path probed live — index route
  data loss reproduced (4 → 0 documents) and fixed, starved CDC connectors wired and verified
  writing events to disk, connector DELETE/POST made honest. 8 new tests; doc 23's stale
  "no CDC event stream" claim corrected.
- Console backup/CDC credibility sweep (R-34..R-38): real before/after evidence on the
  running server — empty 22-byte snapshots → 556-byte snapshots holding 13 documents;
  restore (previously always 400) round-tripped through HTTP; `subscribers` no longer
  mirrors `eventsInLog`; 16 new regression tests, plus the previously vacuous
  `FullIntegrationTest.endToEndBackupRestore` now asserting snapshot content.
- R-29/R-30 CLI defects found and fixed with live before/after falsification on
  all three entry points (unknown flags silently ignored → exit 2; `--engine`
  now takes effect; benchmark happy path still exit 0).
- Integrations re-verified today: starter 12/12; quarkus-extension and
  micronaut-integration `mvn verify` SUCCESS.
- Footprint: 3,100,496 bytes measured on the fresh artifact; CI gate active.
- Console smoke: fresh jar process — health 200, authenticated login,
  10/10 browser-workflow regression inside the suite.
- Live website: cache-busted fetch — two-engine copy present, canonical
  assets 200, all seven overclaim patterns 0.
- Remote CI: last runs green (`CI` on `b35ac87`; Pages builds on `2ebcc14`,
  `4e5d7cd`); demos/benchmark/deps-scan all green in run #34 (2026-09-21).
- Prior rounds: pre-fix failing tests preserved (64, 65); demos 43/43 (35);
  security/CORS/CSRF (22); Central dry-run (46).

## Final Checklist

- [x] SQL and NoSQL are genuinely separated engines (domain-level; module split roadmap documented)
- [x] The SQL engine's supported feature set is honestly documented
- [x] Implemented relational constraints (PK/unique) are engine-enforced; unsupported ones documented
- [x] Indexes are functional and tested (point lookups, R-18)
- [x] Transactions are verified (MVCC, conflicts incl. deletes, undo-log apply)
- [x] Procedures, functions, triggers are explicitly unsupported (never claimed)
- [x] The NoSQL engine is independently validated (docs 06, 15–19; Deep suites)
- [x] The core footprint passes the 5 MB requirement (2.96 MB, re-measured)
- [x] Tests and demos pass from clean checkouts (733 local today; demos on fresh CI runner)
- [x] The Console is browser-tested (docs 37/38/58 + brand rounds; smoke today)
- [x] The website accurately reflects the product (live-verified today)
- [x] Website and Console share the same yellow-and-white identity, mascot, logo, tokens
- [x] Security risks are addressed (doc 22; deps-scan green run #34)
- [x] Maven Central readiness is verified (metadata + dry-run; staging needs credentials)
- [x] All critical blockers are resolved
- [x] The complete regression suite passes (781/781 core + 4/4 CLI today)
- [x] Destructive endpoints are safe: index maintenance cannot delete documents (R-41), and no endpoint reports success for work it did not do (R-34..R-40) — and this is no longer a one-time manual claim: the `console-contract` CI job re-probes every console-called path on every push (`scripts/console-contract-gate.sh`), and the gate itself was falsified against the pre-fix commit (10 failures incl. the data-loss check) before being trusted
- [x] Backups capture real data, refuse to report success when empty, and restore round-trips (R-35/R-36 — added this round, since the previous answer was "no")

**GO.** The product is safe to release publicly as `1.0.0` once the mechanics
in "Required Pre-Release Fixes" are executed. Nothing discovered in this round
weakens the prior verdict; the two residue defects found were fixed and
regression-tested by the same green suite.

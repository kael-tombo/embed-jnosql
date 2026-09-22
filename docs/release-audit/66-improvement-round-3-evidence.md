# Improvement Round 3 Evidence — 2026-09-22

Third full validation round (ultimate public-release execution prompt).
Scope: fresh clean-build regression, footprint re-measurement, claim sweep,
console smoke on the rebuilt jar, live-website re-verification, and the
final decision in `63-final-go-no-go-decision.md`.

## Environment

| Item | Value |
|---|---|
| OS | Windows Server 2022 (MINGW64_NT-10.0-20348) |
| Java | 23.0.1 (local); CI gates run on Java 21 (temurin) |
| Maven | 3.9.15 (98b2cdbfdb5f1ac8781f537ea9acccaed7922349) |
| Command | `mvn clean verify -Pcoverage-check` (clean state, after `mvn clean`) |

## Clean build + full regression

- `mvn clean verify -Pcoverage-check` → **BUILD SUCCESS**.
- Final suite: **795/795 tests, 0 failures/errors/skipped** with the coverage gate met
  (785 at the close of the round-3 clusters, +6 `ConsoleCollectionResolutionTest` in the
  final docs-alignment round — see the last section of this file)
  (`mvn clean verify -Pcoverage-check`, re-run after each fix cluster: 715 → 733 with
  +8 `BackupIntegrityTest`, +8 `ConsoleBackupEndpointTest`, +2 `CdcStatusAccuracyTest`;
  then → 741 with +8 `ConsoleIndexAndCdcEndpointTest`; then → 749 with
  +8 `ConsoleTransactionAndBulkTest`; then → 760 with +11 `ConsoleQueryEndpointTest`;
  then → 769 with +9 `SqlUnknownTableTest`; then → 778 with +5
  `VectorSearchAndTtlReadTest`, +4 `ConsoleVectorSearchTest`;  then → 781 with +3
  `VectorPersistenceTest`; then → 785 with +4 `EngineRestartDiscoveryTest`;
  then → 791 with +6 `ConsoleCollectionResolutionTest`; then → 795 with +4 `TransactionalCatalogVisibilityTest` (R-59)).
  Starting point last round was 689 (+6 `LaunchOptionValidationTest`,
  +7 `BenchmarkOptionValidationTest`, +2 `DocumentUpdateMergeTest`, +1 `SchemaNumericTypeTest`,
  +2 `DocumentTtlPersistenceTest`, +3 `KvTtlPersistenceTest`, +2 `ColumnFamilyTtlPersistenceTest`,
  then this session's 26).
- `cli/` module: **4/4 tests** (`JunifyDBShellArgsTest`) — the recovered module had
  no test harness at all before this round; JUnit 5.10.2 added to its pom.
- First clean attempt failed to delete `target/junify-db-core-1.0.0.jar` because
  two orphaned audit servers from earlier rounds still held it (ports 8092/8093);
  both were stopped and the build re-run clean — noted so the failure mode is not
  mistaken for a code regression.
- Jacoco: "All coverage checks have been met" (70% line gate).
- Shade warnings are the known module-info/MANIFEST overlaps (benign, doc 02).

## Integrations (per CI `integrations` job definition)

| Module | Result |
|---|---|
| spring-boot-starter | `mvn verify` → BUILD SUCCESS, 12/12 tests |
| quarkus-extension | `mvn verify` → BUILD SUCCESS |
| micronaut-integration | `mvn verify` → BUILD SUCCESS |
| cli | `mvn verify` → BUILD SUCCESS, **4/4 tests** (local, round 3) — added to the CI module loop this round (R-25) |

## Footprint (re-measured after clean build)

| Artifact | Bytes |
|---|---|
| `target/junify-db-core-1.0.0.jar` (published, shaded) | **3,100,496** (2.96 MB) |
| 5 MB CI gate limit | 5,242,880 → PASS |

Included in measurement: core jar with bundled Jackson 2.17, slf4j-api (no
backend), JNoSQL/CDI annotation APIs. Excluded: junit, jmh/micrometer,
framework starters, console assets are inside the jar but replaceable.

## Claim sweep (repo-wide, this round)

| Pattern | Files | Result |
|---|---|---|
| "ANSI SQL" in `src/main` | `JunifyDB.java` javadoc ×2 | **FIXED this round** → "built-in SQL dialect" |
| "JNoSQL-EMBED" in CHANGELOG.md header | 1 | **FIXED this round** → "JunifyDB" |
| "JNoSQL-EMBED" in LICENSE copyright | 1 | **FIXED this round** → "JunifyDB Contributors" |
| ANSI SQL / BM25 / 85k / 124k / tamper-evident / zero-loss in README + docs | 0 | clean |
| Same patterns on the **live site** | 0 | clean (below) |

## Console smoke (rebuilt 1.0.0 jar, fresh process, port 8092)

- `java -jar junify-db-core-1.0.0.jar --console --port 8092` → `/api/health` **200**
  (`"status":"ok"`, uptime, memory, threads reported).
- `POST /api/auth/login` → **authenticated** with CSRF token issued.
- **Follow-up investigation turned the `--storage` observation into a real
  defect (R-29, fixed this round).** `--storage`/`--console`/`--password` are
  not supported flags, and `JunifyDB.main` silently ignored every
  unrecognized option: `--storage IN_MEMORY` ran the **FILE** engine (health
  showed `"engine":"FILE"`) and the login above succeeded only because
  console auth is **off by default** (`authEnabled=false`), not because a
  password was applied. Post-fix (rebuilt jar, live):

  | Invocation | Before | After |
  |---|---|---|
  | `--storage IN_MEMORY` | silently started as FILE | `Error: Unknown option: --storage` + exit **2** |
  | `--engine bogus` | silently started as IN_MEMORY | `Error: Unsupported engine: bogus (expected FILE, IN_MEMORY, LSM_TREE or B_TREE)` + exit **2** |
  | `--engine IN_MEMORY --port 8095` | n/a | starts, health `"engine":"IN_MEMORY"`, log `Storage engine: IN_MEMORY` |

  Also corrected the usage banner, which still advertised the retired
  `junify-embed.jar` name. Regression: `LaunchOptionValidationTest` (6 tests).
- Browser-workflow regression (`BrowserConsoleWorkflowVerificationTest` 10/10)
  ran inside the 692-test suite.

## Live website re-verification (2026-09-22)

`https://kael-tombo.github.io/JunifyDB/` (cache-busted fetch):

| Check | Result |
|---|---|
| "Relational SQL Engine" occurrences | 10 |
| "Non-Relational NoSQL Engine" occurrences | 8 |
| Canonical logo asset references | 3 (nav, hero, footer) |
| BM25 / 85k / zero-loss / Crash-Safe / ANSI SQL / "fully production" / JNoSQL-EMBED | **0 each** |
| `assets/favicon-64.png` | HTTP 200 |
| `assets/junifydb-banner.png` (og:image) | HTTP 200 |

## Documentation ↔ implementation mismatches found and fixed

| # | Mismatch | Resolution |
|---|---|---|
| 1 | `src/main` javadoc promised "ANSI SQL" in two public API methods | reworded to built-in SQL dialect |
| 2 | CHANGELOG header + LICENSE copyright carried the retired `JNoSQL-EMBED` name | renamed to JunifyDB |
| 3 | `--help` banner advertised the retired `junify-embed.jar` artifact name | corrected to `junify-db-core.jar` |
| 4 | CI `integrations` job comment still described `cli/` as an orphan with no pom (citing R-24) and excluded it, while register R-25 records it repaired | `cli/` verified locally (`BUILD SUCCESS`, now 4/4 tests) and added to the CI module loop; comment + step name corrected to R-25 |
| 5 | `--workload` accepted any string; a typo produced a "successful" run with zero benchmarks (also undocumented anywhere) | validated against `all,document,kv,mixed`, unknown token → exit 2 (R-30) |

## Entry-point option sweep (R-29 + R-30)

Every argument parser in the repository was audited after R-29. Only two others
existed (`demo/*` mains are framework bootstraps with no parsing):

| Entry point | Defect found | Post-fix live result |
|---|---|---|
| `JunifyDB` (jar) | unknown flags, bad engine silently defaulted | `--storage IN_MEMORY` → exit **2**; `--engine bogus` → exit **2**; `--engine IN_MEMORY` → health `"engine":"IN_MEMORY"` |
| `BenchmarkRunner` | unknown flags ignored; unknown `--engine` → IN_MEMORY; typo `--workload` → **zero benchmarks reported as success** | `--storage FILE` → exit **2**; `--engine mongo` → exit **2**; `--workload kv-reads` → exit **2**; happy path (`--ops 200 --workload kv --engine IN_MEMORY`) → exit **0** with real results |
| `JunifyDBShell` (cli) | every argument after the first silently ignored (no options supported) | `--port 9000` → exit **2** + usage; `--help` → usage |

## Live-preview seeding round (same day) — R-31, R-32, R-33

Seeding the running console with demo data (`.freebuff/seed-preview.sh`) exercised every
panel's real endpoint and surfaced three defects that source review had missed.

| ID | Defect | Pre-fix live evidence | Fix + regression | Post-fix live evidence |
|---|---|---|---|---|
| R-31 | `POST/PUT /api/collections/{name}/{id}` rebuilt the row from `Document.of("name","temp")` then inserted, so a partial update **replaced** the document (dropping omitted fields) and wrote a synthetic `name=temp` | `PUT /api/collections/orders/o1002 {"status":"cancelled"}` → read-back `{name:temp,status:cancelled}`; `customer`/`total`/`items` destroyed | merge onto `findById(id)`; `DocumentUpdateMergeTest` (2) | `PUT …/products/p1 {"stock":41}` → `name`/`price`/`tags` preserved, `stock` updated, no `temp` |
| R-32 | A schema field of type `number` (→ `Double`) rejected integral JSON numbers | Live 400: "Required field 'price' is missing", "Field 'stock' must be of type Double, got Integer" | `SchemaValidator.isTypeCompatible` accepts any `Number`; `SchemaNumericTypeTest` | Integer `stock:42` accepted under the registered schema; strings still rejected (400) |
| R-33 | Persisted `expiresAt` was dropped by `JsonSerde.fromJson`'s custom `Document` path → TTL never applied, nothing expired, `ttlStats.withTtl` always 0. **doc 19 had claimed PASS** | disk had `"expiresAt":1790053862005` while the API returned `expiresAt:null`; a 2s TTL left the document present and `expired:false` after 4s | restore `expiresAt` in `fromJson`; `DocumentTtlPersistenceTest` (2) | `expiresAt:1790057665804`; 2s TTL → `"expired":true` after 4s; `/stats` → `ttl:{total:4,active:4,expired:0,withTtl:1}` |

R-31 and R-33 are **High** severity (silent data loss; feature that never worked
entirely as documented); R-32 is Medium. All three were found only by driving the
running server — reinforcing the audit rule that passing tests are not proof.

## Doc 19's other half re-verified — KV TTL (confirmed, no defect)

Because R-33 falsified the document-TTL PASS, the KV TTL claim was re-tested with
evidence instead of code reading (`KvTtlPersistenceTest`, 3 tests):

| Check | Result |
|---|---|
| Expiry on read | PASS — after its TTL, `get` returns null and `keys()` no longer lists the key |
| Expiry survives restart (FILE engine) | PASS — a 2s-TTL key is readable after closing and reopening the database on the same data dir, then reads null 2.2s later. Expiry after restart is only possible if `meta_store` expirations were reloaded, so this is behavioural proof of the persistence claim |
| Key written without TTL | PASS — unchanged after 1.1s |
| API surface | **Gap noted (TTL-04):** KV TTL exists only on the Java API (`put(key, value, Duration)`); there is no REST/console route, so the console offers no KV TTL control (no fake UI either) |

## All three TTL surfaces settled (doc 19)

| Surface | Verdict | Evidence |
|---|---|---|
| Documents | **BROKEN → fixed (R-33)** | expiry was dropped on every read; fixed in `JsonSerde`, `DocumentTtlPersistenceTest` (2), live re-verified |
| Key-value | **Confirmed working** | `KvTtlPersistenceTest` (3): expiry on read, honoured after restart (proves `meta_store` reload), no expiry without TTL; gap recorded — Java-API only, no REST route (TTL-04) |
| Column family | **Confirmed working** | live: `…/column/{col}` + `…/ttl/{col}` → `hasTtl:true`, `expiresAt:1790055300188`, 404 after expiry; `ColumnFamilyTtlPersistenceTest` (2) proves TTL metadata survives a FILE-engine restart and plain columns never expire |

Also recorded (not a defect, low): the row route's flat-body pitfall —
`PUT /api/columns/{family}/{row}` with `{"value":"x","ttlSeconds":2}` writes columns
*named* `value` and `ttlSeconds` (TTL-05). My own first attempt hit it, which is why the
live column-TTL check initially looked like a failure.

## Console credibility round — fake metrics and a backup that captured nothing (R-34..R-38)

After R-33 proved that a green suite plus code reading can hide a dead feature, I stopped
reading audits and started **driving the running console**, probing endpoints with real
payloads and checking whether the numbers the UI displays are real.

| # | What was broken | How it was found | Live before → after |
|---|---|---|---|
| R-34 | CDC **Subscribers** was `getEventLog().size()` — a copy of *Events in log*, not a subscriber count | Read `/api/cdc` on the live server: `subscribers: 10, eventsInLog: 10` with nothing ever subscribed | `subscribers:10 (=events)` → `subscribers:0` while `eventsInLog:8` |
| R-35 | **Backups were empty by construction.** `BackupManager` iterated `engine.keys("")` (keys of a collection named `""`) and the REST handler backed up a *fresh* engine over an empty temp dir | Asked for a backup and decompressed it: `{}` / 22 bytes while the database held 2 collections | `size:22, {}` → `size:556, documents:13, collections:{products:4,orders:2,inventory:2,…}` in `<dataDir>/backups` |
| R-36 | `POST /api/backup/restore` — the console's own call and the path its GET advertises — always returned **400** | Replayed the UI's exact request: `400 Usage: GET /api/backup or POST /api/backup` | delete `p2` → restore → `p2` back with full payload |
| R-37 | A body-less or malformed POST to `/api/backup` **dropped the connection** (no status line) | `curl -X POST` with no body: `http 000, exit 52` while `/api/sql` answered 400 | `000/exit 52` → `200` (defaults) and `400 Invalid JSON body` |
| R-38 | The panel's **Engine** KPI always rendered `—` (`r.database.engine` was never sent); no directory shown; the toast claimed success without saying what was captured | DOM probe of `#bkInfo` after opening the panel | `ENGINE —` → `ENGINE FILE` + "Backup directory: … · 2 existing snapshots"; toast → "Backup created · 13 documents in 7 collections" |

The common thread across R-33, R-34 and R-35 is a **success signal that cannot fail**: a
PASS verdict, a KPI that mirrors another KPI, and a 22-byte file called a backup. Two of
the three were rated PASS/PARTIAL in the audit set before this round.

### Why the old tests passed

- `BackupManager`'s round trip restored into a database whose documents were still on disk
  from the original write, so `assertEquals(2, restored.count())` held whether or not the
  restore did anything. It is now content-asserting in both
  `BackupIntegrityTest.backupRestoresIntoCleanEngine` (restores into a **different** clean
  engine) and `FullIntegrationTest.endToEndBackupRestore`.
- `DeepInfrastructureTest` only asserted `Files.size(file) > 0` — and the gzip of `{}` is
  22 bytes. Content is now asserted everywhere.

### New regression coverage (16 tests)

| Test | Proves |
|---|---|
| `BackupIntegrityTest` (8) | FILE/IN_MEMORY snapshots hold live documents and non-document collections; restore into a clean engine; **aborts** rather than writing an empty snapshot when an engine cannot be enumerated; empty database still yields a valid `{}`; unflushed FILE writes are enumerable; LSM collections span memtable + SSTables; B-Tree collections discoverable |
| `ConsoleBackupEndpointTest` (8) | GET reports live engine + directory + snapshot list; POST reports real counts; snapshot lands in `<dataDir>/backups` and contains the seeded document; the console's `/restore` call round-trips through HTTP; missing file → 404; missing param → 400; body-less POST → 200; malformed JSON → 400 |
| `CdcStatusAccuracyTest` (2) | `subscribers` reflects real subscriptions and is independent of the event log, and reports 0 on a fresh server |

## Endpoint-integrity sweep — index route data loss and starved CDC connectors (R-39..R-41)

Continuing the same method (probe the running server, never trust a status string), I
enumerated every path the console JS calls and probed each one live with real ids.

| # | What was broken | Live before → after |
|---|---|---|
| R-39 | **CDC connectors were never subscribed to the event stream** — started, listed as `connected`, and starved. `FileCDCConnector` had no subscription and `CDCManager` did not add one (the Kafka connector subscribed only on the path where Kafka is on the classpath) | connector added → `subscribers:0`, **0 files** after a successful INSERT → `subscribers:1` and `afterfix.jsonl` containing the INSERT event for `after-fix-probe` |
| R-40 | `DELETE /api/cdc/connectors/{name}` said `disconnected` for **any** name; `POST` with a body missing `type` threw out of the handler and **dropped the connection** | `200 {"status":"disconnected"}` for `never-existed` → **404**; missing `type` → **400** with the expected body shape |
| R-41 | **`DELETE /api/indexes/{collection}` called `collection.clear()`** — it deleted every document and answered `{"status":"indexes cleared"}` | `products` count **4 → 0** on a route named for index maintenance → no field: `400` + `indexes:[name]` with **5 documents intact**; `?field=name`: `200 index dropped` with 5 documents intact |

R-41 is the second unrecoverable-data-loss defect of the session (after R-33's dead TTL) and
the clearest argument for the method: it is invisible to code review of the *console* code,
because the console never calls that route — the damage lives in the API's own DELETE branch.

### New regression coverage (8 tests)

`ConsoleIndexAndCdcEndpointTest`: index drop preserves documents (and the index is really gone);
missing `field` is a 400 that deletes nothing; unknown field is 404; a file connector receives
events and writes them to disk (subscriber count 0→1→0 across add/remove); deleting an unknown
connector is 404; `POST` without `type` is 400; file connector without `outputDir` is 400;
full HTTP connector lifecycle (201 → listed with `subscribers:1` → 200 → `subscribers:0`).

Also corrected this round: `23-observability-and-diagnostics.md` claimed **no production write
path records CDC events**; the live server returns a real event stream, so the claim was stale
(a doc/implementation mismatch). The remaining Kafka limitation is now stated in the API response
itself rather than implied.

## Console contract gate — the sweep made permanent

The manual probing method that found R-34..R-41 is now a repeatable CI job.

- **`scripts/console-contract-gate.sh`** boots the real server from the built jar and fails
  on any of: a non-2xx/3xx response from a path the console calls, a **dropped connection**
  (no status line), or a response body that contradicts the documented contract — the
  specific lies found this session are asserted directly:
  - backup must report `documents` and a snapshot **size > 100 bytes** (an empty snapshot
    is 22), and the gunzipped snapshot must contain the seeded document;
  - CDC `subscribers` must be `0` on a fresh server;
  - index DELETE without `?field=` must be a 400 and **documents must survive**;
  - unknown connector DELETE must be a 404, not a fake disconnect;
  - a body-less POST must never drop the connection;
  - delete → **restore → payload present** round trip through HTTP.
- **Falsified before trusting it**: the gate was run against the pre-fix commit
  (`b822f9f`, the parent of 9d27af8) in a throwaway worktree — **10 failures**, including
  `index route deleted documents (0 docs left)` — and against the fixed tree: **PASS**.
  A gate that has never failed proves nothing; this one demonstrably catches the class.
- **CI**: new `console-contract` job (needs `build`, Java 21, packages the jar, runs the
  gate on :8097, uploads the server log on failure). YAML validated.

## Auth gate — the security surface gets the same treatment

The unauthenticated gate proves responses are honest; it cannot prove that the security
model *enforces* anything, because with no key configured everything is open by design.
**`scripts/console-auth-gate.sh`** boots a second server with `--api-key` set and asserts:

1. **No open path**: every console-called endpoint (13 GETs + a write) returns **401** to an
   anonymous request — a single unprotected path would be a breach.
2. **Wrong key rejected everywhere** (4 paths), not just on one handler.
3. **Real key accepted everywhere** (10 GETs) — a key that authenticates nowhere is a lockout.
4. **Session lifecycle**: `POST /api/auth/login` with the key issues a session cookie and a
   CSRF token; the cookie authenticates a GET; logout returns 200; the same cookie afterwards
   returns **401**.

Result on the fixed tree: **PASS** (34 checks). Falsification note: the same gate run at the
pre-fix commit `b822f9f` also **passes**, and that is the expected, honest outcome — the
authentication enforcement code was not touched by the R-31..R-41 fixes. This gate is a
regression tripwire for *future* changes to the auth surface, not evidence that a past defect
was fixed; its value is that a change which accidentally opens a path, breaks key
validation, or orphans sessions now fails CI instead of shipping.

The CSRF-only configuration (`csrfEnabled` with session-cookie auth) remains covered by
`SecurityEnforcementTest` at the unit level; the CLI surface cannot reach it because
`--api-key` sets `authEnabled` without enabling CSRF, which is the documented behaviour the
gate now pins down (API-key requests bypass CSRF by design; see `isCsrfValid`).

## Final sweep — transactions and bulk (R-42..R-44)

Probed the remaining console surfaces not yet covered: transactions lifecycle, SSE stream,
cleanup routes, bulk insert.

| # | Finding | Live evidence |
|---|---|---|
| R-42 | Commit/rollback of an **unknown** txId returned `200 {"status":"committed"}` (no-op), double commit "succeeded" twice, rollback answered the malformed `"rollbackted"` | before: `200 committed` for txId 424242 → after: **404** with the active list; `rolled_back` correct |
| R-43 | Suspected fake metric: `transactions` grows on begin. **Probing acquitted it** — begin→`transactions`, commit→`transactionCommits`, rollback→`transactionRollbacks` all move correctly; semantics are "begun", documented here | counters verified before/after live calls; recorded as not-a-defect |
| R-44 | **Bulk inserts discarded client ids** (always UUID), so `GET /{collection}/k1` 404'd after bulk-inserting `k1`; the client's id was demoted to a duplicate field inside the document | before: bulk `k1` → GET **404** → after: **200** `{"id":"k1","fields":{"name":"A"}}`; junk entries now counted (`"skipped":1`) |

Verified honest, no fix needed: SSE `/api/metrics/stream` (first event immediate, concurrent
clients each served), collection/column cleanup routes (real counts), bulk DELETE (honest
deleted count for a whole-collection truncate — dangerous but truthful, and unreachable from
the console UI).

Contract gate extended with the R-42/R-44 blocks (unknown-tx commit → 404, double commit →
404, `rolled_back` string, bulk id addressability); suite **749/749** + 4/4 CLI.

## NoSQL query-operator sweep (R-45..R-47, 2026-09-22 latest)

Probed the `/api/collections/{name}/query` endpoint operator-by-operator against the seeded
preview data. Two operators were silently wrong and one silently permissive — all invisible
to the suite because **no test had ever covered `$regex`, `$and`, or `$or`**:

| # | Finding | Live evidence (seeded `products`) |
|---|---|---|
| R-45 | `$regex` used `String.matches()` — implicitly anchored at both ends, so substring patterns never matched; any `$and`-joined query containing a `$regex` clause silently returned **zero rows** | `{"name":{"$regex":"Key"}}` with "Keyboard" present: **0 → 1** result |
| R-46 | Top-level `{"$and":[...]}` / `{"$or":[...]}` were unreachable: arrays fell through `parseField` to "simple equality on a field named `$and`", so every document failed | `{"$and":[{"name":{"$eq":"Keyboard"}}]}`: **0 → 1** result |
| R-47 | Unknown operators silently ignored (`default -> doc -> true`): a typo returned **every document**; `$in` with a non-list threw a raw ClassCastException → 500 | `{"$gteX":1}`: silent 4-rows → **400** naming the operator; `[unclosed` regex → **400** |

Fix: substring `Matcher.find()` semantics for `$regex` (users who want anchoring write
`^...$`), `$and`/`$or` extracted before the field loop (ANDed with remaining top-level
conditions, MongoDB-style), and a new `QueryParser.QueryFormatException` mapped by the
query endpoint to **400** (parse errors must never be 500).

**Falsified before trusted**: the new `ConsoleQueryEndpointTest` (11 tests) run in a
throwaway worktree at the pre-fix commit `08c4e4f` fails **9/11**; on the fixed tree 11/11.
`CoverageExtensionTest.queryParser_unknownOperatorIgnored` had codified the old
ignore-unknown-operators behavior and was inverted to
`queryParser_unknownOperatorRefused`. Contract gate extended with regex/`$and`/`$or`
match-count blocks and 400 assertions. Suite now **760/760** + 4/4 CLI; both gates PASS.
Live after-states verified on the redeployed preview (regex=1, `$and`=1, typo=400).

## SQL-engine sweep (R-48..R-49, 2026-09-22 latest)

Probed the console's SQL Studio endpoint (`POST /api/sql`) for the same
fake-success class. Two defects, both catalog-mutating or success-lying:

| # | Finding | Live evidence |
|---|---|---|
| R-48 | Every SQL statement resolved its table through the auto-creating `documentCollection()` — **`SELECT * FROM no_such_table` created an empty collection as a side effect of reading** and returned `rowCount:0, status:success`; the collection then polluted the console list permanently. JOINs, UPDATE, DELETE inherited it | before: 200 + new empty collection in `/api/collections` → after: **404** `Table does not exist`, catalog byte-identical before/after |
| R-49 | `DROP TABLE no_such_table` deleted nothing, hit no error path, returned `status:success` | before: 200 success → after: **404** |

Fix: a new `existingCollection()` resolution path in `SqlEngine` (SELECT/FROM, JOIN
targets, UPDATE, DELETE, DROP) that throws the new `SqlUnknownTableException`; the SQL
endpoint maps it to **404** (distinguishing state errors from 400 syntax errors).
INSERT and CREATE TABLE keep auto-create — the documented schemaless workflow, relied
on by backup/restore, migrations, repositories, and the demo app.

**Falsified before trusted**: `SqlUnknownTableTest` (9 tests) in a throwaway worktree at
`80efbea` fails **4/9** (the four defect assertions); on the fixed tree 9/9. Contract gate
extended with a 5-check SQL block: 404 on SELECT/DROP of unknown tables, catalog-count
immutability across a failed SELECT, no leftover collection, existing-table sanity.
Suite now **769/769** + 4/4 CLI; both gates PASS. Live after-states verified on the
redeployed preview, including that the earlier probe pollution (`nope`, `no_such_table`,
`once` collections created by probing the OLD code) demonstrates the defect is real in
practice, not theoretical.

## Vectors / TTL sweep (R-50..R-51, 2026-09-22 latest)

Probed the last unprobed console surfaces: the vector endpoint and the TTL lifecycle.

| # | Finding | Live evidence |
|---|---|---|
| R-50 | Vector **search silently created an empty index** for a typo'd name (dimensioned from the query vector) and answered `200 {"results":[]}`; missing `vector` field → NPE 500; `k=0` → fake empty success; negative `k` → bare `"-5"` 500; dimension mismatch → 500 | search typo'd index: `200 []` → **404**, no index created; missing vector / k≤0 / wrong dims → **400** |
| R-51 | **Expired documents were returned by reads** with only an `expired:true` hint — console, SQL engine, and adapters all read logically-deleted data (the read-side counterpart of R-33's persistence fix) | TTL 1s → wait → point read `200 expired:true` → **404**; scan `[]`; SQL COUNT excludes it |

R-51's fix produced a second-order defect that **the new tests caught before commit**:
`cleanupExpired()` iterated `findAll()` — which now filters expired documents — so the
sweeper could no longer see its own targets. It now scans raw storage. This is the test
suite doing exactly what it exists for: `VectorSearchAndTtlReadTest.cleanupStillRemoves`
failed, the interaction was fixed at the source, and the sweep is asserted to still
remove what reads hide.

`DocumentTtlPersistenceTest` had asserted the old behavior ("expiry is logical until
cleaned up" — expired docs readable) and was inverted per R-51, with the storage
round-trip assertions retained.

**Falsified before trusted**: both new test classes run in a throwaway worktree at
`6d47a4c` fail **9/9**. Contract gate extended with 6 vector checks and 3 TTL checks
(including the sweep count). Suite now **778/778** + 4/4 CLI; both gates PASS; live
after-states verified on the redeployed preview.

## Durability round (R-52, 2026-09-22 latest)

The honest 404 that R-50 introduced exposed its bigger sibling: vector indexes existed
**only in the console server's memory**. Every restart silently lost all vectors and
dimensions while documents, KV entries, and column families all survived the same
restart. A vector store that forgets its vectors on restart is a durability defect,
not a design choice the user ever agreed to.

Fix:
- `HNSWIndex.toJson()` output (vectors + parameters; the HNSW graph is rebuildable and
  is never serialized) is written to `<dataDir>/vectors/{index}.json` on every add and
  remove — best-effort, mirroring the audit writer: a disk failure never fails the API
  call.
- `HNSWIndex.fromJson()` rebuilds the index by re-adding vectors in stored order (same
  search semantics; link layout may differ from the pre-restart graph).
- Restore runs at server start on both bind paths; a corrupt file is renamed aside
  (`*.json.corrupt-<ts>`) instead of blocking startup; IN_MEMORY neither persists nor
  restores.

Covered by `VectorPersistenceTest` (3 tests, full close/reopen cycles over the same
data directory, including deletion persistence and the corrupt-file quarantine),
**falsified at `443fcac`** (restart test fails there). Verified live on the preview:
add vector → kill server → restart → index present with `size:1`, search returns
`lv1`. Contract gate extended with a persistence-file check. Suite **781/781** + 4/4
CLI; both gates PASS.

## Engine-matrix round (R-53, 2026-09-22 latest)

The gates had only ever booted FILE. Booting real servers on LSM_TREE and B_TREE and
comparing catalogs across a restart exposed R-53: **persisted collections were
invisible after restart** on both engines. `materializePersistedCollections()`
enumerated `engine.collectionNames()` — an SPI method whose default returns an empty
set and which only `FileEngine` overrides. LSM data survived on disk (WAL/SSTables),
BTree data survived on disk (index file), but the catalog (`/api/collections`,
backups, SQL) showed nothing until a client requested the exact collection name.

Fix: discovery enumerates `collections()` — the live-and-persisted set that every
engine implements — unioned with `collectionNames()` for engines reporting persisted
identity only there. Covered by `EngineRestartDiscoveryTest` (4 tests: LSM/BTree/FILE
restart cycles plus the zero-record non-case), **falsified: 2/4 fail at `db63c79`**.

Process hardening that came out of this round: the contract gate is now parametrized
by `ENGINE` and CI runs it on **FILE, LSM_TREE, and B_TREE** (all PASS locally); and
after a leftover IN_MEMORY probe server on :8097 masqueraded as the gate's freshly
built jar and produced a false persistence failure, the gate **fails fast if its port
is already bound** — a green gate can no longer be silently earned against the wrong
server. Suite **785/785** + 4/4 CLI; all gates PASS on all three engines; preview
redeployed and re-registered. (Later the same day the suite reached **795/795** with the
R-55 collection-resolution and **R-59** transactional-catalog tests — see the final section.)

## Repository / release mechanics state

- Remote CI: last runs green — `pages build and deployment` success on
  `4e5d7cd` and `2ebcc14`; `CI` success on `b35ac87`.
- `git ls-remote --tags origin` → **no tags yet** (v1.0.0 tag + GitHub
  Release remain the only mechanical release steps).
- Working tree contains unrelated pre-existing modifications (retry-after
  lockout work in `JunifyDBServer.java` + test files, trace JSONs, pages.yml,
  proof doc) — deliberately not committed by the brand-alignment commit
  `b822f9f`; they do not affect this round's evidence.

## Verdict inputs

- No new release blockers found this round, but the backup cluster (R-35/R-36) came
  closest: a backup feature that silently produced empty files and a restore that had
  never worked would have been a **public-release blocker** if left in place, since the
  first user to rely on it would lose data. Both are fixed with content-asserting tests
  and live verification.
- Two product-identity residue defects (javadoc ANSI SQL; CHANGELOG/LICENSE
  old name) found and fixed with this file as evidence.
- Decision document: `63-final-go-no-go-decision.md` (round-3 internal verdict);
  the canonical public-release decision is now `final-go-no-go-decision.md`.

## Docs-alignment + collection-resolution round (R-54, R-55, 2026-09-22 latest)

This round began as the baseline/assessment work and ended with two more defects, one of
them the read-side twin of R-48.

**R-54 — public docs misstated the SQL engine's DDL surface.** The README described the
dialect as "no DDL, no sequences, no views" and the website engine card listed "DDL"
under *Not included*, while `SqlParser.parseCreate()`/`parseDrop()` (lines 231/256)
genuinely implement `CREATE TABLE` and `DROP TABLE`. Found by grepping the parser for DDL
keywords instead of trusting the docs, and confirmed against the lexer: `TABLE` is the
**only** DDL keyword that exists (`ALTER`/`INDEX`/`VIEW`/`SEQUENCE` are not tokens). Both
surfaces now state the exact surface. This is a public-claim defect: the docs *understated*
and *misstated* a working capability — the same class of dishonesty as over-claiming, in
the other direction.

**R-55 — collection resolution was a write.** `CollectionsHandler` resolved its target
with `db.documentCollection(name)` **before** dispatching on the HTTP method, and that
accessor auto-creates. Consequence, observed live on the preview while cleaning up R-48's
leftovers:

| Request | Before | After |
|---|---|---|
| `GET /api/collections/zz_typo_read` | **200** `[]` **and the collection was created** (catalog 15 → 16) | **404** `Collection not found` |
| `DELETE /api/collections/no_such_table` | **405** *and created the collection it was asked to remove* | **404** |
| `POST /api/collections/live_created` | 201 (correct) | 201 — unchanged, the documented schemaless workflow |
| `DELETE` on an existing collection | 405 bare `Method not allowed` | 405 naming the limitation (no collection-drop capability exists) |

A **read** that silently writes to the catalog is the worst variant of the fake-success
family found in this session: it corrupts the catalog permanently, from a request that
looked like a failed lookup. Fixed by resolving without creating for every non-document-
write request (GET, DELETE, and the `stats`/`set-ttl`/`cleanup`/`query` sub-resources),
404 for an unknown collection, and keeping auto-create **only** for POST/PUT document
writes — the R-48/ADR-004 contract, applied consistently to the Console API surface this
time rather than the SQL engine.

Coverage: 6 tests in `ConsoleCollectionResolutionTest`, **falsified: 4/6 fail at pre-fix
`7709d31`** with the defect verbatim in the assertions (`expected: <404> but was: <200>`
returning body `[]`; `expected: <404> but was: <405>`; the bare 405 message). The other two
are guard-rails that must pass on both sides (POST still creates; existing reads still
work). Contract gate extended with five R-55 checks (404s, catalog-count immutability, no
ghost collection, POST-still-creates), and the gate re-verified on FILE, LSM_TREE and
B_TREE. Suite **795/795** core + 4/4 CLI.

**Also this round (release artefacts, not defects):** the recoverable baseline
(`baseline/`, validated with its 28-checksum caveat), the deep current-codebase assessment
(`00-current-codebase-assessment.md` — which is where the missing JDBC driver and the
SQL-over-document-store coupling were documented), the architecture decision records
(`67-…`), and the canonical `final-go-no-go-decision.md`.

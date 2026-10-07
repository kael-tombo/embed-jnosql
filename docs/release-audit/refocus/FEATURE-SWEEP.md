# Release feature sweep — every advertised capability, tested end to end

**Verdict: PASS. 0 FAIL, 0 SKIP.** Every capability the NoSQL-only release advertises was driven
through the public API (library, HTTP, and the Jakarta NoSQL adapter) against real storage in this
round, not only inspected.

- Command: `mvn -B clean verify` → **BUILD SUCCESS, 847 tests, 0 failures, 0 errors, 0 skipped**
  (log: [verify-run.log](verify-run.log) and refreshed [core-verify.log](core-verify.log); count
  includes the round-3 experimental-label assertion and the 11 phrase-contract tests from the
  Finding 1 fix below). Demo layer: spring-boot-demo **36/36** incl. the 22-test
  Hibernate-integration suite (row 22) and the 8-test H2-parity suite (row 23); starter
  **29/29** incl. the 7-test toggle matrix, the 7-test Spring-context parity suite, and the
  3-test transaction-routing unit suite; framework demos **35/35** — quarkus-demo **13/13**,
  micronaut-demo **11/11**, vertx-demo **11/11** incl. the 23 new framework-integration tests
  (row 24; logs: [demo-quarkus-final.log](demo-quarkus-final.log),
  [demo-micronaut-final.log](demo-micronaut-final.log), [demo-vertx-final.log](demo-vertx-final.log))
- New sweep class: `src/test/java/org/embeddedjnosql/db/ReleaseFeatureSweepTest.java` — 22 tests: one per
  capability family exercising the library surface directly, plus the experimental-label contract
  assertion added after the round-3 product decision.
- New phrase-contract class: `src/test/java/org/embeddedjnosql/db/TextIndexPhraseTest.java` — 11 tests
  pinning positional phrase matching after the Finding 1 fix (this round).
- New HTTP cases: `src/test/java/org/embeddedjnosql/db/ConsoleWorkspaceEndpointsTest.java` — 4 added tests
  covering the Redis-style structure routes, `/api/stats`, and the retired-route 404 contract.
- Pre-existing HTTP/console coverage (re-run green, not re-derived): **22 test classes call `/api/...`
  endpoints directly and contribute 185 tests** — including `FullFeatureTest` (72), ten of the eleven
  `Console*` suites (`ConsoleWorkspaceEndpointsTest` among them), `SecurityEnforcementTest`,
  `CorsPolicyConsistencyTest`, and `JNoSQLServerTest`; `ConsoleComprehensiveFeatureProofTest` (18) and
  `BrowserConsoleWorkflowVerificationTest` (10) exercise the same endpoints through a different
  URL-building path, for **213 HTTP-level tests in total**.

The sweep needed **12 assertion corrections before it went green** (9 on the first run, then 3 more
after the last compile fix, plus 2 in the new HTTP cases). **Every one was a wrong expectation in
the test, not a product defect** — each was re-checked against the implementation before the
assertion was corrected (see *Findings* below). No product code was changed to make the sweep pass;
the only assertions strengthened were the three vacuous `FullFeatureTest` ones on retired routes.

---

## 1. Library sweep (ReleaseFeatureSweepTest)

| # | Capability | What is asserted | Verdict |
|---|---|---|---|
| 1 | Document CRUD + lifecycle | `documentCollection(name)` materializes the collection and it survives restart; generated ids assigned on insert; an existing id is replaced (one document per id); `update` on a missing id throws; `upsert` creates; `deleteById` reports false for a missing id; `insertAll`/`bulkDelete`/`clear` (clear keeps the collection) | PASS |
| 2 | Document values | nested map, list, string/int/long/double/bool round-trip with types intact; an explicitly stored `null` is a *present* field (`has` true, `getRaw` null) while a never-written field is absent | PASS |
| 3 | Document TTL | `insert(doc, ttl)`, `setTtl`, `ttlStats`, `cleanupExpired` reclaims every expired document; live documents remain readable until the deadline | PASS |
| 4 | Key-Value | `put/get/exists/delete`, absent key reads null, `putAll`/`getAll`/`keys`/`count`, `increment`/`decrement`, overwrite does not add a key, TTL write with `expiryOf`/`expirations` and expiry invisibility after the deadline, `stats`, `clear` | PASS |
| 5 | Lists (Redis-style) | `rpush`/`lpush` order, `lrange` windows, `lindex`, `lset`, `lpop`/`rpop`, `ltrim`, `rpoplpush` (returns the moved element, lands on the destination head, source emptied), `keys`, `stats`, `delete` | PASS |
| 6 | Sets | `sadd` distinct counting, `smembers`, `scard`, `sismember`/`contains`, `smove` between keys, `sinter`, `srem`, `size`, `spop`, `keys`, `clear` | PASS |
| 7 | Hashes | `hset` single + bulk, `hget`, `hgetall`, `hkeys`, `hvals`, `hgetallFlat` (field/value pairs), `hincrby`, `hstrlen`, `hlen`, `hexists`, `hdel`, `clear` | PASS |
| 8 | Column family | `put`/`get` per row+column, `getRow`, `getRowSlice`, `getRowByPrefix`, `getRowWithFilter`, per-column TTL (`putWithTtl`, `getWithTtlCheck`, `getRemainingTtl`, `isExpired`) and `cleanupExpiredColumns`/`cleanupAllExpired` | PASS |
| 9 | Query predicates | `eq/ne/gt/gte/lt/lte/in/between/contains/regex/exists`, `all`, boolean `and`/`or`, and `count` agreeing with `find` | PASS |
| 10 | Query ordering/windowing | `sortByAsc`/`sortByDesc` (numeric order verified), `limit`, `offset`, `page`, repeated-query determinism, `findOne` null on no match | PASS |
| 11 | Secondary index | `createIndex`, `lookup`, inclusive `range` over one value (returns every document carrying it), `allValues`, `getIndexes`, `toJson`, `dropIndex` (false when missing) | PASS |
| 12 | Text index | tokenized `search`, multi-term intersection, `getIndexedTerms` vocabulary, `toMap`, `size()` reporting postings, `searchPhrases` positional phrase match, `remove` dropping a document's postings (and positions) | PASS |
| 13 | Vector index (experimental) | `add`, `size`, k-NN returns exactly k with the self-match first, `searchEuclidean`, `remove` | PASS |
| 14 | Transactions | committed write visible; rollback leaves nothing; `id`, `operationCount`, `readTimestamp` reported | PASS |
| 15 | Persistence | documents, KV, and the collection catalog survive close/reopen in **all four** storage modes (FILE, LSM_TREE, B_TREE, IN_MEMORY) with no WAL double-apply; IN_MEMORY is asserted to be the documented non-durable mode | PASS |
| 16 | Backup / restore | `backup(path)` writes a snapshot on disk and reports `lastBackupCounts`; `restore` is additive (deleted-after-snapshot documents come back, later writes remain) | PASS |
| 17 | Document validation | registered schema accepts valid fields, rejects a missing required field with errors, leaves unregistered collections schemaless, `dropSchema` | PASS |
| 18 | Events + metrics | one `AFTER_INSERT` per insert, `snapshot`/`memoryStats`/`contentionStats`, insert/read counters advance, listener count reported | PASS |
| 19 | CDC | file connector registers, `start`/`stop` observes inserts/updates/deletes, `getStatus` non-empty, `removeFileConnector` | PASS |
| 20 | Jakarta NoSQL adapter | `@Entity` names the collection, `@Id` becomes the document id, `@Column` renames fields, mapping alone persists nothing, `EmbedRepository` save/findById/existsById/count/findBy/findAll/deleteById, and the fluent `db.from(Book).where("price < ?", …).orderBy("price ASC").list()/count()/first()` | PASS |
| 21 | Lifecycle | `isOpen`/`isClosed` transitions, `flush`, and a closed database failing loudly (`IllegalStateException`) instead of silently | PASS |
| 22 | **Hibernate support inside Spring Boot (deep, this round)** | real `@SpringBootTest` in the Spring Boot demo: Hibernate/JPA-annotated entities map through `EntityMapper`/`EclipseDocumentTemplate` with auto-configured beans — `@Table` collection naming + lowercase fallback, `@UuidGenerator` id backfill vs. explicit `@Id`, `@PrePersist`/`@PostLoad`, `@CreationTimestamp`/`@UpdateTimestamp` columns, `@Formula` derived on load and tracking inputs after update, `@Column(name=…)` alias keys, `@Enumerated(STRING)` stored as names + `Query.eq` on them, `@Transient` excluded, `java.util.Date`↔ISO-string symmetry (write/read bug found and fixed in this round), batch/windowed queries, storage-level enum grouping, `@Immutable` update refusal, `@NaturalId` recognition, whole-stack column audit — 22 tests, log: [demo-spring-hibernate.log](demo-spring-hibernate.log) | PASS |
| 24 | **Framework integrations: Quarkus, Micronaut, Vert.x demos (deep, this round)** | each flagship framework demo gets real framework-runner tests, mirroring the Spring H2-parity round: Quarkus (`@QuarkusTest` + Ark CDI + RESTEasy Reactive) — seeded-catalog CRUD/list/naming via extension-produced `EmbedJNoSQL`, explicit MVCC commit vs. rollback around the HTTP order flow (stock 30→29; oversubscribed → 400 with stock intact), TTL document metadata (`getExpiresAt`, `ttlStats.withTtl`), URL-hostile + unicode/whitespace id round trips, CDI producer probe, SmallRye Config resolution of `embedjnosql.*` — 9 tests; Micronaut (`@MicronautTest` + APT bean graph) — `EmbedJNoSQL` bean resolution through the integration module's APT metadata, `embedjnosql.*` binding (the demo's `application.yml` was silently dead without a YAML source — fixed by adding snakeyaml 2.2 offline-cached), `@SerdeImport` record round trip via `JsonMapper`, read consistency, 404/400 error edges surfaced as `HttpClientResponseException`, two-collection atomic commit — 7 tests; Vert.x (`VertxExtension` + `executeBlocking`) — server-generated ids, order persist-then-list, 8-way concurrent `Future.all` reads, async tx rollback leaves stock intact, 4-way concurrent order placement serializes stock exactly (100−4×10=60), 404 routes — 7 tests. En-route build fixes: `quarkus-extension` and `micronaut-integration` public type names were left stale by a rename sweep (neither module compiled before; both build green now, all references internal) — 23 new tests, logs: [demo-quarkus-final.log](demo-quarkus-final.log), [demo-micronaut-final.log](demo-micronaut-final.log), [demo-vertx-final.log](demo-vertx-final.log) | PASS |
| 23 | **H2-parity Spring Boot integration (deep, this round)** | the H2 experience end to end with no manual wiring: an auto-configured `PlatformTransactionManager` (`EmbedJNoSQLTransactionManager` over the MVCC layer) makes plain `@Transactional` commit/rollback stage and discard writes; the auto-configured database bean is wrapped in a transaction-routing proxy (the `TransactionAwareDataSourceProxy` equivalent) so `documentCollection()` inside a transaction returns a `TransactionAwareDocumentCollection` staging mutations in the transaction with read-your-own-writes (query reads merge the staged overlay); `REQUIRES_NEW` suspends/resumes the outer MVCC transaction and commits independently; every scanned `@Entity` gets an auto-registered, fully-parameterized `EmbedRepository<T, ID>` bean (`parityOrderRepository` injectable by type; user beans win); entity collections materialize at startup (`embedjnosql.auto-create-collections`). Verified in the starter (`EmbedJNoSQLHibernateParityTest` 7 tests + `EmbedJNoSQLParityTogglesTest` toggle matrix + `EmbedTxRoutingTest` unit suite — log: [starter-h2-parity.log](starter-h2-parity.log)) and in the demo (`HibernateParityIntegrationTest` 8 tests over the `HibernateOrder` entity — log: [demo-spring-hibernate.log](demo-spring-hibernate.log)). Three real wiring bugs found and fixed en route: test-app component scan sweeping the starter's own nested configurations (fixed by packaging the test app in its own package — a real app is unaffected), the transaction holder being keyed by the proxy while the routing interceptor resolves by the raw target (both sides now key on the unwrapped instance), and `doGetTransaction` not reflecting the thread-bound transaction so REQUIRED never joined and REQUIRES_NEW never suspended | PASS |
| 22 | Document aggregation (README: “aggregate with the document helpers”) | `DocumentAggregation.count/filter-count/min/max/sum/avg/groupBy/groupByDocuments/groupBy(transformer)/distinct/distinctValues/first/last/limit/skip/orderBy` + `AggregationResult` — covered by the aggregation block in `UtilityClassTest` (functional assertions on every helper) and the annotation demo's invoice roll-up (`AnnotationShowcaseTest`) | PASS |

## 2. HTTP / console sweep

| Route area | Endpoint(s) driven | Verdict |
|---|---|---|
| Documents | `GET/POST/PUT/DELETE /api/collections…`, `POST /api/collections/{name}/query` (filters, `$gt`, sort, limit, offset), `POST /api/bulk/{collection}` | PASS |
| Key-Value | `PUT/GET/DELETE /api/kv/{bucket}/{key}`, `/api/kv-meta/buckets`, `/api/kv-meta/{bucket}` (keys, prefix filter, TTL metadata, expiry) | PASS |
| Lists | `/api/kv/lists/{bucket}/{key}` plus `rpush`, `lpush`, `lrange`, `len`, `lindex`, `lpop`, `rpop`, `ltrim`, `stats`, DELETE — **added this round** | PASS |
| Sets | `/api/kv/sets/{bucket}/{key}` plus `sadd` (incl. re-add = 0), `smembers`, `scard`, `sismember`, `srandmember`, `srem`, `spop`, DELETE — **added this round** | PASS |
| Hashes | `/api/kv/hashes/{bucket}/{key}` plus `hset` (field form + bulk form), `hget`, `hgetall`, `hlen`, `hexists`, `hdel`, and the 400 for a missing `field` — **added this round** | PASS |
| Columns | `/api/columns/{family}/{row}` | PASS |
| Indexes / vectors | `/api/indexes/{collection}?field=`, `/api/vectors/{index}` + `/search` | PASS |
| Transactions | `/api/transactions` | PASS |
| Schema | `GET /api/schema` listing (the previously asserted `/api/schema/collections` never existed) | PASS |
| Backup / CDC | `/api/backup` (+ restore), `/api/cdc`, `/api/cdc/connectors/{name}` | PASS |
| Diagnostics | `/api/health` (manifest version), `/api/metrics`, `/api/stats` (**added this round**), `/api/audit/logs` (+ filters), `/api/storage/status` | PASS |
| Auth / security | `/api/auth/login` and `/api/auth/logout`, unauthenticated access refused, CSRF, CORS consistency, security headers | PASS |
| Static console | `index.html` + `console.js` served; exactly the 12 post-refactor panels; no SQL panel, no `/api/sql` reference | PASS |
| **Retired relational routes** | `POST/DELETE /api/tables/…`, `GET /api/constraints/…`, `POST /api/sql/execute` → **404** | PASS |

## 3. Findings (observations, not failures)

1. **`TextIndex.searchPhrases` matched on the phrase's first token only.** `matchesPhrase(...)` was
   a stub that returned `true`, so phrase queries returned every document containing the first token
   rather than an ordered-phrase match — and a phrase starting with a stop word matched nothing at
   all, since stop words are never indexed. *Resolution (this round): positional matching
   implemented.* The index now tracks token → document → positions alongside the posting sets, and
   `matchesPhrase` requires the phrase's tokens on consecutive positions of the indexable token
   stream (same pipeline both sides: lower-case, split on non-alphanumerics, stop words and single
   characters dropped). `TextIndexPhraseTest` (11 tests) pins adjacency, transparent stop
   words/punctuation, stop-word-initial phrases, repeated tokens, OR across phrases, removal
   clearing positions, and re-add replacement; the prior "doesn't throw" smoke assertion in
   `CoverageExtensionTest` still passes unchanged.
2. **`Query.exists(field)` is presence-based**, so a document that stores an explicit `null` still
   counts as carrying the field (consistent with `Document.has`). Asserted, not "fixed".
3. **Three `FullFeatureTest` assertions were vacuous.** They accepted `200 | 400 | 404 | 503` on
   `/api/tables/…` and `/api/constraints/…` — routes that no longer exist — so they passed on a 404
   without proving the removal. They now require **404**, which turns a dead test into a real
   post-refactor guarantee. `GET /api/schema/collections` (a path that was never implemented) was
   replaced with a real `GET /api/schema` listing check.
4. **Unregistered `/api/...` paths 404** through the static handler rather than falling back to an
   SPA index page — which is what makes the retired-route assertions meaningful.
5. **Vector search remains approximate by design** (HNSW, experimental): the k-NN contract asserted
   here is "exactly k results with the self-match first", not exact tail ranking.
6. **Persistence semantics are deliberately asymmetric:** `IN_MEMORY` discards everything on close
   while the three durable modes restore the catalog, documents, and KV entries; `restore` is
   additive, not a rollback. Both are asserted as the contract.
7. **Three shipped public classes have no functional tests, no callers, and no current
   documentation:** `core/pool/EmbedJNoSQLPool`, `api/reactive/ReactiveJNoSQL`, and
   `core/migration/MigrationManager`. All three are excluded from the JaCoCo gate in `pom.xml`
   (lines ~251–253) and all three are in the released jar.
   *Resolution (review round 3, product decision: keep and label, don't delete):* each class now
   carries the new runtime-visible `org.embeddedjnosql.db.Experimental` annotation plus a class javadoc
   stating it has no production caller, no functional coverage, and may change or be removed
   without notice. The label is machine-checked by `experimentalClassesAreLabeled` in
   `ReleaseFeatureSweepTest`, and the JaCoCo excludes stay because the classes remain intentionally
   outside the coverage gate. No smoke tests were added — the classes are still unreachable API
   that ships to consumers, now visibly marked as experimental.

## 4. Gate scripts executed end-to-end (review round 2)

Both scripts previously only syntax-checked were run against the shaded jar from the green build:

| Script | Result | Evidence |
|---|---|---|
| `scripts/console-contract-gate.sh` | **PASS, 95 checks, 0 failures** | [contract-gate-run.log](contract-gate-run.log) — seeding, every console GET, destructive-route safety, transactions, bulk client ids, query operators, malformed-query 400s, no-create-on-read, TTL expiry, vector persistence, CORS policy, restore round trip, graceful restart, and kill -9 WAL recovery (12 acknowledged writes survived); plus 4 shipped-jar checks that the console JS keeps the collection picker truthful after insert/drop/cleanup/global refresh |
| `scripts/console-auth-gate.sh` | **PASS, 36 checks, 0 failures** | [auth-gate-run.log](auth-gate-run.log) — anonymous 401 on every path, wrong key 401, real key 200 everywhere, authenticated write + readback, login/logout session lifecycle with CSRF |
| `scripts/deep-test.sh` (bash port of `deep-test.ps1`) | **PASS, 90 checks, 0 failures** | [deep-test-run.log](deep-test-run.log) — 20 sections: health, auth (401/401/200), metrics+stats, collection CRUD with query operators and TTL/cleanup, KV, lists, sets, hashes, column families, retired SQL routes → 404, schema, vectors, bulk, transactions, indexes, backup, audit filters, CDC, anonymous static files |

## 5. Live browser verification (review round 2)

A real browser session was driven against `java -jar embed-jnosql-core-1.0.0.jar --engine IN_MEMORY`:

- Console loads with 12 panels — `overview, collections, kv, columns, vectors, schema, tx, indexes,
  backup, cdc, audit, server` — and no SQL panel anywhere in the navigation.
- The retired `#sql-studio` deep link resolves to `overview`; no SQL heading exists on the page. The
  only `sql`-substring matches in the live DOM are the twelve `.engine-tag.nosql` “NoSQL” badges.
- Every fetch returned 200; no `/api/sql` traffic; zero console errors.
- The full user path works through the product's own controls: refresh the collection picker, click
  the `gate_ui_demo 1` chip, and the document table renders the inserted document (count “1 docs”).

Picker accuracy note (review round 3): panel **re-entry** was never the bug — `goto()` calls
`onFirstOpen(id)` on every entry, which refreshes the picker. The real staleness was same-panel
mutations and the topbar ⟳: `insertDoc`/`dropDocs`/`cleanupExpired` never repopulated the picker,
so a brand-new collection stayed invisible as “No collections yet”. Fixed in `console.js`; the
contract gate now asserts the wiring inside the shipped jar, and a live browser re-check confirmed
the chip appears immediately on insert, survives global refresh with nothing loaded, and its count
updates after a drop — zero console errors throughout.

## 6. Still not covered (as of review round 3)

| Item | Status |
|---|---|
| Browser screenshots/video export | NOT RUN — the webview produces no frames ("not compositing"); retried twice on a live server. Browser verification above is DOM/network-level, which proves the DOM, not the pixels |
| Maven Central publish / Pages deploy | BLOCKED by policy (no credentials, nothing pushed) |
| `docs/assets/embedjnosql-banner.png` wording | NOT VERIFIED (raster, no text chunks) |
| `EmbedJNoSQLPool`, `ReactiveJNoSQL`, `MigrationManager` | LABELED EXPERIMENTAL (round-3 decision: keep, don't delete) — `@Experimental` + javadoc on each, enforced by the sweep's label assertion; still no caller, no functional test, still JaCoCo-excluded (see Finding 7) |

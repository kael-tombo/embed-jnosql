# JUNIFY-DB — User Story Map

**Status:** canonical story map for the public release.
**Date:** 2026-09-23
**Companions:** `PRODUCT_BLUEPRINT.md`, `../release-audit/USER_STORY_MAP` traceability
(`../release-audit/55-feature-test-traceability.md`), `../release-audit/final-go-no-go-decision.md`.

**124 stories** in 10 dependency-ordered epics. Every story uses the form
`As a [persona], I want [capability], so that [measurable outcome].`

## Status vocabulary

| Tag | Meaning |
|---|---|
| `VERIFIED` | The real user journey was executed end-to-end with reproducible evidence |
| `PARTIAL` | Works for a subset of the stated surface; the gap is documented |
| `NOT IMPLEMENTED` | The capability does not exist in the code |
| `NOT VERIFIED` | Code exists but no execution-backed evidence was produced |
| `BLOCKED` | Cannot be completed for an external reason |
| `EXPERIMENTAL` | Present but not positioned as production-ready |
| `RELEASE BLOCKER` | Must be resolved or explicitly limited before release |

## Story format

Each story carries: ID · Epic · Priority · Status · Complexity, then Persona, Story, Measurable
outcome, Dependencies, Files/modules affected, API impact, Data impact, UI impact, Security
impact, Performance impact, Negative scenarios, Given/When/Then acceptance criteria, Test plan,
Evidence required, Definition of done, Release impact.

Personas: **P1** plain-Java developer · **P2** test engineer · **P3** framework developer ·
**P4** data/platform engineer · **P5** evaluator · **P6** maintainer.

---

## Epic index

| Epic | Theme | Stories | Range |
|---|---|---|---|
| E1 | Product discovery & onboarding | 10 | US-001…010 |
| E2 | Installation & configuration | 8 | US-011…018 |
| E3 | SQL lifecycle (RDBMS) | 26 | US-019…044 |
| E4 | NoSQL lifecycle | 19 | US-045…063 |
| E5 | Both engines in one JVM | 6 | US-064…069 |
| E6 | Engine health & metrics | 8 | US-070…077 |
| E7 | Developer Console | 30 | US-078…099, US-138…145 |
| E8 | Website, branding & accessibility | 10 | US-100…109 |
| E9 | Framework & language integrations | 12 | US-110…121 |
| E10 | Security, perf, demos, packaging & release | 16 | US-122…137 |

## Status roll-up (2026-09-23)

| Status | Count | Notable IDs |
|---|---|---|
| `VERIFIED` | 116 | US-001, US-022, US-031 (**PRIMARY KEY**), US-032 (**foreign key**), US-033 (**UNIQUE/NOT NULL/CHECK**), US-043, US-062, US-093/095 (**explicit error and empty states**), US-094, US-122, US-138…144 (**Console task-success layer**) |
| `PARTIAL` | 22 | US-034 (no SQL `CREATE INDEX`), US-036 (SQL transaction granularity), US-042 (**JDBC driver** — no transactions/schema reflection), US-120, US-127 |
| `NOT IMPLEMENTED` | 5 | US-039/040/041 (procedures/functions/triggers), US-044 (`EXPLAIN`), US-145 (SQL syntax highlighting / autocomplete / formatting / saved queries / multi-tab / explain plan) |
| `NOT VERIFIED` | 1 | US-128 (Maven Central — credentials absent) |
| `EXPERIMENTAL` | 1 | US-074 (Kafka CDC connector) |
| `RELEASE BLOCKER` | 0 | — (all blockers either fixed, external, or documented limitations) |

Every `NOT IMPLEMENTED` story is a capability the product does not have and **must appear in the
published limitation list**. *(Correction trail 2026-09-23: the row above listing `NOT IMPLEMENTED`
as 7 after the first constraint slice was a miscount — six IDs remained; the referential and
`CHECK` stories then moved to `VERIFIED`, and US-042 became a working `PARTIAL` JDBC driver,
leaving **four**: procedures, functions, triggers and `EXPLAIN`.)* Every `PARTIAL` story carries a documented gap. No story is marked
`VERIFIED` without execution-backed evidence referenced in `../release-audit/evidence/`.

---

## Epic E1 — Product discovery & onboarding

#### US-001 · In-memory database in one call
`E1 · P0 · VERIFIED · S`
**Persona:** P1 Plain-Java developer
**Story:** As a plain-Java developer, I want `JunifyDB.inMemory()` to return a usable database, so that I can store and read data with zero infrastructure.
**Outcome:** One call yields a usable instance; insert → read round-trip succeeds.
**Deps:** —
**Files/modules:** `org.junify.db.JunifyDB`, `storage/spi/InMemoryEngine`
**API:** `JunifyDB.inMemory()`
**Data:** ephemeral only
**UI:** — · **Security:** — · **Perf:** opens fast, binds no port
**Negative:** use-after-close throws a clear error; double-close is safe
**G/W/T:** *Given* a fresh JVM, *When* `inMemory()` is called and a document is inserted, *Then* `findById` returns it.
**Test plan:** core unit test + demo smoke
**Evidence:** green test id + demo console output
**DoD:** acceptance passes; no skipped/ignored tests
**Release impact:** P0 core promise

#### US-002 · File-backed database in one call
`E1 · P0 · VERIFIED · S`
**Persona:** P1
**Story:** As a plain-Java developer, I want a file-backed database from a builder, so that data survives a restart without a server.
**Outcome:** writes persist across a clean JVM restart.
**Deps:** US-001
**Files/modules:** `JunifyDB.embed()`, `storage/spi/FileEngine`
**API:** `.storageEngine(FILE).persistTo(dir).autoFlush(true)`
**Data:** snapshot + WAL under `persistTo(dir)`
**UI:** — · **Security:** files created under the given dir only · **Perf:** measurable flush cadence
**Negative:** unwritable directory fails loudly; restart replays acknowledged writes
**G/W/T:** *Given* a FILE database, *When* a document is written and the JVM stops and restarts, *Then* the document is present.
**Test plan:** restart-durability test; contract gate block
**Evidence:** pre/post restart row counts
**DoD:** accepted write survives forced stop on FILE
**Release impact:** P0 durability promise

#### US-003 · Read the README and write running code in under 10 minutes
`E1 · P0 · PARTIAL · M`
**Persona:** P1
**Story:** As an evaluator, I want the README quick-start to be executable as written, so that I can prove the product works before committing.
**Outcome:** Every code block in the quick-start compiles and runs against the local build.
**Deps:** US-001, US-002
**Files/modules:** `README.md`, `demo/annotation-showcase-demo`
**API:** — · **Data:** local demo data · **UI:** — · **Security:** — · **Perf:** —
**Negative:** stale installed artifact makes demos resolve an old core (documented trap)
**G/W/T:** *Given* a clone and `mvn install`, *When* each README snippet is compiled, *Then* it runs with the documented result.
**Test plan:** manual compile-and-run of each snippet; snippet lint
**Evidence:** transcript per snippet
**DoD:** every snippet runs; discrepancies fixed or the snippet removed
**Release impact:** onboarding trust

#### US-004 · Discover the honest capability boundary up front
`E1 · P0 · VERIFIED · S`
**Persona:** P5 Evaluator
**Story:** As an evaluator, I want the README to state what the SQL engine does *not* do, so that I do not adopt it under a false assumption.
**Outcome:** The remaining limitations (no routines, no `EXPLAIN`, JDBC is `PARTIAL`) are visible before the first API call.
**Deps:** —
**Files/modules:** `README.md`, `docs/index.html`
**API:** — · **Data:** — · **UI:** website · **Security:** — · **Perf:** —
**Negative:** limitations relegated to a footnote
**G/W/T:** *Given* the README, *When* a reader looks for the SQL boundary, *Then* the unsupported list is present and accurate.
**Test plan:** documentation review vs `SqlParser` capabilities
**Evidence:** grep of unsupported list; parser feature list
**DoD:** limitation text matches parser reality
**Release impact:** honesty gate

#### US-005 · Choose between SQL and NoSQL without reading source
`E1 · P1 · VERIFIED · S`
**Persona:** P1
**Story:** As a developer, I want a side-by-side comparison of the two engines, so that I can pick the right API first time.
**Outcome:** One table maps each persona/task to the recommended engine.
**Deps:** —
**Files/modules:** `docs/product/PRODUCT_BLUEPRINT.md`, `README.md`
**API:** — · **Data:** — · **UI:** — · **Security:** — · **Perf:** —
**Negative:** comparison presents SQL as fully relational
**G/W/T:** *Given* the blueprint engine table, *When* a developer reads it, *Then* the "NOT implemented" column is present.
**Test plan:** review
**Evidence:** doc reference
**DoD:** table accurately mirrors `05/06` audits
**Release impact:** positioning clarity

#### US-006 · Understand storage-engine trade-offs
`E1 · P1 · VERIFIED · S`
**Persona:** P1
**Story:** As a developer, I want the four storage engines compared by backing structure and durability, so that I can select one knowingly.
**Outcome:** Each engine's persistence and WAL behavior is stated.
**Deps:** US-002
**Files/modules:** `README.md`, `docs/release-audit/16-durability-and-persistence.md`
**API:** `StorageEngineType`
**Data:** per-engine layout · **UI:** — · **Security:** — · **Perf:** engine-specific
**Negative:** a reader assumes B_TREE loses writes (no longer true)
**G/W/T:** *Given* the storage table, *When* a developer selects an engine, *Then* the durability column matches measured behavior.
**Test plan:** per-engine durability test
**Evidence:** gate output on FILE/LSM/B_TREE
**DoD:** table matches the write-ahead guarantee now shared by all three
**Release impact:** correctness of docs

#### US-007 · Copy a minimal dependency block
`E1 · P0 · PARTIAL · S`
**Persona:** P3
**Story:** As a framework developer, I want exact Maven coordinates and installation steps, so that I can add the dependency without guessing.
**Outcome:** Coordinates resolve after a documented local install.
**Deps:** —
**Files/modules:** `README.md`, `spring-boot-starter/pom.xml`, `quarkus-extension`, `micronaut-integration`
**API:** — · **Data:** — · **UI:** — · **Security:** — · **Perf:** —
**Negative:** coordinates do not resolve on Central (clearly stated)
**G/W/T:** *Given* the install step, *When* run, *Then* the artifacts are in the local repo and the coordinates resolve.
**Test plan:** `mvn -DskipTests install` then resolve
**Evidence:** install log
**DoD:** copy-paste coordinates work after the documented install
**Release impact:** onboarding

#### US-008 · Recognize the brand and mascot on first contact
`E1 · P1 · VERIFIED · S`
**Persona:** P5
**Story:** As a visitor, I want a consistent logo and mascot across site and Console, so that the project reads as one product.
**Outcome:** Same mark/favicon on website and Console.
**Deps:** —
**Files/modules:** `docs/assets/junifydb-*`, Console `static/`, favicon
**API:** — · **Data:** — · **UI:** website + Console · **Security:** — · **Perf:** asset weight
**Negative:** mismatched or missing mascot
**G/W/T:** *Given* the website and the Console, *When* both are opened, *Then* the same logo/mark renders with correct aspect ratio.
**Test plan:** visual check; asset checksum compare
**Evidence:** `../release-audit/44-brand-and-design-system.md`
**DoD:** assets identical and referenced from one source
**Release impact:** brand gate

#### US-009 · See real screenshots of the Console before installing
`E1 · P2 · PARTIAL · S`
**Persona:** P5
**Story:** As an evaluator, I want screenshots of the Console, so that I can judge the UI without running it.
**Outcome:** Website shows current-state screenshots.
**Deps:** US-008
**Files/modules:** `docs/index.html`, `docs/assets/`
**API:** — · **Data:** — · **UI:** website · **Security:** — · **Perf:** —
**Negative:** screenshots show features that do not exist
**G/W/T:** *Given* the website, *When* screenshots are viewed, *Then* they match the shipped Console.
**Test plan:** visual comparison after a fresh capture
**Evidence:** screenshot files + capture date
**DoD:** images regenerated from the released build
**Release impact:** honesty

#### US-010 · Report a problem through a documented channel
`E1 · P1 · VERIFIED · S`
**Persona:** P6
**Story:** As a user, I want SECURITY/CONTRIBUTING/issue links that work, so that I can report a defect.
**Outcome:** Links resolve and channels are stated.
**Deps:** —
**Files/modules:** `SECURITY.md`, `CONTRIBUTING.md`, `pom.xml` issueManagement
**API:** — · **Data:** — · **UI:** — · **Security:** disclosure policy · **Perf:** —
**Negative:** dead links
**G/W/T:** *Given* the repo, *When* the report links are followed, *Then* they resolve.
**Test plan:** link check
**Evidence:** resolver output
**DoD:** all public links return 200
**Release impact:** governance

---

## Epic E2 — Installation & configuration

#### US-011 · Configure the database with zero config files
`E2 · P0 · VERIFIED · S`
**Persona:** P1
**Story:** As a developer, I want sane defaults with no config file, so that I can start instantly.
**Outcome:** in-memory + file modes work with no properties, no env vars.
**Deps:** US-001
**Files/modules:** `config/JunifyDBConfig`, `config/ConfigurationResolver`
**API:** builder defaults
**Data:** default data dir · **UI:** — · **Security:** secure-by-default CORS/auth · **Perf:** —
**Negative:** a missing value silently changes durability semantics
**G/W/T:** *Given* no configuration, *When* an instance is created, *Then* it is usable with documented defaults.
**Test plan:** config defaults test
**Evidence:** test output
**DoD:** every default documented and asserted
**Release impact:** P0

#### US-012 · Override any setting with typed config
`E2 · P1 · VERIFIED · M`
**Persona:** P3
**Story:** As a framework developer, I want typed overrides (engine, data dir, flush), so that environments differ without code changes.
**Outcome:** All documented properties bind and take effect.
**Deps:** US-011
**Files/modules:** `config/*`, framework starters
**API:** `JunifyDBConfig` builder + property binding
**Data:** engine/data-dir selection · **UI:** — · **Security:** — · **Perf:** flush interval
**Negative:** unknown property ignored silently
**G/W/T:** *Given* an override, *When* the app starts, *Then* the observable behavior matches.
**Test plan:** binding test per framework
**Evidence:** per-property effect test
**DoD:** every documented property has a test
**Release impact:** integration

#### US-013 · Know which settings are mandatory vs optional
`E2 · P1 · VERIFIED · S`
**Persona:** P5
**Story:** As an evaluator, I want the property table to mark required vs optional, so that I configure only what matters.
**Outcome:** Table states required-ness and default for each property.
**Deps:** US-012
**Files/modules:** `README.md`, `docs/api/`
**API:** — · **Data:** — · **UI:** — · **Security:** — · **Perf:** —
**Negative:** a property presented as required is actually optional
**G/W/T:** *Given* the property table, *When* compared to `ConfigurationResolver`, *Then* required-ness matches.
**Test plan:** review against code
**Evidence:** doc + code cross-check
**DoD:** no mislabelled properties
**Release impact:** onboarding

#### US-014 · Fail fast on an unwritable data directory
`E2 · P1 · VERIFIED · S`
**Persona:** P1
**Story:** As a developer, I want a clear error when the data directory cannot be written, so that I do not discover it at commit time.
**Outcome:** Startup fails loudly with an actionable message.
**Deps:** US-002
**Files/modules:** `storage/spi/FileEngine`
**API:** — · **Data:** — · **UI:** — · **Security:** — · **Perf:** —
**Negative:** write fails only after many operations
**G/W/T:** *Given* a read-only path, *When* the database opens, *Then* it throws with the path named.
**Test plan:** negative-path test
**Evidence:** exception message
**DoD:** fails at open, message names the path
**Release impact:** reliability

#### US-015 · Select a storage engine via one setting
`E2 · P1 · VERIFIED · S`
**Persona:** P1
**Story:** As a developer, I want to switch storage engine with one value, so that I can trade durability against speed.
**Outcome:** Same code runs on all four engines.
**Deps:** US-002
**Files/modules:** `StorageEngineType`, `storage/spi/*`
**API:** `.storageEngine(...)`
**Data:** engine-specific layout · **UI:** — · **Security:** — · **Perf:** engine-specific
**Negative:** an engine ignores a setting it documents as honored
**G/W/T:** *Given* each engine, *When* the same workload runs, *Then* the API behavior is identical.
**Test plan:** engine matrix test
**Evidence:** per-engine run
**DoD:** matrix green for all four
**Release impact:** P0

#### US-016 · Install the framework starters locally
`E2 · P1 · PARTIAL · M`
**Persona:** P3
**Story:** As a framework developer, I want each starter to build after a core install, so that I can use it immediately.
**Outcome:** `mvn install` on each starter succeeds.
**Deps:** US-007
**Files/modules:** `spring-boot-starter`, `quarkus-extension`, `micronaut-integration`, `cli`
**API:** — · **Data:** — · **UI:** — · **Security:** — · **Perf:** —
**Negative:** starter resolves a stale core
**G/W/T:** *Given* a core install, *When* each starter builds, *Then* it compiles and tests green.
**Test plan:** per-starter `mvn test`
**Evidence:** build logs
**DoD:** all four build from the documented order
**Release impact:** ecosystem

#### US-017 · Verify reproducible builds
`E2 · P6 · VERIFIED · M`
**Persona:** P6
**Story:** As a maintainer, I want byte-stable outputs, so that I can trust release artifacts.
**Outcome:** Two builds produce identical jar checksums.
**Deps:** —
**Files/modules:** `pom.xml` (`project.build.outputTimestamp`), `scripts/reproducibility-check.sh`
**API:** — · **Data:** — · **UI:** — · **Security:** supply chain · **Perf:** —
**Negative:** timestamps embedded make builds differ
**G/W/T:** *Given* two clean builds, *When* checksums are compared, *Then* they match.
**Test plan:** reproducibility script
**Evidence:** checksum pair
**DoD:** script PASS
**Release impact:** release integrity

#### US-018 · Offline / air-gapped operation
`E2 · P1 · VERIFIED · S`
**Persona:** P1
**Story:** As a developer on a restricted network, I want no runtime downloads, so that the database works offline.
**Outcome:** No network calls at runtime.
**Deps:** —
**Files/modules:** core runtime
**API:** — · **Data:** — · **UI:** — · **Security:** — · **Perf:** —
**Negative:** a feature silently phones home
**G/W/T:** *Given* a disconnected host, *When* the database runs any supported operation, *Then* it works.
**Test plan:** offline run
**Evidence:** run with network disabled
**DoD:** no outbound calls observed
**Release impact:** P0 embedded promise

---

## Epic E3 — SQL lifecycle (RDBMS surface)

#### US-019 · Create a table
`E3 · P0 · VERIFIED · S`
**Persona:** P1
**Story:** As a developer, I want `CREATE TABLE` to define a SQL table, so that I can insert rows through SQL.
**Outcome:** Table is queryable immediately after creation.
**Deps:** US-001
**Files/modules:** `sql/parser/SqlParser` (`parseCreate`), `sql/engine/SqlEngine`
**API:** `db.sql("CREATE TABLE ...")`
**Data:** creates an addressing entry · **UI:** Console SQL workspace · **Security:** — · **Perf:** O(1) DDL
**Negative:** duplicate table creation; invalid column list
**G/W/T:** *Given* an empty database, *When* `CREATE TABLE t (id, name)` runs, *Then* `SELECT * FROM t` returns zero rows without error.
**Test plan:** DDL unit + integration test
**Evidence:** green test id
**DoD:** acceptance passes
**Release impact:** P0 SQL baseline

#### US-020 · Drop a table
`E3 · P1 · VERIFIED · S`
**Persona:** P1
**Story:** As a developer, I want `DROP TABLE` to remove a table's rows, so that I can reset state.
**Outcome:** Rows are gone; a missing table errors.
**Deps:** US-019
**Files/modules:** `sql/parser` (`parseDrop`), `sql/engine/SqlEngine`
**API:** `DROP TABLE`
**Data:** empties rows (does not remove the collection — documented)
**UI:** Console · **Security:** — · **Perf:** —
**Negative:** `DROP` on a missing table reports false success
**G/W/T:** *Given* a populated table, *When* `DROP TABLE` runs, *Then* a subsequent `SELECT` returns no rows; *And* `DROP` on a missing table errors.
**Test plan:** DDL test
**Evidence:** test id
**DoD:** error semantics asserted
**Release impact:** correctness

#### US-021 · A created empty table survives restart
`E3 · P0 · VERIFIED · M`
**Persona:** P1
**Story:** As a developer, I want an empty created table to exist after restart, so that "success" means durable.
**Outcome:** Empty table answers `rowCount:0` after a restart.
**Deps:** US-019, US-002
**Files/modules:** storage SPI `ensureCollection` hook, all four engines
**API:** —
**Data:** snapshot/registry entry on disk · **UI:** Console · **Security:** — · **Perf:** —
**Negative:** `CREATE TABLE` reports success but the name disappears on restart (historical R-62)
**G/W/T:** *Given* a FILE database, *When* an empty table is created and the server restarts, *Then* `SELECT` returns 0 rows, not "table does not exist".
**Test plan:** restart test on FILE/LSM/B_TREE
**Evidence:** pre/post restart result
**DoD:** verified on all three persistent engines
**Release impact:** durability honesty

#### US-022 · Insert rows
`E3 · P0 · VERIFIED · S`
**Persona:** P1
**Story:** As a developer, I want `INSERT` to add rows, so that I can populate a table.
**Outcome:** Inserted rows are readable.
**Deps:** US-019
**Files/modules:** `sql/engine/SqlEngine`
**API:** `INSERT INTO ... VALUES (...)`
**Data:** writes rows · **UI:** Console · **Security:** — · **Perf:** batched
**Negative:** NULL/missing column handling
**G/W/T:** *Given* a table, *When* two rows are inserted, *Then* `SELECT` returns both.
**Test plan:** CRUD test
**Evidence:** test id
**DoD:** acceptance passes
**Release impact:** P0

#### US-023 · Select rows with a filter
`E3 · P0 · VERIFIED · S`
**Persona:** P4
**Story:** As a data engineer, I want `SELECT ... WHERE`, so that I can query a subset.
**Outcome:** Filtered result matches expected rows.
**Deps:** US-022
**Files/modules:** `sql/engine/SqlEngine`
**API:** `WHERE a = ? AND b < ?`
**Data:** read-only · **UI:** Console · **Security:** — · **Perf:** table scan unless indexed
**Negative:** reads must not create a collection (R-48)
**G/W/T:** *Given* populated rows, *When* `WHERE` filters, *Then* exactly the matching rows return and no new collection appears.
**Test plan:** query test + catalog-immutability test
**Evidence:** test ids
**DoD:** filter correctness + no auto-create
**Release impact:** correctness

#### US-024 · Update rows
`E3 · P0 · VERIFIED · S`
**Persona:** P1
**Story:** As a developer, I want `UPDATE ... WHERE`, so that I can change existing rows.
**Outcome:** Only matching rows change.
**Deps:** US-022
**Files/modules:** `sql/engine/SqlEngine`
**API:** `UPDATE t SET c = ? WHERE ...`
**Data:** mutation · **UI:** Console · **Security:** — · **Perf:** —
**Negative:** `UPDATE` on a missing table must not create it
**G/W/T:** *Given* rows, *When* `UPDATE` matches a subset, *Then* only those change and a missing table errors.
**Test plan:** mutation test
**Evidence:** test id
**DoD:** no auto-create on missing table
**Release impact:** correctness

#### US-025 · Delete rows
`E3 · P0 · VERIFIED · S`
**Persona:** P1
**Story:** As a developer, I want `DELETE ... WHERE`, so that I can remove rows.
**Outcome:** Only matching rows are removed.
**Deps:** US-022
**Files/modules:** `sql/engine/SqlEngine`
**API:** `DELETE FROM t WHERE ...`
**Data:** mutation · **UI:** Console · **Security:** — · **Perf:** —
**Negative:** delete on missing table must not create it
**G/W/T:** *Given* rows, *When* `DELETE` runs, *Then* the count drops by the matched number.
**Test plan:** mutation test
**Evidence:** test id
**DoD:** acceptance passes
**Release impact:** P0

#### US-026 · Sort and page results
`E3 · P1 · VERIFIED · S`
**Persona:** P4
**Story:** As a data engineer, I want `ORDER BY`, `LIMIT`, `OFFSET`, so that I can page large results.
**Outcome:** Stable ordering across pages.
**Deps:** US-023
**Files/modules:** `sql/engine/SqlEngine`
**API:** `ORDER BY ... LIMIT ... OFFSET ...`
**Data:** read-only · **UI:** Console · **Security:** — · **Perf:** O(n log n)
**Negative:** ties across pages skip/duplicate rows
**G/W/T:** *Given* ordered rows, *When* paged, *Then* pages concatenate to the full set with no duplicates.
**Test plan:** paging test
**Evidence:** test id
**DoD:** concatenation property asserted
**Release impact:** usability

#### US-027 · Aggregate with GROUP BY / HAVING
`E3 · P1 · VERIFIED · M`
**Persona:** P4
**Story:** As a data engineer, I want `GROUP BY`/`HAVING` and aggregates, so that I can summarize.
**Outcome:** Group counts/sums match a hand computation.
**Deps:** US-023
**Files/modules:** `sql/engine/SqlEngine`
**API:** `COUNT/SUM/AVG/MIN/MAX`, `GROUP BY`, `HAVING`
**Data:** read-only · **UI:** Console · **Security:** — · **Perf:** O(n)
**Negative:** null group handling; HAVING without GROUP BY
**G/W/T:** *Given* grouped rows, *When* aggregated, *Then* each group's value equals the expected value.
**Test plan:** aggregation test
**Evidence:** test id + demo output
**DoD:** acceptance passes
**Release impact:** SQL usefulness

#### US-028 · Join two tables
`E3 · P1 · VERIFIED · M`
**Persona:** P4
**Story:** As a data engineer, I want `INNER JOIN`, so that I can correlate data.
**Outcome:** Joined rows match expected pairs.
**Deps:** US-023
**Files/modules:** `sql/engine/SqlEngine`
**API:** `INNER JOIN ... ON ...`
**Data:** read-only · **UI:** Console · **Security:** — · **Perf:** nested loop
**Negative:** join on a missing table must not create it
**G/W/T:** *Given* two tables, *When* joined, *Then* the result set is the correct correlation and the catalog is unchanged.
**Test plan:** join test + catalog test
**Evidence:** test id
**DoD:** acceptance passes
**Release impact:** SQL breadth

#### US-029 · Filter with expressions and predicates
`E3 · P1 · VERIFIED · S`
**Persona:** P4
**Story:** As a data engineer, I want `BETWEEN`, `LIKE`, `IN`, boolean operators, so that I can express real predicates.
**Outcome:** Predicates evaluate as documented.
**Deps:** US-023
**Files/modules:** `sql/parser`, `sql/engine`
**API:** predicate grammar
**Data:** read-only · **UI:** Console · **Security:** — · **Perf:** —
**Negative:** `LIKE` wildcard semantics differ from ANSI
**G/W/T:** *Given* representative data, *When* each predicate runs, *Then* the result matches the documented semantics.
**Test plan:** predicate test
**Evidence:** test id
**DoD:** semantics documented and asserted
**Release impact:** SQL breadth

#### US-030 · Unknown-table reads never mutate the catalog
`E3 · P0 · VERIFIED · S`
**Persona:** P4
**Story:** As a data engineer, I want reads on a missing table to 404, so that a typo does not create junk.
**Outcome:** Read/DELETE on unknown table returns an error and leaves the catalog untouched.
**Deps:** US-023
**Files/modules:** `SqlUnknownTableException`, Console routes
**API:** 404 semantics
**Data:** no catalog write · **UI:** Console error state · **Security:** — · **Perf:** —
**Negative:** mistyped name creates a permanent collection (historical R-48/R-64)
**G/W/T:** *Given* an unknown table, *When* `SELECT`/`UPDATE`/`DELETE`/`GET indexes` runs, *Then* it errors and no collection appears.
**Test plan:** catalog-immutability test
**Evidence:** catalog snapshot before/after
**DoD:** no read path creates state
**Release impact:** data integrity

#### US-031 · Primary-key constraint
`E3 · P0 · VERIFIED · L`
**Persona:** P1
**Story:** As a developer, I want primary keys enforced, so that duplicate identities cannot exist.
**Outcome:** A duplicate PK insert is rejected and the existing row is untouched.
**Deps:** US-019
**Files/modules:** `sql/parser/SqlParser` (column/table-level `PRIMARY KEY`), `sql/engine/SqlEngine` (`enforceConstraints`), `sql/SqlTableSchema`, `sql/SqlSchemaCatalog`
**API:** inline `PRIMARY KEY` and table-level `PRIMARY KEY (col)`; `SqlConstraintViolationException`
**Data:** schema persisted in reserved collection `__junify_sql_schema` · **UI:** Console surfaces the 400 message · **Security:** integrity · **Perf:** O(1) for the `id` key
**Negative:** a duplicate silently overwrote the row (pre-fix, measured); a null key auto-generated
**G/W/T:** *Given* a PK table, *When* a duplicate key is inserted, *Then* `SqlConstraintViolationException` is thrown and the original row remains.
**Test plan:** `SqlConstraintTest.duplicatePrimaryKeyIsRejected`, `primaryKeyRejectsNull`, `tableLevelConstraintsAreEnforced`, `constraintsSurviveRestart`
**Evidence:** 14/14 green; falsified against the pre-change jar (duplicate PK accepted, row count 2)
**DoD:** acceptance passes on inline and table-level forms; enforced after restart on FILE/LSM/B_TREE
**Release impact:** removes a release-blocking limitation

#### US-032 · Foreign-key constraint
`E3 · P2 · VERIFIED · L`
**Persona:** P1
**Story:** As a developer, I want FK integrity, so that orphans cannot be created.
**Outcome:** An orphan child row is rejected on INSERT/UPDATE, and a referenced parent cannot be removed by DELETE or DROP TABLE.
**Deps:** US-031
**Files/modules:** `sql/parser/SqlParser` (`REFERENCES`), `sql/engine/SqlEngine` (`enforceConstraints`, `assertNoIncomingReferences`), `sql/SqlTableSchema`
**API:** inline `REFERENCES t(col)` and table-level `FOREIGN KEY (col) REFERENCES t(col)`; `SqlConstraintViolationException`
**Data:** FK metadata persisted · **UI:** Console error message · **Security:** integrity · **Perf:** child-side is O(1) for an `id` parent, O(n) otherwise; parent-side DELETE scans referencing rows
**Negative:** silent orphans; validating an FK must not create the referenced table (R-48 class)
**G/W/T:** *Given* a child FK, *When* an orphan is inserted, *Then* it is rejected and the referenced table is not created; *And* a referenced parent cannot be deleted or dropped.
**Test plan:** `SqlReferentialConstraintTest` (13 tests, child + parent side, table-level, durability)
**Evidence:** 13/13 green; falsified against the pre-change jar (`PRE-FIX orphan FK: ACCEPTED`)
**DoD:** both directions enforced; enforced after restart on FILE/LSM/B_TREE
**Release impact:** removes a limitation

#### US-033 · Unique / check / not-null constraints
`E3 · P2 · VERIFIED · L`
**Persona:** P1
**Story:** As a developer, I want unique/check/not-null enforced, so that data stays valid.
**Outcome:** `UNIQUE`, `NOT NULL` and `CHECK` are enforced.
**Deps:** US-031
**Files/modules:** `sql/parser/SqlParser`, `sql/engine/SqlEngine`, `sql/SqlTableSchema`
**API:** inline and table-level `UNIQUE`; inline `NOT NULL`
**Data:** schema persisted · **UI:** Console error message · **Security:** integrity · **Perf:** UNIQUE on a non-id column is an O(n) scan (documented limit)
**Negative:** invalid data stored silently (pre-fix, measured)
**G/W/T:** *Given* a `UNIQUE`/`NOT NULL` rule, *When* a violating row is inserted, *Then* it is rejected; `UNIQUE` allows multiple nulls.
**Test plan:** `SqlConstraintTest.uniqueRejectsDuplicate`, `uniqueAllowsMultipleNulls`, `notNullRejectsExplicitNull`, `notNullRejectsOmittedValue`, `updateViolatingUniqueIsRejected`
**Evidence:** green; falsified against the pre-change jar (NULL accepted into a NOT NULL column)
**DoD:** all three enforced (column- and table-level `CHECK`); two-valued null semantics documented
**Release impact:** removes a limitation

#### US-034 · Create an index
`E3 · P1 · PARTIAL · M`
**Persona:** P4
**Story:** As a data engineer, I want to add an index, so that range/filter queries are fast.
**Outcome:** An index added via the NoSQL/Console path accelerates matching queries.
**Deps:** US-023
**Files/modules:** `index/SecondaryIndex`, `index/TextIndex`, `index/hnsw`, Console indexes route
**API:** B-tree + HNSW via NoSQL/Console; **no `CREATE INDEX` in SQL**
**Data:** index sidecar · **UI:** Console Indexes panel · **Security:** — · **Perf:** speeds filters
**Negative:** adding an index on unknown collection auto-creates it (read must not)
**G/W/T:** *Given* a collection, *When* an index is added, *Then* matching queries use it and the catalog change is intentional.
**Test plan:** index test + unknown-collection 404 test
**Evidence:** test ids
**DoD:** SQL `CREATE INDEX` documented as unsupported
**Release impact:** honest indexing story

#### US-035 · Range queries use the B-tree index
`E3 · P1 · VERIFIED · M`
**Persona:** P4
**Story:** As a data engineer, I want range queries to use an index, so that I avoid full scans.
**Outcome:** Indexed range query is materially faster than scan on representative data.
**Deps:** US-034
**Files/modules:** `storage/spi/BTreeEngine`, `index/SecondaryIndex`
**API:** NoSQL criteria
**Data:** index-driven · **UI:** — · **Security:** — · **Perf:** verified improvement
**Negative:** index desync with base data
**G/W/T:** *Given* an indexed field, *When* a range query runs, *Then* results are correct and the index is consulted.
**Test plan:** index correctness test
**Evidence:** timing + result equality
**DoD:** correctness proven; speedup measured or not claimed
**Release impact:** performance claim honesty

#### US-036 · Run SQL inside a transaction
`E3 · P1 · PARTIAL · M`
**Persona:** P1
**Story:** As a developer, I want SQL statements to participate in transactions, so that I can commit or roll back a unit.
**Outcome:** A rollback undoes the SQL writes in the unit.
**Deps:** US-022, US-037
**Files/modules:** `transaction/mvcc/*`, `sql/engine/SqlEngine`
**API:** `db.transactionManager().inTransaction(...)`
**Data:** transactional · **UI:** Console Transactions panel · **Security:** — · **Perf:** —
**Negative:** partial application on failure
**G/W/T:** *Given* a transaction, *When* it rolls back, *Then* none of its SQL writes are visible.
**Test plan:** transaction test
**Evidence:** visible-state assertion
**DoD:** atomicity asserted; SQL-surface granularity limitation documented
**Release impact:** ACID claim scoping

#### US-037 · MVCC isolation and savepoints
`E3 · P1 · VERIFIED · M`
**Persona:** P1
**Story:** As a developer, I want snapshot isolation with savepoints, so that concurrent readers are not blocked.
**Outcome:** Uncommitted writes are invisible; savepoint rollback restores intermediate state.
**Deps:** US-036
**Files/modules:** `transaction/mvcc/MVCCManager`, `transaction/mvcc/Transaction`
**API:** savepoint API
**Data:** versioned docs · **UI:** — · **Security:** — · **Perf:** —
**Negative:** lost update under mixed writers (documented R-20)
**G/W/T:** *Given* two transactions, *When* one is uncommitted, *Then* the other does not see its writes; *And* a savepoint rollback restores the saved state.
**Test plan:** isolation + savepoint tests
**Evidence:** test ids
**DoD:** assertions pass
**Release impact:** concurrency story

#### US-038 · SQL errors roll back cleanly
`E3 · P0 · VERIFIED · S`
**Persona:** P1
**Story:** As a developer, I want a failing statement to leave no partial effect, so that data stays consistent.
**Outcome:** After an error, the pre-statement state is intact.
**Deps:** US-036
**Files/modules:** `sql/engine/SqlEngine`
**API:** error semantics
**Data:** unchanged on error · **UI:** Console error state · **Security:** — · **Perf:** —
**Negative:** half-applied statement
**G/W/T:** *Given* a multi-row statement that fails midway, *When* it errors, *Then* no partial write remains.
**Test plan:** fault-injection test
**Evidence:** state comparison
**DoD:** no partial writes observed
**Release impact:** integrity

#### US-039 · Stored procedures
`E3 · P2 · NOT IMPLEMENTED · L`
**Persona:** P1
**Story:** As a developer, I want stored procedures, so that I can centralize logic.
**Outcome:** (target) create + call a procedure.
**Deps:** US-036
**Files/modules:** none
**API:** none · **Data:** none · **UI:** none · **Security:** — · **Perf:** —
**Negative:** advertised but absent
**G/W/T:** *Given* a procedure, *When* called, *Then* it runs. *(Not implemented — must be stated as unsupported.)*
**Test plan:** —
**Evidence:** explicit unsupported statement
**DoD:** explicitly unsupported in README/website
**Release impact:** limitation list

#### US-040 · User-defined functions
`E3 · P2 · NOT IMPLEMENTED · L`
**Persona:** P1
**Story:** As a developer, I want scalar functions, so that I can reuse expressions.
**Outcome:** (target) define + use a function.
**Deps:** US-039
**Files/modules:** none
**API:** none · **Data:** none · **UI:** none · **Security:** — · **Perf:** —
**Negative:** advertised but absent
**G/W/T:** *Given* a function, *When* used in a query, *Then* it evaluates. *(Not implemented.)*
**Test plan:** —
**Evidence:** unsupported statement
**DoD:** explicitly unsupported
**Release impact:** limitation list

#### US-041 · Triggers
`E3 · P2 · NOT IMPLEMENTED · L`
**Persona:** P1
**Story:** As a developer, I want triggers, so that I can react to mutations declaratively.
**Outcome:** (target) trigger fires on write.
**Deps:** US-039
**Files/modules:** none (event bus exists but is not SQL triggers)
**API:** event bus only
**Data:** none · **UI:** none · **Security:** — · **Perf:** —
**Negative:** triggers confused with the event bus
**G/W/T:** *Given* a trigger, *When* a row is written, *Then* it fires. *(Not implemented; the event bus is the closest feature and must be described as such.)*
**Test plan:** —
**Evidence:** unsupported statement
**DoD:** explicitly unsupported
**Release impact:** limitation list

#### US-042 · JDBC driver
`E3 · P0 · PARTIAL · XL`
**Persona:** P1
**Story:** As a Java developer, I want a JDBC driver, so that existing tools and ORMs can connect.
**Outcome:** `DriverManager.getConnection("jdbc:junifydb:...")` works, with `Statement`/`PreparedStatement` and a forward-only read-only `ResultSet`; transactions and schema reflection are not implemented.
**Deps:** US-023
**Files/modules:** `org.junify.db.jdbc` (Driver, Connection/Statement/ResultSet/DatabaseMetaData handlers), `META-INF/services/java.sql.Driver`
**API:** `jdbc:junifydb:memory:` and `jdbc:junifydb:file:<dir>`; `Driver.jdbcCompliant()` returns **false**
**Data:** per URL · **UI:** — · **Security:** parameters bound by the engine, never string-concatenated · **Perf:** unsupported calls throw rather than degrade silently
**Negative:** claiming JDBC compliance; `DriverManager` discovering the driver class but never registering it (found and fixed)
**G/W/T:** *Given* the JDBC URL, *When* a `PreparedStatement` binds `?` and executes, *Then* rows return and constraint violations surface as `SQLException`.
**Test plan:** `JdbcDriverTest` (13 tests) + a compiled-consumer run against the shaded jar
**Evidence:** 13/13 green; ServiceLoader discovery proven on a real consumer classpath; falsified against the pre-change jar (`No suitable driver found`)
**DoD:** supported surface works; unsupported surface throws `SQLFeatureNotSupportedException`; docs state the `PARTIAL` boundary
**Release impact:** removes the largest gap as a *blocker*; JDBC compliance still **not** claimed

#### US-043 · SQL data survives restart
`E3 · P0 · VERIFIED · M`
**Persona:** P4
**Story:** As a data engineer, I want SQL writes to survive restart, so that persistence is trustworthy.
**Outcome:** 12 of 12 acknowledged rows return with full bodies after a forced stop.
**Deps:** US-002, US-022
**Files/modules:** storage SPI + WAL, all three persistent engines
**API:** — · **Data:** WAL + snapshot · **UI:** — · **Security:** — · **Perf:** fsync per write
**Negative:** acknowledged writes lost across a checkpoint/rotation/kill (historical R-66/R-69…R-73)
**G/W/T:** *Given* a FILE/LSM/B_TREE database, *When* the server is force-stopped and restarted, *Then* every acknowledged row (with its body) is readable.
**Test plan:** crash-durability gate block on 3 engines
**Evidence:** gate output 12/12 per engine
**DoD:** gate PASS proving stop, restart, and replay
**Release impact:** P0 durability

#### US-044 · EXPLAIN / query planner
`E3 · P3 · NOT IMPLEMENTED · L`
**Persona:** P4
**Story:** As a data engineer, I want `EXPLAIN`, so that I can understand query cost.
**Outcome:** (target) plan text returned.
**Deps:** US-023
**Files/modules:** execution is interpretation; no planner
**API:** none (a NoSQL `QueryExplain` exists, not SQL `EXPLAIN`)
**Data:** — · **UI:** — · **Security:** — · **Perf:** —
**Negative:** implying a cost-based optimizer exists
**G/W/T:** *Given* a query, *When* `EXPLAIN` runs, *Then* a plan is returned. *(Not implemented for SQL.)*
**Test plan:** —
**Evidence:** unsupported statement
**DoD:** documented unsupported; `QueryExplain` described as NoSQL-only
**Release impact:** limitation list

---

## Epic E4 — NoSQL lifecycle (non-relational surface)

#### US-045 · Open a document collection
`E4 · P0 · VERIFIED · S`
**Persona:** P1
**Story:** As a developer, I want `documentCollection(name)`, so that I can store documents without DDL.
**Outcome:** Collection is addressed and queryable.
**Deps:** US-001
**Files/modules:** `nosql/document/DocumentCollection`, `JunifyDB`
**API:** `db.documentCollection("users")`
**Data:** creates a collection on write only · **UI:** Console · **Security:** — · **Perf:** O(1)
**Negative:** reads on a missing collection must not create it (R-48)
**G/W/T:** *Given* an empty database, *When* a collection is opened and written, *Then* `findById` returns the document; a bare read does not create the collection.
**Test plan:** collection test + no-create test
**Evidence:** test ids
**DoD:** acceptance + no-create pass
**Release impact:** P0

#### US-046 · Insert, replace, and delete documents
`E4 · P0 · VERIFIED · S`
**Persona:** P1
**Story:** As a developer, I want document CRUD, so that I can manage records.
**Outcome:** Insert/replace/delete are reflected in reads and counts.
**Deps:** US-045
**Files/modules:** `nosql/document/DocumentCollection`, `Document`, `VersionedDocument`
**API:** `insert`, `update`, `delete`, `count`
**Data:** mutation · **UI:** Console · **Security:** — · **Perf:** —
**Negative:** delete then read resurrection (historical LSM bug)
**G/W/T:** *Given* a document, *When* deleted, *Then* all reads (find, query, count) agree it is gone.
**Test plan:** CRUD consistency test
**Evidence:** test ids
**DoD:** cross-consumer agreement asserted
**Release impact:** P0 correctness

#### US-047 · Store nested documents and arrays
`E4 · P1 · VERIFIED · S`
**Persona:** P1
**Story:** As a developer, I want nested objects and arrays, so that I model real data.
**Outcome:** Nested values round-trip without loss.
**Deps:** US-046
**Files/modules:** `nosql/document/Document`
**API:** map/list values
**Data:** nested · **UI:** Console preview · **Security:** — · **Perf:** serialization cost
**Negative:** deep nesting depth limits
**G/W/T:** *Given* a nested document, *When* stored and read, *Then* it is structurally identical.
**Test plan:** round-trip test
**Evidence:** test id
**DoD:** equality asserted
**Release impact:** modeling

#### US-048 · Project only needed fields
`E4 · P1 · VERIFIED · S`
**Persona:** P4
**Story:** As a data engineer, I want projections, so that I transfer less data.
**Outcome:** Only selected fields return.
**Deps:** US-046
**Files/modules:** `nosql/document/DocumentCollection`
**API:** projection argument
**Data:** read-only · **UI:** Console · **Security:** — · **Perf:** smaller payloads
**Negative:** missing field vs null confusion
**G/W/T:** *Given* a document, *When* projected, *Then* exactly the requested fields return.
**Test plan:** projection test
**Evidence:** test id
**DoD:** acceptance passes
**Release impact:** usability

#### US-049 · Fetch a document by id
`E4 · P0 · VERIFIED · S`
**Persona:** P1
**Story:** As a developer, I want `findById`, so that I can retrieve a known record cheaply.
**Outcome:** Returns the document or empty.
**Deps:** US-046
**Files/modules:** `nosql/document/DocumentCollection`
**API:** `findById(id)`
**Data:** read-only · **UI:** Console · **Security:** — · **Perf:** O(1) lookup
**Negative:** expired document readable (historical TTL bug)
**G/W/T:** *Given* an id, *When* fetched, *Then* the document returns; an expired id returns empty everywhere.
**Test plan:** lookup + TTL-read test
**Evidence:** test ids
**DoD:** TTL honored on all consumers
**Release impact:** correctness

#### US-050 · Query with MongoDB-style operators
`E4 · P0 · VERIFIED · M`
**Persona:** P4
**Story:** As a data engineer, I want `$eq/$ne/$gt/$gte/$lt/$lte/$in/$nin/$regex/$exists`, so that I can express filters.
**Outcome:** Each operator returns exactly the matching set.
**Deps:** US-046
**Files/modules:** `nosql/document/QueryParser`
**API:** JSON filter
**Data:** read-only · **UI:** Console query box · **Security:** — · **Perf:** scan/index
**Negative:** over-permissive operators returning wrong rows (historical)
**G/W/T:** *Given* representative documents, *When* each operator runs, *Then* results match the documented semantics exactly.
**Test plan:** operator matrix test
**Evidence:** test ids
**DoD:** no over-permissive operator remains
**Release impact:** correctness

#### US-051 · `$regex` substring semantics
`E4 · P1 · VERIFIED · S`
**Persona:** P4
**Story:** As a data engineer, I want documented `$regex` behavior, so that I anchor patterns consciously.
**Outcome:** `$regex` is substring matching; `^`/`$` anchor.
**Deps:** US-050
**Files/modules:** `nosql/document/QueryParser`, README
**API:** `$regex`
**Data:** read-only · **UI:** Console · **Security:** — · **Perf:** —
**Negative:** users assuming true regex semantics
**G/W/T:** *Given* a pattern, *When* applied, *Then* behavior matches the documented substring semantics.
**Test plan:** regex test
**Evidence:** test id + doc
**DoD:** docs and code agree
**Release impact:** honesty

#### US-052 · Combine with `$and` / `$or`
`E4 · P1 · VERIFIED · S`
**Persona:** P4
**Story:** As a data engineer, I want `$and`/`$or` arrays, so that I can compose filters.
**Outcome:** Combined filters combine correctly with sibling conditions.
**Deps:** US-050
**Files/modules:** `nosql/document/QueryParser`
**API:** `$and`/`$or`
**Data:** read-only · **UI:** Console · **Security:** — · **Perf:** —
**Negative:** `$and` ignored when sibling keys present
**G/W/T:** *Given* a filter mixing `$or` and a sibling key, *When* run, *Then* the documented combination holds.
**Test plan:** boolean-combination test
**Evidence:** test id
**DoD:** acceptance passes
**Release impact:** correctness

#### US-053 · Sort and paginate results
`E4 · P1 · VERIFIED · S`
**Persona:** P4
**Story:** As a data engineer, I want sort + paging, so that I can browse large collections.
**Outcome:** Stable, duplicate-free paging.
**Deps:** US-050
**Files/modules:** `nosql/document/QueryBuilder`
**API:** sort/limit/skip
**Data:** read-only · **UI:** Console · **Security:** — · **Perf:** O(n log n)
**Negative:** unstable ordering across pages
**G/W/T:** *Given* documents, *When* paged, *Then* pages reconstruct the full set.
**Test plan:** paging test
**Evidence:** test id
**DoD:** acceptance passes
**Release impact:** usability

#### US-054 · Partial update with operators
`E4 · P1 · VERIFIED · S`
**Persona:** P1
**Story:** As a developer, I want field-level updates, so that I do not rewrite whole documents.
**Outcome:** Only targeted fields change.
**Deps:** US-046
**Files/modules:** `nosql/document/DocumentCollection`
**API:** `update(id, field, value)`
**Data:** mutation · **UI:** Console · **Security:** — · **Perf:** —
**Negative:** nested field update path errors
**G/W/T:** *Given* a document, *When* one field updates, *Then* other fields are untouched.
**Test plan:** update test
**Evidence:** test id
**DoD:** acceptance passes
**Release impact:** usability

#### US-055 · Validate documents against a schema
`E4 · P2 · VERIFIED · M`
**Persona:** P4
**Story:** As a platform engineer, I want optional schema validation, so that bad documents are rejected.
**Outcome:** Invalid document is rejected; valid one is stored.
**Deps:** US-046
**Files/modules:** `core/schema/*`
**API:** schema API
**Data:** validated · **UI:** Console schema inspector · **Security:** integrity · **Perf:** validation cost
**Negative:** validation silently skipped
**G/W/T:** *Given* a schema, *When* an invalid document arrives, *Then* it is rejected with a reason.
**Test plan:** validation test
**Evidence:** test id
**DoD:** acceptance passes
**Release impact:** data quality

#### US-056 · Expire data with TTL
`E4 · P1 · VERIFIED · M`
**Persona:** P1
**Story:** As a developer, I want TTL, so that ephemeral data self-expires.
**Outcome:** Expired documents are invisible to every consumer.
**Deps:** US-049
**Files/modules:** TTL in `nosql/document` and `nosql/kv`
**API:** `expire(key, duration)`
**Data:** expiry metadata · **UI:** Console TTL display · **Security:** — · **Perf:** lazy eviction
**Negative:** expired item readable via a secondary path (historical R-53)
**G/W/T:** *Given* a TTL item, *When* the TTL passes, *Then* find/query/count all exclude it.
**Test plan:** TTL consistency test
**Evidence:** test id
**DoD:** all consumers honor TTL
**Release impact:** correctness

#### US-057 · Key-value put/get/delete
`E4 · P0 · VERIFIED · S`
**Persona:** P1
**Story:** As a developer, I want a KV bucket, so that I can cache and store sessions.
**Outcome:** Values round-trip; delete removes.
**Deps:** US-001
**Files/modules:** `nosql/kv/KeyValueBucket`
**API:** `keyValueBucket(name).put/get/delete`
**Data:** KV · **UI:** Console KV panel · **Security:** — · **Perf:** O(1)
**Negative:** overwrite semantics
**G/W/T:** *Given* a bucket, *When* put then get, *Then* the value returns; after delete, it does not.
**Test plan:** KV test
**Evidence:** test id
**DoD:** acceptance passes
**Release impact:** P0

#### US-058 · Redis-style list / set / hash structures
`E4 · P1 · VERIFIED · M`
**Persona:** P1
**Story:** As a developer, I want lists/sets/hashes, so that I can build queues and tag sets without Redis.
**Outcome:** `rpush/lrange`, `sadd/smembers`, `hset/hget` behave as documented.
**Deps:** US-057
**Files/modules:** `nosql/kv/ListBucket`, `SetBucket`, `HashBucket`
**API:** Redis-style ops
**Data:** structures · **UI:** Console explorers · **Security:** — · **Perf:** O(1)–O(n)
**Negative:** type confusion across buckets
**G/W/T:** *Given* each structure, *When* its operations run, *Then* results match the documented semantics.
**Test plan:** structure tests
**Evidence:** test ids
**DoD:** acceptance passes
**Release impact:** differentiator

#### US-059 · Column families
`E4 · P2 · VERIFIED · M`
**Persona:** P4
**Story:** As a data engineer, I want wide-column access, so that I can model sparse matrices.
**Outcome:** Row/column get/put works.
**Deps:** US-045
**Files/modules:** `nosql/column/ColumnFamily`
**API:** `columnFamily(name).put(row, col, val)`
**Data:** wide-column · **UI:** Console matrix viewer · **Security:** — · **Perf:** —
**Negative:** sparse read semantics
**G/W/T:** *Given* a column family, *When* cells are written, *Then* they read back per row/column.
**Test plan:** column-family test
**Evidence:** test id
**DoD:** acceptance passes
**Release impact:** breadth

#### US-060 · Repository API
`E4 · P1 · VERIFIED · M`
**Persona:** P3
**Story:** As a framework developer, I want repository interfaces, so that I can use entity-centric data access.
**Outcome:** CRUD + derived queries work through a repository.
**Deps:** US-046
**Files/modules:** `adapter/jnosql/CrudRepository`, `JunifyRepository`
**API:** repository methods
**Data:** entity-mapped · **UI:** — · **Security:** — · **Perf:** —
**Negative:** mapping mismatch for renamed fields
**G/W/T:** *Given* a repository, *When* save/find/delete run, *Then* the entity round-trips.
**Test plan:** repository test
**Evidence:** test id
**DoD:** acceptance passes
**Release impact:** framework ergonomics

#### US-061 · Jakarta NoSQL / JPA / Hibernate annotation interop
`E4 · P1 · VERIFIED · L`
**Persona:** P3
**Story:** As a framework developer, I want my existing annotations honored, so that I do not annotate twice.
**Outcome:** `@Entity/@Id/@Column` from all three standards map to fields.
**Deps:** US-060
**Files/modules:** `adapter/jnosql/AnnotationResolver`, `jpa/*`
**API:** reflective mapping
**Data:** entity mapping · **UI:** — · **Security:** — · **Perf:** reflection cost
**Negative:** conflicting annotations from two standards
**G/W/T:** *Given* an entity annotated by any supported standard, *When* mapped, *Then* fields resolve to the documented names.
**Test plan:** annotation interop test + annotation-showcase demo
**Evidence:** test id + demo output
**DoD:** demo passes for all three standards
**Release impact:** differentiator

#### US-062 · NoSQL data survives restart
`E4 · P0 · VERIFIED · M`
**Persona:** P4
**Story:** As a data engineer, I want NoSQL writes durable, so that restart is safe.
**Outcome:** 12 of 12 acknowledged documents with full bodies survive a forced stop.
**Deps:** US-046, US-043
**Files/modules:** WAL + snapshot, three persistent engines
**API:** — · **Data:** WAL · **UI:** — · **Security:** — · **Perf:** fsync/write
**Negative:** acknowledged writes lost across checkpoint/rotation/kill (historical)
**G/W/T:** *Given* a persistent engine, *When* force-stopped and restarted, *Then* all acknowledged documents return.
**Test plan:** contract gate restart block
**Evidence:** gate output per engine
**DoD:** PASS on FILE/LSM/B_TREE
**Release impact:** P0

#### US-063 · Correct reads after restart on LSM_TREE
`E4 · P0 · VERIFIED · M`
**Persona:** P4
**Story:** As a data engineer, I want a newest-version-per-key read, so that restart does not duplicate or resurrect rows.
**Outcome:** Deletes stay deleted; no double reads.
**Deps:** US-062
**Files/modules:** `storage/spi/LSMTreeEngine`
**API:** — · **Data:** memtable+SSTable merge · **UI:** — · **Security:** — · **Perf:** —
**Negative:** 25 records → 50 after restart; a deleted row returns (historical R-65)
**G/W/T:** *Given* LSM_TREE with updates and a delete, *When* restarted, *Then* counts and membership match the pre-restart state.
**Test plan:** LSM restart test
**Evidence:** before/after counts
**DoD:** no duplication or resurrection
**Release impact:** correctness

---

## Epic E5 — Both engines in one JVM

#### US-064 · Run SQL and NoSQL from one instance
`E5 · P0 · VERIFIED · S`
**Persona:** P1
**Story:** As a developer, I want one `JunifyDB` instance to expose both engines, so that I avoid two databases.
**Outcome:** SQL and NoSQL calls work against the same instance.
**Deps:** US-023, US-046
**Files/modules:** `JunifyDB`
**API:** `db.sql(...)`, `db.documentCollection(...)`
**Data:** shared substrate · **UI:** Console · **Security:** — · **Perf:** —
**Negative:** one engine's call corrupts the other's state
**G/W/T:** *Given* one instance, *When* a document is written and queried via SQL, *Then* the SQL view agrees with the NoSQL view.
**Test plan:** cross-engine test
**Evidence:** test id
**DoD:** agreement asserted both directions
**Release impact:** P0 positioning

#### US-065 · Address the same data from both engines
`E5 · P1 · VERIFIED · M`
**Persona:** P1
**Story:** As a developer, I want SQL tables and document collections to be the same addressable data, so that I can mix paradigms.
**Outcome:** A table created via SQL is visible as a collection and vice versa.
**Deps:** US-064
**Files/modules:** shared catalog
**API:** — · **Data:** shared · **UI:** Console · **Security:** — · **Perf:** —
**Negative:** divergent catalogs
**G/W/T:** *Given* a SQL table, *When* listed as a collection, *Then* it appears.
**Test plan:** cross-engine catalog test
**Evidence:** test id
**DoD:** shared catalog asserted (ADR-002)
**Release impact:** architecture

#### US-066 · Configure each engine independently
`E5 · P1 · PARTIAL · M`
**Persona:** P3
**Story:** As a framework developer, I want engine-level settings, so that I can tune each concern.
**Outcome:** Documented engine settings take effect independently.
**Deps:** US-012
**Files/modules:** `config/*`
**API:** config keys
**Data:** — · **UI:** — · **Security:** — · **Perf:** engine tuning
**Negative:** a setting is global and silently affects both engines
**G/W/T:** *Given* a per-engine setting, *When* set, *Then* only the intended engine changes behavior.
**Test plan:** config isolation test
**Evidence:** test id
**DoD:** documented settings honored; global ones documented as global
**Release impact:** modularity

#### US-067 · Test each engine independently
`E5 · P1 · VERIFIED · M`
**Persona:** P2
**Story:** As a test engineer, I want to instantiate either engine alone, so that my tests stay isolated.
**Outcome:** Either engine can be exercised without the other.
**Deps:** US-064
**Files/modules:** test harness
**API:** —
**Data:** ephemeral per test · **UI:** — · **Security:** — · **Perf:** fast tests
**Negative:** cross-engine singleton state leaking between tests
**G/W/T:** *Given* an isolated test, *When* only the SQL engine is used, *Then* no NoSQL state is required.
**Test plan:** isolation test
**Evidence:** test id
**DoD:** no shared mutable singleton across tests
**Release impact:** testability

#### US-068 · No semantic leakage between engines
`E5 · P0 · VERIFIED · M`
**Persona:** P6
**Story:** As a maintainer, I want a contract gate that catches one engine's semantics leaking into the other, so that regressions fail CI.
**Outcome:** The gate fails on catalog-mutating reads and other known leaks.
**Deps:** US-030
**Files/modules:** `scripts/console-contract-gate.sh`, CI workflow
**API:** — · **Data:** — · **UI:** Console · **Security:** — · **Perf:** —
**Negative:** a leak ships because no gate covers it
**G/W/T:** *Given* a reintroduced leak, *When* the gate runs, *Then* it fails.
**Test plan:** gate self-test
**Evidence:** gate run output
**DoD:** gate runs on CI across engines
**Release impact:** regression safety

#### US-069 · Mixed SQL+NoSQL workload
`E5 · P2 · PARTIAL · M`
**Persona:** P4
**Story:** As a data engineer, I want a mixed workload, so that I can rely on both engines under load.
**Outcome:** Mixed operations succeed with no cross-engine corruption.
**Deps:** US-064
**Files/modules:** concurrency + MVCC
**API:** — · **Data:** shared · **UI:** — · **Security:** — · **Perf:** measured
**Negative:** mixed writers lose updates (documented R-20)
**G/W/T:** *Given* a mixed workload, *When* run, *Then* each engine's data remains internally consistent.
**Test plan:** mixed-load test
**Evidence:** load output
**DoD:** no corruption; mixed-writer limitation documented
**Release impact:** performance/stability

---

## Epic E6 — Engine health & metrics

#### US-070 · Health endpoint
`E6 · P0 · VERIFIED · S`
**Persona:** P4
**Story:** As an operator, I want `GET /api/health`, so that I can monitor liveness.
**Outcome:** Returns a status document reflecting real state.
**Deps:** US-064
**Files/modules:** `core/health/*`, Console HTTP
**API:** `/api/health`
**Data:** read-only · **UI:** Console Overview · **Security:** unauthenticated liveness only · **Perf:** O(1)
**Negative:** health reports OK while the store is broken
**G/W/T:** *Given* a running server, *When* `/api/health` is called, *Then* it returns the documented shape and a real status.
**Test plan:** health test + gate route
**Evidence:** gate output
**DoD:** route covered by the contract gate
**Release impact:** operability

#### US-071 · Metrics snapshot
`E6 · P1 · VERIFIED · S`
**Persona:** P4
**Story:** As an operator, I want counters, so that I can see operation volume.
**Outcome:** `db.metrics().snapshot()` returns counters that increase with activity.
**Deps:** US-064
**Files/modules:** `core/metrics/DatabaseMetrics`
**API:** `db.metrics().snapshot()`
**Data:** counters · **UI:** Console Metrics · **Security:** — · **Perf:** atomic
**Negative:** metrics that never move (fake telemetry)
**G/W/T:** *Given* activity, *When* the snapshot is read, *Then* relevant counters increased.
**Test plan:** metrics test
**Evidence:** test id + gate route
**DoD:** counters demonstrably react to activity
**Release impact:** observability honesty

#### US-072 · Lifecycle events
`E6 · P1 · VERIFIED · M`
**Persona:** P1
**Story:** As a developer, I want BEFORE/AFTER insert/delete events, so that I can hook auditing or cache invalidation.
**Outcome:** Listeners receive events for real mutations.
**Deps:** US-046
**Files/modules:** `core/event/*`
**API:** event bus
**Data:** — · **UI:** — · **Security:** — · **Perf:** listener overhead
**Negative:** events fired for no-op operations
**G/W/T:** *Given* a listener, *When* a document is inserted, *Then* BEFORE and AFTER events fire in order.
**Test plan:** event test
**Evidence:** test id
**DoD:** ordering asserted
**Release impact:** extensibility

#### US-073 · Change Data Capture stream
`E6 · P2 · VERIFIED · M`
**Persona:** P4
**Story:** As a data engineer, I want a CDC stream, so that I can feed downstream systems.
**Outcome:** Mutations produce CDC events.
**Deps:** US-072
**Files/modules:** `core/cdc/CDCManager`, `CDCProcessor`
**API:** CDC stream
**Data:** event log · **UI:** Console CDC panel · **Security:** — · **Perf:** overhead
**Negative:** events dropped under load
**G/W/T:** *Given* CDC enabled, *When* a write occurs, *Then* one CDC event is emitted with the operation.
**Test plan:** CDC test
**Evidence:** test id
**DoD:** acceptance passes
**Release impact:** integration

#### US-074 · Kafka CDC connector
`E6 · P3 · EXPERIMENTAL · M`
**Persona:** P4
**Story:** As a data engineer, I want to forward CDC to Kafka, so that I can wire a broker.
**Outcome:** Connector forwards events when configured.
**Deps:** US-073
**Files/modules:** `core/cdc/KafkaCDCConnector`
**API:** connector config
**Data:** external · **UI:** — · **Security:** broker credentials · **Perf:** —
**Negative:** connector failure drops events silently
**G/W/T:** *Given* a broker, *When* CDC is enabled, *Then* events appear on the topic. *(Not exercised in CI — no broker.)*
**Test plan:** manual with a broker
**Evidence:** manual run transcript, if any
**DoD:** described as experimental; excluded from coverage gate
**Release impact:** must not be marketed as production

#### US-075 · Audit trail of mutations
`E6 · P1 · PARTIAL · S`
**Persona:** P4
**Story:** As an operator, I want a recent-mutation log, so that I can investigate changes.
**Outcome:** Recent events are listed with timestamp, collection, operation.
**Deps:** US-072
**Files/modules:** `core` audit trail
**API:** Console audit route
**Data:** in-memory only · **UI:** Console Audit panel · **Security:** not cryptographically verified · **Perf:** in-memory
**Negative:** presented as a durable audit system
**G/W/T:** *Given* mutations, *When* the audit panel opens, *Then* recent events are listed.
**Test plan:** audit test
**Evidence:** test id
**DoD:** UI states it is in-memory and not verified
**Release impact:** honesty

#### US-076 · JVM telemetry
`E6 · P2 · VERIFIED · S`
**Persona:** P4
**Story:** As an operator, I want memory/thread info, so that I can watch resource use.
**Outcome:** Overview shows JVM memory and thread counts.
**Deps:** US-071
**Files/modules:** Console Overview + metrics
**API:** metrics route
**Data:** JVM telemetry · **UI:** Console Overview · **Security:** — · **Perf:** —
**Negative:** static/placeholder numbers
**G/W/T:** *Given* a running JVM, *When* the Overview opens, *Then* the numbers reflect the JVM.
**Test plan:** manual + gate route
**Evidence:** route output
**DoD:** values change with load
**Release impact:** observability

#### US-077 · Observability covers both engines
`E6 · P2 · PARTIAL · M`
**Persona:** P4
**Story:** As an operator, I want to distinguish SQL from NoSQL activity, so that I can attribute load.
**Outcome:** Metrics/events attribute operations by engine.
**Deps:** US-071
**Files/modules:** metrics + event metadata
**API:** — · **Data:** tagged counters · **UI:** Console Metrics · **Security:** — · **Perf:** —
**Negative:** aggregated-only metrics make attribution impossible
**G/W/T:** *Given* mixed activity, *When* metrics are read, *Then* SQL and NoSQL contributions are distinguishable.
**Test plan:** metrics attribution test
**Evidence:** test id
**DoD:** attribution exists or the gap is documented
**Release impact:** operability

---

## Epic E7 — Developer Console

#### US-078 · Start the embedded Console
`E7 · P0 · VERIFIED · S`
**Persona:** P4
**Story:** As an operator, I want an embedded web Console, so that I can inspect the database without extra services.
**Outcome:** Starting the jar with `--port` opens the Console.
**Deps:** US-064
**Files/modules:** `console/http/JunifyDBServer`, `console/http/PortManager`
**API:** `--port`, `--engine`, `--data-dir`
**Data:** reads the live store · **UI:** full Console · **Security:** binds loopback by default · **Perf:** fast start
**Negative:** binds a wildcard interface; port conflict
**G/W/T:** *Given* the jar, *When* started with a port, *Then* the Console is reachable and serves the UI.
**Test plan:** startup test + gate
**Evidence:** server log + page load
**DoD:** binds loopback by default; port conflict handled
**Release impact:** P0 usability

#### US-079 · Overview dashboard
`E7 · P1 · VERIFIED · S`
**Persona:** P4
**Story:** As an operator, I want an overview, so that I can see engine, counts, and telemetry at a glance.
**Outcome:** Dashboard reflects real counts and JVM telemetry.
**Deps:** US-078
**Files/modules:** `static/js`, Overview route
**API:** metrics/health routes
**Data:** read-only · **UI:** Overview panel · **Security:** — · **Perf:** —
**Negative:** static placeholder values
**G/W/T:** *Given* seeded data, *When* the Overview loads, *Then* counts match the store.
**Test plan:** gate route
**Evidence:** route JSON
**DoD:** numbers match the store
**Release impact:** trust

#### US-080 · Document collection explorer with CRUD
`E7 · P1 · VERIFIED · M`
**Persona:** P4
**Story:** As an operator, I want to browse and edit documents, so that I can fix data in place.
**Outcome:** Create/edit/delete through the UI reflects in the store.
**Deps:** US-046, US-078
**Files/modules:** Console documents panel, collections routes
**API:** `/api/collections/...`
**Data:** mutation · **UI:** documents panel · **Security:** auth + CSRF · **Perf:** —
**Negative:** UI reports success while the write failed
**G/W/T:** *Given* a collection, *When* a document is created via the UI, *Then* a subsequent read (UI and API) shows it.
**Test plan:** gate route round-trip
**Evidence:** network traces
**DoD:** UI↔API↔engine↔storage round-trip proven
**Release impact:** core workflow

#### US-081 · Key-value explorer
`E7 · P1 · VERIFIED · S`
**Persona:** P4
**Story:** As an operator, I want KV browsing with TTL, so that I can inspect sessions.
**Outcome:** Put/get/delete with TTL controls work.
**Deps:** US-057, US-078
**Files/modules:** KV panel, `/api/kv/...`
**API:** KV routes
**Data:** mutation · **UI:** KV panel · **Security:** auth + CSRF · **Perf:** —
**Negative:** fake success on delete
**G/W/T:** *Given* a bucket, *When* a value is put and read via the UI, *Then* both agree.
**Test plan:** gate route
**Evidence:** network traces
**DoD:** round-trip proven
**Release impact:** core workflow

#### US-082 · Redis-structures explorer
`E7 · P2 · VERIFIED · M`
**Persona:** P4
**Story:** As an operator, I want to inspect lists/sets/hashes, so that I can debug queues and tags.
**Outcome:** Each structure renders its members.
**Deps:** US-058, US-078
**Files/modules:** structures panels, routes
**API:** list/set/hash routes
**Data:** read + mutation · **UI:** structure panels · **Security:** auth + CSRF · **Perf:** —
**Negative:** viewer renders stale members
**G/W/T:** *Given* a list, *When* members are added, *Then* the panel shows them.
**Test plan:** gate route
**Evidence:** network traces
**DoD:** viewers match the store
**Release impact:** differentiator

#### US-083 · Column-family matrix viewer
`E7 · P2 · VERIFIED · S`
**Persona:** P4
**Story:** As an operator, I want a row/column matrix, so that I can read wide rows.
**Outcome:** Cells render per row/column.
**Deps:** US-059, US-078
**Files/modules:** columns panel, `/api/columns/...`
**API:** columns routes
**Data:** read + mutation · **UI:** matrix viewer · **Security:** auth + CSRF · **Perf:** —
**Negative:** empty matrix for populated family
**G/W/T:** *Given* a family, *When* cells are written, *Then* the matrix shows them.
**Test plan:** gate route
**Evidence:** network traces
**DoD:** viewer matches the store
**Release impact:** breadth

#### US-084 · Execute SQL from the SQL Studio
`E7 · P0 · VERIFIED · M`
**Persona:** P4
**Story:** As an operator, I want to run SQL in the Console, so that I can query without code.
**Outcome:** A statement runs and returns rows.
**Deps:** US-023, US-078
**Files/modules:** SQL panel, `/api/sql`
**API:** `POST /api/sql`
**Data:** may mutate · **UI:** SQL Studio · **Security:** auth required; CSRF · **Perf:** —
**Negative:** 500 for a user error; silent all-row results
**G/W/T:** *Given* a query, *When* executed, *Then* it returns the documented result or a 400 with a reason.
**Test plan:** gate route
**Evidence:** network traces
**DoD:** errors surface as 400, not 500 or silent success
**Release impact:** core workflow

#### US-085 · Render SQL result tables
`E7 · P1 · VERIFIED · S`
**Persona:** P4
**Story:** As an operator, I want results as a table, so that I can read them.
**Outcome:** Columns and rows render; empty result renders an empty state.
**Deps:** US-084
**Files/modules:** SQL panel
**API:** — · **Data:** — · **UI:** result table · **Security:** — · **Perf:** —
**Negative:** header/row mismatch
**G/W/T:** *Given* a result, *When* rendered, *Then* the headers match the row cells.
**Test plan:** UI check
**Evidence:** screenshot
**DoD:** rendering matches result
**Release impact:** usability

#### US-086 · NoSQL JSON query workspace
`E7 · P1 · VERIFIED · M`
**Persona:** P4
**Story:** As an operator, I want a JSON filter workspace, so that I can query documents visually.
**Outcome:** Filters run and return matching documents.
**Deps:** US-050, US-078
**Files/modules:** NoSQL workspace, query route
**API:** `/api/collections/{name}/query`
**Data:** read-only · **UI:** NoSQL workspace · **Security:** auth + CSRF · **Perf:** —
**Negative:** unknown operator silently ignored
**G/W/T:** *Given* a filter, *When* run, *Then* the documented result returns or a 400 is shown.
**Test plan:** gate route
**Evidence:** network traces
**DoD:** malformed filters rejected with 400
**Release impact:** core workflow

#### US-087 · Indexes panel
`E7 · P2 · VERIFIED · M`
**Persona:** P4
**Story:** As an operator, I want to view and add indexes, so that I can tune queries.
**Outcome:** Existing indexes list; adding one succeeds.
**Deps:** US-034, US-078
**Files/modules:** indexes panel, `/api/indexes/...`
**API:** indexes routes
**Data:** index metadata · **UI:** indexes panel · **Security:** auth + CSRF · **Perf:** —
**Negative:** listing an unknown collection creates it (historical R-64)
**G/W/T:** *Given* an unknown collection, *When* indexes are listed, *Then* it 404s and the catalog is unchanged.
**Test plan:** gate route + catalog test
**Evidence:** network traces + catalog snapshot
**DoD:** no read creates state
**Release impact:** data integrity

#### US-088 · Vector search panel
`E7 · P2 · VERIFIED · M`
**Persona:** P4
**Story:** As an operator, I want vector similarity search, so that I can demo the index.
**Outcome:** Search returns nearest vectors; index persists.
**Deps:** US-034, US-078
**Files/modules:** vectors panel, `/api/vectors/...`, `index/hnsw`
**API:** vectors routes
**Data:** index + vectors · **UI:** vectors panel · **Security:** auth + CSRF · **Perf:** —
**Negative:** search auto-creates an empty index (historical)
**G/W/T:** *Given* vectors, *When* searched, *Then* results are ordered by similarity; search does not create an index.
**Test plan:** gate route
**Evidence:** network traces
**DoD:** no auto-create on search
**Release impact:** honest auxiliary feature

#### US-089 · Schema inspector
`E7 · P2 · VERIFIED · S`
**Persona:** P4
**Story:** As an operator, I want to see inferred schema, so that I understand field shapes.
**Outcome:** Field names/types are listed.
**Deps:** US-055, US-078
**Files/modules:** schema panel, `/api/schema/...`
**API:** schema route
**Data:** read-only · **UI:** schema inspector · **Security:** auth · **Perf:** —
**Negative:** schema invents fields
**G/W/T:** *Given* documents, *When* schema inspected, *Then* fields match the data.
**Test plan:** gate route
**Evidence:** route JSON
**DoD:** inference matches data
**Release impact:** usability

#### US-090 · Metrics panel
`E7 · P2 · VERIFIED · S`
**Persona:** P4
**Story:** As an operator, I want live metrics, so that I can watch activity.
**Outcome:** Panel reflects real counters.
**Deps:** US-071, US-078
**Files/modules:** metrics panel, `/api/metrics`
**API:** metrics route
**Data:** counters · **UI:** metrics panel · **Security:** auth · **Perf:** —
**Negative:** static numbers
**G/W/T:** *Given* activity, *When* the panel loads, *Then* counters moved.
**Test plan:** gate route
**Evidence:** route JSON
**DoD:** counters react to activity
**Release impact:** observability

#### US-091 · CDC panel
`E7 · P3 · VERIFIED · M`
**Persona:** P4
**Story:** As an operator, I want CDC status and events, so that I can inspect the stream.
**Outcome:** Panel shows connector status and recent events.
**Deps:** US-073, US-078
**Files/modules:** CDC panel, `/api/cdc`
**API:** `/api/cdc`
**Data:** events · **UI:** CDC panel · **Security:** auth · **Perf:** —
**Negative:** panel shows events that never existed
**G/W/T:** *Given* CDC enabled, *When* a write occurs, *Then* the panel lists the event.
**Test plan:** gate route
**Evidence:** route JSON
**DoD:** events correspond to real writes
**Release impact:** integration

#### US-092 · Audit panel
`E7 · P2 · PARTIAL · S`
**Persona:** P4
**Story:** As an operator, I want recent mutations, so that I can trace changes.
**Outcome:** Panel lists recent events with metadata.
**Deps:** US-075, US-078
**Files/modules:** audit panel, audit route
**API:** audit route
**Data:** in-memory · **UI:** audit panel · **Security:** not cryptographically verified · **Perf:** —
**Negative:** presented as tamper-proof
**G/W/T:** *Given* mutations, *When* the panel loads, *Then* it lists them and states the in-memory caveat.
**Test plan:** gate route
**Evidence:** route JSON
**DoD:** caveat is visible in the UI
**Release impact:** honesty

#### US-093 · Surface server logs and errors
`E7 · P1 · VERIFIED · M`
**Persona:** P4
**Story:** As an operator, I want to see errors, so that I can diagnose failures.
**Outcome:** Failed operations show a reason in the UI.
**Deps:** US-078
**Files/modules:** Console error handling
**API:** error shapes
**Data:** — · **UI:** error toasts/panels · **Security:** no stack traces leaked by default · **Perf:** —
**Negative:** generic "error" with no detail
**G/W/T:** *Given* a failing operation, *When* it runs, *Then* the UI shows an actionable message and the API returns a non-500 code where applicable.
**Test plan:** `ConsoleTaskSuccessTest.correlationIdOnSuccessAndFailure`, `.failuresAreClassifiable`
**Evidence:** `../release-audit/74-console-task-success-evidence.md` (error banner answers what/why/data-changed/how-to-fix/learn-more/correlation-id)
**DoD:** user errors are 4xx with a message, a correlation id, and a data-safety answer
**Release impact:** diagnosability

#### US-094 · No fake success anywhere in the UI
`E7 · P0 · VERIFIED · M`
**Persona:** P5
**Story:** As an evaluator, I want operations that do nothing to report failure, so that I can trust the Console.
**Outcome:** No operation returns 200 for an effect that did not happen.
**Deps:** US-080, US-084, US-087, US-088
**Files/modules:** all Console routes
**API:** status codes
**Data:** — · **UI:** all panels · **Security:** — · **Perf:** —
**Negative:** 200 for a no-op (the dominant historical defect family)
**G/W/T:** *Given* any route, *When* asked to act on nonexistent state, *Then* it returns an error, not false success.
**Test plan:** contract gate across engines
**Evidence:** gate output
**DoD:** gate includes fake-success checks
**Release impact:** trust

#### US-095 · Meaningful empty states
`E7 · P2 · VERIFIED · S`
**Persona:** P4
**Story:** As an operator, I want empty states, so that I know when a collection is genuinely empty.
**Outcome:** Empty collections render an explicit empty state.
**Deps:** US-078
**Files/modules:** Console panels
**API:** — · **Data:** — · **UI:** empty states · **Security:** — · **Perf:** —
**Negative:** indistinguishable empty vs failed
**G/W/T:** *Given* an empty collection, *When* opened, *Then* an explicit empty state shows; *and* a 0-row result set renders the `empty` state rather than a success badge.
**Test plan:** `ConsoleTaskSuccessTest.staticAssetsExposeRequiredAffordances` (state vocabulary includes `empty`)
**Evidence:** `../release-audit/74-console-task-success-evidence.md`
**DoD:** empty distinct from error and from success
**Release impact:** UX

#### US-096 · Responsive Console layout
`E7 · P2 · PARTIAL · M`
**Persona:** P4
**Story:** As an operator, I want the Console usable on smaller screens, so that I can inspect from a laptop.
**Outcome:** Layout remains usable at common breakpoints.
**Deps:** US-078
**Files/modules:** `static/css`
**API:** — · **Data:** — · **UI:** responsive · **Security:** — · **Perf:** —
**Negative:** controls unusable on narrow screens
**G/W/T:** *Given* a narrow viewport, *When* a panel opens, *Then* controls remain reachable.
**Test plan:** viewport screenshots
**Evidence:** screenshots at breakpoints
**DoD:** no unreachable primary control
**Release impact:** usability

#### US-097 · Authenticate Console access
`E7 · P0 · VERIFIED · M`
**Persona:** P4
**Story:** As an operator, I want to require auth, so that the Console is not open by default in shared envs.
**Outcome:** With auth enabled, unauthenticated requests are rejected.
**Deps:** US-078
**Files/modules:** `console/http/SecureSessionManager`, `security/*`, `config/SecurityConfig`
**API:** `/api/auth/login`, `/api/auth/logout`
**Data:** sessions · **UI:** login screen · **Security:** auth + session · **Perf:** —
**Negative:** auth enabled but a route bypasses it
**G/W/T:** *Given* auth enabled, *When* a protected route is called unauthenticated, *Then* it is rejected.
**Test plan:** auth gate
**Evidence:** gate output
**DoD:** auth gate PASS
**Release impact:** security

#### US-098 · CSRF protection on mutating UI calls
`E7 · P0 · VERIFIED · M`
**Persona:** P6
**Story:** As a maintainer, I want CSRF tokens on mutating requests, so that a hostile page cannot drive the Console.
**Outcome:** Mutations without a valid token are rejected.
**Deps:** US-097
**Files/modules:** `security/CsrfTokenManager`, Console routes
**API:** token endpoints
**Data:** — · **UI:** all mutating panels · **Security:** CSRF · **Perf:** —
**Negative:** token check skipped on some route
**G/W/T:** *Given* a mutation without a token, *When* submitted, *Then* it is rejected; with a valid token, it succeeds.
**Test plan:** CSRF test
**Evidence:** test id + gate
**DoD:** every mutating route checks the token
**Release impact:** security

#### US-099 · Accessible Console
`E7 · P2 · PARTIAL · M`
**Persona:** P4
**Story:** As an operator, I want keyboard-navigable, labelled controls, so that I can use the Console without a mouse.
**Outcome:** Controls are labelled and reachable; status is announced.
**Deps:** US-078
**Files/modules:** Console HTML/JS
**API:** — · **Data:** — · **UI:** a11y · **Security:** — · **Perf:** —
**Negative:** unlabelled icon-only buttons
**G/W/T:** *Given* the Console, *When* navigated by keyboard, *Then* all primary controls are reachable and labelled.
**Test plan:** a11y check
**Evidence:** audit output
**DoD:** no unreachable primary control
**Release impact:** accessibility gate

---

#### US-138 · Always show active engine, database, storage, connection, transaction and security context
`E7 · P0 · VERIFIED · M`
**Persona:** P4
**Story:** As an operator, I want the Console to always state what I am connected to, so that I never act on the wrong engine, database, or durability assumption.
**Outcome:** A persistent status bar shows engine, active database, storage mode, its durability meaning, connection state, transaction state, and identity.
**Deps:** US-078
**Files/modules:** `console/http/JunifyDBServer` (`/api/health` `context` block), `static/index.html` (`#statusbar`), `static/js/console.js` (`pollStatus`)
**API:** `GET /api/health` → `context{engine, relationalEngine, nosqlEngine, storageMode, durability, database, dataDir, authEnabled, user, activeTransactions, transactionalConsoleWrites, transactionScope}`
**Data:** read-only · **UI:** status bar · **Security:** identity shown; never invents one · **Perf:** one poll per 10s
**Negative:** values are guessed in the front end and drift from the server; a memory database implies durability it does not have
**G/W/T:** *Given* a running server, *When* any panel is open, *Then* the status bar lists all six context items with values read from `/api/health`; *and* an in-memory database states "no durability".
**Test plan:** `ConsoleTaskSuccessTest.healthExposesOrientationContext`, `.fileEngineReportsDurabilityAndDataDir`, `.activeTransactionsAppearInContext`
**Evidence:** `../release-audit/74-console-task-success-evidence.md`
**DoD:** every item is server-sourced; no placeholder text
**Release impact:** prevents operations against the wrong context

#### US-139 · Explicit state for every action
`E7 · P0 · VERIFIED · M`
**Persona:** P4
**Story:** As an operator, I want every action to end in a stated outcome, so that I can tell success from failure from "nothing happened".
**Outcome:** Each action resolves to exactly one state: idle, loading, success, empty, validation error, backend error, timeout, permission denied, conflict, rate limited, or recovery required.
**Deps:** US-094
**Files/modules:** `static/js/console.js` (`STATES`, `setBadge`, `successOrEmpty`)
**API:** status codes mapped to states
**Data:** — · **UI:** badges, banners · **Security:** — · **Perf:** 20s client ceiling turns a stall into a timeout
**Negative:** a 0-row result rendered as success; a hung request with no state
**G/W/T:** *Given* a request that returns 0 rows, *When* it completes, *Then* the state is `empty`, not `success`; *and* a request exceeding the ceiling resolves to `timeout`.
**Test plan:** `ConsoleTaskSuccessTest.failuresAreClassifiable`; asset assertions on the state vocabulary
**Evidence:** `../release-audit/74-console-task-success-evidence.md`
**DoD:** no silent failure and no unexplained blank panel
**Release impact:** diagnosability and trust

#### US-140 · Destructive actions name their target, impact and reversibility
`E7 · P0 · VERIFIED · M`
**Persona:** P4
**Story:** As an operator, I want confirmation before destroying data, so that a mistyped statement cannot silently erase a table.
**Outcome:** Drop, unqualified DELETE/UPDATE, collection-wide deletion and restore each require a dialog that names the target, states the impact, states reversibility, and states what changed afterwards.
**Deps:** US-078, US-080
**Files/modules:** `static/index.html` (`#confirmDialog`), `static/js/console.js` (`confirmAction`, `destructiveReason`)
**API:** — · **Data:** prevents unintended mutation · **UI:** modal dialog · **Security:** — · **Perf:** —
**Negative:** `window.confirm` cannot name a target or an impact; a cancelled confirmation deleting anyway
**G/W/T:** *Given* `DELETE FROM products`, *When* Run is pressed, *Then* a dialog names `products`, states the statement has no WHERE clause, and nothing is sent; *and* choosing Cancel leaves the row count unchanged.
**Test plan:** `ConsoleTaskSuccessTest.staticAssetsExposeRequiredAffordances` (dialog present; no bare `confirm(` call); live browser journey in the evidence doc
**Evidence:** `../release-audit/74-console-task-success-evidence.md`
**DoD:** no destructive path without a named confirmation; cancel is provably a no-op
**Release impact:** data-loss prevention

#### US-141 · Every error is reportable with a correlation id
`E7 · P0 · VERIFIED · M`
**Persona:** P4
**Story:** As an operator, I want an id I can quote when something fails, so that a report can be matched to a server event.
**Outcome:** Every response carries `X-Correlation-Id`; every error body repeats that id; the UI shows it with what failed, why, whether data changed, and how to fix it.
**Deps:** US-093
**Files/modules:** `console/http/JunifyDBServer` (`sendJson`, `withCorrelationId`), `static/js/console.js` (`errorBanner`)
**API:** `X-Correlation-Id` request/response header; `correlationId` in error JSON
**Data:** — · **UI:** error banner · **Security:** no stack traces; a caller-supplied id is echoed for tracing · **Perf:** —
**Negative:** an opaque "error" with no id and no data-safety answer
**G/W/T:** *Given* a failing request, *When* it returns, *Then* the header id equals the body id, the UI displays it, and the banner answers "Did data change?".
**Test plan:** `ConsoleTaskSuccessTest.correlationIdOnSuccessAndFailure`, `.requestedCorrelationIdIsHonoured`
**Evidence:** `../release-audit/74-console-task-success-evidence.md`
**DoD:** no failure surface without an id and a data-safety answer
**Release impact:** supportability

#### US-142 · Run only the selected statement
`E7 · P2 · VERIFIED · S`
**Persona:** P4
**Story:** As an operator, I want to run just the highlighted text, so that I do not re-run a whole scratchpad.
**Outcome:** Run selection executes the highlighted text only; the shortcut is shown in the UI.
**Deps:** US-084
**Files/modules:** `static/index.html` (`#sqlRunSel`), `static/js/console.js` (`runSql(onlySelection)`)
**API:** `POST /api/sql` · **Data:** may mutate · **UI:** SQL Studio · **Security:** same guardrails as Run · **Perf:** —
**Negative:** selection silently ignored so the whole editor runs
**G/W/T:** *Given* two statements with one highlighted, *When* Run selection is pressed, *Then* only the highlighted text is sent.
**Test plan:** live browser journey (evidence doc); Control+Shift+Enter binding asserted in assets
**Evidence:** `../release-audit/74-console-task-success-evidence.md`
**DoD:** selection is honoured, or the full editor is run only when nothing is selected
**Release impact:** usability

#### US-143 · Cancel a running query
`E7 · P1 · VERIFIED · S`
**Persona:** P4
**Story:** As an operator, I want to cancel a long query, so that a mistake does not block the panel.
**Outcome:** A Cancel control appears while a query runs; cancelling reports a distinguishable cancelled state and states that a server-side statement may still complete.
**Deps:** US-084, US-139
**Files/modules:** `static/index.html` (`#sqlCancel`), `static/js/console.js` (AbortController in `runSql`)
**API:** `POST /api/sql` with an aborted request · **Data:** possibly unchanged server-side — stated · **UI:** cancel button · **Security:** — · **Perf:** frees the UI
**Negative:** a cancel that reports success; a cancel that claims nothing happened
**G/W/T:** *Given* a running statement, *When* Cancel is pressed, *Then* the state is "cancelled" and the panel does not claim the statement was undone.
**Test plan:** `ConsoleTaskSuccessTest.staticAssetsExposeRequiredAffordances` (control present); live journey for the cancelled/recovery wording
**Evidence:** `../release-audit/74-console-task-success-evidence.md`
**DoD:** cancellation is explicit and honest about server-side uncertainty
**Release impact:** control over long operations

#### US-144 · Export SQL results as CSV and JSON
`E7 · P2 · VERIFIED · S`
**Persona:** P4
**Story:** As an operator, I want to export a result set, so that I can share or analyse it outside the Console.
**Outcome:** The last result set downloads as CSV or JSON without re-running the query; export is offered only when there is something to export.
**Deps:** US-085
**Files/modules:** `static/index.html` (`#sqlExportCsv`, `#sqlExportJson`), `static/js/console.js` (`exportSql`, `csvCell`)
**API:** — · **Data:** read-only · **UI:** export buttons · **Security:** escaping prevents CSV formula/quote breakage · **Perf:** client-side only
**Negative:** an export button that produces an empty or misaligned file
**G/W/T:** *Given* a 4-row result, *When* Export CSV is pressed, *Then* the file has 4 data rows plus a header, with values quoted where needed.
**Test plan:** client-side check in the evidence doc (deliberately client-only: no server round trip to regress)
**Evidence:** `../release-audit/74-console-task-success-evidence.md`
**DoD:** export matches the rendered result set
**Release impact:** interoperability

#### US-145 · SQL editor assistance (highlighting, autocomplete, formatting, saved queries, tabs, explain plan)
`E7 · P3 · NOT IMPLEMENTED · L`
**Persona:** P4
**Story:** As an operator, I want editor assistance, so that writing SQL is faster and errors are caught earlier.
**Outcome:** Not present: syntax highlighting, autocomplete, SQL formatting, named saved-query library, multiple editor tabs, and `EXPLAIN`.
**Deps:** US-084, US-044
**Files/modules:** would touch `static/js/console.js` and a server-side plan endpoint
**API:** `EXPLAIN` needs an engine plan API that does not exist
**Data:** — · **UI:** SQL Studio · **Security:** — · **Perf:** —
**Negative:** advertising assistance that does not work — the failure this story exists to prevent
**G/W/T:** *Given* the SQL Studio, *When* inspected, *Then* these affordances are absent and are listed as limitations rather than implied by the UI.
**Test plan:** none — no capability to test
**Evidence:** documented absence in `../release-audit/74-console-task-success-evidence.md`
**DoD:** remains on the published limitation list until implemented
**Release impact:** must not be claimed as present

## Epic E8 — Website, branding & accessibility

#### US-100 · Static website builds and deploys
`E8 · P1 · VERIFIED · S`
**Persona:** P5
**Story:** As an evaluator, I want the website to deploy from the repo, so that the published page matches the code.
**Outcome:** Pages workflow publishes `docs/`.
**Deps:** —
**Files/modules:** `.github/workflows/pages.yml`, `docs/index.html`
**API:** — · **Data:** — · **UI:** website · **Security:** no secrets in the workflow · **Perf:** —
**Negative:** live site stale relative to committed copy
**G/W/T:** *Given* a push to main, *When* the workflow runs, *Then* the corrected site is published.
**Test plan:** workflow run
**Evidence:** workflow status + live URL
**DoD:** deployed page equals committed page
**Release impact:** RELEASE BLOCKER while stale

#### US-101 · Accurate feature claims on the site
`E8 · P0 · VERIFIED · M`
**Persona:** P5
**Story:** As an evaluator, I want site claims to match the code, so that I am not misled.
**Outcome:** No claim exceeds measured capability.
**Deps:** US-004
**Files/modules:** `docs/index.html`, `README.md`
**API:** — · **Data:** — · **UI:** website · **Security:** — · **Perf:** —
**Negative:** "no DDL" claim while `CREATE/DROP TABLE` exists (historical R-54); fixed-dimension vector claim (historical)
**G/W/T:** *Given* each claim, *When* checked against the parser/engine, *Then* it holds.
**Test plan:** claim-to-code review
**Evidence:** `43-performance-claim-verification.md`
**DoD:** every claim verified or removed
**Release impact:** honesty gate

#### US-102 · Consistent yellow/white identity
`E8 · P1 · VERIFIED · S`
**Persona:** P5
**Story:** As a visitor, I want the site and Console to share one palette, so that they read as one product.
**Outcome:** Shared design tokens.
**Deps:** US-008
**Files/modules:** `docs/index.html`, Console CSS
**API:** — · **Data:** — · **UI:** both · **Security:** — · **Perf:** —
**Negative:** diverging palettes
**G/W/T:** *Given* both surfaces, *When* compared, *Then* they use the same token set.
**Test plan:** token comparison
**Evidence:** `45-website-console-consistency.md`
**DoD:** token sets identical
**Release impact:** brand gate

#### US-103 · Same logo and mascot everywhere
`E8 · P1 · VERIFIED · S`
**Persona:** P5
**Story:** As a visitor, I want one logo/mascot, so that branding is coherent.
**Outcome:** Same files referenced by both surfaces.
**Deps:** US-102
**Files/modules:** `docs/assets/junifydb-*`, Console `logo.svg`
**API:** — · **Data:** — · **UI:** both · **Security:** — · **Perf:** —
**Negative:** two different marks
**G/W/T:** *Given* both surfaces, *When* the mark is inspected, *Then* it is the same asset.
**Test plan:** checksum compare
**Evidence:** asset checksums
**DoD:** one source asset
**Release impact:** brand gate

#### US-104 · Favicon on both surfaces
`E8 · P2 · VERIFIED · S`
**Persona:** P5
**Story:** As a visitor, I want a favicon, so that the tab is identifiable.
**Outcome:** Favicon resolves on site and Console.
**Deps:** US-103
**Files/modules:** favicon assets
**API:** — · **Data:** — · **UI:** both · **Security:** — · **Perf:** —
**Negative:** broken favicon request
**G/W/T:** *Given* either surface, *When* the tab loads, *Then* the favicon resolves.
**Test plan:** network check
**Evidence:** trace entry
**DoD:** 200 for favicon
**Release impact:** polish

#### US-105 · Compiling code samples on the site
`E8 · P1 · PARTIAL · M`
**Persona:** P1
**Story:** As a developer, I want site samples to compile, so that I can copy them safely.
**Outcome:** Each sample compiles against the released API.
**Deps:** US-003
**Files/modules:** `docs/index.html`
**API:** — · **Data:** — · **UI:** website · **Security:** — · **Perf:** —
**Negative:** samples use removed APIs
**G/W/T:** *Given* each sample, *When* compiled, *Then* it compiles.
**Test plan:** extract + compile samples
**Evidence:** compile transcript
**DoD:** all samples compile
**Release impact:** onboarding

#### US-106 · Honest performance claims
`E8 · P1 · VERIFIED · M`
**Persona:** P5
**Story:** As an evaluator, I want throughput numbers labelled as indicative, so that I do not treat them as certified.
**Outcome:** Numbers carry a "re-run it yourself" caveat and a method.
**Deps:** —
**Files/modules:** `README.md`, `docs/index.html`, `24-performance-and-benchmarks.md`
**API:** — · **Data:** — · **UI:** website · **Security:** — · **Perf:** numbers ·
**Negative:** one machine's numbers presented as universal
**G/W/T:** *Given* a perf claim, *When* read, *Then* the hardware/method caveat is adjacent.
**Test plan:** doc review
**Evidence:** claim text + harness
**DoD:** no uncaveated benchmark claim
**Release impact:** honesty

#### US-107 · Mobile-friendly website
`E8 · P2 · PARTIAL · M`
**Persona:** P5
**Story:** As a visitor, I want the site usable on mobile, so that I can read it on a phone.
**Outcome:** Content remains readable at phone widths.
**Deps:** —
**Files/modules:** `docs/index.html`
**API:** — · **Data:** — · **UI:** responsive site · **Security:** — · **Perf:** —
**Negative:** horizontal scrolling / clipped tables
**G/W/T:** *Given* a phone viewport, *When* the site loads, *Then* content is readable without horizontal scroll.
**Test plan:** viewport screenshots
**Evidence:** screenshots
**DoD:** readable at 390px width
**Release impact:** reach

#### US-108 · Accessible website
`E8 · P2 · PARTIAL · M`
**Persona:** P5
**Story:** As a visitor, I want a site that passes basic accessibility checks, so that it is usable with assistive tech.
**Outcome:** Sufficient contrast, alt text, landmarks, headings.
**Deps:** —
**Files/modules:** `docs/index.html`
**API:** — · **Data:** — · **UI:** site a11y · **Security:** — · **Perf:** —
**Negative:** unlabeled images, low contrast
**G/W/T:** *Given* the site, *When* audited, *Then* no critical a11y findings remain.
**Test plan:** automated + manual audit
**Evidence:** audit report
**DoD:** no critical findings
**Release impact:** accessibility gate

#### US-109 · Visible license and attribution
`E8 · P3 · VERIFIED · S`
**Persona:** P5
**Story:** As a consumer, I want the license visible, so that I know my obligations.
**Outcome:** License is linked from README and site.
**Deps:** —
**Files/modules:** `LICENSE`, `README.md`, `docs/index.html`
**API:** — · **Data:** — · **UI:** website · **Security:** — · **Perf:** —
**Negative:** missing license file
**G/W/T:** *Given* the repo, *When* the license link is followed, *Then* Apache-2.0 text is present.
**Test plan:** review
**Evidence:** file presence
**DoD:** license present and linked
**Release impact:** governance

---

## Epic E9 — Framework & language integrations

#### US-110 · Plain Java integration
`E9 · P0 · VERIFIED · S`
**Persona:** P1
**Story:** As a plain-Java developer, I want the core usable with no DI container, so that I can adopt without a framework.
**Outcome:** No CDI runtime is required; the core runs on a bare JVM.
**Deps:** US-001
**Files/modules:** core; CDI is `provided`+`optional`
**API:** direct construction / factory
**Data:** — · **UI:** — · **Security:** — · **Perf:** —
**Negative:** CDI forced onto plain users
**G/W/T:** *Given* only the core + 3 deps on the classpath, *When* the database runs, *Then* no CDI class is required.
**Test plan:** bare-classpath test
**Evidence:** classpath listing + run
**DoD:** no mandatory framework dependency
**Release impact:** P0 positioning

#### US-111 · Spring Boot auto-configuration
`E9 · P1 · VERIFIED · M`
**Persona:** P3
**Story:** As a Spring developer, I want a `JunifyDB` bean auto-configured, so that I inject it directly.
**Outcome:** App starts with an injected database; properties bind.
**Deps:** US-012
**Files/modules:** `spring-boot-starter`
**API:** auto-config + properties
**Data:** per config · **UI:** — · **Security:** — · **Perf:** —
**Negative:** conflicts with the consumer's logging/CDI
**G/W/T:** *Given* the starter, *When* the app starts, *Then* `JunifyDB` is injectable and configured.
**Test plan:** starter test
**Evidence:** build log
**DoD:** starter tests green
**Release impact:** ecosystem

#### US-112 · Spring Boot demo runs
`E9 · P1 · VERIFIED · M`
**Persona:** P3
**Story:** As a Spring developer, I want a runnable demo, so that I can see real REST endpoints.
**Outcome:** REST endpoints serve CRUD.
**Deps:** US-111
**Files/modules:** `demo/spring-boot-demo`
**API:** REST
**Data:** demo data · **UI:** — · **Security:** — · **Perf:** —
**Negative:** demo resolves a stale core
**G/W/T:** *Given* a core install, *When* the demo runs, *Then* endpoints return the expected data.
**Test plan:** demo test
**Evidence:** demo output
**DoD:** demo green from the documented order
**Release impact:** framework claim

#### US-113 · Quarkus extension
`E9 · P1 · VERIFIED · M`
**Persona:** P3
**Story:** As a Quarkus developer, I want build-time config and CDI producers, so that I can `@Inject` the database.
**Outcome:** Extension builds and produces a bean.
**Deps:** US-012
**Files/modules:** `quarkus-extension`
**API:** producer + config
**Data:** per config · **UI:** — · **Security:** — · **Perf:** native-compatible APIs
**Negative:** reflection-based paths break native builds
**G/W/T:** *Given* the extension, *When* injected, *Then* a configured database is available.
**Test plan:** extension build
**Evidence:** build log
**DoD:** builds and injects
**Release impact:** ecosystem

#### US-114 · Quarkus demo runs
`E9 · P1 · VERIFIED · M`
**Persona:** P3
**Story:** As a Quarkus developer, I want a runnable demo, so that I can see CDI + REST working.
**Outcome:** REST resource returns data.
**Deps:** US-113
**Files/modules:** `demo/quarkus-demo`
**API:** REST
**Data:** demo data · **UI:** — · **Security:** — · **Perf:** —
**Negative:** CDI resolution failure
**G/W/T:** *Given* the demo, *When* it runs, *Then* the endpoint returns expected data.
**Test plan:** demo test
**Evidence:** demo output
**DoD:** demo green
**Release impact:** framework claim

#### US-115 · Micronaut integration
`E9 · P2 · VERIFIED · M`
**Persona:** P3
**Story:** As a Micronaut developer, I want a factory bean and config binding, so that I can inject the database.
**Outcome:** Bean is available and config binds.
**Deps:** US-012
**Files/modules:** `micronaut-integration`
**API:** factory + config
**Data:** — · **UI:** — · **Security:** — · **Perf:** reflection-free serde
**Negative:** runtime-reflection issues
**G/W/T:** *Given* the integration, *When* the app starts, *Then* the database is injectable.
**Test plan:** integration test
**Evidence:** build log
**DoD:** tests green
**Release impact:** ecosystem

#### US-116 · Micronaut demo runs
`E9 · P2 · VERIFIED · S`
**Persona:** P3
**Story:** As a Micronaut developer, I want a runnable demo.
**Outcome:** Demo executes repository queries.
**Deps:** US-115
**Files/modules:** `demo/micronaut-demo`
**API:** — · **Data:** demo data · **UI:** — · **Security:** — · **Perf:** —
**Negative:** stale core resolution
**G/W/T:** *Given* the demo, *When* run, *Then* it produces the documented output.
**Test plan:** demo test
**Evidence:** demo output
**DoD:** demo green
**Release impact:** framework claim

#### US-117 · Vert.x demo runs
`E9 · P2 · VERIFIED · S`
**Persona:** P3
**Story:** As a Vert.x developer, I want an async pattern demo, so that I can use `executeBlocking` safely.
**Outcome:** Vertical handles requests with worker-thread DB calls.
**Deps:** US-110
**Files/modules:** `demo/vertx-demo`
**API:** — · **Data:** demo data · **UI:** — · **Security:** — · **Perf:** non-blocking event loop
**Negative:** blocking the event loop
**G/W/T:** *Given* the demo, *When* run, *Then* responses are correct without blocking the loop.
**Test plan:** demo test
**Evidence:** demo output
**DoD:** demo green
**Release impact:** framework claim

#### US-118 · JUnit-friendly fixtures
`E9 · P1 · VERIFIED · M`
**Persona:** P2
**Story:** As a test engineer, I want per-test isolated instances, so that tests are deterministic.
**Outcome:** Each test gets a clean database; no cross-test state.
**Deps:** US-001
**Files/modules:** core test utilities
**API:** lifecycle helpers
**Data:** ephemeral · **UI:** — · **Security:** — · **Perf:** fast setup
**Negative:** shared singleton state
**G/W/T:** *Given* two tests, *When* run in either order, *Then* results are identical.
**Test plan:** order-permutation test
**Evidence:** test ids
**DoD:** no shared mutable state
**Release impact:** testability

#### US-119 · No-Docker integration testing
`E9 · P1 · VERIFIED · S`
**Persona:** P2
**Story:** As a test engineer, I want an in-process store instead of a container, so that CI needs no Docker.
**Outcome:** Integration tests run with no container runtime.
**Deps:** US-118
**Files/modules:** core
**API:** — · **Data:** in-memory · **UI:** — · **Security:** — · **Perf:** fast
**Negative:** a native-image pull leaks back in
**G/W/T:** *Given* no Docker, *When* integration tests run, *Then* they pass.
**Test plan:** CI run without Docker
**Evidence:** CI log
**DoD:** no container dependency
**Release impact:** differentiator

#### US-120 · Jakarta NoSQL compatibility
`E9 · P2 · PARTIAL · L`
**Persona:** P3
**Story:** As a Jakarta NoSQL user, I want the mapping annotations honored, so that I can port entities.
**Outcome:** Supported annotations map correctly.
**Deps:** US-061
**Files/modules:** `adapter/jnosql/*`
**API:** mapping
**Data:** entity mapping · **UI:** — · **Security:** — · **Perf:** —
**Negative:** a subset is claimed as full compliance
**G/W/T:** *Given* a Jakarta NoSQL entity, *When* mapped, *Then* the documented annotation set resolves.
**Test plan:** compatibility test
**Evidence:** `26-jakarta-nosql-compatibility.md`
**DoD:** supported subset documented; unclaimed features listed
**Release impact:** honesty

#### US-121 · End-to-end durability validation demo
`E9 · P1 · VERIFIED · M`
**Persona:** P2
**Story:** As a test engineer, I want a durability matrix demo, so that I can trust restart behavior per engine.
**Outcome:** Demo proves persistence + cold-restart recovery per engine.
**Deps:** US-062
**Files/modules:** `demo/end-to-end-validation`
**API:** — · **Data:** per engine · **UI:** — · **Security:** — · **Perf:** —
**Negative:** demo answered by the process it should replace (historical gate trap)
**G/W/T:** *Given* each engine, *When* the demo runs, *Then* it proves cold-restart recovery from a real stop.
**Test plan:** demo test
**Evidence:** demo output per engine
**DoD:** proves stop, restart, and replay
**Release impact:** durability evidence

---

## Epic E10 — Security, performance, demos, packaging & release

#### US-122 · CORS off by default
`E10 · P0 · VERIFIED · M`
**Persona:** P6
**Story:** As a maintainer, I want no wildcard CORS by default, so that a visited website cannot read the database.
**Outcome:** Default server sends no permissive `Access-Control-Allow-Origin`.
**Deps:** US-078
**Files/modules:** `config/SecurityConfig`, `console/http/JunifyDBServer`
**API:** — · **Data:** — · **UI:** — · **Security:** High-severity historical R-61 · **Perf:** —
**Negative:** wildcard CORS applied only when auth enabled (historical)
**G/W/T:** *Given* a hostile origin, *When* it fetches the API from a browser, *Then* the read is blocked.
**Test plan:** browser cross-origin proof
**Evidence:** pre/post browser proof (READ SUCCESS → BLOCKED)
**DoD:** blocked by default; opt-in documented
**Release impact:** RELEASE BLOCKER resolved

#### US-123 · No secrets in the repository
`E10 · P0 · VERIFIED · S`
**Persona:** P6
**Story:** As a maintainer, I want no tracked credentials, so that the repo is safe to publish.
**Outcome:** No `.env`/keystore/`.pem`/secret patterns tracked.
**Deps:** —
**Files/modules:** repo-wide
**API:** — · **Data:** — · **UI:** — · **Security:** secrets scan · **Perf:** —
**Negative:** a secret in history
**G/W/T:** *Given* the tree, *When* scanned, *Then* no secret patterns match.
**Test plan:** pattern scan
**Evidence:** scan output
**DoD:** clean scan
**Release impact:** publication safety

#### US-124 · Password policy for Console auth
`E10 · P1 · VERIFIED · S`
**Persona:** P4
**Story:** As an operator, I want weak passwords rejected, so that the Console is not trivially broken.
**Outcome:** Policy enforces minimum strength.
**Deps:** US-097
**Files/modules:** `security/PasswordPolicy`
**API:** — · **Data:** sessions · **UI:** login · **Security:** password strength · **Perf:** —
**Negative:** policy silently bypassed
**G/W/T:** *Given* a weak password, *When* set, *Then* it is rejected.
**Test plan:** policy test
**Evidence:** test id
**DoD:** acceptance passes
**Release impact:** security

#### US-125 · Load & stress demo
`E10 · P1 · VERIFIED · M`
**Persona:** P5
**Story:** As an evaluator, I want a stress harness, so that I can reproduce throughput claims.
**Outcome:** Harness runs the documented scenarios with reproducible output.
**Deps:** US-071
**Files/modules:** `demo/load-and-stress-demo`
**API:** — · **Data:** — · **UI:** — · **Security:** — · **Perf:** measured ·
**Negative:** results presented as certified benchmarks
**G/W/T:** *Given* the harness, *When* run, *Then* it prints the documented metrics.
**Test plan:** demo test
**Evidence:** demo output
**DoD:** harness runs; numbers caveated
**Release impact:** performance honesty

#### US-126 · Atomic batch ingestion
`E10 · P2 · VERIFIED · M`
**Persona:** P1
**Story:** As a developer, I want chunked atomic batches, so that a failed batch rolls back.
**Outcome:** 10K documents load in chunks; a fault rolls back its chunk.
**Deps:** US-036
**Files/modules:** `demo/batch-processing-demo`
**API:** batch API
**Data:** bulk · **UI:** — · **Security:** — · **Perf:** chunked
**Negative:** partial chunk applied on fault
**G/W/T:** *Given* a batch with an injected fault, *When* it runs, *Then* the chunk rolls back.
**Test plan:** fault-injection demo
**Evidence:** demo output
**DoD:** rollback proven
**Release impact:** reliability

#### US-127 · Demos run from a clean checkout
`E10 · P0 · PARTIAL · M`
**Persona:** P5
**Story:** As an evaluator, I want every demo to run from a fresh clone, so that I can trust the results.
**Outcome:** Documented ordering reproduces all demo results.
**Deps:** US-016, US-112, US-114, US-116, US-117
**Files/modules:** `demo/*`, `demo/RUNBOOK.md`, `demo/VALIDATION-MATRIX.md`
**API:** — · **Data:** demo data · **UI:** — · **Security:** — · **Perf:** —
**Negative:** demos resolve a stale installed core (documented trap)
**G/W/T:** *Given* a clean checkout, *When* the runbook is followed, *Then* every demo passes.
**Test plan:** clean-checkout dry run
**Evidence:** runbook + logs
**DoD:** all demos pass in order
**Release impact:** RELEASE BLOCKER if demos fail from clean

#### US-128 · Maven Central readiness
`E10 · P1 · NOT VERIFIED · M`
**Persona:** P6
**Story:** As a maintainer, I want Central-ready artifacts, so that users can resolve coordinates.
**Outcome:** Sources/javadoc/GPF signing + publisher plugin configured; a dry-run succeeds.
**Deps:** US-017
**Files/modules:** `pom.xml` (`maven-central` profile)
**API:** — · **Data:** — · **UI:** — · **Security:** signing keys · **Perf:** —
**Negative:** claiming readiness without a dry run (historical R-56)
**G/W/T:** *Given* credentials and a key, *When* a dry-run publishes, *Then* staging validates. *(Blocked: no credentials/key in the environment.)*
**Test plan:** dry-run publication
**Evidence:** `46-maven-central-readiness.md`
**DoD:** dry-run validated **or** Central availability not claimed
**Release impact:** RELEASE BLOCKER only if Central is claimed

#### US-129 · Versioning & backward compatibility
`E10 · P2 · PARTIAL · M`
**Persona:** P6
**Story:** As a consumer, I want a versioning policy, so that upgrades are predictable.
**Outcome:** SemVer policy stated; 0.x label reflects the limitations.
**Deps:** —
**Files/modules:** `CHANGELOG.md`, `README.md`
**API:** — · **Data:** — · **UI:** — · **Security:** — · **Perf:** —
**Negative:** a 1.0 label implies JDBC compliance and stored routines that do not exist
**G/W/T:** *Given* the release version, *When* read, *Then* it signals pre-1.0 maturity.
**Test plan:** review
**Evidence:** `47-versioning-and-backward-compatibility.md`
**DoD:** policy documented; version is honest
**Release impact:** expectation-setting

#### US-130 · README and docs match the code
`E10 · P0 · PARTIAL · M`
**Persona:** P5
**Story:** As an evaluator, I want docs to match behavior, so that I can trust them.
**Outcome:** No documented feature is absent; no present feature is undocumented.
**Deps:** US-101
**Files/modules:** `README.md`, `docs/**`
**API:** — · **Data:** — · **UI:** — · **Security:** — · **Perf:** —
**Negative:** contradictory audit docs (historical R-56)
**G/W/T:** *Given* a documented claim, *When* checked, *Then* it holds.
**Test plan:** traceability review
**Evidence:** `56-documentation-traceability.md`
**DoD:** docs/implementation traceability complete
**Release impact:** honesty

#### US-131 · CHANGELOG reflects changes
`E10 · P2 · PARTIAL · S`
**Persona:** P6
**Story:** As a consumer, I want a changelog, so that I can see what changed.
**Outcome:** Unreleased section covers the correctness fixes.
**Deps:** US-130
**Files/modules:** `CHANGELOG.md`
**API:** — · **Data:** — · **UI:** — · **Security:** — · **Perf:** —
**Negative:** changelog omits breaking fixes
**G/W/T:** *Given* the changelog, *When* read, *Then* it covers this release's fixes.
**Test plan:** review
**Evidence:** CHANGELOG text
**DoD:** entries accurate
**Release impact:** transparency

#### US-132 · Contribution guide
`E10 · P3 · VERIFIED · S`
**Persona:** P6
**Story:** As a contributor, I want a contribution guide, so that I can build and test.
**Outcome:** Guide covers build, test, and PR expectations.
**Deps:** —
**Files/modules:** `CONTRIBUTING.md`
**API:** — · **Data:** — · **UI:** — · **Security:** — · **Perf:** —
**Negative:** stale commands
**G/W/T:** *Given* the guide, *When* commands are followed, *Then* they work.
**Test plan:** follow the guide
**Evidence:** command transcript
**DoD:** commands verified
**Release impact:** governance

#### US-133 · CI enforces the gates
`E10 · P0 · VERIFIED · M`
**Persona:** P6
**Story:** As a maintainer, I want CI to run tests and console gates, so that regressions fail.
**Outcome:** CI runs the suite + contract/auth gates.
**Deps:** US-068, US-097
**Files/modules:** `.github/workflows/ci.yml`, `scripts/*.sh`
**API:** — · **Data:** — · **UI:** — · **Security:** — · **Perf:** —
**Negative:** gate silently skipped
**G/W/T:** *Given* a breaking change, *When* CI runs, *Then* it fails.
**Test plan:** CI dry run
**Evidence:** CI log
**DoD:** gates run and fail correctly
**Release impact:** regression safety

#### US-134 · Release artifact is reproducible
`E10 · P1 · VERIFIED · M`
**Persona:** P6
**Story:** As a maintainer, I want a reproducible jar, so that the release is verifiable.
**Outcome:** Two builds give the same checksum.
**Deps:** US-017
**Files/modules:** `pom.xml`, reproducibility script
**API:** — · **Data:** — · **UI:** — · **Security:** supply chain · **Perf:** —
**Negative:** nondeterministic jar
**G/W/T:** *Given* two builds, *When* compared, *Then* checksums match.
**Test plan:** reproducibility gate
**Evidence:** checksum pair (this session: `b8ce7e55…`)
**DoD:** PASS
**Release impact:** release integrity

#### US-135 · Release-blocker register is live
`E10 · P0 · VERIFIED · S`
**Persona:** P6
**Story:** As a maintainer, I want a blocker register, so that nothing ships unaddressed.
**Outcome:** Every blocker has a disposition (fixed / external / documented limitation).
**Deps:** —
**Files/modules:** `54-release-blocker-register.md`
**API:** — · **Data:** — · **UI:** — · **Security:** — · **Perf:** —
**Negative:** blockers hidden
**G/W/T:** *Given* the register, *When* read, *Then* no entry is "open and unexplained".
**Test plan:** review
**Evidence:** register text
**DoD:** no unexplained open blocker
**Release impact:** release gate

#### US-136 · Public release checklist
`E10 · P0 · VERIFIED · S`
**Persona:** P6
**Story:** As a release commander, I want a checklist, so that every gate is confirmed.
**Outcome:** Every checklist item is green or explicitly explained.
**Deps:** all
**Files/modules:** `60-public-release-checklist.md`
**API:** — · **Data:** — · **UI:** — · **Security:** — · **Perf:** —
**Negative:** a checklist item marked done without evidence
**G/W/T:** *Given* the checklist, *When* reviewed, *Then* each item cites evidence.
**Test plan:** review
**Evidence:** checklist text
**DoD:** every item evidenced or explained
**Release impact:** release gate

#### US-137 · Honest final go/no-go decision
`E10 · P0 · VERIFIED · S`
**Persona:** P5
**Story:** As a stakeholder, I want a final decision with limitations, so that I can approve knowingly.
**Outcome:** Decision is APPROVED-WITH-LIMITATIONS or BLOCKED, with the limitation list published.
**Deps:** US-136
**Files/modules:** `final-go-no-go-decision.md`
**API:** — · **Data:** — · **UI:** — · **Security:** — · **Perf:** —
**Negative:** approval that relies on unverifiable claims
**G/W/T:** *Given* the evidence, *When* the decision is read, *Then* every approved claim has execution-backed evidence and every gap is listed.
**Test plan:** traceability review
**Evidence:** decision doc + evidence table
**DoD:** no approved claim without evidence
**Release impact:** the decision itself


> **⚠ SUPERSEDED — pre-refactor document (SQL / dual-engine).**
> JunifyDB is now NoSQL-only. The relational engine, SQL parser/planning, JDBC driver, JPA
> `EntityManager`, `/api/sql` routes, and the SQL Studio console screen were removed from the
> product. Statements in this file that describe SQL, JDBC, SQL schemas, or an engine selector no
> longer describe shipped behavior.
>
> - Current product definition: [PRODUCT-CONSTITUTION.md](../release-audit/refocus/PRODUCT-CONSTITUTION.md)
> - Removal inventory and evidence: [SQL-REMOVAL-MANIFEST.md](../release-audit/refocus/SQL-REMOVAL-MANIFEST.md)
>
> Retained as historical record only — not part of the current release contract.


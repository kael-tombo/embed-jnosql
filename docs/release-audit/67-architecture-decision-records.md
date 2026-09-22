# 67 — Architecture Decision Records

Decisions taken (or confirmed) during the public-release audit. Each records the context,
the decision, the alternative rejected, and the consequence — including the honest cost.
Statuses: **ACCEPTED**, **ACCEPTED (documented limitation)**, **SUPERSEDED**.

---

## ADR-001 — Ship one artifact (`junify-db-core`), not 14 engine modules

**Status:** ACCEPTED (documented limitation)

**Context.** The product vision calls for separately deployable engine modules
(`junify-db-sql`, `junify-db-nosql`, `junify-db-storage-*`, `junify-db-jdbc`,
`junify-db-console`). The codebase as built is a **single Maven module**
(`<modules>` is empty) whose 97 main source files separate those concerns by *package*.

**Decision.** Keep the single shaded jar for the public release. Package boundaries already
exist and are clean (no circular dependencies; SQL depends on NoSQL only through 3 imports;
`provided` scoping keeps frameworks out of the core). Splitting into modules would change
coordinates, packaging, and consumer imports for cosmetic gain, immediately before a first
public tag.

**Rejected.** A pre-release modularisation refactor — high blast radius on 785 passing
tests, zero functional benefit, and it would invalidate the demo/e2e verification just
completed.

**Consequence.** The jar is 3.11 MB and consumers cannot exclude the SQL engine or the
Console by dependency selection. If the ecosystem ever wants that, splitting is the
follow-up — recorded as a post-release improvement, not a blocker. Consumers who care about
auditability have a precise alternative today: only three mandatory dependencies.

---

## ADR-002 — The SQL engine is a query layer over the document store

**Status:** ACCEPTED (documented limitation)

**Context.** `sql/engine/SqlEngine` resolves SQL tables through
`JunifyDB.documentCollection()` — SQL "tables" *are* document collections. The two engines
have separate lexers, parsers, ASTs, and execution paths, but share storage and catalog.

**Decision.** Keep the shared substrate and describe the product honestly as a **multi-model
database with two query engines over one storage/catalog layer**, rather than claiming two
independently evolvable database products.

**Rejected.** (a) A separate relational storage/catalog layer — enormous work with no
demonstrated user need. (b) Continuing to describe the engines as fully independent —
that would be a false claim.

**Consequence.** The shared substrate is also a defect *vector*, proven by R-48: SQL reads
inherited the document store's auto-create semantics and created collections as a side
effect. Every future SQL feature must be checked for shared-semantics inheritance. The
compensating control is the contract gate (ADR-003).

---

## ADR-003 — Contract gates are the release mechanism, not just tests

**Status:** ACCEPTED

**Context.** Ten audit rounds found 23 defects, overwhelmingly of one class: **fake
success** — an operation that did nothing but answered `200`. Unit tests did not catch them
because the tests were written against the same wrong assumption as the code.

**Decision.** Every console-called surface is covered by executable gates
(`scripts/console-contract-gate.sh`, `scripts/console-auth-gate.sh`) that boot a **real
server** on a **real engine** and assert observable behaviour (match counts, status codes,
catalog size before/after). CI runs the contract gate on **FILE, LSM_TREE, and B_TREE**.

**Rejected.** Relying on unit tests alone; asserting only status codes.

**Consequence.** A defect class that was invisible to the existing suite is now
mechanically caught. Two process hardenings were required by experience: the gate must run
against a **freshly packaged jar** (it silently tested a stale one), and it must **fail fast
if its port is already bound** (it once "passed"/failed against a leftover server from a
previous round).

---

## ADR-004 — Read paths never mutate the catalog

**Status:** ACCEPTED

**Context.** R-48: `SELECT * FROM no_such_table` **created** an empty collection as a side
effect of reading, answered `rowCount:0, success`, and left the typo in the catalog
permanently. JOIN targets, UPDATE, DELETE and DROP inherited it.

**Decision.** Only **INSERT** and **CREATE TABLE** may auto-create a table (the documented
schemaless workflow that backup/restore, migrations, and the demo app depend on — 10+
internal callers). SELECT, JOIN resolution, UPDATE, DELETE, and DROP resolve through a
non-creating path and throw `SqlUnknownTableException`, mapped by the endpoint to **404** —
deliberately distinct from 400 (malformed SQL) and from the old fake success.

**Rejected.** Making the engine non-creating everywhere (breaks the documented workflow);
leaving reads creating silently (a read that writes is indefensible).

**Consequence.** INSERT remains a creator by design and must stay documented as such.

---

## ADR-005 — Expired documents are invisible to all reads

**Status:** ACCEPTED

**Context.** R-51: TTL-expired documents were still returned by reads, carrying an
`expired:true` hint. Console, SQL engine, and adapters all saw logically-deleted data.

**Decision.** Reads filter expired documents out entirely (point read → 404, scans exclude
them, SQL aggregates exclude them). Expiry is a **logical deletion**, not a lifecycle flag
for consumers to interpret. The sweeper scans raw storage rather than `findAll()`, since the
filtered view no longer contains the documents it must delete.

**Rejected.** Making every caller check the flag — the wrong default for a database.

**Consequence.** Any future read path added to `DocumentCollection` must apply the same
filter; the TTL tests are the guard.

---

## ADR-006 — Vector indexes persist as JSON; the graph is rebuildable

**Status:** ACCEPTED

**Context.** R-52: vector indexes lived only in Console-server memory; every restart
silently lost all vectors while documents and KV survived. A vector store that forgets on
restart is a durability defect.

**Decision.** Persist **vectors and index parameters** as JSON per index under
`<dataDir>/vectors/{index}.json` on every add/remove, best-effort exactly like the audit
writer (a disk failure never fails the API call). Do **not** serialize the HNSW graph — it
is rebuilt by re-adding vectors in stored order. A corrupt file is renamed aside
(`*.json.corrupt-<ts>`) instead of blocking startup. `IN_MEMORY` neither persists nor
restores, preserving its no-durability promise.

**Rejected.** Serializing the graph (fragile, coupled to build internals); leaving vectors
volatile and documenting it (users would still lose data).

**Consequence.** Search semantics are identical after restart; the internal link layout may
differ, which is documented in the Javadoc rather than hidden.

---

## ADR-007 — GitHub-first release; Maven Central is a later milestone

**Status:** ACCEPTED (documented limitation)

**Context.** `central-publishing-maven-plugin` and GPG signing are configured, but no
Central credentials or signing key exist in the environment (R-13). Verification of a
Central dry-run publication is therefore impossible.

**Decision.** Release the first public version from **GitHub Releases** (tag + shaded jar +
sources/javadoc), keeping the Central configuration in place and documented. Do not claim
Central availability.

**Rejected.** Declaring a Central release that cannot be verified; removing the Central
config (it is correct and will be needed).

**Consequence.** Consumers use a direct dependency or install locally until Portal
credentials exist. Recorded as the only external-dependency blocker (R-13).

---

## ADR-008 — The first public version is `v0.9.0`

**Status:** ACCEPTED

**Context.** The product is tested (785 + 4 + 4 green), tiny (3.11 MB), and honest — but a
1.0 database is expected to ship **JDBC** and **constraint enforcement**, and this one has
neither.

**Decision.** Tag the first public release **`v0.9.0`**, with the limitation list stated on
the release page and in the README.

**Rejected.** `v1.0.0` for parity with the internal Go/No-Go doc — the credibility cost of
over-claiming is larger than the marketing cost of a 0.x label.

**Consequence.** `docs/release-audit/63-final-go-no-go-decision.md` (round-3 internal verdict,
which referenced v1.0.0) is **superseded** by `final-go-no-go-decision.md` as the canonical
public-release decision.

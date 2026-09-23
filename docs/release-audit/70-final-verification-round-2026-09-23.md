# Final Verification Round — 2026-09-23

**Purpose:** re-verify the release-critical gates on the current `main` (`81a511c`) after the
durability round, and close the product-deliverable gap (`docs/product/`) named by the release
prompt. This document records **what was actually executed in this round** and nothing more.
Prior rounds' evidence is cited, not re-claimed.

## Environment

Java 23.0.1 · Maven 3.9.6 · Node 24.15.0 / npm 11.12.1 · Windows Server 2022 (MINGW64).
Raw capture: `baseline/_environment.txt`.

## What was executed in this round (fresh)

| # | Check | Command | Result |
|---|---|---|---|
| 1 | Full build + test | `./mvnw -B -ntp clean test` | **BUILD SUCCESS — 832 tests, 0 failures, 0 errors, 0 skipped** |
| 2 | Shaded artifact | `./mvnw -B -ntp -DskipTests clean package` | **`junify-db-core-1.0.0.jar` = 3,122,887 bytes (3.12 MB)**, SHA-1 `b8ce7e551f50c25120749c0af8e55d1e0c7fc7d5` |
| 3 | Line coverage | JaCoCo `target/site/jacoco/jacoco.csv` | **75.5%** line (27,791 covered / 9,030 missed); branch **59.2%** |
| 4 | Live runtime probe | `java -jar … --port 18080 --engine IN_MEMORY` + `curl` | health OK, metrics real, Console served, document round-trip OK, **no wildcard CORS** |
| 5 | Structure census | `find`/`grep` | 98 main / 72 test sources, 10 demo projects, 22 Console `/api` routes, 4 storage engines |
| 6 | Product deliverables | new files | `docs/product/PRODUCT_BLUEPRINT.md`, `docs/product/USER_STORY_MAP.md` (137 stories) |

Raw logs: `baseline/_mvn-test-raw.log`, `baseline/_mvn-package-raw.log`,
`baseline/RUNTIME-BASELINE.txt`.

## What was NOT re-executed in this round (cited from the prior round — stated honestly)

These were verified in the previous round and are **not** re-run here. They remain valid
evidence from `66-improvement-round-3-evidence.md` and `TEST-BASELINE.txt`, but this round does
not add a new execution to them:

- Console **contract gate** and **auth gate** across FILE / LSM_TREE / B_TREE.
- The **crash-durability gate** (stop → restart → replay → 12/12 documents with bodies).
- The **reproducibility gate** (two-build checksum equality).
- **CLI** (`4/4`) and **demos** (`43/43` across nine projects).

## Findings

1. **Gates that are green are green by measurement** — the suite, coverage, artifact size, and
   the live runtime probe all pass on the current commit.
2. **The stale documentation numbers are corrected.** The baseline files previously said
   785 tests / 3.11 MB; they now say 832 tests / 3.12 MB. The canonical decision already quoted
   832 for the suite; the baseline now agrees with it.
3. **The startup banner still prints `Flush mode: sync`** while R-67 records that
   `--sync`/`--async` are inverted in effect and that the banner overstates them. This is a
   **naming/documentation defect, not a durability one** (every write is fsynced to the WAL
   before acknowledgement). It is carried forward as R-67, not silently fixed, because changing
   what `--sync` means is a durability-contract change per engine.
4. **The product-deliverable gap is closed.** `docs/product/PRODUCT_BLUEPRINT.md` and
   `docs/product/USER_STORY_MAP.md` (137 stories, 10 epics) now exist. The story map labels every
   capability honestly: 4 `NOT IMPLEMENTED` (procedures/functions/triggers, `EXPLAIN`),
   24 `PARTIAL` (JDBC is one of them), 1 `NOT VERIFIED` (Maven Central), 1 `EXPERIMENTAL`
   (Kafka CDC). (Counted after all three slices below; at the moment of the table above the
   split was 8 / 23.)
5. **Nothing was discarded.** No `reset`, `checkout --`, or `clean -fd`. The concurrent session's
   29 modified files were left untouched.

## Post-script — the constraint slice (same day)

After the table above was captured, the JDBC/constraint follow-up began with the constraint
slice (R-74): `PRIMARY KEY`, `UNIQUE` and `NOT NULL` are now parsed and enforced, durable
across restart. This changed the numbers quoted above:

| Metric | Before slices | Slice 1 (keys) | Slice 2 (referential + CHECK) | Slice 3 (JDBC) |
|---|---|---|---|---|
| Tests | 832 | 846 (+14 `SqlConstraintTest`) | 859 (+13 `SqlReferentialConstraintTest`) | **872** (+13 `JdbcDriverTest`) |
| Line coverage | 75.5% | 76.1% | 76.5% | **74.4%** (proxy dispatch adds uncovered branches) |
| Shaded jar | 3,122,887 B | 3,133,120 B | 3,136,885 B | **3,164,082 B** (still < 5 MB) |

Full records: `71-constraint-enforcement-evidence.md`,
`72-referential-constraint-evidence.md`, `73-jdbc-driver-evidence.md`. The JDBC driver remains
`PARTIAL` (no explicit transactions or schema reflection).

## Impact on the release decision

None of the release gates changed direction: the passing gates still pass, and the one
non-passing item (Maven Central verification) is still blocked on absent credentials, exactly as
`final-go-no-go-decision.md` records. This round therefore **confirms** the canonical decision's
`RELEASE APPROVED WITH EXPLICIT LIMITATIONS` verdict and refreshes its evidence, rather than
overturning it.

---

## Slice 4 (same day): Console task-success layer (R-77 … R-81)

Measured against the **Console UX quality and task-success requirements**, which the earlier
verdict did not cover. The Console rendered *activity* but never *context, outcome or provenance*:
no orientation context (an in-memory database was indistinguishable from a durable one),
`window.confirm` for destructive actions, no correlation id anywhere, a 0-row result shown as
success, and a stalled request with no state at all.

| Metric | Slice 3 (JDBC) | Slice 4 (Console task success) |
|---|---|---|
| Tests | 872 | **879** (+7 `ConsoleTaskSuccessTest`) |
| Coverage gate | not re-run | **PASS** — `All coverage checks have been met.` |
| Measured line coverage | 74.4% (41,140 lines) | 75.4% (**8,728** measured lines) — see the caveat below |
| Shaded jar | 3,164,082 B | **3,174,260 B** (SHA-1 `c5d037f2…`, still < 5 MB) |

**Falsification.** The subject here is UI behaviour, so the falsification is different from the
library slices: the seven new tests assert both the service contract and the *shipped static
assets*, and the **pre-change server still listening on port 8081 was probed directly** —
`NO context block present`, `HTTP/1.1 404` with no `X-Correlation-Id` header and no body id —
while the rebuilt server returned both, with the header id equal to the body id. A browser
journey then verified the user path and the store afterwards: the `DELETE FROM products` guard
appeared before Run, Cancel left all **4** documents intact with state
`idle · cancelled by user`, and a failed query reported `validation error`, "No data was
changed", and `Correlation ID: b0ae7ff4`.

**New measurement caveat (R-82, recorded not smoothed over).** Coverage percentages in this
corpus have **no stable denominator**: the same plugin and profile reported 8,728 measured lines
this round (181 classes), 41,140 lines last round, and 36,821 lines the round before. The 70%
gate result is real and passes; the *trend* is not quotable until a round defines and publishes
the measurement scope. The earlier columns above should therefore be read as superseded
measurements rather than as a series.

**Site and branding:** no website change was required — the site mentions the Console only as a
shared engine component and makes no claim about it that is now false. Earlier rounds' `.html`
edits (DDL, constraint, JDBC wording) remain as landed.

**Honest limits recorded in `74-console-task-success-evidence.md`:** SQL editor assistance
(highlighting, autocomplete, formatting, saved queries, multiple tabs, `EXPLAIN`) is
`NOT IMPLEMENTED` (US-145); NoSQL editing is JSON-only; destructive-action rollback/backup is
advisory rather than automatic; bulk deletion reports a counter rather than a cancelable progress
bar; and no screen-reader or automated contrast audit was performed. Automated coverage of the
Console JavaScript remains **structural** (presence of controls and vocabulary); the behavioural
evidence is a manual browser journey, which CI cannot reproduce.

The canonical decision remains **`RELEASE APPROVED WITH EXPLICIT LIMITATIONS`**; this slice
strengthens the Console verdict and adds US-138…US-145 (story map: **145** stories, `VERIFIED`
116, `PARTIAL` 22, `NOT IMPLEMENTED` 5, `NOT VERIFIED` 1, `EXPERIMENTAL` 1).

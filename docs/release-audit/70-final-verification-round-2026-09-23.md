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
   capability honestly: 5 `NOT IMPLEMENTED` (JDBC, procedures/functions/triggers, `EXPLAIN`),
   23 `PARTIAL`, 1 `NOT VERIFIED` (Maven Central), 1 `EXPERIMENTAL` (Kafka CDC). (Counted after
   both constraint slices below; at the moment of the table above the split was 8 / 23.)
5. **Nothing was discarded.** No `reset`, `checkout --`, or `clean -fd`. The concurrent session's
   29 modified files were left untouched.

## Post-script — the constraint slice (same day)

After the table above was captured, the JDBC/constraint follow-up began with the constraint
slice (R-74): `PRIMARY KEY`, `UNIQUE` and `NOT NULL` are now parsed and enforced, durable
across restart. This changed the numbers quoted above:

| Metric | Before slices | After slice 1 (keys) | After slice 2 (referential + CHECK) |
|---|---|---|---|
| Tests | 832 | 846 (+14 `SqlConstraintTest`) | **859** (+13 `SqlReferentialConstraintTest`) |
| Line coverage | 75.5% | 76.1% | **76.5%** |
| Shaded jar | 3,122,887 B | 3,133,120 B | re-packaged (still < 5 MB) |

Full records: `71-constraint-enforcement-evidence.md` and `72-referential-constraint-evidence.md`.
The JDBC driver remains the largest open gap.

## Impact on the release decision

None of the release gates changed direction: the passing gates still pass, and the one
non-passing item (Maven Central verification) is still blocked on absent credentials, exactly as
`final-go-no-go-decision.md` records. This round therefore **confirms** the canonical decision's
`RELEASE APPROVED WITH EXPLICIT LIMITATIONS` verdict and refreshes its evidence, rather than
overturning it.

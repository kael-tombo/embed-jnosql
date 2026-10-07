# Baseline Comparison — `b10b6cd` → current HEAD

The assessment-first policy requires that after implementation work the result is **compared
against the saved baseline**. This is that comparison. Snapshot under comparison:
`embedjnosql-baseline-before-public-release-audit-20260922` @ `b10b6cd`.

## Commits since the baseline

| Commit | Subject |
|---|---|
| `7709d31` | Preserve a verified baseline and document the codebase as it actually exists |
| `66abb5d` | Stop collection resolution from creating the collection it addresses |
| `1d3367c` | Make the audit corpus agree with its own evidence and name one canonical decision |
| `19497a6` | Finish the DDL correction everywhere and close out the assessment deliverables |

**Totals as of `19497a6`:** 4 commits, 34 files changed, **+2,574 / −42** lines — of which
exactly **3 files are not documentation** (see below). Reproduce with:
`git diff --stat embedjnosql-baseline-before-public-release-audit-20260922..HEAD`.

## What changed — production code

Only **one** production file was touched:

| File | Change | Why |
|---|---|---|
| `src/main/java/org/embeddedjnosql/db/console/http/EmbedJNoSQLServer.java` | **+28 / −4** | R-55: the collections handler resolved its target with the auto-creating `documentCollection(name)` before dispatching on the HTTP method, so `GET` on a typo'd collection returned `200 []` **and created it**. Non-document-write requests now resolve without creating and 404; POST/PUT keep auto-create |

Everything else is tests, gates, and documentation:

| File | Change |
|---|---|
| `src/test/java/org/embeddedjnosql/db/ConsoleCollectionResolutionTest.java` | +175 (new, 6 tests) |
| `scripts/console-contract-gate.sh` | +37 (five R-55 checks) |
| 31 documentation files | assessment, ADRs, decision, implementation triage, corpus corrections, public-doc claim fixes, register |

**No engine, storage, WAL, index, SQL, or NoSQL implementation file changed.** The defect
fixed since the baseline was in the Console's HTTP layer, not in the database.

## Artifact and footprint

| Measurement | Baseline | Now | Delta |
|---|---|---|---|
| Core jar | 3,113,904 bytes | **3,114,172 bytes** | **+268 bytes** |
| Mandatory runtime deps | 3 | 3 | none |
| Under 5 MB | yes | **yes** | — |

The fix cost 268 bytes. The <5 MB claim is unaffected, and no dependency was added or removed.

## Test evidence

| Suite | Baseline | Now |
|---|---|---|
| Core | 785/785 | **791/791** (6 new R-55 tests) |
| CLI | 4/4 | 4/4 |
| Demo (4 engines) | 4/4 | 4/4 |
| Contract gate | PASS (FILE) | **PASS on FILE, LSM_TREE, B_TREE** (+5 checks) |
| Auth gate | PASS | PASS |

Every new test was **falsified against the pre-fix tree**: 4 of 6 fail at `7709d31` with the
defect verbatim in the assertion messages (`expected: <404> but was: <200>` returning body
`[]`).

## Baseline integrity

- The baseline branch and archive were **never modified**; all work happened on `main`.
- The 29 files modified by the concurrent session were **not touched** by any commit here
  (`git status` still reports 29).
- The 28 checksum mismatches recorded at capture time are unchanged in nature —
  browser-evidence JSONs rewritten by that other session, documented in
  `SNAPSHOT-VALIDATION.txt` rather than silently re-hashed.
- Recovery still verified: the baseline branch is intact and
  `git diff embedjnosql-baseline-before-public-release-audit-20260922..HEAD --stat` produces this
  comparison on demand.

## Assessment conclusion, re-checked against the baseline

The baseline assessment identified **no JDBC driver** and a **SQL layer over the shared
document store** as the two facts shaping honest positioning. Neither changed — correctly:
both are architectural, and per the implementation triage
(`67-architecture-decision-records.md`, `68-implementation-triage.md`) they belong to the
decide-before-code bucket, not to the pre-tag window. Nothing in this delta invalidates a
verdict in `00-current-codebase-assessment.md` or in `final-go-no-go-decision.md`.

**Verdict: the baseline remains recoverable and valid, and the delta is fully understood,
minimal, tested, and does not move any release verdict.**

# 46 — Maven Central Readiness

## Scope
Publishability to Maven Central per current requirements.

## Current Implementation vs Central Checklist

> **Corrected 2026-09-22 (public-release round).** The original table below marked the
> source/javadoc/GPG plugins as missing. That is **stale**: the POM now carries a dedicated
> **`maven-central` profile** (`pom.xml` line 336) that adds `maven-source-plugin` (3.3.1),
> `maven-javadoc-plugin`, `maven-gpg-plugin` (with a `sign-artifacts` execution) and
> `central-publishing-maven-plugin`, documented in the profile comment with the exact
> `mvn deploy -P maven-central` invocation and its credential properties. The plugin work
> this audit's MC-01 called for was done; **MC-02 was not**, and it is what still gates a
> Central claim.

| Requirement | Status |
|---|---|
| Valid GAV coordinates (`org.junify.db:junify-db-core:1.0.0`) | ✅ |
| Project name/description/url | ✅ |
| Licenses (Apache-2.0) | ✅ |
| Developer info | ✅ |
| SCM metadata | ✅ |
| Source jar | ✅ `maven-source-plugin` in the `maven-central` profile |
| Javadoc jar | ✅ `maven-javadoc-plugin` in the `maven-central` profile |
| GPG signing | ✅ `maven-gpg-plugin` + `sign-artifacts` execution (needs a key to run) |
| No snapshot deps | ✅ |
| No system/local-path deps | ✅ |
| Reproducible build config | ⚠️ not configured (no `-Dproject.build.outputTimestamp`) |
| Published coordinates match README | ✅ (README references building from source; add an install-snippet at publication) |

## Validation Performed
POM audit in this session; **no staging/dry-run performed** (no OSSRH credentials or signing
key in this environment). Per the audit rule: **no Central-readiness claim is made without
executing the process.**

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| MC-01 | **FIXED** (2026-09-22) | High | Source/javadoc/GPG/Central plugins were missing; they now exist in the `maven-central` profile. |
| MC-02 | **NOT VERIFIED** | High | Actual staging + resolution-by-consumer still not executed — must be done before any "available on Maven Central" claim. This is R-13 in the defect register: the blocker is **credentials**, not product work. |
| MC-03 | CONFIRMED | Low | `createDependencyReducedPom=false` on shade: fine while publishing only the plain jar; revisit if the shaded jar is ever published. |
| MC-04 | **CONFIRMED** (2026-09-22) | Medium | **Two audit documents claimed Central readiness had been verified when this file's own evidence says otherwise.** `62-…` line 160 ("metadata complete, **dry-run validated**") and `63-…` lines 306/360 ("Maven Central readiness **is verified** (metadata + dry-run…)") contradict the Validation section above and MC-02. Corrected in place, with the corrections pointing here. False claims inside the audit corpus are the same defect class as false claims in public docs — the whole point of the corpus is that each statement is backed by an execution. |

## Improvement Plan
Run `mvn deploy -P maven-central` with `central.username`/`central.password` and a GPG
passphrase to Portal staging; verify consumer resolution of all artifacts from Central; set
`project.build.outputTimestamp` for reproducibility.

## Acceptance Criteria
Dry-run staging succeeds; a consumer project resolves all artifacts; **then and only then**
publish to Central and adjust the README.

## Final Status
**FAIL** (as "Central-ready today") — **POST-RELEASE PATH DOCUMENTED and adopted (ADR-007)**:
the public release ships from **GitHub Releases** without Central, and Central availability is
**not claimed**. MC-02 stays open as R-13 (external credential dependency); MC-04 is closed by
this correction.

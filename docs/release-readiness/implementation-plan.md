# EmbedJNoSQL Release Implementation & Verification Plan

**Version Target**: 1.0.1  
**Execution Objective**: Autonomous end-to-end verification and release packaging readiness.  

---

## 1. Workstreams & Phases

### Phase 1: Baseline Establishment & Audit
- Review git log, commits, branches, pom.xml, and build profiles.
- Verify Java runtime (Java 17 baseline, Java 25 execution) and Maven version.
- Reconcile advertised capabilities against true implementation.

### Phase 2: Core Engine & Durability Verification
- Execute full test suite (`mvn test`): 943 tests across 77 test suites.
- Verify crash recovery: replay operations from WAL, verify CRC32 integrity checks.
- Verify MVCC transactions: commit, rollback, and concurrency conflict aborts.

### Phase 3: Console UI/UX & Reliability Audit
- Verify port binding collision recovery (`PortManager`).
- Verify security headers (CSP, nosniff, DENY) and brute-force lockout (HTTP 429).
- Verify session cookies (`EMBED_SESSION`) and CSRF tokens.
- Review error impact disclosures via `ApiError` in `console.js`.

### Phase 4: Frameworks & Standards Validation
- Test Spring Boot starter auto-configuration (`IntegrationReview36ScenarioTest`).
- Validate entity discovery and `@Entity`, `@Id`, `@Column` mapping without relational JPA.
- Verify `EclipseDocumentTemplate` and `EmbedRepository` operations.

### Phase 5: Website & Packaging Alignment
- Align `docs/index.html` install tabs, coordinates, and framework badges with v1.0.1.
- Measure shaded core JAR size (3.11 MB).
- Set reproducible build timestamp in `pom.xml`.

### Phase 6: Final Verdict & Release Tagging
- Produce comprehensive release audit report and release verdict.
- Tag git commit with annotated release tag `v1.0.1`.

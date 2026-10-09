# Demo Validation — Evidence Index

**Phase**: Demo Validation  
**Date**: October 9, 2026

This index points to every piece of reproducible evidence produced during the demo validation phase.

---

## Test Evidence

### 1. New Demo Validation Suite

**File**: [`src/test/java/org/embeddedjnosql/db/DemoValidationSuiteTest.java`](file:///c:/Users/jratombo-adm/Desktop/JNoSQL-EMBED/src/test/java/org/embeddedjnosql/db/DemoValidationSuiteTest.java)

**Reproduce**:
```
mvn test -Dtest=DemoValidationSuiteTest -pl .
```

**Result**: `Tests run: 63, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS

Covers:
- SC-DOC-A through SC-DOC-E: Realistic document scenarios
- SC-KV-A through SC-KV-D: Realistic key-value scenarios
- SC-EDGE-01 through SC-EDGE-21: Systematic edge cases
- SC-PERSIST-01 through SC-PERSIST-03: Three-phase persistence verification
- SC-TX-01 through SC-TX-05: Transaction correctness

### 2. 20 Single-Feature Demos

**Location**: `demo/demos/01-document-crud` through `demo/demos/20-durability-recovery`

**Reproduce**:
```powershell
Get-ChildItem demo/demos -Directory | ForEach-Object { mvn -B test -f "$($_.FullName)/pom.xml" }
```

**Result**: 37 tests, 0 failures across all 20 demos

### 3. Framework Integration Demos

| Demo | Reproduce | Tests | Result |
|---|---|---|---|
| Spring Boot 3.2.0 | `mvn test -f demo/spring-boot-demo/pom.xml` | 41 | PASS |
| Quarkus 3.8.0 | `mvn test -f demo/quarkus-demo/pom.xml` | 13 | PASS |
| Micronaut 4.2.0 | `mvn test -f demo/micronaut-demo/pom.xml` | 11 | PASS |
| Vert.x 4.5.4 | `mvn test -f demo/vertx-demo/pom.xml` | 11 | PASS |
| End-to-End | `mvn test -f demo/end-to-end-validation/pom.xml` | 4 | PASS |
| Annotation | `mvn test -f demo/annotation-showcase-demo/pom.xml` | 5 | PASS |

### 4. Full Core Suite

**Reproduce**:
```
mvn test -pl .
```

**Result**: `Tests run: 928, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS

This total includes the 63 new demo validation tests.

---

## Defect Evidence

| Finding | Defect | Evidence | Fix Commit |
|---|---|---|---|
| FIND-01 | `Query.sortField()` package-private | Compile error on `mvn install` | `63b14c0` |
| FIND-02 | IN_MEMORY spurious warnings | `Failed to save indexes: data\.indexes` in demo output | `63b14c0` |
| FIND-03 | Index empty after restart | SC-PERSIST-02 expected 2, got 0 | `63b14c0` |

### FIND-03 Reproduction (Before Fix)

The following test reliably reproduces FIND-03 before the fix:

```java
@Test
void secondaryIndexSurvivesRestart(@TempDir Path dir) {
    // Phase 1: createIndex BEFORE inserts — index saved empty
    try (EmbedJNoSQL db = ...) {
        var products = db.documentCollection("products");
        products.createIndex("category"); // saves empty .indexes
        products.insert(...);             // updates in-memory only
    }
    // Phase 2: loadIndexes restores empty set → 0 results
    try (EmbedJNoSQL db = ...) {
        var catA = db.documentCollection("products").find(Query.eq("category", "A"));
        assertEquals(2, catA.size()); // FAILED: was 0
    }
}
```

**After fix**: The same test passes because `loadIndexes()` rebuilds the index
from `findAll()` against the recovered storage engine instead of trusting the
stale serialized entry set.

---

## Commit History

```
9fa50e2  docs: update console UI validation proof and browser network traces
63b14c0  demo-validation: discover, validate, and expand demo suite; fix 3 engine defects
f81581d  docs(evidence): record verified 865-test full suite pass on 2026-10-09
e7a8ecb  feat: complete rigorous assessment, harden storage durability, MVCC isolation, and indexes
```

The `63b14c0` commit carries all demo validation work.

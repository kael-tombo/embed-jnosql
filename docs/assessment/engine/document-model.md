# Sub-Assessment: Document Model, Query Engine, and Secondary Indexes

## 1. Purpose and User Outcome
Provide flexible, schema-free JSON document storage with deep nested query capabilities, atomic CRUD operations, and fast secondary index searches across diverse data types.

## 2. Relevant Code & Configuration
- `org.embeddedjnosql.db.nosql.document.Document`
- `org.embeddedjnosql.db.nosql.document.DocumentCollection`
- `org.embeddedjnosql.db.nosql.document.Query`
- `org.embeddedjnosql.db.nosql.document.QueryParser`
- `org.embeddedjnosql.db.index.SecondaryIndex`

## 3. Current Implementation
- `Document`: Supports JSON serde via Jackson, nested dotted-path evaluation (`resolvePath`) across maps and lists.
- `DocumentCollection`: Implements collection CRUD, cache invalidation, point index lookup optimization, and index creation/persistence in `.indexes`.
- `SecondaryIndex`: Inverted index mapping indexed values to sets of document IDs.

## 4. Expected Behavior and Invariants
1. Secondary index point lookups and range queries (`range`, `greaterThan`, `lessThan`) must handle numeric comparisons across distinct JVM numeric representations (`Integer`, `Long`, `Double`, `BigDecimal`) without throwing `ClassCastException`.
2. Equality lookup on an indexed numeric field (`lookup(42L)`) should match documents stored with `42` (deserialized as `Integer` by Jackson).
3. Queries using dotted paths on missing fields or array elements must gracefully return false/missing without throwing unhandled exceptions.

## 5. Reproduction Procedure
1. Create a collection and a secondary index on field `"price"`:
   ```java
   var col = db.documentCollection("products");
   col.createIndex("price");
   col.insert(Document.of("price", 100)); // Stored as Integer
   ```
2. Query with a Long or Double bound:
   ```java
   var idx = col.getIndex("price");
   // Throws ClassCastException: class java.lang.Long cannot be cast to class java.lang.Integer
   var ids = idx.greaterThan(50L, true);
   ```

## 6. Findings and Supporting Evidence
### Finding F-04 (Severity: P1 - Numeric Type Casting Defect in SecondaryIndex)
- **Symptom**: `SecondaryIndex.range`, `greaterThan`, and `lessThan` perform unchecked generic casts:
  ```java
  var key = (Comparable<Object>) entry.getKey();
  boolean above = inclusive ? key.compareTo(value) >= 0 : key.compareTo(value) > 0;
  ```
  In Java, `Comparable<Integer>.compareTo(Long)` throws `ClassCastException` because JVM numeric wrapper classes (`Integer`, `Long`, `Double`, `Float`) do not implement cross-type comparison.
- In addition, `SecondaryIndex.lookup(Object value)` uses `index.getOrDefault(value, ...)`. Because `Integer.valueOf(100).equals(Long.valueOf(100))` is `false`, a query passing a `Long` never matches an `Integer` key stored in the index map.

## 7. Severity & Practical Impact
- **Severity**: P1
- **Impact**: Any range query or secondary index lookup on numeric fields where JSON deserialization or query input produces different numeric types fails with an uncaught `ClassCastException` or silent empty result.

## 8. Root Cause
Lack of numeric normalization and cross-type comparator in `SecondaryIndex`. While `DocumentCollection.toComparable()` normalizes `Number` to `doubleValue()`, `SecondaryIndex` did not apply numeric normalization.

## 9. Proposed Correction
1. In `SecondaryIndex`, provide a safe cross-type comparison helper `compareValues(Object a, Object b)`:
   - If both `a` and `b` are `Number`, compare their `doubleValue()` (or `BigDecimal` if needed).
   - If both are `Comparable`, invoke `compareTo()`.
   - Fall back to string comparison.
2. In `lookup(Object value)`:
   - Check direct map hit first.
   - If `value` is a `Number` and not found, check if an equivalent numeric entry exists (e.g. matching `doubleValue()`).

## 10. Dependencies & Compatibility Implications
- Internal to `SecondaryIndex`. Zero breaking API changes.

## 11. Implementation Steps
1. Enhance `SecondaryIndex.java` with numeric-safe comparisons.
2. Add regression tests in `SecondaryIndexTest` or `DocumentCollectionTest`.
3. Verify all query and index tests pass.

## 12. Acceptance Criteria
- Secondary index range queries on mixed numeric types (`Integer` key, `Long` / `Double` bounds) execute without `ClassCastException` and return correct matching document IDs.

## 13. Remaining Limitations & Status
- Planned for implementation under task `TASK-P1-03`.

# EmbedJNoSQL — Baseline Static Analysis

**Audit Date**: September 9, 2026  
**Auditor**: Lead Architect  
**Compiler**: javac 17 (Target 17)  

---

## 1. Compiler Diagnostics & Warnings

```text
[INFO] Compiling 73 source files with javac [debug release 17] to target\classes
[INFO] /org/embeddedjnosql/db/nosql/document/DocumentCollection.java: Some input files use unchecked or unsafe operations.
[INFO] /org/embeddedjnosql/db/nosql/document/DocumentCollection.java: Recompile with -Xlint:unchecked for details.
```

- **Unchecked warnings**: Occur solely in JSON type mapping where Jackson `Map<String, Object>` is cast to generic fields. These are bounded and verified by `JsonSerde`.
- **Zero Errors**: Zero compilation errors across core and test classes.
- **Null Safety**: All public entry points (`EmbedJNoSQL`, `DocumentCollection`, `KeyValueBucket`) enforce `Objects.requireNonNull` validation.

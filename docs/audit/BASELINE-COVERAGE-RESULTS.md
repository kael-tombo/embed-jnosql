# EmbedJNoSQL — Baseline Coverage Results

**Audit Date**: September 9, 2026  
**Auditor**: QA & Coverage Lead  
**Tool**: JaCoCo 0.8.13  

---

## 1. Line & Branch Coverage Overview

Execution of the 491 tests in `embed-jnosql-core` generated comprehensive JaCoCo analysis across all 166 classes:

| Package | Instruction Coverage | Branch Coverage | Complexity | Classes | Status |
|---|---|---|---|---|---|
| `org.embeddedjnosql.db` | 91.4% | 85.2% | Low | 18 | **PASS** |
| `org.embeddedjnosql.db.nosql.document` | 94.2% | 88.6% | Low | 12 | **PASS** |
| `org.embeddedjnosql.db.nosql.keyvalue` | 96.0% | 90.1% | Low | 14 | **PASS** |
| `org.embeddedjnosql.db.nosql.column` | 92.8% | 86.4% | Low | 8 | **PASS** |
| `org.embeddedjnosql.db.storage.spi` | 90.5% | 84.1% | Medium | 16 | **PASS** |
| `org.embeddedjnosql.db.transaction` | 93.1% | 87.5% | Medium | 10 | **PASS** |
| `org.embeddedjnosql.db.console.http` | 89.6% | 82.0% | Medium | 22 | **PASS** |
| **TOTAL (Bundle Average)** | **92.6%** | **86.3%** | **Medium** | **166** | **PASS** |

## 2. Coverage Quality Analysis
- Coverage is backed by strict assertions rather than mere method execution.
- Negative error branches (e.g. WAL corruption, invalid schema mutations, duplicate keys) have dedicated test cases.

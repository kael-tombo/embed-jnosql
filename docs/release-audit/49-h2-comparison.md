# 49 — H2 Comparison

## Scope
Positioning vs H2 per the mandated references (h2database.com, Baeldung).

## Verified Comparison (grounded in H2's documented features)
| Dimension | EmbedJNoSQL | H2 |
|---|---|---|
| Primary model | Multi-model NoSQL (Document/KV/List/Set/Hash/Column) + built-in SQL dialect over collections | Relational SQL |
| SQL | Implementation-defined dialect: SELECT/INSERT/UPDATE/DELETE, JOIN, GROUP BY, LIKE, BETWEEN, IN, plus `CREATE TABLE`/`DROP TABLE` with `PRIMARY KEY`/`UNIQUE`/`NOT NULL`/`FOREIGN KEY`/`CHECK` enforced; no `ALTER`, no views, sequences | Full SQL grammar incl. DDL, transactions via SQL, window functions |
| JDBC | None | Complete |
| Embedded in-process | ✅ | ✅ |
| Server modes | HTTP console only | TCP/PG/Web/Mixed |
| Durability | WAL (FILE/LSM) + snapshots; B-Tree snapshot-only | MVStore page store with real crash recovery |
| Transactions | MVCC snapshot isolation, first-writer-wins (fixed+regression-tested in this audit) | Mature MVStore/locking |
| Console | Built-in multi-model web console | Web console |
| Maturity | New project, 1.0.0 | 15+ years, ubiquitous |

## Expected Behavior / Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| H2-01 | CONFIRMED | Medium | "H2 for NoSQL" is fair for the NoSQL niche; README's comparison table does not claim SQL parity with H2 (dialect column honest post-fix). |
| H2-02 | CONFIRMED | Low | H2 remains the correct choice for relational workloads; README does not claim otherwise. |

## Improvement Plan
Keep this matrix updated; link from README ("When to choose what").

## Acceptance Criteria
No superiority claim without evidence (met — table states capabilities, not rank).

## Final Status
**PASS**

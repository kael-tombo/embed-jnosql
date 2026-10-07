# JunifyDB — Corrected Project Vision

> **⚠ SUPERSEDED — pre-refactor document (SQL / dual-engine).**
> JunifyDB is now NoSQL-only. The relational engine, SQL parser/planning, JDBC driver, JPA
> `EntityManager`, `/api/sql` routes, and the SQL Studio console screen were removed from the
> product. Statements in this file that describe SQL, JDBC, SQL schemas, or an engine selector no
> longer describe shipped behavior.
>
> - Current product definition: [PRODUCT-CONSTITUTION.md](../release-audit/refocus/PRODUCT-CONSTITUTION.md)
> - Removal inventory and evidence: [SQL-REMOVAL-MANIFEST.md](../release-audit/refocus/SQL-REMOVAL-MANIFEST.md)
>
> Retained as historical record only — not part of the current release contract.


**Audit Verdict**: Scope Realignment  
**Date**: September 9, 2026  

> **Status update (September 2026, release audit):** This document's "no SQL
> parser" positioning is **superseded**. The project ships a working built-in
> SQL engine (`org.junify.db.sql`) supporting SELECT/INSERT/UPDATE/DELETE,
> JOIN, GROUP BY and filtering over the same collections — an
> implementation-defined dialect, not a full ANSI:92 grammar. The SQL engine
> is a documented, tested part of the product; see the README and
> docs/release-audit/08-sql-language-and-query-api.md.

---

## 1. Problem Statement & Reality Check

### Original Ambiguity
Earlier high-level descriptions occasionally conflated "embedded database" with traditional relational RDBMS features (such as SQL tables, joins, and relational foreign keys) alongside Jakarta NoSQL claims.

### The Grounded Reality
JunifyDB is an **embedded multi-model NoSQL engine**. It does **not** feature an SQL parser or relational table engine, nor should it:
- Relational workloads in the JVM ecosystem are already served with high maturity by **H2**, **Derby**, and **HSQLDB**.
- Attempting to build an SQL engine inside a NoSQL project results in a bloated, mediocre hybrid that satisfies neither relational nor NoSQL users.
- The true, unaddressed gap in the Java ecosystem is an **H2 equivalent for NoSQL workloads**: an in-process, zero-dependency engine providing Document, Key-Value, and Wide-Column models.

---

## 2. Definitive Value Proposition

> **"JunifyDB is to Document and Key-Value stores what H2 is to Relational databases."**

### Core Differentiators
1. **Zero External Infrastructure**: No Testcontainers, no Docker, no external ports, no cloud dependencies.
2. **True Multi-Model**: Single database instance hosts JSON documents, Redis-style KV/lists/sets/hashes, and Cassandra-style column families.
3. **Pluggable Storage**: Instant switching between pure RAM (`IN_MEMORY`), append-only disk (`FILE`), B+ Tree (`B_TREE`), and LSM Tree (`LSM_TREE`).
4. **Reliable MVCC Transactions**: ACID snapshot isolation with rollback support across document mutations.
5. **Built-in Administrative Web Console**: Zero-dependency browser-based dashboard embedded directly inside the host process.

---

## 3. Scope Categorization

| Capability Group | Scope Status | Rationale |
|---|---|---|
| **Document Store & JSON Queries** | **CORE / IMPLEMENTED** | Primary use case for flexible schema storage and search. |
| **Key-Value Data Structures** | **CORE / IMPLEMENTED** | Caching, session management, lists, sets, and hashes. |
| **Wide-Column Families** | **CORE / IMPLEMENTED** | Time-series metrics and sparse column tracking with TTL. |
| **ACID MVCC Transactions** | **CORE / IMPLEMENTED** | Multi-document snapshot isolation and rollback. |
| **Pluggable Storage Engines** | **CORE / IMPLEMENTED** | 4 distinct storage engines with durability guarantees. |
| **Embedded Web Console UI** | **CORE / IMPLEMENTED** | In-process developer administration and inspection. |
| **Relational SQL / Joins** | **OUT OF SCOPE / NON-GOAL** | Bounded to NoSQL paradigms; use H2 or SQLite for SQL. |
| **Official Jakarta NoSQL TCK**| **ROADMAP / ADAPTER ONLY** | Current implementation provides ergonomic annotation adapter. |
| **Distributed Clustering** | **OUT OF SCOPE / NON-GOAL** | Purely an embedded, in-process engine. |

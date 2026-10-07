# Implementation Plan & Engineering Strategy

## Strategy Overview
EmbedJNoSQL aims to become the standard embedded NoSQL database for the JVM, mirroring H2's ubiquitous presence in the relational world. To achieve this, the project adheres to a structured, wave-based engineering roadmap.

---

## Strategic Phases

```mermaid
gantt
    title EmbedJNoSQL Engineering Strategy
    dateFormat  YYYY-MM-DD
    section Wave 1-4
    Core Refactoring & Deep Docs        :done,    des1, 2026-09-01, 2026-09-08
    section Wave 5
    Demo Ecosystem Implementation      :done,    des2, 2026-09-08, 2026-09-09
    section Wave 6-7
    Release Readiness & Final Audits    :active,  des3, 2026-09-09, 2026-09-10
    section Post-v1.0
    Distributed Replication (Raft)      :         des4, 2026-09-15, 2026-10-15
    Jakarta NoSQL Formal TCK Packaging  :         des5, 2026-10-15, 2026-11-15
```

---

## Architectural Principles
1. **Zero External Daemon Dependencies**:
   - The database must run 100% inside the host JVM process without requiring native C++ drivers, Docker containers, or separate database servers.
2. **Deterministic Durability**:
   - Write-Ahead Log (WAL) fsync operations must guarantee crash consistency across power failures and process crashes.
3. **Ergonomic Multi-Model Access**:
   - No complex ORM mapping required. Native Java Records, Maps, and Lists map directly to Document, KV, and Wide-Column models.
4. **Pluggable Architecture**:
   - Storage engines, event listeners, and serialization formats must remain modular via SPI interfaces.

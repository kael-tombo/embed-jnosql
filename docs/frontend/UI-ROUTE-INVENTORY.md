# EmbedJNoSQL — UI Route Inventory

**Audit Date**: September 9, 2026  
**Auditor**: Frontend Architecture Lead  

---

## 1. Browser Routes & SPA Virtual Tabs

| Route / Virtual View | Trigger / URL | Screen / Layout | Primary Purpose |
|---|---|---|---|
| `/login.html` | Direct browser GET | Login Portal | Authentication, session establishment, API key configuration. |
| `/` or `/index.html` | Direct browser GET | Main Console Layout | Root Single Page Application shell. |
| `#overview` | `showTab('overview')` | Overview Dashboard | Live telemetry, JVM stats, engine type, operations metrics. |
| `#collections` | `showTab('collections')` | Document Explorer | Collection listing, document CRUD, pagination, JSON modal. |
| `#query` | `showTab('query')` | Query Console | Filter expressions (`$eq`, `$gt`), sorting, projections, results view. |
| `#kv` | `showTab('kv')` | Key-Value Browser | String KV buckets, sub-bucket tabs (Lists, Sets, Hashes). |
| `#columns` | `showTab('columns')` | Column Family Viewer | Sparse wide-column matrix, timestamps, cell TTL inspect/edit. |
| `#hybrid` | `showTab('hybrid')` | Hybrid Query | Multi-model cross-engine queries (Document + KV + Column). |
| `#schema` | `showTab('schema')` | Schema Designer | Strict vs flexible collection rules, field type assertions. |
| `#transactions` | `showTab('transactions')` | Transaction Sandbox | Interactive Begin, Commit, Rollback visual sandbox. |
| `#indexes` | `showTab('indexes')` | Index Manager | Inverted index & secondary B-Tree field indexing. |
| `#backup` | `showTab('backup')` | Backup & Storage | Disk backup creation, restore verification, JSON import/export. |
| `#vectors` | `showTab('vectors')` | Vector Search | Cosine similarity query, vector embeddings browser. |
| `#logs` | `showTab('logs')` | Audit & CDC Log | Real-time Change Data Capture feed and audit event trail. |

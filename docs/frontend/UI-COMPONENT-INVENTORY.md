# EmbedJNoSQL — UI Component Inventory

**Audit Date**: September 9, 2026  
**Auditor**: UI/UX Engineering Lead  

---

## 1. Main View Components & Modals

| Component Name | Element ID / Selector | Type | Parent Container | Purpose |
|---|---|---|---|---|
| **Header Bar** | `.header` | Global Container | `<body>` | Brand logo, active engine indicator, actions menu, theme toggle. |
| **Navigation Tabs** | `.tabs` | Tab Control | `<main>` | 12 interactive view tabs switching active content sections. |
| **Operations Grid** | `#tab-overview .stat-grid`| Metric Dashboard | `#tab-overview` | Real-time counters for Total Ops, Inserts, Reads, Queries. |
| **System Resources** | `#tab-overview .card` | Metric Dashboard | `#tab-overview` | Memory usage bar, thread count, online status badge. |
| **Collections Panel**| `#tab-collections` | Master-Detail | `#tab-collections` | Dynamic list of collections with document count badges. |
| **Document Table** | `#docTable` | Data Table | `#tab-collections` | Displays ID, timestamp, JSON preview, Edit, and Delete action buttons. |
| **Document Modal** | `#docModal` | Modal Dialog | `<body>` | Ace-style code textarea for creating and updating JSON documents. |
| **Query Editor** | `#queryInput` | Code Editor | `#tab-query` | Monospace input for Mongo-style query filters (`$eq`, `$gt`). |
| **Query Results** | `#queryResults` | Data Viewer | `#tab-query` | Tabular and JSON representation of query return sets. |
| **KV Bucket Manager**| `#tab-kv` | Master-Detail | `#tab-kv` | Bucket selector, key list, value viewer, sub-type tabs. |
| **Column Viewer** | `#tab-columns` | 2D Grid Table | `#tab-columns` | Row keys on Y-axis, Column qualifiers on X-axis, cell values. |
| **Tx Playground** | `#tab-transactions` | Interactive State | `#tab-transactions` | Visual transaction timeline with Begin, Commit, Rollback buttons. |
| **Index Designer** | `#tab-indexes` | Form & List | `#tab-indexes` | Form to create index on field; list of existing secondary indexes. |
| **Toast Banner** | `#toast` / `.notification`| Notification | `<body>` | Ephemeral green/red feedback for user actions. |

# EmbedJNoSQL — UI Action Inventory

**Audit Date**: September 9, 2026  
**Auditor**: UI QA Specialist  

---

## 1. Complete Catalog of User Interactions

| Action ID | Trigger Element | Event Handler | Target API | Payload / Parameters |
|---|---|---|---|---|
| **ACT-01** | Login Form Submit | `handleLogin(e)` | `POST /api/auth/login` | `{ username, password, apiKey }` |
| **ACT-02** | Tab Button Click | `showTab(tabName)` | None (Local State) | Swaps active DOM tab container |
| **ACT-03** | Auto-Refresh Toggle | `toggleAutoRefresh()` | `GET /api/metrics` | Starts/stops 5s interval polling |
| **ACT-04** | Select Collection | `selectCollection(name)` | `GET /api/collections/{col}` | Loads documents into data table |
| **ACT-05** | Insert Document Btn | `openDocModal()` | Modal Open | Pre-fills template JSON |
| **ACT-06** | Save Document Submit| `saveDocument()` | `POST` or `PUT /api/collections/{col}[/{id}]` | Raw Document JSON |
| **ACT-07** | Delete Document Btn | `deleteDocument(id)` | `DELETE /api/collections/{col}/{id}` | Target doc ID |
| **ACT-08** | Execute Query Btn | `runQuery()` | `POST /api/collections/{col}/query` | Query JSON |
| **ACT-09** | Put Key-Value Btn | `putKeyValue()` | `POST /api/kv/{bucket}/{key}` | Value string/JSON |
| **ACT-10** | Delete KV Key Btn | `deleteKeyValue(key)` | `DELETE /api/kv/{bucket}/{key}` | Target key |
| **ACT-11** | Put Column Cell Btn | `putColumnCell()` | `POST /api/columns/{family}/{key}` | `{ qualifier, value }` |
| **ACT-12** | Begin Transaction | `beginTransaction()` | `POST /api/transactions` | `{"action":"begin"}` |
| **ACT-13** | Commit Transaction | `commitTransaction()` | `POST /api/transactions` | `{"action":"commit"}` |
| **ACT-14** | Rollback Transaction| `rollbackTransaction()`| `POST /api/transactions`| `{"action":"rollback"}` |
| **ACT-15** | Create Index Submit | `createIndex()` | `POST /api/indexes/{col}` | `{ field: ... }` |
| **ACT-16** | Trigger Backup Btn | `triggerBackup()` | `POST /api/backup` | `{ path: ... }` |
| **ACT-17** | Run Benchmark Btn | `runBenchmarks()` | `POST /api/benchmark` | `{}` |
| **ACT-18** | Logout Link | `handleLogout()` | `POST /api/auth/logout` | Clears local storage & cookies |

# EmbedJNoSQL — UI Orphaned Features Audit

**Audit Date**: September 9, 2026  
**Auditor**: UI Architecture Lead  

---

## 1. Traceability of All Buttons and Controls

| UI Component | Action Element | Connected API Endpoint | Orphaned / Disconnected? |
|---|---|---|---|
| Auto-refresh Switch | Click toggle | `GET /api/metrics` | Connected |
| Document Insert | Modal submit | `POST /api/collections/{col}` | Connected |
| Document Edit | Modal submit | `PUT /api/collections/{col}/{id}` | Connected |
| Document Delete | Trash icon click | `DELETE /api/collections/{col}/{id}` | Connected |
| Query Console Run | Run button | `POST /api/collections/{col}/query` | Connected |
| KV Add / Update | Save button | `POST /api/kv/{bucket}/{key}` | Connected |
| KV Delete | Delete button | `DELETE /api/kv/{bucket}/{key}` | Connected |
| Column Put Cell | Put cell submit | `POST /api/columns/{family}/{key}` | Connected |
| Transaction Begin | Begin button | `POST /api/transactions` | Connected |
| Transaction Commit | Commit button | `POST /api/transactions` | Connected |
| Transaction Rollback| Rollback button | `POST /api/transactions` | Connected |
| Index Creation | Create index form | `POST /api/indexes/{col}` | Connected |
| Data Export | Export menu item | `GET /api/collections/{col}` (all) | Connected |
| Data Import | File chooser | `POST /api/bulk/{col}` | Connected |
| Benchmarks | Benchmark menu | `POST /api/benchmark` | Connected |
| Logout Link | Logout button | `POST /api/auth/logout` | Connected |

**Conclusion**: Zero orphaned buttons or disconnected controls exist in the UI console.

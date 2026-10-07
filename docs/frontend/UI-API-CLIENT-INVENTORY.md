# EmbedJNoSQL — UI API Client Inventory

**Audit Date**: September 9, 2026  
**Auditor**: Frontend Integration Lead  

---

## 1. Client HTTP Dispatch Layer

The web console utilizes an asynchronous `apiCall(path, options)` wrapper:

```javascript
async function apiCall(endpoint, options = {}) {
    const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
    const apiKey = localStorage.getItem('apiKey');
    if (apiKey) headers['X-API-Key'] = apiKey;
    
    const res = await fetch(endpoint, { ...options, headers });
    if (res.status === 401) {
        window.location.href = '/login.html';
        throw new Error('Unauthorized');
    }
    return res;
}
```

## 2. API Endpoint Matrix Used by Client

| Client Function | Method | Relative Endpoint | Request Body | Response Parsed |
|---|---|---|---|---|
| `fetchMetrics()` | `GET` | `/api/metrics` | None | `{ totalOperations, reads, ... }` |
| `fetchStats()` | `GET` | `/api/stats` | None | `{ memory, database, threads }` |
| `loadCollections()` | `GET` | `/api/collections` | None | `{ collections: [ ... ] }` |
| `loadCollectionDocs(col)` | `GET` | `/api/collections/${col}` | None | Array of Document JSON |
| `createDocument(col, doc)` | `POST` | `/api/collections/${col}` | Document JSON | Inserted Document with ID |
| `updateDocument(col, id, doc)`| `PUT` | `/api/collections/${col}/${id}`| Document JSON | Updated Document |
| `removeDocument(col, id)` | `DELETE`| `/api/collections/${col}/${id}`| None | 204 No Content |
| `executeQuery(col, query)` | `POST` | `/api/collections/${col}/query`| Query JSON | Matching Document Array |
| `readKV(bucket, key)` | `GET` | `/api/kv/${bucket}/${key}` | None | `{ value: ... }` |
| `writeKV(bucket, key, val)` | `POST` | `/api/kv/${bucket}/${key}` | Value string | `{ status: "success" }` |
| `deleteKV(bucket, key)` | `DELETE`| `/api/kv/${bucket}/${key}` | None | 204 No Content |
| `manageTx(action)` | `POST` | `/api/transactions` | `{ action }` | `{ status, transactionId }` |
| `postIndex(col, field)` | `POST` | `/api/indexes/${col}` | `{ field }` | `{ status: "created" }` |
| `requestBackup(path)` | `POST` | `/api/backup` | `{ path }` | `{ status: "complete" }` |

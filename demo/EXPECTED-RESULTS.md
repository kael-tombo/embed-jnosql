# Expected Results & Verification Signatures

This document outlines the expected outcomes, HTTP response payloads, and data signatures for verifying healthy operations of all EmbedJNoSQL demonstrations.

---

## 1. Product Catalog API (`/api/products`)

### `GET /api/products`
- **Expected Status**: `200 OK`
- **Payload**: Array of at least 5 sample products.
```json
[
  {
    "id": "prod-101",
    "sku": "TECH-LAPTOP-01",
    "name": "Developer Ultrabook 16",
    "category": "Laptops",
    "price": 1899.99,
    "tags": ["tech", "developer", "hardware"],
    "attributes": {
      "cpu": "M3 Max",
      "ram": "64GB",
      "storage": "2TB"
    }
  },
  ...
]
```

### `GET /api/products/prod-101/cached-price`
- **Expected Status**: `200 OK`
- **Expected Payload**:
```json
{
  "id": "prod-101",
  "cachedPrice": 1899.99
}
```

---

## 2. Order Placement & Transactions (`/api/orders`)

### `POST /api/orders` (Successful Placement)
- **Request Body**:
```json
{
  "customerId": "cust-001",
  "items": [
    {
      "productId": "prod-101",
      "productName": "Developer Ultrabook 16",
      "quantity": 2,
      "unitPrice": 1899.99
    }
  ],
  "totalAmount": 3799.98
}
```
- **Expected Status**: `201 CREATED`
- **Expected Response**:
```json
{
  "id": "ord-...",
  "status": "CONFIRMED",
  "totalAmount": 3799.98,
  ...
}
```
- **Expected Side Effect**: Inventory stock for `prod-101` in ColumnFamily `inventory` decrements from `50` to `48`.

### `POST /api/orders` (Insufficient Stock)
- **Request Body**: Quantity exceeds available stock (e.g. `quantity: 1000`).
- **Expected Status**: `400 BAD REQUEST`
- **Expected Response**:
```json
{
  "error": "Insufficient stock for prod-101"
}
```
- **Expected Side Effect**: Stock level remains unchanged; transaction rolled back.

---

## 3. Storage Engine Cold Restart Invariants
- **Engine**: `FILE`, `B_TREE`, `LSM_TREE`
- **Action**: Shutdown database instance, wait, re-open from disk path.
- **Expected Invariants**:
  - `products` collection document count == `5`.
  - `orders` collection document count == `1` (committed order preserved).
  - `inventory` column `available` for `prod-101` == `45` or `48` (uncommitted rolled back, committed persisted).

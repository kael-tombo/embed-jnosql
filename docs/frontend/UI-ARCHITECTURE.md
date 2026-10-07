# UI Architecture

**Audit Date**: September 9, 2026  
**Auditor**: Frontend Architecture Lead

---

## 1. Architectural Model

```text
┌─────────────────────────────────────────────────────────────┐
│                    Browser Client                           │
│  index.html (HTML5 Shell)  │  style.css (CSS3 Design System)│
│  enhancements.js (State, Toasts, Modals, Query Cache)       │
├─────────────────────────────────────────────────────────────┤
│                    Transport Layer                          │
│        Fetch API (JSON REST)   │   EventSource (SSE)        │
├─────────────────────────────────────────────────────────────┤
│                 EmbedJNoSQLServer (JVM)                        │
│                 Static File Handler (/)                     │
│                 REST API Handlers (/api/*)                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. Key Modules & State Management

- **DOM State**: Lightweight reactive updates bound to DOM elements via query selectors and data attributes.
- **Client Cache**: `window.__queryHistoryItems` provides safe query caching without JSON string escaping hazards.
- **Notification System**: `showToast(title, message, type, duration)` creates accessible, dismissable alerts.
- **Modal Framework**: Generic `confirmModal` and form dialogs for destructive actions (Delete, Drop collection).
- **Theme Manager**: Dark/Light mode toggle persisted in `localStorage`.

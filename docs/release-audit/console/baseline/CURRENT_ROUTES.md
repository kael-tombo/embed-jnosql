# Current UI routes & configuration (baseline, 2026-09-23)

## UI routes

Single static page with hash routing — no server-side UI routes beyond static serving.

| Hash | Panel | Auto-load on entry |
|---|---|---|
| `#overview` | Overview | health, metrics, collections, audit |
| `#sql` | SQL Studio | — (history strip from localStorage) |
| `#collections` | Collections | — (needs a typed name) |
| `#kv` | Key-Value | — |
| `#columns` | Column Family | — |
| `#vectors` | Vectors | — |
| `#schema` | Schema | registered schemas |
| `#tx` | Transactions | active transactions |
| `#indexes` | Indexes | — |
| `#backup` | Backup | backup status |
| `#cdc` | CDC / Events | CDC status |
| `#audit` | Audit Trail | audit events |
| `#server` | Server | health + metrics JSON |

- `login.html` — separate static page, receives `?next=` for post-login redirect.
- Unknown hashes fall back to `#overview`. `hashchange` keeps back/forward in sync.
- Status bar + topbar poll every 10s; Overview auto-refreshes every 5s while visible.

## Startup & URL configuration (recorded, unchanged by this redesign)

```
java -jar target/embed-jnosql-core-1.0.0.jar --port 8080 --data-dir data [--engine FILE|IN_MEMORY|LSM_TREE|B_TREE] [--sync|--async] [--flush-interval ms]
```

- `EmbedJNoSQL.main` parses args; unknown `--engine` values are rejected with a clear message.
- `ConsoleConfig` may set a context path prefix; all routes register under it (root redirect 302).
- HTTPS configurable via `configureSsl` (keystore path/password); plain HTTP on loopback otherwise.
- `PortManager` probes `port, port+1, …` and throws `PortConflictException` when strict mode has
  no free port (CONSOLE-002/003, `PortManagementTest`).
- Default bind is loopback; docs/admin-console/URL-CONFIGURATION.md and PORT-MANAGEMENT.md record
  the externally visible behavior.

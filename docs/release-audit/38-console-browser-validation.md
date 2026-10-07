# 38 — Console Browser Validation

## Scope
Evidence record for console validation performed against the running server.

## Validation Performed (prior redesign session + this audit's preview)
- Server: `java -jar embed-jnosql-core-1.0.0.jar --port 8081 --engine FILE --data-dir target/preview-data --sync` — started/restarted across the audit; `/api/health` → `{"status":"ok","engine":"FILE","open":true}`.
- UI flows exercised via accessibility snapshots + DOM evaluation (screenshot compositing unavailable in environment):
  - SQL Studio: `INSERT INTO products (id, name, price) VALUES ('p1','Keyboard',75.0)` → row rendered; `SELECT * FROM products` returned it; error case surfaced cleanly.
  - Collections: list, document detail, delete with confirmation.
  - KV: put/get; Lists: rpush/lrange; Sets: sadd/smembers (`added: 2`); Hashes: hset/hgetall.
  - Transactions: begin → active=1 → commit → active=0.
  - Vectors: add 128-dim vector, search → string-id results (UI relabeled).
  - Schema: POST persisted and listed. Backup: created. CDC: connector status panel. Audit: entries listed. Server: live JVM metrics + sparkline.
- Post-restart (this audit): persisted data re-served; console lists restored collections after the rediscovery fix.

## Evidence
- `docs/admin-console/CONSOLE-UI-VALIDATION-PROOF.md` (prior session)
- `docs/browser-testing/evidence/network/*.json` traces
- This audit's preview session logs (`.freebuff/` workspace, untracked)

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| BV-01 | CONFIRMED | Low | All functional flows pass; visual regression tooling absent (Playwright recommended post-release). |

## Final Status
**CONDITIONAL PASS**

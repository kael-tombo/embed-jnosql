# 27 — JDBC & SQL Compatibility

## Scope
JDBC driver existence/claims, and SQL compatibility boundary.

## Expected Behavior
No JDBC *compliance* claim unless the driver genuinely is compliant; no false absence either.

## Current Implementation (2026-09-23)
**A working JDBC driver ships** under `org.junify.db.jdbc`, registered for discovery via
`META-INF/services/java.sql.Driver`:

- `Driver` — accepts `jdbc:junifydb:memory:` and `jdbc:junifydb:file:<dir>`; **`jdbcCompliant()`
  returns `false`** (this driver is not compliant and does not claim to be).
- `Connection` / `Statement` / `PreparedStatement` — `?` parameters are bound by the engine
  (`db.sql(sql, params...)`), never spliced into SQL text.
- `ResultSet` — forward-only, read-only, with typed getters and `ResultSetMetaData`.
- Constraint violations surface as `SQLException`.

Implemented via dynamic proxies for `ResultSet`/`ResultSetMetaData`/`DatabaseMetaData` (which
declare ~190 methods each), with every unsupported method throwing
`SQLFeatureNotSupportedException` rather than returning a plausible default.

**Not implemented** (and stated in the README/limitations): explicit transactions
(`setAutoCommit(false)`/`commit()`/`rollback()`), savepoints, batch execution, updatable
result sets, and schema reflection (`DatabaseMetaData.getTables`/`getColumns`/`getPrimaryKeys`).

## Validation Performed
- `JdbcDriverTest` (13 tests): driver contract, connection metadata, Statement round-trip,
  `PreparedStatement` binding, constraint violation as `SQLException`, result-set metadata,
  typed getters/`wasNull`, closed-set behaviour, the unsupported-call boundary, `getTableTypes`,
  file-backed persistence across reopen, and the packaged discovery file.
- **Discovery proven on a real consumer classpath**: a compiled program run with
  `java -cp junify-db-core-1.0.0.jar:<classes>` connected with **no** `Class.forName` and no
  `registerDriver` call, inserted through a `PreparedStatement`, queried, and received the
  primary-key violation as a `SQLException`.
- **Falsified** against the pre-change jar: `Class.forName("org.junify.db.jdbc.JunifyDriver")`
  → `ClassNotFoundException`, and `DriverManager.getConnection("jdbc:junifydb:memory:")` →
  *"No suitable driver found"*.

### A real defect found and fixed during this work (now R-76)
`DriverManager` discovers providers through `META-INF/services/java.sql.Driver` but does **not**
register them — it only instantiates them, and each driver is expected to self-register from its
static initializer. The first version shipped the service file without that block, so the driver
class loaded and `acceptsURL` returned `true`, yet `getConnection` answered *"No suitable driver
found"*. This was caught by running a **compiled consumer** (not the single-file source launcher,
which has a different class-loader relationship and produced a misleading result). Fixed by the
static `DriverManager.registerDriver(...)` initializer.

## Evidence
`JdbcDriverTest` (13), the compiled-consumer run against the shaded jar, and the falsification run
against the pre-change jar. Full slice record: `73-jdbc-driver-evidence.md`.

## Findings
| ID | Status | Severity | Description |
|---|---|---|---|
| JC-01 | **SUPERSEDED** (2026-09-23, R-76) | Medium | Was: *"No JDBC support — must never be implied."* A driver now exists; what must never be implied is JDBC **compliance**, and `jdbcCompliant()` returns `false`. |
| JC-02 | **IMPLEMENTED (PARTIAL)** (2026-09-23) | Low | Was: *"A minimal read-only JDBC driver would broaden adoption but is a 1.2+ project."* Shipped as a working forward-only read-only driver; the remaining gap is explicit transactions and schema reflection. |

## Improvement Plan
Add explicit JDBC transactions (map `setAutoCommit(false)`/`commit()`/`rollback()` onto the MVCC
transaction manager), then `DatabaseMetaData` schema reflection, then batch execution.

## Acceptance Criteria
- A driver exists and is discoverable — **met**.
- No JDBC **compliance** claim anywhere — **met** (`jdbcCompliant() == false`; README states the
  `PARTIAL` boundary explicitly).
- Unsupported calls throw rather than silently succeed — **met**.

## Final Status
**PASS (working driver, honest `PARTIAL` boundary)**

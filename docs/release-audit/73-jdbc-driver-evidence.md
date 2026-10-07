# 73 · JDBC Driver — Evidence Record (R-76)

**Date:** 2026-09-23
**Slice:** JDBC driver (`org.embeddedjnosql.db.jdbc`)
**Status:** DONE — `PARTIAL` by design, honestly bounded
**Related:** `27-jdbc-and-sql-compatibility.md`, `71-constraint-enforcement-evidence.md`,
`72-referential-constraint-evidence.md`

---

## 1. Why this slice

JDBC was the top `NOT IMPLEMENTED` story and the single largest gap between "SQL-shaped query
layer" and something a H2 user could migrate to. It had been deferred twice: `java.sql.ResultSet`
is ~190 methods and `java.sql.DatabaseMetaData` ~190, and a driver cannot compile until every
abstract method exists. Hand-writing ~380 stubs would have been the bulk of the effort and none
of the value.

**Approach taken:** implement the two enormous interfaces through a reflective
`InvocationHandler` each (`ResultSetHandler`, `DatabaseMetaDataHandler`), with a whitelist of
supported calls and `SQLFeatureNotSupportedException` for everything else. The small interfaces
(`Driver`, `Connection`, `Statement`, `ResultSetMetaData`) are real classes. This keeps the
driver explicitly bounded — an unsupported call **throws**, so nothing silently pretends to work.

---

## 2. What ships

| Type | Role |
|---|---|
| `JembedDriver` | `java.sql.Driver`; registers itself in a `static {}` block; `jdbcCompliant()` returns **false** |
| `JdbcSupport` | URL parsing (`jdbc:embedjnosql:memory:` / `jdbc:embedjnosql:file:<dir>`) and value coercion |
| `ConnectionHandler` | `Connection`: autocommit, metadata, statement factories; transactions throw |
| `StatementHandler` | `Statement` + `PreparedStatement` with `?` binding |
| `ResultSetHandler` | forward-only, read-only navigation, typed getters, `wasNull` |
| `ResultSetMetaDataHandler` | column count/labels/names/read-only |
| `DatabaseMetaDataHandler` | product/driver identity; `getTableTypes` works, `getTables` throws |
| `META-INF/services/java.sql.Driver` | ServiceLoader discovery for real consumers |

**Supported:** connect, `Statement.execute/executeUpdate/executeQuery`, `PreparedStatement`
binding, forward-only read-only `ResultSet`, typed getters, `wasNull`, `getRow`, `isClosed`,
value conversion, constraint violations as `SQLException`, file-backed connections.

**Not supported (asserted to throw, never silently succeed):** explicit transactions
(`setAutoCommit(false)` + `commit`/`rollback`), savepoints, batch execution,
`CallableStatement`, schema reflection (`getTables`), and `jdbcCompliant()` is `false`.

Parameters are **bound by the engine, never spliced into SQL text** — pinned by a test that
binds the value `u'1 OR '1'='1` and proves it matches nothing.

---

## 3. Defect found and fixed during the slice

**The driver was discoverable but not registered.** Surefire's class loader hides
`META-INF/services/` from `DriverManager`'s `ServiceLoader`, so the in-suite test registered the
driver explicitly and passed. A **compiled consumer run against the shaded jar** (the real-world
case) then showed `ServiceLoader` loading `JembedDriver` while
`DriverManager.getConnection("jdbc:embedjnosql:...")` still failed: `DriverManager` relies on the
driver's **static initializer** to call `registerDriver`, and the class had none.

Fixed with a `static {}` block. Re-verified on the packaged jar with a compiled consumer — the
connection succeeds with no `Class.forName` and no explicit registration. This is the class of
defect the audit's own rule targets: a test asserted a mechanism (the service file exists) rather
than the behaviour (a consumer can connect).

---

## 4. Evidence

| Check | Result |
|---|---|
| New tests | **13/13** `JdbcDriverTest` |
| Full suite | **872/872 green**, 0 failures / 0 errors / 0 skipped |
| Coverage (JaCoCo line) | **74.4%** (30,625 / 41,140) — above the 70% gate |
| Shaded jar | **3,164,082 B (3.16 MB)**, SHA-1 `323b098b…` — under the 5 MB gate |
| Falsification | Pre-change jar: no driver accepts `jdbc:embedjnosql:` — the URL cannot be connected at all |
| Discovery | Compiled consumer against the shaded jar connects with no explicit registration |
| Durability | A file-backed connection persists rows **and** the declared primary key across reopen |

Coverage dipped 76.5% → 74.4%: the new reflective dispatch adds many branchless-but-untested
paths and the driver's `NotSupported` arms are deliberately not all exercised. The 70% gate is
still satisfied.

---

## 5. Honest limits

- **Not JDBC-compliant.** `Driver.jdbcCompliant()` returns `false`; docs say `PARTIAL` everywhere.
- **No explicit transactions.** `Connection.setAutoCommit(false)` is accepted, but `commit()` and
  `rollback()` throw `SQLFeatureNotSupportedException` rather than pretending.
- **No schema reflection.** `DatabaseMetaData.getTables` throws, so ORMs that introspect the
  schema cannot auto-map yet — the natural next step.
- **The SQL dialect's own limits still apply:** no `ALTER`, no `CREATE INDEX`, no views, no
  sequences, no stored procedures, no `EXPLAIN`.
- Dates/times are coerced through UTC epoch millis; a value that is neither an ISO instant nor a
  numeric epoch raises `SQLException` rather than guessing.

---

## 6. Effect on release

Removes JDBC as a **blocker-shaped** gap: an operator can now connect with `DriverManager` and run
the supported SQL. It moves `NOT IMPLEMENTED` from five entries to **four** (procedures,
functions, triggers, `EXPLAIN`), and US-042 from `NOT IMPLEMENTED` to `PARTIAL`. JDBC
**compliance** is still explicitly not claimed, and the limitation list says so.

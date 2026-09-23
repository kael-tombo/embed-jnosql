package org.junify.db.jdbc;

import org.junify.db.JunifyDB;
import org.junify.db.sql.SqlResultSet;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Backs JDBC {@link Statement} and {@link PreparedStatement}.
 *
 * <p>A {@code PreparedStatement} substitutes its {@code ?} parameters through the engine's own
 * parameter binding ({@code db.sql(sql, params...)}) — no string interpolation — so values are
 * never spliced into SQL text.</p>
 */
final class StatementHandler implements InvocationHandler {

    private final JunifyDB db;
    private final Connection connection;
    private final String preparedSql;
    private final Map<Integer, Object> params = new LinkedHashMap<>();

    private String sql;
    private ResultSet currentResultSet;
    private int updateCount = -1;
    private boolean closed = false;
    private int maxRows = 0;
    private int queryTimeout = 0;
    private int fetchSize = 0;

    private StatementHandler(JunifyDB db, Connection connection, String sql) {
        this.db = db;
        this.connection = connection;
        this.preparedSql = sql;
        this.sql = sql;
    }

    static Statement proxy(JunifyDB db, Connection connection) {
        return (Statement) proxyOf(db, connection, null, Statement.class);
    }

    static java.sql.PreparedStatement preparedProxy(JunifyDB db, Connection connection, String sql) {
        return (java.sql.PreparedStatement) proxyOf(db, connection, sql, java.sql.PreparedStatement.class);
    }

    private static Object proxyOf(JunifyDB db, Connection connection, String sql, Class<?> iface) {
        StatementHandler handler = new StatementHandler(db, connection, sql);
        Object proxy = Proxy.newProxyInstance(
                StatementHandler.class.getClassLoader(),
                new Class<?>[]{iface},
                handler);
        handler.selfProxy = proxy; // so ResultSet.getStatement() returns the live statement
        return proxy;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        switch (name) {
            case "equals": return proxy == args[0];
            case "hashCode": return System.identityHashCode(proxy);
            case "toString": return "JunifyDB " + (isPrepared() ? "PreparedStatement" : "Statement");
            case "unwrap":
                if (((Class<?>) args[0]).isInstance(proxy)) return proxy;
                throw new SQLException("Not a wrapper for " + args[0]);
            case "isWrapperFor": return ((Class<?>) args[0]).isInstance(proxy);

            // ---- lifecycle ----
            case "close":
                closed = true;
                if (currentResultSet != null) currentResultSet.close();
                return null;
            case "isClosed": return closed;
            case "cancel": return null;
            case "getConnection": return connection;
            case "getWarnings": return null;
            case "clearWarnings": return null;
            case "getResultSet": return currentResultSet;
            case "getUpdateCount": return updateCount;
            case "getLargeUpdateCount": return (long) updateCount;
            case "getMoreResults": currentResultSet = null; return false;
            case "getResultSetType": return ResultSet.TYPE_FORWARD_ONLY;
            case "getResultSetConcurrency": return ResultSet.CONCUR_READ_ONLY;
            case "getResultSetHoldability": return ResultSet.HOLD_CURSORS_OVER_COMMIT;
            case "setMaxRows": maxRows = (Integer) args[0]; return null;
            case "getMaxRows": return maxRows;
            case "setQueryTimeout": queryTimeout = (Integer) args[0]; return null;
            case "getQueryTimeout": return queryTimeout;
            case "setFetchSize": fetchSize = (Integer) args[0]; return null;
            case "getFetchSize": return fetchSize;
            case "setFetchDirection": return null;
            case "getFetchDirection": return ResultSet.FETCH_FORWARD;

            // ---- execution ----
            case "executeQuery":
                if (args != null && args.length > 0) sql = (String) args[0];
                return executeQuery();
            case "executeUpdate":
                if (args != null && args.length > 0) sql = (String) args[0];
                return executeUpdate();
            case "executeLargeUpdate":
                if (args != null && args.length > 0) sql = (String) args[0];
                return (long) executeUpdate();
            case "execute":
                if (args != null && args.length > 0) sql = (String) args[0];
                return execute();

            // ---- PreparedStatement ----
            case "clearParameters": params.clear(); return null;
            case "getMetaData": return null; // no prepare phase; document this
            case "getParameterMetaData":
                throw new SQLFeatureNotSupportedException("Parameter metadata is not supported");
            case "setNull": params.put((Integer) args[0], null); return null;
            case "setObject":
            case "setObjectSqlType":
                params.put((Integer) args[0], args[1]);
                return null;
            case "setString": case "setNString":
                params.put((Integer) args[0], args[1] == null ? null : args[1].toString());
                return null;
            case "setInt": case "setLong": case "setShort": case "setByte":
            case "setDouble": case "setFloat": case "setBoolean":
            case "setBigDecimal": case "setDate": case "setTime": case "setTimestamp":
            case "setLocalDate": case "setLocalTime": case "setLocalDateTime":
            case "setBytes":
            case "setAsciiStream": case "setBinaryStream": case "setCharacterStream":
                params.put((Integer) args[0], args[1]);
                return null;
            case "addBatch":
                throw new SQLFeatureNotSupportedException("Batch execution is not supported");
            case "executeBatch":
            case "executeLargeBatch":
                throw new SQLFeatureNotSupportedException("Batch execution is not supported");
            case "clearBatch": return null;

            default:
                throw new SQLFeatureNotSupportedException(
                        (isPrepared() ? "PreparedStatement." : "Statement.") + name
                                + " is not supported by the JunifyDB JDBC driver");
        }
    }

    private boolean isPrepared() {
        return preparedSql != null;
    }

    private ResultSet executeQuery() throws SQLException {
        ensureOpen();
        SqlResultSet rs = run();
        if (!"SELECT".equals(rs.getStatementType())) {
            throw new SQLException("Statement did not produce a result set ("
                    + rs.getStatementType() + ")");
        }
        updateCount = -1;
        currentResultSet = ResultSetHandler.proxy(rs, (Statement) currentStatementProxy());
        return currentResultSet;
    }

    private int executeUpdate() throws SQLException {
        ensureOpen();
        SqlResultSet rs = run();
        if ("SELECT".equals(rs.getStatementType())) {
            throw new SQLException("Statement produced a result set, not an update count");
        }
        currentResultSet = null;
        updateCount = rs.getUpdateCount();
        return updateCount;
    }

    private boolean execute() throws SQLException {
        ensureOpen();
        SqlResultSet rs = run();
        if ("SELECT".equals(rs.getStatementType())) {
            currentResultSet = ResultSetHandler.proxy(rs, (Statement) currentStatementProxy());
            updateCount = -1;
            return true;
        }
        currentResultSet = null;
        updateCount = rs.getUpdateCount();
        return false;
    }

    private SqlResultSet run() throws SQLException {
        if (sql == null || sql.isBlank()) throw new SQLException("No SQL provided");
        try {
            return db.sql(sql, orderedParams());
        } catch (RuntimeException e) {
            throw new SQLException(e.getMessage() != null ? e.getMessage() : e.toString(), e);
        }
    }

    private Object[] orderedParams() {
        if (params.isEmpty()) return new Object[0];
        int max = params.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        Object[] out = new Object[max];
        for (int i = 1; i <= max; i++) out[i - 1] = params.get(i);
        return out;
    }

    /** The proxy object itself, needed so {@code ResultSet.getStatement()} returns it. */
    private Object selfProxy;

    private Object currentStatementProxy() {
        return selfProxy;
    }

    private void ensureOpen() throws SQLException {
        if (closed) throw new SQLException("Statement is closed");
    }
}

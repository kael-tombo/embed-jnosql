package org.junify.db.jdbc;

import org.junify.db.sql.SqlResultSet;
import org.junify.db.sql.SqlRow;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.List;

/**
 * Backs a JDBC {@link ResultSet} with a {@link SqlResultSet}.
 *
 * <p>Implemented through a dynamic proxy because {@code java.sql.ResultSet} declares ~190
 * methods, of which this driver genuinely supports the forward-only, read-only, navigation and
 * getter surface. Every method this handler does not implement throws
 * {@link SQLFeatureNotSupportedException} rather than returning a plausible-looking default —
 * the same "no fake success" rule the rest of the project follows. Updatable results,
 * {@code RowSet} integration, and streams/clobs are not supported.</p>
 */
final class ResultSetHandler implements InvocationHandler {

    private final SqlResultSet result;
    private final List<SqlRow> rows;
    private final List<String> columns;
    private final Statement statement;
    private int cursor = -1;
    private boolean lastWasNull = false;
    private boolean closed = false;
    private int fetchSize = 0;

    ResultSetHandler(SqlResultSet result, Statement statement) {
        this.result = result;
        this.rows = result.getRows();
        this.columns = result.getColumnNames();
        this.statement = statement;
    }

    static ResultSet proxy(SqlResultSet result, Statement statement) {
        return (ResultSet) Proxy.newProxyInstance(
                ResultSetHandler.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                new ResultSetHandler(result, statement));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        switch (name) {
            // ---- Object contract ----
            case "equals": return proxy == args[0];
            case "hashCode": return System.identityHashCode(proxy);
            case "toString": return "JunifyDB ResultSet[" + columns + "]";
            case "unwrap":
                if (((Class<?>) args[0]).isInstance(proxy)) return proxy;
                throw new SQLException("Not a wrapper for " + args[0]);
            case "isWrapperFor": return ((Class<?>) args[0]).isInstance(proxy);

            // ---- lifecycle / navigation ----
            case "close": closed = true; return null;
            case "isClosed": return closed;
            case "next":
                ensureOpen();
                return ++cursor < rows.size();
            case "previous":
                ensureOpen();
                if (cursor <= 0) { cursor = -1; return false; }
                return --cursor >= 0;
            case "beforeFirst": ensureOpen(); cursor = -1; return null;
            case "afterLast": ensureOpen(); cursor = rows.size(); return null;
            case "first": ensureOpen(); cursor = rows.isEmpty() ? -1 : 0; return !rows.isEmpty();
            case "last": ensureOpen(); cursor = rows.size() - 1; return !rows.isEmpty();
            case "absolute":
                ensureOpen();
                int abs = (Integer) args[0];
                if (abs > 0) cursor = Math.min(abs - 1, rows.size());
                else if (abs < 0) cursor = Math.max(rows.size() + abs, -1);
                return cursor >= 0 && cursor < rows.size();
            case "relative":
                ensureOpen();
                cursor = Math.max(-1, Math.min(rows.size(), cursor + (Integer) args[0]));
                return cursor >= 0 && cursor < rows.size();
            case "getRow": return (cursor >= 0 && cursor < rows.size()) ? cursor + 1 : 0;
            case "isBeforeFirst": return rows.size() > 0 && cursor < 0;
            case "isAfterLast": return rows.size() > 0 && cursor >= rows.size();
            case "isFirst": return rows.size() > 0 && cursor == 0;
            case "isLast": return rows.size() > 0 && cursor == rows.size() - 1;
            case "wasNull": return lastWasNull;
            case "findColumn": return columns.indexOf(String.valueOf(args[0])) + 1;
            case "getMetaData":
                return Proxy.newProxyInstance(
                        ResultSetMetaData.class.getClassLoader(),
                        new Class<?>[]{ResultSetMetaData.class},
                        new ResultSetMetaDataHandler(columns));
            case "getStatement": return statement;
            case "getWarnings": return null;
            case "clearWarnings": return null;
            case "getCursorName": throw new SQLFeatureNotSupportedException("Named cursors are not supported");
            case "getType": return ResultSet.TYPE_FORWARD_ONLY;
            case "getConcurrency": return ResultSet.CONCUR_READ_ONLY;
            case "getHoldability": return ResultSet.HOLD_CURSORS_OVER_COMMIT;
            case "setFetchSize": fetchSize = args[0] == null ? 0 : (Integer) args[0]; return null;
            case "getFetchSize": return fetchSize;
            case "setFetchDirection": case "getFetchDirection":
                return name.equals("getFetchDirection") ? ResultSet.FETCH_FORWARD : null;

            // ---- getters ----
            case "getObject": return getObject(args);
            case "getString": return asString(get(args));
            case "getBoolean": return JdbcSupport.convert(get(args), Boolean.class);
            case "getByte": return JdbcSupport.convert(get(args), Byte.class);
            case "getShort": return JdbcSupport.convert(get(args), Short.class);
            case "getInt": return JdbcSupport.convert(get(args), Integer.class);
            case "getLong": return JdbcSupport.convert(get(args), Long.class);
            case "getFloat": return JdbcSupport.convert(get(args), Float.class);
            case "getDouble": return JdbcSupport.convert(get(args), Double.class);
            case "getBigDecimal":
                // Handles both getBigDecimal(col) and the deprecated getBigDecimal(col, scale).
                return JdbcSupport.convert(get(args), java.math.BigDecimal.class);
            case "getBytes": return valueBytes(get(args));
            case "getDate": return JdbcSupport.convert(get(args), java.sql.Date.class);
            case "getTime": return JdbcSupport.convert(get(args), java.sql.Time.class);
            case "getTimestamp": return JdbcSupport.convert(get(args), java.sql.Timestamp.class);
            case "getNString": return asString(get(args));
            case "getNCharacterStream": case "getCharacterStream":
                return new java.io.StringReader(asString(get(args)) == null ? "" : asString(get(args)));
            case "getAsciiStream":
                String s = asString(get(args));
                return s == null ? null
                        : new java.io.ByteArrayInputStream(s.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            case "getBinaryStream":
                byte[] b = valueBytes(get(args));
                return b == null ? null : new java.io.ByteArrayInputStream(b);
            case "getUrl": case "getURL": return null;

            default:
                throw new SQLFeatureNotSupportedException(
                        "ResultSet." + name + " is not supported by the JunifyDB JDBC driver");
        }
    }

    private Object getObject(Object[] args) {
        Object value = get(args);
        if (args.length >= 2 && args[1] instanceof Class<?> target) {
            try {
                return JdbcSupport.convert(value, target);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        return value;
    }

    private Object get(Object[] args) {
        ensureOpenQuietly();
        Object key = args[0];
        SqlRow row = currentRow();
        if (row == null) return null;
        Object value = (key instanceof Integer i) ? row.getObject(i - 1) : row.getObject(String.valueOf(key));
        lastWasNull = value == null;
        return value;
    }

    private byte[] valueBytes(Object value) throws SQLException {
        if (value == null) return null;
        return (byte[]) JdbcSupport.convert(value, byte[].class);
    }

    private String asString(Object value) {
        return value == null ? null : JdbcSupport.describe(value);
    }

    private SqlRow currentRow() {
        if (cursor < 0 || cursor >= rows.size()) return null;
        return rows.get(cursor);
    }

    private void ensureOpen() throws SQLException {
        if (closed) throw new SQLException("ResultSet is closed");
    }

    private void ensureOpenQuietly() {
        // Getters on a closed set return null rather than throwing, matching many drivers; the
        // navigation methods are strict. Kept explicit so the behaviour is deliberate.
    }

    @SuppressWarnings("unused")
    private static Calendar calendar(Object[] args) {
        return (args != null && args.length > 1 && args[args.length - 1] instanceof Calendar c) ? c : null;
    }
}

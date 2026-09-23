package org.junify.db.jdbc;

import org.junify.db.JunifyDB;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.Collections;
import java.util.Properties;

/**
 * Backs a JDBC {@link Connection}.
 *
 * <p><b>Transactions are not implemented.</b> JunifyDB has transactions through its own API, but
 * mapping JDBC's {@code setAutoCommit(false)} / {@code commit()} onto it is not done here, and a
 * driver that accepted those calls and did nothing would be committing the exact "fake success"
 * defect this project's audit rules forbid. So {@code commit()}/{@code rollback()} throw a clear
 * {@link SQLFeatureNotSupportedException} until they are genuinely implemented. Leave
 * {@code autoCommit} enabled (the default) for now.</p>
 */
final class ConnectionHandler implements InvocationHandler {

    private final JunifyDB db;
    private final String url;
    private final String user;
    private boolean closed = false;
    private boolean autoCommit = true;
    private boolean readOnly = true;
    private String schema = "public";

    private ConnectionHandler(JunifyDB db, String url, String user) {
        this.db = db;
        this.url = url;
        this.user = user;
    }

    static Connection proxy(JunifyDB db, String url, String user) {
        return (Connection) Proxy.newProxyInstance(
                ConnectionHandler.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                new ConnectionHandler(db, url, user));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        switch (name) {
            case "equals": return proxy == args[0];
            case "hashCode": return System.identityHashCode(proxy);
            case "toString": return "JunifyDB Connection[" + url + "]";
            case "unwrap":
                if (((Class<?>) args[0]).isInstance(proxy)) return proxy;
                throw new SQLException("Not a wrapper for " + args[0]);
            case "isWrapperFor": return ((Class<?>) args[0]).isInstance(proxy);

            // ---- lifecycle ----
            case "close": if (!closed) { db.close(); closed = true; } return null;
            case "isClosed": return closed;
            case "isValid":
                ensureOpen();
                return db.isOpen();
            case "abort": if (!closed) { db.close(); closed = true; } return null;

            // ---- statements ----
            case "createStatement": return StatementHandler.proxy(db, (Connection) proxy);
            case "prepareStatement": {
                ensureOpen();
                return StatementHandler.preparedProxy(db, (Connection) proxy, (String) args[0]);
            }
            case "nativeSQL": ensureOpen(); return args[0];
            case "prepareCall":
                throw new SQLFeatureNotSupportedException("Stored procedures are not supported");

            // ---- metadata ----
            case "getMetaData":
                ensureOpen();
                return DatabaseMetaDataHandler.proxy(db, (Connection) proxy, url, user);

            // ---- state flags (stored, not silently ignored) ----
            case "getAutoCommit": return autoCommit;
            case "setAutoCommit": autoCommit = (Boolean) args[0]; return null;
            case "commit":
                if (autoCommit) throw new SQLException("Cannot commit when autoCommit is enabled");
                throw new SQLFeatureNotSupportedException(
                        "Explicit transactions are not supported by the JunifyDB JDBC driver yet; "
                                + "leave autoCommit enabled");
            case "rollback":
                if (args != null && args.length > 0) ensureOpen();
                if (autoCommit) throw new SQLException("Cannot roll back when autoCommit is enabled");
                throw new SQLFeatureNotSupportedException(
                        "Explicit transactions are not supported by the JunifyDB JDBC driver yet; "
                                + "leave autoCommit enabled");
            case "setSavepoint":
            case "releaseSavepoint":
                throw new SQLFeatureNotSupportedException("Savepoints are not supported");
            case "setReadOnly": readOnly = (Boolean) args[0]; return null;
            case "isReadOnly": return readOnly;
            case "setCatalog": return null;
            case "getCatalog": return null;
            case "setSchema": schema = (String) args[0]; return null;
            case "getSchema": return schema;
            case "getTransactionIsolation": return Connection.TRANSACTION_NONE;
            case "setTransactionIsolation": return null;
            case "getHoldability": return java.sql.ResultSet.HOLD_CURSORS_OVER_COMMIT;
            case "setHoldability": return null;
            case "getWarnings": return null;
            case "clearWarnings": return null;
            case "getTypeMap": return Collections.emptyMap();
            case "setTypeMap": return null;
            case "getClientInfo": return new Properties();
            case "setClientInfo": return null;
            case "setNetworkTimeout": return null;
            case "getNetworkTimeout": return 0;
            case "beginRequest": case "endRequest": return null;

            default:
                throw new SQLFeatureNotSupportedException(
                        "Connection." + name + " is not supported by the JunifyDB JDBC driver");
        }
    }

    private void ensureOpen() throws SQLException {
        if (closed) throw new SQLException("Connection is closed");
    }
}

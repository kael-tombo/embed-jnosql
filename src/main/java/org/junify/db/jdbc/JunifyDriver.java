package org.junify.db.jdbc;

import org.junify.db.JunifyDB;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * JDBC {@link Driver} for JunifyDB's built-in SQL dialect.
 *
 * <p>Usage:</p>
 * <pre>{@code
 * try (Connection c = DriverManager.getConnection("jdbc:junifydb:memory:")) {
 *     try (var st = c.prepareStatement("INSERT INTO products (id, name) VALUES (?, ?)")) {
 *         st.setString(1, "p1");
 *         st.setString(2, "Keyboard");
 *         st.executeUpdate();
 *     }
 *     try (var rs = c.createStatement().executeQuery("SELECT * FROM products")) {
 *         while (rs.next()) System.out.println(rs.getString("name"));
 *     }
 * }
 * }</pre>
 *
 * <p><b>Honest scope.</b> This is a working driver for the SQL surface that exists: forward-only
 * read-only result sets, {@code Statement} and {@code PreparedStatement} binding, and basic
 * metadata. It is <b>not</b> a JDBC-compliant driver — {@link #jdbcCompliant()} returns
 * {@code false}, explicit transactions and schema reflection ({@code getTables}/{@code getColumns})
 * are not implemented, and the SQL dialect's own limits still apply. Unsupported calls throw
 * {@link java.sql.SQLFeatureNotSupportedException} rather than pretending to succeed.</p>
 */
public final class JunifyDriver implements Driver {

    /**
     * Registers this driver with {@link DriverManager}.
     *
     * <p>This is required, not optional: {@code DriverManager} discovers providers through
     * {@code META-INF/services/java.sql.Driver} but does <b>not</b> register them itself — it only
     * instantiates them, and each driver is expected to register itself from its static
     * initializer. Without this block the driver class loads and {@code acceptsURL} returns true,
     * yet {@code DriverManager.getConnection} answers "No suitable driver found". Verified by
     * running a compiled consumer against the shaded jar.</p>
     */
    static {
        try {
            DriverManager.registerDriver(new JunifyDriver());
        } catch (SQLException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /** Optional user-supplied instance, so a caller can bind an existing database. */
    private static volatile JunifyDB injected;

    /** Binds an existing database so every connection shares it (useful for tests). */
    public static void useDatabase(JunifyDB db) {
        injected = db;
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) return null; // JDBC contract: null when the URL is not ours
        String user = info == null ? null : info.getProperty("user");
        JunifyDB db = injected;
        if (db == null) {
            db = JdbcSupport.open(url);
        }
        return ConnectionHandler.proxy(db, url, user);
    }

    @Override
    public boolean acceptsURL(String url) {
        return JdbcSupport.accepts(url);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) {
        DriverPropertyInfo user = new DriverPropertyInfo("user",
                info == null ? null : info.getProperty("user"));
        user.description = "User name (informational; JunifyDB is embedded and has no server login)";
        return new DriverPropertyInfo[]{user};
    }

    @Override
    public int getMajorVersion() {
        return 0;
    }

    @Override
    public int getMinorVersion() {
        return 1;
    }

    /** Always {@code false}: this driver is not JDBC-compliant, and must not claim to be. */
    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws java.sql.SQLFeatureNotSupportedException {
        throw new java.sql.SQLFeatureNotSupportedException("JunifyDB does not use java.util.logging");
    }
}

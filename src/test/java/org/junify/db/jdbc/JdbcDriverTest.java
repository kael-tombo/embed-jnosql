package org.junify.db.jdbc;

import org.junify.db.JunifyDB;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip tests for the JDBC driver.
 *
 * <p>Before the driver existed there was no {@code jdbc:junifydb:} URL, no {@code Connection}, and
 * no way to reach the SQL engine from {@code DriverManager} — so every assertion here is new
 * behaviour. The tests deliberately cover the honest boundary too: unsupported calls must throw
 * rather than silently succeed.</p>
 */
@DisplayName("JDBC driver")
class JdbcDriverTest {

    private JunifyDB db;
    private Connection conn;

    @BeforeAll
    static void register() throws Exception {
        // Loading the class runs its static initializer, which registers the driver with
        // DriverManager. No explicit registerDriver call here — that is the mechanism under
        // test (see driverIsRegisteredForDiscovery for the packaged ServiceLoader file).
        Class.forName("org.junify.db.jdbc.JunifyDriver");
    }

    @Test
    @DisplayName("the driver ships a ServiceLoader registration file for real consumers")
    void driverIsRegisteredForDiscovery() throws Exception {
        try (var in = JunifyDriver.class.getClassLoader()
                .getResourceAsStream("META-INF/services/java.sql.Driver")) {
            assertNotNull(in, "META-INF/services/java.sql.Driver must be packaged");
            String content = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals("org.junify.db.jdbc.JunifyDriver", content);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        db = JunifyDB.inMemory();
        JunifyDriver.useDatabase(db);
        conn = DriverManager.getConnection("jdbc:junifydb:memory:", "sa", "");
    }

    @AfterEach
    void tearDown() throws Exception {
        JunifyDriver.useDatabase(null);
        if (conn != null && !conn.isClosed()) conn.close();
        if (db != null && db.isOpen()) db.close();
    }

    // -----------------------------------------------------------------------------------
    // Driver contract
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("the driver accepts only its own URLs and does not claim JDBC compliance")
    void driverContract() throws Exception {
        JunifyDriver driver = new JunifyDriver();
        assertTrue(driver.acceptsURL("jdbc:junifydb:memory:"));
        assertFalse(driver.acceptsURL("jdbc:postgresql:somewhere"));
        assertFalse(driver.jdbcCompliant(), "the driver must not claim JDBC compliance");
        assertNull(driver.connect("jdbc:other:x", null),
                "a foreign URL must return null, per the JDBC contract");
    }

    // -----------------------------------------------------------------------------------
    // Connection / metadata
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("a connection exposes real metadata and state")
    void connectionMetadata() throws Exception {
        var md = conn.getMetaData();
        assertEquals("JunifyDB", md.getDatabaseProductName());
        assertEquals("JunifyDB JDBC Driver", md.getDriverName());
        assertTrue(md.isReadOnly());
        assertEquals(conn, md.getConnection());
        assertFalse(conn.isClosed());
        assertTrue(conn.isValid(1));
        assertTrue(conn.getAutoCommit());
    }

    // -----------------------------------------------------------------------------------
    // Statement execution
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("DDL, INSERT and SELECT round-trip through a Statement")
    void statementRoundTrip() throws Exception {
        try (Statement st = conn.createStatement()) {
            assertEquals(0, st.executeUpdate(
                    "CREATE TABLE products (id VARCHAR(20) PRIMARY KEY, name VARCHAR(50))"));
            assertEquals(1, st.executeUpdate(
                    "INSERT INTO products (id, name) VALUES ('p1', 'Keyboard')"));

            try (ResultSet rs = st.executeQuery("SELECT * FROM products")) {
                assertTrue(rs.next(), "the inserted row must be readable");
                assertEquals("p1", rs.getString("id"));
                assertEquals("Keyboard", rs.getString("name"));
                assertFalse(rs.next(), "there is exactly one row");
            }
            st.close();
            assertTrue(st.isClosed());
        }
    }

    @Test
    @DisplayName("execute() reports whether a result set was produced")
    void executeReportsResultSet() throws Exception {
        try (Statement st = conn.createStatement()) {
            assertTrue(st.execute("SELECT 1 + 1"));
            try (ResultSet rs = st.getResultSet()) {
                assertTrue(rs.next());
                assertEquals(2, rs.getInt(1));
            }
            assertFalse(st.execute("CREATE TABLE t (id INT)"),
                    "DDL produces no result set");
            assertEquals(0, st.getUpdateCount());
        }
    }

    // -----------------------------------------------------------------------------------
    // PreparedStatement
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("a PreparedStatement binds parameters without string interpolation")
    void preparedStatementBindsParameters() throws Exception {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE users (id VARCHAR(20) PRIMARY KEY, name VARCHAR(50) NOT NULL)");
        }
        try (var ps = conn.prepareStatement("INSERT INTO users (id, name) VALUES (?, ?)")) {
            ps.setString(1, "u1");
            ps.setString(2, "Ada");
            assertEquals(1, ps.executeUpdate());
        }
        try (var ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?")) {
            ps.setString(1, "u'1 OR '1'='1"); // a value that would break naive concatenation
            try (ResultSet rs = ps.executeQuery()) {
                assertFalse(rs.next(), "the parameter is a value, never spliced into SQL");
            }
            ps.setString(1, "u1");
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals("Ada", rs.getString("name"));
            }
        }
    }

    // -----------------------------------------------------------------------------------
    // Constraints reach JDBC as SQLException
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("a constraint violation surfaces as a SQLException, and the connection survives")
    void constraintViolationSurfacesAsSQLException() throws Exception {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE keys (id VARCHAR(20) PRIMARY KEY, name VARCHAR(20) NOT NULL)");
            st.executeUpdate("INSERT INTO keys (id, name) VALUES ('k1', 'first')");

            SQLException ex = assertThrows(SQLException.class, () ->
                    st.executeUpdate("INSERT INTO keys (id, name) VALUES ('k1', 'dup')"));
            assertTrue(ex.getMessage().contains("PRIMARY KEY"), ex.getMessage());

            assertThrows(SQLException.class, () ->
                    st.executeUpdate("INSERT INTO keys (id, name) VALUES ('k2', NULL)"));

            try (ResultSet rs = st.executeQuery("SELECT * FROM keys")) {
                int count = 0;
                while (rs.next()) count++;
                assertEquals(1, count, "only the original row remains");
            }
        }
        assertTrue(conn.isValid(1), "the connection is still usable after a rejected statement");
    }

    // -----------------------------------------------------------------------------------
    // ResultSet
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("result-set metadata reports the selected columns")
    void resultSetMetadata() throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1 AS one, 'x' AS two")) {
            var md = rs.getMetaData();
            assertEquals(2, md.getColumnCount());
            assertEquals("one", md.getColumnLabel(1));
            assertEquals("two", md.getColumnName(2));
            assertTrue(md.isReadOnly(1), "result sets are read-only");
        }
    }

    @Test
    @DisplayName("typed getters and wasNull behave correctly")
    void typedGetters() throws Exception {
        try (Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE TABLE t (id VARCHAR(10) PRIMARY KEY, qty INT, note VARCHAR(20))");
            st.executeUpdate("INSERT INTO t (id, qty, note) VALUES ('a', 42, NULL)");
        }
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM t")) {
            assertTrue(rs.next());
            assertEquals(42, rs.getInt("qty"));
            assertEquals(42L, rs.getLong("qty"));
            assertEquals("42", rs.getString("qty"));
            assertNull(rs.getString("note"));
            assertTrue(rs.wasNull(), "wasNull must be true after reading a null");
            assertEquals(1, rs.getRow());
        }
    }

    @Test
    @DisplayName("a closed result set rejects navigation")
    void closedResultSetRejectsNavigation() throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT 1 AS n")) {
            rs.close();
            assertTrue(rs.isClosed());
            assertThrows(SQLException.class, rs::next);
        }
    }

    // -----------------------------------------------------------------------------------
    // Honest boundary
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("unsupported JDBC calls throw, they do not silently succeed")
    void unsupportedCallsThrow() throws Exception {
        // Explicit transactions are not implemented yet.
        conn.setAutoCommit(false);
        assertThrows(SQLFeatureNotSupportedException.class, conn::commit);
        assertThrows(SQLFeatureNotSupportedException.class, conn::rollback);
        conn.setAutoCommit(true);

        assertThrows(SQLFeatureNotSupportedException.class, () -> conn.prepareCall("CALL x()"));
        assertThrows(SQLFeatureNotSupportedException.class,
                () -> conn.getMetaData().getTables(null, null, "%", null),
                "schema reflection must throw, not return an empty table list");
    }

    @Test
    @DisplayName("getTableTypes returns a usable result set")
    void tableTypes() throws Exception {
        try (ResultSet rs = conn.getMetaData().getTableTypes()) {
            assertTrue(rs.next());
            assertEquals("TABLE", rs.getString("TABLE_TYPE"));
        }
    }

    // -----------------------------------------------------------------------------------
    // File-backed connections
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("a file-backed JDBC connection persists across reopen")
    void fileBackedConnectionPersists(@TempDir Path dir) throws Exception {
        // Use the URL to open its own database rather than the injected in-memory one.
        JunifyDriver.useDatabase(null);
        String url = "jdbc:junifydb:file:" + dir.toString().replace('\\', '/');

        try (Connection c = DriverManager.getConnection(url)) {
            c.createStatement().executeUpdate(
                    "CREATE TABLE notes (id VARCHAR(20) PRIMARY KEY, body VARCHAR(50) NOT NULL)");
            c.createStatement().executeUpdate("INSERT INTO notes (id, body) VALUES ('n1', 'hello')");
        }

        try (Connection c2 = DriverManager.getConnection(url)) {
            try (ResultSet rs = c2.createStatement().executeQuery("SELECT * FROM notes")) {
                assertTrue(rs.next(), "the row must survive a reopen");
                assertEquals("hello", rs.getString("body"));
            }
            // The primary key declared before the reopen is still enforced.
            SQLException ex = assertThrows(SQLException.class, () ->
                    c2.createStatement().executeUpdate("INSERT INTO notes (id, body) VALUES ('n1', 'dup')"));
            assertTrue(ex.getMessage().contains("PRIMARY KEY"), ex.getMessage());
        }
    }
}

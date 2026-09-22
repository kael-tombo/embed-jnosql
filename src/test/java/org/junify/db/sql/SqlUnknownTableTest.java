package org.junify.db.sql;

import org.junify.db.JunifyDB;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for two SQL-engine defects in which reads and DDL lied
 * about the database they ran against (found by probing the live console's
 * SQL Studio endpoint):
 *
 * <ul>
 *   <li><b>R-48</b> — every SQL statement resolved its target table through
 *       {@code JunifyDB.documentCollection(name)}, which <b>auto-creates</b> the
 *       collection. So {@code SELECT * FROM no_such_table} did not fail — it
 *       <b>created an empty {@code no_such_table} collection as a side effect
 *       of reading</b>, and returned {@code rowCount:0, status:"success"}. The
 *       typo'd table then appeared in the console's collection list forever.
 *       JOIN targets and DROP TABLE inherited the same behavior. Reads are now
 *       read-only: SELECT and JOIN resolve an existing collection or throw
 *       {@link SqlUnknownTableException} (the console maps it to HTTP 404).
 *       INSERT/UPDATE/DELETE keep auto-create — that is the documented
 *       schemaless workflow, and internal callers (backup/restore, migrations,
 *       repositories, bulk) rely on it.</li>
 *   <li><b>R-49</b> — {@code DROP TABLE no_such_table} deleted nothing, hit no
 *       error path, and returned {@code status:"success"}. DROP is DDL: it now
 *       fails with {@link SqlUnknownTableException} when the table does not
 *       exist (an engine-level {@code DROP IF EXISTS} is future work, tracked
 *       in the register).</li>
 * </ul>
 *
 * The auto-create-vs-error distinction follows MongoDB semantics, which the
 * document store underneath already follows.
 */
@DisplayName("SQL reads never create tables and DDL never fakes success (R-48, R-49)")
class SqlUnknownTableTest {

    private JunifyDB db;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        db = JunifyDB.create(JunifyDB.embed()
                .storageEngine(org.junify.db.config.JunifyDBConfig.StorageEngineType.IN_MEMORY)
                .buildConfig());
        db.sql("INSERT INTO known (id, name) VALUES ('k1', 'Keyboard')");
    }

    @AfterEach
    void tearDown() {
        if (db != null && db.isOpen()) db.close();
    }

    // ---- R-48: SELECT on an unknown table -----------------------------------

    @Test
    @DisplayName("R-48: SELECT from an unknown table throws instead of creating it")
    void selectUnknownTableThrows() {
        assertThrows(SqlUnknownTableException.class, () -> db.sql("SELECT * FROM no_such_table"));

        assertFalse(db.getCollectionNames().contains("no_such_table"),
                "a failed SELECT must not leave an empty collection behind");
    }

    @Test
    @DisplayName("R-48: a JOIN against an unknown table throws too")
    void joinUnknownTableThrows() {
        assertThrows(SqlUnknownTableException.class,
                () -> db.sql("SELECT * FROM known k LEFT JOIN no_such_table n ON k.id = n.id"));
        assertFalse(db.getCollectionNames().contains("no_such_table"));
    }

    @Test
    @DisplayName("R-48: SELECT from an existing table still works and creates nothing new")
    void selectExistingTableWorks() {
        var rs = db.sql("SELECT * FROM known");
        assertEquals(1, rs.size());
        assertEquals("Keyboard", rs.getRows().get(0).asMap().get("name"));
        assertEquals(1, db.getCollectionNames().size());
    }

    @Test
    @DisplayName("R-48: projection-only SELECT (no FROM) is unaffected")
    void projectionOnlySelectWorks() {
        var rs = db.sql("SELECT 1 + 1");
        assertEquals(1, rs.size());
    }

    // ---- R-48: INSERT/UPDATE/DELETE keep schemaless auto-create -------------

    @Test
    @DisplayName("R-48: INSERT still auto-creates (documented schemaless workflow)")
    void insertStillAutoCreates() {
        db.sql("INSERT INTO created_by_insert (id, v) VALUES (1, 'x')");
        assertTrue(db.getCollectionNames().contains("created_by_insert"));
    }

    @Test
    @DisplayName("R-48: UPDATE and DELETE on unknown tables are SQL errors, creating nothing")
    void updateDeleteUnknownTableCreateNothing() {
        assertThrows(SqlUnknownTableException.class, () -> db.sql("UPDATE no_such_table SET v = 1"));
        assertThrows(SqlUnknownTableException.class, () -> db.sql("DELETE FROM no_such_table"));
        assertFalse(db.getCollectionNames().contains("no_such_table"),
                "UPDATE/DELETE must not create the collection either");
    }

    // ---- R-49: DROP TABLE on an unknown table -------------------------------

    @Test
    @DisplayName("R-49: DROP TABLE on an unknown table throws instead of faking success")
    void dropUnknownTableThrows() {
        assertThrows(SqlUnknownTableException.class, () -> db.sql("DROP TABLE no_such_table"));
    }

    @Test
    @DisplayName("R-49: DROP TABLE on an existing table still clears it")
    void dropExistingTableWorks() {
        db.sql("DROP TABLE known");
        assertEquals(0, db.sql("SELECT COUNT(*) FROM known").size() == 1
                ? ((Number) db.sql("SELECT COUNT(*) FROM known").getRows().get(0).asMap().values().iterator().next()).intValue()
                : -1);
        assertEquals(0, db.getCollectionNames().contains("known") ? 0 : 1,
                "known should still exist (emptied), matching the old DROP semantics");
    }

    // ---- CREATE TABLE --------------------------------------------------------

    @Test
    @DisplayName("CREATE TABLE remains the explicit create path")
    void createTableExplicit() {
        db.sql("CREATE TABLE explicit_t (id INT)");
        assertTrue(db.getCollectionNames().contains("explicit_t"));
    }
}

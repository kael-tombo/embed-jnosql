package org.junify.db.sql;

import org.junify.db.JunifyDB;
import org.junify.db.config.JunifyDBConfig.StorageEngineType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for column constraints declared through {@code CREATE TABLE}: PRIMARY KEY, UNIQUE
 * and NOT NULL.
 *
 * <p>Before this feature the parser discarded the entire parenthesised column list, so no
 * constraint could be declared or enforced — a duplicate primary key silently overwrote the
 * existing row (the document id is the storage key) and a NULL could be written into any
 * column. Each test below therefore asserts behaviour that did not exist: run against the
 * pre-change commit they fail, because nothing threw.</p>
 *
 * <p>Constraints apply <b>only</b> to tables created with rules. Schemaless tables — created by
 * INSERT or the NoSQL API — keep the original behaviour, pinned by an explicit test so the
 * feature cannot silently start affecting them.</p>
 */
@DisplayName("SQL column constraints (PRIMARY KEY / UNIQUE / NOT NULL)")
class SqlConstraintTest {

    private static final List<StorageEngineType> PERSISTENT_ENGINES = List.of(
            StorageEngineType.FILE, StorageEngineType.LSM_TREE, StorageEngineType.B_TREE);

    // -----------------------------------------------------------------------------------
    // PRIMARY KEY
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("a duplicate primary key is rejected instead of overwriting the row")
    void duplicatePrimaryKeyIsRejected() {
        try (JunifyDB db = JunifyDB.inMemory()) {
            db.sql("CREATE TABLE products (id VARCHAR(20) PRIMARY KEY, name VARCHAR(50))");
            db.sql("INSERT INTO products (id, name) VALUES ('p1', 'Keyboard')");

            SqlConstraintViolationException ex = assertThrows(SqlConstraintViolationException.class,
                    () -> db.sql("INSERT INTO products (id, name) VALUES ('p1', 'Mouse')"),
                    "a second row with the same primary key must be rejected, not silently "
                            + "overwrite the first");

            assertTrue(ex.getMessage().contains("PRIMARY KEY"), "the error must name the constraint: " + ex.getMessage());
            assertEquals(1, db.sql("SELECT * FROM products").size(),
                    "the rejected insert must not have replaced the original row");
            assertEquals("Keyboard", db.sql("SELECT * FROM products").first().get("name"),
                    "the original row must be untouched");
        }
    }

    @Test
    @DisplayName("a primary key may not be null")
    void primaryKeyRejectsNull() {
        try (JunifyDB db = JunifyDB.inMemory()) {
            db.sql("CREATE TABLE accounts (id VARCHAR(20) PRIMARY KEY, balance INT)");

            assertThrows(SqlConstraintViolationException.class,
                    () -> db.sql("INSERT INTO accounts (id, balance) VALUES (NULL, 10)"),
                    "PRIMARY KEY implies NOT NULL; a null key must be rejected");

            assertEquals(0, db.sql("SELECT * FROM accounts").size());
        }
    }

    // -----------------------------------------------------------------------------------
    // NOT NULL
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("a NOT NULL column rejects an explicit null")
    void notNullRejectsExplicitNull() {
        try (JunifyDB db = JunifyDB.inMemory()) {
            db.sql("CREATE TABLE users (id VARCHAR(20) PRIMARY KEY, email VARCHAR(50) NOT NULL)");

            SqlConstraintViolationException ex = assertThrows(SqlConstraintViolationException.class,
                    () -> db.sql("INSERT INTO users (id, email) VALUES ('u1', NULL)"));
            assertTrue(ex.getMessage().contains("NOT NULL"), ex.getMessage());

            assertEquals(0, db.sql("SELECT * FROM users").size());
        }
    }

    @Test
    @DisplayName("a NOT NULL column rejects an omitted value")
    void notNullRejectsOmittedValue() {
        try (JunifyDB db = JunifyDB.inMemory()) {
            db.sql("CREATE TABLE notes (id VARCHAR(20) PRIMARY KEY, body VARCHAR(200) NOT NULL)");

            assertThrows(SqlConstraintViolationException.class,
                    () -> db.sql("INSERT INTO notes (id) VALUES ('n1')"),
                    "a missing NOT NULL column is a violation, not an implicit null");
            assertEquals(0, db.sql("SELECT * FROM notes").size());
        }
    }

    // -----------------------------------------------------------------------------------
    // UNIQUE
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("a UNIQUE column rejects a duplicate value")
    void uniqueRejectsDuplicate() {
        try (JunifyDB db = JunifyDB.inMemory()) {
            db.sql("CREATE TABLE customers (id VARCHAR(20) PRIMARY KEY, email VARCHAR(50) UNIQUE)");
            db.sql("INSERT INTO customers (id, email) VALUES ('c1', 'a@example.com')");

            SqlConstraintViolationException ex = assertThrows(SqlConstraintViolationException.class,
                    () -> db.sql("INSERT INTO customers (id, email) VALUES ('c2', 'a@example.com')"));
            assertTrue(ex.getMessage().contains("UNIQUE"), ex.getMessage());

            assertEquals(1, db.sql("SELECT * FROM customers").size());
        }
    }

    @Test
    @DisplayName("a UNIQUE column allows multiple nulls (SQL semantics)")
    void uniqueAllowsMultipleNulls() {
        try (JunifyDB db = JunifyDB.inMemory()) {
            db.sql("CREATE TABLE guests (id VARCHAR(20) PRIMARY KEY, email VARCHAR(50) UNIQUE)");

            assertDoesNotThrow(() -> {
                db.sql("INSERT INTO guests (id, email) VALUES ('g1', NULL)");
                db.sql("INSERT INTO guests (id, email) VALUES ('g2', NULL)");
            }, "UNIQUE permits multiple nulls in SQL and must not block the second null");
            assertEquals(2, db.sql("SELECT * FROM guests").size());
        }
    }

    // -----------------------------------------------------------------------------------
    // UPDATE
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("an UPDATE that would break a UNIQUE column is rejected and the row is unchanged")
    void updateViolatingUniqueIsRejected() {
        try (JunifyDB db = JunifyDB.inMemory()) {
            db.sql("CREATE TABLE members (id VARCHAR(20) PRIMARY KEY, email VARCHAR(50) UNIQUE)");
            db.sql("INSERT INTO members (id, email) VALUES ('m1', 'one@example.com')");
            db.sql("INSERT INTO members (id, email) VALUES ('m2', 'two@example.com')");

            assertThrows(SqlConstraintViolationException.class,
                    () -> db.sql("UPDATE members SET email = 'two@example.com' WHERE id = 'm1'"),
                    "moving a unique value onto an existing one must be rejected");

            var rows = db.sql("SELECT * FROM members WHERE id = 'm1'");
            assertEquals("one@example.com", rows.first().get("email"),
                    "the rejected update must leave the row exactly as it was");
        }
    }

    @Test
    @DisplayName("an UPDATE may keep a row's own value (a row does not conflict with itself)")
    void updateKeepingOwnValueIsAllowed() {
        try (JunifyDB db = JunifyDB.inMemory()) {
            db.sql("CREATE TABLE items (id VARCHAR(20) PRIMARY KEY, sku VARCHAR(20) UNIQUE, qty INT)");
            db.sql("INSERT INTO items (id, sku, qty) VALUES ('i1', 'SKU-1', 1)");

            assertDoesNotThrow(
                    () -> db.sql("UPDATE items SET qty = 5, sku = 'SKU-1' WHERE id = 'i1'"),
                    "re-writing a row's own unique value must not be treated as a duplicate");
            assertEquals(5L, ((Number) db.sql("SELECT * FROM items").first().get("qty")).longValue());
        }
    }

    // -----------------------------------------------------------------------------------
    // Statement atomicity
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("a multi-row INSERT that violates a constraint on a later row applies nothing")
    void multiRowInsertIsAtomicOnViolation() {
        try (JunifyDB db = JunifyDB.inMemory()) {
            db.sql("CREATE TABLE codes (id VARCHAR(20) PRIMARY KEY, label VARCHAR(20))");
            db.sql("INSERT INTO codes (id, label) VALUES ('x', 'existing')");

            assertThrows(SqlConstraintViolationException.class, () -> db.sql(
                    "INSERT INTO codes (id, label) VALUES ('a', 'first'), ('x', 'collides')"),
                    "the second row collides with an existing key");

            var rows = db.sql("SELECT * FROM codes");
            assertEquals(1, rows.size(),
                    "the first row of the failed statement must have been rolled back, found "
                            + rows.stream().map(r -> r.get("id")).toList());
            assertEquals("x", rows.first().get("id"));
        }
    }

    // -----------------------------------------------------------------------------------
    // Table-level constraints
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("table-level PRIMARY KEY (...) and UNIQUE (...) are enforced")
    void tableLevelConstraintsAreEnforced() {
        try (JunifyDB db = JunifyDB.inMemory()) {
            db.sql("CREATE TABLE orders (id VARCHAR(20), code VARCHAR(20), "
                    + "PRIMARY KEY (id), UNIQUE (code))");

            db.sql("INSERT INTO orders (id, code) VALUES ('o1', 'C1')");

            assertThrows(SqlConstraintViolationException.class,
                    () -> db.sql("INSERT INTO orders (id, code) VALUES ('o1', 'C2')"),
                    "table-level PRIMARY KEY must behave like the column-level form");
            assertThrows(SqlConstraintViolationException.class,
                    () -> db.sql("INSERT INTO orders (id, code) VALUES ('o2', 'C1')"),
                    "table-level UNIQUE must behave like the column-level form");
        }
    }

    // -----------------------------------------------------------------------------------
    // No regression for schemaless tables
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("a schemaless table keeps its original behaviour (no constraints invented)")
    void schemalessTablesAreUnaffected() {
        try (JunifyDB db = JunifyDB.inMemory()) {
            // Created by INSERT, no column rules at all.
            assertDoesNotThrow(() -> {
                db.sql("INSERT INTO free (id, name) VALUES ('same', 'first')");
                db.sql("INSERT INTO free (id, name) VALUES ('same', 'second')");
            }, "a schemaless table has no primary key, so re-using an id must stay allowed");
            assertEquals(1, db.sql("SELECT * FROM free").size(),
                    "the second insert overwrites by id, exactly as before this feature");
        }
    }

    @Test
    @DisplayName("a CREATE TABLE that declares only columns (no rules) writes no metadata")
    void unconstrainedCreateWritesNoMetadata() {
        try (JunifyDB db = JunifyDB.inMemory()) {
            db.sql("CREATE TABLE plain (id INT, sku VARCHAR)");

            assertFalse(db.getCollectionNames().contains(SqlTableSchema.RESERVED_COLLECTION),
                    "a table with no constraints must not create the reserved schema collection; "
                            + "catalog was " + db.getCollectionNames());
            assertDoesNotThrow(() -> {
                db.sql("INSERT INTO plain (id, sku) VALUES ('dup', 'A')");
                db.sql("INSERT INTO plain (id, sku) VALUES ('dup', 'B')");
            }, "with no declared key, a repeated id stays schemaless");
        }
    }

    // -----------------------------------------------------------------------------------
    // Durability of the constraints themselves
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("declared constraints survive a restart on every persistent engine")
    void constraintsSurviveRestart(@TempDir Path tempDir) {
        for (StorageEngineType type : PERSISTENT_ENGINES) {
            Path dir = tempDir.resolve(type.name());

            try (JunifyDB db = JunifyDB.embed()
                    .storageEngine(type).persistTo(dir.toString()).build()) {
                db.sql("CREATE TABLE durable_keys (id VARCHAR(20) PRIMARY KEY, name VARCHAR(20))");
                db.sql("INSERT INTO durable_keys (id, name) VALUES ('k1', 'first')");
            }

            try (JunifyDB reopened = JunifyDB.embed()
                    .storageEngine(type).persistTo(dir.toString()).build()) {
                SqlConstraintViolationException ex = assertThrows(SqlConstraintViolationException.class,
                        () -> reopened.sql("INSERT INTO durable_keys (id, name) VALUES ('k1', 'dup')"),
                        type + ": the primary key declared before the restart must still be enforced");
                assertTrue(ex.getMessage().contains("PRIMARY KEY"), type + ": " + ex.getMessage());
                assertEquals(1, reopened.sql("SELECT * FROM durable_keys").size(),
                        type + ": the existing row must be intact");
            }
        }
    }

    @Test
    @DisplayName("DROP TABLE removes the table's constraints")
    void dropTableRemovesConstraints() {
        try (JunifyDB db = JunifyDB.inMemory()) {
            db.sql("CREATE TABLE temp (id VARCHAR(20) PRIMARY KEY, name VARCHAR(20))");
            db.sql("INSERT INTO temp (id, name) VALUES ('t1', 'a')");
            db.sql("DROP TABLE temp");

            // Re-create the same table schemaless; the old primary key must not linger.
            db.sql("INSERT INTO temp (id, name) VALUES ('t1', 'b')");
            assertDoesNotThrow(() -> db.sql("INSERT INTO temp (id, name) VALUES ('t1', 'c')"),
                    "after DROP the table is schemaless, so the former primary key must not apply");
        }
    }
}

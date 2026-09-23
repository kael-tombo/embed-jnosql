package org.junify.db.sql;

import org.junify.db.JunifyDB;
import org.junify.db.config.JunifyDBConfig.StorageEngineType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Coverage for {@code REFERENCES} (foreign key) and {@code CHECK} constraints.
 *
 * <p>Before this change the parser recognised neither clause — {@code REFERENCES} and
 * {@code CHECK} were simply part of the discarded column list — so no orphan could be rejected
 * and no predicate checked. Each test therefore asserts behaviour that did not exist.</p>
 *
 * <p>Foreign keys are enforced in <b>both</b> directions: a child row must point at an existing
 * parent (INSERT/UPDATE), and a parent row cannot be removed while a child still points at it
 * (DELETE, DROP TABLE). {@code CHECK} uses two-valued logic (a null operand fails the check),
 * which is documented rather than assumed — see the dedicated test.</p>
 */
@DisplayName("SQL FOREIGN KEY and CHECK constraints")
class SqlReferentialConstraintTest {

    private static JunifyDB withParents() {
        JunifyDB db = JunifyDB.inMemory();
        db.sql("CREATE TABLE customers (id VARCHAR(20) PRIMARY KEY, name VARCHAR(50))");
        return db;
    }

    // -----------------------------------------------------------------------------------
    // FOREIGN KEY — child side (INSERT / UPDATE)
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("a child row may reference an existing parent")
    void foreignKeyAcceptsMatchingParent() {
        try (JunifyDB db = withParents()) {
            db.sql("CREATE TABLE orders (id VARCHAR(20) PRIMARY KEY, "
                    + "customer_id VARCHAR(20) REFERENCES customers(id))");
            db.sql("INSERT INTO customers (id, name) VALUES ('c1', 'Ada')");

            assertDoesNotThrow(
                    () -> db.sql("INSERT INTO orders (id, customer_id) VALUES ('o1', 'c1')"));
            assertEquals(1, db.sql("SELECT * FROM orders").size());
        }
    }

    @Test
    @DisplayName("an orphan child row is rejected")
    void foreignKeyRejectsOrphan() {
        try (JunifyDB db = withParents()) {
            db.sql("CREATE TABLE orders (id VARCHAR(20) PRIMARY KEY, "
                    + "customer_id VARCHAR(20) REFERENCES customers(id))");

            SqlConstraintViolationException ex = assertThrows(SqlConstraintViolationException.class,
                    () -> db.sql("INSERT INTO orders (id, customer_id) VALUES ('o1', 'nobody')"),
                    "a foreign key to a non-existent parent must be rejected");
            assertTrue(ex.getMessage().contains("FOREIGN KEY"), ex.getMessage());
            assertEquals(0, db.sql("SELECT * FROM orders").size(),
                    "the rejected insert must leave no row behind");
        }
    }

    @Test
    @DisplayName("a null foreign key is allowed (the row references nothing)")
    void foreignKeyAllowsNull() {
        try (JunifyDB db = withParents()) {
            db.sql("CREATE TABLE orders (id VARCHAR(20) PRIMARY KEY, "
                    + "customer_id VARCHAR(20) REFERENCES customers(id))");

            assertDoesNotThrow(
                    () -> db.sql("INSERT INTO orders (id, customer_id) VALUES ('o1', NULL)"));
            assertEquals(1, db.sql("SELECT * FROM orders").size());
        }
    }

    @Test
    @DisplayName("a foreign key to a table that does not exist is rejected, without creating it")
    void foreignKeyRejectsMissingTable() {
        try (JunifyDB db = JunifyDB.inMemory()) {
            // The referenced table is never created.
            db.sql("CREATE TABLE orphans (id VARCHAR(20) PRIMARY KEY, "
                    + "ref VARCHAR(20) REFERENCES ghosts(id))");

            SqlConstraintViolationException ex = assertThrows(SqlConstraintViolationException.class,
                    () -> db.sql("INSERT INTO orphans (id, ref) VALUES ('x', 'g1')"));
            assertTrue(ex.getMessage().contains("does not exist"), ex.getMessage());
            assertFalse(db.getCollectionNames().contains("ghosts"),
                    "validating a foreign key must not create the referenced table (R-48 class); "
                            + "catalog was " + db.getCollectionNames());
        }
    }

    @Test
    @DisplayName("an UPDATE that would orphan a child row is rejected and the row is unchanged")
    void foreignKeyValidatedOnUpdate() {
        try (JunifyDB db = withParents()) {
            db.sql("CREATE TABLE orders (id VARCHAR(20) PRIMARY KEY, "
                    + "customer_id VARCHAR(20) REFERENCES customers(id))");
            db.sql("INSERT INTO customers (id, name) VALUES ('c1', 'Ada')");
            db.sql("INSERT INTO orders (id, customer_id) VALUES ('o1', 'c1')");

            assertThrows(SqlConstraintViolationException.class,
                    () -> db.sql("UPDATE orders SET customer_id = 'ghost' WHERE id = 'o1'"));
            assertEquals("c1", db.sql("SELECT * FROM orders").first().get("customer_id"),
                    "the rejected update must leave the row as it was");
        }
    }

    @Test
    @DisplayName("table-level FOREIGN KEY (col) REFERENCES ... is enforced")
    void tableLevelForeignKeyIsEnforced() {
        try (JunifyDB db = withParents()) {
            db.sql("CREATE TABLE invoices (id VARCHAR(20), customer_id VARCHAR(20), "
                    + "FOREIGN KEY (customer_id) REFERENCES customers(id))");
            db.sql("INSERT INTO customers (id, name) VALUES ('c1', 'Ada')");

            assertDoesNotThrow(
                    () -> db.sql("INSERT INTO invoices (id, customer_id) VALUES ('i1', 'c1')"));
            assertThrows(SqlConstraintViolationException.class,
                    () -> db.sql("INSERT INTO invoices (id, customer_id) VALUES ('i2', 'ghost')"));
        }
    }

    // -----------------------------------------------------------------------------------
    // FOREIGN KEY — parent side (DELETE / DROP)
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("a parent row still referenced by a child cannot be deleted")
    void foreignKeyBlocksParentDelete() {
        try (JunifyDB db = withParents()) {
            db.sql("CREATE TABLE orders (id VARCHAR(20) PRIMARY KEY, "
                    + "customer_id VARCHAR(20) REFERENCES customers(id))");
            db.sql("INSERT INTO customers (id, name) VALUES ('c1', 'Ada')");
            db.sql("INSERT INTO customers (id, name) VALUES ('c2', 'Grace')");
            db.sql("INSERT INTO orders (id, customer_id) VALUES ('o1', 'c1')");

            SqlConstraintViolationException ex = assertThrows(SqlConstraintViolationException.class,
                    () -> db.sql("DELETE FROM customers WHERE id = 'c1'"),
                    "deleting a referenced parent must be rejected");
            assertTrue(ex.getMessage().contains("FOREIGN KEY"), ex.getMessage());
            assertEquals(2, db.sql("SELECT * FROM customers").size(),
                    "the rejected delete must not remove any row");

            // An unreferenced parent is still deletable.
            assertDoesNotThrow(() -> db.sql("DELETE FROM customers WHERE id = 'c2'"));
            assertEquals(1, db.sql("SELECT * FROM customers").size());
        }
    }

    @Test
    @DisplayName("a referenced table cannot be dropped")
    void foreignKeyBlocksParentDrop() {
        try (JunifyDB db = withParents()) {
            db.sql("CREATE TABLE orders (id VARCHAR(20) PRIMARY KEY, "
                    + "customer_id VARCHAR(20) REFERENCES customers(id))");
            db.sql("INSERT INTO customers (id, name) VALUES ('c1', 'Ada')");
            db.sql("INSERT INTO orders (id, customer_id) VALUES ('o1', 'c1')");

            assertThrows(SqlConstraintViolationException.class,
                    () -> db.sql("DROP TABLE customers"),
                    "dropping a table another table references must be rejected");
            assertEquals(1, db.sql("SELECT * FROM customers").size());
        }
    }

    // -----------------------------------------------------------------------------------
    // CHECK
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("a CHECK predicate rejects a violating row")
    void checkRejectsViolation() {
        try (JunifyDB db = JunifyDB.inMemory()) {
            db.sql("CREATE TABLE stock (id VARCHAR(20) PRIMARY KEY, qty INT CHECK (qty > 0))");
            db.sql("INSERT INTO stock (id, qty) VALUES ('s1', 5)");

            SqlConstraintViolationException ex = assertThrows(SqlConstraintViolationException.class,
                    () -> db.sql("INSERT INTO stock (id, qty) VALUES ('s2', 0)"));
            assertTrue(ex.getMessage().contains("CHECK"), ex.getMessage());
            assertEquals(1, db.sql("SELECT * FROM stock").size());
        }
    }

    @Test
    @DisplayName("a CHECK predicate compares string literals")
    void checkStringComparison() {
        try (JunifyDB db = JunifyDB.inMemory()) {
            db.sql("CREATE TABLE jobs (id VARCHAR(20) PRIMARY KEY, "
                    + "status VARCHAR(20) CHECK (status <> 'BAD'))");
            db.sql("INSERT INTO jobs (id, status) VALUES ('j1', 'OK')");

            assertThrows(SqlConstraintViolationException.class,
                    () -> db.sql("INSERT INTO jobs (id, status) VALUES ('j2', 'BAD')"),
                    "the stored string literal must survive the round trip through schema text");
        }
    }

    @Test
    @DisplayName("table-level CHECK (...) is enforced")
    void tableLevelCheckIsEnforced() {
        try (JunifyDB db = JunifyDB.inMemory()) {
            db.sql("CREATE TABLE ranges (id VARCHAR(20), low INT, high INT, CHECK (high > low))");
            db.sql("INSERT INTO ranges (id, low, high) VALUES ('r1', 1, 10)");

            assertThrows(SqlConstraintViolationException.class,
                    () -> db.sql("INSERT INTO ranges (id, low, high) VALUES ('r2', 10, 1)"));
        }
    }

    @Test
    @DisplayName("a CHECK with a null operand fails (documented two-valued semantics)")
    void checkTreatsNullAsViolation() {
        try (JunifyDB db = JunifyDB.inMemory()) {
            db.sql("CREATE TABLE items (id VARCHAR(20) PRIMARY KEY, qty INT CHECK (qty > 0))");
            // SQL would treat NULL as "unknown" and let the row through; JunifyDB evaluates the
            // predicate two-valued, so a null operand fails. This is asserted so the behaviour is
            // deliberate and cannot drift silently.
            assertThrows(SqlConstraintViolationException.class,
                    () -> db.sql("INSERT INTO items (id, qty) VALUES ('i1', NULL)"));
        }
    }

    // -----------------------------------------------------------------------------------
    // Durability
    // -----------------------------------------------------------------------------------

    @Test
    @DisplayName("foreign key and check constraints survive a restart")
    void referentialConstraintsSurviveRestart(@TempDir Path tempDir) {
        for (StorageEngineType type : new StorageEngineType[]{
                StorageEngineType.FILE, StorageEngineType.LSM_TREE, StorageEngineType.B_TREE}) {
            Path dir = tempDir.resolve(type.name());

            try (JunifyDB db = JunifyDB.embed()
                    .storageEngine(type).persistTo(dir.toString()).build()) {
                db.sql("CREATE TABLE parents (id VARCHAR(20) PRIMARY KEY, name VARCHAR(20))");
                db.sql("CREATE TABLE kids (id VARCHAR(20) PRIMARY KEY, "
                        + "parent_id VARCHAR(20) REFERENCES parents(id), "
                        + "qty INT CHECK (qty > 0))");
                db.sql("INSERT INTO parents (id, name) VALUES ('p1', 'P')");
                db.sql("INSERT INTO kids (id, parent_id, qty) VALUES ('k1', 'p1', 1)");
            }

            try (JunifyDB reopened = JunifyDB.embed()
                    .storageEngine(type).persistTo(dir.toString()).build()) {
                assertThrows(SqlConstraintViolationException.class,
                        () -> reopened.sql("INSERT INTO kids (id, parent_id, qty) VALUES ('k2', 'ghost', 1)"),
                        type + ": the foreign key must still be enforced after a restart");
                assertThrows(SqlConstraintViolationException.class,
                        () -> reopened.sql("INSERT INTO kids (id, parent_id, qty) VALUES ('k3', 'p1', 0)"),
                        type + ": the check must still be enforced after a restart");
            }
        }
    }
}

package org.junify.db.sql;

/**
 * Thrown when an INSERT or UPDATE would violate a column constraint declared on
 * a table created through {@code CREATE TABLE} (PRIMARY KEY, UNIQUE or NOT NULL).
 *
 * <p>Constraints are enforced only for tables that were created <em>with column
 * definitions</em>. Schemaless tables — created by INSERT or by the NoSQL API —
 * keep the original schemaless behaviour, so this exception never appears for a
 * table the user did not explicitly constrain.</p>
 *
 * <p>The console HTTP layer maps this to 400 (client error about data) alongside
 * the generic SQL execution error; the message names the table, the column and
 * the constraint so an operator can act on it.</p>
 */
public class SqlConstraintViolationException extends RuntimeException {

    public SqlConstraintViolationException(String message) {
        super(message);
    }
}

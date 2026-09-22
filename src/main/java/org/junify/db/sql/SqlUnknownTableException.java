package org.junify.db.sql;

/**
 * Thrown when an SQL statement references a table (collection) that does not
 * exist, on a code path that must not create it: SELECT and JOIN table
 * resolution, and DROP TABLE.
 *
 * <p><b>R-48:</b> reads previously resolved tables through the auto-creating
 * {@code JunifyDB.documentCollection(name)}, so {@code SELECT * FROM
 * no_such_table} created an empty collection as a side effect of reading and
 * answered {@code rowCount:0, status:"success"}. Reads are now read-only and
 * throw this exception instead.</p>
 *
 * <p><b>R-49:</b> {@code DROP TABLE} on a missing table previously deleted
 * nothing and reported success; as DDL it now throws this exception (an
 * engine-level {@code DROP TABLE IF EXISTS} is future work).</p>
 *
 * <p>The console HTTP layer maps this to 404 so the client sees "table does
 * not exist" rather than a fake empty result set.</p>
 */
public class SqlUnknownTableException extends RuntimeException {

    public SqlUnknownTableException(String message) {
        super(message);
    }
}

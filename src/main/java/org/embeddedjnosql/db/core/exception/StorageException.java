package org.embeddedjnosql.db.core.exception;

/**
 * Thrown when a storage engine fails to initialize, flush, or persist data
 * (audit R-16 / 21-E-01). The cause chain preserves the underlying
 * {@link java.io.IOException} when one exists.
 */
public class StorageException extends EmbedJNoSQLException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

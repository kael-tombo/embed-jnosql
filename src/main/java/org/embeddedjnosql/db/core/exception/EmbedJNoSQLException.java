package org.embeddedjnosql.db.core.exception;

/**
 * Base class for all EmbedJNoSQL runtime exceptions (audit R-16 / 21-E-01).
 *
 * <p>Unchecked by design: embedded-database failures are almost never
 * recoverable at the call site, and forcing checked handling pushed every
 * caller into wrapping the exception in a bare {@link RuntimeException},
 * which destroyed type information. Catching {@code EmbedJNoSQLException} now
 * catches every engine-level failure EmbedJNoSQL raises deliberately.
 *
 * <p>Hierarchy:
 * <pre>
 * EmbedJNoSQLException
 * ├── SerializationException   (JSON encode/decode, entity mapping)
 * └── StorageException        (engine init, flush, persistence I/O)
 * </pre>
 */
public class EmbedJNoSQLException extends RuntimeException {

    public EmbedJNoSQLException(String message) {
        super(message);
    }

    public EmbedJNoSQLException(String message, Throwable cause) {
        super(message, cause);
    }
}

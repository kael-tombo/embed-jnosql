package org.embeddedjnosql.db.core.exception;

/**
 * Thrown when JSON serialization/deserialization or entity mapping fails
 * (audit R-16 / 21-E-01). Carries the offending payload or target type in the
 * message where available.
 */
public class SerializationException extends EmbedJNoSQLException {

    public SerializationException(String message) {
        super(message);
    }

    public SerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}

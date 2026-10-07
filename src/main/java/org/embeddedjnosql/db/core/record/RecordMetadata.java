package org.embeddedjnosql.db.core.record;

/**
 * Unified record metadata — tracks versioning for MVCC and type hints.
 */
public record RecordMetadata(
    long version,
    String txId,
    long timestamp,
    RecordType typeHint,
    long expiresAt
) {
    public RecordMetadata {
        if (version < 0) version = 0;
        if (timestamp <= 0) timestamp = System.currentTimeMillis();
    }

    public static RecordMetadata initial(RecordType type) {
        return new RecordMetadata(0, null, System.currentTimeMillis(), type, -1);
    }

    public RecordMetadata nextVersion(String txId) {
        return new RecordMetadata(version + 1, txId, System.currentTimeMillis(), typeHint, expiresAt);
    }

    public boolean isExpired() {
        return expiresAt > 0 && System.currentTimeMillis() > expiresAt;
    }

    public enum RecordType { DOCUMENT, KEY_VALUE, COLUMN }
}

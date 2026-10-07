package org.embeddedjnosql.db.core.record;

/**
 * UnifiedRecord — the single data representation across all storage models.
 * Ensures type safety and consistent metadata handling for MVCC.
 */
public interface UnifiedRecord {
    String id();
    String entityName();
    byte[] payload();
    RecordMetadata metadata();

    /**
     * Serialize to JSON string.
     */
    String toJson();

    /**
     * Create a new version of this record with updated metadata.
     */
    default UnifiedRecord withMetadata(RecordMetadata metadata) {
        // Default implementation does nothing; concrete classes should override.
        return this;
    }
}

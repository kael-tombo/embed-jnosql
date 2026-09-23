package org.junify.db.storage.spi;

import org.junify.db.core.record.UnifiedRecord;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Storage Engine SPI — Unified interface for NoSQL data storage.
 * 
 * Features:
 * - Default methods for zero-copy record I/O
 * - Reactive batch operations
 * - UnifiedRecord native support
 */
public interface StorageEngine {

    /**
     * Engine name for identification and routing.
     */
    String name();

    // === Basic Key-Value Operations ===
    
    void put(String collection, String key, String value);

    void putAll(String collection, Map<String, String> entries);

    String get(String collection, String key);

    List<String> getAll(String collection, List<String> keys);

    void delete(String collection, String key);

    void deleteAll(String collection, List<String> keys);

    boolean exists(String collection, String key);

    List<String> scan(String collection);

    List<String> scan(String collection, Predicate<String> filter);

    /**
     * Names of collections persisted by this engine and discoverable on restart.
     * Default: empty — engines without on-disk per-collection identity report none.
     */
    default java.util.Set<String> collectionNames() {
        return java.util.Collections.emptySet();
    }

    /**
     * Every collection currently held by this engine, including collections created
     * in this session that have not been flushed yet.
     *
     * <p>This differs from {@link #collectionNames()}, which only reports collections
     * that can be rediscovered after a restart. Backup and admin surfaces need the live
     * set: enumerating the wrong set silently produces an empty or partial backup, which
     * is worse than an error because the user is told the backup succeeded.
     *
     * <p>Default: {@link #collectionNames()}. Engines that keep collections only in
     * memory must override this, otherwise callers see an empty set for a populated
     * engine (detectable via {@link #size()} being non-zero).
     */
    default java.util.Set<String> collections() {
        return collectionNames();
    }

    Set<String> keys(String collection);

    /**
     * Ensures {@code collection} exists as a first-class object in the engine even when it
     * holds no records, so its existence is discoverable after a restart.
     *
     * <p><b>R-62 (2026-09-23):</b> a collection whose existence lived only in
     * {@code JunifyDB}'s in-memory catalog vanished on restart. {@code CREATE TABLE t (id INT)}
     * answered {@code {"status":"success"}}, wrote no file, and after a restart
     * {@code SELECT * FROM t} reported <i>"Table does not exist"</i> — DDL reporting success
     * for state that was never durable. Rows were never lost (an {@code INSERT} materialises
     * the collection and its snapshot), so the defect was confined to the existence of an
     * empty collection, but a database must not tell an operator that an object exists when
     * it will not.
     *
     * <p>Callers invoke this when a collection is <b>created</b> (SQL DDL, the console's
     * create route, repository/annotation materialisation) — never when one is merely read.
     *
     * @return {@code true} when the engine now reports and persists the collection's
     *         existence with no records; {@code false} when the engine has no notion of an
     *         empty collection (in-memory engines carry no durability contract)
     */
    default boolean ensureCollection(String collection) {
        return false;
    }

    int size();

    Map<String, Object> stats();

    void flush();

    void close();

    // === UnifiedRecord Native Support ===

    /**
     * Store a UnifiedRecord directly.
     * Default implementation serializes to JSON and delegates to put().
     * Implementations should override for efficient binary storage.
     */
    default void putRecord(String collection, UnifiedRecord record) {
        put(collection, record.id(), record.toJson());
    }

    /**
     * Retrieve a UnifiedRecord by ID.
     * Default implementation delegates to get() and deserializes.
     * Implementations should override for efficient binary retrieval.
     */
    default UnifiedRecord getRecord(String collection, String id, 
                                     Function<String, ? extends UnifiedRecord> factory) {
        var json = get(collection, id);
        return json != null ? factory.apply(json) : null;
    }

    /**
     * Batch store UnifiedRecords.
     * Default implementation iterates putRecord().
     */
    default void putAllRecords(String collection, List<UnifiedRecord> records) {
        for (var record : records) {
            putRecord(collection, record);
        }
    }

    /**
     * Batch retrieve UnifiedRecords.
     * Default implementation iterates getRecord().
     */
    default List<UnifiedRecord> getAllRecords(String collection, List<String> ids,
                                               Function<String, ? extends UnifiedRecord> factory) {
        return getAll(collection, ids).stream()
            .map(factory)
            .collect(Collectors.toList());
    }

    // === Query Operations ===

    /**
     * Query with predicate filter.
     * Default implementation scans and filters.
     */
    default List<UnifiedRecord> queryRecords(String collection, 
                                              Predicate<UnifiedRecord> filter,
                                              Function<String, ? extends UnifiedRecord> factory) {
        return scan(collection).stream()
            .map(id -> getRecord(collection, id, factory))
            .filter(record -> record != null && filter.test(record))
            .collect(Collectors.toList());
    }

    /**
     * Count records matching predicate.
     */
    default long countRecords(String collection, 
                               Predicate<UnifiedRecord> filter,
                               Function<String, ? extends UnifiedRecord> factory) {
        return queryRecords(collection, filter, factory).size();
    }

    // === Transaction Support ===

    /**
     * Begin a transaction (if supported).
     * Default implementation is a no-op.
     */
    default void beginTransaction() {
        // No-op for engines that don't support transactions
    }

    /**
     * Commit a transaction (if supported).
     */
    default void commitTransaction() {
        throw new UnsupportedOperationException("Transactions not supported by " + name());
    }

    /**
     * Rollback a transaction (if supported).
     */
    default void rollbackTransaction() {
        throw new UnsupportedOperationException("Transactions not supported by " + name());
    }

    // === Index Support ===

    /**
     * Create an index (if supported).
     */
    default void createIndex(String collection, String field, String indexType) {
        throw new UnsupportedOperationException("Indexes not supported by " + name());
    }

    /**
     * Drop an index (if supported).
     */
    default void dropIndex(String collection, String field) {
        throw new UnsupportedOperationException("Indexes not supported by " + name());
    }

    // === Engine Capabilities ===

    /**
     * Check if engine supports transactions.
     */
    default boolean supportsTransactions() {
        return false;
    }

    /**
     * Check if engine supports indexes.
     */
    default boolean supportsIndexes() {
        return false;
    }



    /**
     * Check if engine is persistent (vs in-memory).
     */
    default boolean isPersistent() {
        return false;
    }
}

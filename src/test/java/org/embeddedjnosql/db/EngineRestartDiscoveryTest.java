package org.embeddedjnosql.db;

import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for <b>R-53</b>: {@code materializePersistedCollections()}
 * enumerated {@code engine.collectionNames()} — an SPI method whose default
 * returns an empty set and which only {@code FileEngine} overrides. On the
 * LSM_TREE and B_TREE engines, data survived a restart on disk (WAL/SSTables
 * and the index file respectively) but was <b>invisible to every listing API</b>:
 *
 * <ul>
 *   <li>{@code GET /api/collections} showed an empty catalog after restart</li>
 *   <li>{@code BackupManager} (which iterates the live {@code collections()})
 *       would capture only collections a client happened to touch</li>
 *   <li>SQL and backups saw nothing until someone requested the exact name,
 *       which lazily re-materialized that one collection</li>
 * </ul>
 *
 * Found by booting real servers on every engine and comparing catalogs before
 * and after a restart — the same probe class that surfaced R-31..R-52, applied
 * to the engines the console gates had never booted (they only ever ran FILE).
 */
@DisplayName("Persisted collections re-materialize on every engine after restart (R-53)")
class EngineRestartDiscoveryTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("R-53: LSM_TREE collections survive restart AND appear in the catalog")
    void lsmTreeRediscoversCollections() {
        EmbedJNoSQLConfig config = EmbedJNoSQLConfig.builder()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.LSM_TREE)
                .dataDir(tempDir.resolve("lsm"))
                .buildConfig();
        try (var db = EmbedJNoSQL.create(config)) {
            db.documentCollection("lsm_col").insert(
                    org.embeddedjnosql.db.nosql.document.Document.fromJson("{\"id\":\"r1\",\"v\":1}"));
        }
        try (var db = EmbedJNoSQL.create(config)) {
            assertTrue(db.getCollectionNames().contains("lsm_col"),
                    "LSM catalog must list persisted collections after restart: " + db.getCollectionNames());
            assertEquals(1, db.documentCollection("lsm_col").count());
        }
    }

    @Test
    @DisplayName("R-53: B_TREE collections survive restart AND appear in the catalog")
    void bTreeRediscoversCollections() {
        EmbedJNoSQLConfig config = EmbedJNoSQLConfig.builder()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.B_TREE)
                .dataDir(tempDir.resolve("btree"))
                .buildConfig();
        try (var db = EmbedJNoSQL.create(config)) {
            db.documentCollection("btree_col").insert(
                    org.embeddedjnosql.db.nosql.document.Document.fromJson("{\"id\":\"r1\",\"v\":1}"));
            db.flush();
        }
        try (var db = EmbedJNoSQL.create(config)) {
            assertTrue(db.getCollectionNames().contains("btree_col"),
                    "BTree catalog must list persisted collections after restart: " + db.getCollectionNames());
            assertEquals(1, db.documentCollection("btree_col").count());
        }
    }

    @Test
    @DisplayName("R-53: FILE engine discovery is unchanged")
    void fileEngineStillDiscovers() {
        EmbedJNoSQLConfig config = EmbedJNoSQLConfig.builder()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.FILE)
                .dataDir(tempDir.resolve("file"))
                .buildConfig();
        try (var db = EmbedJNoSQL.create(config)) {
            db.documentCollection("file_col").insert(
                    org.embeddedjnosql.db.nosql.document.Document.fromJson("{\"id\":\"r1\",\"v\":1}"));
        }
        try (var db = EmbedJNoSQL.create(config)) {
            assertTrue(db.getCollectionNames().contains("file_col"));
            assertEquals(1, db.documentCollection("file_col").count());
        }
    }

    @Test
    @DisplayName("R-62: a collection that exists only as a created empty (no docs) still materializes")
    void emptyCollectionMaterializes() {
        // This assertion was previously inverted: it asserted that an empty collection left
        // "no engine-side trace to discover", which was the R-62 defect stated as a contract.
        // A created collection is now durable on every persistent engine (`CREATE TABLE t`
        // used to answer success and then vanish on restart), so it must be rediscovered here.
        EmbedJNoSQLConfig config = EmbedJNoSQLConfig.builder()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.LSM_TREE)
                .dataDir(tempDir.resolve("lsempty"))
                .buildConfig();
        try (var db = EmbedJNoSQL.create(config)) {
            db.documentCollection("never_written");
        }
        try (var db = EmbedJNoSQL.create(config)) {
            assertTrue(db.getCollectionNames().contains("never_written"),
                    "a created collection with zero records must be rediscovered after a restart, "
                            + "found: " + db.getCollectionNames());
            assertEquals(0, db.documentCollection("never_written").count(),
                    "it comes back empty, not repopulated");
        }
    }
}

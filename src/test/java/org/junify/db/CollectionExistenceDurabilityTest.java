package org.junify.db;

import org.junify.db.config.JunifyDBConfig;
import org.junify.db.config.JunifyDBConfig.StorageEngineType;
import org.junify.db.nosql.document.Document;
import org.junify.db.sql.SqlUnknownTableException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for R-62: a collection that was created but holds no records must
 * still exist after a restart.
 *
 * <p>Measured before the fix on a live {@code --sync FILE} server:
 * {@code CREATE TABLE r61_empty_t (id INT)} answered {@code {"status":"success"}}, no
 * {@code r61_empty_t.json} was written, and after a restart {@code SELECT * FROM
 * r61_empty_t} reported <i>"Table does not exist"</i>. Rows were never lost — an
 * {@code INSERT} materialises and persists the collection — so the defect was the
 * durability of an empty collection's <i>existence</i>, which is what these tests pin.</p>
 *
 * <p>Every engine is covered, because they carry collection identity differently:
 * {@code FILE} writes one snapshot per collection, while {@code LSM_TREE} and {@code B_TREE}
 * address records by {@code collection:key} and need the existence registry. {@code IN_MEMORY}
 * is asserted to list a created collection for its process lifetime and to make no
 * durability promise — that is its documented contract, not a defect.</p>
 */
@DisplayName("A created collection's existence survives a restart (R-62)")
class CollectionExistenceDurabilityTest {

    private static final List<StorageEngineType> PERSISTENT_ENGINES = List.of(
            StorageEngineType.FILE, StorageEngineType.LSM_TREE, StorageEngineType.B_TREE);

    private JunifyDB open(Path dir, StorageEngineType type) {
        return JunifyDB.embed()
                .storageEngine(type)
                .persistTo(dir.toString())
                .build();
    }

    @Test
    @DisplayName("an empty table created by SQL DDL is still there after a restart")
    void emptyTableCreatedByDdlSurvivesRestart(@TempDir Path tempDir) {
        for (StorageEngineType type : PERSISTENT_ENGINES) {
            Path dir = tempDir.resolve(type.name());

            try (JunifyDB db = open(dir, type)) {
                var result = db.sql("CREATE TABLE empty_orders (id INT, sku VARCHAR)");
                assertEquals("CREATE_TABLE", result.getStatementType(),
                        type + ": CREATE TABLE must report the statement it ran");
                assertTrue(db.getCollectionNames().contains("empty_orders"),
                        type + ": the created table must be in the catalog immediately");
            }

            try (JunifyDB reopened = open(dir, type)) {
                assertTrue(reopened.getCollectionNames().contains("empty_orders"),
                        type + ": an empty table must survive a restart, but the catalog was "
                                + reopened.getCollectionNames());

                var select = reopened.sql("SELECT * FROM empty_orders");
                assertEquals(0, select.size(),
                        type + ": the table exists and simply holds no rows");
            }
        }
    }

    @Test
    @DisplayName("a collection created through the API is durable before it holds anything")
    void emptyCollectionCreatedThroughTheApiSurvivesRestart(@TempDir Path tempDir) {
        for (StorageEngineType type : PERSISTENT_ENGINES) {
            Path dir = tempDir.resolve("api-" + type.name());

            try (JunifyDB db = open(dir, type)) {
                db.documentCollection("created_by_api");
                assertEquals(0, db.documentCollection("created_by_api").count());
            }

            try (JunifyDB reopened = open(dir, type)) {
                assertTrue(reopened.getCollectionNames().contains("created_by_api"),
                        type + ": an explicitly created collection with no documents must survive "
                                + "a restart, but the catalog was " + reopened.getCollectionNames());
            }
        }
    }

    @Test
    @DisplayName("the FILE engine leaves an empty snapshot on disk for an empty collection")
    void fileEngineWritesAnEmptySnapshot(@TempDir Path tempDir) throws Exception {
        Path dir = tempDir.resolve("snapshot");

        try (JunifyDB db = open(dir, StorageEngineType.FILE)) {
            db.sql("CREATE TABLE empty_snapshot (id INT)");
        }

        Path snapshot = dir.resolve("empty_snapshot.json");
        assertTrue(Files.exists(snapshot),
                "an empty collection needs its own snapshot file, otherwise nothing on disk "
                        + "records that it exists: " + Files.list(dir).map(p -> p.getFileName().toString()).toList());
        assertEquals("{}", Files.readString(snapshot).trim(),
                "an empty collection's snapshot holds no records and must not be a stub");
    }

    @Test
    @DisplayName("rows written to a collection created by INSERT still persist (no regression)")
    void rowsInAnAutoCreatedCollectionStillPersist(@TempDir Path tempDir) {
        for (StorageEngineType type : PERSISTENT_ENGINES) {
            Path dir = tempDir.resolve("rows-" + type.name());

            try (JunifyDB db = open(dir, type)) {
                db.sql("INSERT INTO auto_created (id, sku) VALUES ('k1', 'ABC')");
                assertEquals(1, db.sql("SELECT * FROM auto_created").size(),
                        type + ": the inserted row must be readable in the same session");
            }

            try (JunifyDB reopened = open(dir, type)) {
                var rows = reopened.sql("SELECT * FROM auto_created");
                assertEquals(1, rows.size(), type + ": the inserted row must survive a restart");
                assertEquals("ABC", rows.first().get("sku"), type + ": field values must round-trip");
            }
        }
    }

    @Test
    @DisplayName("reading a missing table still does not create it (R-48 holds after R-62)")
    void readingAMissingTableDoesNotCreateIt(@TempDir Path tempDir) {
        for (StorageEngineType type : PERSISTENT_ENGINES) {
            Path dir = tempDir.resolve("read-" + type.name());

            try (JunifyDB db = open(dir, type)) {
                assertThrows(SqlUnknownTableException.class,
                        () -> db.sql("SELECT * FROM r62_never_created"),
                        type + ": SELECT on an unknown table is an error, not an empty result");
                assertFalse(db.getCollectionNames().contains("r62_never_created"),
                        type + ": a read must not add anything to the catalog, found "
                                + db.getCollectionNames());
            }

            try (JunifyDB reopened = open(dir, type)) {
                assertFalse(reopened.getCollectionNames().contains("r62_never_created"),
                        type + ": the failed read must not become durable state either");
            }
        }
    }

    @Test
    @DisplayName("the in-memory engine lists a created collection but promises no durability")
    void inMemoryEngineListsCreatedCollectionsWithoutDurability(@TempDir Path tempDir) {
        Path dir = tempDir.resolve("memory");

        try (JunifyDB db = open(dir, StorageEngineType.IN_MEMORY)) {
            db.sql("CREATE TABLE memory_only (id INT)");
            assertTrue(db.getCollectionNames().contains("memory_only"),
                    "an in-memory catalog must still list a collection created in this process");
        }

        try (JunifyDB reopened = open(dir, StorageEngineType.IN_MEMORY)) {
            assertFalse(reopened.getCollectionNames().contains("memory_only"),
                    "an in-memory engine has no durability contract — it must not pretend to "
                            + "have kept the collection, and must not write a data dir either");
        }
        assertFalse(Files.exists(dir.resolve("memory_only.json")),
                "an in-memory engine must not write a FILE-engine snapshot for the collection");
    }

    @Test
    @DisplayName("a document collection created empty is still writable after a restart")
    void emptyCollectionIsFullyUsableAfterRestart(@TempDir Path tempDir) {
        Path dir = tempDir.resolve("usable");

        try (JunifyDB db = open(dir, StorageEngineType.FILE)) {
            db.sql("CREATE TABLE usable_table (id INT, sku VARCHAR)");
        }

        try (JunifyDB reopened = open(dir, StorageEngineType.FILE)) {
            var collection = reopened.documentCollection("usable_table");
            collection.insert(Document.of("sku", "AFTER-RESTART"));
            assertEquals(1, collection.count());

            assertEquals(1, reopened.sql("SELECT * FROM usable_table").size(),
                    "the table created before the restart must accept writes afterwards, not "
                            + "be re-created as a different object");
        }
    }
}

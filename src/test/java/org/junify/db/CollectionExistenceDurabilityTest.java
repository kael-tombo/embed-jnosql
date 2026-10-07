package org.junify.db;

import org.junify.db.adapter.jnosql.Entity;
import org.junify.db.adapter.jnosql.Id;
import org.junify.db.config.JunifyDBConfig.StorageEngineType;
import org.junify.db.nosql.document.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A collection that was created but holds no records must still exist after a restart.
 *
 * <p>Rows were never lost — an {@code insert} materialises and persists the collection — so
 * what these tests pin is the durability of an empty collection's <i>existence</i>, across
 * every engine. {@code FILE} writes one snapshot per collection, while {@code LSM_TREE} and
 * {@code B_TREE} address records by {@code collection:key} and need the existence registry.
 * {@code IN_MEMORY} is asserted to list a created collection for its process lifetime and to
 * make no durability promise — that is its documented contract, not a defect.</p>
 */
@DisplayName("A created collection's existence survives a restart")
class CollectionExistenceDurabilityTest {

    private static final List<StorageEngineType> PERSISTENT_ENGINES = List.of(
            StorageEngineType.FILE, StorageEngineType.LSM_TREE, StorageEngineType.B_TREE);

    private JunifyDB open(Path dir, StorageEngineType type) {
        return JunifyDB.embed()
                .storageEngine(type)
                .persistTo(dir.toString())
                .build();
    }

    /** A read target that never has data written to it. */
    @Entity("r62_never_created")
    static class NeverCreated {
        @Id
        private String id;
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
            db.documentCollection("empty_snapshot");
        }

        Path snapshot = dir.resolve("empty_snapshot.json");
        assertTrue(Files.exists(snapshot),
                "an empty collection needs its own snapshot file, otherwise nothing on disk "
                        + "records that it exists: " + Files.list(dir).map(p -> p.getFileName().toString()).toList());
        assertEquals("{}", Files.readString(snapshot).trim(),
                "an empty collection's snapshot holds no records and must not be a stub");
    }

    @Test
    @DisplayName("rows written to a collection still persist after a restart")
    void rowsInACollectionStillPersist(@TempDir Path tempDir) {
        for (StorageEngineType type : PERSISTENT_ENGINES) {
            Path dir = tempDir.resolve("rows-" + type.name());

            try (JunifyDB db = open(dir, type)) {
                db.documentCollection("auto_created")
                        .insert(Document.of("sku", "ABC").id("k1"));
                assertEquals(1, db.documentCollection("auto_created").count(),
                        type + ": the inserted row must be readable in the same session");
            }

            try (JunifyDB reopened = open(dir, type)) {
                var rows = reopened.documentCollection("auto_created").findAll();
                assertEquals(1, rows.size(), type + ": the inserted row must survive a restart");
                assertEquals("ABC", rows.get(0).get("sku"), type + ": field values must round-trip");
            }
        }
    }

    @Test
    @DisplayName("a query against a missing collection matches nothing and does not create it")
    void queryingAMissingCollectionDoesNotCreateIt(@TempDir Path tempDir) {
        for (StorageEngineType type : PERSISTENT_ENGINES) {
            Path dir = tempDir.resolve("read-" + type.name());

            try (JunifyDB db = open(dir, type)) {
                assertTrue(db.from(NeverCreated.class).list().isEmpty(),
                        type + ": a query against an unknown collection is an empty result");
                assertFalse(db.getCollectionNames().contains("r62_never_created"),
                        type + ": a read must not add anything to the catalog, found "
                                + db.getCollectionNames());
            }

            try (JunifyDB reopened = open(dir, type)) {
                assertFalse(reopened.getCollectionNames().contains("r62_never_created"),
                        type + ": the read must not become durable state either");
            }
        }
    }

    @Test
    @DisplayName("the in-memory engine lists a created collection but promises no durability")
    void inMemoryEngineListsCreatedCollectionsWithoutDurability(@TempDir Path tempDir) {
        Path dir = tempDir.resolve("memory");

        try (JunifyDB db = open(dir, StorageEngineType.IN_MEMORY)) {
            db.documentCollection("memory_only");
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
            db.documentCollection("usable_table");
        }

        try (JunifyDB reopened = open(dir, StorageEngineType.FILE)) {
            var collection = reopened.documentCollection("usable_table");
            collection.insert(Document.of("sku", "AFTER-RESTART"));
            assertEquals(1, collection.count());

            assertEquals(1, collection.findAll().size(),
                    "the collection created before the restart must accept writes afterwards, not "
                            + "be re-created as a different object");
        }
    }
}

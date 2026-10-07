package org.embeddedjnosql.db;

import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class FilePersistenceTest {

    private Path dataDir;
    private EmbedJNoSQL db;
    private DocumentCollection users;

    @BeforeEach
    void setUp() throws IOException {
        dataDir = Files.createTempDirectory("embeddedjnosql-test");
        db = EmbedJNoSQL.embed()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.FILE)
                .persistTo(dataDir.toString())
                .build();
        users = db.documentCollection("users");
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
        try {
            Files.walk(dataDir)
                    .sorted((a, b) -> -a.compareTo(b))
                    .forEach(p -> {
                        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                    });
        } catch (IOException ignored) {}
    }

    @Test
    void persistAndReload() throws IOException {
        users.insert(Document.of("name", "Alice").add("age", 30));
        db.close();

        db = EmbedJNoSQL.embed()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.FILE)
                .persistTo(dataDir.toString())
                .build();
        users = db.documentCollection("users");

        assertEquals(1, users.count());
        var found = users.findAll().get(0);
        assertEquals("Alice", found.get("name"));
        assertEquals(30, (int) found.get("age"));
    }

    @Test
    void multipleCollectionsPersist() {
        var orders = db.documentCollection("orders");
        users.insert(Document.of("name", "Alice"));
        orders.insert(Document.of("item", "Widget").add("qty", 5));

        db.close();

        db = EmbedJNoSQL.embed()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.FILE)
                .persistTo(dataDir.toString())
                .build();

        assertEquals(1, db.documentCollection("users").count());
        assertEquals(1, db.documentCollection("orders").count());
    }

    @Test
    void updatePersists() {
        var doc = users.insert(Document.of("name", "Alice").add("age", 30));
        doc.add("age", 31);
        users.update(doc);
        db.close();

        db = EmbedJNoSQL.embed()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.FILE)
                .persistTo(dataDir.toString())
                .build();
        users = db.documentCollection("users");

        var found = users.findById(doc.id());
        assertEquals(31, (int) found.get("age"));
    }

    @Test
    void deletePersists() {
        var doc = users.insert(Document.of("name", "Alice"));
        users.deleteById(doc.id());
        db.close();

        db = EmbedJNoSQL.embed()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.FILE)
                .persistTo(dataDir.toString())
                .build();
        users = db.documentCollection("users");

        assertEquals(0, users.count());
    }

    @Test
    void dataDirectoryCreated() {
        assertTrue(Files.exists(dataDir));
    }
}

package org.embeddedjnosql.db;

import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.storage.spi.LSMTreeEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LSMTreeEngineTest {

    private EmbedJNoSQL db;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("embeddedjnosql-lsm");
        db = EmbedJNoSQL.embed()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.LSM_TREE)
                .persistTo(tempDir.toString())
                .build();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (db != null && db.isOpen()) db.close();
        Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
    }

    @Test
    void testBasicOperations() {
        var users = db.documentCollection("users");
        var doc = users.insert(Document.of("name", "Alice").add("age", 30));
        
        assertNotNull(doc.id());
        assertEquals(1, users.count());
        
        var found = users.findById(doc.id());
        assertNotNull(found);
        assertEquals("Alice", found.get("name"));
        assertEquals(30, ((Number) found.get("age")).intValue());
    }

    @Test
    void testMultipleDocuments() {
        var users = db.documentCollection("users");
        
        users.insert(Document.of("name", "Alice").add("age", 30));
        users.insert(Document.of("name", "Bob").add("age", 25));
        users.insert(Document.of("name", "Charlie").add("age", 35));
        
        assertEquals(3, users.count());
        
        var all = users.findAll();
        assertEquals(3, all.size());
    }

    @Test
    void testUpdate() {
        var users = db.documentCollection("users");
        var doc = users.insert(Document.of("name", "Alice").add("age", 30));
        
        doc.add("age", 31);
        users.update(doc);
        
        var found = users.findById(doc.id());
        assertEquals(31, ((Number) found.get("age")).intValue());
    }

    @Test
    void testDelete() {
        var users = db.documentCollection("users");
        var doc = users.insert(Document.of("name", "Alice"));
        
        assertTrue(users.deleteById(doc.id()));
        assertEquals(0, users.count());
    }

    @Test
    void testPersistence() {
        var users = db.documentCollection("users");
        users.insert(Document.of("name", "Alice").add("age", 30));
        db.close();
        
        db = EmbedJNoSQL.embed()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.LSM_TREE)
                .persistTo(tempDir.toString())
                .build();
        users = db.documentCollection("users");
        
        assertEquals(1, users.count());
        assertEquals("Alice", users.findAll().get(0).get("name"));
    }

    @Test
    void testFlush() {
        var users = db.documentCollection("users");
        for (int i = 0; i < 100; i++) {
            users.insert(Document.of("name", "User" + i).add("index", i));
        }
        
        db.flush();
        
        assertEquals(100L, users.count());
    }

    @Test
    void testMemtableSize() {
        var engine = new LSMTreeEngine(tempDir, 100, 1024 * 1024);
        
        var users = db.documentCollection("users");
        for (int i = 0; i < 50; i++) {
            users.insert(Document.of("name", "User" + i).add("index", i));
        }
        
        assertEquals(50, users.count());
        
        engine.close();
    }

    @Test
    void testSSTableCreation() {
        var users = db.documentCollection("users");
        
        for (int i = 0; i < 200; i++) {
            users.insert(Document.of("name", "User" + i).add("index", i));
        }
        
        users.insert(Document.of("name", "Special").add("type", "important"));
        
        var result = users.findAll();
        assertTrue(result.size() >= 200);
        
        var special = users.find(org.embeddedjnosql.db.nosql.document.Query.eq("type", "important"));
        assertEquals(1, special.size());
    }
}

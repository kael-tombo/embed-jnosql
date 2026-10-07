package org.embeddedjnosql.db;

import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.storage.spi.BTreeEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class BTreeEngineTest {

    private BTreeEngine engine;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = java.nio.file.Files.createTempDirectory("btree_test");
        engine = new BTreeEngine(tempDir);
    }

    @AfterEach
    void tearDown() {
        engine.close();
        deleteDirectory(tempDir.toFile());
    }

    private void deleteDirectory(java.io.File dir) {
        if (dir.exists()) {
            java.io.File[] files = dir.listFiles();
            if (files != null) {
                for (java.io.File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            dir.delete();
        }
    }

    @Test
    void putAndGet() {
        engine.put("test", "key1", "value1");
        assertEquals("value1", engine.get("test", "key1"));
    }

    @Test
    void putAll() {
        Map<String, String> entries = Map.of(
                "key1", "value1",
                "key2", "value2",
                "key3", "value3"
        );
        engine.putAll("test", entries);
        assertEquals("value1", engine.get("test", "key1"));
        assertEquals("value2", engine.get("test", "key2"));
        assertEquals("value3", engine.get("test", "key3"));
    }

    @Test
    void delete() {
        engine.put("test", "key1", "value1");
        assertEquals("value1", engine.get("test", "key1"));
        engine.delete("test", "key1");
        assertNull(engine.get("test", "key1"));
    }

    @Test
    void exists() {
        assertFalse(engine.exists("test", "key1"));
        engine.put("test", "key1", "value1");
        assertTrue(engine.exists("test", "key1"));
    }

    @Test
    void scan() {
        engine.put("test", "k1", "v1");
        engine.put("test", "k2", "v2");
        engine.put("test", "k3", "v3");
        engine.put("other", "k1", "v1");
        
        List<String> results = engine.scan("test");
        assertEquals(3, results.size());
        assertTrue(results.contains("v1"));
        assertTrue(results.contains("v2"));
        assertTrue(results.contains("v3"));
    }

    @Test
    void keys() {
        engine.put("test", "k1", "v1");
        engine.put("test", "k2", "v2");
        engine.put("other", "k1", "v1");
        
        var keys = engine.keys("test");
        assertEquals(2, keys.size());
        assertTrue(keys.contains("k1"));
        assertTrue(keys.contains("k2"));
    }

    @Test
    void rangeScan() {
        engine.put("test", "a", "v_a");
        engine.put("test", "b", "v_b");
        engine.put("test", "c", "v_c");
        engine.put("test", "d", "v_d");
        
        List<String> results = engine.rangeScan("test", "b", "c");
        assertEquals(2, results.size());
    }

    @Test
    void prefixScan() {
        engine.put("test", "user:1", "{\"name\":\"Alice\"}");
        engine.put("test", "user:2", "{\"name\":\"Bob\"}");
        engine.put("test", "post:1", "{\"title\":\"Post1\"}");
        
        List<String> results = engine.prefixScan("test", "user:");
        assertEquals(2, results.size());
    }

    @Test
    void flush() {
        engine.put("test", "key1", "value1");
        engine.put("test", "key2", "value2");
        engine.flush();
        
        assertEquals("value1", engine.get("test", "key1"));
        assertEquals("value2", engine.get("test", "key2"));
    }

    @Test
    void persistence() {
        engine.put("test", "key1", "value1");
        engine.put("test", "key2", "value2");
        engine.close();
        
        engine = new BTreeEngine(tempDir);
        assertEquals("value1", engine.get("test", "key1"));
        assertEquals("value2", engine.get("test", "key2"));
    }
}

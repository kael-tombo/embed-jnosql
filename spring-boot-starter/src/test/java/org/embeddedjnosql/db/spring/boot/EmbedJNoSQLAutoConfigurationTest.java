package org.embeddedjnosql.db.spring.boot;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.document.Document;
import org.embeddedjnosql.db.nosql.document.Query;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test that verifies the Spring Boot auto-configuration wires
 * {@link EmbedJNoSQL} and {@link EmbedJNoSQLTemplate} correctly and that the
 * core document / key-value APIs work end-to-end inside a Spring context.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "embedjnosql.enabled=true",
        "embedjnosql.storage-engine=IN_MEMORY"
})
class EmbedJNoSQLAutoConfigurationTest {

    /** Minimal Spring Boot application class needed for the test context. */
    @SpringBootApplication
    static class TestApp {}

    @Autowired
    private EmbedJNoSQL db;

    @Autowired
    private EmbedJNoSQLTemplate template;

    // ── Bean wiring ────────────────────────────────────────────────────────

    @Test
    void embeddedjnosqlDBBeanIsPresent() {
        assertNotNull(db, "EmbedJNoSQL bean must be auto-configured");
        assertTrue(db.isOpen(), "Database must be open after startup");
    }

    @Test
    void templateBeanIsPresent() {
        assertNotNull(template, "EmbedJNoSQLTemplate bean must be auto-configured");
        assertNotNull(template.database(), "Template must expose the EmbedJNoSQL instance");
    }

    // ── Document API ───────────────────────────────────────────────────────

    @Test
    void documentInsertAndFindById() {
        var users = db.documentCollection("test_users");

        var doc = new Document();
        doc.id("u-1");
        doc.add("name", "Alice");
        doc.add("age", 30);
        users.insert(doc);

        var loaded = users.findById("u-1");
        assertNotNull(loaded);
        assertEquals("Alice", loaded.<String>get("name"));
        assertEquals(30, loaded.<Integer>get("age"));
    }

    @Test
    void documentQueryWithPredicate() {
        var col = db.documentCollection("test_query");

        for (int i = 0; i < 5; i++) {
            var d = new Document();
            d.add("score", i);
            col.insert(d);
        }

        List<Document> high = col.find(Query.gt("score", 2));
        assertEquals(2, high.size(), "Should find score=3 and score=4");
    }

    @Test
    void documentUpdateAndDelete() {
        var col = db.documentCollection("test_update");

        var doc = new Document();
        doc.id("upd-1");
        doc.add("status", "pending");
        col.insert(doc);

        doc.add("status", "done");
        col.update(doc);
        assertEquals("done", col.findById("upd-1").<String>get("status"));

        assertTrue(col.deleteById("upd-1"));
        assertNull(col.findById("upd-1"));
    }

    // ── Template convenience API ───────────────────────────────────────────

    @Test
    void templateDocumentsAccessor() {
        var col = template.documents("template_col");
        assertNotNull(col);

        var d = new Document();
        d.id("t-1");
        d.add("x", "y");
        col.insert(d);

        assertNotNull(col.findById("t-1"));
    }

    // ── Key-Value API ──────────────────────────────────────────────────────

    @Test
    void keyValuePutAndGet() {
        var kv = db.keyValueBucket("test_kv");

        kv.put("feature:signup", "enabled");
        assertEquals("enabled", kv.get("feature:signup"));

        kv.delete("feature:signup");
        assertNull(kv.get("feature:signup"));
    }

    @Test
    void keyValueIncrementDecrement() {
        var kv = db.keyValueBucket("test_counter");
        kv.put("hits", "0");

        assertEquals(1L, kv.increment("hits"));
        assertEquals(2L, kv.increment("hits"));
        assertEquals(1L, kv.decrement("hits"));
    }

    @Test
    void templateKeyValuesAccessor() {
        var kv = template.keyValues("template_kv");
        assertNotNull(kv);
        kv.put("hello", "world");
        assertEquals("world", kv.get("hello"));
    }

    // ── Column-Family API ──────────────────────────────────────────────────

    @Test
    void columnFamilyPutAndGet() {
        var cf = db.columnFamily("test_cf");
        cf.put("row-1", "name", "Bob");
        cf.put("row-1", "city", "NYC");

        assertEquals("Bob", cf.get("row-1", "name"));
        assertEquals("NYC", cf.get("row-1", "city"));
    }

    @Test
    void templateColumnsAccessor() {
        var cf = template.columns("template_cf");
        assertNotNull(cf);
        cf.put("r", "k", "v");
        assertEquals("v", cf.get("r", "k"));
    }

    // ── Properties binding ─────────────────────────────────────────────────

    @Test
    void propertiesDefaultToInMemory(@Autowired EmbedJNoSQLProperties props) {
        // The test sets storage-engine=IN_MEMORY explicitly, verify it binds
        assertEquals(
                org.embeddedjnosql.db.config.EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY,
                props.getStorageEngine()
        );
    }
}

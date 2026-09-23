package org.junify.db;

import org.junify.db.config.JunifyDBConfig;
import org.junify.db.storage.spi.LSMTreeEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for R-65: on {@code LSM_TREE}, a key must be read once, at its newest
 * version, across the memtable and every SSTable.
 *
 * <p>Measured before the fix: one row written, flushed, and read after a restart came back
 * <b>twice</b> from {@code scan()} while {@code keys()} (and therefore {@code count()})
 * reported one. Two causes, both in the same layered-resolution step:</p>
 * <ol>
 *   <li>{@code scan()} concatenated memory and disk instead of resolving per key, so a key
 *       present in both the memtable and an SSTable was emitted twice.</li>
 *   <li>{@code recoverFromWal()} replays every historical {@code PUT}/{@code DELETE} into the
 *       memtable (the engine never truncates its log), which puts flushed keys back into the
 *       memtable on every startup — so the duplication appeared exactly after a restart.</li>
 * </ol>
 * <p>The same gap meant a tombstone in the memtable did not shadow the same key's value in an
 * SSTable, so deleting a row and restarting brought it back. These tests pin both.</p>
 */
@DisplayName("LSM_TREE reads resolve one newest version per key (R-65)")
class LSMReadResolutionTest {

    /** Runs {@code body} against a fresh engine over {@code dir}, then closes it. */
    private void withEngine(Path dir, Consumer<LSMTreeEngine> body) {
        var engine = new LSMTreeEngine(dir);
        try {
            body.accept(engine);
        } finally {
            engine.close();
        }
    }

    @Test
    @DisplayName("a record is read once after a restart, not once per layer that holds it")
    void recordIsReadOnceAfterRestart(@TempDir Path dir) {
        withEngine(dir, engine -> {
            engine.put("orders", "k1", "{\"id\":\"k1\",\"sku\":\"A\"}");
            assertEquals(1, engine.scan("orders").size(), "one record in the memtable");
        });

        withEngine(dir, reopened -> {
            assertEquals(1, reopened.scan("orders").size(),
                    "one written record must be read once after a restart, but scan() returned "
                            + reopened.scan("orders"));
            assertEquals(1, reopened.keys("orders").size(),
                    "keys() must agree with scan() about how many records exist");
        });
    }

    @Test
    @DisplayName("many records survive a restart exactly once each")
    void manyRecordsSurviveExactlyOnce(@TempDir Path dir) {
        withEngine(dir, engine -> {
            for (int i = 0; i < 25; i++) {
                engine.put("items", "k" + i, "{\"id\":\"k" + i + "\"}");
            }
        });

        withEngine(dir, reopened -> {
            assertEquals(25, reopened.scan("items").size(),
                    "25 records must come back as 25, not 50");
            assertEquals(25, reopened.keys("items").size());
        });
    }

    @Test
    @DisplayName("the newest value of a key wins, with no duplicate of the older value")
    void newestValueWinsWithoutDuplication(@TempDir Path dir) {
        withEngine(dir, engine -> {
            engine.put("cfg", "theme", "{\"v\":1}");
            engine.flush();                          // the old value reaches an SSTable
            engine.put("cfg", "theme", "{\"v\":2}"); // the new value stays in the memtable
        });

        withEngine(dir, reopened -> {
            var values = reopened.scan("cfg");
            assertEquals(1, values.size(), "an updated key is one record, found: " + values);
            assertTrue(values.get(0).contains("\"v\":2"),
                    "the newest value must win across layers, found: " + values);
            assertTrue(reopened.get("cfg", "theme").contains("\"v\":2"));
        });
    }

    @Test
    @DisplayName("a deleted record stays deleted after a restart")
    void deletedRecordStaysDeletedAfterRestart(@TempDir Path dir) {
        withEngine(dir, engine -> {
            engine.put("products", "keep", "{\"id\":\"keep\"}");
            engine.put("products", "gone", "{\"id\":\"gone\"}");
            engine.flush();                        // both reach an SSTable
            engine.delete("products", "gone");     // tombstone only in the memtable
        });

        withEngine(dir, reopened -> {
            assertEquals(1, reopened.scan("products").size(),
                    "a deleted record must not reappear from disk after a restart: "
                            + reopened.scan("products"));
            assertEquals(1, reopened.keys("products").size(),
                    "keys() must not resurrect a deleted record either");
            assertFalse(reopened.keys("products").contains("gone"),
                    "the deleted key must be absent, found: " + reopened.keys("products"));
            assertEquals("{\"id\":\"keep\"}", reopened.get("products", "keep"));
            assertNull(reopened.get("products", "gone"));
        });
    }

    @Test
    @DisplayName("a tombstone in the newest SSTable decides get(), not just scan()")
    void tombstoneInNewestSstableDecidesGet(@TempDir Path dir) {
        withEngine(dir, engine -> {
            engine.put("products", "gone", "{\"id\":\"gone\"}");
            engine.flush();                        // the value reaches an older SSTable
            engine.delete("products", "gone");     // tombstone in the memtable
            engine.flush();                        // and now in a newer SSTable, log truncated
        });

        withEngine(dir, reopened -> {
            // R-73: nothing but an SSTable holds the tombstone here, so the newest table must
            // decide. SSTable.get() reports a tombstone as null — indistinguishable from "this
            // table does not have the key" — which let an older table answer with the dead value.
            assertNull(reopened.get("products", "gone"),
                    "get() must answer from the newest version, which is a tombstone");
            assertTrue(reopened.scan("products").isEmpty(), "scan() must agree with get()");
            assertTrue(reopened.keys("products").isEmpty(), "keys() must agree with get()");
            assertFalse(reopened.exists("products", "gone"), "exists() must agree with get()");
        });
    }

    @Test
    @DisplayName("SQL and the document API see one row after a restart on LSM_TREE")
    void sqlAndDocumentReadsAgreeAfterRestart(@TempDir Path dir) {
        try (JunifyDB db = JunifyDB.embed()
                .storageEngine(JunifyDBConfig.StorageEngineType.LSM_TREE)
                .persistTo(dir.toString())
                .build()) {
            db.sql("INSERT INTO invoices (id, sku) VALUES ('inv-1', 'SKU-1')");
        }

        try (JunifyDB reopened = JunifyDB.embed()
                .storageEngine(JunifyDBConfig.StorageEngineType.LSM_TREE)
                .persistTo(dir.toString())
                .build()) {
            assertEquals(1, reopened.sql("SELECT * FROM invoices").size(),
                    "SELECT must return the row once");
            assertEquals(1, reopened.documentCollection("invoices").count(),
                    "count() and SELECT must agree — they disagreed before the fix");
            assertEquals(1, reopened.documentCollection("invoices").findAll().size(),
                    "findAll() must agree with count()");
        }
    }

    @Test
    @DisplayName("a tombstone written to disk hides an older value for the same key")
    void diskLevelTombstoneHidesAnOlderValue(@TempDir Path dir) {
        withEngine(dir, engine -> {
            engine.put("logs", "entry", "{\"n\":1}");
            engine.flush();                        // value lives in SSTable 1
            engine.delete("logs", "entry");
            engine.flush();                        // tombstone lives in SSTable 2
        });

        withEngine(dir, reopened -> {
            assertTrue(reopened.scan("logs").isEmpty(),
                    "the tombstone is newer than the value, so nothing may be read: "
                            + reopened.scan("logs"));
            assertTrue(reopened.keys("logs").isEmpty(),
                    "keys() must honour a tombstone that reached disk");
        });
    }
}

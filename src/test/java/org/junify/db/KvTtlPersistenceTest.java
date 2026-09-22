package org.junify.db;

import org.junify.db.config.JunifyDBConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verification coverage for the KV half of doc 19, which document TTL (R-33) showed
 * cannot be assumed: expirations must be honored on read <em>and</em> survive a
 * restart on a durable engine (`meta_store` persistence).
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class KvTtlPersistenceTest {

    @Test
    void kvTtlExpiresOnRead() throws Exception {
        try (var db = JunifyDB.embed().build()) {
            var kv = db.keyValueBucket("cache-inmemory");
            kv.put("session", "abc", Duration.ofSeconds(1));

            assertEquals("abc", kv.get("session"), "key must be readable before its TTL elapses");

            Thread.sleep(1200);

            assertNull(kv.get("session"), "expired key must read as null");
            assertFalse(kv.keys().contains("session"), "expired key must not be listed");
        }
    }

    @Test
    void kvTtlSurvivesRestartOnDurableEngine() throws Exception {
        Path dir = Files.createTempDirectory("junify-kv-ttl");

        try (var db = JunifyDB.create(JunifyDBConfig.builder()
                .storageEngine(JunifyDBConfig.StorageEngineType.FILE)
                .persistTo(dir.toString())
                .buildConfig())) {
            db.keyValueBucket("cache-durable").put("session", "abc", Duration.ofSeconds(2));
            assertEquals("abc", db.keyValueBucket("cache-durable").get("session"));
        }

        try (var db = JunifyDB.create(JunifyDBConfig.builder()
                .storageEngine(JunifyDBConfig.StorageEngineType.FILE)
                .persistTo(dir.toString())
                .buildConfig())) {
            assertEquals("abc", db.keyValueBucket("cache-durable").get("session"),
                    "an unexpired key must survive a restart");

            Thread.sleep(2200);

            assertNull(db.keyValueBucket("cache-durable").get("session"),
                    "the persisted expiration must still be honored after a restart "
                            + "(it is only enforced if meta_store expirations were reloaded)");
        }
    }

    @Test
    void kvWithoutTtlIsNotTreatedAsExpired() throws Exception {
        try (var db = JunifyDB.embed().build()) {
            var kv = db.keyValueBucket("cache-plain");
            kv.put("permanent", "value");

            assertEquals("value", kv.get("permanent"));
            Thread.sleep(1100);
            assertEquals("value", kv.get("permanent"), "a key written without TTL must never expire");
            assertTrue(kv.keys().contains("permanent"));
        }
    }
}

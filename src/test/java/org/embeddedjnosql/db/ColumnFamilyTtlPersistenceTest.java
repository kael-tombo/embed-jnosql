package org.embeddedjnosql.db;

import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.embeddedjnosql.db.nosql.column.ColumnFamily;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verification coverage for column-level TTL — the last TTL surface left unchecked after
 * document TTL turned out to be broken (R-33). Confirms expiry on read, TTL metadata
 * surviving a restart on a durable engine, and that plain columns never expire.
 */
@Timeout(value = 60, unit = TimeUnit.SECONDS)
class ColumnFamilyTtlPersistenceTest {

    @Test
    void columnTtlExpiresOnReadAndMetadataIsReadable() throws Exception {
        try (var db = EmbedJNoSQL.embed().build()) {
            ColumnFamily cf = db.columnFamily("cf-inmemory");
            cf.put("row1", "longlived", "keep", 600);
            cf.put("row1", "shortlived", "expire", 1);

            assertNotNull(cf.getColumnData("row1", "longlived").getExpiresAt(),
                    "a TTL'd column must expose its expiry");
            assertFalse(cf.getColumnData("row1", "longlived").isExpired());

            Thread.sleep(1200);

            assertNull(cf.get("row1", "shortlived"), "expired column must read as null");
            assertEquals("keep", cf.get("row1", "longlived"), "the long-lived column must remain");
        }
    }

    @Test
    void columnTtlSurvivesRestartOnDurableEngine() throws Exception {
        Path dir = Files.createTempDirectory("embeddedjnosql-cf-ttl");

        try (var db = EmbedJNoSQL.create(EmbedJNoSQLConfig.builder()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.FILE)
                .persistTo(dir.toString())
                .buildConfig())) {
            ColumnFamily cf = db.columnFamily("inventory");
            cf.put("item-1", "longlived", "keep", 600);
            cf.put("item-1", "shortlived", "expire", 2);
            cf.put("item-1", "plain", "no-ttl", null);
        }

        try (var db = EmbedJNoSQL.create(EmbedJNoSQLConfig.builder()
                .storageEngine(EmbedJNoSQLConfig.StorageEngineType.FILE)
                .persistTo(dir.toString())
                .buildConfig())) {
            ColumnFamily cf = db.columnFamily("inventory");

            assertNotNull(cf.getColumnData("item-1", "longlived"), "column must survive a restart");
            assertNotNull(cf.getColumnData("item-1", "longlived").getExpiresAt(),
                    "TTL metadata must survive a restart, not just the value");
            assertFalse(cf.getColumnData("item-1", "longlived").isExpired());

            assertEquals("no-ttl", cf.get("item-1", "plain"),
                    "a column written without TTL must never expire");

            Thread.sleep(2200);

            assertNull(cf.get("item-1", "shortlived"),
                    "the persisted column TTL must still be honored after a restart");
            assertEquals("keep", cf.get("item-1", "longlived"));
        }
    }
}

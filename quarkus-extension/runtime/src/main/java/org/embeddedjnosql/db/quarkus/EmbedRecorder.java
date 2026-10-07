package org.embeddedjnosql.db.quarkus;

import io.quarkus.runtime.RuntimeValue;
import io.quarkus.runtime.annotations.Recorder;
import org.embeddedjnosql.db.EmbedJNoSQL;

/**
 * Quarkus runtime recorder for EmbedJNoSQL.
 * Manages database initialization and clean shutdown lifecycle during runtime init.
 */
@Recorder
public class EmbedRecorder {

    public RuntimeValue<EmbedJNoSQL> createDatabase(EmbedConfig config) {
        var db = EmbedJNoSQL.create(
                EmbedJNoSQL.embed()
                        .storageEngine(config.getEngine())
                        .persistTo(config.getDataDir())
                        .autoFlush(config.isAutoFlush())
                        .flushIntervalMs(config.getFlushIntervalMs())
                        .buildConfig()
        );
        return new RuntimeValue<>(db);
    }

    public void stopDatabase(RuntimeValue<EmbedJNoSQL> dbValue) {
        try {
            var db = dbValue.getValue();
            if (db != null && db.isOpen()) {
                db.close();
            }
        } catch (Exception e) {
            System.err.println("[EmbedJNoSQL] Error closing database: " + e.getMessage());
        }
    }
}

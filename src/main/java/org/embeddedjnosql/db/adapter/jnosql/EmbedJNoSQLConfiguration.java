package org.embeddedjnosql.db.adapter.jnosql;

import org.eclipse.jnosql.communication.Settings;
import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;

/**
 * Configuration bridge allowing EmbedJNoSQL instances to be created from
 * Eclipse JNoSQL {@link Settings}.
 */
public class EmbedJNoSQLConfiguration {

    public static final String ENGINE_PROP = "jnosql.embed.engine";
    public static final String DATA_DIR_PROP = "jnosql.embed.data.dir";
    public static final String AUTO_FLUSH_PROP = "jnosql.embed.auto.flush";
    public static final String FLUSH_INTERVAL_PROP = "jnosql.embed.flush.interval";
    public static final String QUERY_CACHE_PROP = "jnosql.embed.query.cache";

    public static EmbedJNoSQL create(Settings settings) {
        var builder = EmbedJNoSQL.embed();
        if (settings != null) {
            String engineName = settings.get(ENGINE_PROP, String.class)
                    .or(() -> settings.get("jnosql.engine", String.class))
                    .orElse("IN_MEMORY");
            try {
                builder.storageEngine(EmbedJNoSQLConfig.StorageEngineType.valueOf(engineName.toUpperCase()));
            } catch (Exception ignored) {
                builder.storageEngine(EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY);
            }

            settings.get(DATA_DIR_PROP, String.class)
                    .or(() -> settings.get("jnosql.data.dir", String.class))
                    .ifPresent(builder::dataDir);

            settings.get(AUTO_FLUSH_PROP, Boolean.class).ifPresent(builder::autoFlush);
            settings.get(FLUSH_INTERVAL_PROP, Number.class).ifPresent(n -> builder.flushIntervalMs(n.intValue()));
            settings.get(QUERY_CACHE_PROP, Boolean.class).ifPresent(builder::queryCacheEnabled);
        }
        return builder.build();
    }
}

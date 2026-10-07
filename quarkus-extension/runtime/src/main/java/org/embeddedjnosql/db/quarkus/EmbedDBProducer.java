package org.embeddedjnosql.db.quarkus;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.core.event.EventBus;
import org.embeddedjnosql.db.core.metrics.DatabaseMetrics;
import org.embeddedjnosql.db.nosql.column.ColumnFamily;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.kv.HashBucket;
import org.embeddedjnosql.db.nosql.kv.KeyValueBucket;
import org.embeddedjnosql.db.nosql.kv.ListBucket;
import org.embeddedjnosql.db.nosql.kv.SetBucket;

/**
 * CDI producer for EmbedJNoSQL beans in a Quarkus application.
 *
 * <p>Produces a singleton {@link EmbedJNoSQL} instance configured from
 * {@link JembedConfig}, plus convenience beans for document collections,
 * key-value buckets, Redis-style data structures, column families, event bus,
 * and database metrics.
 *
 * <p>All beans are marked {@code @DefaultBean} so applications can override
 * them with their own {@code @Produces} methods.
 */
@ApplicationScoped
public class EmbedDBProducer {

    @Inject
    JembedConfig config;

    @Produces
    @Singleton
    @DefaultBean
    public EmbedJNoSQL createDatabase() {
        var builder = EmbedJNoSQL.embed()
                .storageEngine(config.getEngine())
                .persistTo(config.getDataDir())
                .autoFlush(config.isAutoFlush())
                .flushIntervalMs(config.getFlushIntervalMs());

        if (config.console() != null) {
            builder.console(org.embeddedjnosql.db.config.ConsoleConfig.builder()
                    .enabled(config.console().enabled())
                    .port(config.console().port())
                    .contextPath(config.console().path())
                    .intelligentPort(config.console().intelligentPort())
                    .build());
        }

        if (config.security() != null) {
            var secBuilder = org.embeddedjnosql.db.config.SecurityConfig.builder()
                    .authEnabled(config.security().enabled())
                    .adminUsername(config.security().adminUsername())
                    .corsEnabled(config.security().corsEnabled());
            config.security().apiKey().ifPresent(secBuilder::apiKey);
            config.security().adminPassword().ifPresent(secBuilder::adminPassword);
            builder.security(secBuilder.build());
        }

        return EmbedJNoSQL.create(builder.buildConfig());
    }

    @Produces
    @Singleton
    @DefaultBean
    public org.embeddedjnosql.db.console.http.EmbedJNoSQLServer consoleServer(EmbedJNoSQL db) {
        return db.consoleServer();
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    public DocumentCollection defaultDocumentCollection(EmbedJNoSQL db) {
        return db.documentCollection("default");
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    public KeyValueBucket defaultKeyValueBucket(EmbedJNoSQL db) {
        return db.keyValueBucket("default");
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    public ListBucket defaultListBucket(EmbedJNoSQL db) {
        return db.listBucket("default");
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    public SetBucket defaultSetBucket(EmbedJNoSQL db) {
        return db.setBucket("default");
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    public HashBucket defaultHashBucket(EmbedJNoSQL db) {
        return db.hashBucket("default");
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    public ColumnFamily defaultColumnFamily(EmbedJNoSQL db) {
        return db.columnFamily("default");
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    public EventBus eventBus(EmbedJNoSQL db) {
        return db.eventBus();
    }

    @Produces
    @ApplicationScoped
    @DefaultBean
    public DatabaseMetrics databaseMetrics(EmbedJNoSQL db) {
        return db.metrics();
    }
}

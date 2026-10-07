package org.embeddedjnosql.db.adapter.jnosql;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.embeddedjnosql.db.transaction.mvcc.MVCCManager;

/**
 * CDI Producer for core EmbedJNoSQL components.
 * Produces EmbedJNoSQL, MVCCManager, and adapter beans for injection.
 */
public class EmbedDBProducer {

    private EmbedJNoSQL db;

    @Produces
    @ApplicationScoped
    public EmbedJNoSQL produceEmbedJNoSQL() {
        if (db == null) {
            db = EmbedJNoSQLConfig.builder()
                    .storageEngine(EmbedJNoSQLConfig.StorageEngineType.IN_MEMORY)
                    .autoFlush(true)
                    .build();
        }
        return db;
    }

    public void close(@Disposes EmbedJNoSQL db) {
        if (db.isOpen()) db.close();
    }

    @Produces
    @ApplicationScoped
    public MVCCManager produceMVCCManager(EmbedJNoSQL db) {
        return db.mvcc();
    }

    @Produces
    @ApplicationScoped
    public EclipseDocumentTemplate produceDocumentTemplate(EmbedJNoSQL db) {
        return EclipseDocumentTemplate.of(db.documentCollection("_default"));
    }

    @Produces
    @ApplicationScoped
    public org.embeddedjnosql.db.nosql.kv.KeyValueBucket produceKeyValueBucket(EmbedJNoSQL db) {
        return db.keyValueBucket("_default");
    }
}

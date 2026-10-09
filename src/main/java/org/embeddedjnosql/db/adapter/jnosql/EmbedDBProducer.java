package org.embeddedjnosql.db.adapter.jnosql;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Default;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Produces;
import org.eclipse.jnosql.mapping.Database;
import org.eclipse.jnosql.mapping.DatabaseType;
import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.config.EmbedJNoSQLConfig;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.kv.KeyValueBucket;
import org.embeddedjnosql.db.transaction.mvcc.MVCCManager;

/**
 * CDI Producer for core EmbedJNoSQL components and Eclipse JNoSQL integration.
 * Produces EmbedJNoSQL, MVCCManager, EclipseDocumentTemplate, and KeyValueBucket,
 * qualified with {@link Default} and {@link Database}.
 */
public class EmbedDBProducer {

    private EmbedJNoSQL db;

    @Produces
    @ApplicationScoped
    @Default
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
    @Default
    public EclipseDocumentTemplate produceDocumentTemplate(EmbedJNoSQL db) {
        return EclipseDocumentTemplate.of(db.documentCollection("_default"));
    }

    @Produces
    @ApplicationScoped
    @Database(DatabaseType.DOCUMENT)
    public EclipseDocumentTemplate produceDocumentTemplateQualified(EmbedJNoSQL db) {
        return EclipseDocumentTemplate.of(db.documentCollection("_default"));
    }

    @Produces
    @ApplicationScoped
    @Database(DatabaseType.DOCUMENT)
    public DocumentCollection produceDocumentCollectionQualified(EmbedJNoSQL db) {
        return db.documentCollection("_default");
    }

    @Produces
    @ApplicationScoped
    @Database(DatabaseType.DOCUMENT)
    public jakarta.nosql.Template produceJakartaTemplateQualified(EmbedJNoSQL db) {
        return EclipseDocumentTemplate.of(db.documentCollection("_default"));
    }

    @Produces
    @ApplicationScoped
    @Default
    public KeyValueBucket produceKeyValueBucket(EmbedJNoSQL db) {
        return db.keyValueBucket("_default");
    }

    @Produces
    @ApplicationScoped
    @Database(DatabaseType.KEY_VALUE)
    public KeyValueBucket produceKeyValueBucketQualified(EmbedJNoSQL db) {
        return db.keyValueBucket("_default");
    }
}

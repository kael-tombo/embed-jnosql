package org.embeddedjnosql.db.spring.boot;

import org.embeddedjnosql.db.EmbedJNoSQL;
import org.embeddedjnosql.db.nosql.column.ColumnFamily;
import org.embeddedjnosql.db.nosql.document.DocumentCollection;
import org.embeddedjnosql.db.nosql.kv.KeyValueBucket;

public class EmbedJNoSQLTemplate {

    private final EmbedJNoSQL db;

    public EmbedJNoSQLTemplate(EmbedJNoSQL db) {
        this.db = db;
    }

    public EmbedJNoSQL database() {
        return db;
    }

    public DocumentCollection documents(String collection) {
        return db.documentCollection(collection);
    }

    public KeyValueBucket keyValues(String bucket) {
        return db.keyValueBucket(bucket);
    }

    public ColumnFamily columns(String family) {
        return db.columnFamily(family);
    }
}

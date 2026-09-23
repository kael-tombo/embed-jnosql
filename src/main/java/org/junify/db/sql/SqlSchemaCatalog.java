package org.junify.db.sql;

import org.junify.db.JunifyDB;
import org.junify.db.nosql.document.Document;
import org.junify.db.nosql.document.DocumentCollection;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Durable store for {@link SqlTableSchema} metadata.
 *
 * <p>Schemas live in the reserved collection {@link SqlTableSchema#RESERVED_COLLECTION}, one
 * document per constrained table, so they inherit the same write-ahead durability as any other
 * data: a constrained table's rules survive a restart exactly as its rows do (verified by a
 * restart test that recreates the table's constraints after a cold start).</p>
 *
 * <p>The reserved collection is created <b>lazily</b> — only when a schema is first saved — and
 * a read never creates it (the read path checks {@code getCollectionNames()} first). This keeps
 * schemaless databases byte-identical to before: no reserved collection appears, and
 * {@code getCollectionNames()} is unchanged unless the user actually declared constraints.</p>
 */
public final class SqlSchemaCatalog {

    private final JunifyDB db;
    private final Map<String, SqlTableSchema> schemas = new LinkedHashMap<>();
    private boolean loaded = false;

    public SqlSchemaCatalog(JunifyDB db) {
        this.db = db;
    }

    /** Returns the schema for {@code table}, or {@code null} when it is schemaless/unknown. */
    public synchronized SqlTableSchema get(String table) {
        if (table == null) return null;
        ensureLoaded();
        return schemas.get(table.toLowerCase());
    }

    /** Persists {@code schema} and caches it. The reserved collection is created here. */
    public synchronized void save(SqlTableSchema schema) {
        ensureLoaded();
        schemas.put(schema.getTableName().toLowerCase(), schema);
        DocumentCollection reserved = db.documentCollection(SqlTableSchema.RESERVED_COLLECTION);
        reserved.insert(schema.toDocument());
    }

    /** Removes a schema (used by DROP TABLE). */
    public synchronized void drop(String table) {
        if (table == null) return;
        ensureLoaded();
        SqlTableSchema removed = schemas.remove(table.toLowerCase());
        if (removed == null) return;
        if (db.getCollectionNames().contains(SqlTableSchema.RESERVED_COLLECTION)) {
            db.documentCollection(SqlTableSchema.RESERVED_COLLECTION).deleteById(removed.getTableName());
        }
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        // Read-only load: never create the reserved collection just to look for schemas.
        if (!db.getCollectionNames().contains(SqlTableSchema.RESERVED_COLLECTION)) {
            return;
        }
        for (Document doc : db.documentCollection(SqlTableSchema.RESERVED_COLLECTION).findAll()) {
            SqlTableSchema schema = SqlTableSchema.fromDocument(doc);
            if (schema.getTableName() != null) {
                schemas.put(schema.getTableName().toLowerCase(), schema);
            }
        }
    }
}

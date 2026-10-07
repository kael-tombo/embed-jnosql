package org.junify.db.core.migration;

import org.junify.db.Experimental;
import org.junify.db.JunifyDB;
import org.junify.db.nosql.document.Document;
import org.junify.db.nosql.document.DocumentCollection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * EXPERIMENTAL: collection-scoped schema migration runner (add/remove/rename/transform field
 * steps plus custom callbacks), with applied versions tracked in a {@code _schema_versions}
 * collection.
 *
 * <p>Labeled by product decision (release review round 3): this class has no production caller,
 * no functional test coverage, and no documentation beyond this notice. It ships in the jar, but
 * the API may change or be removed in a future release without notice. Review each step's effect
 * on real data before relying on it.</p>
 */
@Experimental("no production caller; no functional coverage; API may change or be removed without notice")
public class MigrationManager {

    private final JunifyDB db;
    private final Map<String, Integer> schemaVersions;
    private final List<Migration> migrations;

    public MigrationManager(JunifyDB db) {
        this.db = db;
        this.schemaVersions = new ConcurrentHashMap<>();
        this.migrations = new ArrayList<>();
        initSchemaVersion();
    }

    private void initSchemaVersion() {
        try {
            var versions = db.documentCollection("_schema_versions");
            for (var doc : versions.findAll()) {
                schemaVersions.put(doc.get("collection"), doc.get("version"));
            }
        } catch (Exception ignored) {
        }
    }

    public void registerMigration(Migration migration) {
        migrations.add(migration);
        migrations.sort(Comparator.comparingInt(m -> m.version()));
    }

    public MigrationResult migrate(String collection) {
        int currentVersion = schemaVersions.getOrDefault(collection, 0);
        final int targetVersion = currentVersion;
        var applicable = migrations.stream()
                .filter(m -> m.collection().equals(collection))
                .filter(m -> m.version() > targetVersion)
                .toList();

        if (applicable.isEmpty()) {
            return MigrationResult.noop(collection, currentVersion);
        }

        var results = new ArrayList<String>();
        for (var migration : applicable) {
            try {
                executeMigration(migration, collection);
                currentVersion = migration.version();
                schemaVersions.put(collection, currentVersion);
                results.add("Applied v" + migration.version() + ": " + migration.description());
            } catch (Exception e) {
                return MigrationResult.failed(collection, currentVersion, e.getMessage());
            }
        }

        return MigrationResult.success(collection, currentVersion, results);
    }

    public MigrationResult migrateAll() {
        var results = new ArrayList<String>();
        for (var collection : schemaVersions.keySet()) {
            var result = migrate(collection);
            if (!result.success()) {
                return result;
            }
            results.add(result.toString());
        }
        return MigrationResult.success("_all", 0, results);
    }

    private void executeMigration(Migration migration, String collection) {
        var coll = db.documentCollection(collection);
        switch (migration.type()) {
            case ADD_FIELD -> addField(coll, migration);
            case REMOVE_FIELD -> removeField(coll, migration);
            case RENAME_FIELD -> renameField(coll, migration);
            case TRANSFORM_FIELD -> transformField(coll, migration);
            case CUSTOM -> migration.migrate(coll);
        }
    }

    private void addField(DocumentCollection coll, Migration m) {
        for (var doc : coll.findAll()) {
            if (!doc.has(m.fieldName())) {
                doc.add(m.fieldName(), m.defaultValue());
                coll.update(doc);
            }
        }
    }

    private void removeField(DocumentCollection coll, Migration m) {
        for (var doc : coll.findAll()) {
            if (doc.has(m.fieldName())) {
                doc.remove(m.fieldName());
                coll.update(doc);
            }
        }
    }

    private void renameField(DocumentCollection coll, Migration m) {
        for (var doc : coll.findAll()) {
            if (doc.has(m.fieldName())) {
                var value = doc.getRaw(m.fieldName());
                doc.remove(m.fieldName());
                doc.add((String) m.defaultValue(), value);
                coll.update(doc);
            }
        }
    }

    private void transformField(DocumentCollection coll, Migration m) {
        for (var doc : coll.findAll()) {
            if (doc.has(m.fieldName())) {
                var transformed = m.transform(doc.getRaw(m.fieldName()));
                doc.add(m.fieldName(), transformed);
                coll.update(doc);
            }
        }
    }

    public int getVersion(String collection) {
        return schemaVersions.getOrDefault(collection, 0);
    }

    public interface Migration {
        String collection();
        int version();
        String description();
        Type type();
        String fieldName();
        Object defaultValue();

        default Object transform(Object input) {
            return input;
        }

        default void migrate(DocumentCollection collection) {
        }

        enum Type {
            ADD_FIELD,
            REMOVE_FIELD,
            RENAME_FIELD,
            TRANSFORM_FIELD,
            CUSTOM
        }
    }

    public static class MigrationResult {
        private final String collection;
        private final int targetVersion;
        private final boolean success;
        private final List<String> messages;

        private MigrationResult(String collection, int targetVersion, boolean success, List<String> messages) {
            this.collection = collection;
            this.targetVersion = targetVersion;
            this.success = success;
            this.messages = messages;
        }

        public static MigrationResult success(String collection, int version, List<String> messages) {
            return new MigrationResult(collection, version, true, messages);
        }

        public static MigrationResult failed(String collection, int version, String error) {
            return new MigrationResult(collection, version, false, List.of(error));
        }

        public static MigrationResult noop(String collection, int version) {
            return new MigrationResult(collection, version, true, List.of("No migrations needed"));
        }

        public boolean success() { return success; }
        public List<String> getMessages() { return messages; }

        @Override
        public String toString() {
            return String.format("MigrationResult{collection=%s, version=%d, success=%s, messages=%s}",
                collection, targetVersion, success, messages);
        }
    }

    public static class MigrationBuilder {
        private String collection;
        private int version;
        private String description;
        private Migration.Type type = Migration.Type.CUSTOM;
        private String fieldName;
        private Object defaultValue;
        private Function<Object, Object> transform = Function.identity();

        public MigrationBuilder collection(String collection) {
            this.collection = collection;
            return this;
        }

        public MigrationBuilder version(int version) {
            this.version = version;
            return this;
        }

        public MigrationBuilder description(String description) {
            this.description = description;
            return this;
        }

        public MigrationBuilder type(Migration.Type type) {
            this.type = type;
            return this;
        }

        public MigrationBuilder fieldName(String fieldName) {
            this.fieldName = fieldName;
            return this;
        }

        public MigrationBuilder defaultValue(Object defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public MigrationBuilder transform(Function<Object, Object> transform) {
            this.transform = transform;
            return this;
        }

        public Migration build() {
            final var transformer = this.transform;
            return new Migration() {
                @Override public String collection() { return collection; }
                @Override public int version() { return version; }
                @Override public String description() { return description; }
                @Override public Type type() { return type; }
                @Override public String fieldName() { return fieldName; }
                @Override public Object defaultValue() { return defaultValue; }
                @Override public Object transform(Object input) { return transformer.apply(input); }
            };
        }
    }
}

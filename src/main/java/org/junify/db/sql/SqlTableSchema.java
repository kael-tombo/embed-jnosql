package org.junify.db.sql;

import org.junify.db.nosql.document.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Column-level constraint metadata for a SQL table created through
 * {@code CREATE TABLE ... (col TYPE [PRIMARY KEY|UNIQUE|NOT NULL], ...)}.
 *
 * <p>This is deliberately a small, explicit model rather than a full relational
 * catalogue: it records exactly the constraints the dialect can currently
 * enforce (PRIMARY KEY, UNIQUE, NOT NULL). Foreign keys, CHECK constraints and
 * defaults are <b>not</b> represented here and remain unsupported — see the
 * release limitation list.</p>
 *
 * <p>Instances are immutable-after-construction data holders; they are persisted
 * as documents in a reserved collection by {@link SqlSchemaCatalog}.</p>
 */
public final class SqlTableSchema {

    /** Reserved collection that holds one schema document per constrained table. */
    public static final String RESERVED_COLLECTION = "__junify_sql_schema";

    /** One column and the constraints declared on it. */
    public static final class ColumnRule {
        private final String name;
        private final boolean notNull;
        private final boolean primaryKey;
        private final boolean unique;

        public ColumnRule(String name, boolean notNull, boolean primaryKey, boolean unique) {
            this.name = name;
            this.notNull = notNull || primaryKey; // PK implies NOT NULL in SQL
            this.primaryKey = primaryKey;
            this.unique = unique;
        }

        public String getName() { return name; }
        public boolean isNotNull() { return notNull; }
        public boolean isPrimaryKey() { return primaryKey; }
        public boolean isUnique() { return unique; }
        public boolean isUniqueEnforced() { return primaryKey || unique; }
    }

    private final String tableName;
    private final Map<String, ColumnRule> columns;

    public SqlTableSchema(String tableName, List<ColumnRule> columnRules) {
        this.tableName = tableName;
        Map<String, ColumnRule> map = new LinkedHashMap<>();
        for (ColumnRule rule : columnRules) {
            map.put(rule.getName().toLowerCase(), rule);
        }
        this.columns = Collections.unmodifiableMap(map);
    }

    public String getTableName() { return tableName; }

    public List<ColumnRule> getColumns() { return List.copyOf(columns.values()); }

    /** The column rule for {@code name} (case-insensitive), or {@code null} if undeclared. */
    public ColumnRule column(String name) {
        if (name == null) return null;
        return columns.get(name.toLowerCase());
    }

    /** True when at least one constraint must be enforced on write. */
    public boolean hasConstraints() {
        return columns.values().stream()
                .anyMatch(c -> c.isNotNull() || c.isUniqueEnforced());
    }

    /** Serialises this schema into a persistable document (id = table name). */
    public Document toDocument() {
        Document doc = new Document();
        doc.id(tableName);
        doc.add("table", tableName);
        List<Map<String, Object>> cols = new ArrayList<>();
        for (ColumnRule rule : columns.values()) {
            Map<String, Object> c = new LinkedHashMap<>();
            c.put("name", rule.getName());
            c.put("notNull", rule.isNotNull());
            c.put("primaryKey", rule.isPrimaryKey());
            c.put("unique", rule.isUnique());
            cols.add(c);
        }
        doc.add("columns", cols);
        return doc;
    }

    /** Rebuilds a schema from its persisted document. */
    @SuppressWarnings("unchecked")
    public static SqlTableSchema fromDocument(Document doc) {
        String table = doc.getFields().get("table") != null
                ? doc.getFields().get("table").toString()
                : doc.getId();
        List<ColumnRule> rules = new ArrayList<>();
        Object raw = doc.getFields().get("columns");
        if (raw instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> m) {
                    Map<String, Object> c = new LinkedHashMap<>();
                    m.forEach((k, v) -> c.put(String.valueOf(k), v));
                    String name = c.get("name") != null ? c.get("name").toString() : null;
                    if (name == null) continue;
                    rules.add(new ColumnRule(
                            name,
                            asBoolean(c.get("notNull")),
                            asBoolean(c.get("primaryKey")),
                            asBoolean(c.get("unique"))));
                }
            }
        }
        return new SqlTableSchema(table, rules);
    }

    private static boolean asBoolean(Object o) {
        if (o instanceof Boolean b) return b;
        return o != null && Boolean.parseBoolean(o.toString());
    }

    @Override
    public String toString() {
        return "SqlTableSchema{" + tableName + ", columns=" + getColumns().size() + "}";
    }
}

package org.junify.db.sql;

import org.junify.db.nosql.document.Document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Column-level and table-level constraint metadata for a SQL table created through
 * {@code CREATE TABLE}: PRIMARY KEY, UNIQUE, NOT NULL, REFERENCES (foreign key) and CHECK.
 *
 * <p>This remains a small, explicit model rather than a full relational catalogue. It records
 * exactly the constraints the dialect can currently enforce. What is still <b>not</b>
 * represented (and therefore not enforced) is documented in the release limitation list.</p>
 *
 * <p>Instances are immutable data holders; they are persisted as documents in a reserved
 * collection by {@link SqlSchemaCatalog}.</p>
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
        private final String foreignKeyTable;
        private final String foreignKeyColumn;

        public ColumnRule(String name, boolean notNull, boolean primaryKey, boolean unique) {
            this(name, notNull, primaryKey, unique, null, null);
        }

        public ColumnRule(String name, boolean notNull, boolean primaryKey, boolean unique,
                          String foreignKeyTable, String foreignKeyColumn) {
            this.name = name;
            this.notNull = notNull || primaryKey; // PK implies NOT NULL in SQL
            this.primaryKey = primaryKey;
            this.unique = unique;
            this.foreignKeyTable = foreignKeyTable;
            this.foreignKeyColumn = (foreignKeyTable != null && foreignKeyColumn == null)
                    ? "id" : foreignKeyColumn;
        }

        public String getName() { return name; }
        public boolean isNotNull() { return notNull; }
        public boolean isPrimaryKey() { return primaryKey; }
        public boolean isUnique() { return unique; }
        public boolean isUniqueEnforced() { return primaryKey || unique; }
        public boolean isForeignKey() { return foreignKeyTable != null; }
        public String getForeignKeyTable() { return foreignKeyTable; }
        public String getForeignKeyColumn() { return foreignKeyColumn; }
    }

    private final String tableName;
    private final Map<String, ColumnRule> columns;
    private final List<String> checks;

    public SqlTableSchema(String tableName, List<ColumnRule> columnRules) {
        this(tableName, columnRules, List.of());
    }

    public SqlTableSchema(String tableName, List<ColumnRule> columnRules, List<String> checks) {
        this.tableName = tableName;
        Map<String, ColumnRule> map = new LinkedHashMap<>();
        for (ColumnRule rule : columnRules) {
            map.put(rule.getName().toLowerCase(), rule);
        }
        this.columns = Collections.unmodifiableMap(map);
        this.checks = List.copyOf(checks);
    }

    public String getTableName() { return tableName; }

    public List<ColumnRule> getColumns() { return List.copyOf(columns.values()); }

    /** CHECK predicates, each stored as SQL text and re-parsed when a row is written. */
    public List<String> getChecks() { return checks; }

    /** The column rule for {@code name} (case-insensitive), or {@code null} if undeclared. */
    public ColumnRule column(String name) {
        if (name == null) return null;
        return columns.get(name.toLowerCase());
    }

    /** True when at least one constraint must be enforced on write. */
    public boolean hasConstraints() {
        if (!checks.isEmpty()) return true;
        return columns.values().stream()
                .anyMatch(c -> c.isNotNull() || c.isUniqueEnforced() || c.isForeignKey());
    }

    /** True when any column of this table references another table. */
    public boolean hasForeignKeys() {
        return columns.values().stream().anyMatch(ColumnRule::isForeignKey);
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
            if (rule.isForeignKey()) {
                c.put("fkTable", rule.getForeignKeyTable());
                c.put("fkColumn", rule.getForeignKeyColumn());
            }
            cols.add(c);
        }
        doc.add("columns", cols);
        if (!checks.isEmpty()) {
            doc.add("checks", new ArrayList<>(checks));
        }
        return doc;
    }

    /** Rebuilds a schema from its persisted document. */
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
                    String fkTable = c.get("fkTable") != null ? c.get("fkTable").toString() : null;
                    String fkColumn = c.get("fkColumn") != null ? c.get("fkColumn").toString() : null;
                    rules.add(new ColumnRule(
                            name,
                            asBoolean(c.get("notNull")),
                            asBoolean(c.get("primaryKey")),
                            asBoolean(c.get("unique")),
                            fkTable,
                            fkColumn));
                }
            }
        }
        List<String> checks = new ArrayList<>();
        Object rawChecks = doc.getFields().get("checks");
        if (rawChecks instanceof List<?> list) {
            for (Object c : list) {
                if (c != null) checks.add(c.toString());
            }
        }
        return new SqlTableSchema(table, rules, checks);
    }

    private static boolean asBoolean(Object o) {
        if (o instanceof Boolean b) return b;
        return o != null && Boolean.parseBoolean(o.toString());
    }

    @Override
    public String toString() {
        return "SqlTableSchema{" + tableName + ", columns=" + getColumns().size()
                + ", checks=" + checks.size() + "}";
    }
}

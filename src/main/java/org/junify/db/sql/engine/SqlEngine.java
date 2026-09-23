package org.junify.db.sql.engine;

import org.junify.db.JunifyDB;
import org.junify.db.nosql.document.Document;
import org.junify.db.nosql.document.DocumentCollection;
import org.junify.db.sql.SqlConstraintViolationException;
import org.junify.db.sql.SqlResultSet;
import org.junify.db.sql.SqlRow;
import org.junify.db.sql.SqlSchemaCatalog;
import org.junify.db.sql.SqlTableSchema;
import org.junify.db.sql.ast.*;
import org.junify.db.sql.ast.Expression.*;
import org.junify.db.sql.ast.SqlStatement.*;
import org.junify.db.sql.parser.SqlParser;

import java.util.*;
import java.util.stream.Collectors;

public class SqlEngine {

    private final JunifyDB db;
    private final SqlSchemaCatalog schemaCatalog;
    /** Parsed CHECK predicates, keyed by their stored SQL text (parsed once, reused per write). */
    private final Map<String, Expression> parsedChecks = new HashMap<>();

    public SqlEngine(JunifyDB db) {
        this.db = db;
        this.schemaCatalog = new SqlSchemaCatalog(db);
    }

    public SqlResultSet execute(String sql, Object... params) {
        List<Object> paramList = params != null ? Arrays.asList(params) : Collections.emptyList();
        SqlStatement stmt = SqlParser.parse(sql);
        return execute(stmt, paramList);
    }

    public SqlResultSet execute(SqlStatement stmt, List<Object> params) {
        if (stmt instanceof SelectStatement select) {
            return executeSelect(select, params);
        } else if (stmt instanceof InsertStatement insert) {
            return executeInsert(insert, params);
        } else if (stmt instanceof UpdateStatement update) {
            return executeUpdate(update, params);
        } else if (stmt instanceof DeleteStatement delete) {
            return executeDelete(delete, params);
        } else if (stmt instanceof CreateTableStatement create) {
            return executeCreateTable(create);
        } else if (stmt instanceof DropTableStatement drop) {
            return executeDropTable(drop);
        }
        throw new IllegalArgumentException("Unsupported statement type: " + stmt.getClass());
    }

    // -------------------------------------------------------------------------
    // SELECT Execution
    // -------------------------------------------------------------------------

    private SqlResultSet executeSelect(SelectStatement select, List<Object> params) {
        if (select.getFromTable() == null) {
            // e.g. SELECT 1 + 1
            Map<String, Object> emptyCtx = Collections.emptyMap();
            Map<String, Object> rowData = new LinkedHashMap<>();
            List<String> cols = new ArrayList<>();
            for (SelectItem item : select.getSelectItems()) {
                String name = item.getAlias() != null ? item.getAlias() : item.getExpression().toString();
                rowData.put(name, item.getExpression().evaluate(emptyCtx, params));
                cols.add(name);
            }
            return SqlResultSet.ofRows(List.of(new SqlRow(rowData, cols)), cols);
        }

        String primaryTableName = select.getFromTable().getTableName();
        String primaryAlias = select.getFromTable().getAlias();
        // R-48: SELECT must not create the table it reads. The previous code
        // resolved through documentCollection(), which auto-creates — a SELECT
        // from a typo'd/unknown table silently created an empty collection and
        // returned rowCount:0 as "success".
        DocumentCollection col = existingCollection(primaryTableName, null);

        // 1. Initial rows from FROM table
        List<Map<String, Object>> workingRows = new ArrayList<>();
        for (Document doc : col.findAll()) {
            Map<String, Object> row = new LinkedHashMap<>();
            // Map flat properties
            if (doc.getId() != null) row.put("id", doc.getId());
            doc.getFields().forEach(row::put);
            // Map prefixed properties for qualified access (e.g. u.name)
            if (primaryAlias != null) {
                if (doc.getId() != null) row.put(primaryAlias + ".id", doc.getId());
                doc.getFields().forEach((k, v) -> row.put(primaryAlias + "." + k, v));
            }
            workingRows.add(row);
        }

        // 2. JOINs
        for (JoinClause join : select.getJoins()) {
            String joinTableName = join.getTable().getTableName();
            String joinAlias = join.getTable().getAlias();
            // R-48: JOIN targets are reads too — must not auto-create.
            DocumentCollection joinCol = existingCollection(joinTableName, "JOIN " + joinTableName);
            List<Document> joinDocs = joinCol.findAll();

            List<Map<String, Object>> joinedRows = new ArrayList<>();

            for (Map<String, Object> leftRow : workingRows) {
                boolean matchedAny = false;

                for (Document rightDoc : joinDocs) {
                    Map<String, Object> combined = new LinkedHashMap<>(leftRow);
                    // Add right table fields
                    if (rightDoc.getId() != null) combined.put(joinAlias + ".id", rightDoc.getId());
                    rightDoc.getFields().forEach((k, v) -> combined.put(joinAlias + "." + k, v));
                    // Also non-prefixed if no collision
                    if (!combined.containsKey("id") && rightDoc.getId() != null) combined.put("id", rightDoc.getId());
                    rightDoc.getFields().forEach(combined::putIfAbsent);

                    Object onResult = join.getOnCondition().evaluate(combined, params);
                    if (Boolean.TRUE.equals(onResult)) {
                        joinedRows.add(combined);
                        matchedAny = true;
                    }
                }

                // LEFT JOIN null padding
                if (!matchedAny && join.getType() == JoinClause.JoinType.LEFT) {
                    joinedRows.add(leftRow);
                }
            }

            workingRows = joinedRows;
        }

        // 3. WHERE filtering
        if (select.getWhereClause() != null) {
            workingRows = workingRows.stream()
                    .filter(row -> Boolean.TRUE.equals(select.getWhereClause().evaluate(row, params)))
                    .collect(Collectors.toList());
        }

        // 4. Check for Aggregates
        boolean hasAggregates = select.getSelectItems().stream()
                .anyMatch(item -> item.getExpression() instanceof FunctionExpr fe && fe.isAggregate());

        List<SqlRow> resultRows = new ArrayList<>();
        List<String> columnNames = new ArrayList<>();

        if (hasAggregates && select.getGroupBy().isEmpty()) {
            // Single aggregate row across all matching items
            Map<String, Object> aggRow = new LinkedHashMap<>();
            for (SelectItem item : select.getSelectItems()) {
                String colName = item.getAlias() != null ? item.getAlias() : item.getExpression().toString();
                columnNames.add(colName);

                if (item.getExpression() instanceof FunctionExpr fe && fe.isAggregate()) {
                    aggRow.put(colName, computeAggregate(fe, workingRows, params));
                } else {
                    Object val = workingRows.isEmpty() ? null : item.getExpression().evaluate(workingRows.get(0), params);
                    aggRow.put(colName, val);
                }
            }
            resultRows.add(new SqlRow(aggRow, columnNames));
        } else if (!select.getGroupBy().isEmpty()) {
            // Group By processing
            Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
            for (Map<String, Object> row : workingRows) {
                StringBuilder key = new StringBuilder();
                for (Expression gbExpr : select.getGroupBy()) {
                    key.append(gbExpr.evaluate(row, params)).append("___");
                }
                groups.computeIfAbsent(key.toString(), k -> new ArrayList<>()).add(row);
            }

            for (List<Map<String, Object>> groupRows : groups.values()) {
                Map<String, Object> firstRow = groupRows.get(0);
                Map<String, Object> rowData = new LinkedHashMap<>();
                List<String> currentCols = new ArrayList<>();

                for (SelectItem item : select.getSelectItems()) {
                    String colName = item.getAlias() != null ? item.getAlias() : item.getExpression().toString();
                    currentCols.add(colName);
                    if (item.getExpression() instanceof FunctionExpr fe && fe.isAggregate()) {
                        rowData.put(colName, computeAggregate(fe, groupRows, params));
                    } else {
                        rowData.put(colName, item.getExpression().evaluate(firstRow, params));
                    }
                }
                columnNames = currentCols;
                resultRows.add(new SqlRow(rowData, columnNames));
            }
        } else {
            // Standard projections
            for (Map<String, Object> row : workingRows) {
                Map<String, Object> rowData = new LinkedHashMap<>();
                List<String> currentCols = new ArrayList<>();

                for (SelectItem item : select.getSelectItems()) {
                    if (item.isWildcard()) {
                        // Project all raw fields
                        for (Map.Entry<String, Object> e : row.entrySet()) {
                            if (!e.getKey().contains(".")) { // skip aliases
                                rowData.put(e.getKey(), e.getValue());
                                if (!currentCols.contains(e.getKey())) currentCols.add(e.getKey());
                            }
                        }
                    } else {
                        String colName = item.getAlias() != null ? item.getAlias() : item.getExpression().toString();
                        if (item.getExpression() instanceof ColumnExpr ce && item.getAlias() == null) {
                            colName = ce.getColumnName();
                        }
                        rowData.put(colName, item.getExpression().evaluate(row, params));
                        if (!currentCols.contains(colName)) currentCols.add(colName);
                    }
                }
                columnNames = currentCols;
                resultRows.add(new SqlRow(rowData, columnNames));
            }
        }

        // 5. DISTINCT
        if (select.isDistinct()) {
            Set<String> seen = new HashSet<>();
            List<SqlRow> distinctRows = new ArrayList<>();
            for (SqlRow r : resultRows) {
                String rep = r.asMap().toString();
                if (seen.add(rep)) {
                    distinctRows.add(r);
                }
            }
            resultRows = distinctRows;
        }

        // 6. ORDER BY
        if (!select.getOrderBy().isEmpty()) {
            resultRows.sort((r1, r2) -> {
                for (OrderByItem ob : select.getOrderBy()) {
                    Object v1 = ob.getExpression().evaluate(r1.asMap(), params);
                    Object v2 = ob.getExpression().evaluate(r2.asMap(), params);
                    int cmp = compareValues(v1, v2);
                    if (cmp != 0) {
                        return ob.isAscending() ? cmp : -cmp;
                    }
                }
                return 0;
            });
        }

        // 7. OFFSET & LIMIT
        int offset = select.getOffset() != null ? select.getOffset() : 0;
        int limit = select.getLimit() != null ? select.getLimit() : Integer.MAX_VALUE;

        if (offset > 0 || limit < Integer.MAX_VALUE) {
            int fromIdx = Math.min(offset, resultRows.size());
            int toIdx = Math.min(fromIdx + limit, resultRows.size());
            resultRows = resultRows.subList(fromIdx, toIdx);
        }

        return SqlResultSet.ofRows(resultRows, columnNames);
    }

    private Object computeAggregate(FunctionExpr fe, List<Map<String, Object>> rows, List<Object> params) {
        String func = fe.getFunctionName();
        if ("COUNT".equals(func)) {
            if (fe.getArguments().isEmpty() || (fe.getArguments().get(0) instanceof ColumnExpr ce && "*".equals(ce.getColumnName()))) {
                return (long) rows.size();
            }
            Expression arg = fe.getArguments().get(0);
            long count = 0;
            Set<Object> seen = new HashSet<>();
            for (Map<String, Object> r : rows) {
                Object val = arg.evaluate(r, params);
                if (val != null) {
                    if (fe.isDistinct()) {
                        if (seen.add(val)) count++;
                    } else {
                        count++;
                    }
                }
            }
            return count;
        }

        if (fe.getArguments().isEmpty()) return null;
        Expression arg = fe.getArguments().get(0);

        List<Double> numbers = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Object val = arg.evaluate(r, params);
            if (val instanceof Number n) {
                numbers.add(n.doubleValue());
            } else if (val != null) {
                try {
                    numbers.add(Double.parseDouble(val.toString()));
                } catch (NumberFormatException ignored) {}
            }
        }

        if (numbers.isEmpty()) return null;

        switch (func) {
            case "SUM":
                return numbers.stream().mapToDouble(Double::doubleValue).sum();
            case "AVG":
                return numbers.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            case "MIN":
                return numbers.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
            case "MAX":
                return numbers.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            default:
                return null;
        }
    }

    // -------------------------------------------------------------------------
    // INSERT Execution
    // -------------------------------------------------------------------------

    /**
     * <p>Reads, UPDATE, DELETE and DROP resolve through here and throw
     * {@link SqlUnknownTableException} when the table does not exist. Only
     * INSERT (and CREATE TABLE) create tables — the documented schemaless
     * workflow that internal callers (backup/restore, migrations, repos)
     * rely on.</p>
     */
    private DocumentCollection existingCollection(String name, String context) {
        if (!db.getCollectionNames().contains(name)) {
            throw new org.junify.db.sql.SqlUnknownTableException(
                "Table '" + name + "' does not exist" + (context != null && !context.isBlank() ? " (" + context + ")" : ""));
        }
        return db.documentCollection(name);
    }

    private SqlResultSet executeInsert(InsertStatement insert, List<Object> params) {
        String tableName = insert.getTableName();
        DocumentCollection col = db.documentCollection(tableName);
        SqlTableSchema schema = schemaCatalog.get(tableName);
        int count = 0;
        List<Document> inserted = new ArrayList<>();

        try {
            for (List<Expression> rowExprs : insert.getRowsOfValues()) {
                Document doc = new Document();
                doc.add("_entity", tableName);
                // Distinguishes an explicitly supplied NULL primary key (a violation) from an
                // omitted one (auto-generated id). Without this, the auto-id branch below would
                // quietly paper over INSERT ... VALUES (NULL, ...) on a NOT NULL / PRIMARY KEY id.
                boolean idExplicitlyNull = false;

                if (insert.getColumns().isEmpty()) {
                    // Without column names, map by index col_0, col_1...
                    for (int i = 0; i < rowExprs.size(); i++) {
                        doc.add("col_" + i, rowExprs.get(i).evaluate(Collections.emptyMap(), params));
                    }
                } else {
                    for (int i = 0; i < insert.getColumns().size() && i < rowExprs.size(); i++) {
                        String colName = insert.getColumns().get(i);
                        Object val = rowExprs.get(i).evaluate(Collections.emptyMap(), params);
                        if ("id".equalsIgnoreCase(colName)) {
                            if (val == null) idExplicitlyNull = true;
                            doc.id(val != null ? val.toString() : null);
                        } else {
                            doc.add(colName, val);
                        }
                    }
                }

                if (doc.getId() == null && !idExplicitlyNull) {
                    doc.id(UUID.randomUUID().toString());
                }

                // Constraint enforcement (only for tables that declared constraints).
                // Checked before the row is stored so a violation never leaves data behind.
                enforceConstraints(tableName, schema, doc, null);

                col.insert(doc);
                inserted.add(doc);
                count++;
            }
        } catch (RuntimeException e) {
            // Statement atomicity: a multi-row INSERT that violates a constraint on a
            // later row must not leave the earlier rows of the same statement applied.
            for (Document d : inserted) {
                col.deleteById(d.getId());
            }
            throw e;
        }

        return SqlResultSet.ofUpdate(count, "INSERT");
    }

    // -------------------------------------------------------------------------
    // UPDATE Execution
    // -------------------------------------------------------------------------

    private SqlResultSet executeUpdate(UpdateStatement update, List<Object> params) {
        // R-48: a write against a table that does not exist used to auto-create
        // an empty collection as a side effect (updateCount 0 either way). SQL
        // semantics: error. Only INSERT (and CREATE TABLE) create tables.
        String tableName = update.getTableName();
        DocumentCollection col = existingCollection(tableName, "UPDATE");
        SqlTableSchema schema = schemaCatalog.get(tableName);
        // FK parent-side + CHECK are validated per row below; see enforceConstraints.
        int count = 0;

        for (Document doc : col.findAll()) {
            Map<String, Object> ctx = new LinkedHashMap<>();
            if (doc.getId() != null) ctx.put("id", doc.getId());
            doc.getFields().forEach(ctx::put);

            if (update.getWhereClause() == null || Boolean.TRUE.equals(update.getWhereClause().evaluate(ctx, params))) {
                // Validate the post-update state before mutating: a violation must
                // leave the row exactly as it was.
                Document candidate = new Document();
                candidate.id(doc.getId());
                doc.getFields().forEach(candidate::add);
                for (Map.Entry<String, Expression> assign : update.getAssignments().entrySet()) {
                    String colName = assign.getKey();
                    Object newVal = assign.getValue().evaluate(ctx, params);
                    if ("id".equalsIgnoreCase(colName)) {
                        candidate.id(newVal != null ? newVal.toString() : null);
                    } else {
                        candidate.add(colName, newVal);
                    }
                }
                enforceConstraints(tableName, schema, candidate, doc.getId());

                // Apply assignments
                for (Map.Entry<String, Expression> assign : update.getAssignments().entrySet()) {
                    String colName = assign.getKey();
                    Object newVal = assign.getValue().evaluate(ctx, params);
                    if ("id".equalsIgnoreCase(colName)) {
                        doc.id(newVal != null ? newVal.toString() : null);
                    } else {
                        doc.add(colName, newVal);
                    }
                }
                col.update(doc);
                count++;
            }
        }

        return SqlResultSet.ofUpdate(count, "UPDATE");
    }

    // -------------------------------------------------------------------------
    // DELETE Execution
    // -------------------------------------------------------------------------

    private SqlResultSet executeDelete(DeleteStatement delete, List<Object> params) {
        // R-48: same as UPDATE — DELETE on a missing table is an SQL error,
        // not a silent 0 that leaves an empty collection behind.
        String tableName = delete.getTableName();
        DocumentCollection col = existingCollection(tableName, "DELETE");

        // Collect the matching rows first, so a foreign-key violation leaves the table untouched
        // instead of deleting part of the statement's rows.
        List<Document> matched = new ArrayList<>();
        for (Document doc : col.findAll()) {
            Map<String, Object> ctx = new LinkedHashMap<>();
            if (doc.getId() != null) ctx.put("id", doc.getId());
            doc.getFields().forEach(ctx::put);

            if (delete.getWhereClause() == null || Boolean.TRUE.equals(delete.getWhereClause().evaluate(ctx, params))) {
                matched.add(doc);
            }
        }

        assertNoIncomingReferences(tableName, matched);

        int count = 0;
        for (Document doc : matched) {
            col.deleteById(doc.getId());
            count++;
        }

        return SqlResultSet.ofUpdate(count, "DELETE");
    }

    // -------------------------------------------------------------------------
    // DDL Execution
    // -------------------------------------------------------------------------

    private SqlResultSet executeCreateTable(CreateTableStatement create) {
        db.documentCollection(create.getTableName());
        // Persist constraints only when the statement declared at least one enforceable rule
        // (PRIMARY KEY / UNIQUE / NOT NULL). A bare CREATE TABLE, or one that lists columns with
        // no constraints, keeps the original schemaless behaviour and writes no metadata — so
        // existing databases are byte-identical and no reserved collection appears for them.
        boolean hasColumns = create.getColumns() != null && !create.getColumns().isEmpty();
        boolean hasChecks = create.getChecks() != null && !create.getChecks().isEmpty();
        if (hasColumns || hasChecks) {
            List<SqlTableSchema.ColumnRule> rules = new ArrayList<>();
            if (hasColumns) {
                for (SqlStatement.ColumnDefinition def : create.getColumns()) {
                    rules.add(new SqlTableSchema.ColumnRule(
                            def.getName(), def.isNotNull(), def.isPrimaryKey(), def.isUnique(),
                            def.getForeignKeyTable(), def.getForeignKeyColumn()));
                }
            }
            SqlTableSchema schema = new SqlTableSchema(
                    create.getTableName(), rules, hasChecks ? create.getChecks() : List.of());
            if (schema.hasConstraints()) {
                schemaCatalog.save(schema);
            }
        }
        return SqlResultSet.ofUpdate(0, "CREATE_TABLE");
    }

    /**
     * Enforces PRIMARY KEY, UNIQUE and NOT NULL for a table that declared constraints.
     * No-op for schemaless tables (schema == null), preserving the original behaviour for
     * collections created by INSERT or the NoSQL API.
     *
     * <p>{@code excludeId} is the id of the row being replaced by an UPDATE, so a row does not
     * conflict with its own pre-existing values.</p>
     */
    private void enforceConstraints(String tableName, SqlTableSchema schema, Document candidate, String excludeId) {
        if (schema == null || !schema.hasConstraints()) return;
        DocumentCollection col = db.documentCollection(tableName);
        for (SqlTableSchema.ColumnRule rule : schema.getColumns()) {
            String column = rule.getName();
            Object value = readColumn(candidate, column);

            if (rule.isNotNull() && value == null) {
                throw new SqlConstraintViolationException(
                        "NOT NULL constraint violated on " + tableName + "." + column);
            }

            if (rule.isUniqueEnforced() && value != null) {
                String existingId = findExistingId(col, column, value, excludeId);
                if (existingId != null) {
                    throw new SqlConstraintViolationException(
                            (rule.isPrimaryKey() ? "PRIMARY KEY" : "UNIQUE")
                                    + " constraint violated on " + tableName + "." + column
                                    + ": value '" + value + "' already exists");
                }
            }

            if (rule.isForeignKey() && value != null) {
                String refTable = rule.getForeignKeyTable();
                String refColumn = rule.getForeignKeyColumn();
                if (!db.getCollectionNames().contains(refTable)) {
                    throw new SqlConstraintViolationException(
                            "FOREIGN KEY constraint violated on " + tableName + "." + column
                                    + ": referenced table '" + refTable + "' does not exist");
                }
                if (findExistingId(db.documentCollection(refTable), refColumn, value, null) == null) {
                    throw new SqlConstraintViolationException(
                            "FOREIGN KEY constraint violated on " + tableName + "." + column
                                    + ": no row in " + refTable + "." + refColumn
                                    + " matches '" + value + "'");
                }
            }
        }

        for (String checkSql : schema.getChecks()) {
            Expression expr = parsedChecks.computeIfAbsent(checkSql, SqlParser::parseExpression);
            Map<String, Object> ctx = new LinkedHashMap<>();
            if (candidate.getId() != null) ctx.put("id", candidate.getId());
            candidate.getFields().forEach(ctx::put);
            if (!Boolean.TRUE.equals(expr.evaluate(ctx, List.of()))) {
                throw new SqlConstraintViolationException(
                        "CHECK constraint violated on " + tableName + ": " + checkSql);
            }
        }
    }

    /**
     * Blocks removal of rows that a foreign key still points at. Used by DELETE and DROP TABLE so
     * that a foreign key is enforced in both directions, not only when a child row is written.
     */
    private void assertNoIncomingReferences(String tableName, List<Document> rows) {
        for (SqlTableSchema other : schemaCatalog.all()) {
            if (other.getTableName().equalsIgnoreCase(tableName)) continue;
            if (!db.getCollectionNames().contains(other.getTableName())) continue;
            for (SqlTableSchema.ColumnRule rule : other.getColumns()) {
                if (!rule.isForeignKey()
                        || !rule.getForeignKeyTable().equalsIgnoreCase(tableName)) continue;
                DocumentCollection referrers = db.documentCollection(other.getTableName());
                for (Document row : rows) {
                    Object key = readColumn(row, rule.getForeignKeyColumn());
                    if (key == null) continue;
                    if (findExistingId(referrers, rule.getName(), key, null) != null) {
                        throw new SqlConstraintViolationException(
                                "FOREIGN KEY constraint violated: cannot remove " + tableName
                                        + " row '" + key + "' while " + other.getTableName() + "."
                                        + rule.getName() + " still references it");
                    }
                }
            }
        }
    }

    /** Reads a column from a candidate row, treating {@code id} as the document id. */
    private static Object readColumn(Document doc, String column) {
        if ("id".equalsIgnoreCase(column)) return doc.getId();
        Object value = doc.getFields().get(column);
        if (value != null) return value;
        for (Map.Entry<String, Object> e : doc.getFields().entrySet()) {
            if (e.getKey().equalsIgnoreCase(column)) return e.getValue();
        }
        return null;
    }

    /** Returns the id of a row that already holds {@code value} in {@code column}, or null. */
    private static String findExistingId(DocumentCollection col, String column, Object value, String excludeId) {
        if ("id".equalsIgnoreCase(column)) {
            String id = value.toString();
            if (id.equals(excludeId)) return null;
            return col.exists(id) ? id : null;
        }
        for (Document d : col.findAll()) {
            if (excludeId != null && excludeId.equals(d.getId())) continue;
            Object v = d.getFields().get(column);
            if (v == null) {
                for (Map.Entry<String, Object> e : d.getFields().entrySet()) {
                    if (e.getKey().equalsIgnoreCase(column)) {
                        v = e.getValue();
                        break;
                    }
                }
            }
            if (v != null && v.equals(value)) return d.getId();
        }
        return null;
    }

    private SqlResultSet executeDropTable(DropTableStatement drop) {
        // R-49: DROP TABLE is DDL — dropping a table that does not exist used to
        // delete nothing and report success. Fail loudly instead (IF EXISTS is
        // not yet supported by the parser; tracked in the defect register).
        DocumentCollection col = existingCollection(drop.getTableName(), "DROP TABLE");
        List<Document> rows = col.findAll();
        assertNoIncomingReferences(drop.getTableName(), rows);
        for (Document d : rows) {
            col.deleteById(d.getId());
        }
        schemaCatalog.drop(drop.getTableName());
        return SqlResultSet.ofUpdate(0, "DROP_TABLE");
    }

    private static int compareValues(Object a, Object b) {
        if (a == null && b == null) return 0;
        if (a == null) return -1;
        if (b == null) return 1;
        if (a.equals(b)) return 0;
        if (a instanceof Number na && b instanceof Number nb) {
            return Double.compare(na.doubleValue(), nb.doubleValue());
        }
        return a.toString().compareToIgnoreCase(b.toString());
    }
}

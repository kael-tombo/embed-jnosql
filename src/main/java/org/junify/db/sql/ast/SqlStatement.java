package org.junify.db.sql.ast;

import java.util.*;

public interface SqlStatement {

    enum StatementType {
        SELECT, INSERT, UPDATE, DELETE, CREATE_TABLE, DROP_TABLE
    }

    StatementType getStatementType();

    class SelectItem {
        private final Expression expression;
        private final String alias;

        public SelectItem(Expression expression, String alias) {
            this.expression = expression;
            this.alias = alias;
        }

        public Expression getExpression() { return expression; }
        public String getAlias() { return alias; }
        public boolean isWildcard() {
            return expression instanceof Expression.ColumnExpr ce && "*".equals(ce.getColumnName());
        }
    }

    class TableRef {
        private final String tableName;
        private final String alias;

        public TableRef(String tableName, String alias) {
            this.tableName = tableName;
            this.alias = alias != null ? alias : tableName;
        }

        public String getTableName() { return tableName; }
        public String getAlias() { return alias; }
    }

    class JoinClause {
        public enum JoinType { INNER, LEFT }
        private final JoinType type;
        private final TableRef table;
        private final Expression onCondition;

        public JoinClause(JoinType type, TableRef table, Expression onCondition) {
            this.type = type;
            this.table = table;
            this.onCondition = onCondition;
        }

        public JoinType getType() { return type; }
        public TableRef getTable() { return table; }
        public Expression getOnCondition() { return onCondition; }
    }

    class OrderByItem {
        private final Expression expression;
        private final boolean ascending;

        public OrderByItem(Expression expression, boolean ascending) {
            this.expression = expression;
            this.ascending = ascending;
        }

        public Expression getExpression() { return expression; }
        public boolean isAscending() { return ascending; }
    }

    class SelectStatement implements SqlStatement {
        private boolean distinct = false;
        private final List<SelectItem> selectItems = new ArrayList<>();
        private TableRef fromTable;
        private final List<JoinClause> joins = new ArrayList<>();
        private Expression whereClause;
        private final List<Expression> groupBy = new ArrayList<>();
        private Expression havingClause;
        private final List<OrderByItem> orderBy = new ArrayList<>();
        private Integer limit;
        private Integer offset;

        @Override
        public StatementType getStatementType() { return StatementType.SELECT; }

        public boolean isDistinct() { return distinct; }
        public void setDistinct(boolean distinct) { this.distinct = distinct; }
        public List<SelectItem> getSelectItems() { return selectItems; }
        public TableRef getFromTable() { return fromTable; }
        public void setFromTable(TableRef fromTable) { this.fromTable = fromTable; }
        public List<JoinClause> getJoins() { return joins; }
        public Expression getWhereClause() { return whereClause; }
        public void setWhereClause(Expression whereClause) { this.whereClause = whereClause; }
        public List<Expression> getGroupBy() { return groupBy; }
        public Expression getHavingClause() { return havingClause; }
        public void setHavingClause(Expression havingClause) { this.havingClause = havingClause; }
        public List<OrderByItem> getOrderBy() { return orderBy; }
        public Integer getLimit() { return limit; }
        public void setLimit(Integer limit) { this.limit = limit; }
        public Integer getOffset() { return offset; }
        public void setOffset(Integer offset) { this.offset = offset; }
    }

    class InsertStatement implements SqlStatement {
        private String tableName;
        private final List<String> columns = new ArrayList<>();
        private final List<List<Expression>> rowsOfValues = new ArrayList<>();

        @Override
        public StatementType getStatementType() { return StatementType.INSERT; }

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public List<String> getColumns() { return columns; }
        public List<List<Expression>> getRowsOfValues() { return rowsOfValues; }
    }

    class UpdateStatement implements SqlStatement {
        private String tableName;
        private final Map<String, Expression> assignments = new LinkedHashMap<>();
        private Expression whereClause;

        @Override
        public StatementType getStatementType() { return StatementType.UPDATE; }

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public Map<String, Expression> getAssignments() { return assignments; }
        public Expression getWhereClause() { return whereClause; }
        public void setWhereClause(Expression whereClause) { this.whereClause = whereClause; }
    }

    class DeleteStatement implements SqlStatement {
        private String tableName;
        private Expression whereClause;

        @Override
        public StatementType getStatementType() { return StatementType.DELETE; }

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public Expression getWhereClause() { return whereClause; }
        public void setWhereClause(Expression whereClause) { this.whereClause = whereClause; }
    }

    /**
     * A column declared in {@code CREATE TABLE t (name TYPE [constraints], ...)} and the
     * constraints attached to it. Only the constraints the dialect can enforce are captured
     * (PRIMARY KEY, UNIQUE, NOT NULL); anything else is ignored by the parser.
     */
    class ColumnDefinition {
        private final String name;
        private final boolean notNull;
        private final boolean primaryKey;
        private final boolean unique;

        public ColumnDefinition(String name, boolean notNull, boolean primaryKey, boolean unique) {
            this.name = name;
            this.notNull = notNull;
            this.primaryKey = primaryKey;
            this.unique = unique;
        }

        public String getName() { return name; }
        public boolean isNotNull() { return notNull; }
        public boolean isPrimaryKey() { return primaryKey; }
        public boolean isUnique() { return unique; }
    }

    class CreateTableStatement implements SqlStatement {
        private String tableName;
        private boolean ifNotExists;
        private final List<ColumnDefinition> columns = new ArrayList<>();

        @Override
        public StatementType getStatementType() { return StatementType.CREATE_TABLE; }

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public boolean isIfNotExists() { return ifNotExists; }
        public void setIfNotExists(boolean ifNotExists) { this.ifNotExists = ifNotExists; }
        public List<ColumnDefinition> getColumns() { return columns; }
    }

    class DropTableStatement implements SqlStatement {
        private String tableName;
        private boolean ifExists;

        @Override
        public StatementType getStatementType() { return StatementType.DROP_TABLE; }

        public String getTableName() { return tableName; }
        public void setTableName(String tableName) { this.tableName = tableName; }
        public boolean isIfExists() { return ifExists; }
        public void setIfExists(boolean ifExists) { this.ifExists = ifExists; }
    }
}

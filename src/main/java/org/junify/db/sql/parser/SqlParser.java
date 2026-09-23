package org.junify.db.sql.parser;

import org.junify.db.sql.ast.*;
import org.junify.db.sql.ast.Expression.*;
import org.junify.db.sql.ast.SqlStatement.*;

import java.util.*;

public class SqlParser {

    private final List<SqlLexer.Token> tokens;
    private int current = 0;
    private int paramCounter = 0;

    public SqlParser(List<SqlLexer.Token> tokens) {
        this.tokens = tokens;
    }

    public static SqlStatement parse(String sql) {
        List<SqlLexer.Token> tokens = SqlLexer.tokenize(sql);
        return new SqlParser(tokens).parseStatement();
    }

    public SqlStatement parseStatement() {
        if (matchKeyword("SELECT")) {
            return parseSelect();
        } else if (matchKeyword("INSERT")) {
            return parseInsert();
        } else if (matchKeyword("UPDATE")) {
            return parseUpdate();
        } else if (matchKeyword("DELETE")) {
            return parseDelete();
        } else if (matchKeyword("CREATE")) {
            return parseCreate();
        } else if (matchKeyword("DROP")) {
            return parseDrop();
        }
        throw new IllegalArgumentException("Unsupported or invalid SQL statement at: " + peek());
    }

    // -------------------------------------------------------------------------
    // SELECT
    // -------------------------------------------------------------------------

    private SelectStatement parseSelect() {
        SelectStatement stmt = new SelectStatement();
        if (matchKeyword("DISTINCT")) {
            stmt.setDistinct(true);
        }

        // Select items
        do {
            if (matchSymbol("*")) {
                stmt.getSelectItems().add(new SelectItem(new ColumnExpr(null, "*"), null));
            } else {
                Expression expr = parseExpression();
                String alias = null;
                if (matchKeyword("AS")) {
                    alias = consumeIdentifierOrKeyword();
                } else if (checkIdentifier()) {
                    alias = consumeIdentifierOrKeyword();
                }
                stmt.getSelectItems().add(new SelectItem(expr, alias));
            }
        } while (matchSymbol(","));

        // FROM
        if (matchKeyword("FROM")) {
            String tableName = consumeIdentifierOrKeyword();
            String alias = tableName;
            if (matchKeyword("AS")) {
                alias = consumeIdentifierOrKeyword();
            } else if (checkIdentifier() && !peek().isKeyword("WHERE") && !peek().isKeyword("JOIN")
                    && !peek().isKeyword("LEFT") && !peek().isKeyword("INNER") && !peek().isKeyword("ORDER")
                    && !peek().isKeyword("GROUP") && !peek().isKeyword("LIMIT")) {
                alias = consumeIdentifierOrKeyword();
            }
            stmt.setFromTable(new TableRef(tableName, alias));
        }

        // Joins
        while (checkKeyword("JOIN") || checkKeyword("INNER") || checkKeyword("LEFT")) {
            JoinClause.JoinType joinType = JoinClause.JoinType.INNER;
            if (matchKeyword("LEFT")) {
                matchKeyword("OUTER");
                matchKeyword("JOIN");
                joinType = JoinClause.JoinType.LEFT;
            } else if (matchKeyword("INNER")) {
                matchKeyword("JOIN");
            } else {
                matchKeyword("JOIN");
            }

            String joinTable = consumeIdentifierOrKeyword();
            String joinAlias = joinTable;
            if (matchKeyword("AS")) {
                joinAlias = consumeIdentifierOrKeyword();
            } else if (checkIdentifier() && !peek().isKeyword("ON")) {
                joinAlias = consumeIdentifierOrKeyword();
            }

            consumeKeyword("ON");
            Expression onCond = parseExpression();
            stmt.getJoins().add(new JoinClause(joinType, new TableRef(joinTable, joinAlias), onCond));
        }

        // WHERE
        if (matchKeyword("WHERE")) {
            stmt.setWhereClause(parseExpression());
        }

        // GROUP BY
        if (matchKeyword("GROUP")) {
            consumeKeyword("BY");
            do {
                stmt.getGroupBy().add(parseExpression());
            } while (matchSymbol(","));
        }

        // HAVING
        if (matchKeyword("HAVING")) {
            stmt.setHavingClause(parseExpression());
        }

        // ORDER BY
        if (matchKeyword("ORDER")) {
            consumeKeyword("BY");
            do {
                Expression orderExpr = parseExpression();
                boolean asc = true;
                if (matchKeyword("DESC")) {
                    asc = false;
                } else if (matchKeyword("ASC")) {
                    asc = true;
                }
                stmt.getOrderBy().add(new OrderByItem(orderExpr, asc));
            } while (matchSymbol(","));
        }

        // LIMIT / OFFSET
        if (matchKeyword("LIMIT")) {
            int limit = Integer.parseInt(consumeToken(SqlLexer.TokenType.NUMBER_LITERAL).getValue());
            stmt.setLimit(limit);
            if (matchKeyword("OFFSET")) {
                int offset = Integer.parseInt(consumeToken(SqlLexer.TokenType.NUMBER_LITERAL).getValue());
                stmt.setOffset(offset);
            }
        }

        matchSymbol(";");
        return stmt;
    }

    // -------------------------------------------------------------------------
    // INSERT
    // -------------------------------------------------------------------------

    private InsertStatement parseInsert() {
        consumeKeyword("INTO");
        InsertStatement stmt = new InsertStatement();
        stmt.setTableName(consumeIdentifierOrKeyword());

        // Optional columns list: (col1, col2)
        if (matchSymbol("(")) {
            do {
                stmt.getColumns().add(consumeIdentifierOrKeyword());
            } while (matchSymbol(","));
            consumeSymbol(")");
        }

        consumeKeyword("VALUES");
        do {
            consumeSymbol("(");
            List<Expression> row = new ArrayList<>();
            do {
                row.add(parseExpression());
            } while (matchSymbol(","));
            consumeSymbol(")");
            stmt.getRowsOfValues().add(row);
        } while (matchSymbol(","));

        matchSymbol(";");
        return stmt;
    }

    // -------------------------------------------------------------------------
    // UPDATE
    // -------------------------------------------------------------------------

    private UpdateStatement parseUpdate() {
        UpdateStatement stmt = new UpdateStatement();
        stmt.setTableName(consumeIdentifierOrKeyword());

        consumeKeyword("SET");
        do {
            String col = consumeIdentifierOrKeyword();
            consumeSymbol("=");
            Expression val = parseExpression();
            stmt.getAssignments().put(col, val);
        } while (matchSymbol(","));

        if (matchKeyword("WHERE")) {
            stmt.setWhereClause(parseExpression());
        }

        matchSymbol(";");
        return stmt;
    }

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------

    private DeleteStatement parseDelete() {
        consumeKeyword("FROM");
        DeleteStatement stmt = new DeleteStatement();
        stmt.setTableName(consumeIdentifierOrKeyword());

        if (matchKeyword("WHERE")) {
            stmt.setWhereClause(parseExpression());
        }

        matchSymbol(";");
        return stmt;
    }

    // -------------------------------------------------------------------------
    // CREATE / DROP TABLE
    // -------------------------------------------------------------------------

    private CreateTableStatement parseCreate() {
        consumeKeyword("TABLE");
        CreateTableStatement stmt = new CreateTableStatement();
        if (matchKeyword("IF")) {
            consumeKeyword("NOT");
            consumeKeyword("EXISTS");
            stmt.setIfNotExists(true);
        }
        stmt.setTableName(consumeIdentifierOrKeyword());

        // Optional column definitions with constraints: (col TYPE [PRIMARY KEY|UNIQUE|NOT NULL], ...)
        if (matchSymbol("(")) {
            parseColumnDefinitions(stmt);
            consumeSymbol(")");
        }

        matchSymbol(";");
        return stmt;
    }

    /**
     * Parses the parenthesised body of a {@code CREATE TABLE}, splitting it into top-level
     * comma-separated segments (each a column definition or a table-level constraint) and
     * recording the constraints this dialect can enforce. Nested parentheses — as in
     * {@code VARCHAR(50)} or {@code CHECK (x > 0)} — are tracked by depth so a comma inside
     * them is never treated as a segment boundary.
     */
    private void parseColumnDefinitions(CreateTableStatement stmt) {
        while (!isAtEnd() && !checkSymbol(")")) {
            List<SqlLexer.Token> segment = new ArrayList<>();
            int depth = 0;
            while (!isAtEnd()) {
                if (checkSymbol("(")) {
                    depth++;
                } else if (checkSymbol(")")) {
                    if (depth == 0) break;
                    depth--;
                } else if (checkSymbol(",") && depth == 0) {
                    break;
                }
                segment.add(advance());
            }
            applyColumnSegment(stmt, segment);
            if (!matchSymbol(",")) break;
        }
    }

    private void applyColumnSegment(CreateTableStatement stmt, List<SqlLexer.Token> segment) {
        if (segment.isEmpty()) return;
        String first = segment.get(0).getValue();
        if (first == null) return;
        String upper = first.toUpperCase(Locale.ROOT);

        // Table-level constraints: PRIMARY KEY (col) | UNIQUE (col) | CONSTRAINT name PRIMARY KEY (col)
        if (upper.equals("PRIMARY") || upper.equals("UNIQUE") || upper.equals("CONSTRAINT")) {
            applyTableLevelConstraint(stmt, segment);
            return;
        }

        List<String> words = new ArrayList<>();
        for (int i = 1; i < segment.size(); i++) {
            String v = segment.get(i).getValue();
            if (v != null) words.add(v.toUpperCase(Locale.ROOT));
        }
        boolean primaryKey = hasSequence(words, "PRIMARY", "KEY");
        boolean notNull = hasSequence(words, "NOT", "NULL");
        boolean unique = words.contains("UNIQUE");
        stmt.getColumns().add(new ColumnDefinition(first, notNull, primaryKey, unique));
    }

    private void applyTableLevelConstraint(CreateTableStatement stmt, List<SqlLexer.Token> segment) {
        for (int i = 0; i < segment.size(); i++) {
            String v = segment.get(i).getValue();
            if (v == null) continue;
            String u = v.toUpperCase(Locale.ROOT);
            boolean pk = u.equals("PRIMARY") && i + 1 < segment.size()
                    && "KEY".equalsIgnoreCase(String.valueOf(segment.get(i + 1).getValue()));
            if (!pk && !u.equals("UNIQUE")) continue;

            int j = pk ? i + 2 : i + 1;
            if (j < segment.size() && "(".equals(segment.get(j).getValue())) {
                for (int k = j + 1; k < segment.size(); k++) {
                    String col = segment.get(k).getValue();
                    if (col == null || ")".equals(col) || ",".equals(col) || "(".equals(col)) continue;
                    markColumn(stmt, col, pk, !pk);
                }
            }
        }
    }

    private void markColumn(CreateTableStatement stmt, String colName, boolean primaryKey, boolean unique) {
        List<ColumnDefinition> cols = stmt.getColumns();
        for (int i = 0; i < cols.size(); i++) {
            ColumnDefinition existing = cols.get(i);
            if (existing.getName().equalsIgnoreCase(colName)) {
                cols.set(i, new ColumnDefinition(
                        existing.getName(),
                        existing.isNotNull() || primaryKey,
                        existing.isPrimaryKey() || primaryKey,
                        existing.isUnique() || unique));
                return;
            }
        }
        cols.add(new ColumnDefinition(colName, primaryKey, primaryKey, unique));
    }

    private static boolean hasSequence(List<String> words, String first, String second) {
        for (int i = 0; i + 1 < words.size(); i++) {
            if (words.get(i).equals(first) && words.get(i + 1).equals(second)) return true;
        }
        return false;
    }

    private DropTableStatement parseDrop() {
        consumeKeyword("TABLE");
        DropTableStatement stmt = new DropTableStatement();
        if (matchKeyword("IF")) {
            consumeKeyword("EXISTS");
            stmt.setIfExists(true);
        }
        stmt.setTableName(consumeIdentifierOrKeyword());
        matchSymbol(";");
        return stmt;
    }

    // -------------------------------------------------------------------------
    // Expressions
    // -------------------------------------------------------------------------

    public Expression parseExpression() {
        return parseOr();
    }

    private Expression parseOr() {
        Expression expr = parseAnd();
        while (matchKeyword("OR")) {
            Expression right = parseAnd();
            expr = new BinaryExpr(expr, Operator.OR, right);
        }
        return expr;
    }

    private Expression parseAnd() {
        Expression expr = parseEquality();
        while (matchKeyword("AND")) {
            Expression right = parseEquality();
            expr = new BinaryExpr(expr, Operator.AND, right);
        }
        return expr;
    }

    private Expression parseEquality() {
        Expression expr = parseComparison();

        while (true) {
            if (matchSymbol("=")) {
                expr = new BinaryExpr(expr, Operator.EQ, parseComparison());
            } else if (matchSymbol("!=") || matchSymbol("<>")) {
                expr = new BinaryExpr(expr, Operator.NEQ, parseComparison());
            } else if (matchKeyword("LIKE")) {
                expr = new BinaryExpr(expr, Operator.LIKE, parseComparison());
            } else if (matchKeyword("IS")) {
                boolean notNull = matchKeyword("NOT");
                consumeKeyword("NULL");
                expr = new IsNullExpr(expr, notNull);
            } else if (matchKeyword("IN")) {
                expr = parseIn(expr, false);
            } else if (matchKeyword("BETWEEN")) {
                Expression lower = parseComparison();
                consumeKeyword("AND");
                Expression upper = parseComparison();
                expr = new BetweenExpr(expr, lower, upper, false);
            } else if (matchKeyword("NOT")) {
                if (matchKeyword("BETWEEN")) {
                    Expression lower = parseComparison();
                    consumeKeyword("AND");
                    Expression upper = parseComparison();
                    expr = new BetweenExpr(expr, lower, upper, true);
                } else if (matchKeyword("IN")) {
                    expr = parseIn(expr, true);
                } else if (matchKeyword("LIKE")) {
                    expr = new BinaryExpr(expr, Operator.NEQ, parseComparison());
                }
            } else {
                break;
            }
        }

        return expr;
    }

    private Expression parseIn(Expression target, boolean notIn) {
        consumeSymbol("(");
        List<Expression> list = new ArrayList<>();
        do {
            list.add(parseExpression());
        } while (matchSymbol(","));
        consumeSymbol(")");
        return new InExpr(target, list, notIn);
    }

    private Expression parseComparison() {
        Expression expr = parseAdditive();

        while (true) {
            if (matchSymbol("<")) {
                expr = new BinaryExpr(expr, Operator.LT, parseAdditive());
            } else if (matchSymbol("<=")) {
                expr = new BinaryExpr(expr, Operator.LTE, parseAdditive());
            } else if (matchSymbol(">")) {
                expr = new BinaryExpr(expr, Operator.GT, parseAdditive());
            } else if (matchSymbol(">=")) {
                expr = new BinaryExpr(expr, Operator.GTE, parseAdditive());
            } else {
                break;
            }
        }

        return expr;
    }

    private Expression parseAdditive() {
        Expression expr = parseMultiplicative();

        while (true) {
            if (matchSymbol("+")) {
                expr = new BinaryExpr(expr, Operator.ADD, parseMultiplicative());
            } else if (matchSymbol("-")) {
                expr = new BinaryExpr(expr, Operator.SUB, parseMultiplicative());
            } else {
                break;
            }
        }

        return expr;
    }

    private Expression parseMultiplicative() {
        Expression expr = parsePrimary();

        while (true) {
            if (matchSymbol("*")) {
                expr = new BinaryExpr(expr, Operator.MUL, parsePrimary());
            } else if (matchSymbol("/")) {
                expr = new BinaryExpr(expr, Operator.DIV, parsePrimary());
            } else {
                break;
            }
        }

        return expr;
    }

    private Expression parsePrimary() {
        if (matchSymbol("(")) {
            Expression expr = parseExpression();
            consumeSymbol(")");
            return expr;
        }

        if (check(SqlLexer.TokenType.STRING_LITERAL)) {
            return new LiteralExpr(advance().getValue());
        }

        if (check(SqlLexer.TokenType.NUMBER_LITERAL)) {
            String val = advance().getValue();
            if (val.contains(".")) {
                return new LiteralExpr(Double.parseDouble(val));
            } else {
                return new LiteralExpr(Long.parseLong(val));
            }
        }

        if (matchKeyword("TRUE")) {
            return new LiteralExpr(true);
        }
        if (matchKeyword("FALSE")) {
            return new LiteralExpr(false);
        }
        if (matchKeyword("NULL")) {
            return new LiteralExpr(null);
        }

        if (check(SqlLexer.TokenType.PARAMETER)) {
            SqlLexer.Token tok = advance();
            if (tok.getValue().equals("?")) {
                return new ParameterExpr(paramCounter++, null);
            } else {
                return new ParameterExpr(-1, tok.getValue());
            }
        }

        // Functions or Column identifiers
        if (check(SqlLexer.TokenType.IDENTIFIER) || check(SqlLexer.TokenType.KEYWORD)) {
            String name = advance().getValue();

            // Function call: func(...)
            if (matchSymbol("(")) {
                boolean distinct = matchKeyword("DISTINCT");
                List<Expression> args = new ArrayList<>();
                if (!checkSymbol(")")) {
                    do {
                        if (matchSymbol("*")) {
                            args.add(new ColumnExpr(null, "*"));
                        } else {
                            args.add(parseExpression());
                        }
                    } while (matchSymbol(","));
                }
                consumeSymbol(")");
                return new FunctionExpr(name, args, distinct);
            }

            // Column reference: [table.]column
            if (name.contains(".")) {
                int dot = name.indexOf('.');
                return new ColumnExpr(name.substring(0, dot), name.substring(dot + 1));
            } else {
                return new ColumnExpr(null, name);
            }
        }

        throw new IllegalArgumentException("Unexpected token in expression: " + peek());
    }

    // -------------------------------------------------------------------------
    // Parser utilities
    // -------------------------------------------------------------------------

    private boolean check(SqlLexer.TokenType type) {
        if (isAtEnd()) return false;
        return peek().getType() == type;
    }

    private boolean checkKeyword(String kw) {
        if (isAtEnd()) return false;
        return peek().isKeyword(kw);
    }

    private boolean checkSymbol(String sym) {
        if (isAtEnd()) return false;
        return peek().isSymbol(sym);
    }

    private boolean checkIdentifier() {
        if (isAtEnd()) return false;
        return peek().getType() == SqlLexer.TokenType.IDENTIFIER;
    }

    private boolean matchKeyword(String kw) {
        if (checkKeyword(kw)) {
            advance();
            return true;
        }
        return false;
    }

    private boolean matchSymbol(String sym) {
        if (checkSymbol(sym)) {
            advance();
            return true;
        }
        return false;
    }

    private void consumeKeyword(String kw) {
        if (!matchKeyword(kw)) {
            throw new IllegalArgumentException("Expected keyword '" + kw + "' but found: " + peek());
        }
    }

    private void consumeSymbol(String sym) {
        if (!matchSymbol(sym)) {
            throw new IllegalArgumentException("Expected symbol '" + sym + "' but found: " + peek());
        }
    }

    private String consumeIdentifierOrKeyword() {
        if (isAtEnd()) throw new IllegalArgumentException("Expected identifier but reached EOF");
        SqlLexer.Token tok = advance();
        return tok.getValue();
    }

    private SqlLexer.Token consumeToken(SqlLexer.TokenType type) {
        if (!check(type)) {
            throw new IllegalArgumentException("Expected token of type " + type + " but found: " + peek());
        }
        return advance();
    }

    private SqlLexer.Token advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return current >= tokens.size() || peek().getType() == SqlLexer.TokenType.EOF;
    }

    private SqlLexer.Token peek() {
        return tokens.get(current);
    }

    private SqlLexer.Token previous() {
        return tokens.get(current - 1);
    }
}

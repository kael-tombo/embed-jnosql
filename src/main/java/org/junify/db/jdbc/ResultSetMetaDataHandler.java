package org.junify.db.jdbc;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Types;
import java.util.List;

/**
 * Backs JDBC {@link ResultSetMetaData}.
 *
 * <p>JunifyDB's SQL dialect is dynamically typed over a document store and tracks no declared
 * column type, so every column reports {@link Types#VARCHAR}. That is stated rather than
 * guessed — a driver that invented numeric or temporal types would mislead ORM mapping code
 * that branches on them.</p>
 */
final class ResultSetMetaDataHandler implements InvocationHandler {

    private final List<String> columns;

    ResultSetMetaDataHandler(List<String> columns) {
        this.columns = columns;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        switch (name) {
            case "equals": return proxy == args[0];
            case "hashCode": return System.identityHashCode(proxy);
            case "toString": return "JunifyDB ResultSetMetaData" + columns;
            case "unwrap":
                if (((Class<?>) args[0]).isInstance(proxy)) return proxy;
                throw new SQLException("Not a wrapper for " + args[0]);
            case "isWrapperFor": return ((Class<?>) args[0]).isInstance(proxy);

            case "getColumnCount": return columns.size();
            case "getColumnLabel":
            case "getColumnName": return columnName(args);
            case "getColumnType": return Types.VARCHAR;
            case "getColumnTypeName": return "VARCHAR";
            case "getColumnClassName": return String.class.getName();
            case "isNullable": return ResultSetMetaData.columnNullable;
            case "isAutoIncrement":
            case "isCurrency":
            case "isSigned":
            case "isWritable":
            case "isDefinitelyWritable": return false;
            case "isCaseSensitive":
            case "isSearchable": return true;
            case "isReadOnly": return true;
            case "getColumnDisplaySize":
            case "getPrecision":
            case "getScale": return 0;
            case "getSchemaName":
            case "getTableName":
            case "getCatalogName": return "";
            default:
                throw new SQLFeatureNotSupportedException(
                        "ResultSetMetaData." + name + " is not supported by the JunifyDB JDBC driver");
        }
    }

    private String columnName(Object[] args) throws SQLException {
        int column = (Integer) args[0];
        if (column < 1 || column > columns.size()) {
            throw new SQLException("Column index out of range: " + column);
        }
        return columns.get(column - 1);
    }
}

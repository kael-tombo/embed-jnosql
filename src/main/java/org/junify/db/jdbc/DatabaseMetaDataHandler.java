package org.junify.db.jdbc;

import org.junify.db.JunifyDB;
import org.junify.db.sql.SqlResultSet;
import org.junify.db.sql.SqlRow;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Backs JDBC {@link DatabaseMetaData} with the honest, static facts about this driver.
 *
 * <p>It reports the real product and driver identity and capability flags, but the schema
 * reflection methods ({@code getTables}, {@code getColumns}, {@code getPrimaryKeys}, …) are
 * <b>not</b> implemented and throw {@link SQLFeatureNotSupportedException} rather than returning
 * an empty result set that a tool would read as "this database has no tables".</p>
 */
final class DatabaseMetaDataHandler implements InvocationHandler {

    private final JunifyDB db;
    private final Connection connection;
    private final String url;
    private final String user;

    private DatabaseMetaDataHandler(JunifyDB db, Connection connection, String url, String user) {
        this.db = db;
        this.connection = connection;
        this.url = url;
        this.user = user;
    }

    static DatabaseMetaData proxy(JunifyDB db, Connection connection, String url, String user) {
        return (DatabaseMetaData) Proxy.newProxyInstance(
                DatabaseMetaDataHandler.class.getClassLoader(),
                new Class<?>[]{DatabaseMetaData.class},
                new DatabaseMetaDataHandler(db, connection, url, user));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        switch (name) {
            case "equals": return proxy == args[0];
            case "hashCode": return System.identityHashCode(proxy);
            case "toString": return "JunifyDB DatabaseMetaData";
            case "unwrap":
                if (((Class<?>) args[0]).isInstance(proxy)) return proxy;
                throw new SQLException("Not a wrapper for " + args[0]);
            case "isWrapperFor": return ((Class<?>) args[0]).isInstance(proxy);

            // ---- identity ----
            case "getDatabaseProductName": return "JunifyDB";
            case "getDatabaseProductVersion": return "1.0.0";
            case "getDriverName": return "JunifyDB JDBC Driver";
            case "getDriverVersion": return "0.1";
            case "getDriverMajorVersion": return 0;
            case "getDriverMinorVersion": return 1;
            case "getDatabaseMajorVersion": return 1;
            case "getDatabaseMinorVersion": return 0;
            case "getJDBCMajorVersion": return 4;
            case "getJDBCMinorVersion": return 3;
            case "getURL": return url;
            case "getUserName": return user == null ? "" : user;
            case "getConnection": return connection;
            case "isReadOnly": return true;

            // ---- naming conventions ----
            case "getIdentifierQuoteString": return "\"";
            case "getCatalogSeparator": return ".";
            case "getCatalogTerm": return "catalog";
            case "getSchemaTerm": return "schema";
            case "getProcedureTerm": return "procedure";
            case "getSearchStringEscape": return "\\";
            case "getExtraNameCharacters": return "";
            case "getSQLKeywords": return "";
            case "getNumericFunctions": case "getStringFunctions": case "getSystemFunctions":
            case "getTimeDateFunctions": return "";
            case "storesLowerCaseIdentifiers": return true;
            case "storesUpperCaseIdentifiers": case "storesMixedCaseIdentifiers":
            case "supportsMixedCaseIdentifiers": case "supportsMixedCaseQuotedIdentifiers":
            case "storesUpperCaseQuotedIdentifiers": case "storesLowerCaseQuotedIdentifiers":
            case "storesMixedCaseQuotedIdentifiers": return false;
            case "nullsAreSortedAtEnd": return true;
            case "nullsAreSortedHigh": case "nullsAreSortedLow": case "nullsAreSortedAtStart": return false;
            case "usesLocalFiles": case "usesLocalFilePerTable": return true;
            case "allProceduresAreCallable": return false;
            case "allTablesAreSelectable": return true;
            case "supportsAlterTableWithAddColumn": case "supportsAlterTableWithDropColumn":
            case "supportsANSI92EntryLevelSQL": case "supportsANSI92IntermediateSQL":
            case "supportsANSI92FullSQL": case "supportsTransactions":
            case "supportsSavepoints": case "supportsStoredProcedures":
            case "supportsBatchUpdates": case "supportsUnionAll": case "supportsUnion":
            case "supportsOuterJoins": case "supportsFullOuterJoins": case "supportsLimitedOuterJoins":
            case "supportsGroupBy": case "supportsGroupByUnrelated": case "supportsGroupByBeyondSelect":
            case "supportsOrderByUnrelated": case "supportsColumnAliasing":
            case "supportsExpressionsInOrderBy": case "supportsTableCorrelationNames":
            case "supportsDifferentTableCorrelationNames": case "supportsSchemasInDataManipulation":
            case "dataDefinitionCausesTransactionCommit": case "dataDefinitionIgnoredInTransactions":
                return false;
            case "supportsSelectForUpdate": return false;
            case "supportsMultipleResultSets": case "supportsMultipleOpenResults": return false;
            case "supportsGetGeneratedKeys": return false;
            case "getMaxConnections": case "getMaxStatementLength": case "getMaxTablesInSelect":
            case "getMaxColumnsInTable": case "getMaxColumnsInSelect": case "getMaxRowSize":
            case "getMaxTableNameLength": case "getMaxColumnNameLength": return 0;

            // ---- table types: SQL tables are JunifyDB collections ----
            case "getTableTypes": {
                String column = "TABLE_TYPE";
                Map<String, Object> row = new LinkedHashMap<>();
                row.put(column, "TABLE");
                SqlResultSet rs = SqlResultSet.ofRows(List.of(new SqlRow(row, List.of(column))), List.of(column));
                return ResultSetHandler.proxy(rs, null);
            }

            case "getCatalogs": case "getSchemas": case "getTables": case "getColumns":
            case "getPrimaryKeys": case "getImportedKeys": case "getExportedKeys":
            case "getIndexInfo": case "getTypeInfo": case "getProcedures":
            case "getTablePrivileges": case "getColumnPrivileges":
                throw new SQLFeatureNotSupportedException(
                        "DatabaseMetaData." + name + " is not implemented by the JunifyDB JDBC driver");

            default:
                throw new SQLFeatureNotSupportedException(
                        "DatabaseMetaData." + name + " is not supported by the JunifyDB JDBC driver");
        }
    }

}

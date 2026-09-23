package org.junify.db.jdbc;

import org.junify.db.JunifyDB;
import org.junify.db.config.JunifyDBConfig;
import org.junify.db.config.JunifyDBConfig.StorageEngineType;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Map;

/**
 * Shared helpers for the JDBC driver: URL parsing and value conversion.
 *
 * <p><b>Driver scope (honest).</b> This is a first, working JDBC driver for the JunifyDB SQL
 * dialect. It supports connect / {@code Statement} / {@code PreparedStatement} / forward-only
 * read-only {@code ResultSet} navigation and metadata. It is <b>not</b> a full JDBC
 * implementation: updatable result sets, batch execution, savepoints, and most
 * {@code DatabaseMetaData} methods are unimplemented and throw
 * {@link java.sql.SQLFeatureNotSupportedException}. The SQL dialect's own limits (no
 * {@code ALTER}, no views, no stored procedures) still apply.</p>
 *
 * <p>URL forms:</p>
 * <ul>
 *   <li>{@code jdbc:junifydb:memory:} — in-memory, discarded on close</li>
 *   <li>{@code jdbc:junifydb:file:<dir>} — file-backed under {@code <dir>}</li>
 *   <li>{@code jdbc:junifydb:<dir>} — file-backed (short form)</li>
 * </ul>
 */
final class JdbcSupport {

    static final String URL_PREFIX = "jdbc:junifydb:";

    private JdbcSupport() {}

    static boolean accepts(String url) {
        return url != null && url.startsWith(URL_PREFIX);
    }

    /** Opens the database named by {@code url}. */
    static JunifyDB open(String url) throws SQLException {
        if (!accepts(url)) {
            throw new SQLException("Unsupported JDBC URL: " + url + " (expected " + URL_PREFIX + "...)");
        }
        String rest = url.substring(URL_PREFIX.length());
        try {
            if (rest.isEmpty() || rest.equals("memory:") || rest.equals("memory")
                    || rest.equals(":")) {
                return JunifyDB.inMemory();
            }
            String dir = rest.startsWith("file:") ? rest.substring("file:".length()) : rest;
            if (dir.isEmpty()) {
                return JunifyDB.inMemory();
            }
            return JunifyDB.create(JunifyDB.embed()
                    .storageEngine(StorageEngineType.FILE)
                    .persistTo(dir)
                    .buildConfig());
        } catch (RuntimeException e) {
            throw new SQLException("Could not open " + url + ": " + e.getMessage(), e);
        }
    }

    /**
     * Converts a stored value to the requested JDBC type. JunifyDB's SQL dialect is dynamically
     * typed, so this is a best-effort coercion: numeric strings parse, and a failed coercion is a
     * clear {@link SQLException} rather than a silent wrong value.
     */
    static Object convert(Object value, Class<?> target) throws SQLException {
        if (value == null) return null;
        if (target == null || target == Object.class || target.isInstance(value)) return value;

        try {
            if (target == String.class) return value.toString();
            if (target == Integer.class || target == int.class) return toNumber(value).intValue();
            if (target == Long.class || target == long.class) return toNumber(value).longValue();
            if (target == Short.class || target == short.class) return toNumber(value).shortValue();
            if (target == Byte.class || target == byte.class) return toNumber(value).byteValue();
            if (target == Double.class || target == double.class) return toNumber(value).doubleValue();
            if (target == Float.class || target == float.class) return toNumber(value).floatValue();
            if (target == BigDecimal.class) {
                return value instanceof BigDecimal bd ? bd : new BigDecimal(value.toString());
            }
            if (target == Boolean.class || target == boolean.class) {
                if (value instanceof Boolean b) return b;
                if (value instanceof Number n) return n.doubleValue() != 0;
                return Boolean.parseBoolean(value.toString());
            }
            if (target == byte[].class) {
                return value instanceof byte[] bytes ? bytes : value.toString().getBytes();
            }
            if (target == Timestamp.class) return Timestamp.from(toInstant(value));
            if (target == Date.class) return Date.valueOf(toInstant(value).atZone(ZoneOffset.UTC).toLocalDate());
            if (target == Time.class) return Time.valueOf(toInstant(value).atZone(ZoneOffset.UTC).toLocalTime());
            if (target == LocalDateTime.class) {
                return LocalDateTime.ofInstant(toInstant(value), ZoneOffset.UTC);
            }
            if (target == LocalDate.class) {
                return toInstant(value).atZone(ZoneOffset.UTC).toLocalDate();
            }
            if (target == LocalTime.class) {
                return toInstant(value).atZone(ZoneOffset.UTC).toLocalTime();
            }
        } catch (NumberFormatException e) {
            throw new SQLException("Cannot convert '" + value + "' to " + target.getSimpleName());
        }
        return value;
    }

    private static Number toNumber(Object value) {
        if (value instanceof Number n) return n;
        if (value instanceof Boolean b) return b ? 1 : 0;
        return new BigDecimal(value.toString().trim());
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant i) return i;
        if (value instanceof Number n) return Instant.ofEpochMilli(n.longValue());
        String s = value.toString().trim();
        try {
            return Instant.parse(s);
        } catch (RuntimeException ignored) {
            // fall through to epoch-millis
        }
        return Instant.ofEpochMilli(Long.parseLong(s));
    }

    /** Shallow, human-readable value used by {@code toString} of rows/columns in errors. */
    static String describe(Object value) {
        if (value == null) return "null";
        if (value instanceof Map<?, ?> || value instanceof java.util.List<?>) return value.toString();
        return value.toString();
    }
}

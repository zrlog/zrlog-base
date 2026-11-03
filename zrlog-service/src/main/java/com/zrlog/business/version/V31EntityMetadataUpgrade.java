package com.zrlog.business.version;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.ResultValueConvertUtils;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.BooleanSupplier;

/**
 * The explicit version-31 migration. This is not runtime Entity schema synchronization.
 */
public final class V31EntityMetadataUpgrade {
    private static final int BATCH_SIZE = 20; // CASE update stays below D1's parameter limit.
    private static final Map<String, String> TABLES = tables();
    private static final Set<String> INTEGER_CREATION = Set.of(
            "user_passkey", "user_passkey_challenge", "oauth_grant", "user_access_token");
    private final DataSourceWrapper source;
    private final DAO dao;
    private final boolean sqlite;
    private final boolean mysql;
    private final BooleanSupplier cancelled;

    public V31EntityMetadataUpgrade(DataSourceWrapper source) {
        this(source, () -> false);
    }

    public V31EntityMetadataUpgrade(DataSourceWrapper source, BooleanSupplier cancelled) {
        this.source = source;
        this.cancelled = cancelled;
        this.dao = new DAO(source);
        String url = source.getDataSourceProperties().getProperty("jdbcUrl", "").toLowerCase(Locale.ROOT);
        sqlite = source.isWebApi() || url.startsWith("jdbc:sqlite:");
        mysql = url.startsWith("jdbc:mysql:") || url.startsWith("jdbc:mariadb:");
    }

    private static long epoch(Object value) throws SQLException {
        try {
            return new BigDecimal(value.toString()).longValueExact();
        } catch (RuntimeException e) {
            throw new SQLException("Invalid historical millisecond time", e);
        }
    }

    private static boolean integer(String type) {
        return type != null && type.contains("INT");
    }

    private static boolean dateTime(String type) {
        return type != null && (type.startsWith("DATETIME") || type.startsWith("TIMESTAMP"));
    }

    private static String q(String name) {
        return "`" + name + "`";
    }

    private static Map<String, String> tables() {
        Map<String, String> tables = new LinkedHashMap<>();
        tables.put("link", "linkId");
        tables.put("lognav", "navId");
        tables.put("plugin", "pluginId");
        tables.put("tag", "tagId");
        tables.put("type", "typeId");
        tables.put("user", "userId");
        tables.put("user_passkey", "id");
        tables.put("user_passkey_challenge", "id");
        tables.put("log", "logId");
        tables.put("log_extension_index", "id");
        tables.put("comment", "commentId");
        tables.put("log_version", "id");
        tables.put("website", "siteId");
        tables.put("oauth_client", "clientId");
        tables.put("oauth_grant", "id");
        tables.put("oauth_credential", "hash");
        tables.put("user_access_token", "id");
        return tables;
    }

    public void run() throws SQLException {
        for (Map.Entry<String, String> table : TABLES.entrySet()) {
            String name = table.getKey();
            // This snapshot belongs to this table and run only. Successful DDL updates it;
            // uncertain DDL refreshes it from the database before any subsequent step.
            Map<String, String> columns = columns(name);
            if (INTEGER_CREATION.contains(name)) {
                migrateCreationTime(name, table.getValue(), columns);
            }
            String created = name.equals("log_version") ? "created_at" : name.equals("comment") ? "td" : "createdAt";
            String updated = name.equals("log") ? "last_update_date" : "updatedAt";
            ensureColumn(name, created, "DATETIME(3) DEFAULT NULL", false, columns);
            ensureColumn(name, updated, "DATETIME(3) DEFAULT NULL", false, columns);
            if (!ensureColumn(name, "version", "BIGINT NOT NULL DEFAULT 0", true, columns)) {
                // A newly added NOT NULL DEFAULT 0 column needs no full-table repair or scan.
                // Existing log.version may be NULL; preserve every known revision.
                execute("UPDATE " + q(name) + " SET version=0 WHERE version IS NULL");
                checkRunning();
                if (((Number) dao.queryFirstObj("SELECT count(*) FROM " + q(name) + " WHERE version<0")).longValue() != 0) {
                    throw new SQLException("Negative row version in " + name + "; migration stopped");
                }
            }
        }
    }

    private void migrateCreationTime(String table, String key, Map<String, String> columns) throws SQLException {
        String column = "createdAt";
        String staging = "createdAt_v31";
        String backup = "createdAt_epoch_v31";
        if (!columns.containsKey(column) && !columns.containsKey(backup)) {
            throw new SQLException("Missing historical creation time in " + table);
        }
        if (columns.containsKey(column) && integer(columns.get(column))) {
            if (columns.containsKey(backup)) {
                throw new SQLException("Ambiguous migration columns in " + table);
            }
            ensureColumn(table, staging, "DATETIME(3) DEFAULT NULL", false, columns);
            copyEpochTimes(table, key, column, staging);
            verifyTimes(table, key, column, staging);
            rename(table, column, backup, "BIGINT NULL", columns);
        }
        if (!columns.containsKey(column) && columns.containsKey(backup)) {
            if (!dateTime(columns.get(staging)) || !integer(columns.get(backup))) {
                throw new SQLException("Incomplete date migration in " + table);
            }
            rename(table, staging, column, "DATETIME(3) DEFAULT NULL", columns);
        }
        if (!dateTime(columns.get(column))) {
            throw new SQLException("Unexpected creation time type in " + table);
        }
        if (columns.containsKey(backup)) {
            // Keep the original integers until every converted value has been checked.
            verifyTimes(table, key, backup, column);
            drop(table, backup, columns);
        }
        if (columns.containsKey(staging)) {
            throw new SQLException("Unexpected staging column in " + table);
        }
    }

    private void copyEpochTimes(String table, String key, String original, String target) throws SQLException {
        Object after = null;
        while (true) {
            List<Map<String, Object>> rows = page(table, key, original, target, after);
            if (rows.isEmpty()) {
                return;
            }
            List<Object> values = new ArrayList<>();
            List<Object> keys = new ArrayList<>();
            StringBuilder cases = new StringBuilder();
            for (Map<String, Object> row : rows) {
                after = row.get("row_id");
                Object old = row.get("old_time");
                if (old == null || row.get("new_time") != null) {
                    continue;
                }
                long epoch = epoch(old);
                Timestamp time = new Timestamp(epoch);
                int year = time.toLocalDateTime().getYear();
                if (year < 1000 || year > 9999) {
                    throw new SQLException("Creation time outside SQL DATETIME range in " + table);
                }
                cases.append(" WHEN ? THEN ?");
                values.add(after);
                // Explicit SQL date/time text keeps milliseconds through the legacy WebAPI runner.
                values.add(time.toString());
                keys.add(after);
            }
            if (!keys.isEmpty()) {
                values.addAll(keys);
                execute("UPDATE " + q(table) + " SET " + q(target) + "=CASE " + q(key) + cases
                                + " END WHERE " + q(target) + " IS NULL AND " + q(key) + " IN ("
                                + String.join(",", java.util.Collections.nCopies(keys.size(), "?")) + ")",
                        values.toArray());
            }
        }
    }

    private void verifyTimes(String table, String key, String original, String target) throws SQLException {
        Object after = null;
        while (true) {
            List<Map<String, Object>> rows = page(table, key, original, target, after);
            if (rows.isEmpty()) {
                return;
            }
            for (Map<String, Object> row : rows) {
                after = row.get("row_id");
                Object old = row.get("old_time");
                Object actual = row.get("new_time");
                if (old == null ? actual != null : actual == null || epoch(old) != ResultValueConvertUtils.parseDate(actual)) {
                    throw new SQLException("Creation time verification failed in " + table + "; original column retained");
                }
            }
        }
    }

    private List<Map<String, Object>> page(String table, String key, String old, String current, Object after) throws SQLException {
        checkRunning();
        String original = source.isWebApi() ? "CAST(" + q(old) + " AS TEXT)" : q(old);
        String sql = "SELECT " + q(key) + " AS row_id," + original + " AS old_time," + q(current)
                + " AS new_time FROM " + q(table) + (after == null ? "" : " WHERE " + q(key) + ">?")
                + " ORDER BY " + q(key) + " LIMIT " + BATCH_SIZE;
        return after == null ? dao.queryListWithParams(sql) : dao.queryListWithParams(sql, after);
    }

    private boolean ensureColumn(String table, String column, String definition, boolean numeric,
                                 Map<String, String> columns) throws SQLException {
        boolean added = false;
        if (!columns.containsKey(column)) {
            try {
                execute("ALTER TABLE " + q(table) + " ADD COLUMN " + q(column) + " " + definition);
                columns.put(column, definition.split(" ", 2)[0]);
                added = true;
            } catch (SQLException failure) {
                // A driver may report an error after a DDL statement was already committed.
                refreshColumns(table, columns, failure);
                if (!columns.containsKey(column)) {
                    throw failure;
                }
            }
        }
        String type = columns.get(column);
        if (!(numeric ? integer(type) : dateTime(type))) {
            throw new SQLException("Unexpected type for " + table + "." + column + "; migration stopped");
        }
        return added;
    }

    private void rename(String table, String from, String to, String definition,
                        Map<String, String> columns) throws SQLException {
        String sql = mysql ? "ALTER TABLE " + q(table) + " CHANGE COLUMN " + q(from) + " " + q(to) + " " + definition
                : "ALTER TABLE " + q(table) + " RENAME COLUMN " + q(from) + " TO " + q(to);
        try {
            execute(sql);
            columns.remove(from);
            columns.put(to, definition.split(" ", 2)[0]);
        } catch (SQLException failure) {
            refreshColumns(table, columns, failure);
            if (columns.containsKey(from) || !columns.containsKey(to)) {
                throw failure;
            }
        }
    }

    private void drop(String table, String column, Map<String, String> columns) throws SQLException {
        try {
            execute("ALTER TABLE " + q(table) + " DROP COLUMN " + q(column));
            columns.remove(column);
        } catch (SQLException failure) {
            refreshColumns(table, columns, failure);
            if (columns.containsKey(column)) {
                throw failure;
            }
        }
    }

    private void refreshColumns(String table, Map<String, String> columns, SQLException failure) throws SQLException {
        try {
            Map<String, String> actual = columns(table);
            columns.clear();
            columns.putAll(actual);
        } catch (SQLException readFailure) {
            failure.addSuppressed(readFailure);
            throw failure;
        }
    }

    private Map<String, String> columns(String table) throws SQLException {
        checkRunning();
        Map<String, String> result = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (sqlite) {
            for (Map<String, Object> row : dao.queryListWithParams("PRAGMA table_info(" + q(table) + ")")) {
                result.put((String) row.get("name"), row.get("type").toString().toUpperCase(Locale.ROOT));
            }
        } else {
            try (Connection connection = source.getConnection();
                 ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, table, null)) {
                while (columns.next()) {
                    result.put(columns.getString("COLUMN_NAME"), columns.getString("TYPE_NAME").toUpperCase(Locale.ROOT));
                }
            }
        }
        if (result.isEmpty()) {
            throw new SQLException("Expected installed table is missing: " + table);
        }
        return result;
    }

    private void checkRunning() throws SQLException {
        if (cancelled.getAsBoolean()) {
            throw new SQLException("Schema migration cancelled because the application is stopping");
        }
    }

    private void execute(String sql, Object... params) throws SQLException {
        checkRunning();
        dao.execute(sql, params);
    }
}

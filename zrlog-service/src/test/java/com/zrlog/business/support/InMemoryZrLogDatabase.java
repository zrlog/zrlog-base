package com.zrlog.business.support;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.InMemoryDatabase;
import com.hibegin.common.dao.SqlConvertUtils;
import com.hibegin.common.util.IOUtil;
import com.zrlog.util.DataSourceUtil;
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.commons.dbutils.handlers.ScalarHandler;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

public class InMemoryZrLogDatabase implements AutoCloseable {

    private final DataSourceWrapper dataSource;
    private final InMemoryDatabase database;
    private final DatabaseType databaseType;
    private final Path sqliteFile;

    private InMemoryZrLogDatabase(DatabaseType databaseType) throws Exception {
        this.databaseType = databaseType;
        this.sqliteFile = databaseType == DatabaseType.SQLITE
                ? Files.createTempFile("zrlog-base-test-", ".db")
                : null;
        this.dataSource = newDataSource(databaseType, sqliteFile);
        this.database = InMemoryDatabase.open(dataSource, true);
        loadSchema();
    }

    public static InMemoryZrLogDatabase open() throws Exception {
        return open(DatabaseType.H2);
    }

    public static InMemoryZrLogDatabase open(DatabaseType databaseType) throws Exception {
        return new InMemoryZrLogDatabase(databaseType);
    }

    public DataSourceWrapper dataSource() {
        return dataSource;
    }

    public int update(String sql, Object... params) throws SQLException {
        return dataSource.getQueryRunner().update(sql, params);
    }

    public Object scalar(String sql, Object... params) throws SQLException {
        return dataSource.getQueryRunner().query(sql, new ScalarHandler<>(1), params);
    }

    public Map<String, Object> queryOne(String sql, Object... params) throws SQLException {
        return dataSource.getQueryRunner().query(sql, new MapHandler(), params);
    }

    public List<Map<String, Object>> queryList(String sql, Object... params) throws SQLException {
        return dataSource.getQueryRunner().query(sql, new MapListHandler(), params);
    }

    private static DataSourceWrapper newDataSource(DatabaseType databaseType, Path sqliteFile) {
        Properties properties;
        if (databaseType == DatabaseType.H2) {
            properties = InMemoryDatabase.h2Properties("zrlog_base_" + UUID.randomUUID());
        } else {
            properties = new Properties();
            properties.setProperty("driverClass", "org.sqlite.JDBC");
            properties.setProperty("jdbcUrl", "jdbc:sqlite:" + sqliteFile.toAbsolutePath().normalize()
                    + "?journal_mode=WAL&busy_timeout=10000&foreign_keys=on&synchronous=NORMAL"
                    + "&date_class=TEXT&date_string_format=yyyy-MM-dd HH:mm:ss");
            properties.setProperty("user", "");
            properties.setProperty("password", "");
        }
        return DataSourceUtil.buildDataSource(properties);
    }

    private void loadSchema() throws Exception {
        try (InputStream input = InMemoryZrLogDatabase.class.getResourceAsStream("/init-table-structure.sql")) {
            if (input == null) {
                throw new IllegalStateException("Missing init-table-structure.sql from zrlog-install-web test dependency");
            }
            if (databaseType == DatabaseType.H2) {
                database.loadMySQLSchema(input);
            } else {
                String sql = IOUtil.getStringInputStream(input);
                List<String> statements = new ArrayList<>();
                for (String statement : SqlConvertUtils.doMySQLToSqliteBySqlText(sql)) {
                    if (!SqlConvertUtils.isBatchDropTableSql(statement)) {
                        statements.add(statement);
                    }
                }
                database.executeStatements(statements);
            }
            ensureStickyColumn();
        }
    }

    private void ensureStickyColumn() throws SQLException {
        try {
            dataSource.getQueryRunner().query("select sticky from log where 1=0", new ScalarHandler<>(1));
        } catch (SQLException e) {
            dataSource.getQueryRunner().update("alter table log add column sticky integer not null default 0");
        }
    }

    @Override
    public void close() throws Exception {
        try {
            database.close();
        } finally {
            if (sqliteFile != null) {
                Files.deleteIfExists(sqliteFile);
                Files.deleteIfExists(Path.of(sqliteFile + "-wal"));
                Files.deleteIfExists(Path.of(sqliteFile + "-shm"));
            }
        }
    }

    public enum DatabaseType {
        H2,
        SQLITE
    }
}

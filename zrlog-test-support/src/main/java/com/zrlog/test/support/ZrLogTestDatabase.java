package com.zrlog.test.support;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.InMemoryDatabase;
import com.hibegin.common.dao.SqlConvertUtils;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;

import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.Collectors;

/** Isolated schema fixture. Installation behavior must be tested through InstallService instead. */
public final class ZrLogTestDatabase implements AutoCloseable {

    public enum DatabaseType { H2, SQLITE }

    @FunctionalInterface
    public interface SqlHook { void run(String sql, Object[] params) throws SQLException; }

    private final Path sqliteFile;
    private final Properties properties;
    private final InMemoryDatabase database;
    private DataSourceWrapper activeDataSource;
    private volatile SqlHook beforeWebApiUpdate = (sql, params) -> { };
    private volatile SqlHook afterWebApiQuery = (sql, params) -> { };

    private ZrLogTestDatabase(DatabaseType type) throws Exception {
        sqliteFile = type == DatabaseType.SQLITE ? Files.createTempFile("zrlog-test-", ".db") : null;
        properties = sqliteFile == null ? InMemoryDatabase.h2Properties("zrlog_test_" + UUID.randomUUID())
                : sqliteProperties(sqliteFile);
        InMemoryDatabase opened = null;
        try {
            opened = InMemoryDatabase.open(properties, true);
            try (InputStream input = ZrLogTestDatabase.class.getResourceAsStream("/init-table-structure.sql")) {
                if (input == null) {
                    throw new IllegalStateException("Missing init-table-structure.sql; add zrlog-install-web with test scope");
                }
                if (sqliteFile == null) {
                    opened.loadMySQLSchema(input);
                } else {
                    opened.executeStatements(SqlConvertUtils.doMySQLToSqliteBySqlText(
                                    new String(input.readAllBytes(), StandardCharsets.UTF_8)).stream()
                            .filter(sql -> !SqlConvertUtils.isBatchDropTableSql(sql)).collect(Collectors.toList()));
                }
            }
        } catch (Exception | Error failure) {
            try {
                if (opened != null) opened.close();
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            try {
                deleteSqliteFiles();
            } catch (Exception cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        database = opened;
        activeDataSource = database.dataSource();
    }

    public static ZrLogTestDatabase open() throws Exception { return open(DatabaseType.H2); }

    public static ZrLogTestDatabase open(DatabaseType type) throws Exception {
        return new ZrLogTestDatabase(java.util.Objects.requireNonNull(type));
    }

    /** SQLite-backed, single-statement adapter for D1-style behavior; no JDBC transactions. */
    public static ZrLogTestDatabase openWebApi() throws Exception {
        ZrLogTestDatabase db = open(DatabaseType.SQLITE);
        QueryRunner runner = new QueryRunner() {
            @Override public int update(String sql, Object... params) throws SQLException {
                db.beforeWebApiUpdate.run(sql, params);
                return db.database.update(sql, params);
            }

            @Override public <T> T query(String sql, ResultSetHandler<T> handler, Object... params) throws SQLException {
                T result = db.database.dataSource().getQueryRunner().query(sql, handler, params);
                db.afterWebApiQuery.run(sql, params);
                return result;
            }
        };
        db.activeDataSource = (DataSourceWrapper) Proxy.newProxyInstance(DataSourceWrapper.class.getClassLoader(),
                new Class<?>[]{DataSourceWrapper.class}, (proxy, method, args) -> {
                    if (method.getName().equals("isWebApi")) return true;
                    if (method.getName().equals("getQueryRunner")) return runner;
                    if (method.getName().equals("getConnection")) throw new AssertionError("Web API cannot open JDBC transactions");
                    try { return method.invoke(db.database.dataSource(), args); }
                    catch (InvocationTargetException e) { throw e.getCause(); }
                });
        DAO.setDs(db.activeDataSource);
        return db;
    }

    public DataSourceWrapper dataSource() { return activeDataSource; }

    public Properties properties() {
        Properties copy = new Properties();
        copy.putAll(properties);
        return copy;
    }

    public void beforeWebApiUpdate(SqlHook hook) { beforeWebApiUpdate = java.util.Objects.requireNonNull(hook); }
    public void afterWebApiQuery(SqlHook hook) { afterWebApiQuery = java.util.Objects.requireNonNull(hook); }

    // Fixture setup/assertions use the underlying connection, without triggering application hooks.
    public int update(String sql, Object... params) throws SQLException { return database.update(sql, params); }
    public boolean execute(String sql, Object... params) throws SQLException { return database.execute(sql, params); }
    public Object scalar(String sql, Object... params) throws SQLException { return database.scalar(sql, params); }
    public Map<String, Object> queryOne(String sql, Object... params) throws SQLException { return database.queryOne(sql, params); }
    public List<Map<String, Object>> queryList(String sql, Object... params) throws SQLException { return database.queryList(sql, params); }

    public void putWebsite(String name, Object value) throws SQLException {
        update("delete from website where name=?", name);
        update("insert into website(name,value) values(?,?)", name, value == null ? null : value.toString());
    }

    private static Properties sqliteProperties(Path file) {
        Properties properties = new Properties();
        properties.setProperty("driverClass", "org.sqlite.JDBC");
        properties.setProperty("jdbcUrl", "jdbc:sqlite:" + file
                + "?journal_mode=WAL&busy_timeout=10000&foreign_keys=on&synchronous=NORMAL"
                + "&date_class=TEXT&date_string_format=yyyy-MM-dd HH:mm:ss");
        properties.setProperty("user", "");
        properties.setProperty("password", "");
        return properties;
    }

    private void deleteSqliteFiles() throws Exception {
        if (sqliteFile != null) {
            Files.deleteIfExists(sqliteFile);
            Files.deleteIfExists(Path.of(sqliteFile + "-wal"));
            Files.deleteIfExists(Path.of(sqliteFile + "-shm"));
        }
    }

    @Override public void close() throws Exception {
        try { database.close(); }
        finally { deleteSqliteFiles(); }
    }
}

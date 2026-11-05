package com.zrlog.business.service;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.SqlConvertUtils;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.business.version.UpgradeVersionHandler;
import com.zrlog.business.version.UpgradeVersionHandlerHelpers;
import com.zrlog.business.version.V31EntityMetadataUpgrade;
import com.zrlog.common.CacheService;
import com.zrlog.data.repository.WebSiteRepository;
import com.zrlog.data.service.DistributedLock;
import com.zrlog.data.util.DistributedLockManager;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import dagger.assisted.AssistedInject;
import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 程序升级设计到数据表结构变更，数据的更新 */
public class DbUpgradeService implements AutoCloseable {

    private static final Logger LOGGER = LoggerUtil.getLogger(DbUpgradeService.class);

    private final DAO dao;
    private final DataSourceWrapper dataSource;
    private final String dbName;
    private final long currentSqlVersion;
    private final Object migrationMonitor = new Object();
    private final DistributedLockManager locks;
    private volatile boolean cancelled;
    private Thread migrationThread;
    private DistributedLock acquiredLock;

    public DbUpgradeService(DataSourceWrapper dataSource, long currentSqlVersion) {
        this(dataSource, new DAO(dataSource), currentSqlVersion);
    }

    public DbUpgradeService(DataSourceWrapper dataSource, DAO dao, long currentSqlVersion) {
        this(
                dataSource,
                dao,
                new DistributedLockManager(
                        () -> new WebSiteRepository(dataSource),
                        key -> new DistributedLock(() -> new WebSiteRepository(dataSource), key)),
                currentSqlVersion);
    }

    @AssistedInject
    public DbUpgradeService(
            DataSourceWrapper dataSource,
            DAO dao,
            DistributedLockManager locks,
            @Assisted long currentSqlVersion) {
        this.dataSource = dataSource;
        this.dao = dao;
        this.dbName = resolveDbName(dataSource.getDataSourceProperties().getProperty("jdbcUrl"));
        this.currentSqlVersion = currentSqlVersion;
        this.locks = locks;
    }

    static String resolveDbName(String jdbcUrl) {
        if (StringUtils.isEmpty(jdbcUrl)) {
            return "";
        }
        String normalizedUrl = jdbcUrl.replace("jdbc:", "");
        String urlWithoutParams = normalizedUrl.split("[?;]", 2)[0];
        try {
            URI uri = URI.create(urlWithoutParams);
            String path = uri.getPath();
            if (StringUtils.isNotEmpty(path) && path.length() > 1) {
                String pathWithoutLeadingSlash = path.substring(1);
                int slashIndex = pathWithoutLeadingSlash.lastIndexOf('/');
                if (slashIndex >= 0 && slashIndex < pathWithoutLeadingSlash.length() - 1) {
                    return pathWithoutLeadingSlash.substring(slashIndex + 1);
                }
                return pathWithoutLeadingSlash;
            }
        } catch (IllegalArgumentException ignored) {
        }
        int slashIndex = urlWithoutParams.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < urlWithoutParams.length() - 1) {
            return urlWithoutParams.substring(slashIndex + 1);
        }
        int colonIndex = urlWithoutParams.lastIndexOf(':');
        if (colonIndex >= 0 && colonIndex < urlWithoutParams.length() - 1) {
            return urlWithoutParams.substring(colonIndex + 1);
        }
        return urlWithoutParams;
    }

    private static Map<Integer, String> getSqlFileList() {
        Map<Integer, String> fileList = new LinkedHashMap<>();
        for (int i = 1; i <= UpgradeVersionHandler.SQL_VERSION; i++) {
            InputStream sqlStream = PathUtil.getConfInputStream("/update-sql/" + i + ".sql");
            if (Objects.nonNull(sqlStream)) {
                fileList.put(i, IOUtil.getStringInputStream(sqlStream));
            }
        }
        return fileList;
    }

    private List<Map.Entry<Integer, List<String>>> getExecSqlList(Long dbVersion) {
        List<Map.Entry<Integer, List<String>>> sqlList = new ArrayList<>();

        for (Map.Entry<Integer, String> f : getSqlFileList().entrySet()) {
            int fileVersion = f.getKey();
            if (fileVersion > dbVersion) {
                List<String> executableSql;
                if (f.getValue().stripLeading().startsWith("-- dialect: mysql")
                        && !usesMysqlDialect()) {
                    // Keep the version step on every backend, without executing MySQL-only DDL.
                    executableSql = Collections.emptyList();
                } else if (usesSqliteDialect()) {
                    executableSql = SqlConvertUtils.doMySQLToSqliteBySqlText(f.getValue());
                } else {
                    executableSql = SqlConvertUtils.extractExecutableSql(f.getValue());
                }
                Map.Entry<Integer, List<String>> entry =
                        new AbstractMap.SimpleEntry<>(fileVersion, executableSql);
                LOGGER.info(
                        "Need update sql "
                                + fileVersion
                                + ".sql \n"
                                + String.join(";\n", entry.getValue())
                                + ";");
                sqlList.add(entry);
            }
        }
        return sqlList;
    }

    /**
     * 检查数据文件是否需要更新 为了处理由于数据库表的更新，导致系统无法正常使用的情况，通过执行/conf/update-sql/目录下面的*.sql文件来变更数据库的表格式，
     * 来达到系统无需手动执行数据库脚本文件。
     */
    public void tryDoUpgrade() {
        synchronized (migrationMonitor) {
            if (migrationThread != null) {
                throw new IllegalStateException("Schema migration is already running");
            }
            if (cancelled) {
                throw new IllegalStateException("Application is stopping");
            }
            migrationThread = Thread.currentThread();
        }
        try {
            doUpgrade();
        } finally {
            synchronized (migrationMonitor) {
                migrationThread = null;
                migrationMonitor.notifyAll();
            }
        }
    }

    private void doUpgrade() {
        if (currentSqlVersion < 0) {
            return;
        }
        if (getExecSqlList(currentSqlVersion).isEmpty()) {
            return;
        }
        try {
            // The startup version may be stale by the time this instance reaches migration.
            if (getExecSqlList(storedSqlVersion()).isEmpty()) {
                return;
            }
            try (DistributedLock lock =
                    locks.acquire(
                            DistributedLock.SCHEMA_UPGRADE_KEY,
                            30,
                            TimeUnit.SECONDS,
                            () -> cancelled)) {
                acquiredLock = lock;
                // Another instance may have completed migration since this instance read the cache.
                long version = storedSqlVersion();
                LOGGER.info("Db " + dbName + " connect success");
                for (Map.Entry<Integer, List<String>> entry : getExecSqlList(version)) {
                    for (String sql : entry.getValue()) {
                        checkRunning();
                        if (StringUtils.isEmpty(sql.trim())) {
                            continue;
                        }
                        try {
                            dao.execute(sql);
                        } catch (Exception e) {
                            if (!isAlreadyAppliedSqliteSchemaChange(sql, e)
                                    && !(entry.getKey() == 32
                                            && isExistingIndex(sql, e, "assistant_"))
                                    && !(entry.getKey() == 33
                                            && (isExistingIndex(sql, e, "log_public_")
                                                    || isExistingIndex(sql, e, "logid")))) {
                                throw e;
                            }
                            LOGGER.info("Skip already applied schema migration: " + sql);
                        }
                    }
                    if (entry.getKey() == 31) {
                        new V31EntityMetadataUpgrade(dataSource, () -> cancelled).run();
                    }
                    checkRunning();
                    UpgradeVersionHandler handler =
                            UpgradeVersionHandlerHelpers.getUpgradeVersionHandler(entry.getKey());
                    if (handler != null) {
                        handler.doUpgrade(dao);
                    }
                    // Bootstrap SQL must work before the Entity metadata columns exist.
                    checkRunning();
                    Object versionRow =
                            dao.queryFirstObj(
                                    "SELECT siteId FROM website WHERE name=?",
                                    CacheService.ZRLOG_SQL_VERSION_KEY);
                    checkRunning();
                    if (versionRow == null) {
                        dao.execute(
                                "INSERT INTO website(name,value) VALUES(?,?)",
                                CacheService.ZRLOG_SQL_VERSION_KEY,
                                entry.getKey().toString());
                    } else {
                        dao.execute(
                                "UPDATE website SET value=? WHERE name=?",
                                entry.getKey().toString(),
                                CacheService.ZRLOG_SQL_VERSION_KEY);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Database migration failed; application startup stopped", e);
            throw new IllegalStateException(
                    "Database migration failed; application startup stopped", e);
        }
    }

    private long storedSqlVersion() throws java.sql.SQLException {
        checkRunning();
        Object stored =
                dao.queryFirstObj(
                        "SELECT value FROM website WHERE name=?",
                        CacheService.ZRLOG_SQL_VERSION_KEY);
        return stored == null ? currentSqlVersion : Long.parseLong(stored.toString());
    }

    @Override
    public void close() throws java.sql.SQLException {
        cancelled = true;
        boolean interrupted = Thread.interrupted();
        try {
            synchronized (migrationMonitor) {
                if (migrationThread == Thread.currentThread()) {
                    throw new IllegalStateException(
                            "Cannot close the datasource from inside a running schema migration");
                }
                // Let the current SQL finish; interrupting HTTP can leave its remote outcome
                // unknown.
                while (migrationThread != null) {
                    try {
                        migrationMonitor.wait();
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }
                // Retry a failed release from the migration's try-with-resources before datasource
                // shutdown.
                if (acquiredLock != null) {
                    acquiredLock.close();
                }
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void checkRunning() throws java.sql.SQLException {
        if (cancelled) {
            throw new java.sql.SQLException(
                    "Schema migration cancelled because the application is stopping");
        }
    }

    private boolean usesMysqlDialect() {
        if (dataSource.isWebApi()) {
            return false;
        }
        String jdbcUrl = dataSource.getDataSourceProperties().getProperty("jdbcUrl", "");
        return jdbcUrl.regionMatches(true, 0, "jdbc:mysql:", 0, "jdbc:mysql:".length())
                || jdbcUrl.regionMatches(true, 0, "jdbc:mariadb:", 0, "jdbc:mariadb:".length());
    }

    private boolean usesSqliteDialect() {
        if (dataSource.isWebApi()) {
            return true;
        }
        String jdbcUrl = dataSource.getDataSourceProperties().getProperty("jdbcUrl");
        return jdbcUrl != null
                && jdbcUrl.regionMatches(true, 0, "jdbc:sqlite:", 0, "jdbc:sqlite:".length());
    }

    /** Resume the known index-only steps after a partial DDL commit. */
    private boolean isExistingIndex(String sql, Exception error, String prefix) {
        if (!sql.stripLeading().toLowerCase(Locale.ROOT).startsWith("create index `" + prefix))
            return false;
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.sql.SQLException) {
                var failure = (java.sql.SQLException) cause;
                if (usesMysqlDialect() && failure.getErrorCode() == 1061) return true;
                if ("42S11".equals(failure.getSQLState()) && failure.getErrorCode() == 42111)
                    return true;
            }
        }
        return false;
    }

    private boolean isAlreadyAppliedSqliteSchemaChange(String sql, Exception exception) {
        if (!usesSqliteDialect()) {
            return false;
        }
        String normalizedSql = sql.trim().toLowerCase(Locale.ROOT);
        boolean addColumn =
                normalizedSql.startsWith("alter table ") && normalizedSql.contains(" add column ");
        boolean createIndex =
                normalizedSql.startsWith("create index ")
                        || normalizedSql.startsWith("create unique index ");
        if (!addColumn && !createIndex) {
            return false;
        }
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message == null) {
                continue;
            }
            String normalizedMessage = message.toLowerCase(Locale.ROOT);
            if (addColumn && normalizedMessage.contains("duplicate column name")) {
                return true;
            }
            if (createIndex && normalizedMessage.contains("already exists")) {
                return true;
            }
        }
        return false;
    }

    @AssistedFactory
    public interface Factory {
        DbUpgradeService create(long currentSqlVersion);
    }
}

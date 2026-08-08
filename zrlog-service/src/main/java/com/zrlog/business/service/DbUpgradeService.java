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
import com.zrlog.common.CacheService;
import com.zrlog.model.WebSite;

import java.io.InputStream;
import java.net.URI;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 程序升级设计到数据表结构变更，数据的更新
 */
public class DbUpgradeService {


    private static final Logger LOGGER = LoggerUtil.getLogger(DbUpgradeService.class);

    private final DAO dao;
    private final DataSourceWrapper dataSource;
    private final WebSite webSite;
    private final String dbName;
    private final long currentSqlVersion;

    public DbUpgradeService(DataSourceWrapper dataSource,
                            long currentSqlVersion) {
        this.dataSource = dataSource;
        this.dao = new DAO(dataSource);
        this.webSite = new WebSite(dataSource);
        this.dbName = resolveDbName(dataSource.getDataSourceProperties().getProperty("jdbcUrl"));
        this.currentSqlVersion = currentSqlVersion;
    }

    static String resolveDbName(String jdbcUrl) {
        if (StringUtils.isEmpty(jdbcUrl)) {
            return "";
        }
        String normalizedUrl = jdbcUrl.replace("jdbc:", "");
        try {
            URI uri = URI.create(normalizedUrl);
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
        String urlWithoutParams = normalizedUrl.split(";", 2)[0];
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
                if (dataSource.isWebApi()) {
                    executableSql = SqlConvertUtils.doMySQLToSqliteBySqlText(f.getValue());
                } else {
                    executableSql = SqlConvertUtils.extractExecutableSql(f.getValue());
                }
                Map.Entry<Integer, List<String>> entry = new AbstractMap.SimpleEntry<>(fileVersion, executableSql);
                LOGGER.info("Need update sql " + fileVersion + ".sql \n" + String.join(";\n", entry.getValue()) + ";");
                sqlList.add(entry);
            }
        }
        return sqlList;
    }

    /**
     * 检查数据文件是否需要更新
     * 为了处理由于数据库表的更新，导致系统无法正常使用的情况，通过执行/conf/update-sql/目录下面的*.sql文件来变更数据库的表格式，
     * 来达到系统无需手动执行数据库脚本文件。
     */
    public void tryDoUpgrade() {
        try {
            if (currentSqlVersion < 0) {
                return;
            }
            LOGGER.info("Db " + dbName + " connect success");
            List<Map.Entry<Integer, List<String>>> sqlList = getExecSqlList(currentSqlVersion);
            if (sqlList.isEmpty()) {
                return;
            }
            for (Map.Entry<Integer, List<String>> entry : sqlList) {
                //执行需要更新的sql脚本
                try {
                    for (String sql : entry.getValue()) {
                        if (StringUtils.isEmpty(sql.trim())) {
                            continue;
                        }
                        try {
                            dao.execute(sql);
                        } catch (Exception e) {
                            if (!isAlreadyAppliedWebApiSchemaChange(sql, e)) {
                                throw e;
                            }
                            LOGGER.info("Skip already applied WebApi schema migration: " + sql);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "execution sql ", e);
                    //有异常终止升级
                    return;
                }
                //执行需要转换的数据
                try {
                    UpgradeVersionHandler upgradeVersionHandler = UpgradeVersionHandlerHelpers.getUpgradeVersionHandler(entry.getKey());
                    if (Objects.nonNull(upgradeVersionHandler)) {
                        upgradeVersionHandler.doUpgrade(dao);
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "", e);
                    return;
                }
                webSite.updateByKV(CacheService.ZRLOG_SQL_VERSION_KEY, entry.getKey() + "");
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
    }

    private boolean isAlreadyAppliedWebApiSchemaChange(String sql, Exception exception) {
        if (!dataSource.isWebApi()) {
            return false;
        }
        String normalizedSql = sql.trim().toLowerCase(Locale.ROOT);
        boolean addColumn = normalizedSql.startsWith("alter table ") && normalizedSql.contains(" add column ");
        boolean createIndex = normalizedSql.startsWith("create index ")
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
}

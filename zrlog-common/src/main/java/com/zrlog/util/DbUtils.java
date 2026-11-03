package com.zrlog.util;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.IOUtil;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.common.Constants;
import com.zrlog.common.ZrLogConfig;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.SQLRecoverableException;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DbUtils {

    private static final Logger LOGGER = LoggerUtil.getLogger(DbUtils.class);

    /** 将 env 配置的 DB_PROPERTIES 写入到实际的文件中，便于程序读取 */
    public static File initDbPropertiesFile(ZrLogConfig zrLogConfig) {
        File dbFile =
                PathUtil.getConfFile(
                        "/"
                                + Objects.requireNonNullElse(
                                        System.getenv("DB_PROPERTIES_FILE_NAME"), "db.properties"));
        dbFile.getParentFile().mkdirs();
        try {
            if (!zrLogConfig.isInstalled()) {
                return dbFile;
            }
            if (StringUtils.isNotEmpty(ZrLogUtil.getDbInfoByEnv())) {
                IOUtil.writeBytesToFile(
                        new String(ZrLogUtil.getDbInfoByEnv().getBytes())
                                .replaceAll(" ", "\n")
                                .getBytes(),
                        dbFile);
            }
            Properties properties = getDbProp(dbFile);
            String driverClass = properties.getProperty("driverClass");
            if (StringUtils.isEmpty(driverClass)) {
                return dbFile;
            }
            if (driverClass.contains("mysql")) {
                handleMySQLDbProperties(dbFile, properties);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "initDbPropertiesFile error " + e.getMessage());
        }
        return dbFile;
    }

    private static void handleMySQLDbProperties(File dbFile, Properties properties)
            throws IOException {
        String jdbcUrl = ((String) properties.get("jdbcUrl"));
        if (StringUtils.isEmpty(jdbcUrl)) {
            return;
        }
        if (jdbcUrl.contains("utf8")) {
            return;
        }
        try (FileOutputStream fileOutputStream = new FileOutputStream(dbFile)) {
            properties.put("driverClass", "com.mysql.cj.jdbc.Driver");
            jdbcUrl = jdbcUrl.split("\\?")[0];
            properties.put("jdbcUrl", jdbcUrl + "?" + Constants.MYSQL_JDBC_PARAMS);
            properties.store(fileOutputStream, "Support mysql8");
            if (Constants.debugLoggerPrintAble()) {
                LOGGER.info("Update mysql " + dbFile.getName() + " success");
            }
        }
    }

    public static Properties getDbProp(File dbPropertiesFile) {
        Properties dbProperties = new Properties();
        try (FileInputStream in = new FileInputStream(dbPropertiesFile)) {
            dbProperties.load(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return dbProperties;
    }

    public static DataSourceWrapper configDatabaseWithRetry(
            int timeoutInSeconds, ZrLogConfig zrLogConfig) {
        try {
            return zrLogConfig.configDatabase();
        } catch (Exception e) {
            if (e instanceof com.zrlog.common.exception.DatabaseInitializationException) {
                throw (com.zrlog.common.exception.DatabaseInitializationException) e;
            }
            if (timeoutInSeconds > 0 && e instanceof SQLRecoverableException) {
                int seekSeconds = 5;
                try {
                    Thread.sleep(seekSeconds * 1000);
                } catch (InterruptedException ex) {
                    // ignore
                }
                return configDatabaseWithRetry(timeoutInSeconds - seekSeconds, zrLogConfig);
            }
            LoggerUtil.getLogger(DbUtils.class).warning("Config database error " + e.getMessage());
            if (EnvKit.isNativeImage()) {
                System.exit(-1);
            }
        }
        return null;
    }
}

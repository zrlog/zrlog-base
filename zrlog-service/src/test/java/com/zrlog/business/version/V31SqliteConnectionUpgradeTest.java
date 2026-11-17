package com.zrlog.business.version;

import static org.junit.Assert.*;

import com.hibegin.common.dao.CustomQueryRunner;
import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.ResultValueConvertUtils;
import com.zrlog.business.service.DbUpgradeService;
import com.zrlog.common.CacheService;
import com.zrlog.data.service.DistributedLock;
import com.zrlog.test.support.ZrLogTestDatabase;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Timestamp;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;

public class V31SqliteConnectionUpgradeTest {
    @Test
    public void migratesHistoricalDataWhenPooledConnectionsHaveOlderSchemas() throws Exception {
        try (ZrLogTestDatabase db =
                ZrLogTestDatabase.open(
                        ZrLogTestDatabase.DatabaseType.SQLITE, "/schema/v30-install-e8d8c9d.sql")) {
            long created = Timestamp.valueOf("2024-05-06 07:08:09.987").getTime();
            db.putWebsite(CacheService.ZRLOG_SQL_VERSION_KEY, "30");
            db.update(
                    "INSERT INTO user_passkey_challenge(requestId,ceremony,requestJson,createdAt,expiresAt) VALUES(?,?,?,?,?)",
                    "pooled-connection",
                    "authentication",
                    "{}",
                    created,
                    created + 300000);
            try (Connection first = db.dataSource().getConnection();
                    Connection second = db.dataSource().getConnection()) {
                // Load the old schema on both connections before the migration changes it.
                for (Connection connection : new Connection[] {first, second}) {
                    try (var statement = connection.createStatement();
                            var rows = statement.executeQuery("SELECT count(*) FROM website")) {
                        assertTrue(rows.next());
                    }
                }
                DataSourceWrapper source =
                        switchConnectionAfterRename(db.dataSource(), first, second);
                new DbUpgradeService(source, 30).tryDoUpgrade();
            }
            assertEquals(
                    String.valueOf(UpgradeVersionHandler.SQL_VERSION),
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            CacheService.ZRLOG_SQL_VERSION_KEY));
            assertEquals(
                    created,
                    ResultValueConvertUtils.parseDate(
                                    db.scalar("SELECT createdAt FROM user_passkey_challenge"))
                            .longValue());
            assertEquals(
                    created + 300000,
                    ((Number) db.scalar("SELECT expiresAt FROM user_passkey_challenge"))
                            .longValue());
            assertEquals(
                    1L,
                    ((Number) db.scalar("SELECT count(*) FROM user_passkey_challenge"))
                            .longValue());
            assertNull(
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            DistributedLock.SCHEMA_UPGRADE_KEY));
        }
    }

    private static DataSourceWrapper switchConnectionAfterRename(
            DataSourceWrapper source, Connection... connections) {
        AtomicBoolean switchNext = new AtomicBoolean();
        Connection[] borrowed = new Connection[connections.length];
        for (int i = 0; i < connections.length; i++) {
            Connection connection = connections[i];
            borrowed[i] =
                    (Connection)
                            Proxy.newProxyInstance(
                                    Connection.class.getClassLoader(),
                                    new Class<?>[] {Connection.class},
                                    (proxy, method, args) -> {
                                        if (method.getName().equals("close")) {
                                            // Match a pool return without closing the reserved
                                            // physical connection.
                                            if (!connection.getAutoCommit()) {
                                                connection.rollback();
                                                connection.setAutoCommit(true);
                                            }
                                            return null;
                                        }
                                        try {
                                            return method.invoke(connection, args);
                                        } catch (InvocationTargetException failure) {
                                            throw failure.getCause();
                                        }
                                    });
        }
        return (DataSourceWrapper)
                Proxy.newProxyInstance(
                        DataSourceWrapper.class.getClassLoader(),
                        new Class<?>[] {DataSourceWrapper.class},
                        (proxy, method, args) -> {
                            if (method.getName().equals("getConnection")) {
                                return borrowed[switchNext.compareAndSet(true, false) ? 1 : 0];
                            }
                            if (method.getName().equals("getQueryRunner")) {
                                return new CustomQueryRunner((DataSourceWrapper) proxy, true) {
                                    @Override
                                    public int update(
                                            Connection connection, String sql, Object... params)
                                            throws java.sql.SQLException {
                                        int result = super.update(connection, sql, params);
                                        if (sql.equals(
                                                "ALTER TABLE `user_passkey_challenge` RENAME COLUMN `createdAt` TO `createdAt_epoch_v31`")) {
                                            // Borrow an older connection for the following DDL.
                                            switchNext.set(true);
                                        }
                                        return result;
                                    }
                                };
                            }
                            try {
                                return method.invoke(source, args);
                            } catch (InvocationTargetException failure) {
                                throw failure.getCause();
                            }
                        });
    }
}

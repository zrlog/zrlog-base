package com.zrlog.business.service;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.SqlConvertUtils;
import com.zrlog.business.support.InMemoryZrLogDatabase;
import com.zrlog.business.version.UpgradeVersionHandler;
import com.zrlog.common.CacheService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.SQLException;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class DbUpgradeServiceDatabaseTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldSkipUpgradeWhenDatabaseIsAlreadyAtLatestSqlVersion() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            db.update("insert into website(name, value, remark) values(?, ?, ?)",
                    CacheService.ZRLOG_SQL_VERSION_KEY, String.valueOf(UpgradeVersionHandler.SQL_VERSION), "");

            new DbUpgradeService(db.dataSource(), UpgradeVersionHandler.SQL_VERSION).tryDoUpgrade();

            assertEquals(String.valueOf(UpgradeVersionHandler.SQL_VERSION),
                    db.scalar("select value from website where name=?", CacheService.ZRLOG_SQL_VERSION_KEY));
        }
    }

    @Test
    public void shouldSkipUpgradeWhenCurrentSqlVersionIsUnknown() throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
            new DbUpgradeService(db.dataSource(), -1).tryDoUpgrade();

            assertNull(db.scalar("select value from website where name=?", CacheService.ZRLOG_SQL_VERSION_KEY));
        }
    }

    @Test
    public void shouldExecutePendingSqlAndPersistLatestSqlVersionUsingConfiguredConfPath() throws Exception {
        String previousConfPath = System.getProperty("sws.conf.path");
        File confFolder = writeUpgradeSql("conf",
                "insert into website(name, value, remark) values('db.upgrade.marker', 'ok', '');",
                UpgradeVersionHandler.SQL_VERSION);
        try {
            System.setProperty("sws.conf.path", confFolder.getAbsolutePath());
            try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
                new DbUpgradeService(db.dataSource(), UpgradeVersionHandler.SQL_VERSION - 1).tryDoUpgrade();

                assertEquals("ok", db.scalar("select value from website where name=?", "db.upgrade.marker"));
                assertEquals(String.valueOf(UpgradeVersionHandler.SQL_VERSION),
                        db.scalar("select value from website where name=?", CacheService.ZRLOG_SQL_VERSION_KEY));
            }
        } finally {
            restoreProperty("sws.conf.path", previousConfPath);
        }
    }

    @Test
    public void shouldMigrateArticleExtensionsUsingBundledSql() throws Exception {
        String previousConfPath = System.getProperty("sws.conf.path");
        File confFolder = writeUpgradeSql("article-extension-conf",
                bundledUpgradeSql(24), 24);
        writeUpgradeSql(confFolder, bundledUpgradeSql(25), 25);
        writeUpgradeSql(confFolder, bundledUpgradeSql(26), 26);
        try {
            System.setProperty("sws.conf.path", confFolder.getAbsolutePath());
            try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
                db.update("drop table log_extension_index");
                db.update("alter table log drop column extensions");
                db.update("alter table log drop column sticky");
                dropPasskeySchema(db);

                new DbUpgradeService(db.dataSource(), 23).tryDoUpgrade();

                assertEquals(0L, ((Number) db.scalar(
                        "select count(extensions) from log")).longValue());
                assertEquals(0L, ((Number) db.scalar(
                        "select count(1) from log_extension_index")).longValue());
                assertEquals(String.valueOf(UpgradeVersionHandler.SQL_VERSION),
                        db.scalar("select value from website where name=?", CacheService.ZRLOG_SQL_VERSION_KEY));
            }
        } finally {
            restoreProperty("sws.conf.path", previousConfPath);
        }
    }

    @Test
    public void shouldAddStickyPriorityToExistingArticlesUsingBundledSql() throws Exception {
        String previousConfPath = System.getProperty("sws.conf.path");
        File confFolder = writeUpgradeSql("article-sticky-conf",
                bundledUpgradeSql(25), 25);
        writeUpgradeSql(confFolder, bundledUpgradeSql(26), 26);
        try {
            System.setProperty("sws.conf.path", confFolder.getAbsolutePath());
            try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
                db.update("drop table log_extension_index");
                db.update("alter table log drop column extensions");
                db.update("alter table log drop column if exists sticky");
                dropPasskeySchema(db);
                db.update("insert into log(logId, alias) values(?, ?)", 1, "legacy-article");

                new DbUpgradeService(db.dataSource(), 23).tryDoUpgrade();

                assertEquals(0L, ((Number) db.scalar(
                        "select sticky from log where logId=?", 1)).longValue());
                assertEquals(String.valueOf(UpgradeVersionHandler.SQL_VERSION),
                        db.scalar("select value from website where name=?", CacheService.ZRLOG_SQL_VERSION_KEY));
            }
        } finally {
            restoreProperty("sws.conf.path", previousConfPath);
        }
    }

    @Test
    public void shouldAddPasskeySchemaUsingBundledSql() throws Exception {
        String previousConfPath = System.getProperty("sws.conf.path");
        File confFolder = writeUpgradeSql("passkey-conf", bundledUpgradeSql(26), 26);
        try {
            System.setProperty("sws.conf.path", confFolder.getAbsolutePath());
            try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
                dropPasskeySchema(db);

                new DbUpgradeService(db.dataSource(), 25).tryDoUpgrade();

                assertEquals(0L, ((Number) db.scalar("select count(passkeyUserHandle) from user")).longValue());
                assertEquals(0L, ((Number) db.scalar("select count(1) from user_passkey")).longValue());
                assertEquals(0L, ((Number) db.scalar("select count(1) from user_passkey_challenge")).longValue());
                assertEquals("26",
                        db.scalar("select value from website where name=?", CacheService.ZRLOG_SQL_VERSION_KEY));

                db.update("insert into user(userId, userName, passkeyUserHandle) values(?, ?, ?)",
                        100, "passkey-user", "user-handle");
                assertEquals("user-handle", db.scalar(
                        "select passkeyUserHandle from user where userId=?", 100));
                assertThrows(SQLException.class, () -> db.update(
                        "insert into user(userId, userName, passkeyUserHandle) values(?, ?, ?)",
                        101, "duplicate-handle", "user-handle"));

                db.update("insert into user_passkey(userId, credentialIdHash, credentialId, publicKeyCose, "
                                + "origin, rpId, createdAt) values(?, ?, ?, ?, ?, ?, ?)",
                        100, "credential-hash", "credential-id", "public-key", "https://example.com",
                        "example.com", 1_754_000_000_000L);
                assertEquals(0L, ((Number) db.scalar(
                        "select signatureCount from user_passkey where credentialIdHash=?",
                        "credential-hash")).longValue());
                assertThrows(SQLException.class, () -> db.update(
                        "insert into user_passkey(userId, credentialIdHash, credentialId, publicKeyCose, "
                                + "origin, rpId, createdAt) values(?, ?, ?, ?, ?, ?, ?)",
                        100, "credential-hash", "other-credential-id", "other-public-key",
                        "https://example.com", "example.com", 1_754_000_000_001L));

                db.update("insert into user_passkey_challenge(requestId, ceremony, requestJson, createdAt, expiresAt) "
                                + "values(?, ?, ?, ?, ?)",
                        "request-id", "authentication", "{}", 1_754_000_000_000L, 1_754_000_300_000L);
                assertNull(db.scalar("select userId from user_passkey_challenge where requestId=?", "request-id"));
                assertThrows(SQLException.class, () -> db.update(
                        "insert into user_passkey_challenge(requestId, ceremony, requestJson, createdAt, expiresAt) "
                                + "values(?, ?, ?, ?, ?)",
                        "request-id", "registration", "{}", 1_754_000_000_001L, 1_754_000_300_001L));
            }
        } finally {
            restoreProperty("sws.conf.path", previousConfPath);
        }
    }

    @Test
    public void shouldResumePartiallyAppliedArticleExtensionMigrationForWebApi() throws Exception {
        String previousConfPath = System.getProperty("sws.conf.path");
        File confFolder = writeUpgradeSql("partial-webapi-article-extension",
                "ALTER TABLE log ADD COLUMN extensions longtext DEFAULT NULL;\n"
                        + "CREATE TABLE IF NOT EXISTS log_extension_index(id integer primary key);\n"
                        + "CREATE INDEX log_extension_article ON log_extension_index(id);\n"
                        + "CREATE INDEX log_extension_filter ON log_extension_index(id);\n", 24);
        writeUpgradeSql(confFolder, bundledUpgradeSql(25), 25);
        writeUpgradeSql(confFolder, bundledPasskeyReplaySqlWithoutTableCreation(), 26);
        try {
            System.setProperty("sws.conf.path", confFolder.getAbsolutePath());
            try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
                db.update("alter table log drop column sticky");
                dropPasskeySchema(db);
                preparePartiallyAppliedPasskeySchemaForWebApi(db);

                new DbUpgradeService(asWebApi(db.dataSource()), 23).tryDoUpgrade();

                assertEquals(0L, ((Number) db.scalar("select count(extensions) from log")).longValue());
                assertEquals(0L, ((Number) db.scalar("select count(1) from log_extension_index")).longValue());
                assertEquals(0L, ((Number) db.scalar("select count(sticky) from log")).longValue());
                assertEquals(String.valueOf(UpgradeVersionHandler.SQL_VERSION),
                        db.scalar("select value from website where name=?", CacheService.ZRLOG_SQL_VERSION_KEY));
            }
        } finally {
            restoreProperty("sws.conf.path", previousConfPath);
        }
    }

    @Test
    public void shouldConvertArticleExtensionMigrationToD1CompatibleStatements() throws Exception {
        List<String> statements = SqlConvertUtils.doMySQLToSqliteBySqlText(bundledUpgradeSql(24));

        assertEquals(4, statements.size());
        assertTrue(statements.get(1).contains("INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT"));
        assertFalse(statements.get(1).contains("AUTO_INCREMENT"));
        assertFalse(statements.get(1).contains("log_extension_article"));
        assertFalse(statements.get(1).contains("log_extension_filter"));
        assertTrue(statements.stream().anyMatch(sql -> sql.startsWith("CREATE INDEX `log_extension_article`")));
        assertTrue(statements.stream().anyMatch(sql -> sql.startsWith("CREATE INDEX `log_extension_filter`")));
    }

    @Test
    public void shouldConvertPasskeyMigrationToD1CompatibleStatements() throws Exception {
        List<String> statements = SqlConvertUtils.doMySQLToSqliteBySqlText(bundledUpgradeSql(26));
        String convertedSql = String.join("\n", statements);

        assertEquals(9, statements.size());
        assertFalse(convertedSql.contains("AUTO_INCREMENT"));
        assertFalse(convertedSql.contains("ENGINE=InnoDB"));
        assertFalse(convertedSql.contains("bit(1)"));
        assertFalse(convertedSql.contains("DEFAULT b'0'"));
        assertEquals(2L, statements.stream()
                .filter(sql -> sql.contains("INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT"))
                .count());
        assertTrue(statements.stream().anyMatch(sql -> sql.startsWith(
                "CREATE UNIQUE INDEX `user_passkey_handle`")));
        assertTrue(statements.stream().anyMatch(sql -> sql.startsWith(
                "CREATE UNIQUE INDEX `user_passkey_credential_hash`")));
        assertTrue(statements.stream().anyMatch(sql -> sql.startsWith(
                "CREATE INDEX `user_passkey_user`")));
        assertTrue(statements.stream().anyMatch(sql -> sql.startsWith(
                "CREATE UNIQUE INDEX `user_passkey_challenge_request`")));
        assertTrue(statements.stream().anyMatch(sql -> sql.startsWith(
                "CREATE INDEX `user_passkey_challenge_expiry`")));
        assertTrue(statements.stream().anyMatch(sql -> sql.startsWith(
                "CREATE INDEX `user_passkey_challenge_user_ceremony`")));
    }

    @Test
    public void shouldResumeAfterLaterMigrationFailsWithoutReplayingArticleExtensions() throws Exception {
        String previousConfPath = System.getProperty("sws.conf.path");
        File confFolder = writeUpgradeSql("resumable-article-migrations",
                bundledUpgradeSql(24), 24);
        writeUpgradeSql(confFolder, "bad sql statement;", 25);
        writeUpgradeSql(confFolder, bundledUpgradeSql(26), 26);
        try {
            System.setProperty("sws.conf.path", confFolder.getAbsolutePath());
            try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
                db.update("drop table log_extension_index");
                db.update("alter table log drop column extensions");
                db.update("alter table log drop column sticky");
                dropPasskeySchema(db);

                new DbUpgradeService(db.dataSource(), 23).tryDoUpgrade();

                assertEquals("24",
                        db.scalar("select value from website where name=?", CacheService.ZRLOG_SQL_VERSION_KEY));
                assertEquals(0L, ((Number) db.scalar("select count(extensions) from log")).longValue());

                writeUpgradeSql(confFolder, bundledUpgradeSql(25), 25);
                new DbUpgradeService(db.dataSource(), 24).tryDoUpgrade();

                assertEquals(0L, ((Number) db.scalar("select count(sticky) from log")).longValue());
                assertEquals(String.valueOf(UpgradeVersionHandler.SQL_VERSION),
                        db.scalar("select value from website where name=?", CacheService.ZRLOG_SQL_VERSION_KEY));
            }
        } finally {
            restoreProperty("sws.conf.path", previousConfPath);
        }
    }

    @Test
    public void shouldStopUpgradeAndKeepVersionWhenPendingSqlFails() throws Exception {
        String previousConfPath = System.getProperty("sws.conf.path");
        File confFolder = writeUpgradeSql("bad-conf", "bad sql statement;", UpgradeVersionHandler.SQL_VERSION);
        try {
            System.setProperty("sws.conf.path", confFolder.getAbsolutePath());
            try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
                new DbUpgradeService(db.dataSource(), UpgradeVersionHandler.SQL_VERSION - 1).tryDoUpgrade();

                assertNull(db.scalar("select value from website where name=?", CacheService.ZRLOG_SQL_VERSION_KEY));
            }
        } finally {
            restoreProperty("sws.conf.path", previousConfPath);
        }
    }

    private File writeUpgradeSql(String folderName, String sql, int version) throws Exception {
        File confFolder = temporaryFolder.newFolder(folderName);
        writeUpgradeSql(confFolder, sql, version);
        return confFolder;
    }

    private void writeUpgradeSql(File confFolder, String sql, int version) throws Exception {
        File updateSqlFolder = new File(confFolder, "update-sql");
        if (!updateSqlFolder.exists()) {
            assertEquals(true, updateSqlFolder.mkdirs());
        }
        Files.writeString(new File(updateSqlFolder, version + ".sql").toPath(), sql, StandardCharsets.UTF_8);
    }

    private String bundledUpgradeSql(int version) throws Exception {
        try (InputStream inputStream = DbUpgradeServiceDatabaseTest.class.getResourceAsStream(
                "/conf/update-sql/" + version + ".sql")) {
            if (inputStream == null) {
                throw new IllegalStateException("bundled upgrade SQL not found");
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private String bundledPasskeyReplaySqlWithoutTableCreation() throws Exception {
        return SqlConvertUtils.extractExecutableSql(bundledUpgradeSql(26)).stream()
                .filter(sql -> !sql.startsWith("CREATE TABLE"))
                .collect(Collectors.joining(";\n", "", ";\n"));
    }

    private void dropPasskeySchema(InMemoryZrLogDatabase db) throws SQLException {
        db.update("drop table if exists user_passkey_challenge");
        db.update("drop table if exists user_passkey");
        db.update("drop index if exists user_passkey_handle");
        db.update("alter table user drop column if exists passkeyUserHandle");
    }

    private void preparePartiallyAppliedPasskeySchemaForWebApi(InMemoryZrLogDatabase db) throws SQLException {
        db.update("alter table user add column passkeyUserHandle varchar(64)");
        db.update("create unique index user_passkey_handle on user(passkeyUserHandle)");
        db.update("create table `user_passkey`("
                + "id integer primary key, userId integer not null, credentialIdHash varchar(64) not null)");
        db.update("create table `user_passkey_challenge`("
                + "id integer primary key, requestId varchar(64) not null, expiresAt bigint not null, "
                + "userId integer, ceremony varchar(32) not null)");
    }

    private DataSourceWrapper asWebApi(DataSourceWrapper delegate) {
        return (DataSourceWrapper) Proxy.newProxyInstance(
                DataSourceWrapper.class.getClassLoader(),
                new Class[]{DataSourceWrapper.class},
                (proxy, method, args) -> {
                    if ("isWebApi".equals(method.getName())) {
                        return true;
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}

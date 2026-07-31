package com.zrlog.business.service;

import com.zrlog.business.support.InMemoryZrLogDatabase;
import com.zrlog.business.version.UpgradeVersionHandler;
import com.zrlog.common.CacheService;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
        writeUpgradeSql(confFolder, bundledUpgradeSql(UpgradeVersionHandler.SQL_VERSION),
                UpgradeVersionHandler.SQL_VERSION);
        try {
            System.setProperty("sws.conf.path", confFolder.getAbsolutePath());
            try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
                db.update("drop table log_extension_index");
                db.update("alter table log drop column extensions");
                db.update("alter table log drop column sticky");

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
                bundledUpgradeSql(UpgradeVersionHandler.SQL_VERSION), UpgradeVersionHandler.SQL_VERSION);
        try {
            System.setProperty("sws.conf.path", confFolder.getAbsolutePath());
            try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
                db.update("drop table log_extension_index");
                db.update("alter table log drop column extensions");
                db.update("alter table log drop column if exists sticky");
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
    public void shouldResumeAfterLaterMigrationFailsWithoutReplayingArticleExtensions() throws Exception {
        String previousConfPath = System.getProperty("sws.conf.path");
        File confFolder = writeUpgradeSql("resumable-article-migrations",
                bundledUpgradeSql(24), 24);
        writeUpgradeSql(confFolder, "bad sql statement;", UpgradeVersionHandler.SQL_VERSION);
        try {
            System.setProperty("sws.conf.path", confFolder.getAbsolutePath());
            try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open()) {
                db.update("drop table log_extension_index");
                db.update("alter table log drop column extensions");
                db.update("alter table log drop column sticky");

                new DbUpgradeService(db.dataSource(), 23).tryDoUpgrade();

                assertEquals("24",
                        db.scalar("select value from website where name=?", CacheService.ZRLOG_SQL_VERSION_KEY));
                assertEquals(0L, ((Number) db.scalar("select count(extensions) from log")).longValue());

                writeUpgradeSql(confFolder, bundledUpgradeSql(UpgradeVersionHandler.SQL_VERSION),
                        UpgradeVersionHandler.SQL_VERSION);
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

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}

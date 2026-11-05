package com.zrlog.business.version;

import static org.junit.Assert.*;

import com.zrlog.business.service.DbUpgradeService;
import com.zrlog.common.CacheService;
import com.zrlog.test.support.ZrLogTestDatabase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class V32AssistantStorageUpgradeTest {
    private final String backend;

    public V32AssistantStorageUpgradeTest(String backend) {
        this.backend = backend;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Object[] backends() {
        return new Object[] {"H2", "SQLITE", "WEB_API"};
    }

    @Test
    public void createsEmptyBusinessTablesWithoutReadingOrMovingOldAssistantDataAndResumesDdl()
            throws Exception {
        try (var db =
                backend.equals("WEB_API")
                        ? ZrLogTestDatabase.openWebApi()
                        : ZrLogTestDatabase.open(ZrLogTestDatabase.DatabaseType.valueOf(backend))) {
            db.update("DROP TABLE assistant_notice_read");
            db.update("DROP TABLE assistant_task_run");
            db.update("DROP TABLE assistant_task");
            db.putWebsite(CacheService.ZRLOG_SQL_VERSION_KEY, "31");
            String legacy = "{\"name\":\"Keep old data 🌿\",\"result\":\"unchanged\"}";
            db.putWebsite("assistant.v1.task:old", legacy);
            db.putWebsite("assistant.v1.run:old", legacy);
            new DbUpgradeService(db.dataSource(), 31).tryDoUpgrade();
            for (String table :
                    new String[] {"assistant_task", "assistant_task_run", "assistant_notice_read"})
                assertEquals(0L, ((Number) db.scalar("SELECT count(*) FROM " + table)).longValue());
            assertEquals(
                    legacy,
                    db.scalar("SELECT value FROM website WHERE name=?", "assistant.v1.task:old"));
            assertEquals(
                    legacy,
                    db.scalar("SELECT value FROM website WHERE name=?", "assistant.v1.run:old"));
            assertEquals(
                    String.valueOf(UpgradeVersionHandler.SQL_VERSION),
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            CacheService.ZRLOG_SQL_VERSION_KEY));
            // Simulate a crash after DDL committed but before the version marker did.
            db.putWebsite(CacheService.ZRLOG_SQL_VERSION_KEY, "31");
            new DbUpgradeService(db.dataSource(), 31).tryDoUpgrade();
            assertEquals(
                    String.valueOf(UpgradeVersionHandler.SQL_VERSION),
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            CacheService.ZRLOG_SQL_VERSION_KEY));
            assertEquals(
                    legacy,
                    db.scalar("SELECT value FROM website WHERE name=?", "assistant.v1.run:old"));
        }
    }
}

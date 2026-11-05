package com.zrlog.business.version;

import static org.junit.Assert.*;

import com.hibegin.common.dao.ResultValueConvertUtils;
import com.zrlog.business.service.DbUpgradeService;
import com.zrlog.common.CacheService;
import com.zrlog.data.di.DaggerDataComponent;
import com.zrlog.data.repository.WebSiteRepository;
import com.zrlog.data.service.DistributedLock;
import com.zrlog.test.support.ZrLogTestDatabase;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class V31EntityMetadataUpgradeTest {
    // Exact historical install schema, not a reverse transformation of the current schema.
    private static final String V30 = "/schema/v30-install-e8d8c9d.sql";
    private final String backend;

    public V31EntityMetadataUpgradeTest(String backend) {
        this.backend = backend;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Object[] backends() {
        return new Object[] {"H2", "SQLITE", "WEB_API"};
    }

    private static long time(Object value) {
        return ResultValueConvertUtils.parseDate(value).longValue();
    }

    private ZrLogTestDatabase open() throws Exception {
        return backend.equals("WEB_API")
                ? ZrLogTestDatabase.openWebApi(V30)
                : ZrLogTestDatabase.open(ZrLogTestDatabase.DatabaseType.valueOf(backend), V30);
    }

    @Test
    public void readsSchemaMarkerWithoutEntityMetadataAndPropagatesQueryFailures()
            throws Exception {
        try (ZrLogTestDatabase db = open()) {
            db.putWebsite(CacheService.ZRLOG_SQL_VERSION_KEY, "30");
            db.putWebsite("null-value", null);
            WebSiteRepository website = new WebSiteRepository(db.dataSource());

            assertEquals("30", website.findValueByName(CacheService.ZRLOG_SQL_VERSION_KEY));
            assertNull(website.findValueByName("missing-value"));
            assertNull(website.findValueByName("null-value"));

            db.update("DROP TABLE website");
            assertThrows(
                    SQLException.class,
                    () -> website.findValueByName(CacheService.ZRLOG_SQL_VERSION_KEY));
        }
    }

    @Test
    public void preservesHistoricalTimesRevisionsAndSecurityDataAcrossBatches() throws Exception {
        try (ZrLogTestDatabase db = open()) {
            long time = Timestamp.valueOf("2024-02-29 13:14:15.123").getTime();
            for (int i = 0; i < 47; i++) {
                db.update(
                        "INSERT INTO user_passkey_challenge(requestId,ceremony,requestJson,createdAt,expiresAt) VALUES(?,?,?,?,?)",
                        "request-" + i,
                        "authentication",
                        "{}",
                        time + i,
                        time + i + 300000);
            }
            db.update(
                    "INSERT INTO oauth_grant(id,userId,clientId,scope,resource,authVersion,createdAt) VALUES(?,?,?,?,?,?,?)",
                    "grant",
                    1,
                    "client",
                    "read",
                    "resource",
                    7,
                    time);
            db.update(
                    "INSERT INTO user_access_token(id,userId,name,tokenHash,scope,resource,authVersion,createdAt,expiresAt) VALUES(?,?,?,?,?,?,?,?,?)",
                    "token",
                    1,
                    "test",
                    "hash",
                    "read",
                    "resource",
                    7,
                    time,
                    time + 900000);
            db.update(
                    "INSERT INTO user_passkey(userId,credentialIdHash,credentialId,publicKeyCose,signatureCount,origin,rpId,createdAt,backupEligible,backupState) VALUES(?,?,?,?,?,?,?,?,?,?)",
                    1,
                    "credential-hash",
                    "credential",
                    "public-key",
                    123L,
                    "https://example.com",
                    "example.com",
                    time,
                    false,
                    false);
            db.update(
                    "INSERT INTO log(title,version,releaseTime,last_update_date) VALUES(?,?,?,?)",
                    "historical",
                    123,
                    "2020-01-02 03:04:05",
                    "2021-02-03 04:05:06");
            db.update("INSERT INTO log(title,version) VALUES(?,NULL)", "no-version");
            db.update(
                    "INSERT INTO comment(commTime,td,userComment) VALUES(?,?,?)",
                    "2010-01-01 00:00:00",
                    "2020-01-01 00:00:00",
                    "imported");
            db.putWebsite(CacheService.ZRLOG_SQL_VERSION_KEY, "30");
            new DbUpgradeService(db.dataSource(), 30).tryDoUpgrade();
            assertEquals(
                    String.valueOf(com.zrlog.business.version.UpgradeVersionHandler.SQL_VERSION),
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            CacheService.ZRLOG_SQL_VERSION_KEY));
            for (Map<String, Object> row :
                    db.queryList(
                            "SELECT id,createdAt,expiresAt FROM user_passkey_challenge ORDER BY id")) {
                long expected = time + ((Number) row.get("id")).intValue() - 1;
                assertEquals(expected, time(row.get("createdAt")));
                assertEquals(expected + 300000, ((Number) row.get("expiresAt")).longValue());
            }
            for (String table : new String[] {"oauth_grant", "user_access_token", "user_passkey"}) {
                assertEquals(time, time(db.scalar("SELECT createdAt FROM " + table)));
                assertNull(db.scalar("SELECT updatedAt FROM " + table));
                assertEquals(0L, ((Number) db.scalar("SELECT version FROM " + table)).longValue());
            }
            assertEquals(
                    123L,
                    ((Number) db.scalar("SELECT signatureCount FROM user_passkey")).longValue());
            assertEquals(7, ((Number) db.scalar("SELECT authVersion FROM oauth_grant")).intValue());
            assertEquals(
                    123L,
                    ((Number) db.scalar("SELECT version FROM log WHERE title='historical'"))
                            .longValue());
            assertEquals(
                    0L,
                    ((Number) db.scalar("SELECT version FROM log WHERE title='no-version'"))
                            .longValue());
            assertEquals(0L, ((Number) db.scalar("SELECT count(createdAt) FROM log")).longValue());
            assertEquals(
                    Timestamp.valueOf("2010-01-01 00:00:00").getTime(),
                    time(db.scalar("SELECT commTime FROM comment")));
            assertEquals(
                    Timestamp.valueOf("2020-01-01 00:00:00").getTime(),
                    time(db.scalar("SELECT td FROM comment")));
            assertNull(
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            DistributedLock.SCHEMA_UPGRADE_KEY));
            // A second cold start with a stale cached version must not replay conversion.
            new DbUpgradeService(db.dataSource(), 30).tryDoUpgrade();
            new V31EntityMetadataUpgrade(db.dataSource()).run();
            assertEquals(time, time(db.scalar("SELECT createdAt FROM oauth_grant")));
        }
    }

    @Test
    public void stopsStartupOnInvalidHistoricalTimeWithoutAdvancingVersion() throws Exception {
        try (ZrLogTestDatabase db = open()) {
            db.update(
                    "INSERT INTO user_passkey_challenge(requestId,ceremony,requestJson,createdAt,expiresAt) VALUES(?,?,?,?,?)",
                    "bad-time",
                    "authentication",
                    "{}",
                    Long.MAX_VALUE,
                    Long.MAX_VALUE);
            db.putWebsite(CacheService.ZRLOG_SQL_VERSION_KEY, "30");
            assertThrows(
                    IllegalStateException.class,
                    () -> new DbUpgradeService(db.dataSource(), 30).tryDoUpgrade());
            assertEquals(
                    "30",
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            CacheService.ZRLOG_SQL_VERSION_KEY));
            assertEquals(
                    Long.MAX_VALUE,
                    ((Number) db.scalar("SELECT createdAt FROM user_passkey_challenge"))
                            .longValue());
            assertNull(
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            DistributedLock.SCHEMA_UPGRADE_KEY));
        }
    }

    @Test
    public void serializesColdStartsWithoutExpiringAnActiveMigrationLock() throws Exception {
        try (ZrLogTestDatabase db = open()) {
            db.putWebsite(CacheService.ZRLOG_SQL_VERSION_KEY, "30");
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<?> coldStart;
                try (DistributedLock lock =
                        DaggerDataComponent.factory()
                                .create(() -> db.dataSource(), () -> true)
                                .lockManager()
                                .acquire(
                                        DistributedLock.SCHEMA_UPGRADE_KEY,
                                        30,
                                        TimeUnit.SECONDS,
                                        () -> false)) {
                    Object owner =
                            db.scalar(
                                    "SELECT value FROM website WHERE name=?",
                                    DistributedLock.SCHEMA_UPGRADE_KEY);
                    coldStart =
                            executor.submit(
                                    () -> new DbUpgradeService(db.dataSource(), 30).tryDoUpgrade());
                    assertThrows(
                            TimeoutException.class,
                            () -> coldStart.get(200, TimeUnit.MILLISECONDS));
                    assertEquals(
                            "30",
                            db.scalar(
                                    "SELECT value FROM website WHERE name=?",
                                    CacheService.ZRLOG_SQL_VERSION_KEY));
                    assertEquals(
                            owner,
                            db.scalar(
                                    "SELECT value FROM website WHERE name=?",
                                    DistributedLock.SCHEMA_UPGRADE_KEY));
                    new V31EntityMetadataUpgrade(db.dataSource()).run();
                    db.putWebsite(CacheService.ZRLOG_SQL_VERSION_KEY, "31");
                }
                coldStart.get(5, TimeUnit.SECONDS);
                assertEquals(
                        String.valueOf(
                                com.zrlog.business.version.UpgradeVersionHandler.SQL_VERSION),
                        db.scalar(
                                "SELECT value FROM website WHERE name=?",
                                CacheService.ZRLOG_SQL_VERSION_KEY));
                assertNull(
                        db.scalar(
                                "SELECT value FROM website WHERE name=?",
                                DistributedLock.SCHEMA_UPGRADE_KEY));
            } finally {
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    public void staleStartupVersionDoesNotAcquireLockForAnAlreadyCompletedUpgrade()
            throws Exception {
        try (ZrLogTestDatabase db = open()) {
            new DbUpgradeService(db.dataSource(), 30).tryDoUpgrade();
            // A completed migration's final DELETE may have lost its response or never reached D1.
            db.putWebsite(DistributedLock.SCHEMA_UPGRADE_KEY, "previous-owner");
            new DbUpgradeService(db.dataSource(), 30).tryDoUpgrade();
            assertEquals(
                    String.valueOf(com.zrlog.business.version.UpgradeVersionHandler.SQL_VERSION),
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            CacheService.ZRLOG_SQL_VERSION_KEY));
            assertEquals(
                    "previous-owner",
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            DistributedLock.SCHEMA_UPGRADE_KEY));
        }
    }

    @Test
    public void resumesEveryInterruptedRenameStageAndRetainsOriginalsUntilVerified()
            throws Exception {
        org.junit.Assume.assumeTrue(backend.equals("WEB_API"));
        for (String boundary :
                new String[] {
                    "RENAME COLUMN `createdAt`",
                    "RENAME COLUMN `createdAt_v31`",
                    "DROP COLUMN `createdAt_epoch_v31`"
                }) {
            try (ZrLogTestDatabase db = open()) {
                long time = Timestamp.valueOf("2024-05-06 07:08:09.987").getTime();
                db.update(
                        "INSERT INTO user_passkey_challenge(requestId,ceremony,requestJson,createdAt,expiresAt) VALUES(?,?,?,?,?)",
                        "resume",
                        "authentication",
                        "{}",
                        time,
                        time + 300000);
                AtomicBoolean failed = new AtomicBoolean();
                db.beforeWebApiUpdate(
                        (sql, params) -> {
                            if (sql.contains("`user_passkey_challenge`")
                                    && sql.contains(boundary)
                                    && failed.compareAndSet(false, true)) {
                                throw new SQLException("Injected interruption");
                            }
                        });
                assertThrows(
                        SQLException.class,
                        () -> new V31EntityMetadataUpgrade(db.dataSource()).run());
                assertTrue(failed.get());
                db.beforeWebApiUpdate((sql, params) -> {});
                new V31EntityMetadataUpgrade(db.dataSource()).run();
                assertEquals(time, time(db.scalar("SELECT createdAt FROM user_passkey_challenge")));
                assertEquals(
                        1L,
                        ((Number) db.scalar("SELECT count(*) FROM user_passkey_challenge"))
                                .longValue());
            }
        }
    }

    @Test
    public void migratesEmptyHistoricalDatabaseWithinWebApiRequestBudget() throws Exception {
        org.junit.Assume.assumeTrue(backend.equals("WEB_API"));
        try (ZrLogTestDatabase db = open()) {
            AtomicInteger requests = new AtomicInteger();
            db.beforeWebApiUpdate((sql, params) -> requests.incrementAndGet());
            db.afterWebApiQuery((sql, params) -> requests.incrementAndGet());
            new DbUpgradeService(db.dataSource(), 30).tryDoUpgrade();
            assertEquals(
                    String.valueOf(com.zrlog.business.version.UpgradeVersionHandler.SQL_VERSION),
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            CacheService.ZRLOG_SQL_VERSION_KEY));
            // Includes v32 assistant storage and v33's list/comment indexes and version marker.
            assertTrue(
                    "Empty migration made " + requests.get() + " remote calls",
                    requests.get() <= 118);
            assertNull(
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            DistributedLock.SCHEMA_UPGRADE_KEY));
        }
    }

    @Test
    public void reconcilesCommittedDdlAfterLostWebApiResponses() throws Exception {
        org.junit.Assume.assumeTrue(backend.equals("WEB_API"));
        for (String boundary :
                new String[] {
                    "ADD COLUMN `createdAt_v31`",
                    "RENAME COLUMN `createdAt`",
                    "RENAME COLUMN `createdAt_v31`",
                    "DROP COLUMN `createdAt_epoch_v31`"
                }) {
            try (ZrLogTestDatabase db = open()) {
                long original = Timestamp.valueOf("2024-05-06 07:08:09.987").getTime();
                db.update(
                        "INSERT INTO user_passkey_challenge(requestId,ceremony,requestJson,createdAt,expiresAt) VALUES(?,?,?,?,?)",
                        "lost-response",
                        "authentication",
                        "{}",
                        original,
                        original + 300000);
                AtomicBoolean failed = new AtomicBoolean();
                db.beforeWebApiUpdate(
                        (sql, params) -> {
                            if (sql.contains("`user_passkey_challenge`")
                                    && sql.contains(boundary)
                                    && failed.compareAndSet(false, true)) {
                                db.update(sql, params);
                                throw new SQLException("Committed DDL but lost its response");
                            }
                        });
                new DbUpgradeService(db.dataSource(), 30).tryDoUpgrade();
                assertTrue(failed.get());
                assertEquals(
                        String.valueOf(
                                com.zrlog.business.version.UpgradeVersionHandler.SQL_VERSION),
                        db.scalar(
                                "SELECT value FROM website WHERE name=?",
                                CacheService.ZRLOG_SQL_VERSION_KEY));
                assertEquals(
                        original, time(db.scalar("SELECT createdAt FROM user_passkey_challenge")));
                assertEquals(
                        original + 300000,
                        ((Number) db.scalar("SELECT expiresAt FROM user_passkey_challenge"))
                                .longValue());
                assertNull(
                        db.scalar(
                                "SELECT value FROM website WHERE name=?",
                                DistributedLock.SCHEMA_UPGRADE_KEY));
            }
        }
    }

    @Test
    public void rejectsNegativeExistingVersionsWithoutAdvancingSchema() throws Exception {
        try (ZrLogTestDatabase db = open()) {
            db.putWebsite(CacheService.ZRLOG_SQL_VERSION_KEY, "30");
            db.update("INSERT INTO log(title,version) VALUES(?,?)", "invalid-version", -1);
            assertThrows(
                    IllegalStateException.class,
                    () -> new DbUpgradeService(db.dataSource(), 30).tryDoUpgrade());
            assertEquals(
                    "30",
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            CacheService.ZRLOG_SQL_VERSION_KEY));
            assertEquals(
                    -1,
                    ((Number) db.scalar("SELECT version FROM log WHERE title='invalid-version'"))
                            .intValue());
            assertNull(
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            DistributedLock.SCHEMA_UPGRADE_KEY));
        }
    }
}

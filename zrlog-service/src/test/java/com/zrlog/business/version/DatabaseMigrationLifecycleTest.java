package com.zrlog.business.version;

import static org.junit.Assert.*;

import com.google.gson.Gson;
import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.common.CacheService;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.common.exception.DatabaseInitializationException;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.cache.StartupData;
import com.zrlog.data.service.DistributedLock;
import com.zrlog.plugin.IPlugin;
import com.zrlog.test.support.ZrLogTestDatabase;
import com.zrlog.web.WebSetupContext;
import com.zrlog.web.WebSetupLoader;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Historical-schema lifecycle fixture; fresh installation is covered by the install/main suites.
 */
public class DatabaseMigrationLifecycleTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void currentSchemaLoadsVersionAndCacheOncePerApplication() throws Exception {
        String previousRoot = System.getProperty("sws.root.path");
        System.setProperty("sws.root.path", temporary.getRoot().getAbsolutePath());
        try (var first = ZrLogTestDatabase.openWebApi();
                var second = ZrLogTestDatabase.openWebApi()) {
            first.putWebsite(CacheService.ZRLOG_SQL_VERSION_KEY, UpgradeVersionHandler.SQL_VERSION);
            second.putWebsite(
                    CacheService.ZRLOG_SQL_VERSION_KEY, UpgradeVersionHandler.SQL_VERSION);
            first.putWebsite(StartupData.CACHE_KEY, cachedSite("First site"));
            second.putWebsite(StartupData.CACHE_KEY, cachedSite("Second site"));
            AtomicInteger firstReads = new AtomicInteger();
            AtomicInteger secondReads = new AtomicInteger();
            first.afterWebApiQuery((sql, params) -> firstReads.incrementAndGet());
            second.afterWebApiQuery((sql, params) -> secondReads.incrementAndGet());

            TestConfig a = new TestConfig();
            a.start(first.dataSource());
            TestConfig b = new TestConfig();
            b.start(second.dataSource());
            assertEquals("First site", a.cache().getPublicWebSiteInfo().getTitle());
            assertEquals("Second site", b.cache().getPublicWebSiteInfo().getTitle());
            a.configDatabase();
            b.configDatabase();
            assertEquals(1, firstReads.get());
            assertEquals(1, secondReads.get());
        } finally {
            if (previousRoot == null) System.clearProperty("sws.root.path");
            else System.setProperty("sws.root.path", previousRoot);
        }
    }

    @Test
    public void migrationDiscardsTheStartupSnapshotAndReloadsVersionAndCache() throws Exception {
        String previousRoot = System.getProperty("sws.root.path");
        System.setProperty("sws.root.path", temporary.getRoot().getAbsolutePath());
        try (var db = ZrLogTestDatabase.openWebApi("/schema/v30-install-e8d8c9d.sql")) {
            db.putWebsite(CacheService.ZRLOG_SQL_VERSION_KEY, "30");
            db.putWebsite(StartupData.CACHE_KEY, cachedSite("Before migration"));
            AtomicInteger startupReads = new AtomicInteger();
            db.afterWebApiQuery(
                    (sql, params) -> {
                        if (params != null
                                && params.length == 2
                                && CacheService.ZRLOG_SQL_VERSION_KEY.equals(params[0])
                                && StartupData.CACHE_KEY.equals(params[1]))
                            startupReads.incrementAndGet();
                    });
            db.beforeWebApiUpdate(
                    (sql, params) -> {
                        if (sql.equals("UPDATE website SET value=? WHERE name=?")
                                && String.valueOf(UpgradeVersionHandler.SQL_VERSION)
                                        .equals(params[0])
                                && CacheService.ZRLOG_SQL_VERSION_KEY.equals(params[1])) {
                            db.update(
                                    "UPDATE website SET value=? WHERE name=?",
                                    cachedSite("After migration"),
                                    StartupData.CACHE_KEY);
                        }
                    });
            TestConfig config = new TestConfig();
            config.start(db.dataSource());
            assertEquals("After migration", config.cache().getPublicWebSiteInfo().getTitle());
            assertEquals(
                    String.valueOf(UpgradeVersionHandler.SQL_VERSION),
                    db.scalar(
                            "SELECT value FROM website WHERE name=?",
                            CacheService.ZRLOG_SQL_VERSION_KEY));
            assertEquals(2, startupReads.get());
        } finally {
            if (previousRoot == null) System.clearProperty("sws.root.path");
            else System.setProperty("sws.root.path", previousRoot);
        }
    }

    private static String cachedSite(String title) {
        BaseDataInitVO data = new BaseDataInitVO();
        PublicWebSiteInfo site = new PublicWebSiteInfo();
        site.setTitle(title);
        data.setWebSite(site);
        return new Gson().toJson(data);
    }

    @Test(timeout = 10000)
    public void migrationPrecedesCacheWritesAndFailureStopsModuleStartup() throws Exception {
        String previousRoot = System.getProperty("sws.root.path");
        System.setProperty("sws.root.path", temporary.getRoot().getAbsolutePath());
        try {
            for (boolean invalid : new boolean[] {false, true}) {
                try (ZrLogTestDatabase db =
                        ZrLogTestDatabase.openWebApi("/schema/v30-install-e8d8c9d.sql")) {
                    db.putWebsite(CacheService.ZRLOG_SQL_VERSION_KEY, "30");
                    db.putWebsite("title", "Historical site");
                    db.putWebsite("template", "/include/templates/default");
                    if (invalid) {
                        db.update(
                                "INSERT INTO user_passkey_challenge(requestId,ceremony,requestJson,createdAt,expiresAt) VALUES(?,?,?,?,?)",
                                "bad-time",
                                "authentication",
                                "{}",
                                Long.MAX_VALUE,
                                Long.MAX_VALUE);
                    }
                    TestConfig config = new TestConfig();
                    if (invalid) {
                        assertThrows(
                                DatabaseInitializationException.class,
                                () -> config.start(db.dataSource()));
                        assertEquals(
                                "30",
                                db.scalar(
                                        "SELECT value FROM website WHERE name=?",
                                        CacheService.ZRLOG_SQL_VERSION_KEY));
                        assertNull(
                                db.scalar(
                                        "SELECT value FROM website WHERE name='cache_v1:base_data_init_v3'"));
                    } else {
                        config.start(db.dataSource());
                        CacheService cache =
                                config.getServerConfig()
                                        .getBindings()
                                        .build()
                                        .get(CacheService.class);
                        assertEquals("Historical site", cache.getPublicWebSiteInfo().getTitle());
                        assertNotNull(
                                db.scalar(
                                        "SELECT updatedAt FROM website WHERE name='cache_v1:base_data_init_v3'"));
                        assertEquals(
                                String.valueOf(UpgradeVersionHandler.SQL_VERSION),
                                db.scalar(
                                        "SELECT value FROM website WHERE name=?",
                                        CacheService.ZRLOG_SQL_VERSION_KEY));
                        config.configDatabase();
                        assertSame(
                                cache,
                                config.getServerConfig()
                                        .getBindings()
                                        .build()
                                        .get(CacheService.class));
                    }
                    assertNull(
                            db.scalar(
                                    "SELECT value FROM website WHERE name=?",
                                    DistributedLock.SCHEMA_UPGRADE_KEY));
                }
            }
        } finally {
            if (previousRoot == null) System.clearProperty("sws.root.path");
            else System.setProperty("sws.root.path", previousRoot);
        }
    }

    private static final class TestConfig extends ZrLogConfig {
        private TestConfig() {
            super(19089, null, "");
        }

        private void start(DataSourceWrapper database) {
            this.dataSource = database;
            webSetups.addAll(
                    WebSetupLoader.load(
                            new WebSetupContext(
                                    this, dbPropertiesFile, installLockFile, "", null)));
            initializeWebSetups();
        }

        private CacheService cache() {
            return getServerConfig().getBindings().build().get(CacheService.class);
        }

        @Override
        public boolean isInstalled() {
            return dataSource != null;
        }

        @Override
        protected TokenService initTokenService() {
            return null;
        }

        @Override
        public List<IPlugin> getBasePluginList() {
            return Collections.emptyList();
        }

        // The fixture owns the datasource; no HTTP server or background plugins are started.
        @Override
        public void stop() {}
    }
}

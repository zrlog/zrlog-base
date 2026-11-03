package com.zrlog.business.version;

import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.common.CacheService;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.exception.DatabaseInitializationException;
import com.zrlog.data.service.DistributedLock;
import com.zrlog.plugin.IPlugin;
import com.zrlog.test.support.ZrLogTestDatabase;
import com.zrlog.web.WebSetupContext;
import com.zrlog.web.WebSetupLoader;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Historical-schema lifecycle fixture; fresh installation is covered by the install/main suites.
 */
public class DatabaseMigrationLifecycleTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test(timeout = 10000)
    public void migrationPrecedesCacheWritesAndFailureStopsModuleStartup() throws Exception {
        String previousRoot = System.getProperty("sws.root.path");
        System.setProperty("sws.root.path", temporary.getRoot().getAbsolutePath());
        try {
            for (boolean invalid : new boolean[]{false, true}) {
                try (ZrLogTestDatabase db = ZrLogTestDatabase.openWebApi("/schema/v30-install-e8d8c9d.sql")) {
                    db.putWebsite(CacheService.ZRLOG_SQL_VERSION_KEY, "30");
                    db.putWebsite("title", "Historical site");
                    db.putWebsite("template", "/include/templates/default");
                    if (invalid) {
                        db.update("INSERT INTO user_passkey_challenge(requestId,ceremony,requestJson,createdAt,expiresAt) VALUES(?,?,?,?,?)",
                                "bad-time", "authentication", "{}", Long.MAX_VALUE, Long.MAX_VALUE);
                    }
                    TestConfig config = new TestConfig();
                    if (invalid) {
                        assertThrows(DatabaseInitializationException.class, () -> config.start(db.dataSource()));
                        assertEquals("30", db.scalar("SELECT value FROM website WHERE name=?", CacheService.ZRLOG_SQL_VERSION_KEY));
                        assertNull(db.scalar("SELECT value FROM website WHERE name='base_data_init_cache_v3'"));
                    } else {
                        config.start(db.dataSource());
                        CacheService cache = config.getServerConfig().getBindings().build().get(CacheService.class);
                        assertEquals(31, cache.getCurrentSqlVersion());
                        assertEquals("Historical site", cache.getPublicWebSiteInfo().getTitle());
                        assertNotNull(db.scalar("SELECT updatedAt FROM website WHERE name='base_data_init_cache_v3'"));
                        assertEquals("31", db.scalar("SELECT value FROM website WHERE name=?", CacheService.ZRLOG_SQL_VERSION_KEY));
                        config.configDatabase();
                        assertSame(cache, config.getServerConfig().getBindings().build().get(CacheService.class));
                    }
                    assertNull(db.scalar("SELECT value FROM website WHERE name=?", DistributedLock.SCHEMA_UPGRADE_KEY));
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
            webSetups.addAll(WebSetupLoader.load(new WebSetupContext(this, dbPropertiesFile, installLockFile, "", null)));
            initializeWebSetups();
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
        public void stop() {
        }
    }
}

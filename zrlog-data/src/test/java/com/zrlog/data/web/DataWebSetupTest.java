package com.zrlog.data.web;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.di.SwsBindings;
import com.zrlog.common.CacheService;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.plugin.IPlugin;
import com.zrlog.test.support.ZrLogTestDatabase;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class DataWebSetupTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void cacheCanBeResolvedAfterDatabaseBecomesReadyWithoutRegisteringAgain() throws Exception {
        String previousRoot = System.getProperty("sws.root.path");
        System.setProperty("sws.root.path", temporaryFolder.getRoot().getAbsolutePath());
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open()) {
            TestConfig config = new TestConfig();
            DataWebSetup setup = new DataWebSetup(config);
            setup.setup();
            new ApplicationContext(config.getServerConfig()).init();
            SwsBindings.Container container = config.getServerConfig().getBindings().build();
            assertEquals(Constants.DEFAULT_LANGUAGE, setup.getPublicWebSiteInfo().getLanguage());
            SwsBindings.CreationException error = assertThrows(SwsBindings.CreationException.class,
                    () -> container.get(CacheService.class));
            assertEquals("Application database is not ready", error.getCause().getMessage());

            db.putWebsite("title", "Shared cache site");
            config.attachDatabase(db.dataSource());
            setup.onDatabaseReady(db.dataSource());
            CacheService cache = container.get(CacheService.class);
            assertSame(cache.getPublicWebSiteInfo(), setup.getPublicWebSiteInfo());
            assertEquals("Shared cache site", cache.getPublicWebSiteInfo().getTitle());
            assertSame(cache, container.get(CacheService.class));
            db.putWebsite("title", "Updated site");
            cache.refreshInitData();
            assertEquals("Updated site", container.get(CacheService.class).getPublicWebSiteInfo().getTitle());
        } finally {
            if (previousRoot == null) System.clearProperty("sws.root.path");
            else System.setProperty("sws.root.path", previousRoot);
        }
    }

    private static final class TestConfig extends ZrLogConfig {
        private TestConfig() {
            super(19089, null, "");
        }

        private void attachDatabase(DataSourceWrapper database) {
            this.dataSource = database;
        }

        @Override
        public boolean isInstalled() {
            return dataSource != null;
        }

        @Override
        public DataSourceWrapper configDatabase() {
            return dataSource;
        }

        @Override
        protected TokenService initTokenService() {
            return null;
        }

        @Override
        public List<IPlugin> getBasePluginList() {
            return Collections.emptyList();
        }

        // The database fixture owns and restores its data source.
        @Override
        public void stop() {
        }
    }
}

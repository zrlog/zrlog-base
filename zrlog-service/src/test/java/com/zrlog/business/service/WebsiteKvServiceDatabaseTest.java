package com.zrlog.business.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.zrlog.business.rest.base.UpgradeWebSiteInfo;
import com.zrlog.business.updater.AutoUpgradeVersionType;
import com.zrlog.test.support.ZrLogTestDatabase;
import com.zrlog.test.support.ZrLogTestDatabase.DatabaseType;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;
import java.util.Map;

@RunWith(Parameterized.class)
public class WebsiteKvServiceDatabaseTest {

    @Parameterized.Parameters(name = "{0}")
    public static DatabaseType[] databases() {
        return DatabaseType.values();
    }

    private final DatabaseType databaseType;

    public WebsiteKvServiceDatabaseTest(DatabaseType databaseType) {
        this.databaseType = databaseType;
    }

    @Test
    public void shouldReadWriteListAndRemoveWebsiteKvUsingInstallSchema() throws Exception {
        try (ZrLogTestDatabase ignored = ZrLogTestDatabase.open(databaseType)) {
            WebsiteKvService service = new WebsiteKvService();

            assertTrue(service.putString("feature.alpha", "one"));
            assertEquals("one", service.getString("feature.alpha"));
            assertTrue(service.putString("feature.alpha", "two"));
            assertTrue(service.putString("feature.beta", "beta-value"));

            Map<String, Object> values = service.getByNames(List.of("feature.alpha", "feature.beta", "missing"));
            List<Map<String, Object>> prefixed = service.listByPrefix("feature.");

            assertEquals("two", values.get("feature.alpha"));
            assertEquals("beta-value", values.get("feature.beta"));
            assertNull(values.get("missing"));
            assertEquals(2, prefixed.size());
            assertEquals("feature.alpha", prefixed.get(0).get("name"));
            assertEquals("feature.beta", prefixed.get(1).get("name"));
            assertEquals("two", prefixed.get(0).get("value"));
            assertEquals(3L, ((Number) prefixed.get(0).get("size")).longValue());

            assertTrue(service.removeQuietly("feature.alpha"));
            assertEquals("", service.getString("feature.alpha"));
        }
    }

    @Test
    public void explicitDataSourcesKeepReadsAndWritesOnTheirOwnDatabase() throws Exception {
        try (ZrLogTestDatabase first = ZrLogTestDatabase.open(databaseType)) {
            WebsiteKvService firstService = new WebsiteKvService(first.dataSource());
            assertTrue(firstService.putString("isolated.setting", "first"));
            try (ZrLogTestDatabase second = ZrLogTestDatabase.open(databaseType)) {
                WebsiteKvService secondService = new WebsiteKvService(second.dataSource());
                assertTrue(secondService.putString("isolated.setting", "second"));
                assertEquals("first", firstService.getString("isolated.setting"));
                assertEquals(
                        "first",
                        firstService
                                .getByNames(List.of("isolated.setting"))
                                .get("isolated.setting"));
                assertEquals("first", firstService.listByPrefix("isolated.").get(0).get("value"));

                assertTrue(firstService.putString("isolated.setting", "updated-first"));
                assertEquals("updated-first", firstService.getString("isolated.setting"));
                assertEquals("second", secondService.getString("isolated.setting"));
                assertTrue(firstService.remove("isolated.setting"));
                assertEquals("", firstService.getString("isolated.setting"));
                assertEquals("second", secondService.getString("isolated.setting"));
            }
        }
    }

    @Test
    public void shouldBuildUpgradeWebsiteInfoFromRealWebsiteKv() throws Exception {
        try (ZrLogTestDatabase ignored = ZrLogTestDatabase.open(databaseType)) {
            WebsiteKvService service = new WebsiteKvService();

            UpgradeWebSiteInfo defaults = service.upgradeWebSiteInfo();
            assertEquals(Integer.valueOf(AutoUpgradeVersionType.ONE_DAY.getCycle()),
                    defaults.getAutoUpgradeVersion());
            assertEquals(Boolean.FALSE, defaults.getUpgradePreview());

            assertTrue(service.putString(WebsiteKvService.AUTO_UPGRADE_VERSION_KEY,
                    String.valueOf(AutoUpgradeVersionType.ONE_HOUR.getCycle())));
            assertTrue(service.putString(WebsiteKvService.UPGRADE_PREVIEW_KEY, "true"));

            UpgradeWebSiteInfo configured = service.upgradeWebSiteInfo();
            assertEquals(Integer.valueOf(AutoUpgradeVersionType.ONE_HOUR.getCycle()),
                    configured.getAutoUpgradeVersion());
            assertEquals(Boolean.TRUE, configured.getUpgradePreview());
        }
    }
}

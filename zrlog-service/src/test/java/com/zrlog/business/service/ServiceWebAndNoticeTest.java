package com.zrlog.business.service;

import com.google.gson.Gson;
import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.business.dto.StoredUpgradeNotice;
import com.zrlog.business.rest.response.CheckVersionResponse;
import com.zrlog.business.rest.response.PublicInfoVO;
import com.zrlog.common.CacheService;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.cache.dto.TagDTO;
import com.zrlog.common.cache.dto.TypeDTO;
import com.zrlog.common.cache.dto.UserBasicDTO;
import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.common.vo.Version;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import com.zrlog.web.WebSiteInfoProvider;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ServiceWebAndNoticeTest {

    private final ZrLogConfig previousConfig = Constants.zrLogConfig;

    @After
    public void tearDown() {
        Constants.zrLogConfig = previousConfig;
    }

    @Test
    public void shouldBuildPublicInfoWithLightThemeColor() throws Exception {
        PublicWebSiteInfo info = siteInfo(false);
        Constants.zrLogConfig = new TestZrLogConfig(info);

        PublicInfoVO publicInfo = new CommonService().getPublicInfo(request("/blog", "fallback.example.com"));

        assertNotNull(publicInfo.getCurrentVersion());
        assertEquals("ZrLog", publicInfo.getWebsiteTitle());
        assertEquals("//example.com/blog/", publicInfo.getHomeUrl());
        assertEquals(false, publicInfo.getAdmin_darkMode());
        assertEquals("#1677ff", publicInfo.getAdmin_color_primary());
        assertEquals("#1677ff", publicInfo.getPwaThemeColor());
        assertEquals("app-id", publicInfo.getAppId());
    }

    @Test
    public void shouldBuildPublicInfoWithDarkThemeColor() throws Exception {
        PublicWebSiteInfo info = siteInfo(true);
        Constants.zrLogConfig = new TestZrLogConfig(info);

        PublicInfoVO publicInfo = new CommonService().getPublicInfo(request("/", "fallback.example.com"));

        assertEquals("//example.com/", publicInfo.getHomeUrl());
        assertEquals(true, publicInfo.getAdmin_darkMode());
        assertEquals("#000000", publicInfo.getPwaThemeColor());
    }

    @Test
    public void shouldBuildEmptyUpgradeNoticeWhenVersionIsMissing() {
        CheckVersionResponse nullResponse = new UpgradeNoticeService().buildResponse(null);
        CheckVersionResponse missingDate = new UpgradeNoticeService().buildResponse(new Version());

        assertFalse(nullResponse.getUpgrade());
        assertNull(nullResponse.getVersion());
        assertFalse(missingDate.getUpgrade());
        assertNull(missingDate.getVersion());
    }

    @Test
    public void shouldNormalizePreviewUpgradeNoticeForDisplay() {
        Version version = new Version();
        version.setBuildId("future-build");
        version.setVersion("99.0.0-SNAPSHOT");
        version.setReleaseDate("2099-01-01 00:00");
        version.setBuildDate(new Date(System.currentTimeMillis() + 86_400_000L));

        CheckVersionResponse response = new UpgradeNoticeService().buildResponse(version);

        assertTrue(response.getUpgrade());
        assertEquals("99.0.0", response.getVersion().getVersion());
        assertNotNull(response.getVersion().getType());
    }

    @Test
    public void shouldReturnEmptyNoticeWhenKvStoreIsUnavailable() {
        CheckVersionResponse response = new UpgradeNoticeService().getNotice();

        assertFalse(response.getUpgrade());
        assertNull(response.getVersion());
    }

    @Test
    public void shouldReturnEmptyNoticeWhenStoredNoticeJsonIsInvalid() throws Exception {
        FakeWebsiteKvService kvService = new FakeWebsiteKvService();
        kvService.storedJson = "{invalid-json";
        UpgradeNoticeService service = noticeService(kvService);

        CheckVersionResponse response = service.getNotice();

        assertFalse(response.getUpgrade());
        assertNull(response.getVersion());
    }

    @Test
    public void shouldReadStoredUpgradeNoticeFromKvStore() throws Exception {
        Version version = futureVersion();
        StoredUpgradeNotice storedUpgradeNotice = new StoredUpgradeNotice();
        storedUpgradeNotice.setVersion(version);
        storedUpgradeNotice.setUpdatedAt(version.getBuildDate().getTime());
        FakeWebsiteKvService kvService = new FakeWebsiteKvService();
        kvService.storedJson = new Gson().toJson(storedUpgradeNotice);
        UpgradeNoticeService service = noticeService(kvService);

        StoredUpgradeNotice notice = service.getStoredUpgradeNotice();

        assertNotNull(notice);
        assertEquals("future-build", notice.getVersion().getBuildId());
        assertEquals(version.getBuildDate().getTime(), notice.getUpdatedAt().longValue());
    }

    @Test
    public void shouldSyncFutureUpgradeNoticeToKvStoreWhenInstalled() throws Exception {
        Constants.zrLogConfig = new TestZrLogConfig(siteInfo(false), true);
        FakeWebsiteKvService kvService = new FakeWebsiteKvService();
        UpgradeNoticeService service = noticeService(kvService);

        service.sync(futureVersion());

        assertEquals("admin_message_center_upgrade_notice", kvService.putKey);
        assertTrue(kvService.putValue.contains("future-build"));
        assertNull(kvService.removedKey);
    }

    @Test
    public void shouldClearStoredUpgradeNoticeWhenNoUpgradeIsAvailable() throws Exception {
        Constants.zrLogConfig = new TestZrLogConfig(siteInfo(false), true);
        FakeWebsiteKvService kvService = new FakeWebsiteKvService();
        UpgradeNoticeService service = noticeService(kvService);

        service.sync(null);

        assertEquals("admin_message_center_upgrade_notice", kvService.removedKey);
        assertNull(kvService.putKey);
    }

    @Test
    public void shouldIgnoreKvWriteFailuresWhenSyncingUpgradeNotice() throws Exception {
        Constants.zrLogConfig = new TestZrLogConfig(siteInfo(false), true);
        FakeWebsiteKvService kvService = new FakeWebsiteKvService();
        kvService.throwOnPut = true;
        UpgradeNoticeService service = noticeService(kvService);

        service.sync(futureVersion());

        assertEquals(1, kvService.putAttempts);
    }

    @Test
    public void shouldIgnoreKvRemoveFailuresWhenClearingUpgradeNotice() throws Exception {
        FakeWebsiteKvService kvService = new FakeWebsiteKvService();
        kvService.throwOnRemove = true;
        UpgradeNoticeService service = noticeService(kvService);

        service.clearStoredUpgradeNotice();

        assertEquals(1, kvService.removeAttempts);
    }

    @Test
    public void shouldSkipSyncWhenConfigIsMissingOrNotInstalled() throws Exception {
        FakeWebsiteKvService missingConfigKv = new FakeWebsiteKvService();
        Constants.zrLogConfig = null;
        noticeService(missingConfigKv).sync(futureVersion());
        assertNull(missingConfigKv.putKey);
        assertNull(missingConfigKv.removedKey);

        FakeWebsiteKvService notInstalledKv = new FakeWebsiteKvService();
        Constants.zrLogConfig = new TestZrLogConfig(siteInfo(false), false);
        noticeService(notInstalledKv).sync(futureVersion());
        assertNull(notInstalledKv.putKey);
        assertNull(notInstalledKv.removedKey);
    }

    private static PublicWebSiteInfo siteInfo(boolean darkMode) {
        PublicWebSiteInfo info = new PublicWebSiteInfo();
        info.setTitle("ZrLog");
        info.setHost("example.com");
        info.setLanguage("zh_CN");
        info.setAdmin_darkMode(darkMode);
        info.setAdmin_color_primary("#1677ff");
        info.setAppId("app-id");
        return info;
    }

    private static HttpRequest request(String contextPath, String host) {
        return (HttpRequest) Proxy.newProxyInstance(
                ServiceWebAndNoticeTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getContextPath".equals(method.getName())) {
                        return contextPath;
                    }
                    if ("getHeader".equals(method.getName())) {
                        return host;
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }

    private static UpgradeNoticeService noticeService(FakeWebsiteKvService kvService) throws Exception {
        UpgradeNoticeService service = new UpgradeNoticeService();
        Field field = UpgradeNoticeService.class.getDeclaredField("kvService");
        field.setAccessible(true);
        field.set(service, kvService);
        return service;
    }

    private static Version futureVersion() {
        Version version = new Version();
        version.setBuildId("future-build");
        version.setVersion("99.0.0-SNAPSHOT");
        version.setReleaseDate("2099-01-01 00:00");
        version.setBuildDate(new Date(System.currentTimeMillis() + 86_400_000L));
        return version;
    }

    private static class TestZrLogConfig extends ZrLogConfig {
        private CacheService cacheService;

        @Override
        public <T> T getWebSetup(Class<T> capability) {
            if (capability == WebSiteInfoProvider.class) {
                return capability.cast((WebSiteInfoProvider) cacheService::getPublicWebSiteInfo);
            }
            return super.getWebSetup(capability);
        }


        private final boolean installed;

        TestZrLogConfig(PublicWebSiteInfo info) throws Exception {
            this(info, false);
        }

        TestZrLogConfig(PublicWebSiteInfo info, boolean installed) throws Exception {
            super(19082, null, "/");
            this.cacheService = new TestCacheService(info);
            this.installed = installed;
        }

        @Override
        public boolean isInstalled() {
            return installed;
        }

        @Override
        public DataSourceWrapper configDatabase() {
            return null;
        }

        @Override
        protected TokenService initTokenService() {
            return null;
        }

        @Override
        public List<IPlugin> getBasePluginList() {
            return new Plugins();
        }
    }

    private static class FakeWebsiteKvService extends WebsiteKvService {

        private String storedJson;
        private String putKey;
        private String putValue;
        private String removedKey;
        private int putAttempts;
        private int removeAttempts;
        private boolean throwOnPut;
        private boolean throwOnRemove;

        @Override
        public String getString(String key) {
            return storedJson;
        }

        @Override
        public boolean putString(String key, String value) {
            putAttempts++;
            if (throwOnPut) {
                throw new IllegalStateException("write failed");
            }
            putKey = key;
            putValue = value;
            return true;
        }

        @Override
        public boolean remove(String key) throws SQLException {
            removeAttempts++;
            if (throwOnRemove) {
                throw new SQLException("remove failed");
            }
            removedKey = key;
            return true;
        }
    }

    private static class TestCacheService implements CacheService {

        private final PublicWebSiteInfo publicWebSiteInfo;

        TestCacheService(PublicWebSiteInfo publicWebSiteInfo) {
            this.publicWebSiteInfo = publicWebSiteInfo;
        }

        @Override
        public long getCurrentSqlVersion() {
            return 0;
        }

        @Override
        public long getWebSiteVersion() {
            return 0;
        }

        @Override
        public BaseDataInitVO getInitData() {
            return null;
        }

        @Override
        public BaseDataInitVO refreshInitData() {
            return null;
        }

        @Override
        public PublicWebSiteInfo getPublicWebSiteInfo() {
            return publicWebSiteInfo;
        }

        @Override
        public List<TypeDTO> getArticleTypes() {
            return null;
        }

        @Override
        public List<TagDTO> getTags() {
            return null;
        }

        @Override
        public UserBasicDTO getUserInfoById(Long userId) {
            return null;
        }

        @Override
        public Map<String, Object> getTemplateConfigMapWithCache(String template) {
            return null;
        }
    }
}

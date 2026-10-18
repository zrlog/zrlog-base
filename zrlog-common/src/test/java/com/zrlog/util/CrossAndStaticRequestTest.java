package com.zrlog.util;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.common.CacheService;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.cache.dto.TagDTO;
import com.zrlog.common.cache.dto.TypeDTO;
import com.zrlog.common.cache.dto.UserBasicDTO;
import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.plugin.BaseStaticSitePlugin;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import com.zrlog.web.WebSiteInfoProvider;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CrossAndStaticRequestTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldDetectStaticPluginRequestsByUserAgent() {
        assertFalse(BaseStaticSitePlugin.isStaticPluginRequest(null));
        assertFalse(BaseStaticSitePlugin.isStaticPluginRequest(request(null, null)));
        assertFalse(BaseStaticSitePlugin.isStaticPluginRequest(request("Browser", null)));
        assertTrue(BaseStaticSitePlugin.isStaticPluginRequest(
                request(BaseStaticSitePlugin.STATIC_USER_AGENT, null)));
    }

    @Test
    public void shouldEnableOriginForExplicitOriginOrStaticHtmlMode() throws Exception {
        withConfig(false, () -> {
            assertFalse(CrossUtils.isEnableOrigin(request(null, null)));
            assertTrue(CrossUtils.isEnableOrigin(request(null, "https://client.example")));
        });

        withConfig(true, () -> assertTrue(CrossUtils.isEnableOrigin(request(null, null))));
    }

    @Test
    public void shouldWriteCrossOriginHeadersWhenOriginIsEnabled() throws Exception {
        withConfig(false, () -> {
            ResponseRecorder recorder = new ResponseRecorder();

            CrossUtils.cross(request(null, "https://client.example"), recorder.response());

            assertEquals("https://client.example", recorder.headers.get("Access-Control-Allow-Origin"));
            assertEquals("true", recorder.headers.get("Access-Control-Allow-Credentials"));
        });
    }

    private void withConfig(boolean generatorHtmlStatus, ThrowingRunnable runnable) throws Exception {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        String previousRootPath = System.getProperty("sws.root.path");
        try {
            System.setProperty("sws.root.path", temporaryFolder.newFolder().getAbsolutePath());
            Constants.zrLogConfig = new TestZrLogConfig(generatorHtmlStatus);
            runnable.run();
        } finally {
            Constants.zrLogConfig = previousConfig;
            restoreProperty("sws.root.path", previousRootPath);
        }
    }

    private static HttpRequest request(String userAgent, String origin) {
        return (HttpRequest) Proxy.newProxyInstance(
                CrossAndStaticRequestTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getHeader".equals(method.getName())) {
                        if ("User-Agent".equals(args[0])) {
                            return userAgent;
                        }
                        if ("Origin".equals(args[0])) {
                            return origin;
                        }
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static class ResponseRecorder {

        private final Map<String, String> headers = new LinkedHashMap<>();

        private HttpResponse response() {
            return (HttpResponse) Proxy.newProxyInstance(
                    CrossAndStaticRequestTest.class.getClassLoader(),
                    new Class[]{HttpResponse.class},
                    (proxy, method, args) -> {
                        if ("addHeader".equals(method.getName())) {
                            headers.put(args[0].toString(), args[1].toString());
                        }
                        return null;
                    });
        }
    }

    private static class TestZrLogConfig extends ZrLogConfig {

        private final boolean generatorHtmlStatus;

        TestZrLogConfig(boolean generatorHtmlStatus) {
            super(18080, null, "");
            this.generatorHtmlStatus = generatorHtmlStatus;
        }

        @Override
        public boolean isInstalled() {
            return false;
        }

        @Override
        public DataSourceWrapper configDatabase() {
            return null;
        }

        @Override
        public <T> T getWebSetup(Class<T> capability) {
            if (capability == WebSiteInfoProvider.class) {
                CacheService cache = cacheForTest();
                return cache == null ? null : capability.cast(
                        (WebSiteInfoProvider) cache::getPublicWebSiteInfo);
            }
            return super.getWebSetup(capability);
        }

        private CacheService cacheForTest() {
            return new TestCacheService(generatorHtmlStatus);
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

    private static class TestCacheService implements CacheService {

        private final boolean generatorHtmlStatus;

        TestCacheService(boolean generatorHtmlStatus) {
            this.generatorHtmlStatus = generatorHtmlStatus;
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
            return new BaseDataInitVO();
        }

        @Override
        public BaseDataInitVO refreshInitData() {
            return new BaseDataInitVO();
        }

        @Override
        public PublicWebSiteInfo getPublicWebSiteInfo() {
            PublicWebSiteInfo publicWebSiteInfo = new PublicWebSiteInfo();
            publicWebSiteInfo.setGenerator_html_status(generatorHtmlStatus);
            return publicWebSiteInfo;
        }

        @Override
        public List<TypeDTO> getArticleTypes() {
            return Collections.emptyList();
        }

        @Override
        public List<TagDTO> getTags() {
            return Collections.emptyList();
        }

        @Override
        public UserBasicDTO getUserInfoById(Long userId) {
            return null;
        }

        @Override
        public Map<String, Object> getTemplateConfigMapWithCache(String template) {
            return Collections.emptyMap();
        }
    }
}

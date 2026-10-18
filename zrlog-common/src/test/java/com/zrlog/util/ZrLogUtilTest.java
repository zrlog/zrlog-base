package com.zrlog.util;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.util.EnvKit;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.common.CacheService;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.Updater;
import com.zrlog.common.UpdaterTypeEnum;
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
import java.io.File;
import java.lang.reflect.Proxy;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ZrLogUtilTest {

    private final ZrLogConfig previousConfig = Constants.zrLogConfig;

    @After
    public void tearDown() {
        Constants.zrLogConfig = previousConfig;
    }

    @Test
    public void shouldConvertRequestParamsToBean() {
        Map<String, String[]> params = new HashMap<>();
        params.put("name", new String[]{"ZrLog"});
        params.put("tags", new String[]{"java", "blog"});
        params.put("ignoredEmpty", new String[0]);
        params.put("ignoredNull", null);

        RequestParamBean bean = ZrLogUtil.convertRequestParam(params, RequestParamBean.class);

        assertEquals("ZrLog", bean.getName());
        assertEquals(List.of("java", "blog"), bean.getTags());
    }

    @Test
    public void shouldPreferConfiguredBlogHostWhenPresent() throws Exception {
        Constants.zrLogConfig = new TestZrLogConfig("configured.example.com");

        assertEquals("configured.example.com", ZrLogUtil.getBlogHost(request("/blog", "fallback.example.com",
                "/post/1")));
        assertEquals("//configured.example.com/blog/", ZrLogUtil.getHomeUrlWithHost(request("/blog",
                "fallback.example.com", "/post/1")));
        assertEquals("configured.example.com/blog/", ZrLogUtil.getHomeUrlWithHostNotProtocol(request("/blog",
                "fallback.example.com", "/post/1")));
    }

    @Test
    public void shouldBuildFullUrlFromConfiguredHost() throws Exception {
        Constants.zrLogConfig = new TestZrLogConfig("configured.example.com/");

        assertEquals("//configured.example.com/post/1", ZrLogUtil.getFullUrl(request("/blog",
                "fallback.example.com", "/post/1")));
    }

    @Test
    public void shouldBuildFullUrlFromRequestHostWithoutDroppingPathSeparator() throws Exception {
        Constants.zrLogConfig = new TestZrLogConfig("");

        assertEquals("//localhost:7080/all-3", ZrLogUtil.getFullUrl(request("/",
                "localhost:7080", "/all-3")));
        assertEquals("//localhost:7080/", ZrLogUtil.getFullUrl(request("/",
                "localhost:7080", "/")));

        Constants.zrLogConfig = new TestZrLogConfig("blog.example.com/");
        assertEquals("//blog.example.com/all-3", ZrLogUtil.getFullUrl(request("/",
                "ignored.example.com", "/all-3")));
    }

    @Test
    public void shouldFallbackToRequestHostWhenWebsiteHostIsMissing() throws Exception {
        Constants.zrLogConfig = new TestZrLogConfig("");

        assertEquals("fallback.example.com", ZrLogUtil.getBlogHost(request("/", "fallback.example.com", "/post/1")));
        assertEquals("", ZrLogUtil.getBlogHost(null));
    }

    @Test
    public void shouldExposeSmallUtilityContracts() {
        assertEquals(".ftl", ZrLogUtil.getViewExt("freemarker"));
        assertEquals(".jsp", ZrLogUtil.getViewExt("beetl"));
        assertTrue(ZrLogUtil.isInternalHostName("127.0.0.1"));
        assertFalse(ZrLogUtil.isInternalHostName("invalid.invalid.invalid"));
        assertEquals("true".equalsIgnoreCase(System.getenv("PREVIEW_MODE")), ZrLogUtil.isPreviewMode());
        assertEquals("true".equalsIgnoreCase(System.getenv("DOCKER_MODE")), ZrLogUtil.isDockerMode());
        assertEquals("true".equalsIgnoreCase(System.getenv("SYSTEM_SERVICE_MODE")), ZrLogUtil.isSystemServiceMode());
        assertEquals(System.getenv(ZrLogUtil.DB_PROPERTIES_KEY_IN_ENV), ZrLogUtil.getDbInfoByEnv());
        assertEquals(EnvKit.isLambda() ? System.getenv("LAMBDA_TASK_ROOT") : "/app", ZrLogUtil.getFaaSRoot());
        assertEquals("shell".equals(System.getenv("DOCKER_MODE_START_BY")), ZrLogUtil.isShellDockerMode());
        assertEquals("", Constants.getArticleUri());
        assertEquals(List.of("/"), Constants.articleRouterList());
        assertEquals(Constants.class, new Constants().getClass());
    }

    @Test
    public void shouldWriteLongCacheHeader() {
        ResponseRecorder recorder = new ResponseRecorder();

        ZrLogUtil.putLongTimeCache(recorder.response());

        assertEquals("max-age=31536000, immutable", recorder.headers.get("Cache-Control"));
    }

    @Test
    public void shouldCompareFetchedVersionAgainstCurrentBuildInfo() {
        assertFalse(ZrLogUtil.greatThenCurrentVersion(BlogBuildInfoUtil.getBuildId(),
                new Date(System.currentTimeMillis() + 86_400_000L), "99.0.0"));
        assertFalse(ZrLogUtil.greatThenCurrentVersion("older-release",
                new Date(BlogBuildInfoUtil.getTime().getTime() - 1), "99.0.0"));
        assertTrue(ZrLogUtil.greatThenCurrentVersion("future-release",
                new Date(System.currentTimeMillis() + 86_400_000L), "99.0.0"));
        assertTrue(ZrLogUtil.greatThenCurrentVersion("same-version-newer-build",
                new Date(BlogBuildInfoUtil.getTime().getTime() + 1), BlogBuildInfoUtil.getVersion()));
    }

    @Test
    public void shouldReadWebsiteInfoFromConstantsCache() throws Exception {
        Constants.zrLogConfig = null;
        assertEquals(Constants.DEFAULT_LANGUAGE, Constants.getLanguage());

        Constants.zrLogConfig = new TestZrLogConfig("configured.example.com", "en_US", true, null);

        assertEquals("configured.example.com", Constants.getHost());
        assertEquals("en_US", Constants.getLanguage());
        assertTrue(Constants.isStaticHtmlStatus());
        Constants.setLastAccessTime(1234L);
        assertEquals(1234L, Constants.getLastAccessTime());
    }

    @Test
    public void shouldDetectWarModeByUpdaterTypeOnlyOutsideDevMode() throws Exception {
        String previousRunMode = System.getProperty("sws.run.mode");
        try {
            System.setProperty("sws.run.mode", "test");
            Constants.zrLogConfig = new TestZrLogConfig("configured.example.com", updater(UpdaterTypeEnum.WAR));
            assertTrue(ZrLogUtil.isWarMode());

            Constants.zrLogConfig = new TestZrLogConfig("configured.example.com", updater(UpdaterTypeEnum.ZIP));
            assertFalse(ZrLogUtil.isWarMode());

            Constants.zrLogConfig = new TestZrLogConfig("configured.example.com", null);
            assertFalse(ZrLogUtil.isWarMode());

            System.setProperty("sws.run.mode", "dev");
            Constants.zrLogConfig = new TestZrLogConfig("configured.example.com", updater(UpdaterTypeEnum.WAR));
            assertFalse(ZrLogUtil.isWarMode());
        } finally {
            restoreProperty("sws.run.mode", previousRunMode);
        }
    }

    private static HttpRequest request(String contextPath, String host, String uri) {
        return (HttpRequest) Proxy.newProxyInstance(
                ZrLogUtilTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getContextPath".equals(method.getName())) {
                        return contextPath;
                    }
                    if ("getHeader".equals(method.getName()) && "Host".equals(args[0])) {
                        return host;
                    }
                    if ("getUri".equals(method.getName())) {
                        return uri;
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }

    private static Updater updater(UpdaterTypeEnum type) {
        return new Updater() {
            @Override
            public void restartProcessAsync(Version upgradeVersion) {
            }

            @Override
            public String getUnzipPath() {
                return "/tmp";
            }

            @Override
            public File execFile() {
                return new File("zrlog");
            }

            @Override
            public UpdaterTypeEnum getType() {
                return type;
            }
        };
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static class ResponseRecorder {

        private final Map<String, String> headers = new HashMap<>();

        private HttpResponse response() {
            return (HttpResponse) Proxy.newProxyInstance(
                    ZrLogUtilTest.class.getClassLoader(),
                    new Class[]{HttpResponse.class},
                    (proxy, method, args) -> {
                        if ("addHeader".equals(method.getName())) {
                            headers.put(args[0].toString(), args[1].toString());
                        }
                        return null;
                    });
        }
    }

    public static class RequestParamBean {

        private String name;
        private List<String> tags;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
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


        TestZrLogConfig(String host) throws Exception {
            this(host, Constants.DEFAULT_LANGUAGE, false, null);
        }

        TestZrLogConfig(String host, Updater updater) throws Exception {
            this(host, Constants.DEFAULT_LANGUAGE, false, updater);
        }

        TestZrLogConfig(String host, String language, boolean generatorHtmlStatus, Updater updater) throws Exception {
            super(19083, updater, "/");
            this.cacheService = new TestCacheService(host, language, generatorHtmlStatus);
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
        protected TokenService initTokenService() {
            return null;
        }

        @Override
        public List<IPlugin> getBasePluginList() {
            return new Plugins();
        }
    }

    private static class TestCacheService implements CacheService {

        private final PublicWebSiteInfo publicWebSiteInfo;

        TestCacheService(String host, String language, boolean generatorHtmlStatus) {
            publicWebSiteInfo = new PublicWebSiteInfo();
            publicWebSiteInfo.setHost(host);
            publicWebSiteInfo.setLanguage(language);
            publicWebSiteInfo.setGenerator_html_status(generatorHtmlStatus);
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
        public List<TypeDTO> getArticleTypes() {
            return List.of();
        }

        @Override
        public List<TagDTO> getTags() {
            return List.of();
        }

        @Override
        public UserBasicDTO getUserInfoById(Long userId) {
            return null;
        }

        @Override
        public PublicWebSiteInfo getPublicWebSiteInfo() {
            return publicWebSiteInfo;
        }

        @Override
        public Map<String, Object> getTemplateConfigMapWithCache(String template) {
            return Map.of();
        }
    }
}

package com.zrlog.business.plugin;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.api.Interceptor;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.util.PathUtil;
import com.sun.net.httpserver.HttpServer;
import com.zrlog.business.plugin.type.StaticSiteType;
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
import java.io.File;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotEquals;

public class StaticSitePluginTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldParseOnlyRootHtmlLinksFromCachedPage() throws Exception {
        TestStaticSitePlugin plugin = new TestStaticSitePlugin();
        File html = Files.createTempFile("zrlog-static-site", ".html").toFile();
        Files.writeString(html.toPath(), "<html><body>"
                + "<a href=\"#top\">anchor</a>"
                + "<a href=\"javascript:;\">noop</a>"
                + "<a href=\"/article.html#comment\">article</a>"
                + "<a href=\"/asset.css\">asset</a>"
                + "<a href=\"relative.html\">relative</a>"
                + "</body></html>", StandardCharsets.UTF_8);

        StaticSiteHtmlLinks.parseInto(html, "", plugin.handleStatusPageMap);

        assertEquals(StaticSitePlugin.HandleState.NEW, plugin.handleStatusPageMap.get("/article.html"));
        assertFalse(plugin.handleStatusPageMap.containsKey(""));
        assertFalse(plugin.handleStatusPageMap.containsKey("/asset.css"));
        assertFalse(plugin.handleStatusPageMap.containsKey("relative.html"));
    }

    @Test
    public void shouldParseProtocolRelativeLinksForConfiguredHost() throws Exception {
        TestStaticSitePlugin plugin = new TestStaticSitePlugin();
        File html = Files.createTempFile("zrlog-static-host", ".html").toFile();
        Files.writeString(html.toPath(), "<html><body>"
                + "<a href=\"//blog.example.com\">home</a>"
                + "<a href=\"//blog.example.com/about.html#intro\">about</a>"
                + "<a href=\"//cdn.example.com/asset.html\">external</a>"
                + "</body></html>", StandardCharsets.UTF_8);

        StaticSiteHtmlLinks.parseInto(html, "blog.example.com", plugin.handleStatusPageMap);

        assertEquals(StaticSitePlugin.HandleState.NEW, plugin.handleStatusPageMap.get("/about.html"));
        assertFalse(plugin.handleStatusPageMap.containsKey("/"));
        assertFalse(plugin.handleStatusPageMap.containsKey("//cdn.example.com/asset.html"));
    }

    @Test
    public void shouldIgnoreNonHtmlFilesDuringParse() throws Exception {
        TestStaticSitePlugin plugin = new TestStaticSitePlugin();
        File css = Files.createTempFile("zrlog-static-style", ".css").toFile();
        Files.writeString(css.toPath(), "<a href=\"/ignored.html\">ignored</a>", StandardCharsets.UTF_8);

        StaticSiteHtmlLinks.parseInto(css, "blog.example.com", plugin.handleStatusPageMap);

        assertTrue(plugin.handleStatusPageMap.isEmpty());
    }

    @Test
    public void shouldExposeDefaultStaticSiteMetadata() throws Exception {
        TestStaticSitePlugin plugin = new TestStaticSitePlugin();
        File first = Files.createTempFile("zrlog-static-a", ".txt").toFile();
        File second = Files.createTempFile("zrlog-static-b", ".txt").toFile();
        Files.writeString(first.toPath(), "a", StandardCharsets.UTF_8);
        Files.writeString(second.toPath(), "b", StandardCharsets.UTF_8);
        plugin.cacheFiles.add(first);
        plugin.cacheFiles.add(second);

        String version = plugin.getSiteVersion();

        assertEquals("/static/error/404.html", plugin.notFindFile());
        assertFalse(version.isEmpty());
        Files.writeString(second.toPath(), "changed", StandardCharsets.UTF_8);
        assertNotEquals(version, plugin.getSiteVersion());
    }

    @Test
    public void shouldResolveStaticSuffixFromRequestOrWebsiteConfig() throws Exception {
        withConfig(false, "blog.example.com", () -> {
            assertEquals("", StaticSitePlugin.getSuffix(request("/article", null, null)));
            assertEquals(".html", StaticSitePlugin.getSuffix(
                    request("/article", BaseStaticSitePlugin.STATIC_USER_AGENT, null)));
        });

        withConfig(true, "blog.example.com", () ->
                assertEquals(".html", StaticSitePlugin.getSuffix(request("/article", null, null))));
    }

    @Test
    public void shouldDetectDisabledStaticSiteByConfigAndHost() throws Exception {
        withConfig(false, "blog.example.com", () -> assertTrue(StaticSitePlugin.isDisabled()));
        withConfig(true, "", () -> assertTrue(StaticSitePlugin.isDisabled()));
        withConfig(true, "blog.example.com", () -> assertFalse(StaticSitePlugin.isDisabled()));
    }

    @Test
    public void shouldTreatDisabledStaticSiteAsAlreadySynchronized() throws Exception {
        TestStaticSitePlugin plugin = new TestStaticSitePlugin();

        withConfig(false, "blog.example.com", () -> assertTrue(plugin.isSynchronized("http")));
    }

    @Test
    public void shouldReturnFalseWhenRemoteStaticSiteVersionCannotBeFetched() throws Exception {
        TestStaticSitePlugin plugin = new TestStaticSitePlugin();
        int unusedPort;
        try (java.net.ServerSocket serverSocket = new java.net.ServerSocket(0)) {
            unusedPort = serverSocket.getLocalPort();
        }

        withConfig(true, "127.0.0.1:" + unusedPort, () ->
                assertFalse(plugin.isSynchronized("http")));
    }

    @Test
    public void shouldCompareRemoteStaticSiteVersion() throws Exception {
        TestStaticSitePlugin plugin = new TestStaticSitePlugin();
        File cacheFile = Files.createTempFile("zrlog-static-version", ".html").toFile();
        Files.writeString(cacheFile.toPath(), "version-source", StandardCharsets.UTF_8);
        plugin.cacheFiles.add(cacheFile);
        AtomicReference<String> remoteVersion = new AtomicReference<>(plugin.getSiteVersion());
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/static/version.txt", exchange -> {
            byte[] body = remoteVersion.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            withConfig(true, "127.0.0.1:" + server.getAddress().getPort(), () -> {
                assertTrue(plugin.isSynchronized("http"));
                remoteVersion.set("different-version");
                assertFalse(plugin.isSynchronized("http"));
            });
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void shouldSaveCacheVersionWhenNoPagesNeedFetching() throws Exception {
        TestStaticSitePlugin plugin = new TestStaticSitePlugin();
        File cacheFile = Files.createTempFile("zrlog-static-do-fetch", ".html").toFile();
        Files.writeString(cacheFile.toPath(), "fetch-source", StandardCharsets.UTF_8);
        plugin.cacheFiles.add(cacheFile);
        String expectedVersion = plugin.getSiteVersion();

        withConfig(true, "blog.example.com", () -> {
            plugin.doFetch(null, null);

            assertEquals(expectedVersion, Files.readString(plugin.getCacheFile(plugin.getVersionFileName()).toPath()));
        });
    }

    @Test
    public void shouldFetchPagesAndParseDiscoveredLinks() throws Exception {
        TestStaticSitePlugin plugin = new TestStaticSitePlugin();
        plugin.handleStatusPageMap.put("/index.html", StaticSitePlugin.HandleState.NEW);
        StaticFetchInterceptor.pages.clear();
        StaticFetchInterceptor.pages.put("/index.html",
                "<html><body><a href=\"/next.html\">next</a></body></html>");
        StaticFetchInterceptor.pages.put("/next.html",
                "<html><body><a href=\"/index.html\">home</a></body></html>");

        withConfig(true, "blog.example.com", plugin, config -> {
            ServerConfig serverConfig = config.getServerConfig();
            serverConfig.addInterceptor(StaticFetchInterceptor.class);
            ApplicationContext applicationContext = new ApplicationContext(serverConfig);
            applicationContext.init();

            plugin.doFetch(config, applicationContext);

            assertEquals(StaticSitePlugin.HandleState.HANDLED, plugin.handleStatusPageMap.get("/index.html"));
            assertEquals(StaticSitePlugin.HandleState.HANDLED, plugin.handleStatusPageMap.get("/next.html"));
            assertEquals("test.static.version", plugin.getDbCacheKey());
            assertTrue(plugin.getCacheFile("/index.html").exists());
            assertTrue(plugin.getCacheFile("/next.html").exists());
            assertTrue(plugin.getCacheFile(plugin.getVersionFileName()).exists());
            assertTrue(plugin.cacheFiles.contains(plugin.getCacheFile("/index.html")));
            assertTrue(plugin.cacheFiles.contains(plugin.getCacheFile("/next.html")));
        });
    }

    @Test
    public void shouldCopyExistingResourceToCacheFolderAndIgnoreMissingResource() throws Exception {
        TestStaticSitePlugin plugin = new TestStaticSitePlugin();

        withConfig(true, "blog.example.com", () -> {
            plugin.copyResourceToCacheFolder("/conf/error/404.html");
            plugin.copyResourceToCacheFolder("/conf/error/missing.html");

            assertTrue(plugin.getCacheFile("/conf/error/404.html").exists());
            assertFalse(plugin.getCacheFile("/conf/error/missing.html").exists());
        });
    }

    @Test
    public void shouldTreatAdminAndNotFoundFetchesAsHandledWithoutParsing() throws Exception {
        TestStaticSitePlugin plugin = new TestStaticSitePlugin();
        plugin.handleStatusPageMap.put("/admin/index", StaticSitePlugin.HandleState.NEW);
        plugin.handleStatusPageMap.put(plugin.notFindFile(), StaticSitePlugin.HandleState.NEW);
        StaticFetchInterceptor.pages.clear();
        StaticFetchInterceptor.pages.put("/admin/index",
                "<html><body><a href=\"/should-not-parse.html\">ignored</a></body></html>");
        StaticFetchInterceptor.pages.put(plugin.notFindFile(),
                "<html><body><a href=\"/also-ignored.html\">ignored</a></body></html>");

        withConfig(true, "blog.example.com", plugin, config -> {
            ServerConfig serverConfig = config.getServerConfig();
            serverConfig.addInterceptor(StaticFetchInterceptor.class);
            ApplicationContext applicationContext = new ApplicationContext(serverConfig);
            applicationContext.init();

            plugin.doFetch(config, applicationContext);

            assertEquals(StaticSitePlugin.HandleState.HANDLED, plugin.handleStatusPageMap.get("/admin/index"));
            assertEquals(StaticSitePlugin.HandleState.HANDLED, plugin.handleStatusPageMap.get(plugin.notFindFile()));
            assertFalse(plugin.handleStatusPageMap.containsKey("/should-not-parse.html"));
            assertFalse(plugin.handleStatusPageMap.containsKey("/also-ignored.html"));
            assertTrue(plugin.getCacheFiles().stream().anyMatch(file -> file.getPath().endsWith("/error/404.html")));
            assertTrue(plugin.getCacheFiles().stream().filter(file -> file.getPath().endsWith("/error/404.html"))
                    .allMatch(File::isFile));
        });
    }

    @Test
    public void shouldExportNotFoundDocumentWithAndWithoutContextPath() throws Exception {
        for (String contextPath : new String[]{"", "/sub"}) {
            TestStaticSitePlugin plugin = new TestStaticSitePlugin() {
                @Override public String getContextPath() { return contextPath; }
            };
            plugin.handleStatusPageMap.put(plugin.notFindFile(), StaticSitePlugin.HandleState.NEW);
            StaticFetchInterceptor.pages.put("/error/404.html", "<html><body>Missing page"
                    + "<a href=\"/must-not-crawl.html\">ignored</a></body></html>");
            withConfig(true, "blog.example.com", plugin, config -> {
                ServerConfig serverConfig = config.getServerConfig();
                serverConfig.setContextPath(contextPath);
                serverConfig.addInterceptor(StaticFetchInterceptor.class);
                ApplicationContext applicationContext = new ApplicationContext(serverConfig);
                applicationContext.init();
                plugin.doFetch(config, applicationContext);
                File output = plugin.getCacheFile("/error/404.html");
                assertTrue(output.isFile());
                assertTrue(Files.readString(output.toPath()).contains("Missing page"));
                assertTrue(plugin.getCacheFiles().contains(output));
                assertEquals(StaticSitePlugin.HandleState.HANDLED, plugin.handleStatusPageMap.get(plugin.notFindFile()));
                assertFalse(plugin.handleStatusPageMap.containsKey("/must-not-crawl.html"));
            });
        }
    }

    @Test
    public void shouldMarkMissingGeneratedFileForRefetch() throws Exception {
        TestStaticSitePlugin plugin = new TestStaticSitePlugin();
        plugin.handleStatusPageMap.put("/missing.html", StaticSitePlugin.HandleState.NEW);

        withConfig(true, "blog.example.com", plugin, config -> {
            ServerConfig serverConfig = config.getServerConfig();
            serverConfig.addInterceptor(NoBodyInterceptor.class);
            ApplicationContext applicationContext = new ApplicationContext(serverConfig);
            applicationContext.init();

            plugin.doFetch(config, applicationContext);

            assertEquals(StaticSitePlugin.HandleState.RE_FETCH, plugin.handleStatusPageMap.get("/missing.html"));
            assertFalse(plugin.getCacheFile("/missing.html").exists());
        });
    }

    @Test
    public void shouldLoadLocalizedCacheFileAndFallbackToDefaultLanguage() throws Exception {
        TestStaticSitePlugin plugin = new TestStaticSitePlugin();
        withConfig(true, "blog.example.com", () -> {
            File defaultFile = PathUtil.getCacheFile("zh_CN/static/index.html");
            defaultFile.getParentFile().mkdirs();
            Files.writeString(defaultFile.toPath(), "default", StandardCharsets.UTF_8);

            File fallback = plugin.loadCacheFile(request("/", null, "en-US,en;q=0.9"));
            assertEquals(defaultFile.getCanonicalFile(), fallback.getCanonicalFile());

            File englishFile = PathUtil.getCacheFile("en_US/static/index.html");
            englishFile.getParentFile().mkdirs();
            Files.writeString(englishFile.toPath(), "english", StandardCharsets.UTF_8);

            File localized = plugin.loadCacheFile(request("/", null, "en-US,en;q=0.9"));
            assertEquals(englishFile.getCanonicalFile(), localized.getCanonicalFile());
        });
    }

    @Test
    public void shouldSaveCacheFilesOnlyWhenStaticHtmlIsEnabled() throws Exception {
        TestStaticSitePlugin plugin = new TestStaticSitePlugin();

        withConfig(false, "blog.example.com", () -> {
            plugin.saveToCacheFolder(input("disabled"), "/disabled.html");
            assertFalse(plugin.getCacheFile("/disabled.html").exists());
            assertTrue(plugin.cacheFiles.isEmpty());
        });

        withConfig(true, "blog.example.com", () -> {
            plugin.saveToCacheFolder(input("enabled"), "/enabled.html");
            assertEquals("enabled", Files.readString(plugin.getCacheFile("/enabled.html").toPath()));
            assertTrue(plugin.cacheFiles.contains(plugin.getCacheFile("/enabled.html")));
        });
    }

    @Test
    public void shouldSaveBase64FaviconToStaticAndCacheFolders() throws Exception {
        TestStaticSitePlugin plugin = new TestStaticSitePlugin();
        String icon = Base64.getEncoder().encodeToString("icon".getBytes(StandardCharsets.UTF_8));

        withConfig(true, "blog.example.com", () -> {
            plugin.faviconHandle("data:image/png;base64," + icon, "/favicon.ico", true);

            assertEquals("icon", Files.readString(PathUtil.getStaticFile("/favicon.ico").toPath()));
            assertEquals("icon", Files.readString(plugin.getCacheFile("/favicon.ico").toPath()));
            assertTrue(plugin.cacheFiles.contains(plugin.getCacheFile("/favicon.ico")));
        });
    }

    @Test
    public void shouldIgnoreInvalidFaviconBase64WithoutWritingFiles() throws Exception {
        TestStaticSitePlugin plugin = new TestStaticSitePlugin();

        withConfig(true, "blog.example.com", () -> {
            plugin.faviconHandle("not-base64", "/bad-favicon.ico", true);

            assertFalse(PathUtil.getStaticFile("/bad-favicon.ico").exists());
            assertFalse(plugin.getCacheFile("/bad-favicon.ico").exists());
        });
    }

    private void withConfig(boolean staticHtmlStatus, String host, ThrowingRunnable runnable) throws Exception {
        withConfig(staticHtmlStatus, host, null, config -> runnable.run());
    }

    private void withConfig(boolean staticHtmlStatus, String host, IPlugin plugin, ConfigThrowingRunnable runnable) throws Exception {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        String previousRootPath = System.getProperty("sws.root.path");
        try {
            System.setProperty("sws.root.path", temporaryFolder.newFolder().getAbsolutePath());
            TestZrLogConfig config = new TestZrLogConfig(staticHtmlStatus, host, plugin);
            Constants.zrLogConfig = config;
            runnable.run(config);
        } finally {
            Constants.zrLogConfig = previousConfig;
            restoreProperty("sws.root.path", previousRootPath);
            StaticFetchInterceptor.pages.clear();
        }
    }

    private static HttpRequest request(String uri, String userAgent, String acceptLanguage) {
        return (HttpRequest) Proxy.newProxyInstance(
                StaticSitePluginTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getUri".equals(method.getName())) {
                        return uri;
                    }
                    if ("getHeader".equals(method.getName())) {
                        if ("User-Agent".equals(args[0])) {
                            return userAgent;
                        }
                        if ("Accept-Language".equals(args[0])) {
                            return acceptLanguage;
                        }
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }

    private static java.io.InputStream input(String value) {
        return new java.io.ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
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

    private interface ConfigThrowingRunnable {
        void run(TestZrLogConfig config) throws Exception;
    }

    public static class StaticFetchInterceptor implements Interceptor {

        private static final Map<String, String> pages = new ConcurrentHashMap<>();

        @Override
        public boolean doInterceptor(HttpRequest request, HttpResponse response) {
            String html = pages.getOrDefault(request.getUri(), "<html><body></body></html>");
            if (request.getUri().endsWith("/error/404.html")) {
                response.setContentType("text/html;charset=UTF-8");
                response.write(input(html), 404);
            } else {
                response.renderHtmlStr(html);
            }
            return false;
        }
    }

    public static class NoBodyInterceptor implements Interceptor {

        @Override
        public boolean doInterceptor(HttpRequest request, HttpResponse response) {
            return false;
        }
    }

    private static class TestStaticSitePlugin implements StaticSitePlugin {

        private final Map<String, HandleState> handleStatusPageMap = new ConcurrentHashMap<>();
        private final Lock parseLock = new ReentrantLock();
        private final List<File> cacheFiles = new ArrayList<>();

        @Override
        public String getVersionFileName() {
            return "version.txt";
        }

        @Override
        public String getDbCacheKey() {
            return "test.static.version";
        }

        @Override
        public String getContextPath() {
            return "/static";
        }

        @Override
        public String getDefaultLang() {
            return "zh_CN";
        }

        @Override
        public Map<String, HandleState> getHandleStatusPageMap() {
            return handleStatusPageMap;
        }

        @Override
        public Lock getParseLock() {
            return parseLock;
        }

        @Override
        public Executor getExecutorService() {
            return Runnable::run;
        }

        @Override
        public List<File> getCacheFiles() {
            return cacheFiles;
        }

        @Override
        public StaticSiteType getType() {
            return StaticSiteType.BLOG;
        }

        @Override
        public boolean start() {
            return true;
        }

        @Override
        public boolean isStarted() {
            return true;
        }

        @Override
        public boolean stop() {
            return true;
        }
    }

    private static class TestZrLogConfig extends ZrLogConfig {

        private final PublicWebSiteInfo publicWebSiteInfo;
        private final IPlugin plugin;

        TestZrLogConfig(boolean staticHtmlStatus, String host) {
            this(staticHtmlStatus, host, null);
        }

        TestZrLogConfig(boolean staticHtmlStatus, String host, IPlugin plugin) {
            super(18080, null, "");
            this.plugin = plugin;
            this.publicWebSiteInfo = new PublicWebSiteInfo();
            this.publicWebSiteInfo.setGenerator_html_status(staticHtmlStatus);
            this.publicWebSiteInfo.setHost(host);
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
            return new TestCacheService(publicWebSiteInfo);
        }

        @Override
        protected TokenService initTokenService() {
            return null;
        }

        @Override
        public List<IPlugin> getBasePluginList() {
            return new Plugins();
        }

        @Override
        public <T extends IPlugin> T getPlugin(Class<T> pluginClass) {
            if (pluginClass.isInstance(plugin)) {
                return pluginClass.cast(plugin);
            }
            return super.getPlugin(pluginClass);
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
            return new BaseDataInitVO();
        }

        @Override
        public BaseDataInitVO refreshInitData() {
            return new BaseDataInitVO();
        }

        @Override
        public PublicWebSiteInfo getPublicWebSiteInfo() {
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

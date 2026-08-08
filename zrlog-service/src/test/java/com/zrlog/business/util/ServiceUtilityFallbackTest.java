package com.zrlog.business.util;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.DatabaseConnectPoolInfo;
import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.util.SystemType;
import com.hibegin.common.util.http.handle.CloseResponseHandle;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.business.rest.base.UpgradeWebSiteInfo;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.business.updater.AutoUpgradeVersionType;
import com.zrlog.common.CacheService;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.cache.dto.TagDTO;
import com.zrlog.common.cache.dto.TypeDTO;
import com.zrlog.common.cache.dto.UserBasicDTO;
import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import com.zrlog.util.BlogBuildInfoUtil;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class ServiceUtilityFallbackTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final Logger cmdLogger = Logger.getLogger(CmdUtil.class.getName());
    private Level previousLevel;
    private boolean previousUseParentHandlers;

    @Before
    public void setUp() {
        previousLevel = cmdLogger.getLevel();
        previousUseParentHandlers = cmdLogger.getUseParentHandlers();
        cmdLogger.setUseParentHandlers(false);
        cmdLogger.setLevel(Level.OFF);
    }

    @After
    public void tearDown() {
        cmdLogger.setLevel(previousLevel);
        cmdLogger.setUseParentHandlers(previousUseParentHandlers);
    }

    @Test
    public void shouldReturnEmptyOutputWhenCommandCannotStart() {
        InputStream[] streams = CmdUtil.getCmdInputStream("/path/to/missing-zrlog-command");

        assertNull(streams[0]);
        assertNull(streams[1]);
        assertEquals("zrlog", CmdUtil.sendCmd("printf", "zrlog"));
        assertNotNull(new PluginCoreUtils());
        assertNotNull(new CacheUtils());
        assertNotNull(new ControllerUtil());
    }

    @Test
    public void shouldParseLinuxPidFromNetstatOutput() {
        String output = "Active Internet connections (servers and established)\n"
                + "Proto Recv-Q Send-Q Local Address           Foreign Address         State       PID/Program name\n"
                + "tcp        0      0 0.0.0.0:8080            0.0.0.0:*               LISTEN      1234/java\n"
                + "tcp        0      0 127.0.0.1:not-a-port    0.0.0.0:*               LISTEN      4321/java\n";

        assertEquals(1234, CmdUtil.findPidByPort(output, 8080, SystemType.LINUX));
        assertEquals(-1, CmdUtil.findPidByPort(output, 9090, SystemType.LINUX));
    }

    @Test
    public void shouldIgnoreMissingOrNonLinuxPidFromNetstatOutput() {
        String windowsStyleOutput = "Active Connections\n"
                + "Proto  Local Address          Foreign Address        State\n"
                + "TCP    0.0.0.0:8080           0.0.0.0:0              LISTENING\n";
        String shortLinuxOutput = "Active Internet connections (servers and established)\n"
                + "Proto Recv-Q Send-Q Local Address Foreign Address State PID/Program name\n"
                + "tcp 0 0\n";
        String linuxWithoutPidOutput = "Active Internet connections (servers and established)\n"
                + "Proto Recv-Q Send-Q Local Address           Foreign Address         State       PID/Program name\n"
                + "tcp        0      0 0.0.0.0:8080            0.0.0.0:*               LISTEN      -\n";
        String linuxWithoutPortOutput = "Active Internet connections (servers and established)\n"
                + "Proto Recv-Q Send-Q Local Address           Foreign Address         State       PID/Program name\n"
                + "tcp        0      0 0.0.0.0                 0.0.0.0:*               LISTEN      1234/java\n";

        assertEquals(-1, CmdUtil.findPidByPort(null, 8080, SystemType.LINUX));
        assertEquals(-1, CmdUtil.findPidByPort("", 8080, SystemType.LINUX));
        assertEquals(-1, CmdUtil.findPidByPort(windowsStyleOutput, 8080, SystemType.WINDOWS));
        assertEquals(-1, CmdUtil.findPidByPort(shortLinuxOutput, 8080, SystemType.LINUX));
        assertEquals(-1, CmdUtil.findPidByPort(linuxWithoutPidOutput, 8080, SystemType.LINUX));
        assertEquals(-1, CmdUtil.findPidByPort(linuxWithoutPortOutput, 8080, SystemType.LINUX));
    }

    @Test
    public void shouldKillFoundPidByPortThroughInjectedHandlers() {
        AtomicInteger killedPid = new AtomicInteger(-1);

        CmdUtil.killProcByPort(8080, port -> {
            assertEquals(8080, port);
            return 1234;
        }, killedPid::set);

        assertEquals(1234, killedPid.get());
    }

    @Test
    public void shouldIgnoreMissingPidAndSwallowKillErrors() {
        AtomicInteger killedPid = new AtomicInteger(-1);

        CmdUtil.killProcByPort(8080, port -> -1, killedPid::set);
        CmdUtil.killProcByPort(8080, port -> {
            throw new IllegalStateException("find failed");
        }, killedPid::set);
        CmdUtil.killProcByPort(8080, port -> 1234, pid -> {
            throw new IllegalStateException("kill failed");
        });

        assertEquals(-1, killedPid.get());
        assertNotNull(new CmdUtil());
    }

    @Test
    public void shouldReturnExistingPluginCoreFileWithoutDownload() throws Exception {
        File pluginsFolder = Files.createTempDirectory("zrlog-plugin-core").toFile();
        File pluginCore = new File(pluginsFolder, "plugin-core.jar");
        Files.writeString(pluginCore.toPath(), "jar");

        File result = PluginCoreUtils.tryDownloadPluginCoreFile(pluginsFolder.getAbsolutePath());

        assertEquals(pluginCore.getCanonicalFile(), result.getCanonicalFile());
    }

    @Test
    public void shouldDownloadPluginCoreFileFromConfiguredResourceHost() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/plugin/core/plugin-core.jar", exchange -> {
            byte[] body = "downloaded-jar".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        String previousResourceDownloadUrl = setBlogBuildInfoString("resourceDownloadUrl",
                "http://127.0.0.1:" + server.getAddress().getPort());
        try {
            File pluginsFolder = Files.createTempDirectory("zrlog-plugin-core-download").toFile();

            File result = PluginCoreUtils.tryDownloadPluginCoreFile(pluginsFolder.getAbsolutePath());

            assertEquals("plugin-core.jar", result.getName());
            assertEquals("downloaded-jar", Files.readString(result.toPath()));
        } finally {
            setBlogBuildInfoString("resourceDownloadUrl", previousResourceDownloadUrl);
            server.stop(0);
        }
    }

    @Test
    public void shouldWrapPluginCoreDownloadFailure() throws Exception {
        int unusedPort;
        try (java.net.ServerSocket serverSocket = new java.net.ServerSocket(0)) {
            unusedPort = serverSocket.getLocalPort();
        }
        String previousResourceDownloadUrl = setBlogBuildInfoString("resourceDownloadUrl",
                "http://127.0.0.1:" + unusedPort);
        try {
            File pluginsFolder = Files.createTempDirectory("zrlog-plugin-core-download-fail").toFile();

            RuntimeException thrown = assertThrows(RuntimeException.class,
                    () -> PluginCoreUtils.tryDownloadPluginCoreFile(pluginsFolder.getAbsolutePath()));

            assertTrue(thrown.getMessage().contains("download plugin core error"));
        } finally {
            setBlogBuildInfoString("resourceDownloadUrl", previousResourceDownloadUrl);
        }
    }

    @Test
    public void shouldBuildDefaultPluginCoreFileName() throws Exception {
        File file = PluginCoreUtils.getPluginFileName("/plugins", false, "");

        assertEquals(new File("/plugins/plugin-core.jar"), file);
    }

    @Test
    public void shouldBuildNativePluginCoreFileNameByFileArch() throws Exception {
        assertEquals(new File("/plugins/plugin-core-Linux-x86_64.bin"),
                PluginCoreUtils.getPluginFileName("/plugins", true, "Linux-x86_64"));
        assertEquals(new File("/plugins/plugin-core-Windows-x86_64.exe"),
                PluginCoreUtils.getPluginFileName("/plugins", true, "Windows-x86_64"));
    }

    @Test
    public void shouldMarkExistingNativePluginCoreBinExecutableWithoutDownload() throws Exception {
        File pluginsFolder = Files.createTempDirectory("zrlog-plugin-core-native").toFile();
        File pluginCore = new File(pluginsFolder, "plugin-core-Linux-x86_64.bin");
        Files.writeString(pluginCore.toPath(), "bin");
        assertTrue(pluginCore.setExecutable(false, false));
        assertFalse(pluginCore.canExecute());

        File result = PluginCoreUtils.tryDownloadPluginCoreFile(pluginsFolder.getAbsolutePath(),
                true, "Linux-x86_64");

        assertEquals(pluginCore.getCanonicalFile(), result.getCanonicalFile());
        assertTrue(pluginCore.canExecute());
    }

    @Test
    public void shouldReturnFallbackValuesWhenWebsiteKvDaoIsUnavailable() {
        WebsiteKvService service = new WebsiteKvService();

        assertNull(service.getString("missing"));
        assertEquals(Collections.emptyMap(), service.getByNames(Collections.singletonList("missing")));
        assertEquals(Collections.emptyList(), service.listByPrefix("missing."));

        UpgradeWebSiteInfo upgrade = service.upgradeWebSiteInfo();
        assertEquals(Integer.valueOf(AutoUpgradeVersionType.ONE_DAY.getCycle()), upgrade.getAutoUpgradeVersion());
        assertFalse(upgrade.getUpgradePreview());
    }

    @Test
    public void shouldReturnFalseWhenWebsiteKvQuietWriteFails() throws Exception {
        DataSourceWrapper previousDataSource = setDefaultDataSource(null);
        try {
            setDefaultDataSource(dataSource(new FailingQueryRunner()));
            WebsiteKvService service = new WebsiteKvService();

            assertFalse(service.putStringQuietly("missing", "value"));
            assertFalse(service.removeQuietly("missing"));
        } finally {
            restoreDefaultDataSource(previousDataSource);
        }
    }

    @Test
    public void shouldExposeCacheUtilsFastFallbacks() throws Exception {
        assertFalse(CacheUtils.refreshStaticSiteCache(null, null));
        assertFalse(CacheUtils.refreshStaticSiteCache(null, Collections.emptyList()));
    }

    @Test
    public void shouldRefreshStaticSiteCacheWithPluginCoreNotification() throws Exception {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        DataSourceWrapper previousDataSource = setDefaultDataSource(null);
        FakeQueryRunner queryRunner = new FakeQueryRunner();
        FakePluginCorePlugin pluginCorePlugin = new FakePluginCorePlugin();
        FakeStaticSitePlugin staticSitePlugin = new FakeStaticSitePlugin();
        try {
            PathUtil.setRootPath(temporaryFolder.newFolder("zrlog-cache-utils").getAbsolutePath());
            setDefaultDataSource(dataSource(queryRunner));
            Constants.zrLogConfig = new CacheTestConfig(pluginCorePlugin, staticSitePlugin);

            boolean refreshed = CacheUtils.refreshStaticSiteCache(
                    request("https"), Collections.singletonList(StaticSiteType.BLOG));

            assertTrue(refreshed);
            assertEquals(1, staticSitePlugin.startCount);
            assertEquals(1, pluginCorePlugin.startCount);
            assertEquals(2, pluginCorePlugin.refreshCacheCount);
            assertEquals("static-version", pluginCorePlugin.lastCacheVersion);
            assertEquals("update website set value=? where name=?", queryRunner.sql);
            assertArrayEquals(new Object[]{"static-version", "test.static.version"}, queryRunner.params);
        } finally {
            Constants.zrLogConfig = previousConfig;
            restoreDefaultDataSource(previousDataSource);
        }
    }

    @Test
    public void shouldUpdateCacheSynchronouslyAndNotifyPluginCore() throws Exception {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        FakePluginCorePlugin pluginCorePlugin = new FakePluginCorePlugin();
        FakeCacheService cacheService = new FakeCacheService(77L);
        try {
            PathUtil.setRootPath(temporaryFolder.newFolder("zrlog-update-cache").getAbsolutePath());
            Constants.zrLogConfig = new CacheTestConfig(pluginCorePlugin, new FakeStaticSitePlugin(), cacheService);

            CacheUtils.updateCache(false, request("http"), Collections.singletonList(StaticSiteType.BLOG));

            assertEquals(1, cacheService.refreshInitDataCount);
            assertEquals(1, pluginCorePlugin.startCount);
            assertEquals(1, pluginCorePlugin.refreshCacheCount);
            assertEquals("77", pluginCorePlugin.lastCacheVersion);
        } finally {
            Constants.zrLogConfig = previousConfig;
        }
    }

    @Test
    public void shouldUpdateCacheSynchronouslyOrThrowAndNotifyPluginCore() throws Exception {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        FakePluginCorePlugin pluginCorePlugin = new FakePluginCorePlugin();
        FakeCacheService cacheService = new FakeCacheService(78L);
        try {
            PathUtil.setRootPath(temporaryFolder.newFolder("zrlog-update-cache-strict").getAbsolutePath());
            Constants.zrLogConfig = new CacheTestConfig(pluginCorePlugin, new FakeStaticSitePlugin(), cacheService);

            CacheUtils.updateCacheSynchronouslyOrThrow(
                    request("http"), Collections.singletonList(StaticSiteType.BLOG));

            assertEquals(1, cacheService.refreshInitDataCount);
            assertEquals(1, pluginCorePlugin.startCount);
            assertEquals(1, pluginCorePlugin.refreshCacheCount);
            assertEquals("78", pluginCorePlugin.lastCacheVersion);
        } finally {
            Constants.zrLogConfig = previousConfig;
        }
    }

    @Test
    public void shouldSwallowCacheRefreshFailureDuringUpdateCache() throws Exception {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        try {
            Constants.zrLogConfig = new CacheTestConfig(new FakePluginCorePlugin(), new FakeStaticSitePlugin(),
                    new ThrowingCacheService());

            CacheUtils.updateCache(false, request("http"), Collections.singletonList(StaticSiteType.BLOG));
        } finally {
            Constants.zrLogConfig = previousConfig;
        }
    }

    @Test
    public void shouldPropagateCacheRefreshFailureDuringSynchronousStrictUpdate() throws Exception {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        try {
            Constants.zrLogConfig = new CacheTestConfig(new FakePluginCorePlugin(), new FakeStaticSitePlugin(),
                    new ThrowingCacheService());

            IllegalStateException error = assertThrows(IllegalStateException.class,
                    () -> CacheUtils.updateCacheSynchronouslyOrThrow(
                            request("http"), Collections.singletonList(StaticSiteType.BLOG)));

            assertEquals("refresh failed", error.getMessage());
        } finally {
            Constants.zrLogConfig = previousConfig;
        }
    }

    @Test
    public void shouldContinueStaticSiteRefreshWhenVersionWriteFails() throws Exception {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        DataSourceWrapper previousDataSource = setDefaultDataSource(null);
        FakePluginCorePlugin pluginCorePlugin = new FakePluginCorePlugin();
        FakeStaticSitePlugin staticSitePlugin = new FakeStaticSitePlugin();
        try {
            setDefaultDataSource(dataSource(new FailingQueryRunner()));
            Constants.zrLogConfig = new CacheTestConfig(pluginCorePlugin, staticSitePlugin);

            boolean refreshed = CacheUtils.refreshStaticSiteCache(
                    request("https"), Collections.singletonList(StaticSiteType.BLOG));

            assertTrue(refreshed);
            assertEquals(1, staticSitePlugin.startCount);
            assertEquals(2, pluginCorePlugin.refreshCacheCount);
        } finally {
            Constants.zrLogConfig = previousConfig;
            restoreDefaultDataSource(previousDataSource);
        }
    }

    @Test
    public void shouldUpdateCacheAndRefreshStaticSiteWhenStaticHtmlIsEnabled() throws Exception {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        DataSourceWrapper previousDataSource = setDefaultDataSource(null);
        FakePluginCorePlugin pluginCorePlugin = new FakePluginCorePlugin();
        FakeStaticSitePlugin staticSitePlugin = new FakeStaticSitePlugin();
        FakeCacheService cacheService = new FakeCacheService(99L, true, "blog.example.com");
        try {
            setDefaultDataSource(dataSource(new FakeQueryRunner()));
            Constants.zrLogConfig = new CacheTestConfig(pluginCorePlugin, staticSitePlugin, cacheService);

            CacheUtils.updateCache(false, request("https"), Collections.singletonList(StaticSiteType.BLOG));

            assertEquals(1, cacheService.refreshInitDataCount);
            assertEquals(1, staticSitePlugin.startCount);
            assertEquals(3, pluginCorePlugin.refreshCacheCount);
            assertEquals("static-version", pluginCorePlugin.lastCacheVersion);
        } finally {
            Constants.zrLogConfig = previousConfig;
            restoreDefaultDataSource(previousDataSource);
        }
    }

    @Test
    public void shouldUpdateCacheAsynchronouslyAndNotifyPluginCore() throws Exception {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        FakePluginCorePlugin pluginCorePlugin = new FakePluginCorePlugin();
        FakeCacheService cacheService = new FakeCacheService(88L);
        try {
            Constants.zrLogConfig = new CacheTestConfig(pluginCorePlugin, new FakeStaticSitePlugin(), cacheService);

            CacheUtils.updateCache(true, request("http"), Collections.singletonList(StaticSiteType.BLOG));
            waitUntil(() -> pluginCorePlugin.refreshCacheCount == 1);

            assertEquals(1, cacheService.refreshInitDataCount);
            assertEquals(1, pluginCorePlugin.startCount);
            assertEquals("88", pluginCorePlugin.lastCacheVersion);
        } finally {
            Constants.zrLogConfig = previousConfig;
        }
    }

    private static void waitUntil(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(25);
        }
        throw new AssertionError("Timed out waiting for async cache update");
    }

    private static HttpRequest request(String scheme) {
        return (HttpRequest) Proxy.newProxyInstance(
                ServiceUtilityFallbackTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getScheme".equals(method.getName())) {
                        return scheme;
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }

    private static DataSourceWrapper setDefaultDataSource(DataSourceWrapper dataSource) {
        DataSourceWrapper previous = DAO.getDefaultDataSource();
        DAO.setDs(dataSource);
        return previous;
    }

    private static void restoreDefaultDataSource(DataSourceWrapper previousDataSource) {
        DAO.setDs(previousDataSource);
    }

    private static String setBlogBuildInfoString(String fieldName, String value) throws Exception {
        Field field = BlogBuildInfoUtil.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        String previous = (String) field.get(null);
        field.set(null, value);
        return previous;
    }

    private static DataSourceWrapper dataSource(QueryRunner queryRunner) {
        return (DataSourceWrapper) Proxy.newProxyInstance(
                ServiceUtilityFallbackTest.class.getClassLoader(),
                new Class[]{DataSourceWrapper.class},
                (proxy, method, args) -> {
                    if ("getQueryRunner".equals(method.getName())) {
                        return queryRunner;
                    }
                    if ("getDataSourceProperties".equals(method.getName())) {
                        return new Properties();
                    }
                    if ("getDatabaseConnectPoolInfo".equals(method.getName())) {
                        return new DatabaseConnectPoolInfo(0, 0);
                    }
                    if ("getDbInfo".equals(method.getName())) {
                        return "test-db";
                    }
                    if ("isWebApi".equals(method.getName()) || "isDev".equals(method.getName())) {
                        return false;
                    }
                    if ("toString".equals(method.getName())) {
                        return "DataSourceWrapperProxy";
                    }
                    Class<?> returnType = method.getReturnType();
                    if (returnType == boolean.class) {
                        return false;
                    }
                    if (returnType == int.class) {
                        return 0;
                    }
                    if (returnType == long.class) {
                        return 0L;
                    }
                    return null;
                });
    }

    private static class CacheTestConfig extends ZrLogConfig {

        private final FakePluginCorePlugin pluginCorePlugin;
        private final FakeStaticSitePlugin staticSitePlugin;
        private final CacheService cacheService;

        CacheTestConfig(FakePluginCorePlugin pluginCorePlugin, FakeStaticSitePlugin staticSitePlugin) {
            this(pluginCorePlugin, staticSitePlugin, null);
        }

        CacheTestConfig(FakePluginCorePlugin pluginCorePlugin, FakeStaticSitePlugin staticSitePlugin,
                        CacheService cacheService) {
            super(18080, null, "");
            this.pluginCorePlugin = pluginCorePlugin;
            this.staticSitePlugin = staticSitePlugin;
            this.cacheService = cacheService;
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

        @Override
        public CacheService getCacheService() {
            return cacheService;
        }

        @Override
        public <T extends IPlugin> T getPlugin(Class<T> pluginClass) {
            if (pluginClass.isInstance(pluginCorePlugin)) {
                return pluginClass.cast(pluginCorePlugin);
            }
            return null;
        }

        @Override
        public <T extends IPlugin> List<T> getPluginsByClazz(Class<T> pluginClass) {
            if (pluginClass.isInstance(staticSitePlugin)) {
                return Collections.singletonList(pluginClass.cast(staticSitePlugin));
            }
            return Collections.emptyList();
        }
    }

    private static class FakeQueryRunner extends QueryRunner {

        private String sql;
        private Object[] params = new Object[0];

        @Override
        public int update(String sql, Object... params) {
            this.sql = sql;
            this.params = Arrays.copyOf(params, params.length);
            return 1;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T query(String sql, ResultSetHandler<T> rsh, Object... params) {
            this.sql = sql;
            this.params = Arrays.copyOf(params, params.length);
            return (T) Integer.valueOf(1);
        }
    }

    private static class FailingQueryRunner extends QueryRunner {

        @Override
        public int update(String sql, Object... params) throws SQLException {
            throw new SQLException("write failed");
        }

        @Override
        public <T> T query(String sql, ResultSetHandler<T> rsh, Object... params) throws SQLException {
            throw new SQLException("read failed");
        }
    }

    private static class FakePluginCorePlugin implements PluginCorePlugin {

        private boolean started;
        private int startCount;
        private int refreshCacheCount;
        private String lastCacheVersion;

        @Override
        public boolean refreshCache(String cacheVersion, HttpRequest request) {
            refreshCacheCount++;
            lastCacheVersion = cacheVersion;
            return true;
        }

        @Override
        public boolean start() {
            startCount++;
            started = true;
            return true;
        }

        @Override
        public boolean isStarted() {
            return started;
        }

        @Override
        public boolean stop() {
            started = false;
            return true;
        }

        @Override
        public CloseResponseHandle getContext(String uri, HttpMethod method, HttpRequest request,
                                              AdminTokenVO adminTokenVO)
                throws IOException, URISyntaxException, InterruptedException {
            return null;
        }

        @Override
        public <T> T requestService(HttpRequest inputRequest, Map<String, String[]> params,
                                    AdminTokenVO adminTokenVO, Class<T> clazz)
                throws IOException, URISyntaxException, InterruptedException {
            return null;
        }

        @Override
        public boolean accessPlugin(String uri, HttpRequest request, HttpResponse response,
                                    AdminTokenVO adminTokenVO)
                throws IOException, URISyntaxException, InterruptedException {
            return false;
        }

        @Override
        public String getToken() {
            return "token";
        }
    }

    private static class FakeStaticSitePlugin implements StaticSitePlugin {

        private int startCount;
        private final Lock lock = new ReentrantLock();

        @Override
        public String getSiteVersion() {
            return "static-version";
        }

        @Override
        public boolean isSynchronized(String scheme) {
            return "https".equals(scheme);
        }

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
            return "";
        }

        @Override
        public String getDefaultLang() {
            return "zh_CN";
        }

        @Override
        public Map<String, HandleState> getHandleStatusPageMap() {
            return Collections.emptyMap();
        }

        @Override
        public Lock getParseLock() {
            return lock;
        }

        @Override
        public Executor getExecutorService() {
            return Runnable::run;
        }

        @Override
        public List<File> getCacheFiles() {
            return Collections.emptyList();
        }

        @Override
        public StaticSiteType getType() {
            return StaticSiteType.BLOG;
        }

        @Override
        public boolean start() {
            startCount++;
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

        @Override
        public File loadCacheFile(HttpRequest request) {
            return null;
        }
    }

    private static class FakeCacheService implements CacheService {

        private final Long version;
        private final boolean generatorHtmlStatus;
        private final String host;
        private int refreshInitDataCount;

        FakeCacheService(Long version) {
            this(version, false, "");
        }

        FakeCacheService(Long version, boolean generatorHtmlStatus, String host) {
            this.version = version;
            this.generatorHtmlStatus = generatorHtmlStatus;
            this.host = host;
        }

        @Override
        public long getCurrentSqlVersion() {
            return 0;
        }

        @Override
        public long getWebSiteVersion() {
            return version;
        }

        @Override
        public BaseDataInitVO getInitData() {
            return refreshInitData();
        }

        @Override
        public BaseDataInitVO refreshInitData() {
            refreshInitDataCount++;
            BaseDataInitVO initVO = new BaseDataInitVO();
            initVO.setVersion(version);
            return initVO;
        }

        @Override
        public PublicWebSiteInfo getPublicWebSiteInfo() {
            PublicWebSiteInfo publicWebSiteInfo = new PublicWebSiteInfo();
            publicWebSiteInfo.setGenerator_html_status(generatorHtmlStatus);
            publicWebSiteInfo.setHost(host);
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

    private static class ThrowingCacheService extends FakeCacheService {

        ThrowingCacheService() {
            super(0L);
        }

        @Override
        public BaseDataInitVO refreshInitData() {
            throw new IllegalStateException("refresh failed");
        }
    }
}

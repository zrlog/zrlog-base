package com.zrlog.business.plugin;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.util.UrlEncodeUtils;
import com.hibegin.common.util.http.handle.CloseResponseHandle;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.config.ServerConfig;
import com.zrlog.business.rest.response.PluginCoreStatus;
import com.zrlog.business.rest.response.PluginStatusResponse;
import com.zrlog.common.CacheService;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import org.junit.Test;
import sun.misc.Unsafe;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PluginCorePluginImplTest {

    @Test
    public void shouldExposeLifecycleWithoutStartingPluginCore() {
        PluginCorePluginImpl plugin = new PluginCorePluginImpl(new File("db.properties"), "/blog");

        assertTrue(plugin.autoStart());
        assertFalse(plugin.isStarted());
        assertEquals(32, plugin.getToken().length());
        assertTrue(plugin.stop());
        assertFalse(plugin.isStarted());
    }

    @Test
    public void shouldBuildForwardHeadersFromRequestAndAdminToken() throws Exception {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        try {
            Constants.zrLogConfig = testConfig();
            AdminTokenVO adminToken = new AdminTokenVO();
            adminToken.setUserId(5);
            adminToken.setProtocol("https");
            HttpRequest request = request(Map.of(
                    "Cookie", "sid=1",
                    "Content-Type", "application/json",
                    "Referer", "https://example.com/admin",
                    "User-Agent", "JUnit",
                    "Authorization", "Bearer token",
                    "X-forwarded-for", "203.0.113.7, 10.0.0.1"
            ));

            Map<String, String> headers = headerMap(request, adminToken);

            assertEquals("5", headers.get("LoginUserId"));
            assertEquals("true", headers.get("IsLogin"));
            assertEquals("true", headers.get("Dark-Mode"));
            assertEquals("#1677ff", headers.get("Admin-Color-Primary"));
            assertEquals("sid=1", headers.get("Cookie"));
            assertEquals("http://127.0.0.1:19080", headers.get("AccessUrl"));
            assertEquals("application/json", headers.get("Content-Type"));
            assertEquals("https://example.com/admin", headers.get("Referer"));
            assertEquals("JUnit", headers.get("User-Agent"));
            assertEquals("Bearer token", headers.get("Authorization"));
            assertEquals("203.0.113.7", headers.get("X-Real-IP"));
            assertEquals(UrlEncodeUtils.encodeUrl("https://example.com/admin/plugin"), headers.get("Full-Url"));
        } finally {
            Constants.zrLogConfig = previousConfig;
        }
    }

    @Test
    public void shouldBuildBaseForwardHeadersWithoutRequestOrLogin() throws Exception {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        try {
            Constants.zrLogConfig = testConfig();

            Map<String, String> headers = headerMap(null, null);

            assertEquals("false", headers.get("IsLogin"));
            assertEquals("true", headers.get("Dark-Mode"));
            assertEquals("#1677ff", headers.get("Admin-Color-Primary"));
            assertFalse(headers.containsKey("Full-Url"));
            assertFalse(headers.containsKey("Cookie"));
        } finally {
            Constants.zrLogConfig = previousConfig;
        }
    }

    @Test
    public void shouldForwardGetContextToStartedPluginCoreServer() throws Exception {
        AtomicReference<String> queryRef = new AtomicReference<>();
        AtomicReference<String> loginHeaderRef = new AtomicReference<>();
        HttpServer server = localServer();
        server.createContext("/api/test", exchange -> {
            queryRef.set(exchange.getRequestURI().getQuery());
            loginHeaderRef.set(exchange.getRequestHeaders().getFirst("IsLogin"));
            byte[] body = "plugin-ok".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("X-Plugin", "ok");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            ZrLogConfig previousConfig = Constants.zrLogConfig;
            try {
                Constants.zrLogConfig = testConfig();
                PluginCorePluginImpl plugin = startedPlugin(server);

                CloseResponseHandle handle = plugin.getContext("/api/test", HttpMethod.GET,
                        request(Map.of("User-Agent", "JUnit")), null);

                assertEquals(200, handle.getStatusCode());
                assertEquals("plugin-ok", readBody(handle.getT().body()));
                assertEquals("q=1", queryRef.get());
                assertEquals("false", loginHeaderRef.get());
            } finally {
                Constants.zrLogConfig = previousConfig;
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void shouldForwardPostContextWhenRequestMethodDiffersFromTargetMethod() throws Exception {
        AtomicReference<String> methodRef = new AtomicReference<>();
        AtomicReference<String> bodyRef = new AtomicReference<>();
        HttpServer server = localServer();
        server.createContext("/api/post", exchange -> {
            methodRef.set(exchange.getRequestMethod());
            bodyRef.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "posted".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            ZrLogConfig previousConfig = Constants.zrLogConfig;
            try {
                Constants.zrLogConfig = testConfig();
                PluginCorePluginImpl plugin = startedPlugin(server);

                CloseResponseHandle handle = plugin.getContext("/api/post", HttpMethod.POST,
                        request(Collections.emptyMap()), null);

                assertEquals("POST", methodRef.get());
                assertEquals("body", bodyRef.get());
                assertEquals("posted", readBody(handle.getT().body()));
            } finally {
                Constants.zrLogConfig = previousConfig;
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void shouldWritePluginResponseHeadersAndBodyToHostResponse() throws Exception {
        HttpServer server = localServer();
        server.createContext("/redirect", exchange -> {
            byte[] body = "redirect-body".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Location", "/login");
            exchange.getResponseHeaders().add("X-Plugin", "redirect");
            exchange.sendResponseHeaders(302, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            ZrLogConfig previousConfig = Constants.zrLogConfig;
            try {
                Constants.zrLogConfig = testConfig();
                PluginCorePluginImpl plugin = startedPlugin(server);
                CapturedHostResponse hostResponse = new CapturedHostResponse();

                assertTrue(plugin.accessPlugin("/redirect", request(Collections.emptyMap()),
                        hostResponse.proxy(), null));

                assertEquals(302, hostResponse.statusCode.get());
                assertEquals("redirect-body", hostResponse.body());
                assertEquals("/blog/login", hostResponse.header("location"));
                assertEquals("redirect", hostResponse.header("x-plugin"));
            } finally {
                Constants.zrLogConfig = previousConfig;
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void shouldReturnFalseWhenPluginResponseBodyIsMissing() throws Exception {
        PluginCorePluginImpl plugin = new PluginCorePluginImpl(new File("db.properties"), "/blog") {
            @Override
            public CloseResponseHandle getContext(String uri, HttpMethod method, HttpRequest request,
                                                  AdminTokenVO adminTokenVO) {
                return new CloseResponseHandle();
            }
        };
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        try {
            Constants.zrLogConfig = testConfig();
            Field serverBaseUrl = PluginCorePluginImpl.class.getDeclaredField("pluginServerBaseUrl");
            serverBaseUrl.setAccessible(true);
            serverBaseUrl.set(plugin, "http://127.0.0.1:1");

            assertFalse(plugin.accessPlugin("/empty", request(Collections.emptyMap()),
                    new CapturedHostResponse().proxy(), null));
        } finally {
            Constants.zrLogConfig = previousConfig;
        }
    }

    @Test
    public void shouldPostRequestServiceToStartedPluginCoreServer() throws Exception {
        AtomicReference<String> methodRef = new AtomicReference<>();
        AtomicReference<String> bodyRef = new AtomicReference<>();
        AtomicReference<String> loginUserRef = new AtomicReference<>();
        HttpServer server = localServer();
        server.createContext("/service", exchange -> {
            methodRef.set(exchange.getRequestMethod());
            bodyRef.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            loginUserRef.set(exchange.getRequestHeaders().getFirst("LoginUserId"));
            byte[] body = "{\"code\":200,\"status\":\"STARTED\",\"runningPlugins\":[\"comment\"]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            ZrLogConfig previousConfig = Constants.zrLogConfig;
            try {
                Constants.zrLogConfig = testConfig();
                PluginCorePluginImpl plugin = startedPlugin(server);
                AdminTokenVO adminToken = new AdminTokenVO();
                adminToken.setUserId(7);

                PluginStatusResponse response = plugin.requestService(request(Collections.emptyMap()),
                        Map.of("action", new String[]{"ping"}), adminToken, PluginStatusResponse.class);

                assertEquals("POST", methodRef.get());
                assertEquals("action=ping", bodyRef.get());
                assertEquals("7", loginUserRef.get());
                assertEquals(PluginCoreStatus.STARTED, response.getStatus());
                assertEquals("comment", response.getRunningPlugins().get(0));
            } finally {
                Constants.zrLogConfig = previousConfig;
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void shouldStartPluginCoreProcessAndWaitForStartedStatus() throws Exception {
        AtomicInteger startCount = new AtomicInteger();
        HttpServer server = localServer();
        server.createContext("/api/status", exchange -> {
            byte[] body = "{\"status\":\"STARTED\",\"runningPlugins\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            ZrLogConfig previousConfig = Constants.zrLogConfig;
            try {
                Constants.zrLogConfig = testConfig();
                PluginCorePluginImpl plugin = new PluginCorePluginImpl(new File("db.properties"), "/blog");
                setPluginCoreProcess(plugin, new PluginCoreProcess() {
                    @Override
                    public int pluginServerStart(String dbProperties, String pluginJvmArgs, String runtimePath,
                                                 String runTimeVersion, String token) {
                        startCount.incrementAndGet();
                        return server.getAddress().getPort();
                    }

                    @Override
                    public void stopPluginCore() {
                    }
                });

                assertTrue(plugin.start());
                assertTrue(plugin.start());

                assertTrue(plugin.isStarted());
                assertEquals(1, startCount.get());
            } finally {
                Constants.zrLogConfig = previousConfig;
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void shouldKeepPendingSupervisorAndReuseItAfterReadinessTimeout() throws Exception {
        AtomicInteger startCount = new AtomicInteger();
        AtomicInteger stopCount = new AtomicInteger();
        AtomicInteger readinessCount = new AtomicInteger();
        PluginCorePluginImpl plugin = new PluginCorePluginImpl(new File("db.properties"), "/blog") {
            @Override
            boolean waitToStarted(String pluginServerBaseUrl, String token) {
                assertEquals("http://127.0.0.1:21000", pluginServerBaseUrl);
                return readinessCount.incrementAndGet() > 1;
            }
        };
        setPluginCoreProcess(plugin, new PluginCoreProcess() {
            @Override
            public int pluginServerStart(String dbProperties, String pluginJvmArgs, String runtimePath,
                                         String runTimeVersion, String token) {
                startCount.incrementAndGet();
                return 21000;
            }

            @Override
            public void stopPluginCore() {
                stopCount.incrementAndGet();
            }
        });

        assertFalse(plugin.start());
        assertFalse(plugin.isStarted());
        assertEquals(1, startCount.get());
        assertEquals(0, stopCount.get());

        assertTrue(plugin.start());
        assertTrue(plugin.isStarted());
        assertEquals(1, startCount.get());
        assertEquals(0, stopCount.get());
    }

    @Test
    public void shouldLaunchOnlyOneSupervisorForConcurrentStarts() throws Exception {
        AtomicInteger startCount = new AtomicInteger();
        PluginCorePluginImpl plugin = new PluginCorePluginImpl(new File("db.properties"), "/blog") {
            @Override
            boolean waitToStarted(String pluginServerBaseUrl, String token) {
                return true;
            }
        };
        setPluginCoreProcess(plugin, pluginCoreProcess(startCount, new AtomicInteger(), 21000));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startGate = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> {
                startGate.await();
                return plugin.start();
            });
            Future<Boolean> second = executor.submit(() -> {
                startGate.await();
                return plugin.start();
            });

            startGate.countDown();

            assertTrue(first.get(5, TimeUnit.SECONDS));
            assertTrue(second.get(5, TimeUnit.SECONDS));
            assertEquals(1, startCount.get());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    public void shouldRejectForwardingWhilePendingWithoutBuildingNullUrl() throws Exception {
        AtomicInteger startCount = new AtomicInteger();
        PluginCorePluginImpl plugin = new PluginCorePluginImpl(new File("db.properties"), "/blog") {
            @Override
            boolean waitToStarted(String pluginServerBaseUrl, String token) {
                return false;
            }
        };
        setPluginCoreProcess(plugin, pluginCoreProcess(startCount, new AtomicInteger(), 21000));

        try {
            plugin.getContext("/api/test", HttpMethod.GET, request(Collections.emptyMap()), null);
            throw new AssertionError("Expected plugin-core readiness failure");
        } catch (java.io.IOException e) {
            assertEquals("plugin-core is not ready", e.getMessage());
        }
        try {
            plugin.requestService(request(Collections.emptyMap()), Collections.emptyMap(), null,
                    PluginStatusResponse.class);
            throw new AssertionError("Expected plugin-core readiness failure");
        } catch (java.io.IOException e) {
            assertEquals("plugin-core is not ready", e.getMessage());
        }
        assertFalse(plugin.accessPlugin("/test", request(Collections.emptyMap()),
                new CapturedHostResponse().proxy(), null));
        assertFalse(plugin.refreshCache("cache-123", request(Collections.emptyMap())));
        assertEquals(1, startCount.get());
    }

    @Test
    public void shouldClearPendingSupervisorOnExplicitStop() throws Exception {
        AtomicInteger startCount = new AtomicInteger();
        AtomicInteger stopCount = new AtomicInteger();
        PluginCorePluginImpl plugin = new PluginCorePluginImpl(new File("db.properties"), "/blog") {
            @Override
            boolean waitToStarted(String pluginServerBaseUrl, String token) {
                return false;
            }
        };
        setPluginCoreProcess(plugin, pluginCoreProcess(startCount, stopCount, 21000));

        assertFalse(plugin.start());
        assertEquals(1, startCount.get());
        assertTrue(plugin.stop());
        assertEquals(1, stopCount.get());

        assertFalse(plugin.start());
        assertEquals(2, startCount.get());
    }

    @Test
    public void shouldRefreshPluginCacheThroughStartedPluginCoreServer() throws Exception {
        AtomicReference<String> queryRef = new AtomicReference<>();
        HttpServer server = localServer();
        server.createContext("/api/refreshCache", exchange -> {
            queryRef.set(exchange.getRequestURI().getQuery());
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            ZrLogConfig previousConfig = Constants.zrLogConfig;
            try {
                Constants.zrLogConfig = testConfig();
                PluginCorePluginImpl plugin = startedPlugin(server);

                assertTrue(plugin.refreshCache("cache-123", request(Collections.emptyMap())));

                assertNotNull(queryRef.get());
                assertTrue(queryRef.get().contains("cacheVersion=cache-123"));
                assertTrue(queryRef.get().contains("token=" + plugin.getToken()));
            } finally {
                Constants.zrLogConfig = previousConfig;
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void shouldRetryRefreshCacheUntilRetriesAreExhausted() throws Exception {
        AtomicInteger pauseCount = new AtomicInteger();
        PluginCorePluginImpl plugin = new PluginCorePluginImpl(new File("db.properties"), "/blog") {
            @Override
            void pauseBeforeRefreshCacheRetry() {
                pauseCount.incrementAndGet();
            }
        };
        setPluginServerBaseUrl(plugin, "http://127.0.0.1:1");

        invokeRefreshCacheWithRetry(plugin, 0, "cache-123");

        assertEquals(1, pauseCount.get());
    }

    @Test
    public void shouldWaitToStartedFailWhenStatusPayloadIsMissing() throws Exception {
        HttpServer server = localServer();
        server.createContext("/api/status", exchange -> {
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            assertFalse(invokeWaitToStarted(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "token", 0));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void shouldWaitToStartedRetryUntilStartedStatus() throws Exception {
        AtomicInteger count = new AtomicInteger();
        HttpServer server = localServer();
        server.createContext("/api/status", exchange -> {
            PluginCoreStatus status = count.getAndIncrement() == 0 ? PluginCoreStatus.STARTING : PluginCoreStatus.STARTED;
            byte[] body = ("{\"status\":\"" + status + "\",\"runningPlugins\":[]}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            assertTrue(invokeWaitToStarted(
                    "http://127.0.0.1:" + server.getAddress().getPort(), "token", 1));

            assertEquals(2, count.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void shouldWaitToStartedReturnImmediatelyAfterRetriesExhausted() throws Exception {
        assertFalse(invokeWaitToStarted("http://127.0.0.1:1", "token", -1));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> headerMap(HttpRequest request, AdminTokenVO adminTokenVO) throws Exception {
        Method method = PluginCorePluginImpl.class.getDeclaredMethod("genHeaderMapByRequest",
                HttpRequest.class, AdminTokenVO.class);
        method.setAccessible(true);
        return (Map<String, String>) method.invoke(null, request, adminTokenVO);
    }

    private static HttpRequest request(Map<String, String> headers) {
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.setPort(19080);
        return (HttpRequest) Proxy.newProxyInstance(
                PluginCorePluginImplTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getHeader":
                            return headers.get((String) args[0]);
                        case "getHeaderMap":
                            return headers;
                        case "getServerConfig":
                            return serverConfig;
                        case "getFullUrl":
                            return "http://example.com/admin/plugin";
                        case "getRemoteHost":
                            return "198.51.100.8";
                        case "getQueryStr":
                            return "q=1";
                        case "getMethod":
                            return HttpMethod.GET;
                        case "getInputStream":
                            return new ByteArrayInputStream("body".getBytes(StandardCharsets.UTF_8));
                        case "getContextPath":
                            return "/blog";
                        case "toString":
                            return "HttpRequestProxy";
                        default:
                            return null;
                    }
                });
    }

    private static HttpServer localServer() throws Exception {
        return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    private static PluginCorePluginImpl startedPlugin(HttpServer server) throws Exception {
        PluginCorePluginImpl plugin = new PluginCorePluginImpl(new File("db.properties"), "/blog");
        setPluginServerBaseUrl(plugin, "http://127.0.0.1:" + server.getAddress().getPort());
        return plugin;
    }

    private static void setPluginServerBaseUrl(PluginCorePluginImpl plugin, String value) throws Exception {
        Field serverBaseUrl = PluginCorePluginImpl.class.getDeclaredField("pluginServerBaseUrl");
        serverBaseUrl.setAccessible(true);
        serverBaseUrl.set(plugin, value);
    }

    private static void setPluginCoreProcess(PluginCorePluginImpl plugin, PluginCoreProcess process) throws Exception {
        Field field = PluginCorePluginImpl.class.getDeclaredField("pluginCoreProcess");
        field.setAccessible(true);
        field.set(plugin, process);
    }

    private static PluginCoreProcess pluginCoreProcess(AtomicInteger startCount, AtomicInteger stopCount, int port) {
        return new PluginCoreProcess() {
            @Override
            public int pluginServerStart(String dbProperties, String pluginJvmArgs, String runtimePath,
                                         String runTimeVersion, String token) {
                startCount.incrementAndGet();
                return port;
            }

            @Override
            public void stopPluginCore() {
                stopCount.incrementAndGet();
            }
        };
    }

    private static boolean invokeWaitToStarted(String serverBaseUrl, String token, int retryCount) throws Exception {
        Method method = PluginCorePluginImpl.class.getDeclaredMethod("waitToStarted", String.class, String.class,
                int.class);
        method.setAccessible(true);
        return (boolean) method.invoke(null, serverBaseUrl, token, retryCount);
    }

    private static void invokeRefreshCacheWithRetry(PluginCorePluginImpl plugin, int retryCount,
                                                    String cacheVersion) throws Exception {
        Method method = PluginCorePluginImpl.class.getDeclaredMethod("refreshCacheWithRetry", int.class,
                String.class);
        method.setAccessible(true);
        method.invoke(plugin, retryCount, cacheVersion);
    }

    private static String readBody(InputStream inputStream) throws Exception {
        try (InputStream body = inputStream) {
            return new String(body.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static ZrLogConfig testConfig() throws Exception {
        TestZrLogConfig config = allocate(TestZrLogConfig.class);
        Field cacheServiceField = TestZrLogConfig.class.getDeclaredField("cacheService");
        cacheServiceField.setAccessible(true);
        cacheServiceField.set(config, cacheService());
        return config;
    }

    @SuppressWarnings("unchecked")
    private static <T> T allocate(Class<T> type) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (T) ((Unsafe) field.get(null)).allocateInstance(type);
    }

    private static CacheService cacheService() {
        PublicWebSiteInfo info = new PublicWebSiteInfo();
        info.setAdmin_darkMode(true);
        info.setAdmin_color_primary("#1677ff");
        return (CacheService) Proxy.newProxyInstance(
                PluginCorePluginImplTest.class.getClassLoader(),
                new Class[]{CacheService.class},
                (proxy, method, args) -> {
                    if ("getPublicWebSiteInfo".equals(method.getName())) {
                        return info;
                    }
                    if ("getCurrentSqlVersion".equals(method.getName()) || "getWebSiteVersion".equals(method.getName())) {
                        return 0L;
                    }
                    if ("toString".equals(method.getName())) {
                        return "CacheServiceProxy";
                    }
                    return null;
                });
    }

    private static class TestZrLogConfig extends ZrLogConfig {

        private CacheService cacheService;

        private TestZrLogConfig() {
            super(0, null, "");
        }

        @Override
        public CacheService getCacheService() {
            return cacheService;
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
        public <T extends IPlugin> List<T> getPluginsByClazz(Class<T> pluginClass) {
            return Collections.emptyList();
        }
    }

    private static class CapturedHostResponse {

        private final Map<String, String> headers = new LinkedHashMap<>();
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private final AtomicInteger statusCode = new AtomicInteger();

        HttpResponse proxy() {
            return (HttpResponse) Proxy.newProxyInstance(
                    PluginCorePluginImplTest.class.getClassLoader(),
                    new Class[]{HttpResponse.class},
                    (proxy, method, args) -> {
                        if ("addHeader".equals(method.getName())) {
                            headers.put(((String) args[0]).toLowerCase(), (String) args[1]);
                            return null;
                        }
                        if ("write".equals(method.getName()) && args.length == 2 && args[0] instanceof InputStream) {
                            statusCode.set((Integer) args[1]);
                            body.write(((InputStream) args[0]).readAllBytes());
                            return null;
                        }
                        if ("getHeader".equals(method.getName())) {
                            return headers;
                        }
                        if ("toString".equals(method.getName())) {
                            return "CapturedHostResponse";
                        }
                        return null;
                    });
        }

        String header(String name) {
            return headers.get(name);
        }

        String body() {
            return new String(body.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}

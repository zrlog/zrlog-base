package com.zrlog.admin.web.token;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.web.cookie.Cookie;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.vo.AdminFullTokenVO;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AdminTokenServiceTest {

    //TODO: Cover the DAO-backed secret lookup path with an isolated DataSource fixture.
    // The current unit tests intentionally avoid bootstrapping the shared DAO/global config stack.
    private final ZrLogConfig previousConfig = Constants.zrLogConfig;

    private AdminTokenService testService(long timeout) {
        return new AdminTokenService(timeout) {
            @Override protected com.zrlog.data.security.AccountAccess loadAccount(int userId) {
                return com.zrlog.data.security.AccountAccess.from(Map.of("userId", userId, "role", "owner", "enabled", true, "authVersion", 0));
            }
        };
    }

    @After
    public void tearDown() {
        Constants.zrLogConfig = previousConfig;
        AdminTokenThreadLocal.remove();
    }

    @Test
    public void shouldSetCookieAndParseTokenFromHeader() throws Exception {
        Constants.zrLogConfig = new TestZrLogConfig(true);
        AdminTokenService service = testService(30);
        CapturedResponse capturedResponse = new CapturedResponse();
        HttpRequest request = request("/admin", new HashMap<>(), null, new HashMap<>());

        service.setAdminToken(7, "secret-key", "session-id", "http", request, capturedResponse.response());
        Cookie cookie = capturedResponse.cookies.get(0);
        cacheSecretKey(service, 7, "secret-key");

        Map<String, String> headers = new HashMap<>();
        headers.put(AdminTokenService.ADMIN_TOKEN_KEY_IN_REQUEST_HEADER, cookie.getValue());
        AdminFullTokenVO token = service.getAdminTokenVO(request("/admin", headers, null, new HashMap<>()));

        assertNotNull(token);
        assertEquals(7, token.getUserId());
        assertEquals("session-id", token.getSessionId());
        assertEquals("http", token.getProtocol());
        assertEquals("secret-key", token.getSecretKey());
        assertEquals("admin-token", cookie.getName());
        assertEquals("/admin/", cookie.getPath());
        assertTrue(cookie.isHttpOnly());
        assertFalse(cookie.isSecure());
        assertEquals(7, AdminTokenThreadLocal.getUserId());
    }

    @Test
    public void shouldParseTokenFromCookieWhenHeaderIsMissing() throws Exception {
        Constants.zrLogConfig = new TestZrLogConfig(true);
        AdminTokenService service = testService(30);
        Cookie cookie = createAdminCookie(service, "secret-key");
        cacheSecretKey(service, 7, "secret-key");

        AdminFullTokenVO token = service.getAdminTokenVO(
                request("/admin", new HashMap<>(), new Cookie[]{cookie}, new HashMap<>()));

        assertNotNull(token);
        assertEquals(7, token.getUserId());
        assertEquals("session-id", token.getSessionId());
    }

    @Test
    public void shouldFallbackToCookieWhenHeaderTokenIsInvalid() throws Exception {
        Constants.zrLogConfig = new TestZrLogConfig(true);
        AdminTokenService service = testService(30);
        Cookie cookie = createAdminCookie(service, "secret-key");
        cacheSecretKey(service, 7, "secret-key");
        Map<String, String> headers = new HashMap<>();
        headers.put(AdminTokenService.ADMIN_TOKEN_KEY_IN_REQUEST_HEADER, "bad-token");

        AdminFullTokenVO token = service.getAdminTokenVO(
                request("/admin", headers, new Cookie[]{cookie}, new HashMap<>()));

        assertNotNull(token);
        assertEquals(7, token.getUserId());
    }

    @Test
    public void shouldRejectMalformedOrExpiredTokens() throws Exception {
        Constants.zrLogConfig = new TestZrLogConfig(true);
        AdminTokenService service = testService(30);
        Cookie cookie = createAdminCookie(service, "secret-key");
        cacheSecretKey(service, 7, "wrong-secret");

        assertNull(service.getAdminTokenVO(request("/admin", header("bad-token"), null, new HashMap<>())));
        assertNull(service.getAdminTokenVO(request("/admin", header("abc#1234"), null, new HashMap<>())));
        assertNull(service.getAdminTokenVO(request("/admin", header(cookie.getValue()), null, new HashMap<>())));
        assertFalse(secretCache(service).containsKey(7));

        cacheSecretKey(service, 7, "secret-key");
        service.updateSessionTimeout(-1);
        assertNull(service.getAdminTokenVO(request("/admin", header(cookie.getValue()), null, new HashMap<>())));
    }

    @Test
    public void shouldReturnNullWhenCachedTokenPayloadCannotBeDecoded() throws Exception {
        Constants.zrLogConfig = new TestZrLogConfig(true);
        AdminTokenService service = testService(30);
        cacheSecretKey(service, 7, "secret-key");

        assertNull(service.getAdminTokenVO(request("/admin", header("7#"), null, new HashMap<>())));
    }

    @Test
    public void shouldReturnNullWhenCookiesAreMissingOrNotAdminToken() throws Exception {
        Constants.zrLogConfig = new TestZrLogConfig(true);
        AdminTokenService service = testService(30);
        Cookie cookie = new Cookie();
        cookie.setName("other-token");
        cookie.setValue("token");

        assertNull(service.getAdminTokenVO(request("/admin", new HashMap<>(), null, new HashMap<>())));
        assertNull(service.getAdminTokenVO(request("/admin", new HashMap<>(), new Cookie[]{cookie}, new HashMap<>())));
    }

    @Test
    public void shouldMarkHttpsCookieSecureAndSameSiteForCrossOriginRequest() throws Exception {
        Constants.zrLogConfig = new TestZrLogConfig(true);
        AdminTokenService service = testService(30);
        CapturedResponse capturedResponse = new CapturedResponse();
        Map<String, String> headers = new HashMap<>();
        headers.put("Origin", "https://admin.example.com");

        service.setAdminToken(7, "secret-key", "session-id", "https",
                request("/admin", headers, null, new HashMap<>()), capturedResponse.response());

        Cookie cookie = capturedResponse.cookies.get(0);
        assertTrue(cookie.isSecure());
        assertEquals("None", cookie.getSameSite());
    }

    @Test
    public void shouldReturnNullWhenSiteIsNotInstalled() {
        Constants.zrLogConfig = new TestZrLogConfig(false);

        AdminFullTokenVO token = testService(30)
                .getAdminTokenVO(request("/", new HashMap<>(), null, new HashMap<>()));

        assertNull(token);
    }

    @Test
    public void shouldRemoveAdminTokenCookieAndRedirectToLoginWhenRefererMissing() {
        CapturedResponse capturedResponse = new CapturedResponse();
        Cookie cookie = new Cookie();
        cookie.setName("admin-token");
        cookie.setValue("token");

        testService(30).removeAdminToken(
                request("/", new HashMap<>(), new Cookie[]{cookie}, new HashMap<>()),
                capturedResponse.response());

        assertEquals("", capturedResponse.cookies.get(0).getValue());
        assertEquals("/", capturedResponse.cookies.get(0).getPath());
        assertTrue(capturedResponse.cookies.get(0).isHttpOnly());
        assertEquals(TokenService.ADMIN_LOGIN_URI_PATH, capturedResponse.redirects.get(0));
    }

    @Test
    public void shouldRedirectToLoginWhenRefererIsNotCrossOriginEnabled() {
        Constants.zrLogConfig = new TestZrLogConfig(true);
        CapturedResponse capturedResponse = new CapturedResponse();
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://example.com/admin/page");

        testService(30).removeAdminToken(
                request("/admin", headers, new Cookie[0], new HashMap<>()),
                capturedResponse.response());

        assertEquals(TokenService.ADMIN_LOGIN_URI_PATH, capturedResponse.redirects.get(0));
    }

    @Test
    public void shouldRedirectToEncodedLoginUrlForCrossOriginReferer() {
        Constants.zrLogConfig = new TestZrLogConfig(true);
        CapturedResponse capturedResponse = new CapturedResponse();
        Map<String, String> headers = new HashMap<>();
        headers.put("Referer", "https://example.com/admin/page");
        headers.put("Origin", "https://example.com");
        Map<String, String[]> params = new HashMap<>();
        params.put("sp", new String[]{"true"});

        testService(30).removeAdminToken(
                request("/admin", headers, new Cookie[0], params),
                capturedResponse.response());

        assertEquals("https://example.com/admin/admin/login.html", capturedResponse.redirects.get(0));
    }

    private static Cookie createAdminCookie(AdminTokenService service, String secretKey) throws Exception {
        CapturedResponse capturedResponse = new CapturedResponse();
        service.setAdminToken(7, secretKey, "session-id", "http",
                request("/admin", new HashMap<>(), null, new HashMap<>()),
                capturedResponse.response());
        AdminTokenThreadLocal.remove();
        return capturedResponse.cookies.get(0);
    }

    private static Map<String, String> header(String token) {
        Map<String, String> headers = new HashMap<>();
        headers.put(AdminTokenService.ADMIN_TOKEN_KEY_IN_REQUEST_HEADER, token);
        return headers;
    }

    private static void cacheSecretKey(AdminTokenService service, int userId, String secretKey) throws Exception {
        secretCache(service).put(userId, secretKey);
    }

    private static Map<Integer, String> secretCache(AdminTokenService service) throws Exception {
        Field field = AdminTokenService.class.getDeclaredField("userSecretKeyCacheMap");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Integer, String> cache = (Map<Integer, String>) field.get(service);
        return cache;
    }

    private static HttpRequest request(String contextPath, Map<String, String> headers,
                                       Cookie[] cookies, Map<String, String[]> params) {
        return (HttpRequest) Proxy.newProxyInstance(
                AdminTokenServiceTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if ("getContextPath".equals(name)) {
                        return contextPath;
                    }
                    if ("getHeader".equals(name)) {
                        return headers.get((String) args[0]);
                    }
                    if ("getHeaderMap".equals(name)) {
                        return headers;
                    }
                    if ("getCookies".equals(name)) {
                        return cookies;
                    }
                    if ("getParaToStr".equals(name)) {
                        String[] values = params.get((String) args[0]);
                        return values == null || values.length == 0 ? null : values[0];
                    }
                    if ("getParamMap".equals(name)) {
                        return params;
                    }
                    if ("getScheme".equals(name)) {
                        return "http";
                    }
                    if ("toString".equals(name)) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }

    private static class CapturedResponse {
        private final List<Cookie> cookies = new ArrayList<>();
        private final List<String> redirects = new ArrayList<>();

        HttpResponse response() {
            return (HttpResponse) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class[]{HttpResponse.class},
                    (proxy, method, args) -> {
                        if ("addCookie".equals(method.getName())) {
                            cookies.add((Cookie) args[0]);
                            return null;
                        }
                        if ("redirect".equals(method.getName())) {
                            redirects.add((String) args[0]);
                            return null;
                        }
                        if ("getHeader".equals(method.getName())) {
                            return new HashMap<String, String>();
                        }
                        if ("toString".equals(method.getName())) {
                            return "HttpResponseProxy";
                        }
                        return null;
                    });
        }
    }

    private static class TestZrLogConfig extends ZrLogConfig {

        private final boolean installed;

        TestZrLogConfig(boolean installed) {
            super(18080, null, "/");
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
}

package com.zrlog.web.inteceptor;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.execption.NotFindResourceException;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.vo.AdminFullTokenVO;
import com.zrlog.plugin.BaseStaticSitePlugin;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import com.zrlog.util.I18nUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WebInterceptorContractsTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldRenderDefault404ForPageRequests() {
        ResponseRecorder recorder = new ResponseRecorder();

        boolean continued = new DefaultInterceptor().doInterceptor(request("/missing"), recorder.response());

        assertTrue(continued);
        assertEquals(404, recorder.renderCode);
    }

    @Test(expected = NotFindResourceException.class)
    public void shouldThrowNotFoundForApiRequests() {
        new DefaultInterceptor().doInterceptor(request("/api/missing"), new ResponseRecorder().response());
    }

    @Test
    public void shouldMatchInstallStatusRoutesAndRedirectWhenNeeded() throws Exception {
        InstallStatusInterceptor interceptor = new InstallStatusInterceptor();
        assertFalse(interceptor.isHandleAble(request("/api/public/version")));
        assertFalse(interceptor.isHandleAble(request("/favicon.ico")));
        assertFalse(interceptor.isHandleAble(request("/install")));
        assertFalse(interceptor.isHandleAble(request("/install/index.html")));
        assertTrue(interceptor.isHandleAble(request("/admin/index")));

        withConfig(false, false, () -> {
            ResponseRecorder recorder = new ResponseRecorder();
            assertFalse(interceptor.doInterceptor(request("/admin/index"), recorder.response()));
            assertEquals("/install?ref=%2Fadmin%2Findex", recorder.redirect);
        });

        withConfig(true, true, () -> {
            ResponseRecorder recorder = new ResponseRecorder();
            assertFalse(interceptor.doInterceptor(request("/admin/index"), recorder.response()));
            assertEquals("/install?ref=%2Fadmin%2Findex&missingConfig=true", recorder.redirect);
        });

        withConfig(true, false, () -> assertTrue(
                interceptor.doInterceptor(request("/admin/index"), new ResponseRecorder().response())));
    }

    @Test
    public void shouldKeepTheOriginalPathInsideTheInstallRefParameter() throws Exception {
        String path = "/文章/50% &next=1#内容";
        for (boolean missingConfig : new boolean[]{false, true}) {
            withConfig(missingConfig, missingConfig, () -> {
                ResponseRecorder recorder = new ResponseRecorder();
                assertFalse(new InstallStatusInterceptor().doInterceptor(request(path), recorder.response()));
                URI target = URI.create(recorder.redirect);
                assertEquals("/install", target.getPath());
                assertNull(target.getRawFragment());
                String[] parameters = target.getRawQuery().split("&");
                assertEquals(missingConfig ? 2 : 1, parameters.length);
                assertEquals(path, URLDecoder.decode(parameters[0].substring("ref=".length()), StandardCharsets.UTF_8));
                if (missingConfig) assertEquals("missingConfig=true", parameters[1]);
            });
        }
    }

    @Test
    public void shouldBlockForbiddenStaticTemplateAndAdminHtmlRequests() throws Exception {
        withConfig(true, false, () -> {
            GlobalBaseInterceptor interceptor = new GlobalBaseInterceptor();
            ResponseRecorder template = new ResponseRecorder();
            assertFalse(interceptor.doInterceptor(request("/include/templates/default/index.ftl"), template.response()));
            assertEquals(403, template.renderCode);
            assertTrue(template.headers.containsKey("X-ZrLog"));

            ResponseRecorder adminHtml = new ResponseRecorder();
            assertFalse(interceptor.doInterceptor(request("/admin/index.html"), adminHtml.response()));
            assertEquals(403, adminHtml.renderCode);

            ResponseRecorder staticPluginAdminHtml = new ResponseRecorder();
            assertTrue(interceptor.doInterceptor(
                    request("/admin/index.html", BaseStaticSitePlugin.STATIC_USER_AGENT),
                    staticPluginAdminHtml.response()));
            assertEquals(0, staticPluginAdminHtml.renderCode);
        });
    }

    @Test
    public void shouldAddJsonContentTypeForApiRequests() throws Exception {
        withConfig(true, false, () -> {
            ResponseRecorder recorder = new ResponseRecorder();

            assertTrue(new GlobalBaseInterceptor().doInterceptor(request("/api/public/version"), recorder.response()));

            assertEquals("application/json", recorder.headers.get("Content-Type"));
            assertTrue(recorder.headers.containsKey("X-ZrLog"));
        });
    }

    @Test
    public void shouldSkipI18nForStaticAssetsAndSetLocaleForPages() {
        MyI18nInterceptor interceptor = new MyI18nInterceptor();
        assertFalse(interceptor.isHandleAble(request("/assets/app.css")));
        assertFalse(interceptor.isHandleAble(request("/assets/app.js")));
        assertFalse(interceptor.isHandleAble(request("/assets/logo.png")));
        assertTrue(interceptor.isHandleAble(request("/article")));
        assertTrue(interceptor.isHandleAble(request("/api/public/version")));

        try {
            assertTrue(interceptor.doInterceptor(request("/article", null, "en-US,en;q=0.9"),
                    new ResponseRecorder().response()));
            assertEquals("en_US", I18nUtil.threadLocal.get().getLocale());
        } finally {
            I18nUtil.threadLocal.remove();
        }
    }

    private void withConfig(boolean installed, boolean missingConfig, ThrowingRunnable runnable) throws Exception {
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        String previousRootPath = System.getProperty("sws.root.path");
        try {
            System.setProperty("sws.root.path", temporaryFolder.newFolder().getAbsolutePath());
            Constants.zrLogConfig = new TestZrLogConfig(installed, missingConfig);
            runnable.run();
        } finally {
            Constants.zrLogConfig = previousConfig;
            restoreProperty("sws.root.path", previousRootPath);
        }
    }

    private static HttpRequest request(String uri) {
        return request(uri, null, null);
    }

    private static HttpRequest request(String uri, String userAgent) {
        return request(uri, userAgent, null);
    }

    private static HttpRequest request(String uri, String userAgent, String acceptLanguage) {
        return (HttpRequest) Proxy.newProxyInstance(
                WebInterceptorContractsTest.class.getClassLoader(),
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
        private int renderCode;
        private String redirect;

        private HttpResponse response() {
            return (HttpResponse) Proxy.newProxyInstance(
                    WebInterceptorContractsTest.class.getClassLoader(),
                    new Class[]{HttpResponse.class},
                    (proxy, method, args) -> {
                        if ("renderCode".equals(method.getName())) {
                            renderCode = (Integer) args[0];
                        } else if ("redirect".equals(method.getName())) {
                            redirect = args[0].toString();
                        } else if ("addHeader".equals(method.getName())) {
                            headers.put(args[0].toString(), args[1].toString());
                        }
                        return null;
                    });
        }
    }

    private static class TestZrLogConfig extends ZrLogConfig {

        private final boolean installed;
        private final boolean missingConfig;

        TestZrLogConfig(boolean installed, boolean missingConfig) {
            super(18080, null, "");
            this.installed = installed;
            this.missingConfig = missingConfig;
        }

        @Override
        public boolean isInstalled() {
            return installed;
        }

        @Override
        public boolean isMissingConfig() {
            return missingConfig;
        }

        @Override
        public DataSourceWrapper configDatabase() {
            return null;
        }

        @Override
        protected TokenService initTokenService() {
            return new TokenService() {
                @Override
                public void updateSessionTimeout(long sessionTimeoutInMinutes) {
                }

                @Override
                public AdminFullTokenVO getAdminTokenVO(HttpRequest request) {
                    return null;
                }

                @Override
                public void removeAdminToken(HttpRequest request, HttpResponse response) {
                }

                @Override
                public void setAdminToken(Integer userId, String secretKey, String sessionId, String protocol,
                                          HttpRequest request, HttpResponse response) {
                }
            };
        }

        @Override
        public List<IPlugin> getBasePluginList() {
            return new Plugins();
        }
    }
}

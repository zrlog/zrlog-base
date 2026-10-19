package com.zrlog.web.inteceptor;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.execption.NotFindResourceException;
import com.zrlog.common.Constants;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.plugin.BaseStaticSitePlugin;
import com.zrlog.util.I18nUtil;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WebInterceptorContractsTest {

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
        java.util.concurrent.atomic.AtomicBoolean installed = new java.util.concurrent.atomic.AtomicBoolean();
        java.util.concurrent.atomic.AtomicBoolean missingConfig = new java.util.concurrent.atomic.AtomicBoolean();
        InstallStatusInterceptor interceptor = new InstallStatusInterceptor(installed::get, missingConfig::get);
        assertFalse(interceptor.isHandleAble(request("/api/public/version")));
        assertFalse(interceptor.isHandleAble(request("/favicon.ico")));
        assertFalse(interceptor.isHandleAble(request("/install")));
        assertFalse(interceptor.isHandleAble(request("/install/index.html")));
        assertTrue(interceptor.isHandleAble(request("/admin/index")));

        ZrLogConfig previous = Constants.zrLogConfig;
        try {
            Constants.zrLogConfig = null;
            ResponseRecorder recorder = new ResponseRecorder();
            assertFalse(interceptor.doInterceptor(request("/admin/index"), recorder.response()));
            assertEquals("/install?ref=%2Fadmin%2Findex", recorder.redirect);
            installed.set(true);
            missingConfig.set(true);
            assertFalse(interceptor.doInterceptor(request("/admin/index"), recorder.response()));
            assertEquals("/install?ref=%2Fadmin%2Findex&missingConfig=true", recorder.redirect);
            missingConfig.set(false);
            assertTrue(interceptor.doInterceptor(request("/admin/index"), new ResponseRecorder().response()));
        } finally {
            Constants.zrLogConfig = previous;
        }
    }

    @Test
    public void shouldKeepTheOriginalPathInsideTheInstallRefParameter() throws Exception {
        String path = "/文章/50% &next=1#内容";
        for (boolean missingConfig : new boolean[]{false, true}) {
            ResponseRecorder recorder = new ResponseRecorder();
            assertFalse(new InstallStatusInterceptor(() -> missingConfig, () -> missingConfig).doInterceptor(request(path), recorder.response()));
            URI target = URI.create(recorder.redirect);
            assertEquals("/install", target.getPath());
            assertNull(target.getRawFragment());
            String[] parameters = target.getRawQuery().split("&");
            assertEquals(missingConfig ? 2 : 1, parameters.length);
            assertEquals(path, URLDecoder.decode(parameters[0].substring("ref=".length()), StandardCharsets.UTF_8));
            if (missingConfig) assertEquals("missingConfig=true", parameters[1]);
        }
    }

    @Test
    public void shouldBlockForbiddenStaticTemplateAndAdminHtmlRequests() throws Exception {
        GlobalBaseInterceptor interceptor = new GlobalBaseInterceptor(false);
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
    }

    @Test
    public void shouldBlockJspOnlyForTheWarRuntime() {
        ResponseRecorder war = new ResponseRecorder();
        assertFalse(new GlobalBaseInterceptor(true).doInterceptor(request("/page.jsp"), war.response()));
        assertEquals(403, war.renderCode);
        assertTrue(new GlobalBaseInterceptor(false).doInterceptor(request("/page.jsp"), new ResponseRecorder().response()));
    }

    @Test
    public void shouldAddJsonContentTypeForApiRequests() throws Exception {
        ResponseRecorder recorder = new ResponseRecorder();

        assertTrue(new GlobalBaseInterceptor(false).doInterceptor(request("/api/public/version"), recorder.response()));

        assertEquals("application/json", recorder.headers.get("Content-Type"));
        assertTrue(recorder.headers.containsKey("X-ZrLog"));
    }

    @Test
    public void shouldSkipI18nForStaticAssetsAndSetLocaleForPages() {
        MyI18nInterceptor interceptor = new MyI18nInterceptor(() -> "zh_CN");
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

    @Test
    public void adminLanguageFollowsItsSupplierWhilePublicPagesKeepBrowserPreference() {
        var previousConfig = Constants.zrLogConfig;
        var previousLanguage = I18nUtil.threadLocal.get();
        var siteLanguage = new java.util.concurrent.atomic.AtomicReference<>("en_US");
        MyI18nInterceptor first = new MyI18nInterceptor(siteLanguage::get);
        MyI18nInterceptor second = new MyI18nInterceptor(() -> "zh_CN");
        try {
            Constants.zrLogConfig = null;
            first.doInterceptor(request("/api/admin/user", null, "zh-CN"), new ResponseRecorder().response());
            assertEquals("en_US", I18nUtil.getCurrentLocale());
            second.doInterceptor(request("/api/public/adminResource"), new ResponseRecorder().response());
            assertEquals("zh_CN", I18nUtil.getCurrentLocale());
            first.doInterceptor(request("/api/admin/user"), new ResponseRecorder().response());
            assertEquals("en_US", I18nUtil.getCurrentLocale());
            siteLanguage.set("zh_CN");
            first.doInterceptor(request("/api/admin/user"), new ResponseRecorder().response());
            assertEquals("zh_CN", I18nUtil.getCurrentLocale());
            first.doInterceptor(request("/article", null, "en-US"), new ResponseRecorder().response());
            assertEquals("en_US", I18nUtil.getCurrentLocale());
            I18nUtil.removeI18n();
            assertEquals("en_US", I18nUtil.getCurrentLocale("en_US"));
            assertEquals(Constants.DEFAULT_LANGUAGE, I18nUtil.getCurrentLocale());
        } finally {
            Constants.zrLogConfig = previousConfig;
            if (previousLanguage == null) I18nUtil.removeI18n();
            else I18nUtil.threadLocal.set(previousLanguage);
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

}

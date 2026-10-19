package com.zrlog.util;

import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.plugin.BaseStaticSitePlugin;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

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
            assertFalse(CrossUtils.isEnableOrigin(request(null, null), false));
            assertTrue(CrossUtils.isEnableOrigin(request(null, "https://client.example"), false));
        });

        withConfig(true, () -> assertTrue(CrossUtils.isEnableOrigin(request(null, null), true)));
    }

    @Test
    public void shouldWriteCrossOriginHeadersWhenOriginIsEnabled() throws Exception {
        withConfig(false, () -> {
            ResponseRecorder recorder = new ResponseRecorder();

            CrossUtils.cross(request(null, "https://client.example"), recorder.response(), false);

            assertEquals("https://client.example", recorder.headers.get("Access-Control-Allow-Origin"));
            assertEquals("true", recorder.headers.get("Access-Control-Allow-Credentials"));
        });
    }

    private void withConfig(boolean generatorHtmlStatus, ThrowingRunnable runnable) throws Exception {
        String previousRootPath = System.getProperty("sws.root.path");
        try {
            System.setProperty("sws.root.path", temporaryFolder.newFolder().getAbsolutePath());
            runnable.run();
        } finally {
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

}

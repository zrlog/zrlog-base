package com.zrlog.blog.web.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.hibegin.http.server.api.HttpRequest;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class WebToolsTest {

    @Test
    public void shouldPutRealIpHeaderFromForwardedFor() {
        Map<String, String> output = new HashMap<>();
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Forwarded-For", "203.0.113.10, 10.0.0.1");

        WebTools.putRealIpHeader(output, request(headers, "127.0.0.1"));

        assertEquals("203.0.113.10", output.get(WebTools.REAL_IP_HEADER));
    }

    @Test
    public void shouldFallbackToRemoteHostWhenProxyHeadersMissing() {
        Map<String, String> output = new HashMap<>();

        WebTools.putRealIpHeader(output, request(new HashMap<>(), "198.51.100.20"));

        assertEquals("198.51.100.20", output.get(WebTools.REAL_IP_HEADER));
    }

    @Test
    public void shouldFallbackThroughProxyHeadersAndIgnoreUnknownValues() {
        Map<String, String> output = new HashMap<>();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Forwarded-For", "unknown");
        headers.put("x-real-ip", "");
        headers.put("Proxy-Client-IP", "unknown");
        headers.put("WL-Proxy-Client-IP", "203.0.113.25");

        WebTools.putRealIpHeader(output, request(headers, "127.0.0.1"));

        assertEquals("203.0.113.25", output.get(WebTools.REAL_IP_HEADER));
    }

    @Test
    public void shouldSkipRealIpHeaderWhenInputsOrRealIpAreMissing() {
        Map<String, String> output = new HashMap<>();

        WebTools.putRealIpHeader(null, request(new HashMap<>(), "198.51.100.20"));
        WebTools.putRealIpHeader(output, null);
        WebTools.putRealIpHeader(output, request(new HashMap<>(), "unknown"));

        assertFalse(output.containsKey(WebTools.REAL_IP_HEADER));
    }

    @Test
    public void shouldBuildHomeUrlFromContextPath() {
        assertEquals("/", WebTools.getHomeUrl(request(new HashMap<>(), "127.0.0.1", "/")));
        assertEquals("/blog/", WebTools.getHomeUrl(request(new HashMap<>(), "127.0.0.1", "/blog")));
    }

    @Test
    public void shouldBuildEncodedUrl() {
        assertEquals("/a%20b", WebTools.buildEncodedUrl(null, "/a b"));
        assertEquals(
                "/blog/post%201",
                WebTools.buildEncodedUrl(
                        request(new HashMap<>(), "127.0.0.1", "/blog"), "/post 1"));
        assertEquals(
                "/blog/post%202",
                WebTools.buildEncodedUrl(request(new HashMap<>(), "127.0.0.1", "/blog"), "post 2"));
    }

    @Test
    public void shouldHtmlEncodeSpecialCharacters() {
        assertEquals("", WebTools.htmlEncode(null));
        assertEquals(
                "&lt;div title=&quot;a&amp;b&quot;&gt;",
                WebTools.htmlEncode("<div title=\"a&b\">"));
    }

    private HttpRequest request(Map<String, String> headers, String remoteHost) {
        return request(headers, remoteHost, "/");
    }

    private HttpRequest request(
            Map<String, String> headers, String remoteHost, String contextPath) {
        return (HttpRequest)
                Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class[] {HttpRequest.class},
                        (proxy, method, args) -> {
                            if ("getHeader".equals(method.getName())) {
                                return headers.get((String) args[0]);
                            }
                            if ("getHeaderMap".equals(method.getName())) {
                                return headers;
                            }
                            if ("getRemoteHost".equals(method.getName())) {
                                return remoteHost;
                            }
                            if ("getContextPath".equals(method.getName())) {
                                return contextPath;
                            }
                            if ("toString".equals(method.getName())) {
                                return "HttpRequestProxy";
                            }
                            return null;
                        });
    }
}

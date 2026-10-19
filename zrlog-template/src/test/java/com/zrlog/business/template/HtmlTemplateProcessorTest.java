package com.zrlog.business.template;

import com.hibegin.common.util.http.handle.CloseResponseHandle;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.common.Constants;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.vo.AdminTokenVO;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class HtmlTemplateProcessorTest {
    @Test
    public void asyncPluginRenderingPreservesEachApplicationsPluginRequestAndIdentity() {
        ZrLogConfig previous = Constants.zrLogConfig;
        try {
            Constants.zrLogConfig = null;
            HttpRequest request = (HttpRequest) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{HttpRequest.class}, (proxy, method, args) -> {
                        if (method.getName().equals("getContextPath")) return "/blog";
                        if (method.getName().equals("getCreateTime")) return System.currentTimeMillis();
                        return null;
                    });
            AdminTokenVO user = new AdminTokenVO();
            user.setUserId(7);
            Thread requestThread = Thread.currentThread();
            String template = "<main><plugin name=\"comment\" view=\"/index\" param=\"post=1\"></plugin></main>";
            for (String application : new String[]{"Host A", "Host B"}) {
                PluginCorePlugin plugin = (PluginCorePlugin) Proxy.newProxyInstance(getClass().getClassLoader(),
                        new Class[]{PluginCorePlugin.class}, (proxy, method, args) -> {
                            assertEquals("getContext", method.getName());
                            assertEquals("/comment/index?post=1", args[0]);
                            assertEquals(HttpMethod.GET, args[1]);
                            assertSame(request, args[2]);
                            assertSame(user, args[3]);
                            assertNotSame(requestThread, Thread.currentThread());
                            CloseResponseHandle handle = new CloseResponseHandle();
                            handle.handle(null, (java.net.http.HttpResponse<java.io.InputStream>) Proxy.newProxyInstance(
                                    getClass().getClassLoader(), new Class[]{java.net.http.HttpResponse.class},
                                    (response, responseMethod, responseArgs) -> {
                                        if (responseMethod.getName().equals("statusCode")) return 200;
                                        if (responseMethod.getName().equals("body")) return new ByteArrayInputStream(
                                                ("<section>" + application + "</section>").getBytes(StandardCharsets.UTF_8));
                                        return null;
                                    }));
                            return handle;
                        });
                String html = new HtmlTemplateProcessor(request, user, "/", plugin).transform(template);
                assertTrue(html.contains("<section>" + application + "</section>"));
                assertFalse(html.contains("<plugin"));
                assertFalse(html.contains(application.equals("Host A") ? "Host B" : "Host A"));
            }
        } finally {
            Constants.zrLogConfig = previous;
        }
    }
}

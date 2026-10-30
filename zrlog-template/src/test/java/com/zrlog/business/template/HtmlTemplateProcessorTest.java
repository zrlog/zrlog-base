package com.zrlog.business.template;

import com.hibegin.common.util.http.handle.CloseResponseHandle;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.business.plugin.StaticGenerationBudget;
import com.zrlog.common.vo.AdminTokenVO;
import com.zrlog.util.ExecutionDeadline;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class HtmlTemplateProcessorTest {
    @Test
    public void boundedViewsShareTheCallingThreadBudgetAndExecutionIdentity() {
        StaticGenerationBudget budget = new StaticGenerationBudget(ExecutionDeadline.afterMillis(30000), () -> {}, "run-1", 7);
        HttpRequest request = boundedRequest(budget);
        Thread owner = Thread.currentThread();
        java.util.List<String> calls = new java.util.ArrayList<>();
        PluginCorePlugin plugin = (PluginCorePlugin) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class[]{PluginCorePlugin.class}, (proxy, method, args) -> {
                    assertEquals("renderViewWithinBudget", method.getName());
                    assertSame(owner, Thread.currentThread()); assertSame(request, args[1]); assertSame(budget, args[2]);
                    calls.add((String) args[0]); return "<aside>插件内容</aside>";
                });
        String html = new HtmlTemplateProcessor(request, null, "/", plugin).transform(
                "<plugin name=\"comment\" view=\"/index\" param=\"post=1&amp;tag=a\"></plugin>"
                        + "<plugin name=\"links\" view=\"/list\"></plugin>");
        assertEquals(java.util.List.of("/comment/index?post=1&tag=a", "/links/list"), calls);
        assertTrue(html.contains("<aside>插件内容</aside>")); assertFalse(html.contains("<plugin"));
    }

    @Test
    public void revokedOrFailedViewStopsLaterTagsAndDoesNotProduceACachePage() {
        for (boolean fail : new boolean[]{false, true}) {
            java.util.concurrent.atomic.AtomicBoolean authorized = new java.util.concurrent.atomic.AtomicBoolean(true);
            java.util.concurrent.atomic.AtomicInteger calls = new java.util.concurrent.atomic.AtomicInteger();
            StaticGenerationBudget budget = new StaticGenerationBudget(ExecutionDeadline.afterMillis(30000), () -> {
                if (!authorized.get()) throw new IllegalStateException("revoked");
            }, "run-1", 7);
            PluginCorePlugin plugin = (PluginCorePlugin) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class[]{PluginCorePlugin.class}, (proxy, method, args) -> {
                        calls.incrementAndGet();
                        if (fail) throw new java.io.IOException("unconfirmed");
                        authorized.set(false); return "partial";
                    });
            HtmlTemplateProcessor processor = new HtmlTemplateProcessor(boundedRequest(budget), null, "/", plugin);
            assertThrows(IllegalStateException.class, () -> processor.transform(
                    "<plugin name=\"first\"></plugin><plugin name=\"second\"></plugin>"));
            assertEquals(1, calls.get());
        }
    }

    private HttpRequest boundedRequest(StaticGenerationBudget budget) {
        return (HttpRequest) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getAttr")) return java.util.Map.of(StaticGenerationBudget.REQUEST_ATTRIBUTE, budget);
                    if (method.getName().equals("getContextPath")) return "/blog";
                    if (method.getName().equals("getCreateTime")) return System.currentTimeMillis();
                    return null;
                });
    }

    @Test
    public void asyncPluginRenderingPreservesEachApplicationsPluginRequestAndIdentity() {
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
    }
}

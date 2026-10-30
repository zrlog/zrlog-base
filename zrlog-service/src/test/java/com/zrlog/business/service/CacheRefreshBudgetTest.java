package com.zrlog.business.service;

import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.common.CacheService;
import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.common.vo.PublicWebSiteInfo;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class CacheRefreshBudgetTest {
    @Test
    public void revokedFenceStopsBeforeAnyCacheSideEffect() {
        AtomicInteger refreshed = new AtomicInteger();
        var service = new CacheRefreshService(cache(refreshed), () -> null, List::of, () -> {throw new AssertionError();});
        assertThrows(IllegalStateException.class, () -> service.updateCacheWithinBudget(null, List.of(), "0000000001.00000001793297580247.94783943-99bf-4773-89bc-5883a479898b", 1, () -> 1000L,
                () -> {throw new IllegalStateException("revoked");}));
        assertEquals(0, refreshed.get());
    }

    @Test
    public void pluginFailureIsReportedAndNeverUsesTheLegacyRetryPath() {
        AtomicInteger refreshed = new AtomicInteger();
        AtomicInteger pluginCalls = new AtomicInteger();
        PluginCorePlugin plugin = (PluginCorePlugin) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{PluginCorePlugin.class}, (proxy, method, args) -> {
            if (method.getName().equals("isStarted")) return true;
            if (method.getName().equals("refreshCacheWithinBudget")) {pluginCalls.incrementAndGet(); return false;}
            throw new AssertionError("Unexpected plugin method: " + method.getName());
        });
        var service = new CacheRefreshService(cache(refreshed), () -> plugin, List::of, () -> {throw new AssertionError();});
        assertThrows(IllegalStateException.class, () -> service.updateCacheWithinBudget(null, List.of(), "0000000001.00000001793297580247.94783943-99bf-4773-89bc-5883a479898b", 1, () -> 1000L, () -> {}));
        assertEquals(1, refreshed.get());
        assertEquals(1, pluginCalls.get());
    }

    private CacheService cache(AtomicInteger refreshed) {
        return cache(refreshed, false);
    }

    @Test
    public void startupFailureDoesNotFallBackToUnboundedStartOrRefresh() {
        AtomicInteger starts = new AtomicInteger();
        PluginCorePlugin plugin = (PluginCorePlugin) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{PluginCorePlugin.class}, (proxy, method, args) -> {
            if (method.getName().equals("isStarted")) return false;
            if (method.getName().equals("startWithinBudget")) {
                assertTrue((long) args[0] > 0 && (long) args[0] <= 5000);
                starts.incrementAndGet();
                return false;
            }
            throw new AssertionError("Unexpected plugin method: " + method.getName());
        });
        var service = new CacheRefreshService(cache(new AtomicInteger()), () -> plugin, List::of, () -> {throw new AssertionError();});
        assertThrows(IllegalStateException.class, () -> service.updateCacheWithinBudget(null, List.of(), "0000000001.00000001793297580247.94783943-99bf-4773-89bc-5883a479898b", 1, () -> 5000L, () -> {}));
        assertEquals(1, starts.get());
    }

    @Test
    public void generationFailureDoesNotDeliverOrStampPartialStaticFiles() {
        AtomicInteger pluginCalls = new AtomicInteger();
        PluginCorePlugin plugin = (PluginCorePlugin) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{PluginCorePlugin.class}, (proxy, method, args) -> {
            if (method.getName().equals("isStarted")) return true;
            if (method.getName().equals("refreshCacheWithinBudget")) { pluginCalls.incrementAndGet(); return true; }
            throw new AssertionError(method.getName());
        });
        com.zrlog.business.plugin.StaticSitePlugin site = (com.zrlog.business.plugin.StaticSitePlugin) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class[]{com.zrlog.business.plugin.StaticSitePlugin.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getType")) return com.zrlog.business.plugin.type.StaticSiteType.BLOG;
                    if (method.getName().equals("startWithinBudget")) throw new java.io.IOException("page generation failed");
                    throw new AssertionError("Unexpected static method: " + method.getName());
                });
        var service = new CacheRefreshService(cache(new AtomicInteger(), true), () -> plugin, () -> List.of(site),
                () -> { throw new AssertionError("Partial generation must not persist a sync stamp"); });
        assertThrows(java.io.IOException.class, () -> service.updateCacheWithinBudget(null,
                List.of(com.zrlog.business.plugin.type.StaticSiteType.BLOG), "0000000001.00000001793297580247.94783943-99bf-4773-89bc-5883a479898b", 1, () -> 5000L, () -> {}));
        assertEquals(1, pluginCalls.get());
    }

    private CacheService cache(AtomicInteger refreshed, boolean staticHtml) {
        PublicWebSiteInfo site = new PublicWebSiteInfo();
        site.setGenerator_html_status(staticHtml);
        site.setHost("blog.example.com");
        return (CacheService) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{CacheService.class}, (proxy, method, args) -> {
            if (method.getName().equals("getPublicWebSiteInfo")) return site;
            if (method.getName().equals("refreshInitData")) {refreshed.incrementAndGet(); return new BaseDataInitVO();}
            throw new AssertionError(method.getName());
        });
    }
}

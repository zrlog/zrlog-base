package com.zrlog.business.service;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.common.CacheService;
import com.zrlog.common.Constants;
import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.model.WebSite;
import com.zrlog.util.ThreadUtils;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 应用内缓存刷新，协调程序缓存、插件缓存和静态发布
 */
@Singleton
public class CacheRefreshService {
    private final CacheService cacheService;
    private final jakarta.inject.Provider<WebSite> websites;
    private final Supplier<PluginCorePlugin> pluginCore;
    private final Supplier<List<StaticSitePlugin>> staticSites;

    @Inject
    public CacheRefreshService(
            CacheService cacheService,
            Supplier<PluginCorePlugin> pluginCore,
            Supplier<List<StaticSitePlugin>> staticSites,
            jakarta.inject.Provider<WebSite> websites) {
        this.websites = Objects.requireNonNull(websites);
        this.cacheService = Objects.requireNonNull(cacheService);
        this.pluginCore = Objects.requireNonNull(pluginCore);
        this.staticSites = Objects.requireNonNull(staticSites);
    }

    public boolean isStaticSiteDisabled() {
        PublicWebSiteInfo site = cacheService.getPublicWebSiteInfo();
        return site == null || !Boolean.TRUE.equals(site.getGenerator_html_status()) || StringUtils.isEmpty(site.getHost());
    }


    private static final Logger LOGGER = LoggerUtil.getLogger(CacheRefreshService.class);

    public void updateCache(boolean async, HttpRequest request, List<StaticSiteType> staticSiteTypeList) {
        try {
            if (async) {
                BaseDataInitVO initVO = cacheService.refreshInitData();
                ThreadUtils.start(() -> {
                    refreshPluginCacheData(initVO.getVersion() + "", request, staticSiteTypeList);
                });
            } else {
                updateCacheSynchronouslyOrThrow(request, staticSiteTypeList);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Refresh cache error ", e);
        }
    }

    /**
     * Refreshes cache data synchronously for callers that need to report the actual completion result.
     */
    public void updateCacheSynchronouslyOrThrow(HttpRequest request,
                                                 List<StaticSiteType> staticSiteTypeList) {
        BaseDataInitVO initVO = cacheService.refreshInitData();
        refreshPluginCacheData(initVO.getVersion() + "", request, staticSiteTypeList);
    }

    private static int getSyncTimeout() {
        if (EnvKit.isFaaSMode()) {
            //建议配置 FaaS 为最大超时
            return 12 * 60;
        }
        return 3600;
    }

    private void notifyPluginUpdateCache(String cacheVersion, HttpRequest request) {
        //启动插件
        PluginCorePlugin pluginCorePlugin = pluginCore.get();
        if (Objects.nonNull(pluginCorePlugin) && !pluginCorePlugin.isStarted()) {
            pluginCorePlugin.start();
        }
        //plugin cache
        if (Objects.nonNull(pluginCorePlugin)) {
            pluginCorePlugin.refreshCache(cacheVersion, request);
        }
    }

    private void waitStaticSiteCacheSync(HttpRequest request, StaticSitePlugin staticSitePlugin) {
        //启动插件
        String version = staticSitePlugin.getSiteVersion();
        notifyPluginUpdateCache(version, request);
        for (; ; ) {
            if (staticSitePlugin.isSynchronized(request.getScheme())) {
                try {
                    websites.get().updateByKV(staticSitePlugin.getDbCacheKey(), version);
                } catch (SQLException e) {
                    LOGGER.log(Level.SEVERE, "update site version " + version + " cache error", e);
                }
                if (Constants.debugLoggerPrintAble()) {
                    LOGGER.info("update site version " + version + " cache success");
                }
                return;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public boolean refreshStaticSiteCache(HttpRequest request, List<StaticSiteType> siteTypes) {
        if (siteTypes == null || siteTypes.isEmpty()) {
            return false;
        }
        List<StaticSitePlugin> staticSitePlugins = staticSites.get().stream().filter(e -> siteTypes.contains(e.getType())).collect(Collectors.toList());
        if (staticSitePlugins.isEmpty()) {
            return false;
        }
        ExecutorService executorService = ThreadUtils.newFixedThreadPool(staticSitePlugins.size());
        try {
            CompletableFuture.allOf(staticSitePlugins.stream().map(staticSitePlugin -> {
                return CompletableFuture.runAsync(() -> {
                    staticSitePlugin.start();
                    notifyPluginUpdateCache(staticSitePlugin.getSiteVersion(), request);
                    waitStaticSiteCacheSync(request, staticSitePlugin);
                }, executorService);
            }).toArray(CompletableFuture[]::new)).get(getSyncTimeout(), TimeUnit.SECONDS);
            return true;
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            throw new RuntimeException(e);
        } finally {
            executorService.shutdown();
        }
    }

    private void refreshPluginCacheData(String cacheVersion, HttpRequest request, List<StaticSiteType> staticSiteTypeList) {
        notifyPluginUpdateCache(cacheVersion, request);
        if (!isStaticSiteDisabled()) {
            refreshStaticSiteCache(request, staticSiteTypeList);
        }
    }
}

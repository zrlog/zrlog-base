package com.zrlog.business.util;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.plugin.type.StaticSiteType;
import com.zrlog.common.Constants;
import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.model.WebSite;
import com.zrlog.util.ThreadUtils;

import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * 缓存工具，便于处理程序缓存和插件缓存
 */
public class CacheUtils {

    private static final Logger LOGGER = LoggerUtil.getLogger(CacheUtils.class);

    public static void updateCache(boolean async, HttpRequest request, List<StaticSiteType> staticSiteTypeList) {
        try {
            if (async) {
                BaseDataInitVO initVO = Constants.zrLogConfig.getCacheService().refreshInitData();
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
    public static void updateCacheSynchronouslyOrThrow(HttpRequest request,
                                                       List<StaticSiteType> staticSiteTypeList) {
        BaseDataInitVO initVO = Constants.zrLogConfig.getCacheService().refreshInitData();
        refreshPluginCacheData(initVO.getVersion() + "", request, staticSiteTypeList);
    }


    private static int getSyncTimeout() {
        if (EnvKit.isFaaSMode()) {
            //建议配置 FaaS 为最大超时
            return 12 * 60;
        }
        return 3600;
    }

    private static void notifyPluginUpdateCache(String cacheVersion, HttpRequest request) {
        //启动插件
        PluginCorePlugin pluginCorePlugin = Constants.zrLogConfig.getPlugin(PluginCorePlugin.class);
        if (Objects.nonNull(pluginCorePlugin) && !pluginCorePlugin.isStarted()) {
            pluginCorePlugin.start();
        }
        //plugin cache
        if (Objects.nonNull(pluginCorePlugin)) {
            pluginCorePlugin.refreshCache(cacheVersion, request);
        }
    }

    private static void waitStaticSiteCacheSync(HttpRequest request, StaticSitePlugin staticSitePlugin) {
        //启动插件
        String version = staticSitePlugin.getSiteVersion();
        CacheUtils.notifyPluginUpdateCache(version, request);
        for (; ; ) {
            if (staticSitePlugin.isSynchronized(request.getScheme())) {
                try {
                    new WebSite().updateByKV(staticSitePlugin.getDbCacheKey(), version);
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

    public static boolean refreshStaticSiteCache(HttpRequest request, List<StaticSiteType> siteTypes) {
        if (siteTypes == null || siteTypes.isEmpty()) {
            return false;
        }
        List<StaticSitePlugin> staticSitePlugins = Constants.zrLogConfig.getPluginsByClazz(StaticSitePlugin.class).stream().filter(e -> siteTypes.contains(e.getType())).collect(Collectors.toList());
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

    private static void refreshPluginCacheData(String cacheVersion, HttpRequest request, List<StaticSiteType> staticSiteTypeList) {
        notifyPluginUpdateCache(cacheVersion, request);
        if (!StaticSitePlugin.isDisabled()) {
            refreshStaticSiteCache(request, staticSiteTypeList);
        }
    }
}

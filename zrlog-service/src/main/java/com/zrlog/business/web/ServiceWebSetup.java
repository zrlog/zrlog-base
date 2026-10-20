package com.zrlog.business.web;

import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.business.di.DaggerServiceComponent;
import com.zrlog.business.di.ServiceComponent;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.business.plugin.PluginCorePluginImpl;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.service.*;
import com.zrlog.common.CacheService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.data.di.DataComponent;
import com.zrlog.plugin.Plugins;
import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSetupContext;

import java.io.File;

/**
 * Owns shared service plugins and database upgrades.
 */
public final class ServiceWebSetup implements WebSetup {
    private final ZrLogConfig config;
    private final File dbPropertiesFile;
    private final String contextPath;

    public ServiceWebSetup(WebSetupContext context) {
        this.config = context.getZrLogConfig();
        this.dbPropertiesFile = context.getDbPropertiesFile();
        this.contextPath = context.getContextPath();
    }

    @Override
    public void setup() {
        config.getServerConfig()
                .getBindings()
                .singleton(
                        ServiceComponent.class,
                        r ->
                                DaggerServiceComponent.factory()
                                        .create(
                                                r.get(DataComponent.class),
                                                config::isInstalled,
                                                config::getUpdater,
                                                () -> config.getPlugin(PluginCorePlugin.class),
                                                () ->
                                                        config.getPluginsByClazz(
                                                                StaticSitePlugin.class)))
                .singleton(CommonService.class, r -> r.get(ServiceComponent.class).common())
                .singleton(WebsiteKvService.class, r -> r.get(ServiceComponent.class).websiteKv())
                .singleton(UpgradeNoticeService.class, r -> r.get(ServiceComponent.class).notices())
                .singleton(UpgradeService.class, r -> r.get(ServiceComponent.class).upgrades())
                .singleton(
                        CacheRefreshService.class,
                        r -> r.get(ServiceComponent.class).cacheRefresh());
    }

    @Override
    public Plugins getPlugins() {
        Plugins plugins = new Plugins();
        if (config.isTest()) {
            return plugins;
        }
        CacheService cache = config.getServerConfig().getBindings().build().get(CacheService.class);
        plugins.add(
                config.getServerConfig()
                        .getBindings()
                        .build()
                        .get(ServiceComponent.class)
                        .cacheManager());
        plugins.add(new PluginCorePluginImpl(dbPropertiesFile, contextPath,
                config.getServerConfig()::getPort, cache::getPublicWebSiteInfo));
        return plugins;
    }

    @Override
    public void onDatabaseReady(DataSourceWrapper dataSource) {
        CacheService cache = config.getServerConfig().getBindings().build().get(CacheService.class);
        long sqlVersion = cache.getCurrentSqlVersion();
        if (sqlVersion >= 0) {
            config.getServerConfig().getBindings().build().get(ServiceComponent.class)
                    .databaseUpgrades().create(sqlVersion).tryDoUpgrade();
        }
    }
}

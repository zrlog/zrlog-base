package com.zrlog.business.web;

import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.business.di.DaggerServiceComponent;
import com.zrlog.business.di.ServiceComponent;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.business.plugin.PluginCorePluginImpl;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.service.*;
import com.zrlog.business.version.UpgradeVersionHandler;
import com.zrlog.common.CacheService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.data.di.DataComponent;
import com.zrlog.plugin.Plugins;
import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSetupContext;
import java.io.File;

/** Owns shared service plugins and database upgrades. */
public final class ServiceWebSetup implements WebSetup {
    private final ZrLogConfig config;
    private final File dbPropertiesFile;
    private final String contextPath;
    private final Object migrationMonitor = new Object();
    private boolean stopping;
    private DbUpgradeService migration;

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
        plugins.add(
                new PluginCorePluginImpl(
                        dbPropertiesFile,
                        contextPath,
                        config.getServerConfig()::getPort,
                        cache::getPublicWebSiteInfo));
        return plugins;
    }

    @Override
    public void beforeDatabaseReady(DataSourceWrapper dataSource) throws java.sql.SQLException {
        var startupData =
                config.getServerConfig()
                        .getBindings()
                        .build()
                        .get(DataComponent.class)
                        .startupData();
        long sqlVersion = startupData.readSqlVersion();
        if (sqlVersion != UpgradeVersionHandler.SQL_VERSION) startupData.invalidate();
        if (sqlVersion >= 0) {
            DbUpgradeService upgrade;
            synchronized (migrationMonitor) {
                if (stopping) {
                    throw new java.sql.SQLException("Application stopped before schema migration");
                }
                upgrade =
                        config.getServerConfig()
                                .getBindings()
                                .build()
                                .get(ServiceComponent.class)
                                .databaseUpgrades()
                                .create(sqlVersion);
                migration = upgrade;
            }
            upgrade.tryDoUpgrade();
        }
    }

    @Override
    public void stop() {
        DbUpgradeService current;
        synchronized (migrationMonitor) {
            stopping = true;
            current = migration;
        }
        if (current != null) {
            try {
                current.close();
            } catch (java.sql.SQLException failure) {
                throw new IllegalStateException(
                        "Cannot release schema migration lock during application stop", failure);
            }
        }
    }
}

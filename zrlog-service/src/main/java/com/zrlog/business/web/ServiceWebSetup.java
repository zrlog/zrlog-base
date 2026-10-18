package com.zrlog.business.web;

import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.business.plugin.CacheManagerPlugin;
import com.zrlog.business.plugin.PluginCorePluginImpl;
import com.zrlog.business.service.DbUpgradeService;
import com.zrlog.common.CacheService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.plugin.Plugins;
import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSetupContext;
import java.io.File;

/** Owns shared service plugins and database upgrades. */
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
    }

    @Override
    public Plugins getPlugins() {
        Plugins plugins = new Plugins();
        if (config.isTest()) {
            return plugins;
        }
        CacheService cache = config.getServerConfig().getBindings().build().get(CacheService.class);
        plugins.add(new CacheManagerPlugin(cache));
        plugins.add(new PluginCorePluginImpl(dbPropertiesFile, contextPath));
        return plugins;
    }

    @Override
    public void onDatabaseReady(DataSourceWrapper dataSource) {
        CacheService cache = config.getServerConfig().getBindings().build().get(CacheService.class);
        long sqlVersion = cache.getCurrentSqlVersion();
        if (sqlVersion >= 0) {
            new DbUpgradeService(dataSource, sqlVersion).tryDoUpgrade();
        }
    }
}

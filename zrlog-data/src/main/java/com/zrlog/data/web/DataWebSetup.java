package com.zrlog.data.web;

import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.common.CacheService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.cache.CacheServiceImpl;
import com.zrlog.data.util.WebSiteUtils;
import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSiteInfoProvider;

/** Owns registration and initialization of shared data services. */
public final class DataWebSetup implements WebSetup, WebSiteInfoProvider {
    private volatile CacheService cache;
    private final ZrLogConfig config;

    public DataWebSetup(ZrLogConfig config) {
        this.config = config;
    }

    @Override
    public void setup() {
        config.getServerConfig().getBindings().singleton(CacheService.class, resolver -> {
            if (config.getDataSource() == null) {
                throw new IllegalStateException("CacheService requires an initialized database");
            }
            return new CacheServiceImpl(config::isInstalled);
        });
    }

    @Override
    public PublicWebSiteInfo getPublicWebSiteInfo() {
        CacheService current = cache;
        return current == null
                ? WebSiteUtils.fillDefaultInfo(new PublicWebSiteInfo())
                : current.getPublicWebSiteInfo();
    }

    @Override
    public void onDatabaseReady(DataSourceWrapper dataSource) {
        cache = config.getServerConfig().getBindings().build().get(CacheService.class);
    }
}

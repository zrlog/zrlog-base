package com.zrlog.data.web;

import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.common.CacheService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.di.DaggerDataComponent;
import com.zrlog.data.di.DataComponent;
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
        DataComponent data =
                DaggerDataComponent.factory()
                        .create(
                                () -> (DataSourceWrapper) config.getDataSource(),
                                config::isInstalled);
        config.getServerConfig()
                .getBindings()
                .instance(DataComponent.class, data)
                .singleton(CacheService.class, resolver -> data.cache());
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

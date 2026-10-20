package com.zrlog.data.di;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.common.CacheService;
import dagger.Module;
import dagger.Provides;

/**
 * Share the owning application's database and cache with another module's graph.
 */
@Module
public final class DataBindingsModule {
    private DataBindingsModule() {
    }

    @Provides
    static DataSourceWrapper dataSource(DataComponent data) {
        return data.dataSource();
    }

    @Provides
    static CacheService cache(DataComponent data) {
        return data.cache();
    }

    @Provides
    static DAO dao(DataSourceWrapper source) {
        return new DAO(source);
    }
}

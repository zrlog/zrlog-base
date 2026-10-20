package com.zrlog.data.di;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.common.CacheService;
import com.zrlog.data.cache.CacheServiceImpl;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.function.Supplier;

@Module
abstract class DataModule {
    @Provides
    @Singleton
    static DataSourceWrapper dataSource(
            @Named("applicationDatabase") Supplier<DataSourceWrapper> source) {
        DataSourceWrapper database = source.get();
        if (database == null) throw new IllegalStateException("Application database is not ready");
        return database;
    }

    @Provides
    static DAO dao(DataSourceWrapper source) {
        return new DAO(source);
    }

    @Binds
    @Singleton
    abstract CacheService cache(CacheServiceImpl implementation);
}

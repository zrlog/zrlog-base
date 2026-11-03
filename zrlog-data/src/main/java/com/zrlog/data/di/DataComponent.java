package com.zrlog.data.di;

import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.common.CacheService;
import com.zrlog.data.service.BaseDataDbService;
import com.zrlog.data.service.DistributedLock;
import com.zrlog.data.service.UserPreferenceStore;
import com.zrlog.data.util.DistributedLockManager;
import com.zrlog.data.repository.*;
import dagger.BindsInstance;
import dagger.Component;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Application data ownership; DAO methods create fresh mutable instances.
 */
@Singleton
@Component(modules = DataModule.class)
public interface DataComponent {
    DataSourceWrapper dataSource();

    CacheService cache();

    DAO dao();

    LogRepository log();

    WebSiteRepository website();

    UserRepository user();

    TagRepository tag();

    TypeRepository type();

    LinkRepository link();

    LogNavRepository navigation();

    CommentRepository comment();

    PluginRepository plugin();

    BaseDataDbService baseData();

    UserPreferenceStore preferences();

    DistributedLock.Factory locks();

    DistributedLockManager lockManager();

    @Component.Factory
    interface Factory {
        DataComponent create(
                @BindsInstance @Named("applicationDatabase") Supplier<DataSourceWrapper> source,
                @BindsInstance BooleanSupplier installed);
    }
}

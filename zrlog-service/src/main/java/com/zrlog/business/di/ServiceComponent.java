package com.zrlog.business.di;

import com.zrlog.business.plugin.CacheManagerPlugin;
import com.zrlog.business.plugin.PluginCorePlugin;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.service.*;
import com.zrlog.common.Updater;
import com.zrlog.data.di.DaggerDataComponent;
import com.zrlog.data.di.DataBindingsModule;
import com.zrlog.data.di.DataComponent;

import dagger.BindsInstance;
import dagger.Component;

import jakarta.inject.Singleton;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

@Singleton
@Component(modules = {DataBindingsModule.class, ServiceModule.class})
public interface ServiceComponent {
    /** CLI and pre-install upgrades use defaults before an application database exists. */
    static ServiceComponent forUninstalledApplication(Updater updater) {
        DataComponent data = DaggerDataComponent.factory().create(() -> null, () -> false);
        return DaggerServiceComponent.factory()
                .create(
                        data,
                        () -> false,
                        () -> updater,
                        () -> null,
                        java.util.Collections::emptyList);
    }

    WebsiteKvService websiteKv();

    UpgradeNoticeService notices();

    UpgradeService upgrades();

    DbUpgradeService.Factory databaseUpgrades();

    BackupProtectionService backupProtection();

    CacheRefreshService cacheRefresh();

    CommonService common();

    CacheManagerPlugin cacheManager();

    @Component.Factory
    interface Factory {
        ServiceComponent create(
                @BindsInstance DataComponent data,
                @BindsInstance BooleanSupplier installed,
                @BindsInstance Supplier<Updater> updater,
                @BindsInstance Supplier<PluginCorePlugin> plugin,
                @BindsInstance Supplier<List<StaticSitePlugin>> staticSites);
    }
}

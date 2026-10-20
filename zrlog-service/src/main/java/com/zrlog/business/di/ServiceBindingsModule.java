package com.zrlog.business.di;

import com.zrlog.business.service.*;
import dagger.Module;
import dagger.Provides;

/**
 * Exposes services owned by the application's shared-service graph.
 */
@Module
public final class ServiceBindingsModule {
    private ServiceBindingsModule() {
    }

    @Provides
    static WebsiteKvService websiteKv(ServiceComponent services) {
        return services.websiteKv();
    }

    @Provides
    static UpgradeNoticeService notices(ServiceComponent services) {
        return services.notices();
    }

    @Provides
    static UpgradeService upgrades(ServiceComponent services) {
        return services.upgrades();
    }

    @Provides
    static BackupProtectionService backupProtection(ServiceComponent services) {
        return services.backupProtection();
    }

    @Provides
    static CacheRefreshService cacheRefresh(ServiceComponent services) {
        return services.cacheRefresh();
    }

    @Provides
    static CommonService common(ServiceComponent services) {
        return services.common();
    }
}

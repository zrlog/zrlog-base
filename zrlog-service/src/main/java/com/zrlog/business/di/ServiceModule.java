package com.zrlog.business.di;

import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.di.DataComponent;
import dagger.Module;
import dagger.Provides;

import java.util.function.Supplier;

@Module
final class ServiceModule {
    private ServiceModule() {
    }

    @Provides
    static Supplier<PublicWebSiteInfo> site(DataComponent data) {
        return () -> data.cache().getPublicWebSiteInfo();
    }
}

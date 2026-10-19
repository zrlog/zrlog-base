package com.zrlog.web;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.http.server.api.Interceptor;
import com.zrlog.common.Constants;
import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.util.ThreadUtils;
import com.zrlog.util.ZrLogBaseNativeImageUtils;
import com.zrlog.web.inteceptor.GlobalBaseInterceptor;
import com.zrlog.web.inteceptor.InstallStatusInterceptor;
import com.zrlog.web.inteceptor.MyI18nInterceptor;

import java.util.List;
import java.util.logging.Logger;

public class BaseWebSetup implements WebSetup {

    private static final Logger LOGGER = LoggerUtil.getLogger(BaseWebSetup.class);

    private final ZrLogConfig zrLogConfig;

    public BaseWebSetup(ZrLogConfig zrLogConfig) {
        this.zrLogConfig = zrLogConfig;
        if (zrLogConfig.getServerConfig().isNativeImageAgent()) {
            try {
                ZrLogBaseNativeImageUtils.reg();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void setup() {
        zrLogConfig.getServerConfig().getBindings()
                .interceptor(MyI18nInterceptor.class, r -> new MyI18nInterceptor(() -> {
                    var provider = zrLogConfig.getWebSetup(WebSiteInfoProvider.class);
                    var site = provider == null ? null : provider.getPublicWebSiteInfo();
                    return site == null ? Constants.DEFAULT_LANGUAGE : site.getLanguage();
                }))
                .interceptor(InstallStatusInterceptor.class, r -> new InstallStatusInterceptor(
                        zrLogConfig::isInstalled, zrLogConfig::isMissingConfig))
                .interceptor(GlobalBaseInterceptor.class, r -> new GlobalBaseInterceptor(!EnvKit.isDevMode()
                        && zrLogConfig.getUpdater() != null && zrLogConfig.getUpdater().getType() == UpdaterTypeEnum.WAR));
        if (ThreadUtils.isEnableLoom() && Constants.debugLoggerPrintAble()) {
            LOGGER.info("Java VirtualThread(loom) enabled");
        }
        List<Class<? extends Interceptor>> interceptors = zrLogConfig.getServerConfig().getInterceptors();
        //all
        interceptors.add(GlobalBaseInterceptor.class);
        interceptors.add(MyI18nInterceptor.class);
        interceptors.add(InstallStatusInterceptor.class);
    }
}

package com.zrlog.business.service;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.business.rest.response.PublicInfoVO;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.ZrLogUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Objects;
import java.util.function.Supplier;

@Singleton
public class CommonService {
    private final Supplier<PublicWebSiteInfo> site;

    @Inject
    public CommonService(Supplier<PublicWebSiteInfo> site) {
        this.site = Objects.requireNonNull(site);
    }

    public PublicInfoVO getPublicInfo(HttpRequest request) {
        PublicWebSiteInfo publicWebSiteInfo = site.get();
        boolean darkMode = publicWebSiteInfo.getAdmin_darkMode();
        String themeColor;
        String adminColor = publicWebSiteInfo.getAdmin_color_primary();
        if (darkMode) {
            themeColor = "#000000";
        } else {
            themeColor = adminColor;
        }
        String appId = publicWebSiteInfo.getAppId();
        return new PublicInfoVO(BlogBuildInfoUtil.getVersion(), publicWebSiteInfo.getTitle(), ZrLogUtil.getHomeUrlWithHost(request, publicWebSiteInfo.getHost()), darkMode, adminColor, themeColor, appId);
    }

}

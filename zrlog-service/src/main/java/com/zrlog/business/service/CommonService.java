package com.zrlog.business.service;

import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.business.rest.response.PublicInfoVO;
import com.zrlog.common.Constants;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.ZrLogUtil;

public class CommonService {

    public PublicInfoVO getPublicInfo(HttpRequest request) {
        PublicWebSiteInfo publicWebSiteInfo = Constants.getPublicWebSiteInfo();
        boolean darkMode = publicWebSiteInfo.getAdmin_darkMode();
        String themeColor;
        String adminColor = publicWebSiteInfo.getAdmin_color_primary();
        if (darkMode) {
            themeColor = "#000000";
        } else {
            themeColor = adminColor;
        }
        String appId = publicWebSiteInfo.getAppId();
        return new PublicInfoVO(BlogBuildInfoUtil.getVersion(), publicWebSiteInfo.getTitle(), ZrLogUtil.getHomeUrlWithHost(request), darkMode, adminColor, themeColor, appId);
    }

}

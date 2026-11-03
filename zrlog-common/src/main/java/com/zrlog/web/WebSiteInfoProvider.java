package com.zrlog.web;

import com.zrlog.common.vo.PublicWebSiteInfo;

/** Read-only site settings contributed by the data module, also available before installation. */
public interface WebSiteInfoProvider {
    PublicWebSiteInfo getPublicWebSiteInfo();
}

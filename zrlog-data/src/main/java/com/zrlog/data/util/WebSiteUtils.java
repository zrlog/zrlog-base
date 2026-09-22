package com.zrlog.data.util;

import com.hibegin.common.dao.ResultValueConvertUtils;
import com.hibegin.common.util.StringUtils;
import com.zrlog.common.Constants;
import com.zrlog.common.vo.PublicWebSiteInfo;

import java.util.Objects;

public class WebSiteUtils {

    public static long DEFAULT_SESSION_TIMEOUT = 1000 * 60 * 60 * 24L;
    public static final String DEFAULT_COLOR_PRIMARY_COLOR = "#1677ff";
    public static final long DEFAULT_ARTICLE_DIGEST_LENGTH = 200;
    public static final String DEFAULT_CONTENT_PROTECTOR_LICENSE_TYPE = "ALL_RIGHTS_RESERVED";
    public static final String DEFAULT_CONTENT_PROTECTOR_TEMPLATE = "<div class=\"zrlog-content-protector\" data-license=\"{licenseType}\">\n"
            + "  <p>本文作者：{author}</p>\n"
            + "  <p>原文链接：<a href=\"{url}\">{url}</a></p>\n"
            + "  <p>版权声明：{licenseName}，转载请注明出处。</p>\n"
            + "</div>";

    public static PublicWebSiteInfo fillDefaultInfo(PublicWebSiteInfo info) {
        if (Objects.isNull(info.getRows())) {
            info.setRows(10L);
        }
        if (Objects.isNull(info.getArticle_auto_digest_length())) {
            info.setArticle_auto_digest_length(DEFAULT_ARTICLE_DIGEST_LENGTH);
        }
        if (Objects.isNull(info.getSession_timeout())) {
            info.setSession_timeout(DEFAULT_SESSION_TIMEOUT / 60 / 1000);
        }
        info.setAdmin_darkMode(Objects.equals(info.getAdmin_darkMode(), true));
        info.setDisable_comment_status(Objects.equals(info.getDisable_comment_status(), true));
        info.setGenerator_html_status(Objects.equals(info.getGenerator_html_status(), true));
        info.setArticle_thumbnail_status(Objects.equals(info.getArticle_thumbnail_status(), true));
        info.setAdmin_compactMode(Objects.equals(info.getAdmin_compactMode(), true));
        info.setContent_protector_enabled(Objects.equals(info.getContent_protector_enabled(), true));
        if (StringUtils.isEmpty(info.getContent_protector_license_type())) {
            info.setContent_protector_license_type(DEFAULT_CONTENT_PROTECTOR_LICENSE_TYPE);
        }
        if (StringUtils.isEmpty(info.getContent_protector_template())) {
            info.setContent_protector_template(DEFAULT_CONTENT_PROTECTOR_TEMPLATE);
        }
        if (StringUtils.isEmpty(info.getAdmin_color_primary())) {
            info.setAdmin_color_primary(DEFAULT_COLOR_PRIMARY_COLOR);
        }
        if (StringUtils.isEmpty(info.getAdmin_theme())) {
            info.setAdmin_theme("default");
        }
        if (StringUtils.isEmpty(info.getLanguage())) {
            info.setLanguage(Constants.DEFAULT_LANGUAGE);
        }
        if (StringUtils.isEmpty(info.getTemplate())) {
            info.setTemplate(Constants.getDefaultTemplatePath());
        }
        boolean changyanStatus = ResultValueConvertUtils.toBoolean(info.getChangyan_status());
        if (changyanStatus) {
            if (StringUtils.isEmpty(info.getComment_plugin_name())) {
                info.setComment_plugin_name("changyan");
            }
        }
        if (StringUtils.isEmpty(info.getComment_plugin_name())) {
            info.setComment_plugin_name("comment");
        }
        return info;
    }
}

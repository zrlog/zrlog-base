package com.zrlog.common.vo;

import java.io.Serializable;

/** 存放全局对外可以访问的配置 */
public class PublicWebSiteInfo implements Serializable {

    private String appId;
    private String changyan_status;
    private String title;
    private String second_title;
    private String keywords;
    private String description;
    private Boolean generator_html_status;
    private String host;
    private Boolean disable_comment_status;
    private Boolean article_thumbnail_status;
    private String icp;
    private String webCm;
    private String language;
    private String admin_color_primary;
    private String admin_theme;
    private Long article_auto_digest_length;
    private Boolean admin_darkMode;
    private Boolean admin_compactMode;
    private Long rows;
    private String staticResourceHost;
    private String template;
    private String robotRuleContent;
    private Long session_timeout;
    private String comment_plugin_name;
    private String system_notification;
    private String author;
    private Boolean content_protector_enabled;
    private String content_protector_license_type;
    private String content_protector_template;

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getChangyan_status() {
        return changyan_status;
    }

    public void setChangyan_status(String changyan_status) {
        this.changyan_status = changyan_status;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSecond_title() {
        return second_title;
    }

    public void setSecond_title(String second_title) {
        this.second_title = second_title;
    }

    public String getKeywords() {
        return keywords;
    }

    public void setKeywords(String keywords) {
        this.keywords = keywords;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getGenerator_html_status() {
        return generator_html_status;
    }

    public void setGenerator_html_status(Boolean generator_html_status) {
        this.generator_html_status = generator_html_status;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Boolean getDisable_comment_status() {
        return disable_comment_status;
    }

    public void setDisable_comment_status(Boolean disable_comment_status) {
        this.disable_comment_status = disable_comment_status;
    }

    public Boolean getArticle_thumbnail_status() {
        return article_thumbnail_status;
    }

    public void setArticle_thumbnail_status(Boolean article_thumbnail_status) {
        this.article_thumbnail_status = article_thumbnail_status;
    }

    public String getIcp() {
        return icp;
    }

    public void setIcp(String icp) {
        this.icp = icp;
    }

    public String getWebCm() {
        return webCm;
    }

    public void setWebCm(String webCm) {
        this.webCm = webCm;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getAdmin_color_primary() {
        return admin_color_primary;
    }

    public void setAdmin_color_primary(String admin_color_primary) {
        this.admin_color_primary = admin_color_primary;
    }

    public Boolean getAdmin_darkMode() {
        return admin_darkMode;
    }

    public void setAdmin_darkMode(Boolean admin_darkMode) {
        this.admin_darkMode = admin_darkMode;
    }

    public String getStaticResourceHost() {
        return staticResourceHost;
    }

    public void setStaticResourceHost(String staticResourceHost) {
        this.staticResourceHost = staticResourceHost;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public String getRobotRuleContent() {
        return robotRuleContent;
    }

    public void setRobotRuleContent(String robotRuleContent) {
        this.robotRuleContent = robotRuleContent;
    }

    public Long getArticle_auto_digest_length() {
        return article_auto_digest_length;
    }

    public void setArticle_auto_digest_length(Long article_auto_digest_length) {
        this.article_auto_digest_length = article_auto_digest_length;
    }

    public Long getRows() {
        return rows;
    }

    public void setRows(Long rows) {
        this.rows = rows;
    }

    public Long getSession_timeout() {
        return session_timeout;
    }

    public void setSession_timeout(Long session_timeout) {
        this.session_timeout = session_timeout;
    }

    public String getComment_plugin_name() {
        return comment_plugin_name;
    }

    public void setComment_plugin_name(String comment_plugin_name) {
        this.comment_plugin_name = comment_plugin_name;
    }

    public String getSystem_notification() {
        return system_notification;
    }

    public void setSystem_notification(String system_notification) {
        this.system_notification = system_notification;
    }

    public Boolean getAdmin_compactMode() {
        return admin_compactMode;
    }

    public void setAdmin_compactMode(Boolean admin_compactMode) {
        this.admin_compactMode = admin_compactMode;
    }

    public String getAdmin_theme() {
        return admin_theme;
    }

    public void setAdmin_theme(String admin_theme) {
        this.admin_theme = admin_theme;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public Boolean getContent_protector_enabled() {
        return content_protector_enabled;
    }

    public void setContent_protector_enabled(Boolean content_protector_enabled) {
        this.content_protector_enabled = content_protector_enabled;
    }

    public String getContent_protector_license_type() {
        return content_protector_license_type;
    }

    public void setContent_protector_license_type(String content_protector_license_type) {
        this.content_protector_license_type = content_protector_license_type;
    }

    public String getContent_protector_template() {
        return content_protector_template;
    }

    public void setContent_protector_template(String content_protector_template) {
        this.content_protector_template = content_protector_template;
    }
}

package com.zrlog.blog.web.template.vo;

import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.common.vo.SocialPreviewDTO;

import java.util.Map;
import java.util.Objects;

/**
 * 页面公共数据
 */
public class BasePageInfo {

    protected BaseDataInitVO init;
    private String title;
    private String keywords;
    private String description;
    private Boolean staticBlog;
    private String suffix;
    private String url;
    private String lang;
    private String local;
    private String rurl;
    private String host;
    private String baseUrl;
    private String templateUrl;
    private String searchUrl;
    private String arrangePlugin;
    private String template;
    private String staticResourceBaseUrl;
    private Map<String, Object> _res;
    private String key;
    private String requrl;
    private String reqUriPath;
    private String reqQueryString;
    private String basePath;
    private String baseWithHostPath;
    private String contextPath;
    private SocialPreviewDTO socialPreview;
    private String socialPreviewHtml;

    public String getRequrl() {
        return requrl;
    }

    public void setRequrl(String requrl) {
        this.requrl = requrl;
    }

    public String getReqUriPath() {
        return reqUriPath;
    }

    public void setReqUriPath(String reqUriPath) {
        this.reqUriPath = reqUriPath;
    }

    public String getReqQueryString() {
        return reqQueryString;
    }

    public void setReqQueryString(String reqQueryString) {
        this.reqQueryString = reqQueryString;
    }

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getBaseWithHostPath() {
        return baseWithHostPath;
    }

    public void setBaseWithHostPath(String baseWithHostPath) {
        this.baseWithHostPath = baseWithHostPath;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public Boolean getStaticBlog() {
        return staticBlog;
    }

    public void setStaticBlog(Boolean staticBlog) {
        this.staticBlog = staticBlog;
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getRurl() {
        return rurl;
    }

    public void setRurl(String rurl) {
        this.rurl = rurl;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getTemplateUrl() {
        return templateUrl;
    }

    public void setTemplateUrl(String templateUrl) {
        this.templateUrl = templateUrl;
    }

    public String getSearchUrl() {
        return searchUrl;
    }

    public void setSearchUrl(String searchUrl) {
        this.searchUrl = searchUrl;
    }

    public String getArrangePlugin() {
        return arrangePlugin;
    }

    public void setArrangePlugin(String arrangePlugin) {
        this.arrangePlugin = arrangePlugin;
    }

    public String getTemplate() {
        return template;
    }

    public void setTemplate(String template) {
        this.template = template;
    }

    public String getStaticResourceBaseUrl() {
        return staticResourceBaseUrl;
    }

    public void setStaticResourceBaseUrl(String staticResourceBaseUrl) {
        this.staticResourceBaseUrl = staticResourceBaseUrl;
    }

    public BaseDataInitVO getInit() {
        return init;
    }

    public void setInit(BaseDataInitVO init) {
        this.init = init;
    }

    public PublicWebSiteInfo getWebs() {
        if (Objects.isNull(init)) {
            return null;
        }
        return init.getWebSite();
    }

    /**
     * @return website
     * @deprecated see getWebs();
     */
    @Deprecated
    public PublicWebSiteInfo getWebSite() {
        return getWebs();
    }

    /**
     * @return website
     * @deprecated see getWebs();
     */
    @Deprecated
    public PublicWebSiteInfo getWebsite() {
        return getWebs();
    }

    /**
     * @return website
     * @deprecated see getWebs();
     */
    @Deprecated
    public PublicWebSiteInfo getWEB_SITE() {
        return getWebs();
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getLocal() {
        return local;
    }

    public void setLocal(String local) {
        this.local = local;
    }

    public Map<String, Object> get_res() {
        return _res;
    }

    public void set_res(Map<String, Object> _res) {
        this._res = _res;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public SocialPreviewDTO getSocialPreview() {
        return socialPreview;
    }

    public void setSocialPreview(SocialPreviewDTO socialPreview) {
        this.socialPreview = socialPreview;
    }

    public String getSocialPreviewHtml() {
        return socialPreviewHtml;
    }

    public void setSocialPreviewHtml(String socialPreviewHtml) {
        this.socialPreviewHtml = socialPreviewHtml;
    }
}

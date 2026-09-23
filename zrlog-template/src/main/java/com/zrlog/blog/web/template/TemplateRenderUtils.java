package com.zrlog.blog.web.template;

import com.hibegin.common.dao.dto.PageData;
import com.hibegin.common.util.BeanUtil;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.blog.web.template.vo.ArticleDetailPageVO;
import com.zrlog.blog.web.template.vo.ArticleListPageVO;
import com.zrlog.blog.web.template.vo.BasePageInfo;
import com.zrlog.blog.web.template.vo.NotFindPageVO;
import com.zrlog.blog.web.util.WebTools;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.common.Constants;
import com.zrlog.common.cache.dto.LogNavDTO;
import com.zrlog.common.cache.dto.TagDTO;
import com.zrlog.common.cache.dto.TypeDTO;
import com.zrlog.common.cache.vo.Archive;
import com.zrlog.common.cache.vo.BaseDataInitVO;
import com.zrlog.common.vo.I18nVO;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.dto.ArticleBasicDTO;
import com.zrlog.data.dto.ArticleDetailDTO;
import com.zrlog.common.vo.SocialPreviewDTO;
import com.zrlog.data.util.ContentProtectorUtils;
import com.zrlog.data.util.SocialPreviewUtils;
import com.zrlog.plugin.BaseStaticSitePlugin;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.TemplateHelper;
import com.zrlog.util.ZrLogUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TemplateRenderUtils {

    private static final Logger LOGGER = LoggerUtil.getLogger(TemplateHelper.class);


    /**
     * 转换导航条数据
     *
     * @param request
     * @param suffix
     * @param url
     * @return
     */
    public static String getNavUrl(HttpRequest request, String suffix, String url) {
        if ("/".equals(url)) {
            return ZrLogUtil.getHomeUrlWithHost(request);
        }
        //文章页
        if (url.startsWith("/")) {
            String nUrl = ZrLogUtil.getHomeUrlWithHost(request) + url.substring(1);
            if (Objects.nonNull(suffix) && !suffix.trim().isEmpty() && nUrl.endsWith(suffix)) {
                return nUrl;
            }
            if (Objects.equals("/admin/login", url)) {
                return nUrl;
            }
            return nUrl + suffix;
        }
        return url;
    }

    private static BasePageInfo buildBasePageInfo(HttpRequest request) {
        ArticleDetailDTO log = (ArticleDetailDTO) request.getAttr().get("log");
        BaseDataInitVO baseDataInitVO = BeanUtil.convert(Constants.zrLogConfig.getCacheService().getInitData(), BaseDataInitVO.class);
        if (Objects.nonNull(log)) {
            return new ArticleDetailPageVO(log, baseDataInitVO);
        }
        PageData<ArticleBasicDTO> data = (PageData<ArticleBasicDTO>) request.getAttr().get("data");
        if (Objects.nonNull(data)) {
            return new ArticleListPageVO(data, baseDataInitVO);
        }
        return new NotFindPageVO(baseDataInitVO);
    }

    public static BasePageInfo fullTemplateInfo(HttpRequest request) {
        String templatePath = TemplateHelper.getTemplatePath(request);
        I18nVO i18nInfo = I18nUtil.addToRequestWithTemplatePath(templatePath, request);
        BasePageInfo basePageInfo = buildBasePageInfo(request);
        basePageInfo.setTemplate(templatePath);
        basePageInfo.setLang(i18nInfo.getLang());
        basePageInfo.setLocal(i18nInfo.getLocale());
        Map<String, Object> res = i18nInfo.getBlog().get(i18nInfo.getLocale());
        if (Objects.nonNull(res)) {
            res.putAll(Constants.zrLogConfig.getCacheService().getTemplateConfigMapWithCache(templatePath));
            basePageInfo.set_res(res);
        }
        fillInfo(request, basePageInfo);
        return basePageInfo;
    }

    private static void fillInfo(HttpRequest request, BasePageInfo basePageInfo) {
        boolean staticBlog = BaseStaticSitePlugin.isStaticPluginRequest(request);
        String suffix = StaticSitePlugin.getSuffix(request);
        basePageInfo.setStaticBlog(staticBlog);
        basePageInfo.setSuffix(suffix);
        basePageInfo.setKey((String) request.getAttr().get("key"));
        PublicWebSiteInfo webSite = basePageInfo.getWebs();
        setUrlInfo(request, staticBlog, webSite, basePageInfo);
        staticHtml(request, basePageInfo);
        fillTags(suffix, request, basePageInfo.getInit().getTags());
        fillType(suffix, basePageInfo.getInit().getTypes(), request, basePageInfo);
        fullNavBar(request, suffix, basePageInfo.getInit());
        basePageInfo.getInit().setArchiveList(getConvertedArchives(suffix, request, basePageInfo.getInit().getArchives()));
        //set comment info
        basePageInfo.setRequrl(ZrLogUtil.getFullUrl(request));
        basePageInfo.setReqUriPath(request.getUri());
        basePageInfo.setReqQueryString(request.getQueryStr());
        basePageInfo.setBasePath(WebTools.getHomeUrl(request));
        basePageInfo.setContextPath(request.getContextPath());
        basePageInfo.setBaseWithHostPath(ZrLogUtil.getHomeUrlWithHostNotProtocol(request));
    }

    private static boolean isHomePage(HttpRequest request) {
        String uri = request.getUri().replace(".html", "");
        return uri.isEmpty() || "/".equals(uri) || "/all-1".equals(uri) || "/all".equals(uri) || ("/" + Constants.getArticleUri() + "all").equals(uri) || ("/" + Constants.getArticleUri() + "all-1").equals(uri);
    }

    private static String ignoreScheme(String url, String suffix) {
        if (suffix != null && !suffix.isEmpty() && url.endsWith(suffix)) {
            url = url.substring(0, url.length() - suffix.length());
        }
        if (url.startsWith("http://")) {
            return url.substring("http:".length());
        } else if (url.startsWith("https://")) {
            return url.substring("https:".length());
        }
        return url;
    }

    private static void setUrlInfo(HttpRequest request, boolean staticBlog, PublicWebSiteInfo webSite, BasePageInfo basePageInfo) {
        String templateUrl;
        String templatePath = basePageInfo.getTemplate();
        if (staticBlog) {
            templateUrl = templatePath;
        } else if (isCdnResourceAble(webSite, templatePath)) {
            String cndUrl = "//" + webSite.getStaticResourceHost();
            templateUrl = cndUrl + templatePath;
            basePageInfo.setStaticResourceBaseUrl(cndUrl + "/");
        } else {
            templateUrl = basePageInfo.getTemplate();
        }
        String baseUrl = WebTools.getHomeUrl(request);
        basePageInfo.setBaseUrl(baseUrl);
        basePageInfo.setUrl(WebTools.buildEncodedUrl(request, templateUrl));
        basePageInfo.setTemplateUrl(WebTools.buildEncodedUrl(request, templateUrl));
        basePageInfo.setRurl(baseUrl);
        basePageInfo.setBaseUrl(baseUrl);
        basePageInfo.setHost(ZrLogUtil.getBlogHost(request));
        basePageInfo.setSearchUrl(WebTools.buildEncodedUrl(request, Constants.getArticleUri() + "search"));
    }

    private static boolean isCdnResourceAble(PublicWebSiteInfo webSite, String templatePath) {
        if (Objects.equals("0", System.getenv().get("CDN"))) {
            return false;
        }
        Properties properties = new Properties();
        File file = PathUtil.getStaticFile(templatePath + "/template.properties");
        if (!file.exists()) {
            return false;
        }
        try (FileInputStream fileInputStream = new FileInputStream(file)) {
            properties.load(fileInputStream);
            if (properties.getProperty("staticResource") != null) {
                return webSite.getStaticResourceHost() != null && !"".equals(webSite.getStaticResourceHost());
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "load properties error", e);
        }
        return false;
    }

    private static void staticHtml(HttpRequest request, BasePageInfo pageInfo) {
        PublicWebSiteInfo webSite = pageInfo.getWebs();
        String webSiteTitle = webSite.getTitle();
        String webSiteSecondTitle = webSite.getSecond_title();
        StringJoiner sj = new StringJoiner(" - ");
        if (pageInfo instanceof ArticleListPageVO) {
            pageInfo.setKeywords(webSite.getKeywords());
            String tipsType = (String) request.getAttr().get("tipsType");
            String tipsName = (String) request.getAttr().get("tipsName");
            ((ArticleListPageVO) pageInfo).setTipsType(tipsType);
            ((ArticleListPageVO) pageInfo).setTipsName(tipsName);
            appendListPageTitle(sj, tipsType, tipsName);
            PagerVO pager = (PagerVO) request.getAttr().get("pager");
            if (Objects.nonNull(pager)) {
                ((ArticleListPageVO) pageInfo).setPager(pager);
            }
            ((ArticleListPageVO) pageInfo).setYurl((String) request.getAttr().get("yurl"));
        } else if (pageInfo instanceof ArticleDetailPageVO) {
            ArticleDetailDTO objectMap = ((ArticleDetailPageVO) pageInfo).getLog();
            objectMap.setContentProtectorHtml(ContentProtectorUtils.render(webSite, objectMap, ZrLogUtil.getFullUrl(request)));
            tryEnableArrangePlugin(objectMap.getArrange_plugin(), pageInfo);
            String articleTitle = objectMap.getTitle();
            if (StringUtils.isNotEmpty(articleTitle)) {
                sj.add(articleTitle);
            }
            String keywords = objectMap.getKeywords();
            if (StringUtils.isNotEmpty(keywords)) {
                pageInfo.setKeywords(keywords);
            } else {
                pageInfo.setKeywords(webSite.getKeywords());
            }
        } else if (pageInfo instanceof NotFindPageVO) {
            sj.add(notFoundPageTitle());
        }
        appendListPageTitle(sj, webSiteTitle, webSiteSecondTitle);
        pageInfo.setTitle(sj.toString());
        pageInfo.setDescription(webSite.getDescription());
        fillSocialPreview(request, pageInfo, webSite);
    }

    static void appendListPageTitle(StringJoiner title, String tipsType, String tipsName) {
        if (StringUtils.isNotEmpty(tipsType)) {
            title.add(tipsType);
        }
        if (StringUtils.isNotEmpty(tipsName)) {
            title.add(tipsName);
        }
    }

    static String notFoundPageTitle() {
        return I18nUtil.getBackendStringFromRes("notFound");
    }

    private static void fillSocialPreview(HttpRequest request, BasePageInfo pageInfo, PublicWebSiteInfo webSite) {
        SocialPreviewDTO socialPreview;
        String fullUrl = ZrLogUtil.getFullUrl(request);
        if (pageInfo instanceof ArticleDetailPageVO) {
            ArticleDetailDTO log = ((ArticleDetailPageVO) pageInfo).getLog();
            socialPreview = SocialPreviewUtils.article(webSite, log, pageInfo.getTitle(), fullUrl, log.getThumbnail());
        } else {
            socialPreview = SocialPreviewUtils.website(webSite, pageInfo.getTitle(), pageInfo.getDescription(), fullUrl, "");
        }
        pageInfo.setSocialPreview(socialPreview);
        pageInfo.setSocialPreviewHtml(socialPreview.getMetaHtml());
    }

    private static void tryEnableArrangePlugin(String pluginName, BasePageInfo basePageInfo) {
        if (StringUtils.isNotEmpty(pluginName)) {
            basePageInfo.setArrangePlugin(pluginName);
        }
    }

    static void fullNavBar(HttpRequest request, String suffix, BaseDataInitVO baseDataInitVO) {
        List<LogNavDTO> logNavList = baseDataInitVO.getLogNavs();
        for (LogNavDTO logNav : logNavList) {
            String url = logNav.getUrl();
            boolean current = "/".equals(url) && isHomePage(request);
            if (url.startsWith("/")) {
                url = getNavUrl(request, suffix, url);
                logNav.setUrl(url);
                if (!current) {
                    current = ignoreScheme(request.getUrl(), suffix).equals(ignoreScheme(url, suffix));
                }
            } else if (!current) {
                current = ignoreScheme(request.getUrl(), suffix).equals(ignoreScheme(url, suffix));
            }
            logNav.setCurrent(current);
        }
    }

    private static List<Archive> getConvertedArchives(String suffix, HttpRequest request, Map<String, Long> archiveMap) {
        List<Archive> archives = new ArrayList<>();
        for (Map.Entry<String, Long> entry : archiveMap.entrySet()) {
            Archive archive = new Archive();
            archive.setCount(entry.getValue());
            archive.setText(entry.getKey());
            String tagUri = WebTools.buildEncodedUrl(request, Constants.getArticleUri() + "record/" + entry.getKey() + suffix);
            archive.setUrl(tagUri);
            archives.add(archive);
        }
        return archives;
    }

    private static void fillTags(String suffix, HttpRequest request, List<TagDTO> tags) {
        for (TagDTO tag : tags) {
            tag.setUrl(WebTools.buildEncodedUrl(request, Constants.getArticleUri() + "tag/" + tag.getText() + suffix));
        }
    }

    private static void fillType(String suffix, List<TypeDTO> types, HttpRequest request, BasePageInfo pageInfo) {
        for (TypeDTO type : types) {
            String typeUri = "/" + Constants.getArticleUri() + "sort/" + type.getAlias();
            if (request.getUri().startsWith(typeUri)) {
                tryEnableArrangePlugin(type.getArrange_plugin(), pageInfo);
            }
            type.setUrl(WebTools.buildEncodedUrl(request, typeUri + suffix));
        }
    }

}

package com.zrlog.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hibegin.common.dao.DAO;
import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.ResultBeanUtils;
import com.hibegin.common.util.BeanUtil;
import com.hibegin.common.util.StringUtils;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.dto.FaviconBase64DTO;
import com.zrlog.data.exception.DAOException;
import com.zrlog.data.util.WebSiteUtils;
import jakarta.inject.Inject;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 存放全局的设置，比如网站标题，关键字，插件，主题的配置信息等，当字典表处理即可，对应数据库的 website 表
 */
public class WebSite extends DAO {

    //number
    public static final String generator_html_status = "generator_html_status";
    public static final String disable_comment_status = "disable_comment_status";
    public static final String article_thumbnail_status = "article_thumbnail_status";
    public static final String article_auto_digest_length = "article_auto_digest_length";
    public static final String admin_darkMode = "admin_darkMode";
    public static final String admin_theme = "admin_theme";
    public static final String admin_compactMode = "admin_compactMode";
    public static final String rows = "rows";
    public static final String session_timeout = "session_timeout";
    public static final String content_protector_enabled = "content_protector_enabled";
    //string
    public static final String appId = "appId";
    public static final String changyan_status = "changyan_status";
    public static final String title = "title";
    public static final String second_title = "second_title";
    public static final String keywords = "keywords";
    public static final String description = "description";
    public static final String host = "host";
    public static final String icp = "icp";
    public static final String webCm = "webCm";
    public static final String language = "language";
    public static final String admin_color_primary = "admin_color_primary";
    public static final String staticResourceHost = "staticResourceHost";
    public static final String template = "template";
    public static final String robotRuleContent = "robotRuleContent";
    public static final String comment_plugin_name = "comment_plugin_name";
    public static final String system_notification = "system_notification";
    public static final String author = "author";
    public static final String content_protector_license_type = "content_protector_license_type";
    public static final String content_protector_template = "content_protector_template";
    private static final List<String> websitePublicQueryKeys;
    private static final List<String> websitePublicQueryStrKeys;
    private static final String TEMPLATE_CONFIG_SUFFIX = "_setting";

    static {

        String[] listNum = new String[]{generator_html_status, disable_comment_status,
                article_thumbnail_status, article_auto_digest_length, admin_darkMode, admin_compactMode, rows,
                session_timeout, content_protector_enabled};
        websitePublicQueryKeys = new ArrayList<>();
        //str
        websitePublicQueryStrKeys = Arrays.asList(appId, changyan_status, title, second_title, keywords, description, host,
                icp, robotRuleContent, comment_plugin_name, webCm, language, admin_color_primary, admin_theme,
                staticResourceHost, template, system_notification, author, content_protector_license_type,
                content_protector_template);
        websitePublicQueryKeys.addAll(websitePublicQueryStrKeys);
        websitePublicQueryKeys.addAll(Arrays.asList(listNum));
    }

    private final DataSourceWrapper dataSource;

    @Inject
    public WebSite(DataSourceWrapper dataSource) {
        super(dataSource);
        this.dataSource = dataSource;
        this.tableName = "website";
        this.pk = "siteId";
    }

    public PublicWebSiteInfo getPublicWebSite() {
        return WebSiteUtils.fillDefaultInfo(ResultBeanUtils.convert(getWebSiteByNameIn(websitePublicQueryKeys), PublicWebSiteInfo.class));
    }

    private Map<String, Object> fillToMap(List<Map<String, Object>> lw, List<String> names) {
        Map<String, Object> webSites = new LinkedHashMap<>();
        for (String name : names) {
            Object value = null;
            Object remark = null;
            for (Map<String, Object> map : lw) {
                if (Objects.equals(map.get("name"), name)) {
                    value = map.get("value");
                    remark = map.get("remark");
                    break;
                }
            }
            if (Objects.isNull(value) && websitePublicQueryStrKeys.contains(name)) {
                value = "";
            }
            webSites.put(name, value);
            webSites.put(name + "Remark", remark);
        }
        return webSites;
    }

    public Map<String, Object> getWebSiteByNameIn(List<String> names) {
        List<Map<String, Object>> lw;
        StringJoiner sj = new StringJoiner(",");
        Object[] params = new String[names.size()];
        for (int i = 0; i < params.length; i++) {
            sj.add("?");
            params[i] = names.get(i);
        }
        try {
            lw = queryListWithParams("select name,remark,value from " + tableName + " where name in " + "(" + sj + ")", params);
        } catch (SQLException e) {
            throw new DAOException(e);
        }
        return fillToMap(lw, names);
    }

    public boolean updateByKV(String name, Object value) throws SQLException {
        if (queryFirstObj("select siteId from " + tableName + " where name=?", name) != null) {
            return execute("update " + tableName + " set value=? where name=?", value, name);
        } else {
            return execute("insert INTO  " + tableName + " (`value`,`name`) VALUES (?,?)", value, name);
        }
    }

    public String getStringValueByName(String name) {
        try {
            Map<String, Object> webSiteByNameIn = getWebSiteByNameIn(Collections.singletonList(name));
            Object value = webSiteByNameIn.get(name);
            if (Objects.isNull(value)) {
                return "";
            }
            return value.toString();
        } catch (Exception e) {
            //ignore，比如在未安装时，会有该异常但是不影响逻辑
        }
        return "";
    }


    public Map<String, Object> getTemplateConfigMap(String templateName) {
        String dbJsonStr =
                new WebSite(dataSource).getStringValueByName(templateName + TEMPLATE_CONFIG_SUFFIX);
        if (StringUtils.isNotEmpty(dbJsonStr)) {
            return new Gson().fromJson(dbJsonStr, Map.class);
        }
        return new ConcurrentHashMap<>();
    }

    public Map<String, Object> updateTemplateConfigMap(String templateName, Map<String, Object> settingMap) throws SQLException {
        new WebSite(dataSource)
                .updateByKV(
                        templateName + TEMPLATE_CONFIG_SUFFIX,
                        new GsonBuilder().serializeNulls().create().toJson(settingMap));
        return settingMap;
    }

    public FaviconBase64DTO faviconBase64DTO() {
        Map<String, Object> dataMap = getWebSiteByNameIn(Arrays.asList("favicon_ico_base64", "favicon_png_pwa_192_base64", "favicon_png_pwa_512_base64", generator_html_status));
        return BeanUtil.convert(dataMap, FaviconBase64DTO.class);
    }
}

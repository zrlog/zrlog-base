package com.zrlog.common;

import com.hibegin.common.util.EnvKit;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.theme.spi.BundledThemes;
import com.zrlog.web.WebSiteInfoProvider;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 存放全局的静态变量，有多个地方使用一个key时，存放在这里，方便代码的维护。
 */
public class Constants {

    public static final String ADMIN_URI_BASE_PATH = "/admin";
    public static final String API_PUBLIC_ADMIN_RESOURCE = "/api/public/adminResource";
    public static final String API_PUBLIC_VERSION = "/api/public/version";
    public static final String TEMPLATE_BASE_PATH = "/include/templates/";
    public static String getDefaultTemplatePath() { return BundledThemes.getInstance().defaultPath(); }
    public static final String ATTACHED_FOLDER = "/attached/";
    public static final String TEMPLATE_CONFIG_STR_KEY = "configStr";
    public static final String DATE_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ssXXX";
    public static ZrLogConfig zrLogConfig;
    private static volatile long lastAccessTime = System.currentTimeMillis();
    public static final String DEFAULT_LANGUAGE = "zh_CN";
    public static final String MYSQL_JDBC_PARAMS = "characterEncoding=utf8&allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=GMT";

    static {
        init();
    }

    public static long getLastAccessTime() {
        return lastAccessTime;
    }

    public static void setLastAccessTime(long lastAccessTime) {
        Constants.lastAccessTime = lastAccessTime;
    }

    public static boolean debugLoggerPrintAble() {
        return EnvKit.isDevMode();
    }

    public static String getZrLogHomeByEnv() {
        return System.getenv().get("ZRLOG_HOME");
    }

    public static String getZrLogHome() {
        if (Constants.getZrLogHomeByEnv() == null) {
            return System.getProperty("user.dir");
        } else {
            return Constants.getZrLogHomeByEnv();
        }
    }

    public static PublicWebSiteInfo getPublicWebSiteInfo() {
        WebSiteInfoProvider provider = zrLogConfig == null ? null
                : zrLogConfig.getWebSetup(WebSiteInfoProvider.class);
        return provider == null ? null : provider.getPublicWebSiteInfo();
    }

    public static boolean isStaticHtmlStatus() {
        PublicWebSiteInfo info = getPublicWebSiteInfo();
        return info != null && Boolean.TRUE.equals(info.getGenerator_html_status());
    }

    public static String getArticleUri() {
        return "";
    }

    public static String getHost() {
        PublicWebSiteInfo info = getPublicWebSiteInfo();
        return info == null ? "" : Objects.requireNonNullElse(info.getHost(), "");
    }

    public static List<String> articleRouterList() {
        return Collections.singletonList("/");
    }

    public static void init() {
        System.getProperties().put("java.util.logging.SimpleFormatter.format", "%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS.%1$tL %4$s %5$s%6$s%n");
    }

    public static String getLanguage() {
        PublicWebSiteInfo info = getPublicWebSiteInfo();
        return info == null ? DEFAULT_LANGUAGE : info.getLanguage();
    }
}

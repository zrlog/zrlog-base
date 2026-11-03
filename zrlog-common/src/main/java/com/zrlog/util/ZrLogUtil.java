package com.zrlog.util;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.VersionComparator;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.blog.web.util.WebTools;
import com.zrlog.common.Updater;
import com.zrlog.common.UpdaterTypeEnum;
import java.util.*;

/** ZrLog特有的一些工具方法 */
public class ZrLogUtil {

    public static final String DB_PROPERTIES_KEY_IN_ENV = "DB_PROPERTIES";

    private ZrLogUtil() {}

    public static String getHomeUrlWithHost(HttpRequest request, String configuredHost) {
        return "//" + getBlogHost(request, configuredHost) + WebTools.getHomeUrl(request);
    }

    public static String getHomeUrlWithHostNotProtocol(HttpRequest request, String configuredHost) {
        return getBlogHost(request, configuredHost) + WebTools.getHomeUrl(request);
    }

    public static String getBlogHost(HttpRequest request, String websiteHost) {
        if (Objects.nonNull(websiteHost) && !websiteHost.trim().isEmpty()) {
            return websiteHost;
        }
        if (Objects.isNull(request)) {
            return "";
        }
        return request.getHeader("Host");
    }

    public static String getFullUrl(HttpRequest request, String configuredHost) {
        String host = Objects.toString(getBlogHost(request, configuredHost), "");
        while (host.endsWith("/")) {
            host = host.substring(0, host.length() - 1);
        }
        String uri = Objects.toString(request.getUri(), "/");
        if (uri.isEmpty()) {
            uri = "/";
        }
        return "//" + host + (uri.startsWith("/") ? uri : "/" + uri);
    }

    public static boolean greatThenCurrentVersion(
            String buildId, Date releaseDate, String fetchedVersion) {
        if (buildId.equals(BlogBuildInfoUtil.getBuildId())
                || releaseDate.before(BlogBuildInfoUtil.getTime())) {
            return false;
        }
        int result =
                new VersionComparator().compare(fetchedVersion, BlogBuildInfoUtil.getVersion());
        if (result == 0) {
            return releaseDate.after(BlogBuildInfoUtil.getTime());
        } else {
            return result > 0;
        }
    }

    public static boolean isBae() {
        String value = System.getenv("SERVER_SOFTWARE");
        return value != null && value.startsWith("bae");
    }

    public static boolean isPreviewMode() {
        String value = System.getenv("PREVIEW_MODE");
        return "true".equalsIgnoreCase(value);
    }

    public static boolean isDockerMode() {
        String value = System.getenv("DOCKER_MODE");
        return "true".equalsIgnoreCase(value);
    }

    public static String getDbInfoByEnv() {
        return System.getenv(DB_PROPERTIES_KEY_IN_ENV);
    }

    public static boolean isSystemServiceMode() {
        String value = System.getenv("SYSTEM_SERVICE_MODE");
        return "true".equalsIgnoreCase(value);
    }

    public static boolean isWarMode(Updater updater) {
        if (EnvKit.isDevMode()) {
            return false;
        }
        if (Objects.isNull(updater)) {
            return false;
        }
        return updater.getType() == UpdaterTypeEnum.WAR;
    }

    public static void putLongTimeCache(HttpResponse response) {
        response.addHeader("Cache-Control", "max-age=31536000, immutable"); // 1 年的秒数
    }

    public static String getFaaSRoot() {
        if (EnvKit.isLambda()) {
            return System.getenv("LAMBDA_TASK_ROOT");
        }
        // Not implement
        return "/app";
    }

    public static boolean isShellDockerMode() {
        return Objects.equals(System.getenv().get("DOCKER_MODE_START_BY"), "shell");
    }
}

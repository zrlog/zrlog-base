package com.zrlog.util;


import com.hibegin.common.util.LoggerUtil;
import com.zrlog.common.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Objects;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 读取 ZrLog 构建信息，及 build.properties。
 * 注 build.properties 为使用 CI 工具自动加入的，git代码仓库并没有该文件。
 */
public class BlogBuildInfoUtil {

    private static final Logger LOGGER = LoggerUtil.getLogger(BlogBuildInfoUtil.class);
    private static final String DEFAULT_BUILD_ID = "0000000";
    private static final String DEFAULT_VERSION = "1.0.0-SNAPSHOT";
    private static final String DEFAULT_RUN_MODE = "RELEASE";
    private static final String DEFAULT_RESOURCE_DOWNLOAD_URL = "https://dl.zrlog.com/";
    private static final String DEFAULT_RUNTIME_TYPE = "java";
    private static final String DEFAULT_PACKAGE_TYPE = "zip";
    private static final String DEFAULT_UPDATE_VERSION_JSON_FILENAME = "last.version.json";

    /**
     * 目前以git的commitId的前7位标记构建的Id
     */
    private static String buildId = DEFAULT_BUILD_ID;
    private static String version = DEFAULT_VERSION;
    private static Date time = defaultBuildTime();
    private static String runMode = DEFAULT_RUN_MODE;
    private static String fileArch = "";
    private static String resourceDownloadUrl = DEFAULT_RESOURCE_DOWNLOAD_URL;
    private static String runtimeType = DEFAULT_RUNTIME_TYPE;
    private static String packageType = DEFAULT_PACKAGE_TYPE;
    private static String updateVersionJsonFilename = DEFAULT_UPDATE_VERSION_JSON_FILENAME;
    public static final String BUILD_PROPERTIES_FILE_PATH = "/build.properties";

    static {
        Properties properties = new Properties();
        try (InputStream inputStream = BlogBuildInfoUtil.class.getResourceAsStream(BUILD_PROPERTIES_FILE_PATH)) {
            if (Objects.nonNull(inputStream)) {
                properties.load(inputStream);
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "doRead stream error", e);
        }
        applyBuildInfo(loadBuildInfo(properties));
    }

    static BuildInfo loadBuildInfo(Properties properties) {
        String loadedBuildId = properties.get("buildId") == null
                ? DEFAULT_BUILD_ID : properties.get("buildId").toString();
        String loadedVersion = propertyOrDefault(properties, "version", DEFAULT_VERSION);
        Date loadedTime = defaultBuildTime();
        String buildTime = propertyOrDefault(properties, "buildTime", "");
        if (!buildTime.isEmpty()) {
            try {
                loadedTime = parseBuildTime(buildTime);
            } catch (ParseException e) {
                LOGGER.log(Level.SEVERE, "Invalid buildTime in build.properties; using fallback time", e);
            }
        }
        String loadedResourceDownloadUrl = propertyOrDefault(properties, "mirrorWebSite",
                DEFAULT_RESOURCE_DOWNLOAD_URL);
        if (loadedResourceDownloadUrl.endsWith("/")) {
            loadedResourceDownloadUrl = loadedResourceDownloadUrl.substring(0,
                    loadedResourceDownloadUrl.length() - 1);
        }
        return new BuildInfo(loadedBuildId, loadedVersion, loadedTime,
                propertyOrDefault(properties, "runMode", DEFAULT_RUN_MODE),
                propertyOrDefault(properties, "fileArch", ""), loadedResourceDownloadUrl,
                propertyOrDefault(properties, "runtimeType", DEFAULT_RUNTIME_TYPE),
                propertyOrDefault(properties, "packageType", DEFAULT_PACKAGE_TYPE),
                propertyOrDefault(properties, "updateVersionJsonFilename", DEFAULT_UPDATE_VERSION_JSON_FILENAME));
    }

    static Date parseBuildTime(String buildTime) throws ParseException {
        String normalizedBuildTime = buildTime;
        if (buildTime.length() > 10 && buildTime.charAt(10) == 'T') {
            normalizedBuildTime = buildTime.substring(0, 10) + " " + buildTime.substring(11);
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat(Constants.DATE_FORMAT_PATTERN);
        dateFormat.setLenient(false);
        ParsePosition position = new ParsePosition(0);
        Date parsedTime = dateFormat.parse(normalizedBuildTime, position);
        if (parsedTime == null || position.getIndex() != normalizedBuildTime.length()) {
            int errorOffset = position.getErrorIndex() >= 0 ? position.getErrorIndex() : position.getIndex();
            throw new ParseException("Unsupported buildTime: " + buildTime, errorOffset);
        }
        return parsedTime;
    }

    private static String propertyOrDefault(Properties properties, String key, String defaultValue) {
        Object value = properties.get(key);
        return value == null || value.toString().isEmpty() ? defaultValue : value.toString();
    }

    private static Date defaultBuildTime() {
        return Date.from(LocalDateTime.of(2015, 3, 29, 0, 0, 0)
                .atZone(ZoneOffset.systemDefault()).toInstant());
    }

    private static void applyBuildInfo(BuildInfo buildInfo) {
        buildId = buildInfo.buildId;
        version = buildInfo.version;
        time = buildInfo.time;
        runMode = buildInfo.runMode;
        fileArch = buildInfo.fileArch;
        resourceDownloadUrl = buildInfo.resourceDownloadUrl;
        runtimeType = buildInfo.runtimeType;
        packageType = buildInfo.packageType;
        updateVersionJsonFilename = buildInfo.updateVersionJsonFilename;
    }

    static final class BuildInfo {

        final String buildId;
        final String version;
        final Date time;
        final String runMode;
        final String fileArch;
        final String resourceDownloadUrl;
        final String runtimeType;
        final String packageType;
        final String updateVersionJsonFilename;

        private BuildInfo(String buildId, String version, Date time, String runMode, String fileArch,
                          String resourceDownloadUrl, String runtimeType, String packageType,
                          String updateVersionJsonFilename) {
            this.buildId = buildId;
            this.version = version;
            this.time = time;
            this.runMode = runMode;
            this.fileArch = fileArch;
            this.resourceDownloadUrl = resourceDownloadUrl;
            this.runtimeType = runtimeType;
            this.packageType = packageType;
            this.updateVersionJsonFilename = updateVersionJsonFilename;
        }
    }

    public static String getBuildId() {
        return buildId;
    }

    public static String getVersion() {
        return version;
    }

    public static Date getTime() {
        return time;
    }

    public static String getRunMode() {
        return runMode;
    }

    public static String getFileArch() {
        return fileArch;
    }

    public static String getRuntimeType() {
        return runtimeType;
    }

    public static String getPackageType() {
        return packageType;
    }

    public static String getUpdateVersionJsonFilename() {
        return updateVersionJsonFilename;
    }

    public static String getResourceDownloadUrl() {
        return resourceDownloadUrl;
    }

    public static boolean isRelease() {
        return "RELEASE".equalsIgnoreCase(runMode);
    }

    public static boolean isPreview() {
        return "PREVIEW".equalsIgnoreCase(runMode);
    }

    public static boolean isDev() {
        return "DEV".equalsIgnoreCase(runMode);
    }

    public static void main(String[] args) {
        LOGGER.info("isRelease = " + isRelease());
        LOGGER.info("version = " + getVersion());
        LOGGER.info("buildId = " + getBuildId());
        LOGGER.info("time = " + getTime());
        LOGGER.info("resourceDownloadUrl = " + getResourceDownloadUrl());
    }

    public static Properties getBlogProp() {
        Properties blogProperties = new Properties();
        blogProperties.put("version", BlogBuildInfoUtil.getVersion());
        blogProperties.put("buildId", BlogBuildInfoUtil.getBuildId());
        blogProperties.put("buildTime", new SimpleDateFormat("yyyy-MM-dd").format(BlogBuildInfoUtil.getTime()));
        blogProperties.put("runMode", BlogBuildInfoUtil.getRunMode());
        return blogProperties;
    }

    public static String getVersionShortInfo() {
        return BlogBuildInfoUtil.getVersion() + " - " + BlogBuildInfoUtil.getBuildId();
    }

    public static String getVersionInfo() {
        return getVersionShortInfo() + " (" + new SimpleDateFormat("yyyy-MM-dd").format(BlogBuildInfoUtil.getTime()) + ")";
    }

    public static String getVersionInfoFull() {
        return BlogBuildInfoUtil.getVersion() + " - " + BlogBuildInfoUtil.getBuildId() + " (" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(BlogBuildInfoUtil.getTime()) + ")";
    }
}

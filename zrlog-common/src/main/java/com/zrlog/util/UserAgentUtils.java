package com.zrlog.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class UserAgentUtils {

    private static final Pattern ZRLOGCTL_PATTERN = Pattern.compile(
            "(?:^|\\s)zrlogctl/([-!#$%&'*+.^_`|~0-9A-Za-z]+)(?=\\s|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CRAWLER_PATTERN = Pattern.compile(
            "(bot|spider|crawler|crawl|slurp|curl|wget|python-requests|python-urllib|java/|okhttp|apache-httpclient|httpclient|go-http-client|postmanruntime|headlesschrome|phantomjs|bingpreview|facebookexternalhit|googleweblight|baiduspider|bytespider|petalbot|semrushbot|ahrefsbot|mj12bot|yandexbot)",
            Pattern.CASE_INSENSITIVE);

    public static class UserAgentInfo {
        private final String os;
        private final String browser;
        private final String browserVersion;
        private final boolean crawler;

        public UserAgentInfo(String os, String browser, String browserVersion, boolean crawler) {
            this.os = os;
            this.browser = browser;
            this.browserVersion = browserVersion;
            this.crawler = crawler;
        }

        public String getOs() { return os; }
        public String getBrowser() { return browser; }
        public String getBrowserVersion() { return browserVersion; }
        public boolean isCrawler() { return crawler; }

        public String getFullBrowser() {
            if ("Unknown".equals(browserVersion)) {
                return browser;
            }
            return browser + " " + browserVersion;
        }
    }

    public static UserAgentInfo parse(String ua) {
        if (ua == null || ua.isEmpty()) {
            return new UserAgentInfo("Unknown", "Unknown", "Unknown", false);
        }

        String os = "Unknown";
        String browser = "Unknown";
        String version = "Unknown";
        boolean crawler = isCrawlerUserAgent(ua);

        String lowerUa = ua.toLowerCase();

        if (lowerUa.contains("windows")) {
            os = "Windows";
            if (lowerUa.contains("windows nt 10.0")) os = "Windows 10/11";
            else if (lowerUa.contains("windows nt 6.1")) os = "Windows 7";
            else if (lowerUa.contains("windows nt 6.2")) os = "Windows 8";
        } else if (lowerUa.contains("mac os x")) {
            os = "Mac OS X";
            if (lowerUa.contains("iphone")) os = "iPhone (iOS)";
            else if (lowerUa.contains("ipad")) os = "iPad (iOS)";
        } else if (lowerUa.contains("android")) {
            os = "Android";
        } else if (lowerUa.contains("linux")) {
            os = "Linux";
        }

        Matcher zrlogCtl = ZRLOGCTL_PATTERN.matcher(ua);
        if (zrlogCtl.find()) {
            browser = "ZrLogCtl";
            version = zrlogCtl.group(1);
        } else if (lowerUa.contains("micromessenger")) {
            browser = "WeChat";
            version = getVersion(ua, "MicroMessenger/(\\d+(\\.\\d+)*)");
        } else if (lowerUa.contains("edg/")) {
            browser = "Edge";
            version = getVersion(ua, "Edg/(\\d+(\\.\\d+)*)");
        } else if (lowerUa.contains("firefox")) {
            browser = "Firefox";
            version = getVersion(ua, "Firefox/(\\d+(\\.\\d+)*)");
        } else if (lowerUa.contains("chrome")) {
            browser = "Chrome";
            version = getVersion(ua, "Chrome/(\\d+(\\.\\d+)*)");
        } else if (lowerUa.contains("safari")) {
            browser = "Safari";
            version = getVersion(ua, "Version/(\\d+(\\.\\d+)*)");
            if ("Unknown".equals(version)) {
                version = getVersion(ua, "Safari/(\\d+(\\.\\d+)*)");
            }
        } else if (lowerUa.contains("msie") || lowerUa.contains("trident")) {
            browser = "Internet Explorer";
            version = getVersion(ua, "(?:MSIE |rv:)(\\d+(\\.\\d+)*)");
        }

        return new UserAgentInfo(os, browser, version, crawler);
    }

    private static String getVersion(String ua, String regex) {
        try {
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(ua);
            if (matcher.find()) {
                return matcher.group(1);
            }
        } catch (Exception e) {
            // ignore
        }
        return "Unknown";
    }

    private static boolean isCrawlerUserAgent(String ua) {
        return CRAWLER_PATTERN.matcher(ua).find();
    }
}

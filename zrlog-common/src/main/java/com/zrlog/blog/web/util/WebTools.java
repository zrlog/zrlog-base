package com.zrlog.blog.web.util;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.UrlEncodeUtils;
import com.hibegin.http.server.api.HttpRequest;
import com.zrlog.util.ZrLogUtil;
import java.util.Map;
import java.util.Objects;

/** 存放与Web相关的工具代码 */
public class WebTools {

    public static final String REAL_IP_HEADER = "X-Real-IP";

    /** 处理由于浏览器使用透明代理，或者是WebServer运行在诸如 nginx/apache 这类 HttpServer后面的情况，通过获取请求头真实IP地址 */
    public static String getRealIp(HttpRequest request) {
        String ip = null;
        // bae env
        if (ZrLogUtil.isBae() && header(request, "clientip") != null) {
            ip = header(request, "clientip");
        }
        if (EnvKit.isFaaSMode() && header(request, "cf-connecting-ip") != null) {
            ip = header(request, "cf-connecting-ip");
        }
        if (ip == null || ip.isEmpty()) {
            ip = header(request, "X-forwarded-for");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = header(request, "X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = header(request, "Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = header(request, "WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteHost();
        }
        return ip;
    }

    public static void putRealIpHeader(Map<String, String> headers, HttpRequest request) {
        if (headers == null || request == null) {
            return;
        }
        String realIp = firstIp(getRealIp(request));
        if (realIp == null || realIp.isEmpty() || "unknown".equalsIgnoreCase(realIp)) {
            return;
        }
        headers.put(REAL_IP_HEADER, realIp);
    }

    private static String header(HttpRequest request, String key) {
        String value = request.getHeader(key);
        if (value != null && !value.isEmpty()) {
            return value;
        }
        if (request.getHeaderMap() == null) {
            return value;
        }
        for (Map.Entry<String, String> entry : request.getHeaderMap().entrySet()) {
            String entryKey = entry.getKey();
            if (entryKey != null && entryKey.equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return value;
    }

    private static String firstIp(String value) {
        if (value == null) {
            return null;
        }
        int commaIndex = value.indexOf(',');
        if (commaIndex >= 0) {
            return value.substring(0, commaIndex).trim();
        }
        return value.trim();
    }

    public static String getHomeUrl(HttpRequest request) {
        if (Objects.equals("/", request.getContextPath())) {
            return "/";
        }
        return request.getContextPath() + "/";
    }

    public static String buildEncodedUrl(HttpRequest request, String url) {
        if (request == null) {
            return UrlEncodeUtils.encodeUrl(url);
        }
        if (url.startsWith("/")) {
            return UrlEncodeUtils.encodeUrl(getHomeUrl(request) + url.substring(1));
        }
        return UrlEncodeUtils.encodeUrl(getHomeUrl(request) + url);
    }

    public static String htmlEncode(String source) {
        if (source == null) {
            return "";
        }
        String html;
        StringBuilder buffer = new StringBuilder();
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            switch (c) {
                case '<':
                    buffer.append("&lt;");
                    break;
                case '>':
                    buffer.append("&gt;");
                    break;
                case '&':
                    buffer.append("&amp;");
                    break;
                case '"':
                    buffer.append("&quot;");
                    break;
                default:
                    buffer.append(c);
            }
        }
        html = buffer.toString();
        return html;
    }
}

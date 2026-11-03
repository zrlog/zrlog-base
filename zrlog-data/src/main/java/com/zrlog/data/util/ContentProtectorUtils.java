package com.zrlog.data.util;

import com.hibegin.common.util.StringUtils;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.dto.ArticleDetailDTO;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ContentProtectorUtils {

    private static final Map<String, String> LICENSE_NAMES = buildLicenseNames();

    private ContentProtectorUtils() {}

    private static Map<String, String> buildLicenseNames() {
        Map<String, String> licenseNames = new HashMap<>();
        licenseNames.put("ALL_RIGHTS_RESERVED", "版权所有");
        licenseNames.put("CC_BY_4_0", "CC BY 4.0");
        licenseNames.put("CC_BY_SA_4_0", "CC BY-SA 4.0");
        licenseNames.put("CC_BY_ND_4_0", "CC BY-ND 4.0");
        licenseNames.put("CC_BY_NC_4_0", "CC BY-NC 4.0");
        licenseNames.put("CC_BY_NC_SA_4_0", "CC BY-NC-SA 4.0");
        licenseNames.put("CC_BY_NC_ND_4_0", "CC BY-NC-ND 4.0");
        return Collections.unmodifiableMap(licenseNames);
    }

    public static String render(
            PublicWebSiteInfo webSite, ArticleDetailDTO article, String articleUrl) {
        if (Objects.isNull(webSite)
                || Objects.isNull(article)
                || !Objects.equals(webSite.getContent_protector_enabled(), true)) {
            return "";
        }
        String template = webSite.getContent_protector_template();
        if (StringUtils.isEmpty(template)) {
            template = WebSiteUtils.DEFAULT_CONTENT_PROTECTOR_TEMPLATE;
        }
        String licenseType = webSite.getContent_protector_license_type();
        if (StringUtils.isEmpty(licenseType) || !LICENSE_NAMES.containsKey(licenseType)) {
            licenseType = WebSiteUtils.DEFAULT_CONTENT_PROTECTOR_LICENSE_TYPE;
        }
        String author = article.getUserName();
        if (StringUtils.isEmpty(author)) {
            author = webSite.getAuthor();
        }
        return template.replace("{title}", escapeHtml(article.getTitle()))
                .replace("{url}", escapeHtml(articleUrl))
                .replace("{author}", escapeHtml(author))
                .replace("{site}", escapeHtml(webSite.getTitle()))
                .replace("{licenseType}", escapeHtml(licenseType))
                .replace("{licenseName}", escapeHtml(LICENSE_NAMES.get(licenseType)));
    }

    private static String escapeHtml(String source) {
        if (source == null) {
            return "";
        }
        StringBuilder buffer = new StringBuilder(source.length());
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
                case '\'':
                    buffer.append("&#39;");
                    break;
                default:
                    buffer.append(c);
                    break;
            }
        }
        return buffer.toString();
    }
}

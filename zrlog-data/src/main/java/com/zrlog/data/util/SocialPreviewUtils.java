package com.zrlog.data.util;

import com.hibegin.common.util.StringUtils;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.common.vo.SocialPreviewDTO;
import com.zrlog.data.dto.ArticleBasicDTO;
import com.zrlog.util.ParseUtil;

import java.util.StringJoiner;

public class SocialPreviewUtils {

    private static final int DESCRIPTION_MAX_LENGTH = 110;

    private SocialPreviewUtils() {
    }

    public static SocialPreviewDTO article(PublicWebSiteInfo webSite, ArticleBasicDTO article, String title,
                                           String url, String image) {
        SocialPreviewDTO preview = new SocialPreviewDTO();
        preview.setType("article");
        preview.setTitle(cleanText(title));
        preview.setDescription(resolveDescription(article, webSite));
        preview.setUrl(cleanText(url));
        preview.setSiteName(cleanText(webSite == null ? "" : webSite.getTitle()));
        preview.setImage(cleanText(image));
        preview.setTwitterCard(StringUtils.isEmpty(preview.getImage()) ? "summary" : "summary_large_image");
        preview.setAuthor(cleanText(resolveAuthor(webSite, article)));
        preview.setPublishedTime(cleanText(article == null ? "" : article.getFullReleaseTime()));
        preview.setModifiedTime(cleanText(article == null ? "" : article.getFullLastUpdateDate()));
        preview.setMetaHtml(renderMetaHtml(preview));
        return preview;
    }

    public static SocialPreviewDTO website(PublicWebSiteInfo webSite, String title, String description, String url,
                                           String image) {
        SocialPreviewDTO preview = new SocialPreviewDTO();
        preview.setType("website");
        preview.setTitle(cleanText(title));
        preview.setDescription(truncate(cleanText(description)));
        preview.setUrl(cleanText(url));
        preview.setSiteName(cleanText(webSite == null ? "" : webSite.getTitle()));
        preview.setImage(cleanText(image));
        preview.setTwitterCard(StringUtils.isEmpty(preview.getImage()) ? "summary" : "summary_large_image");
        preview.setAuthor(cleanText(webSite == null ? "" : webSite.getAuthor()));
        preview.setMetaHtml(renderMetaHtml(preview));
        return preview;
    }

    public static String renderMetaHtml(SocialPreviewDTO preview) {
        StringJoiner joiner = new StringJoiner("\n");
        addProperty(joiner, "og:type", preview.getType());
        addProperty(joiner, "og:title", preview.getTitle());
        addProperty(joiner, "og:description", preview.getDescription());
        addProperty(joiner, "og:url", preview.getUrl());
        addProperty(joiner, "og:site_name", preview.getSiteName());
        addProperty(joiner, "og:image", preview.getImage());
        addName(joiner, "twitter:card", preview.getTwitterCard());
        addName(joiner, "twitter:title", preview.getTitle());
        addName(joiner, "twitter:description", preview.getDescription());
        addName(joiner, "twitter:image", preview.getImage());
        if ("article".equals(preview.getType())) {
            addProperty(joiner, "article:author", preview.getAuthor());
            addProperty(joiner, "article:published_time", preview.getPublishedTime());
            addProperty(joiner, "article:modified_time", preview.getModifiedTime());
        }
        return joiner.toString();
    }

    private static void addProperty(StringJoiner joiner, String property, String content) {
        if (StringUtils.isNotEmpty(content)) {
            joiner.add("<meta property=\"" + escapeAttribute(property) + "\" content=\"" + escapeAttribute(content) + "\"/>");
        }
    }

    private static void addName(StringJoiner joiner, String name, String content) {
        if (StringUtils.isNotEmpty(content)) {
            joiner.add("<meta name=\"" + escapeAttribute(name) + "\" content=\"" + escapeAttribute(content) + "\"/>");
        }
    }

    private static String resolveDescription(ArticleBasicDTO article, PublicWebSiteInfo webSite) {
        if (article != null) {
            String digest = cleanText(article.getDigest());
            if (StringUtils.isNotEmpty(digest)) {
                return truncate(digest);
            }
            String plainContent = cleanText(article.getPlain_content());
            if (StringUtils.isNotEmpty(plainContent)) {
                return truncate(plainContent);
            }
            String content = cleanText(article.getContent());
            if (StringUtils.isNotEmpty(content)) {
                return truncate(content);
            }
        }
        return truncate(cleanText(webSite == null ? "" : webSite.getDescription()));
    }

    private static String resolveAuthor(PublicWebSiteInfo webSite, ArticleBasicDTO article) {
        if (article != null && StringUtils.isNotEmpty(article.getUserName())) {
            return article.getUserName();
        }
        return webSite == null ? "" : webSite.getAuthor();
    }

    public static String cleanText(String text) {
        if (StringUtils.isEmpty(text)) {
            return "";
        }
        return ParseUtil.removeHtmlElement(text).replaceAll("\\s+", " ").trim();
    }

    public static String truncate(String text) {
        if (StringUtils.isEmpty(text) || text.length() <= DESCRIPTION_MAX_LENGTH) {
            return text;
        }
        return text.substring(0, DESCRIPTION_MAX_LENGTH).trim() + "...";
    }

    private static String escapeAttribute(String source) {
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

package com.zrlog.data.util;

import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.common.vo.SocialPreviewDTO;
import com.zrlog.data.dto.ArticleBasicDTO;
import com.zrlog.data.dto.ArticleDetailDTO;
import org.junit.Test;

import static org.junit.Assert.*;

public class SocialPreviewAndContentProtectorUtilsTest {

    @Test
    public void shouldRenderArticleSocialPreviewWithCleanAndEscapedMeta() {
        PublicWebSiteInfo webSite = new PublicWebSiteInfo();
        webSite.setTitle("ZrLog & \"Site\"");
        webSite.setAuthor("Site Author");
        ArticleBasicDTO article = new ArticleBasicDTO();
        article.setDigest("<p>Digest & summary</p>\n with   spaces");
        article.setUserName("Article Author");
        article.setFullReleaseTime("2026-01-01T10:00:00+08:00");
        article.setFullLastUpdateDate("2026-01-02T10:00:00+08:00");

        SocialPreviewDTO preview = SocialPreviewUtils.article(webSite, article, "<b>Hello</b> \"World\"",
                "https://example.com/post?a=1&b=2", "https://example.com/cover.png");

        assertEquals("article", preview.getType());
        assertEquals("Hello \"World\"", preview.getTitle());
        assertEquals("Digest & summary with spaces", preview.getDescription());
        assertEquals("summary_large_image", preview.getTwitterCard());
        assertEquals("Article Author", preview.getAuthor());
        assertTrue(preview.getMetaHtml().contains("property=\"og:title\" content=\"Hello &quot;World&quot;\""));
        assertTrue(preview.getMetaHtml().contains("property=\"og:site_name\" content=\"ZrLog &amp; &quot;Site&quot;\""));
        assertTrue(preview.getMetaHtml().contains("property=\"article:published_time\""));
        assertFalse(preview.getMetaHtml().contains("<b>"));
    }

    @Test
    public void shouldRenderWebsiteSocialPreviewWithFallbackCardAndTruncatedDescription() {
        String description = "x".repeat(120);

        SocialPreviewDTO preview = SocialPreviewUtils.website(null, "Home", description,
                "https://example.com", "");

        assertEquals("website", preview.getType());
        assertEquals("summary", preview.getTwitterCard());
        assertEquals(113, preview.getDescription().length());
        assertTrue(preview.getDescription().endsWith("..."));
        assertFalse(preview.getMetaHtml().contains("og:image"));
    }

    @Test
    public void shouldResolveArticleDescriptionFallbacks() {
        PublicWebSiteInfo webSite = new PublicWebSiteInfo();
        webSite.setTitle("Site");
        webSite.setAuthor("Site Author");
        webSite.setDescription("<p>Site description</p>");
        ArticleBasicDTO plainArticle = new ArticleBasicDTO();
        plainArticle.setPlain_content("<p>Plain content</p>");
        ArticleBasicDTO htmlArticle = new ArticleBasicDTO();
        htmlArticle.setContent("<p>HTML content</p>");

        SocialPreviewDTO plainPreview = SocialPreviewUtils.article(webSite, plainArticle, "Plain",
                "https://example.com/plain", "");
        SocialPreviewDTO htmlPreview = SocialPreviewUtils.article(webSite, htmlArticle, "HTML",
                "https://example.com/html", "");
        SocialPreviewDTO sitePreview = SocialPreviewUtils.article(webSite, null, "Site",
                "https://example.com/site", "");

        assertEquals("Plain content", plainPreview.getDescription());
        assertEquals("HTML content", htmlPreview.getDescription());
        assertEquals("Site description", sitePreview.getDescription());
        assertEquals("Site Author", sitePreview.getAuthor());
        assertEquals("summary", plainPreview.getTwitterCard());
        assertTrue(sitePreview.getMetaHtml().contains("article:author"));
    }

    @Test
    public void shouldCleanAndTruncateEmptyTextSafely() {
        assertEquals("", SocialPreviewUtils.cleanText(null));
        assertEquals("", SocialPreviewUtils.cleanText(""));
        assertEquals("", SocialPreviewUtils.truncate(""));
        assertEquals("short", SocialPreviewUtils.truncate("short"));
    }

    @Test
    public void shouldEscapeAllMetaAttributeCharacters() {
        SocialPreviewDTO preview = new SocialPreviewDTO();
        preview.setType("website");
        preview.setTitle("<Title> & \"Quote\" 'Single'");
        preview.setDescription("Description");
        preview.setUrl("https://example.com/?a=<b>&q='x'");
        preview.setSiteName("Site");
        preview.setTwitterCard("summary");

        String html = SocialPreviewUtils.renderMetaHtml(preview);

        assertTrue(html.contains("&lt;Title&gt; &amp; &quot;Quote&quot; &#39;Single&#39;"));
        assertTrue(html.contains("https://example.com/?a=&lt;b&gt;&amp;q=&#39;x&#39;"));
    }

    @Test
    public void shouldRenderContentProtectorWithEscapedCustomTemplate() {
        PublicWebSiteInfo webSite = new PublicWebSiteInfo();
        webSite.setContent_protector_enabled(true);
        webSite.setContent_protector_license_type("CC_BY_NC_4_0");
        webSite.setContent_protector_template("{title}|{url}|{author}|{site}|{licenseType}|{licenseName}");
        webSite.setTitle("Site <Name>");
        webSite.setAuthor("Site Author");
        ArticleDetailDTO article = new ArticleDetailDTO();
        article.setTitle("Title <A> & \"B\"");
        article.setUserName("Alice 'Admin'");

        String html = ContentProtectorUtils.render(webSite, article,
                "https://example.com/post?a=1&b=2");

        assertEquals("Title &lt;A&gt; &amp; &quot;B&quot;|https://example.com/post?a=1&amp;b=2|"
                + "Alice &#39;Admin&#39;|Site &lt;Name&gt;|CC_BY_NC_4_0|CC BY-NC 4.0", html);
    }

    @Test
    public void shouldSkipDisabledContentProtectorAndUseDefaultLicenseFallback() {
        PublicWebSiteInfo disabledWebSite = new PublicWebSiteInfo();
        ArticleDetailDTO article = new ArticleDetailDTO();
        article.setTitle("Title");
        assertEquals("", ContentProtectorUtils.render(disabledWebSite, article, "https://example.com/post"));

        PublicWebSiteInfo webSite = new PublicWebSiteInfo();
        webSite.setContent_protector_enabled(true);
        webSite.setContent_protector_license_type("INVALID");
        webSite.setContent_protector_template("");
        webSite.setAuthor("Fallback Author");
        ArticleDetailDTO fallbackArticle = new ArticleDetailDTO();
        fallbackArticle.setTitle("Fallback");

        String html = ContentProtectorUtils.render(webSite, fallbackArticle, "https://example.com/post");

        assertTrue(html.contains("data-license=\"ALL_RIGHTS_RESERVED\""));
        assertTrue(html.contains("本文作者：Fallback Author"));
        assertTrue(html.contains("版权声明：版权所有"));
    }
}

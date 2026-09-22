package com.zrlog.data.util;

import com.zrlog.common.Constants;
import com.zrlog.common.vo.PublicWebSiteInfo;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WebSiteUtilsTest {

    @Test
    public void shouldFillMissingWebsiteDefaults() {
        PublicWebSiteInfo info = WebSiteUtils.fillDefaultInfo(new PublicWebSiteInfo());

        assertEquals(Long.valueOf(10), info.getRows());
        assertEquals(Long.valueOf(WebSiteUtils.DEFAULT_ARTICLE_DIGEST_LENGTH),
                info.getArticle_auto_digest_length());
        assertEquals(Long.valueOf(24 * 60), info.getSession_timeout());
        assertEquals(WebSiteUtils.DEFAULT_CONTENT_PROTECTOR_LICENSE_TYPE,
                info.getContent_protector_license_type());
        assertEquals(WebSiteUtils.DEFAULT_COLOR_PRIMARY_COLOR, info.getAdmin_color_primary());
        assertEquals("default", info.getAdmin_theme());
        assertEquals(Constants.DEFAULT_LANGUAGE, info.getLanguage());
        assertEquals(Constants.getDefaultTemplatePath(), info.getTemplate());
        assertEquals("comment", info.getComment_plugin_name());
        assertFalse(info.getAdmin_darkMode());
        assertFalse(info.getDisable_comment_status());
        assertFalse(info.getGenerator_html_status());
        assertFalse(info.getArticle_thumbnail_status());
        assertFalse(info.getAdmin_compactMode());
        assertFalse(info.getContent_protector_enabled());
    }

    @Test
    public void shouldPreserveExplicitWebsiteDefaultsAndUseChangyanPlugin() {
        PublicWebSiteInfo info = new PublicWebSiteInfo();
        info.setRows(20L);
        info.setArticle_auto_digest_length(300L);
        info.setSession_timeout(30L);
        info.setAdmin_darkMode(true);
        info.setDisable_comment_status(true);
        info.setGenerator_html_status(true);
        info.setArticle_thumbnail_status(true);
        info.setAdmin_compactMode(true);
        info.setContent_protector_enabled(true);
        info.setContent_protector_license_type("CC_BY_4_0");
        info.setContent_protector_template("template");
        info.setAdmin_color_primary("#000");
        info.setAdmin_theme("compact");
        info.setLanguage("en_US");
        info.setTemplate("custom");
        info.setChangyan_status("true");

        PublicWebSiteInfo filled = WebSiteUtils.fillDefaultInfo(info);

        assertEquals(Long.valueOf(20), filled.getRows());
        assertEquals(Long.valueOf(300), filled.getArticle_auto_digest_length());
        assertEquals(Long.valueOf(30), filled.getSession_timeout());
        assertTrue(filled.getAdmin_darkMode());
        assertTrue(filled.getDisable_comment_status());
        assertTrue(filled.getGenerator_html_status());
        assertTrue(filled.getArticle_thumbnail_status());
        assertTrue(filled.getAdmin_compactMode());
        assertTrue(filled.getContent_protector_enabled());
        assertEquals("CC_BY_4_0", filled.getContent_protector_license_type());
        assertEquals("template", filled.getContent_protector_template());
        assertEquals("#000", filled.getAdmin_color_primary());
        assertEquals("compact", filled.getAdmin_theme());
        assertEquals("en_US", filled.getLanguage());
        assertEquals("custom", filled.getTemplate());
        assertEquals("changyan", filled.getComment_plugin_name());
    }
}

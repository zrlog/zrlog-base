package com.zrlog.model;

import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.dto.FaviconBase64DTO;
import com.zrlog.data.support.InMemoryZrLogDatabase;
import com.zrlog.data.support.InMemoryZrLogDatabase.DatabaseType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class WebSiteDatabaseTest {

    @Parameterized.Parameters(name = "{0}")
    public static DatabaseType[] databases() {
        return DatabaseType.values();
    }

    private final DatabaseType databaseType;

    public WebSiteDatabaseTest(DatabaseType databaseType) {
        this.databaseType = databaseType;
    }

    @Test
    public void shouldInsertUpdateAndReadWebsiteValuesUsingInstallSchema() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open(databaseType)) {
            WebSite webSite = new WebSite();

            assertTrue(webSite.updateByKV("title", "ZrLog"));
            assertTrue(webSite.updateByKV("rows", "20"));
            assertTrue(webSite.updateByKV("title", "ZrLog Updated"));

            Map<String, Object> values = webSite.getWebSiteByNameIn(List.of("title", "rows", "missing"));

            assertEquals("ZrLog Updated", values.get("title"));
            assertEquals("20", values.get("rows"));
            assertNull(values.get("missing"));
            assertEquals("ZrLog Updated", webSite.getStringValueByName("title"));
            assertEquals("", webSite.getStringValueByName("missing"));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldReadPublicInfoTemplateConfigAndFaviconValues() throws Exception {
        try (InMemoryZrLogDatabase ignored = InMemoryZrLogDatabase.open(databaseType)) {
            WebSite webSite = new WebSite();
            webSite.updateByKV("title", "ZrLog");
            webSite.updateByKV("host", "https://example.com");
            webSite.updateByKV("language", "en_US");
            webSite.updateByKV("generator_html_status", "1");
            webSite.updateByKV("favicon_ico_base64", "ico");
            webSite.updateByKV("favicon_png_pwa_192_base64", "png192");
            webSite.updateByKV("favicon_png_pwa_512_base64", "png512");

            Map<String, Object> config = webSite.updateTemplateConfigMap("default",
                    Map.of("color", "blue", "nested", Map.of("enabled", true)));
            PublicWebSiteInfo publicInfo = webSite.getPublicWebSite();
            FaviconBase64DTO favicon = webSite.faviconBase64DTO();
            Map<String, Object> storedConfig = webSite.getTemplateConfigMap("default");

            assertEquals("blue", config.get("color"));
            assertEquals("ZrLog", publicInfo.getTitle());
            assertEquals("https://example.com", publicInfo.getHost());
            assertEquals("en_US", publicInfo.getLanguage());
            assertEquals("ico", favicon.getFavicon_ico_base64());
            assertEquals("png192", favicon.getFavicon_png_pwa_192_base64());
            assertEquals("png512", favicon.getFavicon_png_pwa_512_base64());
            assertEquals("blue", storedConfig.get("color"));
            assertEquals(Boolean.TRUE, ((Map<String, Object>) storedConfig.get("nested")).get("enabled"));
        }
    }
}

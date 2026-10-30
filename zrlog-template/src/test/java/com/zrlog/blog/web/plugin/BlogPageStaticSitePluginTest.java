package com.zrlog.blog.web.plugin;

import com.hibegin.http.server.ApplicationContext;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.hibegin.http.server.api.Interceptor;
import com.hibegin.http.server.config.RequestConfig;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.business.plugin.StaticGenerationBudget;
import com.zrlog.business.plugin.StaticSitePlugin;
import com.zrlog.business.plugin.StaticSiteRenderer;
import com.zrlog.common.vo.PublicWebSiteInfo;
import com.zrlog.data.dto.FaviconBase64DTO;
import com.zrlog.util.ExecutionDeadline;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.ExecutorService;

import static org.junit.Assert.*;

public class BlogPageStaticSitePluginTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void boundedGenerationCopiesTemplateAndRendersPagesBeforeVersion() throws Exception {
        withPlugin((plugin, site) -> {
            assertTrue(plugin.startWithinBudget(budget()));
            assertEquals("body{}", Files.readString(plugin.getCacheFile("/include/templates/fixture/css/main.css").toPath()));
            assertEquals("icon", Files.readString(plugin.getCacheFile("/favicon.ico").toPath()));
            assertEquals(site.getRobotRuleContent(), Files.readString(plugin.getCacheFile("/robots.txt").toPath()));
            assertEquals("/* /error/404.html 404", Files.readString(plugin.getCacheFile("/_redirects").toPath()));
            assertTrue(plugin.getCacheFile("/index.html").isFile());
            assertTrue(plugin.getCacheFile("/article.html").isFile());
            assertTrue(plugin.getCacheFile("/error/404.html").isFile());
            assertTrue(plugin.getHandleStatusPageMap().values().stream().allMatch(state -> state == StaticSitePlugin.HandleState.HANDLED));
            assertEquals(plugin.getSiteVersion(), Files.readString(plugin.getCacheFile(plugin.getVersionFileName()).toPath()));
            assertFalse(plugin.isStarted());
        });
    }

    @Test
    public void missingTemplateCannotPublishSuccessfulVersion() throws Exception {
        withPlugin((plugin, site) -> {
            site.setTemplate("/include/templates/missing");
            assertThrows(IllegalStateException.class, () -> plugin.startWithinBudget(budget()));
            assertFalse(plugin.getCacheFile(plugin.getVersionFileName()).exists());
            assertFalse(plugin.isStarted());
        });
    }

    @Test
    public void revokedGenerationReleasesItsLockAndDoesNotRenderPages() throws Exception {
        withPlugin((plugin, site) -> {
            StaticGenerationBudget budget = new StaticGenerationBudget(ExecutionDeadline.afterMillis(5000), () -> {
                if (!plugin.getCacheFiles().isEmpty()) throw new IllegalStateException("revoked");
            });
            assertThrows(IllegalStateException.class, () -> plugin.startWithinBudget(budget));
            assertFalse(plugin.getCacheFile("/index.html").exists());
            assertFalse(plugin.getCacheFile(plugin.getVersionFileName()).exists());
            assertFalse(plugin.isStarted());
        });
    }

    private StaticGenerationBudget budget() {
        return new StaticGenerationBudget(ExecutionDeadline.afterMillis(5000), () -> {});
    }

    private void withPlugin(Scenario scenario) throws Exception {
        String previous = System.getProperty("sws.root.path");
        BlogPageStaticSitePlugin plugin = null;
        try {
            System.setProperty("sws.root.path", folder.getRoot().getAbsolutePath());
            Path template = PathUtil.getStaticFile("/include/templates/fixture").toPath();
            Files.createDirectories(template.resolve("css"));
            Files.writeString(template.resolve("template.properties"), "name=fixture\ndigest=fixture\nstaticResource=css\n");
            Files.writeString(template.resolve("css/main.css"), "body{}");
            PublicWebSiteInfo site = new PublicWebSiteInfo();
            site.setGenerator_html_status(true);
            site.setHost("blog.example.com");
            site.setTemplate("/include/templates/fixture");
            site.setRobotRuleContent("User-agent: *\nAllow: /");
            FaviconBase64DTO favicon = new FaviconBase64DTO();
            favicon.setGenerator_html_status("true");
            favicon.setFavicon_ico_base64(Base64.getEncoder().encodeToString("icon".getBytes(StandardCharsets.UTF_8)));
            ServerConfig server = new ServerConfig();
            server.setPort(18080);
            server.setContextPath("");
            server.addInterceptor(Pages.class);
            ApplicationContext context = new ApplicationContext(server);
            context.init();
            plugin = new BlogPageStaticSitePlugin(new StaticSiteRenderer(new RequestConfig(), new ResponseConfig(), context),
                    () -> site, "", () -> favicon);
            // Any accidental use of the legacy worker pool now rejects immediately.
            ((ExecutorService) plugin.getExecutorService()).shutdownNow();
            scenario.run(plugin, site);
        } finally {
            if (plugin != null) ((ExecutorService) plugin.getExecutorService()).shutdownNow();
            if (previous == null) System.clearProperty("sws.root.path");
            else System.setProperty("sws.root.path", previous);
        }
    }

    private interface Scenario { void run(BlogPageStaticSitePlugin plugin, PublicWebSiteInfo site) throws Exception; }

    public static class Pages implements Interceptor {
        @Override public boolean doInterceptor(HttpRequest request, HttpResponse response) {
            Object value = request.getAttr().get(StaticGenerationBudget.REQUEST_ATTRIBUTE);
            if (!(value instanceof StaticGenerationBudget)) throw new IllegalStateException("Static generation lost its budget");
            ((StaticGenerationBudget) value).check();
            if (request.getUri().equals("/error/404.html"))
                response.write(new ByteArrayInputStream("missing".getBytes(StandardCharsets.UTF_8)), 404);
            else response.renderHtmlStr("<a href=\"/article.html\">article</a>");
            return false;
        }
    }
}

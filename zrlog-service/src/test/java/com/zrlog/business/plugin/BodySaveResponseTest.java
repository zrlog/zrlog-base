package com.zrlog.business.plugin;

import com.hibegin.http.HttpVersion;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.config.ResponseConfig;
import com.hibegin.http.server.config.ServerConfig;
import com.zrlog.business.plugin.type.StaticSiteType;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BodySaveResponseTest {


    @Test
    public void shouldPersistSuccessfulTextBodyToStaticCacheFile() throws Exception {
        File cacheFile = tempFile("article.html");

        try (BodySaveResponse response = new BodySaveResponse(request("/article.html"), new ResponseConfig(), true, new TestStaticSitePlugin(cacheFile))) {
            response.renderText("zrlog");

            assertEquals(cacheFile.getCanonicalFile(), response.getCacheFile().getCanonicalFile());
        }
        assertEquals("zrlog", Files.readString(cacheFile.toPath()));
    }

    @Test
    public void shouldAppendHtmlSuffixForAdminExtensionlessUri() throws Exception {
        File cacheFile = tempFile("admin-page");

        try (BodySaveResponse response = new BodySaveResponse(request("/admin/dashboard"), new ResponseConfig(), true, new TestStaticSitePlugin(cacheFile))) {
            response.renderText("admin");

            assertTrue(response.getCacheFile().getName().endsWith(".html"));
        }
        assertEquals("admin", Files.readString(new File(cacheFile + ".html").toPath()));
    }

    @Test
    public void shouldSkipNonSuccessBodiesWhenSuccessStatusIsRequired() throws Exception {
        File cacheFile = tempFile("missing.html");

        try (BodySaveResponse response = new BodySaveResponse(request("/missing.html"), new ResponseConfig(), true, new TestStaticSitePlugin(cacheFile))) {
            response.write(new ByteArrayInputStream("missing".getBytes(StandardCharsets.UTF_8)), 404);

            assertNull(response.getCacheFile());
        }
        assertFalse(cacheFile.exists());
    }

    @Test
    public void shouldAllowErrorBodyWhenSuccessStatusIsNotRequired() throws Exception {
        File cacheFile = tempFile("error.html");

        try (BodySaveResponse response = new BodySaveResponse(request("/error.html"), new ResponseConfig(), false, new TestStaticSitePlugin(cacheFile))) {
            response.write(new ByteArrayInputStream("error".getBytes(StandardCharsets.UTF_8)), 500);

            assertNull(response.getCacheFile());
        }
        assertEquals("error", Files.readString(cacheFile.toPath()));
    }

    @Test
    public void shouldExportNotFoundAndLegacySuccessButRejectOtherErrors() throws Exception {
        for (int status : new int[]{404, 200, 500, 302}) {
            File cacheFile = tempFile("error-" + status + ".html");
            try (BodySaveResponse response = new BodySaveResponse(request("/error/404.html"),
                    new ResponseConfig(), true, 404, new TestStaticSitePlugin(cacheFile))) {
                response.write(new ByteArrayInputStream("missing page".getBytes(StandardCharsets.UTF_8)), status);
                if (status == 404 || status == 200) assertEquals(cacheFile, response.getCacheFile());
                else assertNull(response.getCacheFile());
            }
            assertEquals(status == 404 || status == 200, cacheFile.exists());
            if (status == 404 || status == 200) assertEquals("missing page", Files.readString(cacheFile.toPath()));
        }
    }

    private static File tempFile(String name) throws Exception {
        File dir = Files.createTempDirectory("zrlog-body-save").toFile();
        File file = new File(dir, name);
        Files.writeString(file.toPath(), "stale", StandardCharsets.UTF_8);
        return file;
    }

    private static HttpRequest request(String uri) {
        ServerConfig serverConfig = new ServerConfig()
                .setApplicationName("zrlog-test")
                .setApplicationVersion("test");
        serverConfig.setContextPath("");
        return (HttpRequest) Proxy.newProxyInstance(
                BodySaveResponseTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getUri".equals(method.getName())) {
                        return uri;
                    }
                    if ("getContextPath".equals(method.getName())) {
                        return "";
                    }
                    if ("getScheme".equals(method.getName())) {
                        return "https";
                    }
                    if ("getServerConfig".equals(method.getName())) {
                        return serverConfig;
                    }
                    if ("getHttpVersion".equals(method.getName())) {
                        return HttpVersion.HTTP_1_1;
                    }
                    if ("getHeader".equals(method.getName())) {
                        return null;
                    }
                    if ("getCookies".equals(method.getName())) {
                        return null;
                    }
                    if ("getHandler".equals(method.getName())) {
                        return null;
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }

    private static class TestStaticSitePlugin implements StaticSitePlugin {
        private final com.zrlog.common.vo.PublicWebSiteInfo site = new com.zrlog.common.vo.PublicWebSiteInfo();
        @Override public com.zrlog.common.vo.PublicWebSiteInfo getSiteInfo() { return site; }


        private final File cacheFile;
        private final Map<String, HandleState> handleStatusPageMap = new HashMap<>();
        private final Lock parseLock = new ReentrantLock();
        private final List<File> cacheFiles = new ArrayList<>();

        TestStaticSitePlugin(File cacheFile) {
            this.cacheFile = cacheFile;
        }

        @Override
        public File loadCacheFile(HttpRequest request) {
            return cacheFile;
        }

        @Override
        public String getVersionFileName() {
            return "version.txt";
        }

        @Override
        public String getDbCacheKey() {
            return "static.version";
        }

        @Override
        public String getContextPath() {
            return "";
        }

        @Override
        public String getDefaultLang() {
            return "zh_CN";
        }

        @Override
        public Map<String, HandleState> getHandleStatusPageMap() {
            return handleStatusPageMap;
        }

        @Override
        public Lock getParseLock() {
            return parseLock;
        }

        @Override
        public Executor getExecutorService() {
            return Runnable::run;
        }

        @Override
        public List<File> getCacheFiles() {
            return cacheFiles;
        }

        @Override
        public StaticSiteType getType() {
            return StaticSiteType.BLOG;
        }

        @Override
        public boolean start() {
            return true;
        }

        @Override
        public boolean isStarted() {
            return true;
        }

        @Override
        public boolean stop() {
            return true;
        }

        @Override
        public void doFetch(StaticSiteRenderer renderer) {
        }
    }
}

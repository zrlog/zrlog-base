package com.zrlog.business.template.util;

import com.hibegin.http.server.util.PathUtil;
import com.zrlog.util.StaticFileCacheUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class TemplateDownloadUtilsTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldRefreshStaticFileTagAfterTemplateInstallation() throws Exception {
        String previousRootPath = System.getProperty("sws.root.path");
        try {
            System.setProperty("sws.root.path", temporaryFolder.getRoot().getAbsolutePath());
            File cssFile = PathUtil.getStaticFile("/include/templates/cache-theme/css/style.css");
            assertTrue(cssFile.getParentFile().mkdirs());
            Files.writeString(cssFile.toPath(), "old-content", StandardCharsets.UTF_8);
            StaticFileCacheUtils cacheUtils = StaticFileCacheUtils.getInstance();
            cacheUtils.refreshCacheFileMap();
            assertTrue(cacheUtils.isCacheableByRequest("/include/templates/cache-theme/css/style.css"));
            String oldTag = cacheUtils.getFileFlagFirstByCache("/include/templates/cache-theme/css/style.css");

            File zipFile = temporaryFolder.newFile("cache-theme.zip");
            try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(zipFile.toPath()))) {
                output.putNextEntry(new ZipEntry("css/style.css"));
                output.write("new-content".getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }

            TemplateDownloadUtils.installByZipFile(zipFile, "/include/templates/cache-theme");

            assertTrue(cacheUtils.isCacheableByRequest("/include/templates/cache-theme/css/style.css"));
            String newTag = cacheUtils.getFileFlagFirstByCache("/include/templates/cache-theme/css/style.css");
            assertNotEquals(oldTag, newTag);
        } finally {
            if (previousRootPath == null) {
                System.clearProperty("sws.root.path");
            } else {
                System.setProperty("sws.root.path", previousRootPath);
            }
            StaticFileCacheUtils.getInstance().refreshCacheFileMap();
        }
    }
}

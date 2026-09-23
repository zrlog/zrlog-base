package com.zrlog.util;

import org.junit.Test;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class BlogBuildInfoUtilTest {

    @Test
    public void shouldParseCanonicalAndIsoBuildTimes() throws Exception {
        Instant expected = Instant.parse("2026-09-05T05:26:19Z");

        assertEquals(expected, BlogBuildInfoUtil.parseBuildTime(
                "2026-09-05 13:26:19+08:00").toInstant());
        assertEquals(expected, BlogBuildInfoUtil.parseBuildTime(
                "2026-09-05T13:26:19+08:00").toInstant());
    }

    @Test
    public void shouldLoadRemainingPropertiesWhenBuildTimeIsInvalid() {
        Properties properties = new Properties();
        properties.setProperty("buildId", "abcdef0");
        properties.setProperty("version", "4.0.0-SNAPSHOT");
        properties.setProperty("buildTime", "not-a-build-time");
        properties.setProperty("runMode", "PREVIEW");
        properties.setProperty("mirrorWebSite", "https://preview.zrlog.com/");
        properties.setProperty("fileArch", "Linux-amd64");
        properties.setProperty("runtimeType", "native");
        properties.setProperty("packageType", "deb");
        properties.setProperty("updateVersionJsonFilename", "last.Linux-amd64.deb.version.json");

        BlogBuildInfoUtil.BuildInfo buildInfo = BlogBuildInfoUtil.loadBuildInfo(properties);

        assertEquals("abcdef0", buildInfo.buildId);
        assertEquals("4.0.0-SNAPSHOT", buildInfo.version);
        assertNotNull(buildInfo.time);
        assertEquals("PREVIEW", buildInfo.runMode);
        assertEquals("https://preview.zrlog.com", buildInfo.resourceDownloadUrl);
        assertEquals("Linux-amd64", buildInfo.fileArch);
        assertEquals("native", buildInfo.runtimeType);
        assertEquals("deb", buildInfo.packageType);
        assertEquals("last.Linux-amd64.deb.version.json", buildInfo.updateVersionJsonFilename);
    }

    @Test
    public void shouldExposeBuildPropertiesSnapshot() {
        Properties properties = BlogBuildInfoUtil.getBlogProp();

        assertEquals(BlogBuildInfoUtil.getVersion(), properties.get("version"));
        assertEquals(BlogBuildInfoUtil.getBuildId(), properties.get("buildId"));
        assertEquals(BlogBuildInfoUtil.getRunMode(), properties.get("runMode"));
        assertEquals(new SimpleDateFormat("yyyy-MM-dd").format(BlogBuildInfoUtil.getTime()),
                properties.get("buildTime"));
    }

    @Test
    public void shouldFormatVersionInformation() {
        assertEquals(BlogBuildInfoUtil.getVersion() + " - " + BlogBuildInfoUtil.getBuildId(),
                BlogBuildInfoUtil.getVersionShortInfo());
        assertTrue(BlogBuildInfoUtil.getVersionInfo().startsWith(BlogBuildInfoUtil.getVersionShortInfo()));
        assertTrue(BlogBuildInfoUtil.getVersionInfoFull().startsWith(BlogBuildInfoUtil.getVersionShortInfo()));
        assertTrue(BlogBuildInfoUtil.getVersionInfoFull().contains(":"));
    }

    @Test
    public void shouldExposeRunModeAndPackageDefaults() {
        assertNotNull(BlogBuildInfoUtil.getFileArch());
        assertNotNull(BlogBuildInfoUtil.getRuntimeType());
        assertNotNull(BlogBuildInfoUtil.getPackageType());
        assertNotNull(BlogBuildInfoUtil.getUpdateVersionJsonFilename());
        assertFalse(BlogBuildInfoUtil.getResourceDownloadUrl().endsWith("/"));

        String runMode = BlogBuildInfoUtil.getRunMode();
        assertEquals("RELEASE".equalsIgnoreCase(runMode), BlogBuildInfoUtil.isRelease());
        assertEquals("PREVIEW".equalsIgnoreCase(runMode), BlogBuildInfoUtil.isPreview());
        assertEquals("DEV".equalsIgnoreCase(runMode), BlogBuildInfoUtil.isDev());
    }
}

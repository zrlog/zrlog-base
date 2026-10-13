package com.zrlog.common.updater;

import com.zrlog.common.vo.Version;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class PackageDownloadSourceTest {
    @Test
    public void selectsSameVerifiedBytesWithoutFallback() {
        Version version = new Version();
        version.setZipDownloadUrl("https://dl.example/release/app.zip");
        assertEquals(version.getZipDownloadUrl(), PackageDownloadSource.select(version, false, "dl"));
        assertThrows(IllegalStateException.class, () -> PackageDownloadSource.select(version, false, "github"));
        Version.DownloadSource dl = new Version.DownloadSource(); dl.id = "dl"; dl.url = version.getZipDownloadUrl();
        Version.DownloadSource github = new Version.DownloadSource(); github.id = "github";
        github.url = "https://github.com/example/app/releases/download/v4.0.0/app.zip";
        version.setZipSources(List.of(dl, github));
        assertThrows(IllegalStateException.class, () -> PackageDownloadSource.select(version, false, "github"));
        version.setZipSha256("a".repeat(64)); version.setZipFileSize(123);
        assertEquals(github.url, PackageDownloadSource.select(version, false, "github"));
        assertEquals(dl.url, PackageDownloadSource.select(version, false, "dl"));
        assertEquals("a".repeat(64), version.getZipSha256());
        assertEquals(123, version.getZipFileSize());
        version.setZipSources(List.of(dl));
        assertThrows(IllegalStateException.class, () -> PackageDownloadSource.select(version, false, "github"));
        version.setZipSources(List.of(dl, dl));
        assertThrows(IllegalArgumentException.class, () -> PackageDownloadSource.select(version, false, "dl"));
    }
}

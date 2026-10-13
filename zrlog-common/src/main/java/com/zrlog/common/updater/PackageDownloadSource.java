package com.zrlog.common.updater;

import com.zrlog.common.vo.Version;

import java.net.URI;
import java.util.List;

/** Select a published location of the same bytes; checksums and lengths remain on Version. */
public final class PackageDownloadSource {
    private PackageDownloadSource() { }

    public static String select(Version version, boolean war, String source) {
        String selected = source == null ? "dl" : source;
        if (!"dl".equals(selected) && !"github".equals(selected)) {
            throw new IllegalArgumentException("Invalid upgrade package source");
        }
        List<Version.DownloadSource> locations = war ? version.getWarSources() : version.getZipSources();
        if (locations == null || locations.isEmpty()) {
            if ("dl".equals(selected)) { return war ? version.getWarDownloadUrl() : version.getZipDownloadUrl(); }
            throw new IllegalStateException("The selected package source is not published in this manifest");
        }
        String checksum = war ? version.getWarSha256() : version.getZipSha256();
        long size = war ? version.getWarFileSize() : version.getZipFileSize();
        if (checksum == null || !checksum.matches("[0-9a-fA-F]{64}") || size <= 0) {
            throw new IllegalStateException("Package sources require SHA-256 and file size");
        }
        Version.DownloadSource match = null;
        for (Version.DownloadSource location : locations) {
            if (location != null && selected.equals(location.id)) {
                if (match != null) { throw new IllegalArgumentException("Ambiguous package source"); }
                match = location;
            }
        }
        if (match == null || match.url == null) {
            throw new IllegalStateException("The selected package source is not published in this manifest");
        }
        URI uri = URI.create(match.url);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getFragment() != null || !uri.normalize().equals(uri)) {
            throw new IllegalArgumentException("Invalid package download URL");
        }
        return uri.toString();
    }
}

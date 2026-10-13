package com.zrlog.common.updater.docker;

import com.hibegin.common.util.http.HttpUtil;
import com.hibegin.common.util.http.handle.HttpStringHandle;
import com.zrlog.common.vo.Version;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class UpdateMetadata {
    private UpdateMetadata() { }

    public static String fetch(URI uri) throws IOException {
        try {
            HttpStringHandle response = new HttpStringHandle();
            HttpUtil.getInstance().sendGetRequest(uri.toString(), response, Map.of());
            int status = response.getStatusCode();
            if (status == 404) { throw new FileNotFoundException("Update metadata source returned HTTP 404"); }
            if (status != 200) { throw new IOException("Update metadata source returned HTTP " + status); }
            return response.getT();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Update metadata request interrupted", error);
        } catch (URISyntaxException error) {
            throw new IOException("Invalid update metadata URL", error);
        }
    }

    public static URI uri(String origin, String filename, String channel) {
        URI base = URI.create(origin.endsWith("/") ? origin : origin + "/");
        if (!"https".equals(base.getScheme()) || base.getHost() == null || base.getUserInfo() != null
                || base.getQuery() != null || base.getFragment() != null || !base.normalize().equals(base)
                || !filename.matches("[A-Za-z0-9][A-Za-z0-9._-]*\\.json")
                || !("release".equals(channel) || "preview".equals(channel))) {
            throw new IllegalArgumentException("Invalid update metadata configuration in build.properties");
        }
        return base.resolve(channel + "/" + filename);
    }

    public static void validateDocker(Version version, String channel) {
        if (version == null || version.getSchemaVersion() != 1 || !"docker-image".equals(version.getPackageType())
                || !channel.equals(version.getChannel()) || version.getVersion() == null
                || !version.getVersion().matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?")
                || version.getSourceCommit() == null || !version.getSourceCommit().matches("[0-9a-f]{40}")
                || version.getBuildId() == null || !version.getBuildId().matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
                || ("release".equals(channel) && (!version.getBuildId().matches("[0-9a-f]{7,40}")
                    || !version.getSourceCommit().startsWith(version.getBuildId())))
                || version.getImages() == null || version.getImages().isEmpty() || version.getImages().size() > 32) {
            throw new IllegalArgumentException("Invalid Docker update manifest");
        }
        OffsetDateTime.parse(version.getReleaseDate(), DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ssXXX")
                .withResolverStyle(ResolverStyle.STRICT));
        Set<String> targets = new HashSet<>();
        for (DockerUpdaterProtocol.Image image : version.getImages()) {
            if (image == null || image.repository == null
                    || !image.repository.matches("[a-z0-9][a-z0-9.:-]*(?:/[a-z0-9]+(?:[._-][a-z0-9]+)*)+")
                    || image.repository.length() > 255 || image.digest == null || !image.digest.matches("sha256:[0-9a-f]{64}")
                    || image.tag == null || !image.tag.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}")
                    || image.platforms == null || image.platforms.isEmpty() || image.platforms.size() > 16) {
                throw new IllegalArgumentException("Invalid Docker image entry");
            }
            String repository = image.repository;
            String first = repository.substring(0, repository.indexOf('/'));
            if (!first.contains(".") && !first.contains(":") && !first.equals("localhost")) { repository = "docker.io/" + repository; }
            repository = repository.replaceFirst("^index\\.docker\\.io/", "docker.io/");
            for (String platform : image.platforms) {
                if (platform == null || !platform.matches("[a-z0-9]+/[a-z0-9]+(?:/[a-z0-9]+)?")
                        || !targets.add(repository + "/" + platform.replaceFirst("^linux/arm64/v8$", "linux/arm64"))) {
                    throw new IllegalArgumentException("Ambiguous Docker platform");
                }
            }
        }
    }
}

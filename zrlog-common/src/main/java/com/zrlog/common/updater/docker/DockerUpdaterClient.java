package com.zrlog.common.updater.docker;

import com.google.gson.Gson;
import com.zrlog.util.BlogBuildInfoUtil;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Backend-only client. Address/token come from deployment environment, never from browser input. */
public class DockerUpdaterClient {
    private static final Gson GSON = new Gson();
    private final URI base;
    private final String token;
    private final String metadataOrigin;
    private final String manifestFilename;

    public static boolean isConfigured() {
        return System.getenv("ZRLOG_UPDATER_URL") != null && System.getenv("ZRLOG_UPDATER_TOKEN") != null;
    }

    public static DockerUpdaterClient fromEnvironment() {
        return new DockerUpdaterClient(System.getenv("ZRLOG_UPDATER_URL"), System.getenv("ZRLOG_UPDATER_TOKEN"),
                BlogBuildInfoUtil.getResourceDownloadUrl(), BlogBuildInfoUtil.getDockerUpdateVersionJsonFilename());
    }

    public DockerUpdaterClient(String url, String token, String metadataOrigin, String manifestFilename) {
        if (url == null || token == null || !token.matches("[A-Za-z0-9_-]{32,256}")) {
            throw new IllegalStateException("Docker updater is not configured");
        }
        base = URI.create(url.endsWith("/") ? url : url + "/");
        if (!("http".equals(base.getScheme()) || "https".equals(base.getScheme())) || base.getHost() == null
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null
                || !"/".equals(base.getPath())) {
            throw new IllegalArgumentException("Invalid ZRLOG_UPDATER_URL");
        }
        this.token = token;
        this.metadataOrigin = metadataOrigin;
        this.manifestFilename = manifestFilename;
        UpdateMetadata.uri(metadataOrigin, manifestFilename, "release");
    }

    public DockerUpdaterProtocol.Capabilities capabilities() {
        DockerUpdaterProtocol.Capabilities value = call("GET", "v1/capabilities", null, DockerUpdaterProtocol.Capabilities.class);
        if (value.protocolVersion != 1 || !value.ready
                || !Objects.equals(UpdateMetadata.uri(metadataOrigin, manifestFilename, "release"),
                    UpdateMetadata.uri(value.metadataBaseUrl, value.manifestFilename, "release"))) {
            throw new IllegalStateException("Docker updater is not ready or build.properties metadata configuration differs");
        }
        return value;
    }

    public DockerUpdaterProtocol.SettingsResponse settings(String channel) {
        channel(channel);
        capabilities();
        return call("GET", "v1/settings" + ("preview".equals(channel) ? "/preview" : ""), null,
                DockerUpdaterProtocol.SettingsResponse.class);
    }

    public DockerUpdaterProtocol.Settings saveSettings(String repository, String channel) {
        channel(channel);
        capabilities();
        DockerUpdaterProtocol.Settings value = new DockerUpdaterProtocol.Settings();
        value.repository = Objects.requireNonNull(repository);
        value.channel = channel;
        return call("PUT", "v1/settings", value, DockerUpdaterProtocol.Settings.class);
    }

    public DockerUpdaterProtocol.Job submit(DockerUpdaterProtocol.UpgradeRequest request) {
        requestId(request.requestId);
        channel(request.channel);
        capabilities();
        return call("POST", "v1/upgrades", request, DockerUpdaterProtocol.Job.class);
    }

    public DockerUpdaterProtocol.Job status(String requestId) {
        requestId(requestId);
        return call("GET", "v1/upgrades/" + requestId, null, DockerUpdaterProtocol.Job.class);
    }

    public static void requestId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9][A-Za-z0-9_-]{15,79}")) {
            throw new IllegalArgumentException("Invalid updater request ID");
        }
    }

    private static void channel(String value) {
        if (!"release".equals(value) && !"preview".equals(value)) { throw new IllegalArgumentException("Invalid update channel"); }
    }

    private <T> T call(String method, String path, Object body, Class<T> type) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) base.resolve(path).toURL().openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(30000);
            connection.setRequestMethod(method);
            connection.setRequestProperty("Authorization", "Bearer " + token);
            connection.setRequestProperty("Accept", "application/json");
            if (body != null) {
                byte[] bytes = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setFixedLengthStreamingMode(bytes.length);
                connection.setDoOutput(true);
                try (java.io.OutputStream output = connection.getOutputStream()) { output.write(bytes); }
            }
            int status = connection.getResponseCode();
            if (status != 200 && status != 202) { throw new RequestException(status); }
            try (InputStream input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes(1024 * 1024 + 1);
                if (bytes.length > 1024 * 1024) { throw new IOException("Updater response too large"); }
                T value = GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), type);
                if (value == null) { throw new IOException("Empty updater response"); }
                return value;
            }
        } catch (IOException error) {
            // Do not expose configured credentials, internal URLs or raw engine output to the admin API.
            throw new IllegalStateException("Unable to communicate with the Docker updater");
        } finally {
            if (connection != null) { connection.disconnect(); }
        }
    }

    public static class RequestException extends IllegalStateException {
        private final int status;
        public RequestException(int status) { super("Docker updater request failed (HTTP " + status + ")"); this.status = status; }
        public int getStatus() { return status; }
    }
}

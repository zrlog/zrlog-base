package com.zrlog.common.updater.docker;

import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.updater.UpdaterHosts;
import com.zrlog.updater.spi.*;
import com.zrlog.util.BlogBuildInfoUtil;
import java.util.function.Supplier;

/** Compatibility facade; protocol implementation belongs to zrlog-updater. */
public class DockerUpdaterClient implements DockerControl {
    private final DockerControl delegate;
    public static boolean isConfigured() { return System.getenv("ZRLOG_UPDATER_URL") != null && System.getenv("ZRLOG_UPDATER_TOKEN") != null; }
    public static DockerUpdaterClient fromEnvironment() {
        return new DockerUpdaterClient(System.getenv("ZRLOG_UPDATER_URL"), System.getenv("ZRLOG_UPDATER_TOKEN"),
                BlogBuildInfoUtil.getResourceDownloadUrl(), BlogBuildInfoUtil.getDockerUpdateVersionJsonFilename());
    }
    public DockerUpdaterClient(String url, String token, String origin, String filename) {
        UpdateContext.Builder b = UpdaterHosts.builder(new String[0], null);
        b.docker = new DockerConnection(url, token, origin, filename);
        delegate = (DockerControl) UpdateProviders.get(UpdaterTypeEnum.DOCKER).create(b.build());
    }
    public DockerUpdaterProtocol.Capabilities capabilities() { return call(delegate::capabilities); }
    public DockerUpdaterProtocol.SettingsResponse settings(String channel) { return call(() -> delegate.settings(channel)); }
    public DockerUpdaterProtocol.Settings saveSettings(String repository, String channel) { return call(() -> delegate.saveSettings(repository, channel)); }
    public DockerUpdaterProtocol.Job submit(DockerUpdaterProtocol.UpgradeRequest request) { return call(() -> delegate.submit(request)); }
    public DockerUpdaterProtocol.Job status(String id) { return call(() -> delegate.status(id)); }
    public static void requestId(String id) { DockerControl.requestId(id); }
    private <T> T call(Supplier<T> operation) {
        try { return operation.get(); }
        catch (DockerControl.RequestException e) { throw new RequestException(e.getStatus(), e.getCode()); }
    }
    public static class RequestException extends DockerControl.RequestException {
        public RequestException(int status) { this(status, status == 401 || status == 403 ? "updater_unauthorized" : "updater_request_failed"); }
        public RequestException(int status, String code) { super(status, code); }
    }
}

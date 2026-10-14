package com.zrlog.common.updater;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.http.HttpUtil;
import com.hibegin.common.util.http.handle.HttpFileHandle;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.common.Constants;
import com.zrlog.common.Updater;
import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.updater.docker.UpdateMetadata;
import com.zrlog.common.vo.Version;
import com.zrlog.theme.spi.BundledThemes;
import com.zrlog.updater.spi.*;
import com.zrlog.util.ArgsParser;
import com.zrlog.util.BlogBuildInfoUtil;
import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.function.Function;

/** Adapts application paths, HTTP and lifecycle to the standalone updater contracts. */
public final class UpdaterHosts {
    private UpdaterHosts() { }
    public static final UpdateHttp HTTP = new UpdateHttp() {
        public String read(URI uri) throws Exception { return UpdateMetadata.fetch(uri); }
        public int download(URI uri, File file) throws Exception {
            HttpFileHandle handle = new HttpFileHandle(file.getParent(), file.getName()); handle.setT(file);
            HttpUtil.getInstance().sendGetRequest(uri.toString(), handle, Map.of());
            return handle.getStatusCode();
        }
        public HttpResponse<String> send(HttpRequest request) throws Exception { return HttpUtil.getInstance().sendRequest(request); }
    };
    public static UpdateContext.Builder builder(String[] args, File executable) {
        UpdateContext.Builder b = new UpdateContext.Builder();
        b.root = new File(PathUtil.getRootPath()); b.temp = new File(PathUtil.getTempPath());
        b.updateTemp = PathUtil.getConfFile("/update-temp"); b.executable = executable; b.arguments = args;
        b.jvmArguments = EnvKit.isNativeImage() ? java.util.List.of() : ManagementFactory.getRuntimeMXBean().getInputArguments();
        b.port = Constants.zrLogConfig == null ? ArgsParser.getPort(args) : Constants.zrLogConfig.getServerConfig().getPort();
        b.currentPort = () -> Constants.zrLogConfig == null ? ArgsParser.getPort(args) : Constants.zrLogConfig.getServerConfig().getPort();
        b.fileArch = BlogBuildInfoUtil.getFileArch(); b.metadataOrigin = BlogBuildInfoUtil.getResourceDownloadUrl();
        b.manifestFilename = BlogBuildInfoUtil.getUpdateVersionJsonFilename();
        b.lambda = EnvKit.isLambda(); b.http = HTTP;
        b.bundledTheme = path -> BundledThemes.getInstance().containsResource(path);
        b.stop = () -> { if (Constants.zrLogConfig != null) Constants.zrLogConfig.stop(); };
        return b;
    }
    public static UpdateContext context(Updater updater) {
        return updater instanceof DelegatingUpdater ? ((DelegatingUpdater) updater).context : builder(new String[0], updater == null ? null : updater.execFile()).build();
    }
    public static Updater create(UpdaterTypeEnum type, String[] args, File executable) {
        UpdateContext.Builder b = builder(args, executable);
        if (type == UpdaterTypeEnum.WAR) {
            Runnable stop = b.stop;
            b.stop = () -> {
                java.util.Enumeration<Driver> drivers = DriverManager.getDrivers();
                while (drivers.hasMoreElements()) {
                    Driver driver = drivers.nextElement();
                    if (driver.getClass().getClassLoader() == UpdaterHosts.class.getClassLoader()) {
                        try { DriverManager.deregisterDriver(driver); }
                        catch (SQLException e) { java.util.logging.Logger.getLogger(UpdaterHosts.class.getName()).warning(e.getMessage()); }
                    }
                }
                stop.run();
            };
        }
        if (type == UpdaterTypeEnum.DOCKER) {
            b.manifestFilename = BlogBuildInfoUtil.getDockerUpdateVersionJsonFilename();
            b.docker = new DockerConnection(System.getenv("ZRLOG_UPDATER_URL"), System.getenv("ZRLOG_UPDATER_TOKEN"), b.metadataOrigin, b.manifestFilename);
        }
        UpdateContext context = b.build();
        return new DelegatingUpdater(context, UpdateProviders.get(type).create(context));
    }
    public static ApplicationUpdater unwrap(Updater updater) {
        return updater instanceof DelegatingUpdater ? ((DelegatingUpdater) updater).delegate : updater;
    }
    public static Function<String, String> text(Map<String, Object> backend) {
        return key -> java.util.Objects.toString(backend.get(key), key);
    }
    public static UpdateVersionHandler handler(UpdaterTypeEnum type, Updater updater, File file, Version version,
                                               String key, UpgradeProgressListener progress, Map<String, Object> backend) {
        return UpdateProviders.get(type).handler(context(updater), new UpdateRequest(file, version, key, unwrap(updater), progress, text(backend)));
    }
    public static class DelegatingUpdater implements Updater {
        public final UpdateContext context;
        public final ApplicationUpdater delegate;
        public DelegatingUpdater(UpdateContext context, ApplicationUpdater delegate) { this.context = context; this.delegate = delegate; }
        public void restartProcessAsync(Version version) { delegate.restartProcessAsync(version); }
        public File getUpdateTempPath() { return delegate.getUpdateTempPath(); }
        public String buildUpgradeFile(String file, String key) throws Exception { return delegate.buildUpgradeFile(file, key); }
        public String backup() throws Exception { return delegate.backup(); }
        public String getUnzipPath() { return delegate.getUnzipPath(); }
        public File execFile() { return delegate.execFile(); }
        public UpdaterTypeEnum getType() { return delegate.getType(); }
    }
}

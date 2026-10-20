package com.zrlog.common.adapter;

import com.hibegin.common.util.EnvKit;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.common.Updater;
import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.updater.UpdateVersionHandler;
import com.zrlog.common.updater.UpgradeProgressListener;
import com.zrlog.common.vo.Version;
import com.zrlog.theme.spi.BundledThemes;
import com.zrlog.updater.spi.*;
import com.zrlog.util.ArgsParser;
import com.zrlog.util.BlogBuildInfoUtil;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntSupplier;

/**
 * Adapts application paths, HTTP and lifecycle to the standalone updater contracts.
 */
public final class UpdaterHosts {
    public static final UpdateHttp HTTP = UpdaterHttp.INSTANCE;

    private UpdaterHosts() {
    }

    public static UpdateContext.Builder builder(String[] args, File executable) {
        UpdateContext.Builder b = new UpdateContext.Builder();
        b.root = new File(PathUtil.getRootPath());
        b.temp = new File(PathUtil.getTempPath());
        b.updateTemp = PathUtil.getConfFile("/update-temp");
        b.executable = executable;
        b.arguments = args;
        b.jvmArguments = EnvKit.isNativeImage() ? java.util.List.of() : ManagementFactory.getRuntimeMXBean().getInputArguments();
        b.port = ArgsParser.getPort(args);
        b.fileArch = BlogBuildInfoUtil.getFileArch();
        b.metadataOrigin = BlogBuildInfoUtil.getResourceDownloadUrl();
        b.manifestFilename = BlogBuildInfoUtil.getUpdateVersionJsonFilename();
        b.lambda = EnvKit.isLambda();
        b.http = HTTP;
        b.bundledTheme = path -> BundledThemes.getInstance().containsResource(path);
        b.releaseJdbcDrivers = UpdaterHosts::releaseJdbcDrivers;
        b.docker = new DockerConnection(System.getenv("ZRLOG_UPDATER_URL"), System.getenv("ZRLOG_UPDATER_TOKEN"),
                b.metadataOrigin, BlogBuildInfoUtil.getDockerUpdateVersionJsonFilename());
        return b;
    }

    /**
     * Supplies host capabilities before updater discovery and later binds them to the owning server.
     */
    public static ApplicationHost application(String[] args, File executable) {
        return new ApplicationHost(builder(args, executable));
    }

    /**
     * Called once by the owning application during configuration, before any upgrade can run.
     */
    public static void bindLifecycle(Updater updater, IntSupplier currentPort, Runnable stop) {
        if (updater instanceof HostedUpdaterAdapter) {
            HostLifecycle lifecycle = ((HostedUpdaterAdapter) updater).lifecycle;
            if (lifecycle != null) {
                lifecycle.currentPort = java.util.Objects.requireNonNull(currentPort);
                lifecycle.stop = java.util.Objects.requireNonNull(stop);
            }
        }
    }

    public static UpdateContext context(Updater updater) {
        return updater instanceof HostedUpdaterAdapter ? ((HostedUpdaterAdapter) updater).context : builder(new String[0], updater == null ? null : updater.execFile()).build();
    }

    public static Updater adapt(UpdateSession session) {
        return session == null ? null : new HostedUpdaterAdapter(session.context, session.updater, null);
    }

    private static void releaseJdbcDrivers() {
        java.util.Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (driver.getClass().getClassLoader() == UpdaterHosts.class.getClassLoader()) {
                try {
                    DriverManager.deregisterDriver(driver);
                } catch (SQLException e) {
                    java.util.logging.Logger.getLogger(UpdaterHosts.class.getName()).warning(e.getMessage());
                }
            }
        }
    }

    public static ApplicationUpdater unwrap(Updater updater) {
        return updater instanceof HostedUpdaterAdapter ? ((HostedUpdaterAdapter) updater).delegate : updater;
    }

    public static boolean dockerConfigured() {
        return System.getenv("ZRLOG_UPDATER_URL") != null && System.getenv("ZRLOG_UPDATER_TOKEN") != null;
    }

    public static DockerControl dockerControl(Updater updater) {
        ApplicationUpdater implementation = unwrap(updater);
        if (!(implementation instanceof DockerControl)) {
            throw new IllegalStateException("Docker updater is not assembled");
        }
        return (DockerControl) implementation;
    }

    public static boolean onlineUpgradeSupported(UpdaterTypeEnum type, Updater updater) {
        return UpdateProviders.find(type).map(provider -> provider.onlineUpgradeSupported(context(updater))).orElse(false);
    }

    public static Function<String, String> text(Map<String, Object> backend) {
        return key -> java.util.Objects.toString(backend.get(key), key);
    }

    public static UpdateVersionHandler handler(UpdaterTypeEnum type, Updater updater, File file, Version version,
                                               String key, UpgradeProgressListener progress, Map<String, Object> backend) {
        return UpdateProviders.get(type).handler(context(updater), new UpdateRequest(file, version, key, unwrap(updater), progress, text(backend)));
    }

    public static UpdateVersionHandler manualHandler(UpdaterTypeEnum type, Updater updater, Version version,
                                                     Map<String, Object> backend) {
        return UpdateProviders.get(type).manualHandler(context(updater),
                new UpdateRequest(null, version, null, unwrap(updater), UpgradeProgressListener.NONE, text(backend)));
    }

    public static final class ApplicationHost {
        private final UpdateContext context;
        private final HostLifecycle lifecycle;

        private ApplicationHost(UpdateContext.Builder builder) {
            lifecycle = new HostLifecycle(builder.port);
            builder.currentPort = lifecycle::port;
            builder.stop = lifecycle::stop;
            context = builder.build();
        }

        public UpdateContext context() {
            return context;
        }

        public Updater adapt(UpdateSession session) {
            return session == null ? null : new HostedUpdaterAdapter(session.context, session.updater, lifecycle);
        }
    }

    private static final class HostLifecycle {
        private volatile IntSupplier currentPort;
        private volatile Runnable stop = () -> {
        };

        private HostLifecycle(int initialPort) {
            currentPort = () -> initialPort;
        }

        private int port() {
            return currentPort.getAsInt();
        }

        private void stop() {
            stop.run();
        }
    }

    private static final class HostedUpdaterAdapter implements Updater {
        public final UpdateContext context;
        public final ApplicationUpdater delegate;
        private final HostLifecycle lifecycle;

        private HostedUpdaterAdapter(UpdateContext context, ApplicationUpdater delegate, HostLifecycle lifecycle) {
            this.context = context;
            this.delegate = delegate;
            this.lifecycle = lifecycle;
        }

        public void restartProcessAsync(Version version) {
            delegate.restartProcessAsync(version);
        }

        public File getUpdateTempPath() {
            return delegate.getUpdateTempPath();
        }

        public String buildUpgradeFile(String file, String key) throws Exception {
            return delegate.buildUpgradeFile(file, key);
        }

        public String backup() throws Exception {
            return delegate.backup();
        }

        public String getUnzipPath() {
            return delegate.getUnzipPath();
        }

        public File execFile() {
            return delegate.execFile();
        }

        public UpdaterTypeEnum getType() {
            return delegate.getType();
        }
    }
}

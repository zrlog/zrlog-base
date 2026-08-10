package com.zrlog.business.plugin;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.ZipUtil;
import com.hibegin.http.server.config.ConfigKit;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.business.util.PluginCoreUtils;
import com.zrlog.common.Constants;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.ThreadUtils;
import com.zrlog.util.ZrLogUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PluginCoreProcessImpl implements PluginCoreProcess {

    private static final Logger LOGGER = LoggerUtil.getLogger(PluginCoreProcessImpl.class);
    private static final long PROCESS_STOP_TIMEOUT_MILLIS = 2000L;
    private static final long PROCESS_STOP_RETRY_MILLIS = 1000L;
    static final int PLUGIN_MASTER_PORT_MIN = 40000;
    static final int PLUGIN_MASTER_PORT_MAX_EXCLUSIVE = 50000;
    static final long MIN_RESTART_BACKOFF_MILLIS = TimeUnit.SECONDS.toMillis(5);
    static final long MAX_RESTART_BACKOFF_MILLIS = TimeUnit.SECONDS.toMillis(60);
    static final long STABLE_PROCESS_RUNTIME_MILLIS = TimeUnit.SECONDS.toMillis(60);
    static final long PLUGIN_CORE_READY_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(60);
    static final long DEFAULT_NATIVE_MAX_HEAP_SIZE = 128L * 1024L * 1024L;
    static final long MIN_NATIVE_MAX_HEAP_SIZE = 64L * 1024L * 1024L;
    static final long MAX_NATIVE_MAX_HEAP_SIZE = 512L * 1024L * 1024L;
    private static final String NATIVE_MAX_HEAP_SIZE_KEY = "pluginCoreNativeMaxHeapSize";
    static final String PLUGIN_CORE_MAIN_CLASS = "com.zrlog.plugincore.server.Application";

    private AbstractPluginCoreProcessHandle pluginCoreProcessHandle;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);
    private final Object processLifecycleLock = new Object();
    private File infoLogFile;
    private File errorLogFile;
    private int lastPluginMasterPort = -1;
    private final Runnable onStopRunnable;
    //插件服务存放的物理路径
    private final File pluginsFolder;
    private boolean unzipped = false;
    private final String contextPath;


    private File getLogFile(boolean error) {
        File logFile = new File(PathUtil.getLogPath() + "/plugin-core-" + (error ? "error" : "info") + "." + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".log");
        logFile.getParentFile().mkdirs();
        return logFile;
    }

    public PluginCoreProcessImpl(Runnable onStopRunnable, String contextPath) {
        this.contextPath = contextPath;
        this.onStopRunnable = onStopRunnable;
        if (EnvKit.isFaaSMode()) {
            this.pluginsFolder = new File(ZrLogUtil.getFaaSRoot() + "/conf/plugins/");
        } else {
            this.pluginsFolder = PathUtil.getConfFile("/plugins/");
        }
    }

    String programName(File pluginCoreFile) {
        if (EnvKit.isNativeImage()) {
            return pluginCoreFile.toString();
        }
        String java = System.getProperty("java.home");
        if (Objects.nonNull(java)) {
            return java.replace("\\", "/") + "/bin/java";
        }
        return "java";
    }

    private void prepareUnzipPlugins() {
        if (unzipped) {
            return;
        }
        if (!EnvKit.isFaaSMode()) {
            return;
        }
        try {
            String zipFile = ZrLogUtil.getFaaSRoot() + "/conf/plugins.zip";
            if (new File(zipFile).exists()) {
                ZipUtil.unZip(zipFile, PathUtil.getConfPath());
            }
        } catch (IOException e) {
            LOGGER.warning("Can't unzip " + ZrLogUtil.getFaaSRoot());
        } finally {
            unzipped = true;
        }
    }

    String getPluginWorkerPath(File pluginsFolder) {
        if (EnvKit.isLambda()) {
            return PathUtil.getConfFile("/conf/plugins").toString();
        }
        return pluginsFolder.getParent();
    }

    Process startPluginCore(final File pluginCoreFile, final String dbProperties, final String pluginJvmArgs,
                            final String runtimePath, final String runTimeVersion, String token,
                            int randomServerPort, int pluginMasterPort, int randomWatcherListenPort) throws IOException {
        if (!pluginCoreFile.exists() || pluginCoreFile.length() <= 0) {
            LOGGER.warning("Missing plugin-core file " + pluginCoreFile.getName());
            return null;
        }
        if (Objects.isNull(infoLogFile) || Objects.isNull(errorLogFile)) {
            infoLogFile = getLogFile(false);
            errorLogFile = getLogFile(true);
        }
        List<String> args = new ArrayList<>();
        if (EnvKit.isNativeImage()) {
            args.add("-XX:MaxHeapSize=" + nativeMaxHeapSize());
            args.add("-XX:+ExitOnOutOfMemoryError");
        } else {
            args.addAll(jvmLaunchArguments(pluginCoreFile, dbProperties, pluginJvmArgs));
        }
        //args start
        args.add(randomServerPort + "");
        args.add(pluginMasterPort + "");
        args.add(dbProperties);

        args.add(getInstalledPluginFolder());
        args.add(randomWatcherListenPort + "");
        args.add(runtimePath);
        args.add(runTimeVersion);
        args.add(Constants.zrLogConfig.getServerConfig().getPort() + "");
        args.add(token);
        if (EnvKit.isNativeImage()) {
            args.add(BlogBuildInfoUtil.getFileArch());
        } else {
            args.add("-");
        }
        if (Objects.isNull(contextPath) || contextPath.isEmpty() || contextPath.equals("/")) {
            //not config
            args.add("#");
        } else {
            args.add(contextPath);
        }
        //参数位置顺序需要固定
        args.add("-Duser.dir=" + getPluginWorkerPath(pluginsFolder));
        //args end
        List<String> cmd = new ArrayList<>();
        cmd.add(programName(pluginCoreFile));
        cmd.addAll(args);
        return new ProcessBuilder(cmd).redirectOutput(infoLogFile).redirectError(errorLogFile).start();
    }

    static List<String> jvmLaunchArguments(File pluginCoreFile, String dbProperties, String pluginJvmArgs) {
        List<String> args = new ArrayList<>(Arrays.asList(pluginJvmArgs.split(" ")));
        if (usesLocalSqlite(dbProperties)) {
            String parentClasspath = System.getProperty("java.class.path", "");
            String pluginCoreClasspath = pluginCoreFile.toString();
            if (!parentClasspath.isEmpty()) {
                pluginCoreClasspath += File.pathSeparator + parentClasspath;
            }
            args.add("-cp");
            args.add(pluginCoreClasspath);
            args.add(PLUGIN_CORE_MAIN_CLASS);
        } else {
            args.add("-jar");
            args.add(pluginCoreFile.toString());
        }
        return args;
    }

    static boolean usesLocalSqlite(String dbProperties) {
        if (dbProperties == null) {
            return false;
        }
        Properties properties = new Properties();
        try (FileInputStream inputStream = new FileInputStream(dbProperties)) {
            properties.load(inputStream);
        } catch (IOException e) {
            return false;
        }
        String jdbcUrl = properties.getProperty("jdbcUrl");
        return jdbcUrl != null && jdbcUrl.regionMatches(true, 0, "jdbc:sqlite:", 0, "jdbc:sqlite:".length());
    }

    long nativeMaxHeapSize() {
        String configured = ConfigKit.get(NATIVE_MAX_HEAP_SIZE_KEY, Long.toString(DEFAULT_NATIVE_MAX_HEAP_SIZE));
        if (!isValidNativeMaxHeapSize(configured)) {
            LOGGER.warning("Invalid " + NATIVE_MAX_HEAP_SIZE_KEY + "=" + configured
                    + ", use " + DEFAULT_NATIVE_MAX_HEAP_SIZE);
        }
        return resolveNativeMaxHeapSize(configured);
    }

    static long resolveNativeMaxHeapSize(String configured) {
        if (!isValidNativeMaxHeapSize(configured)) {
            return DEFAULT_NATIVE_MAX_HEAP_SIZE;
        }
        return Long.parseLong(configured.trim());
    }

    private static boolean isValidNativeMaxHeapSize(String configured) {
        if (configured == null || configured.trim().isEmpty()) {
            return false;
        }
        try {
            long value = Long.parseLong(configured.trim());
            return value >= MIN_NATIVE_MAX_HEAP_SIZE && value <= MAX_NATIVE_MAX_HEAP_SIZE;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    String getInstalledPluginFolder() {
        return pluginsFolder + "/installed-plugins";
    }

    @Override
    public int pluginServerStart(final String dbProperties, final String pluginJvmArgs, final String runtimePath, final String runTimeVersion, String token) {
        prepareUnzipPlugins();
        //简单处理，为了能在一个服务器上面启动多个ZrLog程序，使用Random端口的方式，（感兴趣可以算算概率）
        final int randomServerPort = new Random().nextInt(10000) + 20000;
        final int randomWatcherListenPort = randomServerPort + 30000;
        try {
            final AbstractPluginCoreProcessHandle handle;
            synchronized (processLifecycleLock) {
                if (Objects.nonNull(pluginCoreProcessHandle) && !pluginCoreProcessHandle.close()) {
                    throw new IllegalStateException("Previous plugin-core process is still alive");
                }
                stopped.set(false);
                handle = new AbstractPluginCoreProcessHandle() {

                    private Process process;
                    private long processStartedAtNanos;
                    private long restartBackoffMillis;
                    private long launchDelayMillis;
                    private long launchAttemptCount;
                    private long processGeneration;
                    private boolean watcherConnected;
                    private boolean closed;
                    private Thread runnerThread;

                    public void run() {
                        Thread.currentThread().setName("plugin-core-thread");
                        synchronized (processLifecycleLock) {
                            runnerThread = Thread.currentThread();
                        }

                        try {
                            while (shouldSupervise()) {
                                try {
                                    if (!stopPreviousProcessBeforeLaunch() || !pauseBeforeNextLaunch()) {
                                        return;
                                    }
                                    if (!startNextProcess()) {
                                        continue;
                                    }
                                    watchCurrentProcess();
                                    if (shouldSupervise()) {
                                        scheduleProcessRestart("watcher became unavailable", null);
                                    }
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    return;
                                } catch (RuntimeException e) {
                                    if (!shouldSupervise()) {
                                        return;
                                    }
                                    scheduleProcessRestart("recoverable supervisor failure", e);
                                }
                            }
                        } finally {
                            close();
                            synchronized (processLifecycleLock) {
                                if (runnerThread == Thread.currentThread()) {
                                    runnerThread = null;
                                }
                            }
                        }
                    }

                    private boolean shouldSupervise() {
                        synchronized (processLifecycleLock) {
                            return shouldSuperviseLocked();
                        }
                    }

                    private boolean shouldSuperviseLocked() {
                        return !closed && !stopped.get() && !Thread.currentThread().isInterrupted();
                    }

                    private boolean stopPreviousProcessBeforeLaunch() throws InterruptedException {
                        while (shouldSupervise()) {
                            Process previousProcess;
                            synchronized (processLifecycleLock) {
                                if (!shouldSuperviseLocked()) {
                                    return false;
                                }
                                previousProcess = process;
                                if (Objects.isNull(previousProcess)) {
                                    return true;
                                }
                                if (!previousProcess.isAlive() || stopAndAwaitProcess(previousProcess)) {
                                    process = null;
                                    return true;
                                }
                            }
                            LOGGER.warning("Unable to stop plugin-core pid=" + processId(previousProcess)
                                    + "; retry stop in " + PROCESS_STOP_RETRY_MILLIS
                                    + " ms before starting a replacement");
                            pauseBeforePluginCoreStopRetry(PROCESS_STOP_RETRY_MILLIS);
                        }
                        return false;
                    }

                    private boolean pauseBeforeNextLaunch() throws InterruptedException {
                        long delayMillis;
                        synchronized (processLifecycleLock) {
                            if (!shouldSuperviseLocked()) {
                                return false;
                            }
                            delayMillis = launchDelayMillis;
                            launchDelayMillis = 0L;
                        }
                        if (delayMillis > 0L) {
                            pauseBeforePluginCoreRestart(delayMillis);
                        }
                        return shouldSupervise();
                    }

                    private boolean startNextProcess() {
                        int nextMasterPort = -1;
                        long currentAttempt = -1L;
                        try {
                            synchronized (processLifecycleLock) {
                                if (!shouldSuperviseLocked()) {
                                    return false;
                                }
                                currentAttempt = ++launchAttemptCount;
                                nextMasterPort = nextPluginMasterPort(lastPluginMasterPort);
                                lastPluginMasterPort = nextMasterPort;
                            }
                            File coreFile = PluginCoreUtils.tryDownloadPluginCoreFile(pluginsFolder.toString());
                            synchronized (processLifecycleLock) {
                                if (!shouldSuperviseLocked()) {
                                    return false;
                                }
                                Process startedProcess = startPluginCore(coreFile, dbProperties, pluginJvmArgs,
                                        runtimePath, runTimeVersion, token, randomServerPort, nextMasterPort,
                                        randomWatcherListenPort);
                                if (Objects.isNull(startedProcess)) {
                                    throw new IOException("plugin-core launcher returned no process");
                                }
                                process = startedProcess;
                                processStartedAtNanos = monotonicTimeNanos();
                                watcherConnected = false;
                                processGeneration++;
                            }
                            LOGGER.info("Started plugin-core pid=" + processId(process)
                                    + ", generation=" + processGeneration
                                    + ", launchAttempt=" + currentAttempt
                                    + ", httpPort=" + randomServerPort
                                    + ", masterPort=" + nextMasterPort
                                    + ", watcherPort=" + randomWatcherListenPort);
                            return true;
                        } catch (Exception e) {
                            synchronized (processLifecycleLock) {
                                if (!shouldSuperviseLocked()) {
                                    return false;
                                }
                                restartBackoffMillis = nextRestartBackoffMillis(restartBackoffMillis, 0L);
                                launchDelayMillis = restartBackoffMillis;
                            }
                            LOGGER.log(Level.WARNING, "Unable to start plugin-core launchAttempt=" + currentAttempt
                                    + ", masterPort=" + nextMasterPort
                                    + "; retry in " + launchDelayMillis + " ms", e);
                            return false;
                        }
                    }

                    private void watchCurrentProcess() throws InterruptedException {
                        PluginConsole errorPluginConsole = new PluginConsole(errorLogFile, pluginsFolder, true);
                        PluginConsole infoPluginConsole = new PluginConsole(infoLogFile, pluginsFolder, false);
                        try {
                            infoPluginConsole.printAsync();
                            errorPluginConsole.printAsync();
                            while (shouldRun()) {
                                boolean connected = newPluginGhostWatcher("127.0.0.1", randomWatcherListenPort).doWatch();
                                if (connected) {
                                    watcherConnected = true;
                                }
                                // Avoid a tight loop while the child is still starting.
                                pauseAfterPluginGhostWatcher();
                                if (connected || !shouldRetryWatcherConnection()) {
                                    break;
                                }
                            }
                        } finally {
                            try {
                                errorPluginConsole.close();
                            } catch (Exception e) {
                                LOGGER.warning("Close error stream " + e.getMessage());
                            }
                            try {
                                infoPluginConsole.close();
                            } catch (Exception e) {
                                LOGGER.warning("Close info stream " + e.getMessage());
                            }
                        }
                    }

                    private boolean shouldRun() {
                        synchronized (processLifecycleLock) {
                            return shouldSuperviseLocked() && Objects.nonNull(process);
                        }
                    }

                    private boolean shouldRetryWatcherConnection() {
                        synchronized (processLifecycleLock) {
                            return !closed && !stopped.get() && !Thread.currentThread().isInterrupted()
                                    && Objects.nonNull(process) && process.isAlive()
                                    && processRuntimeMillis() < PLUGIN_CORE_READY_TIMEOUT_MILLIS;
                        }
                    }

                    private void scheduleProcessRestart(String reason, RuntimeException failure) {
                        long observedRuntimeMillis;
                        long delayMillis;
                        boolean connected;
                        Process unavailableProcess;
                        synchronized (processLifecycleLock) {
                            if (!shouldSuperviseLocked()) {
                                return;
                            }
                            unavailableProcess = process;
                            observedRuntimeMillis = Objects.isNull(unavailableProcess) ? 0L : processRuntimeMillis();
                            connected = watcherConnected;
                            restartBackoffMillis = nextRestartBackoffMillis(
                                    restartBackoffMillis, connected ? observedRuntimeMillis : 0L);
                            launchDelayMillis = restartBackoffMillis;
                            delayMillis = launchDelayMillis;
                        }
                        String message = "plugin-core unavailable: " + reason
                                + ", pid=" + processId(unavailableProcess)
                                + ", exitCode=" + processExitCode(unavailableProcess)
                                + ", watcherConnected=" + connected
                                + ", runtimeMs=" + observedRuntimeMillis
                                + "; restart in " + delayMillis + " ms";
                        if (Objects.isNull(failure)) {
                            LOGGER.warning(message);
                        } else {
                            LOGGER.log(Level.WARNING, message, failure);
                        }
                    }

                    private long processRuntimeMillis() {
                        long elapsedNanos = monotonicTimeNanos() - processStartedAtNanos;
                        return TimeUnit.NANOSECONDS.toMillis(Math.max(0L, elapsedNanos));
                    }

                    public boolean close() {
                        Thread threadToInterrupt;
                        boolean processStopped;
                        synchronized (processLifecycleLock) {
                            closed = true;
                            threadToInterrupt = runnerThread;
                            processStopped = stopAndAwaitProcess(process);
                            if (processStopped) {
                                process = null;
                            }
                        }
                        if (threadToInterrupt != null && threadToInterrupt != Thread.currentThread()) {
                            threadToInterrupt.interrupt();
                        }
                        if (!processStopped) {
                            LOGGER.warning("Unable to stop plugin-core process; retaining its process handle");
                        }
                        return processStopped;
                    }
                };
                pluginCoreProcessHandle = handle;
            }
            registerShutdownHook();
            startPluginCoreHandle(handle);
            return randomServerPort;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "start plugin exception ", e);
            return -1;
        }

    }

    int nextPluginMasterPort(int previousPort) {
        int candidate = ThreadLocalRandom.current().nextInt(
                PLUGIN_MASTER_PORT_MIN, PLUGIN_MASTER_PORT_MAX_EXCLUSIVE);
        if (candidate != previousPort) {
            return candidate;
        }
        return PLUGIN_MASTER_PORT_MIN
                + (candidate - PLUGIN_MASTER_PORT_MIN + 1)
                % (PLUGIN_MASTER_PORT_MAX_EXCLUSIVE - PLUGIN_MASTER_PORT_MIN);
    }

    private static String processId(Process process) {
        if (Objects.isNull(process)) {
            return "none";
        }
        try {
            return Long.toString(process.pid());
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    private static String processExitCode(Process process) {
        try {
            if (Objects.isNull(process) || process.isAlive()) {
                return "running";
            }
            return Integer.toString(process.exitValue());
        } catch (RuntimeException e) {
            return "unknown";
        }
    }

    PluginGhostWatcher newPluginGhostWatcher(String host, int port) {
        return new PluginGhostWatcher(host, port);
    }

    void pauseAfterPluginGhostWatcher() throws InterruptedException {
        Thread.sleep(1000);
    }

    long monotonicTimeNanos() {
        return System.nanoTime();
    }

    void pauseBeforePluginCoreRestart(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    void pauseBeforePluginCoreStopRetry(long millis) throws InterruptedException {
        Thread.sleep(millis);
    }

    static long nextRestartBackoffMillis(long previousBackoffMillis, long processRuntimeMillis) {
        if (processRuntimeMillis >= STABLE_PROCESS_RUNTIME_MILLIS) {
            return MIN_RESTART_BACKOFF_MILLIS;
        }
        if (previousBackoffMillis < MIN_RESTART_BACKOFF_MILLIS) {
            return MIN_RESTART_BACKOFF_MILLIS;
        }
        if (previousBackoffMillis >= MAX_RESTART_BACKOFF_MILLIS / 2L) {
            return MAX_RESTART_BACKOFF_MILLIS;
        }
        return Math.min(MAX_RESTART_BACKOFF_MILLIS, previousBackoffMillis * 2L);
    }

    boolean stopAndAwaitProcess(Process process) {
        if (Objects.isNull(process) || !process.isAlive()) {
            return true;
        }
        process.destroy();
        if (awaitProcessExit(process) || !process.isAlive()) {
            return true;
        }
        process.destroyForcibly();
        if (Thread.currentThread().isInterrupted()) {
            return !process.isAlive();
        }
        return awaitProcessExit(process) || !process.isAlive();
    }

    private boolean awaitProcessExit(Process process) {
        try {
            return process.waitFor(PROCESS_STOP_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    Thread startPluginCoreHandle(AbstractPluginCoreProcessHandle handle) {
        return ThreadUtils.start(handle);
    }

    /**
     * ZrLog异常终止后，停止对应的插件服务。
     */
    private void registerShutdownHook() {
        if (!shutdownHookRegistered.compareAndSet(false, true)) {
            return;
        }
        Runtime rt = Runtime.getRuntime();
        try {
            rt.addShutdownHook(new Thread(this::stopPluginCore));
        } catch (RuntimeException e) {
            shutdownHookRegistered.set(false);
            throw e;
        }
    }

    @Override
    public void stopPluginCore() {
        boolean shouldNotifyStop;
        synchronized (processLifecycleLock) {
            shouldNotifyStop = stopped.compareAndSet(false, true);
            AbstractPluginCoreProcessHandle handle = pluginCoreProcessHandle;
            if (Objects.nonNull(handle) && handle.close()) {
                pluginCoreProcessHandle = null;
            }
        }
        if (shouldNotifyStop && Objects.nonNull(onStopRunnable)) {
            onStopRunnable.run();
        }
    }
}

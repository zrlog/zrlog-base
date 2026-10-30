package com.zrlog.business.plugin;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.ZipUtil;
import com.hibegin.http.server.config.ConfigKit;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.business.util.PluginCoreUtils;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.ThreadUtils;
import com.zrlog.util.ExecutionDeadline;
import com.zrlog.util.ZrLogUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PluginCoreProcessImpl implements PluginCoreProcess {

    static final int PLUGIN_MASTER_PORT_MIN = 40000;
    static final int PLUGIN_MASTER_PORT_MAX_EXCLUSIVE = 50000;
    static final long MIN_RESTART_BACKOFF_MILLIS = TimeUnit.SECONDS.toMillis(5);
    static final long MAX_RESTART_BACKOFF_MILLIS = TimeUnit.SECONDS.toMillis(60);
    static final long STABLE_PROCESS_RUNTIME_MILLIS = TimeUnit.SECONDS.toMillis(60);
    static final long PLUGIN_CORE_READY_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(60);
    static final long DEFAULT_NATIVE_MAX_HEAP_SIZE = 128L * 1024L * 1024L;
    static final long MIN_NATIVE_MAX_HEAP_SIZE = 64L * 1024L * 1024L;
    static final long MAX_NATIVE_MAX_HEAP_SIZE = 512L * 1024L * 1024L;
    static final String ENABLE_NATIVE_ACCESS_ARGUMENT = "--enable-native-access=ALL-UNNAMED";
    static final String PLUGIN_CORE_MAIN_CLASS = "com.zrlog.plugincore.server.Application";
    private static final Logger LOGGER = LoggerUtil.getLogger(PluginCoreProcessImpl.class);
    private static final long PROCESS_STOP_TIMEOUT_MILLIS = 2000L;
    private static final long PROCESS_STOP_RETRY_MILLIS = 1000L;
    private static final String NATIVE_MAX_HEAP_SIZE_KEY = "pluginCoreNativeMaxHeapSize";
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicBoolean shutdownHookRegistered = new AtomicBoolean(false);
    private final ReentrantLock processLifecycleLock = new ReentrantLock();
    private final Runnable onStopRunnable;
    //插件服务存放的物理路径
    private final File pluginsFolder;
    private final String contextPath;
    private final IntSupplier hostPort;
    private AbstractPluginCoreProcessHandle pluginCoreProcessHandle;
    private File infoLogFile;
    private File errorLogFile;
    private int lastPluginMasterPort = -1;
    private boolean unzipped = false;
    private volatile boolean externallyScheduled;


    public PluginCoreProcessImpl(Runnable onStopRunnable, String contextPath, IntSupplier hostPort) {
        this.contextPath = contextPath;
        this.hostPort = Objects.requireNonNull(hostPort);
        this.onStopRunnable = onStopRunnable;
        if (EnvKit.isFaaSMode()) {
            this.pluginsFolder = new File(ZrLogUtil.getFaaSRoot() + "/conf/plugins/");
        } else {
            this.pluginsFolder = PathUtil.getConfFile("/plugins/");
        }
    }

    @Override
    public void setExternallyScheduled(boolean externallyScheduled) {
        this.externallyScheduled = externallyScheduled;
    }

    static List<String> jvmLaunchArguments(File pluginCoreFile, String dbProperties, String pluginJvmArgs) {
        return jvmLaunchArguments(pluginCoreFile, dbProperties, pluginJvmArgs, Runtime.version().feature());
    }

    static List<String> jvmLaunchArguments(File pluginCoreFile, String dbProperties, String pluginJvmArgs,
                                           int runtimeFeatureVersion) {
        List<String> args = new ArrayList<>(Arrays.asList(pluginJvmArgs.split(" ")));
        args.addAll(com.zrlog.util.NetworkProxy.initialize().launchArguments());
        if (runtimeFeatureVersion >= 22 && !args.contains(ENABLE_NATIVE_ACCESS_ARGUMENT)) {
            args.add(ENABLE_NATIVE_ACCESS_ARGUMENT);
        }
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

    private File getLogFile(boolean error) {
        File logFile = new File(PathUtil.getLogPath() + "/plugin-core-" + (error ? "error" : "info") + "." + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + ".log");
        logFile.getParentFile().mkdirs();
        return logFile;
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

    private void prepareUnzipPlugins(ExecutionDeadline deadline) throws IOException, InterruptedException {
        if (unzipped) {
            return;
        }
        if (!EnvKit.isFaaSMode()) {
            return;
        }
        try {
            String zipFile = ZrLogUtil.getFaaSRoot() + "/conf/plugins.zip";
            if (new File(zipFile).exists()) {
                if (deadline == null) ZipUtil.unZip(zipFile, PathUtil.getConfPath());
                else PluginCoreUtils.unpackPlugins(new File(zipFile).toPath(), new File(PathUtil.getConfPath()).toPath(), deadline);
            }
            unzipped = true;
        } catch (IOException e) {
            if (deadline != null) throw e;
            LOGGER.warning("Can't unzip " + ZrLogUtil.getFaaSRoot());
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
        prepareProcessLogs();
        List<String> args = new ArrayList<>();
        if (EnvKit.isNativeImage()) {
            args.add("-XX:MaxHeapSize=" + nativeMaxHeapSize());
            args.add("-XX:+ExitOnOutOfMemoryError");
            args.addAll(com.zrlog.util.NetworkProxy.initialize().launchArguments());
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
        args.add(Integer.toString(hostPort.getAsInt()));
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
        if (externallyScheduled) args.add("--host-scheduler");
        //args end
        List<String> cmd = new ArrayList<>();
        cmd.add(programName(pluginCoreFile));
        cmd.addAll(args);
        return new ProcessBuilder(cmd).redirectOutput(infoLogFile).redirectError(errorLogFile).start();
    }

    long nativeMaxHeapSize() {
        String configured = ConfigKit.get(NATIVE_MAX_HEAP_SIZE_KEY, Long.toString(DEFAULT_NATIVE_MAX_HEAP_SIZE));
        if (!isValidNativeMaxHeapSize(configured)) {
            LOGGER.warning("Invalid " + NATIVE_MAX_HEAP_SIZE_KEY + "=" + configured
                    + ", use " + DEFAULT_NATIVE_MAX_HEAP_SIZE);
        }
        return resolveNativeMaxHeapSize(configured);
    }

    String getInstalledPluginFolder() {
        return pluginsFolder + "/installed-plugins";
    }

    @Override
    public int pluginServerStart(final String dbProperties, final String pluginJvmArgs, final String runtimePath, final String runTimeVersion, String token) {
        return pluginServerStart(dbProperties, pluginJvmArgs, runtimePath, runTimeVersion, token, null);
    }

    @Override
    public int pluginServerStart(final String dbProperties, final String pluginJvmArgs, final String runtimePath,
                                  final String runTimeVersion, String token, final ExecutionDeadline deadline) {
        if (deadline != null && (deadline.remainingMillis() == 0 || Thread.currentThread().isInterrupted())) return -1;
        //简单处理，为了能在一个服务器上面启动多个ZrLog程序，使用Random端口的方式，（感兴趣可以算算概率）
        final int randomServerPort = new Random().nextInt(10000) + 20000;
        final int randomWatcherListenPort = randomServerPort + 30000;
        AbstractPluginCoreProcessHandle handle = null;
        try {
            if (!lockWithin(deadline)) return -1;
            try {
                if (Objects.nonNull(pluginCoreProcessHandle) && !(deadline == null
                        ? pluginCoreProcessHandle.close() : pluginCoreProcessHandle.close(deadline))) {
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
                    private volatile boolean closed;
                    private volatile Thread runnerThread;
                    private volatile PluginGhostWatcher watcher;
                    private volatile ExecutionDeadline startupDeadline = deadline;

                    public void run() {
                        Thread.currentThread().setName("plugin-core-thread");
                        processLifecycleLock.lock();
                        try {
                            runnerThread = Thread.currentThread();
                        } finally {
                            processLifecycleLock.unlock();
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
                            processLifecycleLock.lock();
                            try {
                                if (runnerThread == Thread.currentThread()) {
                                    runnerThread = null;
                                }
                            } finally {
                                processLifecycleLock.unlock();
                            }
                        }
                    }

                    private boolean shouldSupervise() {
                        processLifecycleLock.lock();
                        try {
                            return shouldSuperviseLocked();
                        } finally {
                            processLifecycleLock.unlock();
                        }
                    }

                    private boolean shouldSuperviseLocked() {
                        return !closed && !stopped.get() && !Thread.currentThread().isInterrupted()
                                && (startupDeadline == null || startupDeadline.remainingMillis() > 0);
                    }

                    private boolean stopPreviousProcessBeforeLaunch() throws InterruptedException {
                        while (shouldSupervise()) {
                            Process previousProcess;
                            processLifecycleLock.lock();
                            try {
                                if (!shouldSuperviseLocked()) {
                                    return false;
                                }
                                previousProcess = process;
                                if (Objects.isNull(previousProcess)) {
                                    return true;
                                }
                                if (!previousProcess.isAlive() || (startupDeadline == null
                                        ? stopAndAwaitProcess(previousProcess) : stopAndAwaitProcess(previousProcess, startupDeadline))) {
                                    process = null;
                                    return true;
                                }
                            } finally {
                                processLifecycleLock.unlock();
                            }
                            LOGGER.warning("Unable to stop plugin-core pid=" + processId(previousProcess)
                                    + "; retry stop in " + PROCESS_STOP_RETRY_MILLIS
                                    + " ms before starting a replacement");
                            pauseBeforePluginCoreStopRetry(waitWithin(PROCESS_STOP_RETRY_MILLIS, startupDeadline));
                        }
                        return false;
                    }

                    private boolean pauseBeforeNextLaunch() throws InterruptedException {
                        long delayMillis;
                        processLifecycleLock.lock();
                        try {
                            if (!shouldSuperviseLocked()) {
                                return false;
                            }
                            delayMillis = launchDelayMillis;
                            launchDelayMillis = 0L;
                        } finally {
                            processLifecycleLock.unlock();
                        }
                        if (delayMillis > 0L) {
                            pauseBeforePluginCoreRestart(waitWithin(delayMillis, startupDeadline));
                        }
                        return shouldSupervise();
                    }

                    private boolean startNextProcess() {
                        int nextMasterPort = -1;
                        long currentAttempt = -1L;
                        try {
                            processLifecycleLock.lock();
                            try {
                                if (!shouldSuperviseLocked()) {
                                    return false;
                                }
                                currentAttempt = ++launchAttemptCount;
                                nextMasterPort = nextPluginMasterPort(lastPluginMasterPort);
                                lastPluginMasterPort = nextMasterPort;
                            } finally {
                                processLifecycleLock.unlock();
                            }
                            ExecutionDeadline launchDeadline = startupDeadline;
                            prepareUnzipPlugins(launchDeadline);
                            File coreFile = launchDeadline == null
                                    ? PluginCoreUtils.tryDownloadPluginCoreFile(pluginsFolder.toString())
                                    : PluginCoreUtils.tryDownloadPluginCoreFile(pluginsFolder.toString(), launchDeadline);
                            processLifecycleLock.lock();
                            try {
                                if (!shouldSuperviseLocked()) {
                                    return false;
                                }
                                prepareProcessLogs();
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
                            } finally {
                                processLifecycleLock.unlock();
                            }
                            LOGGER.info("Started plugin-core pid=" + processId(process)
                                    + ", generation=" + processGeneration
                                    + ", launchAttempt=" + currentAttempt
                                    + ", httpPort=" + randomServerPort
                                    + ", masterPort=" + nextMasterPort
                                    + ", watcherPort=" + randomWatcherListenPort);
                            return true;
                        } catch (Exception e) {
                            processLifecycleLock.lock();
                            try {
                                if (!shouldSuperviseLocked()) {
                                    return false;
                                }
                                restartBackoffMillis = nextRestartBackoffMillis(restartBackoffMillis, 0L);
                                launchDelayMillis = restartBackoffMillis;
                            } finally {
                                processLifecycleLock.unlock();
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
                                PluginGhostWatcher currentWatcher = newPluginGhostWatcher("127.0.0.1", randomWatcherListenPort);
                                watcher = currentWatcher;
                                if (!shouldRun()) {
                                    currentWatcher.close();
                                    break;
                                }
                                boolean connected;
                                try { connected = startupDeadline == null ? currentWatcher.doWatch()
                                        : currentWatcher.doWatch(() -> startupDeadline); }
                                finally { currentWatcher.close(); watcher = null; }
                                if (connected) {
                                    watcherConnected = true;
                                }
                                // Avoid a tight loop while the child is still starting.
                                if (startupDeadline == null) pauseAfterPluginGhostWatcher();
                                else Thread.sleep(waitWithin(1000, startupDeadline));
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
                        processLifecycleLock.lock();
                        try {
                            return shouldSuperviseLocked() && Objects.nonNull(process);
                        } finally {
                            processLifecycleLock.unlock();
                        }
                    }

                    private boolean shouldRetryWatcherConnection() {
                        processLifecycleLock.lock();
                        try {
                            return shouldSuperviseLocked() && Objects.nonNull(process) && process.isAlive()
                                    && processRuntimeMillis() < PLUGIN_CORE_READY_TIMEOUT_MILLIS;
                        } finally {
                            processLifecycleLock.unlock();
                        }
                    }

                    private void scheduleProcessRestart(String reason, RuntimeException failure) {
                        long observedRuntimeMillis;
                        long delayMillis;
                        boolean connected;
                        Process unavailableProcess;
                        processLifecycleLock.lock();
                        try {
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
                        } finally {
                            processLifecycleLock.unlock();
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

                    @Override
                    boolean confirmStarted(int port) {
                        if (port != randomServerPort || closed || stopped.get()
                                || process == null || !process.isAlive()
                                || (startupDeadline != null && startupDeadline.remainingMillis() == 0)) return false;
                        startupDeadline = null;
                        return true;
                    }

                    @Override
                    boolean isPendingAt(int port) {
                        return port == randomServerPort && startupDeadline != null;
                    }

                    @Override
                    void cancelStartup() {
                        closed = true;
                        PluginGhostWatcher currentWatcher = watcher;
                        if (currentWatcher != null) currentWatcher.close();
                        Thread thread = runnerThread;
                        if (thread != null && thread != Thread.currentThread()) thread.interrupt();
                    }

                    public boolean close() {
                        return close(startupDeadline);
                    }

                    @Override
                    boolean close(ExecutionDeadline cleanupDeadline) {
                        cancelStartup();
                        if (!lockWithin(cleanupDeadline)) return false;
                        try {
                            boolean processStopped = (cleanupDeadline == null ? stopAndAwaitProcess(process) : stopAndAwaitProcess(process, cleanupDeadline));
                            if (processStopped) process = null;
                            return processStopped;
                        } finally {
                            processLifecycleLock.unlock();
                        }
                    }
                };
                pluginCoreProcessHandle = handle;
            } finally {
                processLifecycleLock.unlock();
            }
            registerShutdownHook();
            startPluginCoreHandle(handle);
            return randomServerPort;
        } catch (Exception e) {
            if (handle != null) handle.close(deadline);
            LOGGER.log(Level.WARNING, "start plugin exception ", e);
            return -1;
        }

    }

    @Override
    public boolean confirmStarted(int port, ExecutionDeadline deadline) {
        if (deadline != null && (deadline.remainingMillis() == 0 || Thread.currentThread().isInterrupted())) return false;
        if (!lockWithin(deadline)) return false;
        try {
            return pluginCoreProcessHandle != null && pluginCoreProcessHandle.confirmStarted(port);
        } finally { processLifecycleLock.unlock(); }
    }

    @Override
    public void cancelStartup(int port, ExecutionDeadline deadline) {
        if (!lockWithin(deadline)) return;
        try {
            AbstractPluginCoreProcessHandle handle = pluginCoreProcessHandle;
            if (handle != null && handle.isPendingAt(port)) {
                handle.cancelStartup();
                if (handle.close(deadline)) pluginCoreProcessHandle = null;
            }
        } finally { processLifecycleLock.unlock(); }
    }

    private boolean lockWithin(ExecutionDeadline deadline) {
        if (deadline == null) {
            processLifecycleLock.lock();
            return true;
        }
        if (Thread.currentThread().isInterrupted()) return processLifecycleLock.tryLock();
        try {
            return processLifecycleLock.tryLock(deadline.remainingMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static long waitWithin(long maximum, ExecutionDeadline deadline) {
        return deadline == null ? maximum : Math.min(maximum, deadline.remainingMillis());
    }

    private void prepareProcessLogs() {
        if (infoLogFile == null || errorLogFile == null) {
            infoLogFile = getLogFile(false);
            errorLogFile = getLogFile(true);
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

    boolean stopAndAwaitProcess(Process process) {
        return stopAndAwaitProcess(process, null);
    }

    boolean stopAndAwaitProcess(Process process, ExecutionDeadline deadline) {
        if (Objects.isNull(process) || !process.isAlive()) {
            return true;
        }
        process.destroy();
        if (awaitProcessExit(process, deadline) || !process.isAlive()) {
            return true;
        }
        process.destroyForcibly();
        if (Thread.currentThread().isInterrupted()) {
            return !process.isAlive();
        }
        return awaitProcessExit(process, deadline) || !process.isAlive();
    }

    private boolean awaitProcessExit(Process process, ExecutionDeadline deadline) {
        try {
            return process.waitFor(waitWithin(PROCESS_STOP_TIMEOUT_MILLIS, deadline), TimeUnit.MILLISECONDS);
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
        processLifecycleLock.lock();
        try {
            shouldNotifyStop = stopped.compareAndSet(false, true);
            AbstractPluginCoreProcessHandle handle = pluginCoreProcessHandle;
            if (Objects.nonNull(handle) && handle.close()) {
                pluginCoreProcessHandle = null;
            }
        } finally {
            processLifecycleLock.unlock();
        }
        if (shouldNotifyStop && Objects.nonNull(onStopRunnable)) {
            onStopRunnable.run();
        }
    }
}

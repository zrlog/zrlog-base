package com.zrlog.business.plugin;

import com.hibegin.http.server.util.PathUtil;
import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.common.CacheService;
import com.zrlog.common.Constants;
import com.zrlog.common.TokenService;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.plugin.IPlugin;
import com.zrlog.plugin.Plugins;
import com.zrlog.util.BlogBuildInfoUtil;
import org.graalvm.nativeimage.ImageInfo;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class PluginCoreProcessImplTest {

    @Test
    public void restartBackoffShouldGrowBoundAndResetAfterStableRuntime() {
        long backoff = PluginCoreProcessImpl.nextRestartBackoffMillis(0L, 1000L);
        assertEquals(5000L, backoff);
        backoff = PluginCoreProcessImpl.nextRestartBackoffMillis(backoff, 1000L);
        assertEquals(10000L, backoff);
        backoff = PluginCoreProcessImpl.nextRestartBackoffMillis(backoff, 1000L);
        assertEquals(20000L, backoff);
        backoff = PluginCoreProcessImpl.nextRestartBackoffMillis(backoff, 1000L);
        assertEquals(40000L, backoff);
        backoff = PluginCoreProcessImpl.nextRestartBackoffMillis(backoff, 1000L);
        assertEquals(60000L, backoff);
        assertEquals(60000L, PluginCoreProcessImpl.nextRestartBackoffMillis(backoff, 1000L));
        assertEquals(5000L, PluginCoreProcessImpl.nextRestartBackoffMillis(
                backoff, PluginCoreProcessImpl.STABLE_PROCESS_RUNTIME_MILLIS));
    }

    @Test
    public void pluginMasterPortShouldRotateWithinDedicatedRange() {
        PluginCoreProcessImpl process = new PluginCoreProcessImpl(null, "/blog");
        int previousPort = -1;
        for (int i = 0; i < 100; i++) {
            int port = process.nextPluginMasterPort(previousPort);
            assertTrue(port >= PluginCoreProcessImpl.PLUGIN_MASTER_PORT_MIN);
            assertTrue(port < PluginCoreProcessImpl.PLUGIN_MASTER_PORT_MAX_EXCLUSIVE);
            assertTrue(port != previousPort);
            previousPort = port;
        }
    }

    @Test
    public void constructorShouldNotCreatePluginCoreLogFiles() throws Exception {
        Path logDir = Files.createTempDirectory("zrlog-plugin-core-log");
        String previousLogPath = System.getProperty("sws.log.path");
        try {
            System.setProperty("sws.log.path", logDir.toString());

            new PluginCoreProcessImpl(null, "");

            try (Stream<Path> paths = Files.list(logDir)) {
                assertFalse(paths.anyMatch(path -> path.getFileName().toString().startsWith("plugin-core-")));
            }
        } finally {
            restoreProperty("sws.log.path", previousLogPath);
            delete(logDir);
        }
    }

    @Test
    public void shouldResolveProgramAndPluginWorkerPaths() throws Exception {
        Path root = Files.createTempDirectory("zrlog-plugin-core-root");
        try {
            PathUtil.setRootPath(root.toString());
            PluginCoreProcessImpl process = new PluginCoreProcessImpl(null, "/blog");

            String javaProgram = process.programName(new File("plugin-core.jar"));
            String workerPath = process.getPluginWorkerPath(new File(root + "/conf/plugins"));
            String installedPlugins = process.getInstalledPluginFolder();

            assertTrue(javaProgram.replace("\\", "/").endsWith("/bin/java"));
            assertEquals(new File(root + "/conf").toString(), workerPath);
            assertTrue(installedPlugins.replace("\\", "/").endsWith("/conf/plugins/installed-plugins"));
        } finally {
            delete(root);
        }
    }

    @Test
    public void shouldUseParentClasspathOnlyForLocalSqlitePluginCore() throws Exception {
        Path root = Files.createTempDirectory("zrlog-plugin-core-jvm-command");
        try {
            File pluginCore = root.resolve("plugin-core.jar").toFile();
            Path sqliteProperties = root.resolve("sqlite.properties");
            Path mysqlProperties = root.resolve("mysql.properties");
            Path webApiProperties = root.resolve("webapi.properties");
            Files.writeString(sqliteProperties, "jdbcUrl=jdbc:sqlite:/tmp/zrlog.db\n", StandardCharsets.UTF_8);
            Files.writeString(mysqlProperties, "jdbcUrl=jdbc:mysql://localhost/zrlog\n", StandardCharsets.UTF_8);
            Files.writeString(webApiProperties, "jdbcUrl=jdbc:webapi://example.com/zrlog\n", StandardCharsets.UTF_8);

            List<String> sqliteArgs = PluginCoreProcessImpl.jvmLaunchArguments(
                    pluginCore, sqliteProperties.toString(), "-Xmx64m");
            assertEquals("-Xmx64m", sqliteArgs.get(0));
            assertEquals("-cp", sqliteArgs.get(1));
            assertEquals(pluginCore + File.pathSeparator + System.getProperty("java.class.path"), sqliteArgs.get(2));
            assertEquals(PluginCoreProcessImpl.PLUGIN_CORE_MAIN_CLASS, sqliteArgs.get(3));
            assertFalse(sqliteArgs.contains("-jar"));

            assertEquals(List.of("-Xmx64m", "-jar", pluginCore.toString()),
                    PluginCoreProcessImpl.jvmLaunchArguments(pluginCore, mysqlProperties.toString(), "-Xmx64m"));
            assertEquals(List.of("-Xmx64m", "-jar", pluginCore.toString()),
                    PluginCoreProcessImpl.jvmLaunchArguments(pluginCore, webApiProperties.toString(), "-Xmx64m"));
            assertEquals(List.of("-Xmx64m", "-jar", pluginCore.toString()),
                    PluginCoreProcessImpl.jvmLaunchArguments(pluginCore, root.resolve("missing.properties").toString(),
                            "-Xmx64m"));
        } finally {
            delete(root);
        }
    }

    @Test
    public void shouldUsePluginCoreFileAsProgramInNativeImage() throws Exception {
        PluginCoreProcessImpl process = new PluginCoreProcessImpl(null, "/blog");
        File pluginCore = new File("plugin-core-Linux-x86_64.bin");
        try {
            ImageInfo.setInImageRuntimeCode(true);

            assertEquals(pluginCore.toString(), process.programName(pluginCore));
        } finally {
            ImageInfo.setInImageRuntimeCode(false);
        }
    }

    @Test
    public void shouldStartNativePluginCoreExecutableWithExpectedArguments() throws Exception {
        Path root = Files.createTempDirectory("zrlog-plugin-core-native");
        Path argsFile = root.resolve("plugin-args.txt");
        Path pluginCore = root.resolve("conf/plugins/plugin-core-test.bin");
        Files.createDirectories(pluginCore.getParent());
        Files.writeString(pluginCore,
                "#!/bin/sh\nprintf '%s\\n' \"$@\" > " + argsFile + "\n",
                StandardCharsets.UTF_8);
        assertTrue(pluginCore.toFile().setExecutable(true));
        String previousRootPath = System.getProperty("sws.root.path");
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        try {
            System.setProperty("sws.root.path", root.toString());
            Constants.zrLogConfig = new TestZrLogConfig();
            ImageInfo.setInImageRuntimeCode(true);
            PluginCoreProcessImpl process = new PluginCoreProcessImpl(null, "/blog");

            Process started = process.startPluginCore(pluginCore.toFile(), "db=ok",
                    "-Xmx64m", root.resolve("static").toString(), "3.6.0", "token-123",
                    21000, 41000, 51000);

            assertNotNull(started);
            assertEquals(0, started.waitFor());
            List<String> args = Files.readAllLines(argsFile);
            assertEquals("-XX:MaxHeapSize=134217728", args.get(0));
            assertEquals("-XX:+ExitOnOutOfMemoryError", args.get(1));
            assertEquals("21000", args.get(2));
            assertEquals("41000", args.get(3));
            assertEquals("db=ok", args.get(4));
            assertTrue(args.get(5).endsWith("conf/plugins/installed-plugins"));
            assertEquals("51000", args.get(6));
            assertEquals(root.resolve("static").toString(), args.get(7));
            assertEquals("3.6.0", args.get(8));
            assertEquals("19084", args.get(9));
            assertEquals("token-123", args.get(10));
            assertEquals("/blog", args.get(12));
            assertTrue(args.get(13).startsWith("-Duser.dir="));
        } finally {
            ImageInfo.setInImageRuntimeCode(false);
            Constants.zrLogConfig = previousConfig;
            restoreProperty("sws.root.path", previousRootPath);
            delete(root);
        }
    }

    @Test
    public void nativeHeapSizeShouldUseValidatedByteRange() {
        assertEquals(PluginCoreProcessImpl.DEFAULT_NATIVE_MAX_HEAP_SIZE,
                PluginCoreProcessImpl.resolveNativeMaxHeapSize(null));
        assertEquals(PluginCoreProcessImpl.DEFAULT_NATIVE_MAX_HEAP_SIZE,
                PluginCoreProcessImpl.resolveNativeMaxHeapSize("not-a-number"));
        assertEquals(PluginCoreProcessImpl.DEFAULT_NATIVE_MAX_HEAP_SIZE,
                PluginCoreProcessImpl.resolveNativeMaxHeapSize("67108863"));
        assertEquals(PluginCoreProcessImpl.MIN_NATIVE_MAX_HEAP_SIZE,
                PluginCoreProcessImpl.resolveNativeMaxHeapSize("67108864"));
        assertEquals(PluginCoreProcessImpl.DEFAULT_NATIVE_MAX_HEAP_SIZE,
                PluginCoreProcessImpl.resolveNativeMaxHeapSize("134217728"));
        assertEquals(PluginCoreProcessImpl.MAX_NATIVE_MAX_HEAP_SIZE,
                PluginCoreProcessImpl.resolveNativeMaxHeapSize("536870912"));
        assertEquals(PluginCoreProcessImpl.DEFAULT_NATIVE_MAX_HEAP_SIZE,
                PluginCoreProcessImpl.resolveNativeMaxHeapSize("536870913"));
        assertEquals(PluginCoreProcessImpl.DEFAULT_NATIVE_MAX_HEAP_SIZE,
                PluginCoreProcessImpl.resolveNativeMaxHeapSize("999999999999999999999999"));
    }

    @Test
    public void shouldReturnNullWhenPluginCoreFileIsMissing() throws Exception {
        Path root = Files.createTempDirectory("zrlog-plugin-core-missing");
        String previousRootPath = System.getProperty("sws.root.path");
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        try {
            System.setProperty("sws.root.path", root.toString());
            Constants.zrLogConfig = new TestZrLogConfig();
            PluginCoreProcessImpl process = new PluginCoreProcessImpl(null, "/blog");

            Process started = process.startPluginCore(root.resolve("missing.jar").toFile(),
                    "db=ok", "-Xmx64m", root.resolve("static").toString(), "3.6.0", "token-123",
                    21000, 41000, 51000);

            assertEquals(null, started);
        } finally {
            Constants.zrLogConfig = previousConfig;
            restoreProperty("sws.root.path", previousRootPath);
            delete(root);
        }
    }

    @Test
    public void shouldUsePlaceholderContextPathWhenContextPathIsRoot() throws Exception {
        Path root = Files.createTempDirectory("zrlog-plugin-core-root-context");
        Path argsFile = root.resolve("plugin-args.txt");
        Path pluginCore = root.resolve("conf/plugins/plugin-core-test.bin");
        Files.createDirectories(pluginCore.getParent());
        Files.writeString(pluginCore,
                "#!/bin/sh\nprintf '%s\\n' \"$@\" > " + argsFile + "\n",
                StandardCharsets.UTF_8);
        assertTrue(pluginCore.toFile().setExecutable(true));
        String previousRootPath = System.getProperty("sws.root.path");
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        try {
            System.setProperty("sws.root.path", root.toString());
            Constants.zrLogConfig = new TestZrLogConfig();
            ImageInfo.setInImageRuntimeCode(true);
            PluginCoreProcessImpl process = new PluginCoreProcessImpl(null, "/");

            Process started = process.startPluginCore(pluginCore.toFile(), "db=ok",
                    "-Xmx64m", root.resolve("static").toString(), "3.6.0", "token-123",
                    21000, 41000, 51000);

            assertNotNull(started);
            assertEquals(0, started.waitFor());
            List<String> args = Files.readAllLines(argsFile);
            assertEquals("#", args.get(12));
        } finally {
            ImageInfo.setInImageRuntimeCode(false);
            Constants.zrLogConfig = previousConfig;
            restoreProperty("sws.root.path", previousRootPath);
            delete(root);
        }
    }

    @Test
    public void pluginServerStartShouldBuildRunnableHandleWithoutBackgroundThread() throws Exception {
        Path root = Files.createTempDirectory("zrlog-plugin-core-handle");
        Path argsFile = root.resolve("plugin-args.txt");
        Path pluginCore = root.resolve("conf/plugins/plugin-core-Linux-x86_64.bin");
        Files.createDirectories(pluginCore.getParent());
        Files.writeString(pluginCore,
                "#!/bin/sh\nprintf 'launch %s %s %s\\n' \"$3\" \"$4\" \"$7\" >> " + argsFile + "\n",
                StandardCharsets.UTF_8);
        assertTrue(pluginCore.toFile().setExecutable(true));
        String previousRootPath = System.getProperty("sws.root.path");
        ZrLogConfig previousConfig = Constants.zrLogConfig;
        String previousFileArch = setFileArch("Linux-x86_64");
        try {
            System.setProperty("sws.root.path", root.toString());
            Constants.zrLogConfig = new TestZrLogConfig();
            ImageInfo.setInImageRuntimeCode(true);
            TestablePluginCoreProcessImpl process = new TestablePluginCoreProcessImpl(null, "/blog");

            int port = process.pluginServerStart("db=ok", "-Xmx64m", root.resolve("static").toString(),
                    "3.6.0", "token-456");

            assertTrue(port >= 20000);
            assertNotNull(process.handle.get());
            process.handle.get().run();
            assertTrue(awaitFile(argsFile));
            process.stopPluginCore();

            List<String> launches = Files.readAllLines(argsFile);
            assertEquals(1, launches.size());
            assertTrue(launches.get(0).contains(String.valueOf(port)));
            assertEquals(1, process.handleStartCount.get());
            assertEquals(1, process.watcherCount.get());
        } finally {
            ImageInfo.setInImageRuntimeCode(false);
            setFileArch(previousFileArch);
            Constants.zrLogConfig = previousConfig;
            restoreProperty("sws.root.path", previousRootPath);
            delete(root);
        }
    }

    @Test
    public void watcherShouldRetryStartupConnectionAndStopOldProcessBeforeReplacement() throws Exception {
        Path root = Files.createTempDirectory("zrlog-plugin-core-restart");
        Path pluginCore = root.resolve("conf/plugins/plugin-core-Linux-x86_64.bin");
        Files.createDirectories(pluginCore.getParent());
        Files.writeString(pluginCore, "fake-plugin-core", StandardCharsets.UTF_8);
        String previousRootPath = System.getProperty("sws.root.path");
        String previousFileArch = setFileArch("Linux-x86_64");
        ControlledProcess oldProcess = new ControlledProcess(false, true);
        ControlledProcess replacementProcess = new ControlledProcess(true, true);
        try {
            System.setProperty("sws.root.path", root.toString());
            ImageInfo.setInImageRuntimeCode(true);
            RestartTrackingPluginCoreProcessImpl process = new RestartTrackingPluginCoreProcessImpl(
                    oldProcess, replacementProcess, 1);
            setField(process, "infoLogFile", Files.createFile(root.resolve("plugin-info.log")).toFile());
            setField(process, "errorLogFile", Files.createFile(root.resolve("plugin-error.log")).toFile());

            process.pluginServerStart("db=ok", "-Xmx64m", root.resolve("static").toString(),
                    "3.6.0", "token-789");
            process.handle.get().run();

            assertEquals(2, process.startCount.get());
            assertEquals(3, process.watcherCount.get());
            assertEquals(1, process.restartDelays.size());
            assertEquals(PluginCoreProcessImpl.MIN_RESTART_BACKOFF_MILLIS,
                    process.restartDelays.get(0).longValue());
            assertFalse(process.oldProcessAliveAtReplacement.get());
            assertEquals(1, oldProcess.destroyCount.get());
            assertEquals(1, oldProcess.destroyForciblyCount.get());
            assertFalse(oldProcess.isAlive());
            assertFalse(replacementProcess.isAlive());
        } finally {
            ImageInfo.setInImageRuntimeCode(false);
            setFileArch(previousFileArch);
            restoreProperty("sws.root.path", previousRootPath);
            delete(root);
        }
    }

    @Test
    public void watcherShouldKeepUsingLiveProcessWhileStartupConnectionFails() throws Exception {
        Path root = Files.createTempDirectory("zrlog-plugin-core-connect-retry");
        Path pluginCore = root.resolve("conf/plugins/plugin-core-Linux-x86_64.bin");
        Files.createDirectories(pluginCore.getParent());
        Files.writeString(pluginCore, "fake-plugin-core", StandardCharsets.UTF_8);
        String previousRootPath = System.getProperty("sws.root.path");
        String previousFileArch = setFileArch("Linux-x86_64");
        ControlledProcess oldProcess = new ControlledProcess(true, true);
        ControlledProcess replacementProcess = new ControlledProcess(true, true);
        try {
            System.setProperty("sws.root.path", root.toString());
            ImageInfo.setInImageRuntimeCode(true);
            RestartTrackingPluginCoreProcessImpl process = new RestartTrackingPluginCoreProcessImpl(
                    oldProcess, replacementProcess, 3, 3);
            setField(process, "infoLogFile", Files.createFile(root.resolve("plugin-info.log")).toFile());
            setField(process, "errorLogFile", Files.createFile(root.resolve("plugin-error.log")).toFile());

            process.pluginServerStart("db=ok", "-Xmx64m", root.resolve("static").toString(),
                    "3.6.0", "token-connect-retry");
            process.handle.get().run();

            assertEquals(1, process.startCount.get());
            assertEquals(3, process.watcherCount.get());
            assertTrue(process.restartDelays.isEmpty());
            assertFalse(oldProcess.isAlive());
        } finally {
            ImageInfo.setInImageRuntimeCode(false);
            setFileArch(previousFileArch);
            restoreProperty("sws.root.path", previousRootPath);
            delete(root);
        }
    }

    @Test
    public void watcherShouldReplaceLiveProcessThatNeverBecomesReady() throws Exception {
        Path root = Files.createTempDirectory("zrlog-plugin-core-ready-timeout");
        Path pluginCore = root.resolve("conf/plugins/plugin-core-Linux-x86_64.bin");
        Files.createDirectories(pluginCore.getParent());
        Files.writeString(pluginCore, "fake-plugin-core", StandardCharsets.UTF_8);
        String previousRootPath = System.getProperty("sws.root.path");
        String previousFileArch = setFileArch("Linux-x86_64");
        ControlledProcess oldProcess = new ControlledProcess(false, true);
        ControlledProcess replacementProcess = new ControlledProcess(true, true);
        try {
            System.setProperty("sws.root.path", root.toString());
            ImageInfo.setInImageRuntimeCode(true);
            RestartTrackingPluginCoreProcessImpl process = new RestartTrackingPluginCoreProcessImpl(
                    oldProcess, replacementProcess, Integer.MAX_VALUE, 2,
                    PluginCoreProcessImpl.PLUGIN_CORE_READY_TIMEOUT_MILLIS);
            setField(process, "infoLogFile", Files.createFile(root.resolve("plugin-info.log")).toFile());
            setField(process, "errorLogFile", Files.createFile(root.resolve("plugin-error.log")).toFile());

            process.pluginServerStart("db=ok", "-Xmx64m", root.resolve("static").toString(),
                    "3.6.0", "token-ready-timeout");
            process.handle.get().run();

            assertEquals(2, process.startCount.get());
            assertEquals(2, process.watcherCount.get());
            assertEquals(1, process.restartDelays.size());
            assertFalse(process.oldProcessAliveAtReplacement.get());
            assertFalse(oldProcess.isAlive());
            assertFalse(replacementProcess.isAlive());
        } finally {
            ImageInfo.setInImageRuntimeCode(false);
            setFileArch(previousFileArch);
            restoreProperty("sws.root.path", previousRootPath);
            delete(root);
        }
    }

    @Test
    public void watcherShouldIncreaseBackoffAcrossConsecutiveFastCrashes() throws Exception {
        Path root = Files.createTempDirectory("zrlog-plugin-core-crash-backoff");
        Path pluginCore = root.resolve("conf/plugins/plugin-core-Linux-x86_64.bin");
        Files.createDirectories(pluginCore.getParent());
        Files.writeString(pluginCore, "fake-plugin-core", StandardCharsets.UTF_8);
        String previousRootPath = System.getProperty("sws.root.path");
        String previousFileArch = setFileArch("Linux-x86_64");
        try {
            System.setProperty("sws.root.path", root.toString());
            ImageInfo.setInImageRuntimeCode(true);
            CrashLoopPluginCoreProcessImpl process = new CrashLoopPluginCoreProcessImpl(1000L, 1000L);
            setField(process, "infoLogFile", Files.createFile(root.resolve("plugin-info.log")).toFile());
            setField(process, "errorLogFile", Files.createFile(root.resolve("plugin-error.log")).toFile());

            process.pluginServerStart("db=ok", "-Xmx64m", root.resolve("static").toString(),
                    "3.6.0", "token-crash-backoff");
            process.handle.get().run();

            assertEquals(3, process.startCount.get());
            assertEquals(2, process.restartDelays.size());
            assertEquals(5000L, process.restartDelays.get(0).longValue());
            assertEquals(10000L, process.restartDelays.get(1).longValue());
            assertEquals(3, process.masterPorts.size());
            assertTrue(!process.masterPorts.get(0).equals(process.masterPorts.get(1)));
            assertTrue(!process.masterPorts.get(1).equals(process.masterPorts.get(2)));
        } finally {
            ImageInfo.setInImageRuntimeCode(false);
            setFileArch(previousFileArch);
            restoreProperty("sws.root.path", previousRootPath);
            delete(root);
        }
    }

    @Test
    public void watcherShouldRetryWhenRestartLaunchThrowsIOException() throws Exception {
        Path root = Files.createTempDirectory("zrlog-plugin-core-restart-io");
        Path pluginCore = root.resolve("conf/plugins/plugin-core-Linux-x86_64.bin");
        Files.createDirectories(pluginCore.getParent());
        Files.writeString(pluginCore, "fake-plugin-core", StandardCharsets.UTF_8);
        String previousRootPath = System.getProperty("sws.root.path");
        String previousFileArch = setFileArch("Linux-x86_64");
        try {
            System.setProperty("sws.root.path", root.toString());
            ImageInfo.setInImageRuntimeCode(true);
            CrashLoopPluginCoreProcessImpl process = new CrashLoopPluginCoreProcessImpl(1, 1000L);
            setField(process, "infoLogFile", Files.createFile(root.resolve("plugin-info.log")).toFile());
            setField(process, "errorLogFile", Files.createFile(root.resolve("plugin-error.log")).toFile());

            process.pluginServerStart("db=ok", "-Xmx64m", root.resolve("static").toString(),
                    "3.6.0", "token-restart-io");
            process.handle.get().run();

            assertEquals(3, process.startCount.get());
            assertEquals(2, process.watcherCount.get());
            assertEquals(2, process.restartDelays.size());
            assertEquals(5000L, process.restartDelays.get(0).longValue());
            assertEquals(10000L, process.restartDelays.get(1).longValue());
        } finally {
            ImageInfo.setInImageRuntimeCode(false);
            setFileArch(previousFileArch);
            restoreProperty("sws.root.path", previousRootPath);
            delete(root);
        }
    }

    @Test
    public void supervisorShouldRetryInitialIOExceptionAndNullLaunchWithRotatingMasterPorts() throws Exception {
        Path root = Files.createTempDirectory("zrlog-plugin-core-initial-retry");
        Path pluginCore = root.resolve("conf/plugins/plugin-core-Linux-x86_64.bin");
        Files.createDirectories(pluginCore.getParent());
        Files.writeString(pluginCore, "fake-plugin-core", StandardCharsets.UTF_8);
        String previousRootPath = System.getProperty("sws.root.path");
        String previousFileArch = setFileArch("Linux-x86_64");
        try {
            System.setProperty("sws.root.path", root.toString());
            ImageInfo.setInImageRuntimeCode(true);
            InitialLaunchRetryPluginCoreProcessImpl process = new InitialLaunchRetryPluginCoreProcessImpl();
            setField(process, "infoLogFile", Files.createFile(root.resolve("plugin-info.log")).toFile());
            setField(process, "errorLogFile", Files.createFile(root.resolve("plugin-error.log")).toFile());

            int serverPort = process.pluginServerStart("db=ok", "-Xmx64m", root.resolve("static").toString(),
                    "3.6.0", "token-initial-retry");
            process.handle.get().run();

            assertEquals(3, process.startCount.get());
            assertEquals(1, process.watcherCount.get());
            assertEquals(List.of(5000L, 10000L), process.restartDelays);
            assertEquals(List.of(40000, 40001, 40002), process.masterPorts);
            assertEquals(List.of(serverPort, serverPort, serverPort), process.serverPorts);
            assertEquals(1, process.watcherPorts.stream().distinct().count());
            assertFalse(process.successfulProcess.isAlive());
        } finally {
            ImageInfo.setInImageRuntimeCode(false);
            setFileArch(previousFileArch);
            restoreProperty("sws.root.path", previousRootPath);
            delete(root);
        }
    }

    @Test
    public void supervisorShouldRecoverAfterWatcherRuntimeException() throws Exception {
        Path root = Files.createTempDirectory("zrlog-plugin-core-watcher-runtime");
        Path pluginCore = root.resolve("conf/plugins/plugin-core-Linux-x86_64.bin");
        Files.createDirectories(pluginCore.getParent());
        Files.writeString(pluginCore, "fake-plugin-core", StandardCharsets.UTF_8);
        String previousRootPath = System.getProperty("sws.root.path");
        String previousFileArch = setFileArch("Linux-x86_64");
        try {
            System.setProperty("sws.root.path", root.toString());
            ImageInfo.setInImageRuntimeCode(true);
            RecoverableWatcherFailurePluginCoreProcessImpl process =
                    new RecoverableWatcherFailurePluginCoreProcessImpl();
            setField(process, "infoLogFile", Files.createFile(root.resolve("plugin-info.log")).toFile());
            setField(process, "errorLogFile", Files.createFile(root.resolve("plugin-error.log")).toFile());

            process.pluginServerStart("db=ok", "-Xmx64m", root.resolve("static").toString(),
                    "3.6.0", "token-watcher-runtime");
            process.handle.get().run();

            assertEquals(2, process.startCount.get());
            assertEquals(2, process.watcherCount.get());
            assertEquals(List.of(5000L), process.restartDelays);
            assertFalse(process.processes.get(0).isAlive());
            assertFalse(process.processes.get(1).isAlive());
        } finally {
            ImageInfo.setInImageRuntimeCode(false);
            setFileArch(previousFileArch);
            restoreProperty("sws.root.path", previousRootPath);
            delete(root);
        }
    }

    @Test
    public void stopShouldInterruptRestartBackoffAndPreventReplacement() throws Exception {
        Path root = Files.createTempDirectory("zrlog-plugin-core-stop-backoff");
        Path pluginCore = root.resolve("conf/plugins/plugin-core-Linux-x86_64.bin");
        Files.createDirectories(pluginCore.getParent());
        Files.writeString(pluginCore, "fake-plugin-core", StandardCharsets.UTF_8);
        String previousRootPath = System.getProperty("sws.root.path");
        String previousFileArch = setFileArch("Linux-x86_64");
        CrashLoopPluginCoreProcessImpl process = new CrashLoopPluginCoreProcessImpl(true, 1000L);
        try {
            System.setProperty("sws.root.path", root.toString());
            ImageInfo.setInImageRuntimeCode(true);
            setField(process, "infoLogFile", Files.createFile(root.resolve("plugin-info.log")).toFile());
            setField(process, "errorLogFile", Files.createFile(root.resolve("plugin-error.log")).toFile());

            process.pluginServerStart("db=ok", "-Xmx64m", root.resolve("static").toString(),
                    "3.6.0", "token-stop-backoff");
            assertTrue(process.backoffEntered.await(1, TimeUnit.SECONDS));

            process.stopPluginCore();
            Thread runner = process.runnerThread.get();
            runner.join(1000L);

            assertFalse(runner.isAlive());
            assertEquals(1, process.startCount.get());
        } finally {
            process.stopPluginCore();
            ImageInfo.setInImageRuntimeCode(false);
            setFileArch(previousFileArch);
            restoreProperty("sws.root.path", previousRootPath);
            delete(root);
        }
    }

    @Test
    public void watcherShouldRestartAfterOldProcessEventuallyStops() throws Exception {
        Path root = Files.createTempDirectory("zrlog-plugin-core-still-alive");
        Path pluginCore = root.resolve("conf/plugins/plugin-core-Linux-x86_64.bin");
        Files.createDirectories(pluginCore.getParent());
        Files.writeString(pluginCore, "fake-plugin-core", StandardCharsets.UTF_8);
        String previousRootPath = System.getProperty("sws.root.path");
        String previousFileArch = setFileArch("Linux-x86_64");
        AtomicInteger forcedStopAttempts = new AtomicInteger();
        ControlledProcess oldProcess = new ControlledProcess(false, false) {
            @Override
            public Process destroyForcibly() {
                Process result = super.destroyForcibly();
                if (forcedStopAttempts.incrementAndGet() >= 2) {
                    markExited();
                }
                return result;
            }
        };
        ControlledProcess replacementProcess = new ControlledProcess(true, true);
        try {
            System.setProperty("sws.root.path", root.toString());
            ImageInfo.setInImageRuntimeCode(true);
            RestartTrackingPluginCoreProcessImpl process = new RestartTrackingPluginCoreProcessImpl(
                    oldProcess, replacementProcess, 0);
            setField(process, "infoLogFile", Files.createFile(root.resolve("plugin-info.log")).toFile());
            setField(process, "errorLogFile", Files.createFile(root.resolve("plugin-error.log")).toFile());

            process.pluginServerStart("db=ok", "-Xmx64m", root.resolve("static").toString(),
                    "3.6.0", "token-999");
            process.handle.get().run();

            assertEquals(2, process.startCount.get());
            assertFalse(oldProcess.isAlive());
            assertTrue(oldProcess.destroyCount.get() >= 1);
            assertTrue(oldProcess.destroyForciblyCount.get() >= 2);
            assertFalse(replacementProcess.isAlive());
        } finally {
            oldProcess.markExited();
            ImageInfo.setInImageRuntimeCode(false);
            setFileArch(previousFileArch);
            restoreProperty("sws.root.path", previousRootPath);
            delete(root);
        }
    }

    @Test
    public void stopPluginCoreShouldCloseHandleAndRunCallbackOnlyOnce() throws Exception {
        AtomicInteger stopCallbacks = new AtomicInteger();
        PluginCoreProcessImpl process = new PluginCoreProcessImpl(stopCallbacks::incrementAndGet, "/blog");
        TestPluginCoreProcessHandle handle = new TestPluginCoreProcessHandle();
        Field field = PluginCoreProcessImpl.class.getDeclaredField("pluginCoreProcessHandle");
        field.setAccessible(true);
        field.set(process, handle);

        process.stopPluginCore();
        process.stopPluginCore();

        assertEquals(1, handle.closeCount);
        assertEquals(1, stopCallbacks.get());
    }

    @Test
    public void stopPluginCoreShouldRetryHandleCloseAfterFailure() throws Exception {
        AtomicInteger stopCallbacks = new AtomicInteger();
        PluginCoreProcessImpl process = new PluginCoreProcessImpl(stopCallbacks::incrementAndGet, "/blog");
        TestPluginCoreProcessHandle handle = new TestPluginCoreProcessHandle(1);
        Field field = PluginCoreProcessImpl.class.getDeclaredField("pluginCoreProcessHandle");
        field.setAccessible(true);
        field.set(process, handle);

        process.stopPluginCore();
        process.stopPluginCore();

        assertEquals(2, handle.closeCount);
        assertEquals(1, stopCallbacks.get());
        assertEquals(null, field.get(process));
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static boolean awaitFile(Path path) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(path)) {
                return true;
            }
            Thread.sleep(5L);
        }
        return Files.exists(path);
    }

    private static void delete(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(path)) {
            paths.sorted(Comparator.reverseOrder()).forEach(item -> {
                try {
                    Files.deleteIfExists(item);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    private static String setFileArch(String value) throws Exception {
        Field fileArch = BlogBuildInfoUtil.class.getDeclaredField("fileArch");
        fileArch.setAccessible(true);
        String previousFileArch = (String) fileArch.get(null);
        fileArch.set(null, value);
        return previousFileArch;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = PluginCoreProcessImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class TestPluginCoreProcessHandle extends AbstractPluginCoreProcessHandle {

        private final int failuresBeforeSuccess;
        private int closeCount;

        TestPluginCoreProcessHandle() {
            this(0);
        }

        TestPluginCoreProcessHandle(int failuresBeforeSuccess) {
            this.failuresBeforeSuccess = failuresBeforeSuccess;
        }

        @Override
        public void run() {
        }

        @Override
        boolean close() {
            closeCount++;
            return closeCount > failuresBeforeSuccess;
        }
    }

    private static class RestartTrackingPluginCoreProcessImpl extends PluginCoreProcessImpl {

        private final ControlledProcess oldProcess;
        private final ControlledProcess replacementProcess;
        private final int failedWatcherConnections;
        private final int stopAfterWatcherAttempts;
        private final long watcherRetryElapsedMillis;
        private final AtomicReference<AbstractPluginCoreProcessHandle> handle = new AtomicReference<>();
        private final AtomicInteger startCount = new AtomicInteger();
        private final AtomicInteger watcherCount = new AtomicInteger();
        private final AtomicBoolean oldProcessAliveAtReplacement = new AtomicBoolean();
        private final AtomicLong clockNanos = new AtomicLong();
        private final List<Long> restartDelays = new ArrayList<>();

        RestartTrackingPluginCoreProcessImpl(ControlledProcess oldProcess, ControlledProcess replacementProcess,
                                             int failedWatcherConnections) {
            this(oldProcess, replacementProcess, failedWatcherConnections, Integer.MAX_VALUE);
        }

        RestartTrackingPluginCoreProcessImpl(ControlledProcess oldProcess, ControlledProcess replacementProcess,
                                             int failedWatcherConnections, int stopAfterWatcherAttempts) {
            this(oldProcess, replacementProcess, failedWatcherConnections, stopAfterWatcherAttempts, 0L);
        }

        RestartTrackingPluginCoreProcessImpl(ControlledProcess oldProcess, ControlledProcess replacementProcess,
                                             int failedWatcherConnections, int stopAfterWatcherAttempts,
                                             long watcherRetryElapsedMillis) {
            super(null, "/blog");
            this.oldProcess = oldProcess;
            this.replacementProcess = replacementProcess;
            this.failedWatcherConnections = failedWatcherConnections;
            this.stopAfterWatcherAttempts = stopAfterWatcherAttempts;
            this.watcherRetryElapsedMillis = watcherRetryElapsedMillis;
        }

        @Override
        Process startPluginCore(File pluginCoreFile, String dbProperties, String pluginJvmArgs, String runtimePath,
                                String runTimeVersion, String token, int randomServerPort,
                                int pluginMasterPort, int randomWatcherListenPort) {
            if (startCount.incrementAndGet() == 1) {
                return oldProcess;
            }
            oldProcessAliveAtReplacement.set(oldProcess.isAlive());
            return replacementProcess;
        }

        @Override
        PluginGhostWatcher newPluginGhostWatcher(String host, int port) {
            return new PluginGhostWatcher(host, port, 0) {
                @Override
                public boolean doWatch() {
                    int currentWatcherCount = watcherCount.incrementAndGet();
                    if (currentWatcherCount >= stopAfterWatcherAttempts) {
                        RestartTrackingPluginCoreProcessImpl.this.stopPluginCore();
                        return false;
                    }
                    if (currentWatcherCount <= failedWatcherConnections) {
                        return false;
                    }
                    if (currentWatcherCount >= failedWatcherConnections + 2) {
                        RestartTrackingPluginCoreProcessImpl.this.stopPluginCore();
                    }
                    return true;
                }
            };
        }

        @Override
        void pauseAfterPluginGhostWatcher() {
            clockNanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(watcherRetryElapsedMillis));
        }

        @Override
        void pauseBeforePluginCoreRestart(long millis) {
            restartDelays.add(millis);
            clockNanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis));
        }

        @Override
        void pauseBeforePluginCoreStopRetry(long millis) {
            clockNanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis));
        }

        @Override
        long monotonicTimeNanos() {
            return clockNanos.get();
        }

        @Override
        Thread startPluginCoreHandle(AbstractPluginCoreProcessHandle handle) {
            this.handle.set(handle);
            return new Thread(handle, "captured-plugin-core-restart-thread");
        }
    }

    private static class InitialLaunchRetryPluginCoreProcessImpl extends PluginCoreProcessImpl {

        private final AtomicReference<AbstractPluginCoreProcessHandle> handle = new AtomicReference<>();
        private final AtomicInteger startCount = new AtomicInteger();
        private final AtomicInteger watcherCount = new AtomicInteger();
        private final List<Long> restartDelays = new ArrayList<>();
        private final List<Integer> serverPorts = new ArrayList<>();
        private final List<Integer> masterPorts = new ArrayList<>();
        private final List<Integer> watcherPorts = new ArrayList<>();
        private final ControlledProcess successfulProcess = new ControlledProcess(true, true);

        private InitialLaunchRetryPluginCoreProcessImpl() {
            super(null, "/blog");
        }

        @Override
        Process startPluginCore(File pluginCoreFile, String dbProperties, String pluginJvmArgs, String runtimePath,
                                String runTimeVersion, String token, int randomServerPort, int pluginMasterPort,
                                int randomWatcherListenPort) throws IOException {
            int attempt = startCount.incrementAndGet();
            serverPorts.add(randomServerPort);
            masterPorts.add(pluginMasterPort);
            watcherPorts.add(randomWatcherListenPort);
            if (attempt == 1) {
                throw new IOException("simulated initial plugin-core launch failure");
            }
            if (attempt == 2) {
                return null;
            }
            return successfulProcess;
        }

        @Override
        int nextPluginMasterPort(int previousPort) {
            return previousPort < PLUGIN_MASTER_PORT_MIN ? PLUGIN_MASTER_PORT_MIN : previousPort + 1;
        }

        @Override
        PluginGhostWatcher newPluginGhostWatcher(String host, int port) {
            return new PluginGhostWatcher(host, port, 0L) {
                @Override
                public boolean doWatch() {
                    watcherCount.incrementAndGet();
                    InitialLaunchRetryPluginCoreProcessImpl.this.stopPluginCore();
                    return true;
                }
            };
        }

        @Override
        void pauseAfterPluginGhostWatcher() {
        }

        @Override
        void pauseBeforePluginCoreRestart(long millis) {
            restartDelays.add(millis);
        }

        @Override
        Thread startPluginCoreHandle(AbstractPluginCoreProcessHandle handle) {
            this.handle.set(handle);
            return new Thread(handle, "captured-plugin-core-initial-retry-thread");
        }
    }

    private static class RecoverableWatcherFailurePluginCoreProcessImpl extends PluginCoreProcessImpl {

        private final AtomicReference<AbstractPluginCoreProcessHandle> handle = new AtomicReference<>();
        private final AtomicInteger startCount = new AtomicInteger();
        private final AtomicInteger watcherCount = new AtomicInteger();
        private final List<Long> restartDelays = new ArrayList<>();
        private final List<ControlledProcess> processes = List.of(
                new ControlledProcess(true, true), new ControlledProcess(true, true));

        private RecoverableWatcherFailurePluginCoreProcessImpl() {
            super(null, "/blog");
        }

        @Override
        Process startPluginCore(File pluginCoreFile, String dbProperties, String pluginJvmArgs, String runtimePath,
                                String runTimeVersion, String token, int randomServerPort, int pluginMasterPort,
                                int randomWatcherListenPort) {
            return processes.get(startCount.getAndIncrement());
        }

        @Override
        PluginGhostWatcher newPluginGhostWatcher(String host, int port) {
            return new PluginGhostWatcher(host, port, 0L) {
                @Override
                public boolean doWatch() {
                    if (watcherCount.incrementAndGet() == 1) {
                        throw new IllegalStateException("simulated watcher runtime failure");
                    }
                    RecoverableWatcherFailurePluginCoreProcessImpl.this.stopPluginCore();
                    return true;
                }
            };
        }

        @Override
        void pauseAfterPluginGhostWatcher() {
        }

        @Override
        void pauseBeforePluginCoreRestart(long millis) {
            restartDelays.add(millis);
        }

        @Override
        Thread startPluginCoreHandle(AbstractPluginCoreProcessHandle handle) {
            this.handle.set(handle);
            return new Thread(handle, "captured-plugin-core-watcher-runtime-thread");
        }
    }

    private static class ControlledProcess extends Process {

        private final boolean exitsOnDestroy;
        private final boolean exitsOnDestroyForcibly;
        private final AtomicBoolean alive = new AtomicBoolean(true);
        private final AtomicInteger destroyCount = new AtomicInteger();
        private final AtomicInteger destroyForciblyCount = new AtomicInteger();

        ControlledProcess(boolean exitsOnDestroy, boolean exitsOnDestroyForcibly) {
            this.exitsOnDestroy = exitsOnDestroy;
            this.exitsOnDestroyForcibly = exitsOnDestroyForcibly;
        }

        @Override
        public OutputStream getOutputStream() {
            return OutputStream.nullOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public InputStream getErrorStream() {
            return InputStream.nullInputStream();
        }

        @Override
        public int waitFor() {
            return exitValue();
        }

        @Override
        public boolean waitFor(long timeout, TimeUnit unit) {
            return !alive.get();
        }

        @Override
        public int exitValue() {
            if (alive.get()) {
                throw new IllegalThreadStateException("process is still alive");
            }
            return 0;
        }

        @Override
        public void destroy() {
            destroyCount.incrementAndGet();
            if (exitsOnDestroy) {
                alive.set(false);
            }
        }

        @Override
        public Process destroyForcibly() {
            destroyForciblyCount.incrementAndGet();
            if (exitsOnDestroyForcibly) {
                alive.set(false);
            }
            return this;
        }

        @Override
        public boolean isAlive() {
            return alive.get();
        }

        void markExited() {
            alive.set(false);
        }
    }

    private static class CrashLoopPluginCoreProcessImpl extends PluginCoreProcessImpl {

        private final long[] runtimesBeforeCrashMillis;
        private final int restartLaunchFailures;
        private final List<ControlledProcess> processes = new ArrayList<>();
        private final AtomicReference<AbstractPluginCoreProcessHandle> handle = new AtomicReference<>();
        private final AtomicReference<ControlledProcess> currentProcess = new AtomicReference<>();
        private final AtomicInteger startCount = new AtomicInteger();
        private final AtomicInteger watcherCount = new AtomicInteger();
        private final AtomicLong clockNanos = new AtomicLong();
        private final List<Long> restartDelays = new ArrayList<>();
        private final List<Integer> masterPorts = new ArrayList<>();
        private final boolean runAsync;
        private final CountDownLatch backoffEntered = new CountDownLatch(1);
        private final AtomicReference<Thread> runnerThread = new AtomicReference<>();
        private final AtomicInteger successfulStartCount = new AtomicInteger();

        private CrashLoopPluginCoreProcessImpl(long... runtimesBeforeCrashMillis) {
            this(false, 0, runtimesBeforeCrashMillis);
        }

        private CrashLoopPluginCoreProcessImpl(boolean runAsync, long... runtimesBeforeCrashMillis) {
            this(runAsync, 0, runtimesBeforeCrashMillis);
        }

        private CrashLoopPluginCoreProcessImpl(int restartLaunchFailures, long... runtimesBeforeCrashMillis) {
            this(false, restartLaunchFailures, runtimesBeforeCrashMillis);
        }

        private CrashLoopPluginCoreProcessImpl(boolean runAsync, int restartLaunchFailures,
                                                long... runtimesBeforeCrashMillis) {
            super(null, "/blog");
            this.runAsync = runAsync;
            this.restartLaunchFailures = restartLaunchFailures;
            this.runtimesBeforeCrashMillis = runtimesBeforeCrashMillis;
            for (int i = 0; i <= runtimesBeforeCrashMillis.length; i++) {
                processes.add(new ControlledProcess(true, true));
            }
        }

        @Override
        Process startPluginCore(File pluginCoreFile, String dbProperties, String pluginJvmArgs, String runtimePath,
                                String runTimeVersion, String token, int randomServerPort,
                                int pluginMasterPort, int randomWatcherListenPort) throws IOException {
            int currentStart = startCount.getAndIncrement();
            masterPorts.add(pluginMasterPort);
            if (currentStart > 0 && currentStart <= restartLaunchFailures) {
                throw new IOException("simulated plugin-core restart failure");
            }
            ControlledProcess process = processes.get(successfulStartCount.getAndIncrement());
            currentProcess.set(process);
            return process;
        }

        @Override
        PluginGhostWatcher newPluginGhostWatcher(String host, int port) {
            return new PluginGhostWatcher(host, port, 0L) {
                @Override
                public boolean doWatch() {
                    int currentWatcher = watcherCount.getAndIncrement();
                    if (currentWatcher < runtimesBeforeCrashMillis.length) {
                        clockNanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(
                                runtimesBeforeCrashMillis[currentWatcher]));
                        currentProcess.get().markExited();
                        return true;
                    }
                    CrashLoopPluginCoreProcessImpl.this.stopPluginCore();
                    return true;
                }
            };
        }

        @Override
        void pauseAfterPluginGhostWatcher() {
        }

        @Override
        long monotonicTimeNanos() {
            return clockNanos.get();
        }

        @Override
        void pauseBeforePluginCoreRestart(long millis) throws InterruptedException {
            restartDelays.add(millis);
            if (runAsync) {
                backoffEntered.countDown();
                Thread.sleep(millis);
            } else {
                clockNanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(millis));
            }
        }

        @Override
        Thread startPluginCoreHandle(AbstractPluginCoreProcessHandle handle) {
            this.handle.set(handle);
            Thread thread = new Thread(handle, "captured-plugin-core-crash-loop-thread");
            runnerThread.set(thread);
            if (runAsync) {
                thread.start();
            }
            return thread;
        }
    }

    private static class TestablePluginCoreProcessImpl extends PluginCoreProcessImpl {

        private final AtomicReference<AbstractPluginCoreProcessHandle> handle = new AtomicReference<>();
        private final AtomicInteger handleStartCount = new AtomicInteger();
        private final AtomicInteger watcherCount = new AtomicInteger();

        TestablePluginCoreProcessImpl(Runnable onStopRunnable, String contextPath) {
            super(onStopRunnable, contextPath);
        }

        @Override
        PluginGhostWatcher newPluginGhostWatcher(String host, int port) {
            return new PluginGhostWatcher(host, port, 0) {
                @Override
                public boolean doWatch() {
                    watcherCount.incrementAndGet();
                    TestablePluginCoreProcessImpl.this.stopPluginCore();
                    return true;
                }
            };
        }

        @Override
        void pauseAfterPluginGhostWatcher() {
        }

        @Override
        Thread startPluginCoreHandle(AbstractPluginCoreProcessHandle handle) {
            this.handle.set(handle);
            handleStartCount.incrementAndGet();
            return new Thread(handle, "captured-plugin-core-thread");
        }
    }

    private static class TestZrLogConfig extends ZrLogConfig {

        TestZrLogConfig() {
            super(19084, null, "/blog");
        }

        @Override
        public boolean isInstalled() {
            return false;
        }

        @Override
        public DataSourceWrapper configDatabase() {
            return null;
        }

        @Override
        protected TokenService initTokenService() {
            return null;
        }

        @Override
        public CacheService getCacheService() {
            return null;
        }

        @Override
        public List<IPlugin> getBasePluginList() {
            return new Plugins();
        }
    }
}

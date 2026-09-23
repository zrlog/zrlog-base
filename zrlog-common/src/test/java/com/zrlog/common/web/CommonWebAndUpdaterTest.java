package com.zrlog.common.web;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.hibegin.http.HttpMethod;
import com.hibegin.http.server.api.HttpRequest;
import com.hibegin.http.server.api.HttpResponse;
import com.zrlog.common.Updater;
import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.updater.JarPackageUtil;
import com.zrlog.common.updater.NativeImageUpdater;
import com.zrlog.common.updater.WarUpdater;
import com.zrlog.common.updater.ZipUpdater;
import com.zrlog.common.vo.Version;
import com.zrlog.plugin.IPlugin;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.web.WebSetup;
import com.zrlog.web.WebSetupContext;
import com.zrlog.web.WebSetupProvider;
import org.junit.Test;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class CommonWebAndUpdaterTest {

    @Test
    public void shouldSerializeLocalDateTimeWithAdapterAndConverter() throws Exception {
        LocalDateTimeAdapter adapter = new LocalDateTimeAdapter();
        StringWriter writer = new StringWriter();
        adapter.write(new JsonWriter(writer), LocalDateTime.of(2026, 6, 29, 11, 20, 30));

        assertEquals("\"2026-06-29T11:20:30\"", writer.toString());
        assertEquals(LocalDateTime.of(2026, 6, 29, 11, 20, 30),
                adapter.read(new JsonReader(new StringReader("\"2026-06-29T11:20:30\""))));

        ZrLogHttpJsonMessageConverter converter = new ZrLogHttpJsonMessageConverter();
        assertTrue(converter.toJson(new TimeHolder(LocalDateTime.of(2026, 6, 29, 11, 20, 30)))
                .contains("2026-06-29T11:20:30"));
        assertNotNull(converter.fromJson("{\"time\":\"2026-06-29T11:20:30\"}"));
    }

    @Test
    public void shouldAccumulateRequestHandledTime() {
        ZrLogHttpRequestListener listener = new ZrLogHttpRequestListener();

        listener.onHandled(request(System.currentTimeMillis() - 5), response());

        assertTrue(listener.getTotalHandleTime() >= 0);
    }

    @Test
    public void shouldHandleSlowAndDebugRequestLoggingBranches() {
        String previousRunMode = System.getProperty("sws.run.mode");
        try {
            ZrLogHttpRequestListener listener = new ZrLogHttpRequestListener();

            listener.onHandled(request(System.currentTimeMillis() - 6_000), response());
            System.setProperty("sws.run.mode", "debug");
            listener.onHandled(request(System.currentTimeMillis()), response());

            assertTrue(listener.getTotalHandleTime() >= 6_000);
        } finally {
            restoreProperty("sws.run.mode", previousRunMode);
        }
    }

    @Test
    public void shouldExposeBuildInfoDefaultsAndProperties() {
        assertNotNull(BlogBuildInfoUtil.getBuildId());
        assertNotNull(BlogBuildInfoUtil.getVersion());
        assertNotNull(BlogBuildInfoUtil.getTime());
        assertNotNull(BlogBuildInfoUtil.getRunMode());
        assertNotNull(BlogBuildInfoUtil.getFileArch());
        assertNotNull(BlogBuildInfoUtil.getRuntimeType());
        assertNotNull(BlogBuildInfoUtil.getPackageType());
        assertNotNull(BlogBuildInfoUtil.getUpdateVersionJsonFilename());
        assertFalse(BlogBuildInfoUtil.getResourceDownloadUrl().endsWith("/"));
        assertTrue(BlogBuildInfoUtil.isRelease() || BlogBuildInfoUtil.isPreview() || BlogBuildInfoUtil.isDev()
                || BlogBuildInfoUtil.getRunMode().length() > 0);
        assertTrue(BlogBuildInfoUtil.getBlogProp().containsKey("version"));
        assertTrue(BlogBuildInfoUtil.getVersionShortInfo().contains(" - "));
        assertTrue(BlogBuildInfoUtil.getVersionInfo().contains("("));
        assertTrue(BlogBuildInfoUtil.getVersionInfoFull().contains(":"));
    }

    @Test
    public void shouldPackageJarWithManifestAndEntries() throws Exception {
        File root = Files.createTempDirectory("zrlog-jar").toFile();
        File metaInf = new File(root, "META-INF");
        assertTrue(metaInf.mkdirs());
        File manifest = new File(metaInf, "MANIFEST.MF");
        Files.writeString(manifest.toPath(), "Manifest-Version: 1.0\nMain-Class: demo.Main\n\n");
        File file = new File(root, "demo.txt");
        Files.writeString(file.toPath(), "demo", StandardCharsets.UTF_8);
        File target = new File(root.getParentFile(), "demo.jar");
        List<File> files = new ArrayList<>(List.of(root));

        JarPackageUtil.inJar(files, root.getAbsolutePath() + File.separator, target.getAbsolutePath());

        try (JarFile jarFile = new JarFile(target)) {
            Manifest jarManifest = jarFile.getManifest();
            assertEquals("1.0", jarManifest.getMainAttributes().getValue("Manifest-Version"));
            assertNotNull(jarFile.getEntry("demo.txt"));
        }
    }

    @Test
    public void shouldExposeUpdaterMetadataWithoutRestarting() throws Exception {
        File exec = Files.createTempFile("zrlog", ".jar").toFile();
        ZipUpdater zipUpdater = new ZipUpdater(new String[]{"--data=/tmp"}, exec);
        WarUpdater warUpdater = new WarUpdater(exec);
        NativeImageUpdater nativeImageUpdater = new NativeImageUpdater(new String[]{"--port=8080", "--data=/tmp"}, exec);

        assertEquals(exec, zipUpdater.execFile());
        assertEquals(UpdaterTypeEnum.ZIP, zipUpdater.getType());
        assertNotNull(zipUpdater.getUnzipPath());
        assertEquals(exec, warUpdater.execFile());
        assertEquals(UpdaterTypeEnum.WAR, warUpdater.getType());
        assertEquals("", warUpdater.getUnzipPath());
        assertEquals(exec, nativeImageUpdater.execFile());
        assertEquals(UpdaterTypeEnum.NATIVE_IMAGE, nativeImageUpdater.getType());
        assertNotNull(nativeImageUpdater.getUnzipPath());

        Method buildStartExec = NativeImageUpdater.class.getDeclaredMethod("buildStartExec", boolean.class);
        buildStartExec.setAccessible(true);
        String cmd = buildStartExec.invoke(nativeImageUpdater, false).toString();
        assertTrue(cmd.contains(exec.toString()));
        assertTrue(cmd.contains("--data=/tmp"));
        assertTrue(cmd.contains("--port=8080"));

        Method isClassPathTemplate = WarUpdater.class.getDeclaredMethod("isClassPathTemplate", String.class);
        isClassPathTemplate.setAccessible(true);
        assertEquals(true, isClassPathTemplate.invoke(null, "/include/templates/default"));
        assertEquals(false, isClassPathTemplate.invoke(null, "/include/templates/custom"));
    }

    @Test
    public void shouldExposeDefaultExtensionContracts() throws Exception {
        Updater updater = new Updater() {
            @Override
            public void restartProcessAsync(Version upgradeVersion) {
            }

            @Override
            public String getUnzipPath() {
                return "";
            }

            @Override
            public File execFile() {
                return new File("zrlog");
            }

            @Override
            public UpdaterTypeEnum getType() {
                return UpdaterTypeEnum.ZIP;
            }
        };
        IPlugin plugin = new IPlugin() {
            @Override
            public boolean start() {
                return true;
            }

            @Override
            public boolean isStarted() {
                return false;
            }

            @Override
            public boolean stop() {
                return true;
            }
        };
        WebSetupProvider setupProvider = new WebSetupProvider() {
            @Override
            public String name() {
                return "test";
            }

            @Override
            public WebSetup create(WebSetupContext context) {
                return null;
            }
        };

        assertEquals(null, updater.buildUpgradeFile("upgrade.zip", "key"));
        assertEquals("", updater.backup());
        assertTrue(plugin.autoStart());
        assertEquals(100, setupProvider.order());
    }

    private static HttpRequest request(long createTime) {
        return (HttpRequest) Proxy.newProxyInstance(
                CommonWebAndUpdaterTest.class.getClassLoader(),
                new Class[]{HttpRequest.class},
                (proxy, method, args) -> {
                    if ("getCreateTime".equals(method.getName())) {
                        return createTime;
                    }
                    if ("getMethod".equals(method.getName())) {
                        return HttpMethod.GET;
                    }
                    if ("getUri".equals(method.getName())) {
                        return "/admin";
                    }
                    if ("toString".equals(method.getName())) {
                        return "HttpRequestProxy";
                    }
                    return null;
                });
    }

    private static HttpResponse response() {
        return (HttpResponse) Proxy.newProxyInstance(
                CommonWebAndUpdaterTest.class.getClassLoader(),
                new Class[]{HttpResponse.class},
                (proxy, method, args) -> null);
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }

    private static class TimeHolder {
        private final LocalDateTime time;

        private TimeHolder(LocalDateTime time) {
            this.time = time;
        }
    }
}

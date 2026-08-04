package com.zrlog.business.service;

import com.sun.net.httpserver.HttpServer;
import com.zrlog.business.exception.DownloadUpgradeFileException;
import com.zrlog.business.rest.response.CheckVersionResponse;
import com.zrlog.business.rest.response.BackupProtectionStatus;
import com.zrlog.business.rest.response.PreCheckVersionResponse;
import com.zrlog.business.rest.response.UpgradeProcessResponse;
import com.zrlog.common.updater.UpgradeProgressEvent;
import com.zrlog.common.updater.UpdateVersionHandler;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.updater.UpgradeProgressListener;
import com.zrlog.common.Updater;
import com.zrlog.common.updater.handle.FaasUpdateVersionHandler;
import com.zrlog.common.updater.handle.WarUpdateVersionHandle;
import com.zrlog.common.updater.handle.ZipUpdateVersionHandle;
import com.zrlog.common.vo.Version;
import com.zrlog.util.BlogBuildInfoUtil;
import org.junit.Test;

import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class UpgradeServicePublicFlowTest {

    @Test
    public void shouldReturnNoUpgradeWhenPluginIsMissing() {
        CheckVersionResponse response = new UpgradeService().getCheckVersionResponse(false, null);

        assertFalse(response.getUpgrade());
        assertNull(response.getVersion());
    }

    @Test
    public void shouldBuildPreCheckResponseWhenPluginHasNoVersion() {
        FakeUpdateVersionInfoPlugin plugin = new FakeUpdateVersionInfoPlugin(null);

        PreCheckVersionResponse response = new UpgradeService().getPreCheckVersionResponse(false, plugin);

        assertFalse(response.getUpgrade());
        assertNull(response.getVersion());
        assertEquals(1, plugin.fetchFalseCount);
        assertEquals(0, plugin.fetchTrueCount);
    }

    @Test
    public void shouldBuildPreUpgradeResponseWhenOnlineUpgradeIsAllowed() {
        FakeUpdateVersionInfoPlugin plugin = new FakeUpdateVersionInfoPlugin(null);

        PreCheckVersionResponse response = new TestableUpgradeService(false, false, false, true)
                .preUpgradeVersion(false, plugin);

        assertFalse(response.getUpgrade());
        assertTrue(response.getOnlineUpgradable());
        assertFalse(response.getDockerMode());
        assertFalse(response.getSystemServiceMode());
        assertNull(response.getDisableUpgradeReason());
    }

    @Test
    public void shouldExposeRuntimeModeHelpers() {
        UpgradeService service = new UpgradeService();

        assertNotNull(Boolean.valueOf(service.isDockerMode()));
        assertNotNull(Boolean.valueOf(service.isSystemServiceMode()));
        assertNotNull(Boolean.valueOf(service.isFaaSMode()));
        assertNotNull(Boolean.valueOf(service.isFaaSOnlineUpgradeSupported()));
    }

    @Test
    public void shouldReturnNoChangeWithoutDownloadingWhenNoUpgradeVersionExists() {
        FakeUpdateVersionInfoPlugin plugin = new FakeUpdateVersionInfoPlugin(null);
        Map<String, Object> backend = new HashMap<>();
        backend.put("upgrade.result.noChange", "No change");

        UpgradeProcessResponse response = new UpgradeService().doUpgrade(plugin, UpgradeProgressListener.NONE,
                backend);

        assertTrue(response.getFinish());
        assertEquals("No change", response.getMessage());
        assertEquals(1, plugin.fetchTrueCount);
        assertEquals(1, plugin.fetchFalseCount);
    }

    @Test
    public void shouldRejectExplicitUpgradeWithoutUpdater() {
        UpgradeProcessResponse response = new TestableUpgradeService(false, false, false, true)
                .doUpgrade(futureVersion("99.0.0"), null, UpgradeProgressListener.NONE, backend());

        assertFalse(response.getFinish());
    }

    @Test
    public void shouldReturnDockerManualUpgradeResponseWithoutDownloading() {
        FakeUpdateVersionInfoPlugin preCheckPlugin = new FakeUpdateVersionInfoPlugin(futureVersion("99.0.0"));
        TestableUpgradeService service = new TestableUpgradeService(true, false, false, true);

        PreCheckVersionResponse preCheckResponse = service.preUpgradeVersion(false, preCheckPlugin);

        assertTrue(preCheckResponse.getUpgrade());
        assertTrue(preCheckResponse.getDockerMode());
        assertFalse(preCheckResponse.getSystemServiceMode());
        assertFalse(preCheckResponse.getOnlineUpgradable());
        assertNotNull(preCheckResponse.getDisableUpgradeReason());

        FakeUpdateVersionInfoPlugin upgradePlugin = new FakeUpdateVersionInfoPlugin(futureVersion("99.0.0"));
        UpgradeProcessResponse response = service.doUpgrade(upgradePlugin, UpgradeProgressListener.NONE, backend());

        assertTrue(response.getFinish());
        assertEquals("Use docker image", response.getMessage());
        assertEquals(1, upgradePlugin.fetchTrueCount);
        assertEquals(1, upgradePlugin.fetchFalseCount);
    }

    @Test
    public void shouldReturnSystemServiceManualUpgradeResponseWithoutDownloading() {
        FakeUpdateVersionInfoPlugin plugin = new FakeUpdateVersionInfoPlugin(futureVersion("99.0.0-SNAPSHOT"));
        UpgradeProcessResponse response = new TestableUpgradeService(false, true, false, true)
                .doUpgrade(plugin, UpgradeProgressListener.NONE, backend());

        assertTrue(response.getFinish());
        assertEquals("/etc/init.d/zrlog upgrade-preview", response.getMessage());
        assertEquals(1, plugin.fetchTrueCount);
        assertEquals(1, plugin.fetchFalseCount);
    }

    @Test
    public void shouldReturnFaasManualUpgradeResponseWithoutDownloadingWhenOnlineUpgradeUnsupported() {
        FakeUpdateVersionInfoPlugin plugin = new FakeUpdateVersionInfoPlugin(futureVersion("99.0.0"));

        UpgradeProcessResponse response = new TestableUpgradeService(false, false, true, false)
                .doUpgrade(plugin, UpgradeProgressListener.NONE, backend());

        assertTrue(response.getFinish());
        assertEquals("[download upgrade file](https://example.invalid/zrlog.zip)", response.getMessage());
        assertEquals(1, plugin.fetchTrueCount);
        assertEquals(1, plugin.fetchFalseCount);
    }

    @Test
    public void shouldFailUpgradeWhenDownloadedPackageChecksumMismatches() throws Exception {
        byte[] packageBytes = "zip-bytes".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/zrlog.zip", exchange -> {
            exchange.sendResponseHeaders(200, packageBytes.length);
            exchange.getResponseBody().write(packageBytes);
            exchange.close();
        });
        server.start();
        try {
            Version version = futureVersion("99.0.1");
            version.setZipDownloadUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/zrlog.zip");
            version.setZipFileSize(packageBytes.length);
            version.setZipMd5sum("bad-md5");
            FakeUpdateVersionInfoPlugin plugin = new FakeUpdateVersionInfoPlugin(version);
            List<UpgradeProgressEvent.Data> progressEvents = new ArrayList<>();

            assertThrows(DownloadUpgradeFileException.class, () ->
                    new TestableUpgradeService(false, false, false, true)
                            .doUpgrade(plugin, (event, data) -> progressEvents.add(data), backend()));

            assertFalse(progressEvents.isEmpty());
            assertEquals(UpgradeProgressEvent.STAGE_DOWNLOAD, progressEvents.get(0).getStage());
            assertEquals(UpgradeProgressEvent.STATUS_RUNNING, progressEvents.get(0).getStatus());
            assertEquals(1, plugin.fetchTrueCount);
            assertEquals(1, plugin.fetchFalseCount);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void shouldUseWarPackageMetadataWhenRunningInWarMode() throws Exception {
        byte[] packageBytes = "war-bytes".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/zrlog.war", exchange -> {
            exchange.sendResponseHeaders(200, packageBytes.length);
            exchange.getResponseBody().write(packageBytes);
            exchange.close();
        });
        server.start();
        try {
            Version version = futureVersion("99.0.2");
            version.setZipDownloadUrl("https://example.invalid/unused.zip");
            version.setWarDownloadUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/zrlog.war");
            version.setWarFileSize(packageBytes.length);
            version.setWarMd5sum("bad-md5");
            FakeUpdateVersionInfoPlugin plugin = new FakeUpdateVersionInfoPlugin(version);

            assertThrows(DownloadUpgradeFileException.class, () ->
                    new TestableUpgradeService(false, false, false, true, true)
                            .doUpgrade(plugin, UpgradeProgressListener.NONE, backend()));

            assertEquals(1, plugin.fetchTrueCount);
            assertEquals(1, plugin.fetchFalseCount);
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void shouldCreateOnlineUpdateHandlerForCurrentRuntimeMode() throws Exception {
        File packageFile = File.createTempFile("zrlog-upgrade-handler", ".zip");
        Version version = futureVersion("99.0.3");

        assertTrue(new TestableUpgradeService(false, false, false, true)
                .newOnlineUpdateVersionHandler(version, packageFile, UpgradeProgressListener.NONE, backend())
                instanceof ZipUpdateVersionHandle);
        assertTrue(new TestableUpgradeService(false, false, false, true, true)
                .newOnlineUpdateVersionHandler(version, packageFile, UpgradeProgressListener.NONE, backend())
                instanceof WarUpdateVersionHandle);
        assertTrue(new TestableUpgradeService(false, false, true, true)
                .newOnlineUpdateVersionHandler(version, packageFile, UpgradeProgressListener.NONE, backend())
                instanceof FaasUpdateVersionHandler);
    }

    @Test
    public void shouldDownloadPackageWhenBackupEvidenceIsMissing() throws Exception {
        byte[] packageBytes = "zip-bytes".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/zrlog.zip", exchange -> {
            exchange.sendResponseHeaders(200, packageBytes.length);
            exchange.getResponseBody().write(packageBytes);
            exchange.close();
        });
        server.start();
        try {
            Version version = futureVersion("99.0.4");
            version.setZipDownloadUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/zrlog.zip");
            FakeUpdateVersionInfoPlugin plugin = new FakeUpdateVersionInfoPlugin(version);
            FakeUpdateVersionHandler handler = new FakeUpdateVersionHandler(true, "Updated");
            TestableUpgradeService service = new TestableUpgradeService(false, false, false, true,
                    false, false);
            service.onlineHandler = handler;
            List<UpgradeProgressEvent.Data> progressEvents = new ArrayList<>();

            UpgradeProcessResponse response = service.doUpgrade(plugin,
                    (event, data) -> progressEvents.add(data), backend(), false);

            assertTrue(response.getFinish());
            assertEquals("Updated", response.getMessage());
            assertTrue(handler.handled);
            assertNotNull(service.onlinePackage);
            assertTrue(service.onlinePackage.exists());
            assertEquals(packageBytes.length, service.onlinePackage.length());
            assertEquals(1, plugin.fetchTrueCount);
            assertEquals(1, plugin.fetchFalseCount);
            assertTrue(progressEvents.stream().anyMatch(data ->
                    UpgradeProgressEvent.STATUS_COMPLETE.equals(data.getStatus())));
        } finally {
            server.stop(0);
        }
    }

    private static Version futureVersion(String versionText) {
        Version version = new Version();
        version.setBuildId("test-" + versionText);
        version.setVersion(versionText);
        version.setBuildDate(new Date(BlogBuildInfoUtil.getTime().getTime() + 86_400_000L));
        version.setZipDownloadUrl("https://example.invalid/zrlog.zip");
        version.setWarDownloadUrl("https://example.invalid/zrlog.war");
        return version;
    }

    private static Map<String, Object> backend() {
        Map<String, Object> backend = new HashMap<>();
        backend.put("upgrade.result.noChange", "No change");
        backend.put("upgrade.tips.docker", "Use docker image");
        backend.put("upgrade.tips.systemService", "/etc/init.d/zrlog upgrade");
        backend.put("upgrade.status.downloading", "Downloading");
        backend.put("upgrade.status.downloaded", "Downloaded");
        backend.put("upgrade.backupProtection.riskAcceptanceRequired",
                "Verify a recent backup or accept the backup risk");
        return backend;
    }

    private static class FakeUpdateVersionInfoPlugin extends UpdateVersionInfoPlugin {

        private final Version version;
        private int fetchTrueCount;
        private int fetchFalseCount;

        FakeUpdateVersionInfoPlugin(Version version) {
            this.version = version;
        }

        @Override
        public Version getLastVersion(boolean fetch) {
            if (fetch) {
                fetchTrueCount++;
            } else {
                fetchFalseCount++;
            }
            return version;
        }
    }

    private static class TestableUpgradeService extends UpgradeService {

        private final boolean dockerMode;
        private final boolean systemServiceMode;
        private final boolean faasMode;
        private final boolean faasOnlineUpgradeSupported;
        private final boolean warMode;
        private UpdateVersionHandler onlineHandler;
        private File onlinePackage;

        TestableUpgradeService(boolean dockerMode, boolean systemServiceMode, boolean faasMode,
                               boolean faasOnlineUpgradeSupported) {
            this(dockerMode, systemServiceMode, faasMode, faasOnlineUpgradeSupported, false);
        }

        TestableUpgradeService(boolean dockerMode, boolean systemServiceMode, boolean faasMode,
                               boolean faasOnlineUpgradeSupported, boolean warMode) {
            this(dockerMode, systemServiceMode, faasMode, faasOnlineUpgradeSupported, warMode, true);
        }

        TestableUpgradeService(boolean dockerMode, boolean systemServiceMode, boolean faasMode,
                               boolean faasOnlineUpgradeSupported, boolean warMode, boolean backupReady) {
            super(backupProtection(backupReady));
            this.dockerMode = dockerMode;
            this.systemServiceMode = systemServiceMode;
            this.faasMode = faasMode;
            this.faasOnlineUpgradeSupported = faasOnlineUpgradeSupported;
            this.warMode = warMode;
        }

        private static BackupProtectionService backupProtection(boolean ready) {
            return new BackupProtectionService() {
                @Override
                public BackupProtectionStatus getStatus() {
                    BackupProtectionStatus status = new BackupProtectionStatus();
                    status.setReady(ready);
                    status.setRequiresRiskAcceptance(!ready);
                    status.setStatus(ready ? BackupProtectionStatus.READY
                            : BackupProtectionStatus.MISSING_VERIFICATION);
                    return status;
                }
            };
        }

        @Override
        boolean isDockerMode() {
            return dockerMode;
        }

        @Override
        boolean isSystemServiceMode() {
            return systemServiceMode;
        }

        @Override
        boolean isFaaSMode() {
            return faasMode;
        }

        @Override
        boolean isWarMode() {
            return warMode;
        }

        @Override
        boolean isFaaSOnlineUpgradeSupported() {
            return faasOnlineUpgradeSupported;
        }

        @Override
        UpdateVersionHandler newOnlineUpdateVersionHandler(Version version, File upgradePackage,
                                                           UpgradeProgressListener progressListener,
                                                           Map<String, Object> backend, Updater updater) {
            onlinePackage = upgradePackage;
            if (onlineHandler != null) {
                return onlineHandler;
            }
            return super.newOnlineUpdateVersionHandler(version, upgradePackage, progressListener, backend, updater);
        }
    }

    private static class FakeUpdateVersionHandler implements UpdateVersionHandler {

        private final boolean finish;
        private final String message;
        private boolean handled;

        FakeUpdateVersionHandler(boolean finish, String message) {
            this.finish = finish;
            this.message = message;
        }

        @Override
        public String getMessage() {
            return message;
        }

        @Override
        public boolean isFinish() {
            return finish;
        }

        @Override
        public void doHandle() {
            handled = true;
        }
    }
}

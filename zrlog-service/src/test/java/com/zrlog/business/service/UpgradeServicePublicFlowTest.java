package com.zrlog.business.service;

import static org.junit.Assert.*;

import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.business.rest.response.BackupProtectionStatus;
import com.zrlog.business.rest.response.CheckVersionResponse;
import com.zrlog.business.rest.response.PreCheckVersionResponse;
import com.zrlog.business.rest.response.UpgradeProcessResponse;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.updater.UpgradeProgressEvent;
import com.zrlog.common.updater.UpgradeProgressListener;
import com.zrlog.common.vo.Version;
import com.zrlog.data.repository.WebSiteRepository;
import com.zrlog.util.BlogBuildInfoUtil;
import java.util.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class UpgradeServicePublicFlowTest {

    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

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
        backend.put("upgrade.lambda.download", "download upgrade file");
        backend.put("upgrade.result.noChange", "No change");
        backend.put("upgrade.tips.docker", "Use docker image");
        backend.put("upgrade.tips.systemService", "/etc/init.d/zrlog upgrade");
        backend.put("upgrade.status.downloading", "Downloading");
        backend.put("upgrade.status.downloaded", "Downloaded");
        backend.put(
                "upgrade.backupProtection.riskAcceptanceRequired",
                "Verify a recent backup or accept the backup risk");
        return backend;
    }

    @Test
    public void shouldReturnNoUpgradeWhenPluginIsMissing() {
        CheckVersionResponse response =
                com.zrlog.business.di.ServiceComponent.forUninstalledApplication(null)
                        .upgrades()
                        .getCheckVersionResponse(false, null);

        assertFalse(response.getUpgrade());
        assertNull(response.getVersion());
    }

    @Test
    public void shouldBuildPreCheckResponseWhenPluginHasNoVersion() {
        FakeUpdateVersionInfoPlugin plugin = new FakeUpdateVersionInfoPlugin(null, null);

        PreCheckVersionResponse response =
                com.zrlog.business.di.ServiceComponent.forUninstalledApplication(null)
                        .upgrades()
                        .getPreCheckVersionResponse(false, plugin);

        assertFalse(response.getUpgrade());
        assertNull(response.getVersion());
        assertEquals(1, plugin.fetchFalseCount);
        assertEquals(0, plugin.fetchTrueCount);
    }

    @Test
    public void shouldReportUnavailableWhenNoUpdaterWasAssembled() {
        FakeUpdateVersionInfoPlugin plugin = new FakeUpdateVersionInfoPlugin(null, null);

        PreCheckVersionResponse response =
                new TestableUpgradeService(false, false, false, true)
                        .preUpgradeVersion(false, plugin);

        assertFalse(response.getUpgrade());
        assertFalse(response.getOnlineUpgradable());
        assertFalse(response.getDockerMode());
        assertFalse(response.getSystemServiceMode());
        assertFalse(response.getFaasMode());
        assertFalse(response.getWarMode());
        assertNotNull(response.getNativeImageMode());
        assertNotNull(response.getBackupProtection());
        assertNotNull(response.getDisableUpgradeReason());
    }

    @Test
    public void shouldExposeRuntimeModeHelpers() {
        UpgradeService service =
                com.zrlog.business.di.ServiceComponent.forUninstalledApplication(null).upgrades();

        assertNotNull(Boolean.valueOf(service.isDockerMode()));
        assertNotNull(Boolean.valueOf(service.isSystemServiceMode()));
        assertNotNull(Boolean.valueOf(service.isFaaSMode()));
        assertNotNull(Boolean.valueOf(service.isFaaSOnlineUpgradeSupported()));
    }

    @Test
    public void shouldReturnNoChangeWithoutDownloadingWhenNoUpgradeVersionExists() {
        FakeUpdateVersionInfoPlugin plugin = new FakeUpdateVersionInfoPlugin(null, null);
        Map<String, Object> backend = new HashMap<>();
        backend.put("upgrade.result.noChange", "No change");
        backend.put("upgrade.lambda.download", "download upgrade file");

        UpgradeProcessResponse response =
                com.zrlog.business.di.ServiceComponent.forUninstalledApplication(null)
                        .upgrades()
                        .doUpgrade(plugin, UpgradeProgressListener.NONE, backend);

        assertTrue(response.getFinish());
        assertEquals("No change", response.getMessage());
        assertEquals(1, plugin.fetchTrueCount);
        assertEquals(1, plugin.fetchFalseCount);
    }

    @Test
    public void shouldRejectExplicitUpgradeWithoutUpdater() {
        UpgradeProcessResponse response =
                new TestableUpgradeService(false, false, false, true)
                        .doUpgrade(
                                futureVersion("99.0.0"),
                                null,
                                UpgradeProgressListener.NONE,
                                backend());

        assertFalse(response.getFinish());
    }

    @Test
    public void shouldUseTheClaimedVersionForManualInstructionsWithoutFetchingAgain() {
        Version selected = futureVersion("99.1.0");
        selected.setZipDownloadUrl("https://example.invalid/reviewed-version.zip");
        UpgradeProcessResponse response =
                new TestableUpgradeService(false, false, true, false)
                        .doUpgradeVersion(selected, UpgradeProgressListener.NONE, backend());
        assertTrue(response.getFinish());
        assertEquals(
                "[download upgrade file](https://example.invalid/reviewed-version.zip)",
                response.getMessage());
    }

    @Test
    public void shouldReturnDockerManualUpgradeResponseWithoutDownloading() {
        FakeUpdateVersionInfoPlugin preCheckPlugin =
                new FakeUpdateVersionInfoPlugin(futureVersion("99.0.0"), null);
        TestableUpgradeService service = new TestableUpgradeService(true, false, false, true);

        PreCheckVersionResponse preCheckResponse = service.preUpgradeVersion(false, preCheckPlugin);

        assertTrue(preCheckResponse.getUpgrade());
        assertTrue(preCheckResponse.getDockerMode());
        assertFalse(preCheckResponse.getSystemServiceMode());
        assertFalse(preCheckResponse.getFaasMode());
        assertFalse(preCheckResponse.getWarMode());
        assertFalse(preCheckResponse.getOnlineUpgradable());
        assertNotNull(preCheckResponse.getBackupProtection());
        assertNotNull(preCheckResponse.getDisableUpgradeReason());

        FakeUpdateVersionInfoPlugin upgradePlugin =
                new FakeUpdateVersionInfoPlugin(futureVersion("99.0.0"), null);
        UpgradeProcessResponse response =
                service.doUpgrade(upgradePlugin, UpgradeProgressListener.NONE, backend());

        assertTrue(response.getFinish());
        assertEquals("Use docker image", response.getMessage());
        assertEquals(1, upgradePlugin.fetchTrueCount);
        assertEquals(1, upgradePlugin.fetchFalseCount);
    }

    @Test
    public void shouldReturnSystemServiceManualUpgradeResponseWithoutDownloading() {
        FakeUpdateVersionInfoPlugin plugin =
                new FakeUpdateVersionInfoPlugin(futureVersion("99.0.0-SNAPSHOT"), null);
        UpgradeProcessResponse response =
                new TestableUpgradeService(false, true, false, true)
                        .doUpgrade(plugin, UpgradeProgressListener.NONE, backend());

        assertTrue(response.getFinish());
        assertEquals("/etc/init.d/zrlog upgrade-preview", response.getMessage());
        assertEquals(1, plugin.fetchTrueCount);
        assertEquals(1, plugin.fetchFalseCount);
    }

    @Test
    public void
            shouldReturnFaasManualUpgradeResponseWithoutDownloadingWhenOnlineUpgradeUnsupported() {
        FakeUpdateVersionInfoPlugin plugin =
                new FakeUpdateVersionInfoPlugin(futureVersion("99.0.0"), null);

        UpgradeProcessResponse response =
                new TestableUpgradeService(false, false, true, false)
                        .doUpgrade(plugin, UpgradeProgressListener.NONE, backend());

        assertTrue(response.getFinish());
        assertEquals(
                "[download upgrade file](https://example.invalid/zrlog.zip)",
                response.getMessage());
        assertEquals(1, plugin.fetchTrueCount);
        assertEquals(1, plugin.fetchFalseCount);
    }

    @Test
    public void submitsPinnedTasksThroughTheSameProviderDespiteMissingBackupEvidence()
            throws Exception {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var command = new com.zrlog.updater.spi.UpdateCommand();
        command.requestId = "manual-request-123456";
        command.version = "99.0.4";
        command.buildId = "pinned-build";
        command.channel = "release";
        var service =
                new TestableUpgradeService(false, false, false, true, false, false, null) {
                    public com.zrlog.updater.spi.UpdateOperations operations() {
                        return new com.zrlog.updater.common.AbstractUpdateOperations() {
                            public com.zrlog.updater.spi.UpdateAvailability availability(
                                    java.util.function.Function<String, String> text) {
                                return new com.zrlog.updater.spi.UpdateAvailability(true, null);
                            }

                            public com.zrlog.updater.spi.UpdateTask submit(
                                    com.zrlog.updater.spi.UpdateCommand received,
                                    UpgradeProgressListener listener,
                                    java.util.function.Function<String, String> text)
                                    throws Exception {
                                calls.incrementAndGet();
                                assertSame(command, received);
                                listener.onProgress(
                                        UpgradeProgressEvent.EVENT,
                                        UpgradeProgressEvent.data("download", "Downloading"));
                                var task = new com.zrlog.updater.spi.UpdateTask();
                                task.id = received.requestId;
                                task.buildId = received.buildId;
                                task.state = "queued";
                                return task;
                            }

                            public com.zrlog.updater.spi.UpdateTask status(
                                    String id, java.util.function.Function<String, String> text) {
                                return null;
                            }

                            public com.zrlog.updater.spi.UpdateSourceSettings settings(
                                    String channel,
                                    java.util.function.Function<String, String> text) {
                                throw new UnsupportedOperationException();
                            }

                            public void saveSettings(
                                    String source,
                                    String channel,
                                    java.util.function.Function<String, String> text) {
                                throw new UnsupportedOperationException();
                            }
                        };
                    }
                };
        var check =
                service.preUpgradeVersion(
                        false, new FakeUpdateVersionInfoPlugin(futureVersion("99.0.4"), null));
        assertFalse(check.getBackupProtection().getReady());
        assertTrue(check.getOnlineUpgradable());
        List<UpgradeProgressEvent.Data> events = new ArrayList<>();
        var submitted =
                service.submitUpgrade(command, (event, data) -> events.add(data), backend());
        assertEquals(command.requestId, submitted.id);
        assertEquals(command.buildId, submitted.buildId);
        assertEquals("queued", submitted.state);
        assertEquals("download", events.get(0).getStage());
        assertEquals(1, calls.get());
    }

    private static class FakeUpdateVersionInfoPlugin extends UpdateVersionInfoPlugin {

        private final Version version;
        private int fetchTrueCount;
        private int fetchFalseCount;

        FakeUpdateVersionInfoPlugin(Version version, DataSourceWrapper testDataSource) {
            super(
                    new UpgradeNoticeService(
                            () -> false,
                            new WebsiteKvService(() -> new WebSiteRepository(testDataSource))),
                    () -> "zh_CN",
                    null,
                    new WebsiteKvService(() -> new WebSiteRepository(testDataSource)));
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

        TestableUpgradeService(
                boolean dockerMode,
                boolean systemServiceMode,
                boolean faasMode,
                boolean faasOnlineUpgradeSupported) {
            this(dockerMode, systemServiceMode, faasMode, faasOnlineUpgradeSupported, false);
        }

        TestableUpgradeService(
                boolean dockerMode,
                boolean systemServiceMode,
                boolean faasMode,
                boolean faasOnlineUpgradeSupported,
                boolean warMode) {
            this(
                    dockerMode,
                    systemServiceMode,
                    faasMode,
                    faasOnlineUpgradeSupported,
                    warMode,
                    true,
                    null);
        }

        TestableUpgradeService(
                boolean dockerMode,
                boolean systemServiceMode,
                boolean faasMode,
                boolean faasOnlineUpgradeSupported,
                boolean warMode,
                boolean backupReady,
                DataSourceWrapper testDataSource) {
            super(
                    () -> null,
                    new UpgradeNoticeService(
                            () -> false,
                            new WebsiteKvService(() -> new WebSiteRepository(testDataSource))),
                    backupProtection(backupReady, testDataSource),
                    new WebsiteKvService(() -> new WebSiteRepository(testDataSource)));
            this.dockerMode = dockerMode;
            this.systemServiceMode = systemServiceMode;
            this.faasMode = faasMode;
            this.faasOnlineUpgradeSupported = faasOnlineUpgradeSupported;
            this.warMode = warMode;
        }

        private static BackupProtectionService backupProtection(
                boolean ready, DataSourceWrapper testDataSource) {
            return new BackupProtectionService(
                    new WebsiteKvService(() -> new WebSiteRepository(testDataSource))) {
                @Override
                public BackupProtectionStatus getStatus() {
                    BackupProtectionStatus status = new BackupProtectionStatus();
                    status.setReady(ready);
                    status.setRequiresRiskAcceptance(!ready);
                    status.setStatus(
                            ready
                                    ? BackupProtectionStatus.READY
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
    }
}

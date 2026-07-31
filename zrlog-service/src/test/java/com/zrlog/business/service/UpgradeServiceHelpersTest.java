package com.zrlog.business.service;

import com.zrlog.common.updater.UpgradeProgressEvent;
import com.zrlog.common.updater.UpgradeProgressListener;
import com.zrlog.common.vo.Version;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class UpgradeServiceHelpersTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldCalculateDownloadProgress() throws Exception {
        File file = temporaryFolder.newFile("zrlog-upgrade.zip");
        Files.writeString(file.toPath(), "hello", StandardCharsets.UTF_8);

        assertEquals(0, UpgradeService.getDownloadProcess(null, 10L));
        assertEquals(0, UpgradeService.getDownloadProcess(file, 0L));
        assertEquals(0, UpgradeService.getDownloadProcess(new File(temporaryFolder.getRoot(), "missing.zip"), 10L));
        assertEquals(50, UpgradeService.getDownloadProcess(file, 10L));
        assertEquals(100, UpgradeService.getDownloadProcess(file, 2L));
    }

    @Test
    public void shouldDetectInvalidDownloadedPackage() throws Exception {
        File file = temporaryFolder.newFile("zrlog-upgrade.zip");
        Files.writeString(file.toPath(), "hello", StandardCharsets.UTF_8);
        File empty = temporaryFolder.newFile("empty.zip");

        assertTrue(UpgradeService.isErrorFile(null, 0L, ""));
        assertTrue(UpgradeService.isErrorFile(new File(temporaryFolder.getRoot(), "missing.zip"), 0L, ""));
        assertTrue(UpgradeService.isErrorFile(empty, 0L, ""));
        assertTrue(UpgradeService.isErrorFile(file, 6L, ""));
        assertTrue(UpgradeService.isErrorFile(file, 5L, "bad-md5"));
        assertFalse(UpgradeService.isErrorFile(file, 5L, "5d41402abc4b2a76b9719d911017c592"));
        assertTrue(UpgradeService.isErrorFile(file, 5L, "bad-sha256",
                "5d41402abc4b2a76b9719d911017c592"));
        assertFalse(UpgradeService.isErrorFile(file, 5L,
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                "bad-md5"));
        assertFalse(UpgradeService.isErrorFile(file, 0L, ""));
    }

    @Test
    public void shouldBuildSanitizedUpgradeKey() throws Exception {
        Version buildIdVersion = new Version();
        buildIdVersion.setBuildId("3.6.0 SNAPSHOT/abc");
        buildIdVersion.setVersion("ignored");
        Version versionFallback = new Version();
        versionFallback.setVersion("4.0.0+meta");

        String buildIdKey = UpgradeService.buildUpgradeKey(buildIdVersion);
        String versionKey = UpgradeService.buildUpgradeKey(versionFallback);
        String fallbackKey = UpgradeService.buildUpgradeKey(null);

        assertTrue(buildIdKey.startsWith("upgrade-3.6.0-SNAPSHOT-abc-"));
        assertTrue(versionKey.startsWith("upgrade-4.0.0-meta-"));
        assertTrue(fallbackKey.startsWith("upgrade-"));
        assertTrue(buildIdKey.matches("[A-Za-z0-9._-]+"));
        assertTrue(versionKey.matches("[A-Za-z0-9._-]+"));
    }

    @Test
    public void shouldBuildDownloadMessagesFromBackendMap() throws Exception {
        Map<String, Object> backend = new HashMap<>();
        backend.put("upgrade.status.downloading", "Downloading");

        assertEquals("Downloading", UpgradeService.backendString(backend, "upgrade.status.downloading"));
        assertEquals("missing.key", UpgradeService.backendString(backend, "missing.key"));
        assertEquals("Downloading", UpgradeService.getDownloadMessage(backend, null));
        assertEquals("Downloading", UpgradeService.getDownloadMessage(backend, 0));
        assertEquals("Downloading 42%", UpgradeService.getDownloadMessage(backend, 42));
    }

    @Test
    public void shouldPublishProgressAndWrapListenerFailures() throws Exception {
        AtomicReference<String> eventRef = new AtomicReference<>();
        AtomicReference<UpgradeProgressEvent.Data> dataRef = new AtomicReference<>();
        UpgradeProgressListener listener = (event, data) -> {
            eventRef.set(event);
            dataRef.set(data);
        };

        UpgradeService.publishProgress(listener, UpgradeProgressEvent.STAGE_DOWNLOAD, UpgradeProgressEvent.STATUS_RUNNING,
                "Downloading", "zrlog.zip");

        assertEquals(UpgradeProgressEvent.EVENT, eventRef.get());
        UpgradeProgressEvent.Data data = dataRef.get();
        assertNotNull(data);
        assertEquals(UpgradeProgressEvent.STAGE_DOWNLOAD, data.getStage());
        assertEquals(UpgradeProgressEvent.STATUS_RUNNING, data.getStatus());
        assertEquals("Downloading", data.getMessage());
        assertEquals("zrlog.zip", data.getDetail());

        UpgradeProgressListener failing = (event, progressData) -> {
            throw new Exception("listener failed");
        };
        assertThrows(IllegalStateException.class,
                () -> UpgradeService.publishProgress(failing, UpgradeProgressEvent.STAGE_DOWNLOAD,
                        UpgradeProgressEvent.STATUS_RUNNING, "Downloading", null));
    }
}

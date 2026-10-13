package com.zrlog.common.updater;

import com.zrlog.common.vo.Version;
import com.zrlog.util.BlogBuildInfoUtil;
import org.junit.Test;

import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class UpdateVersionTimerTaskTest {

    @Test
    public void shouldExposeSelectedChannelAndVersionToConsumers() {
        Version version = version("3.9.0-SNAPSHOT");
        UpdateVersionCheckResult result = new UpdateVersionCheckResult(version, UpdateChannel.PREVIEW, true);
        AtomicReference<Version> consumedVersion = new AtomicReference<>();
        AtomicReference<UpdateVersionCheckResult> consumedResult = new AtomicReference<>();
        UpdateVersionTimerTask task = new FakeUpdateVersionTimerTask(result, consumedVersion, consumedResult);

        task.run();

        assertSame(version, consumedVersion.get());
        assertSame(result, consumedResult.get());
        assertSame(version, task.getVersion());
        assertEquals(UpdateChannel.PREVIEW, consumedResult.get().getChannel());
    }

    @Test
    public void shouldReportFailureAndKeepPreviouslyCheckedVersion() {
        java.util.concurrent.atomic.AtomicBoolean fail = new java.util.concurrent.atomic.AtomicBoolean();
        Version version = version("4.0.0");
        UpdateVersionTimerTask task = new UpdateVersionTimerTask(false, "zh_CN") {
            @Override protected UpdateVersionCheckResult fetchLastVersion(boolean preview) throws java.io.IOException {
                if (fail.get()) { throw new java.io.IOException("metadata unavailable"); }
                return new UpdateVersionCheckResult(version, UpdateChannel.RELEASE, true);
            }
        };
        task.run();
        org.junit.Assert.assertFalse(task.isLastCheckFailed());
        fail.set(true);
        task.run();
        org.junit.Assert.assertTrue(task.isLastCheckFailed());
        assertSame(version, task.getVersion());
        fail.set(false);
        task.run();
        org.junit.Assert.assertFalse(task.isLastCheckFailed());
    }

    private static Version version(String value) {
        Version version = new Version();
        version.setVersion(value);
        version.setBuildId("build");
        version.setBuildDate(new Date(1_000));
        return version;
    }

    @Test
    public void shouldKeepCheckedPreviewWhenNoReleaseManifestHasBeenPublished() {
        Version preview = currentVersion();
        AtomicReference<UpdateVersionCheckResult> result = new AtomicReference<>();
        UpdateVersionTimerTask task = channelTask(true, preview,
                new java.io.FileNotFoundException("HTTP 404"), result);

        task.run();

        assertFalse(task.isLastCheckFailed());
        assertSame(preview, task.getVersion());
        assertEquals(UpdateChannel.PREVIEW, result.get().getChannel());
        assertFalse(result.get().isUpgradeAvailable());
    }

    @Test
    public void shouldNotHideFailuresInTheSelectedChannelOrBrokenReleaseMetadata() {
        for (boolean previewEnabled : new boolean[]{false, true}) {
            UpdateVersionTimerTask task = new UpdateVersionTimerTask(previewEnabled, "zh_CN") {
                @Override protected Version getVersion(boolean preview) throws java.io.IOException {
                    throw new java.io.FileNotFoundException("HTTP 404");
                }
            };
            task.run();
            assertTrue(task.isLastCheckFailed());
        }
        for (Exception failure : new Exception[]{new java.io.IOException("HTTP 403"),
                new java.io.IOException("HTTP 503"), new java.net.SocketTimeoutException("timeout"),
                new IllegalArgumentException("Invalid Docker update manifest")}) {
            UpdateVersionTimerTask task = channelTask(true, currentVersion(), failure, new AtomicReference<>());
            task.run();
            assertTrue(task.isLastCheckFailed());
        }
    }

    @Test
    public void shouldStillFindANewerPublishedRelease() {
        Version preview = currentVersion();
        Version release = version("99.0.0");
        release.setBuildId("new-release");
        release.setBuildDate(new Date(BlogBuildInfoUtil.getTime().getTime() + 60_000));
        AtomicReference<UpdateVersionCheckResult> result = new AtomicReference<>();
        UpdateVersionTimerTask task = new UpdateVersionTimerTask(true, "zh_CN", ignored -> {}, result::set) {
            @Override protected Version getVersion(boolean isPreview) { return isPreview ? preview : release; }
        };

        task.run();

        assertFalse(task.isLastCheckFailed());
        assertSame(release, task.getVersion());
        assertEquals(UpdateChannel.RELEASE, result.get().getChannel());
        assertTrue(result.get().isUpgradeAvailable());
    }

    private static Version currentVersion() {
        Version version = version(BlogBuildInfoUtil.getVersion());
        version.setBuildId(BlogBuildInfoUtil.getBuildId());
        version.setBuildDate(BlogBuildInfoUtil.getTime());
        return version;
    }

    private static UpdateVersionTimerTask channelTask(boolean previewEnabled, Version preview, Exception failure,
                                                     AtomicReference<UpdateVersionCheckResult> result) {
        return new UpdateVersionTimerTask(previewEnabled, "zh_CN", ignored -> {}, result::set) {
            @Override protected Version getVersion(boolean isPreview) throws java.io.IOException {
                if (isPreview) { return preview; }
                if (failure instanceof java.io.IOException) { throw (java.io.IOException) failure; }
                throw (RuntimeException) failure;
            }
        };
    }

    private static class FakeUpdateVersionTimerTask extends UpdateVersionTimerTask {

        private final UpdateVersionCheckResult result;

        FakeUpdateVersionTimerTask(UpdateVersionCheckResult result, AtomicReference<Version> versionConsumer,
                                   AtomicReference<UpdateVersionCheckResult> resultConsumer) {
            super(false, "zh_CN", versionConsumer::set, resultConsumer::set);
            this.result = result;
        }

        @Override
        protected UpdateVersionCheckResult fetchLastVersion(boolean ckPreview) {
            return result;
        }
    }
}

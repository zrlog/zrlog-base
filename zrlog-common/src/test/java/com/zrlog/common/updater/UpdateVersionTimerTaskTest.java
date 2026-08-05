package com.zrlog.common.updater;

import com.zrlog.common.vo.Version;
import org.junit.Test;

import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

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

    private static Version version(String value) {
        Version version = new Version();
        version.setVersion(value);
        version.setBuildId("build");
        version.setBuildDate(new Date(1_000));
        return version;
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

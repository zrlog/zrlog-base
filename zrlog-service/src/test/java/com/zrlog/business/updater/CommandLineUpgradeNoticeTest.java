package com.zrlog.business.updater;

import com.zrlog.common.Updater;
import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.updater.UpdateChannel;
import com.zrlog.common.updater.UpdateVersionCheckResult;
import com.zrlog.common.vo.Version;
import org.junit.Test;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CommandLineUpgradeNoticeTest {

    @Test
    public void shouldBuildReleaseCommandForZipPackage() {
        String notice = nextNotice(result("3.9.0", "release-build", UpdateChannel.RELEASE),
                new TestUpdater(UpdaterTypeEnum.ZIP, new File("/opt/zrlog/zrlog-starter.jar")), new HashSet<>());

        assertNotNull(notice);
        assertTrue(notice.contains("ZrLog update available"));
        assertTrue(notice.contains("Latest:  3.9.0"));
        assertTrue(notice.contains("Channel: release"));
        assertTrue(notice.contains("java -jar \"/opt/zrlog/zrlog-starter.jar\" upgrade --channel=release"));
    }

    @Test
    public void shouldBuildPreviewCommandForNativeImage() {
        String notice = nextNotice(result("3.9.0-SNAPSHOT", "preview-build", UpdateChannel.PREVIEW),
                new TestUpdater(UpdaterTypeEnum.NATIVE_IMAGE, new File("/opt/zrlog/zrlog")), new HashSet<>());

        assertNotNull(notice);
        assertTrue(notice.contains("Latest:  3.9.0"));
        assertTrue(notice.contains("Channel: preview"));
        assertTrue(notice.contains("\"/opt/zrlog/zrlog\" upgrade --channel=preview"));
    }

    @Test
    public void shouldSkipUnsupportedAndManagedRuntimeTypes() {
        UpdateVersionCheckResult result = result("3.9.0", "build", UpdateChannel.RELEASE);
        Updater zipUpdater = new TestUpdater(UpdaterTypeEnum.ZIP, new File("/opt/zrlog/zrlog-starter.jar"));

        assertNull(nextNotice(result, new TestUpdater(UpdaterTypeEnum.WAR, new File("/opt/zrlog/zrlog.war")),
                new HashSet<>()));
        assertNull(CommandLineUpgradeNotice.nextNotice(result, zipUpdater, true, false, false, false,
                new HashSet<>()));
        assertNull(CommandLineUpgradeNotice.nextNotice(result, zipUpdater, false, true, false, false,
                new HashSet<>()));
        assertNull(CommandLineUpgradeNotice.nextNotice(result, zipUpdater, false, false, true, false,
                new HashSet<>()));
        assertNull(CommandLineUpgradeNotice.nextNotice(result, zipUpdater, false, false, false, true,
                new HashSet<>()));
        assertNull(nextNotice(new UpdateVersionCheckResult(result.getVersion(), UpdateChannel.RELEASE, false),
                zipUpdater, new HashSet<>()));
    }

    @Test
    public void shouldNotifyOnlyOnceForTheSameTargetVersion() {
        Set<String> notifiedUpdates = new HashSet<>();
        Updater updater = new TestUpdater(UpdaterTypeEnum.ZIP, new File("/opt/zrlog/zrlog-starter.jar"));

        assertNotNull(nextNotice(result("3.9.0", "same-build", UpdateChannel.RELEASE), updater,
                notifiedUpdates));
        assertNull(nextNotice(result("3.9.0", "same-build", UpdateChannel.PREVIEW), updater,
                notifiedUpdates));
        assertNotNull(nextNotice(result("3.9.1", "next-build", UpdateChannel.RELEASE), updater,
                notifiedUpdates));
    }

    private static String nextNotice(UpdateVersionCheckResult result, Updater updater, Set<String> notifiedUpdates) {
        return CommandLineUpgradeNotice.nextNotice(result, updater, false, false, false, false, notifiedUpdates);
    }

    private static UpdateVersionCheckResult result(String versionValue, String buildId, UpdateChannel channel) {
        Version version = new Version();
        version.setVersion(versionValue);
        version.setBuildId(buildId);
        return new UpdateVersionCheckResult(version, channel, true);
    }

    private static class TestUpdater implements Updater {

        private final UpdaterTypeEnum type;
        private final File execFile;

        TestUpdater(UpdaterTypeEnum type, File execFile) {
            this.type = type;
            this.execFile = execFile;
        }

        @Override
        public void restartProcessAsync(Version upgradeVersion) {
        }

        @Override
        public String getUnzipPath() {
            return "";
        }

        @Override
        public File execFile() {
            return execFile;
        }

        @Override
        public UpdaterTypeEnum getType() {
            return type;
        }
    }
}

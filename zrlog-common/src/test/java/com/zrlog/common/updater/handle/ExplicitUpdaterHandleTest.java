package com.zrlog.common.updater.handle;

import com.zrlog.common.Constants;
import com.zrlog.common.Updater;
import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.updater.UpgradeProgressListener;
import com.zrlog.common.vo.Version;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ExplicitUpdaterHandleTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void shouldUpgradeWithInjectedUpdaterBeforeGlobalConfigExists() throws Exception {
        File packageFile = temporaryFolder.newFile("zrlog.zip");
        try (ZipOutputStream outputStream = new ZipOutputStream(new FileOutputStream(packageFile))) {
            outputStream.putNextEntry(new ZipEntry("version.txt"));
            outputStream.write("new-version".getBytes(StandardCharsets.UTF_8));
            outputStream.closeEntry();
        }
        File target = temporaryFolder.newFolder("runtime");
        TestUpdater updater = new TestUpdater(target);
        Constants.zrLogConfig = null;

        ZipUpdateVersionHandle handler = new ZipUpdateVersionHandle(packageFile, new HashMap<>(), new Version(),
                UpgradeProgressListener.NONE, updater);
        handler.doHandle();

        assertTrue(handler.isFinish());
        assertTrue(updater.restarted);
        assertEquals("new-version", Files.readString(new File(target, "version.txt").toPath()));
        assertNull(Constants.zrLogConfig);
    }

    private static class TestUpdater implements Updater {

        private final File target;
        private boolean restarted;

        TestUpdater(File target) {
            this.target = target;
        }

        @Override
        public void restartProcessAsync(Version upgradeVersion) {
            restarted = true;
        }

        @Override
        public String getUnzipPath() {
            return target.getAbsolutePath();
        }

        @Override
        public File execFile() {
            return new File(target, "zrlog-starter.jar");
        }

        @Override
        public UpdaterTypeEnum getType() {
            return UpdaterTypeEnum.ZIP;
        }
    }
}

package com.zrlog.common.adapter;

import com.hibegin.common.dao.DataSourceWrapper;
import com.zrlog.common.Updater;
import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.ZrLogConfig;
import com.zrlog.common.vo.Version;
import com.zrlog.plugin.IPlugin;
import com.zrlog.updater.spi.UpdateSession;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

public class UpdaterHostLifecycleTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private static Updater updater(int port) {
        UpdaterHosts.ApplicationHost host = UpdaterHosts.application(new String[]{"--port=" + port}, null);
        return host.adapt(new UpdateSession(host.context(), new FakeUpdater()));
    }

    @Test
    public void upgradeCallbacksFollowTheOwningServerPortAndStopWithoutGlobalConfig() throws Exception {
        String oldRoot = System.getProperty("sws.root.path");
        try {
            System.setProperty("sws.root.path", temporaryFolder.newFolder().getAbsolutePath());
            Updater first = updater(18080);
            assertEquals(18080, UpdaterHosts.context(first).port());
            UpdaterHosts.context(first).stop.run(); // CLI context is valid before a server is attached.
            TestConfig firstServer = new TestConfig(19080, first);
            Updater second = updater(18081);
            TestConfig secondServer = new TestConfig(19081, second);

            firstServer.getServerConfig().setPort(20080);
            assertEquals(20080, UpdaterHosts.context(first).port());
            assertEquals(19081, UpdaterHosts.context(second).port());
            UpdaterHosts.context(first).stop.run();
            assertTrue(firstServer.stopped);
            assertFalse(secondServer.stopped);

            secondServer.getServerConfig().setPort(20081);
            assertEquals(20081, UpdaterHosts.context(second).port());
            UpdaterHosts.context(second).stop.run();
            assertTrue(secondServer.stopped);
        } finally {
            if (oldRoot == null) System.clearProperty("sws.root.path");
            else System.setProperty("sws.root.path", oldRoot);
        }
    }

    private static class TestConfig extends ZrLogConfig {
        private boolean stopped;

        TestConfig(int port, Updater updater) {
            super(port, updater, "");
        }

        @Override
        public DataSourceWrapper configDatabase() {
            return null;
        }

        @Override
        public List<IPlugin> getBasePluginList() {
            return Collections.emptyList();
        }

        @Override
        public void stop() {
            stopped = true;
        }
    }

    private static class FakeUpdater implements Updater {
        @Override
        public void restartProcessAsync(Version version) {
            throw new AssertionError("No process should be started");
        }

        @Override
        public String getUnzipPath() {
            return "";
        }

        @Override
        public File execFile() {
            return null;
        }

        @Override
        public UpdaterTypeEnum getType() {
            return UpdaterTypeEnum.ZIP;
        }
    }
}

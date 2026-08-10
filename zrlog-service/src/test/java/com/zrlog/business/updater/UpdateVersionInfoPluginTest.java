package com.zrlog.business.updater;

import com.hibegin.common.util.EnvKit;
import com.zrlog.business.rest.base.UpgradeWebSiteInfo;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.business.support.InMemoryZrLogDatabase;
import com.zrlog.business.support.InMemoryZrLogDatabase.DatabaseType;
import com.zrlog.common.updater.UpdateVersionTimerTask;
import com.zrlog.common.vo.Version;
import org.junit.Test;
import org.junit.experimental.theories.DataPoints;
import org.junit.experimental.theories.Theories;
import org.junit.experimental.theories.Theory;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(Theories.class)
public class UpdateVersionInfoPluginTest {

    @DataPoints
    public static final DatabaseType[] DATABASES = DatabaseType.values();

    @Test
    public void shouldNormalizePreviewAndReleaseVersionsForDisplay() {
        Version preview = version("3.6.0-SNAPSHOT");
        Version release = version("3.6.0");

        Version normalizedPreview = UpdateVersionInfoPlugin.normalizeVersionForDisplay(preview);
        Version normalizedRelease = UpdateVersionInfoPlugin.normalizeVersionForDisplay(release);

        assertEquals("3.6.0", normalizedPreview.getVersion());
        assertEquals("3.6.0-SNAPSHOT", preview.getVersion());
        assertNotNull(normalizedPreview.getType());
        assertEquals("3.6.0", normalizedRelease.getVersion());
        assertNotNull(normalizedRelease.getType());
        assertNull(UpdateVersionInfoPlugin.normalizeVersionForDisplay(null));
    }

    @Test
    public void shouldExposeLifecycleWithoutStartingNetworkTask() {
        UpdateVersionInfoPlugin plugin = new UpdateVersionInfoPlugin();

        assertEquals(!EnvKit.isFaaSMode(), plugin.autoStart());
        assertFalse(plugin.isStarted());
        assertNull(plugin.getLastVersion(false));
        assertEquals(true, plugin.stop());
        assertFalse(plugin.isStarted());
    }

    @Theory
    public void shouldStartWithoutSchedulingWhenAutoUpgradeIsDisabled(DatabaseType databaseType) throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open(databaseType)) {
            db.update("insert into website(name, value, remark) values(?, ?, ?)",
                    WebsiteKvService.AUTO_UPGRADE_VERSION_KEY,
                    String.valueOf(AutoUpgradeVersionType.NEVER.getCycle()), "");
            UpdateVersionInfoPlugin plugin = new UpdateVersionInfoPlugin();

            assertTrue(plugin.start());

            assertFalse(plugin.isStarted());
            assertTrue(plugin.stop());
        }
    }

    @Theory
    public void shouldStartScheduledVersionTaskFromDatabaseConfigWithoutNetwork(DatabaseType databaseType) throws Exception {
        try (InMemoryZrLogDatabase db = InMemoryZrLogDatabase.open(databaseType)) {
            db.update("insert into website(name, value, remark) values(?, ?, ?)",
                    WebsiteKvService.AUTO_UPGRADE_VERSION_KEY,
                    String.valueOf(AutoUpgradeVersionType.ONE_MINUTE.getCycle()), "");
            db.update("insert into website(name, value, remark) values(?, ?, ?)",
                    WebsiteKvService.UPGRADE_PREVIEW_KEY, "true", "");
            TestUpdateVersionInfoPlugin plugin = new TestUpdateVersionInfoPlugin(new FakeVersionTimerTask(version("3.7.0")));

            assertTrue(plugin.start());

            assertTrue(plugin.isStarted());
            assertTrue(plugin.taskCreated.get());
            assertEquals(Boolean.TRUE, plugin.upgradePreview);
            assertTrue(plugin.stop());
            assertFalse(plugin.isStarted());
        }
    }

    @Test
    public void shouldFetchLastVersionThroughVersionTaskAndNormalizeDisplayVersion() {
        FakeVersionTimerTask task = new FakeVersionTimerTask(version("3.8.0-SNAPSHOT"));
        TestUpdateVersionInfoPlugin plugin = new TestUpdateVersionInfoPlugin(task);

        Version version = plugin.getLastVersion(true);

        assertEquals("3.8.0", version.getVersion());
        assertEquals(1, task.runCount.get());
    }

    @Test
    public void shouldReturnCachedTaskVersionWithoutRunningWhenFetchIsFalse() {
        FakeVersionTimerTask task = new FakeVersionTimerTask(version("3.9.0"));
        TestUpdateVersionInfoPlugin plugin = new TestUpdateVersionInfoPlugin(task);

        Version version = plugin.getLastVersion(false);

        assertEquals("3.9.0", version.getVersion());
        assertEquals(0, task.runCount.get());
    }

    @Test
    public void shouldSwallowVersionTaskFailureDuringManualFetch() {
        FakeVersionTimerTask task = new FakeVersionTimerTask(version("4.0.0"));
        task.throwOnRun = true;
        TestUpdateVersionInfoPlugin plugin = new TestUpdateVersionInfoPlugin(task);

        Version version = plugin.getLastVersion(true);

        assertEquals("4.0.0", version.getVersion());
        assertEquals(1, task.runCount.get());
    }

    @Test
    public void shouldReturnFetchedCurrentChangeLogMarkdown() {
        AtomicReference<String> requestedPath = new AtomicReference<>();
        String changeLog = UpdateVersionInfoPlugin.getCurrentChangeLog(noChangeLogRes(), (version, buildId, locale) -> {
            requestedPath.set(version + "/" + buildId + "/" + locale);
            return "### current\n- fixed";
        });

        assertEquals("### current\n- fixed", changeLog);
        assertNotNull(requestedPath.get());
    }

    @Test
    public void shouldFallbackCurrentChangeLogWhenRemoteContentIsHtml() {
        String changeLog = UpdateVersionInfoPlugin.getCurrentChangeLog(noChangeLogRes(), (version, buildId, locale) ->
                "<html><body>not markdown</body></html>");

        assertTrue(changeLog.startsWith("No commit log"));
        assertTrue(changeLog.contains("94fzb/zrlog/commits/"));
        assertTrue(changeLog.contains("https://github.com/94fzb/zrlog/commits/"));
    }

    @Test
    public void shouldFallbackCurrentChangeLogWhenFetchFails() {
        String changeLog = UpdateVersionInfoPlugin.getCurrentChangeLog(noChangeLogRes(), (version, buildId, locale) -> {
            throw new IOException("offline");
        });

        assertTrue(changeLog.startsWith("No commit log"));
        assertFalse(changeLog.contains("diff"));
        assertTrue(changeLog.contains("commit"));
    }

    private static Map<String, Object> noChangeLogRes() {
        Map<String, Object> res = new HashMap<>();
        res.put("upgrade.result.noChangeLog", "No diff log");
        return res;
    }

    private static Version version(String value) {
        Version version = new Version();
        version.setVersion(value);
        version.setBuildId("build");
        version.setBuildDate(new Date(1_000));
        version.setReleaseDate("1970-01-01 00:00");
        return version;
    }

    private static class TestUpdateVersionInfoPlugin extends UpdateVersionInfoPlugin {

        private final UpdateVersionTimerTask task;
        private final AtomicBoolean taskCreated = new AtomicBoolean();
        private Boolean upgradePreview;

        TestUpdateVersionInfoPlugin(UpdateVersionTimerTask task) {
            this.task = task;
        }

        @Override
        UpdateVersionTimerTask newUpdateVersionTimerTask(UpgradeWebSiteInfo upgradeWebSiteInfo) {
            taskCreated.set(true);
            upgradePreview = upgradeWebSiteInfo.getUpgradePreview();
            return task;
        }
    }

    private static class FakeVersionTimerTask extends UpdateVersionTimerTask {

        private final Version version;
        private final AtomicInteger runCount = new AtomicInteger();
        private boolean throwOnRun;

        FakeVersionTimerTask(Version version) {
            super(false, "zh_CN");
            this.version = version;
        }

        @Override
        public void run() {
            runCount.incrementAndGet();
            if (throwOnRun) {
                throw new IllegalStateException("fetch failed");
            }
        }

        @Override
        public Version getVersion() {
            return version;
        }
    }
}

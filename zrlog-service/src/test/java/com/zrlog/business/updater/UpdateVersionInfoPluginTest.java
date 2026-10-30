package com.zrlog.business.updater;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.util.EnvKit;
import com.zrlog.business.rest.base.UpgradeWebSiteInfo;
import com.zrlog.business.service.UpgradeNoticeService;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.common.vo.Version;
import com.zrlog.model.WebSite;
import com.zrlog.test.support.ZrLogTestDatabase;
import com.zrlog.test.support.ZrLogTestDatabase.DatabaseType;
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

import static org.junit.Assert.*;

@RunWith(Theories.class)
public class UpdateVersionInfoPluginTest {

    @DataPoints
    public static final DatabaseType[] DATABASES = DatabaseType.values();

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

    @Test
    public void shouldLabelPreviewAndReleaseWithoutChangingVersionIdentity() {
        Version preview = version("3.6.0-SNAPSHOT");
        preview.setArtifactVersion("3.6.0-SNAPSHOT");
        preview.setChannel("preview");
        Version release = version("3.6.0");

        Version normalizedPreview = UpdateVersionInfoPlugin.normalizeVersionForDisplay(preview);
        Version normalizedRelease = UpdateVersionInfoPlugin.normalizeVersionForDisplay(release);

        assertEquals("3.6.0-SNAPSHOT", normalizedPreview.getVersion());
        assertEquals(preview.getArtifactVersion(), normalizedPreview.getArtifactVersion());
        assertEquals(preview.getBuildId(), normalizedPreview.getBuildId());
        assertEquals(preview.getChannel(), normalizedPreview.getChannel());
        assertEquals("3.6.0-SNAPSHOT", preview.getVersion());
        assertNotNull(normalizedPreview.getType());
        assertEquals("3.6.0", normalizedRelease.getVersion());
        assertNotNull(normalizedRelease.getType());
        assertNull(UpdateVersionInfoPlugin.normalizeVersionForDisplay(null));
    }

    @Theory
    public void storedUpgradeNoticeKeepsTheCompletePreviewVersion(DatabaseType databaseType) throws Exception {
        try (var db = ZrLogTestDatabase.open(databaseType)) {
            var notices = new UpgradeNoticeService(() -> true, new WebsiteKvService(() -> new WebSite(db.dataSource())));
            Version preview = version("99.0.0-SNAPSHOT");
            preview.setArtifactVersion(preview.getVersion());
            preview.setBuildDate(new Date(System.currentTimeMillis() + 3600000));
            notices.sync(preview);

            assertEquals("99.0.0-SNAPSHOT", notices.getStoredUpgradeNotice().getVersion().getVersion());
            assertEquals("99.0.0-SNAPSHOT", notices.getNotice().getVersion().getVersion());
            assertEquals("99.0.0-SNAPSHOT", notices.getNotice().getVersion().getArtifactVersion());
        }
    }

    @Test
    public void shouldExposeLifecycleWithoutStartingNetworkTask() {
        UpdateVersionInfoPlugin plugin = new UpdateVersionInfoPlugin(new UpgradeNoticeService(() -> false, new WebsiteKvService(() -> {
            throw new IllegalStateException("Database is unavailable in this test");
        })), () -> "zh_CN", null, new WebsiteKvService(() -> {
            throw new IllegalStateException("Database is unavailable in this test");
        }));

        assertEquals(!EnvKit.isFaaSMode(), plugin.autoStart());
        assertFalse(plugin.isStarted());
        assertNull(plugin.getLastVersion(false));
        assertEquals(true, plugin.stop());
        assertFalse(plugin.isStarted());
    }

    @Theory
    public void hostSchedulingDisablesLegacyTimerAndKeepsOnDemandChecks(DatabaseType databaseType) throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            db.update("insert into website(name, value) values(?, ?)", WebsiteKvService.AUTO_UPGRADE_VERSION_KEY, "60");
            var plugin = new TestUpdateVersionInfoPlugin(new FakeVersionTimerTask(version("3.7.0")), db.dataSource());
            plugin.setExternallyScheduled(true);
            assertFalse(plugin.autoStart());
            assertTrue(plugin.start());
            assertFalse(plugin.isStarted());
            assertNotNull(plugin.getLastVersion(true));
            assertFalse(plugin.isStarted());
        }
    }

    @Theory
    public void shouldStartWithoutSchedulingWhenAutoUpgradeIsDisabled(DatabaseType databaseType) throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            db.update("insert into website(name, value, remark) values(?, ?, ?)",
                    WebsiteKvService.AUTO_UPGRADE_VERSION_KEY,
                    String.valueOf(AutoUpgradeVersionType.NEVER.getCycle()), "");
            UpdateVersionInfoPlugin plugin = new UpdateVersionInfoPlugin(new UpgradeNoticeService(() -> false, new WebsiteKvService(() -> new WebSite(db.dataSource()))), () -> "zh_CN", null, new WebsiteKvService(() -> new WebSite(db.dataSource())));

            assertTrue(plugin.start());

            assertFalse(plugin.isStarted());
            assertTrue(plugin.stop());
        }
    }

    @Theory
    public void shouldStartScheduledVersionTaskFromDatabaseConfigWithoutNetwork(DatabaseType databaseType) throws Exception {
        try (ZrLogTestDatabase db = ZrLogTestDatabase.open(databaseType)) {
            db.update("insert into website(name, value, remark) values(?, ?, ?)",
                    WebsiteKvService.AUTO_UPGRADE_VERSION_KEY,
                    String.valueOf(AutoUpgradeVersionType.ONE_MINUTE.getCycle()), "");
            db.update("insert into website(name, value, remark) values(?, ?, ?)",
                    WebsiteKvService.UPGRADE_PREVIEW_KEY, "true", "");
            TestUpdateVersionInfoPlugin plugin = new TestUpdateVersionInfoPlugin(new FakeVersionTimerTask(version("3.7.0")), db.dataSource());

            assertTrue(plugin.start());

            assertTrue(plugin.isStarted());
            assertTrue(plugin.taskCreated.get());
            assertEquals(Boolean.TRUE, plugin.upgradePreview);
            assertTrue(plugin.stop());
            assertFalse(plugin.isStarted());
        }
    }

    @Test
    public void shouldFetchLastVersionThroughVersionTaskWithoutRemovingSnapshot() {
        FakeVersionTimerTask task = new FakeVersionTimerTask(version("3.8.0-SNAPSHOT"));
        TestUpdateVersionInfoPlugin plugin = new TestUpdateVersionInfoPlugin(task, null);

        Version version = plugin.getLastVersion(true);

        assertEquals("3.8.0-SNAPSHOT", version.getVersion());
        assertEquals(1, task.runCount.get());
    }

    @Test
    public void shouldReturnCachedTaskVersionWithoutRunningWhenFetchIsFalse() {
        FakeVersionTimerTask task = new FakeVersionTimerTask(version("3.9.0"));
        TestUpdateVersionInfoPlugin plugin = new TestUpdateVersionInfoPlugin(task, null);

        Version version = plugin.getLastVersion(false);

        assertEquals("3.9.0", version.getVersion());
        assertEquals(0, task.runCount.get());
    }

    @Test
    public void shouldSwallowVersionTaskFailureDuringManualFetch() {
        FakeVersionTimerTask task = new FakeVersionTimerTask(version("4.0.0"));
        task.throwOnRun = true;
        TestUpdateVersionInfoPlugin plugin = new TestUpdateVersionInfoPlugin(task, null);

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

    private static class TestUpdateVersionInfoPlugin extends UpdateVersionInfoPlugin {

        private final UpdateVersionTimerTask task;
        private final AtomicBoolean taskCreated = new AtomicBoolean();
        private Boolean upgradePreview;

        TestUpdateVersionInfoPlugin(UpdateVersionTimerTask task, DataSourceWrapper testDataSource) {
            super(new UpgradeNoticeService(() -> false, new WebsiteKvService(() -> new WebSite(testDataSource))), () -> "zh_CN", null, new WebsiteKvService(() -> new WebSite(testDataSource)));
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

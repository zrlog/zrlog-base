package com.zrlog.business.updater;

import com.hibegin.common.util.BeanUtil;
import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.common.util.http.HttpUtil;
import com.zrlog.business.rest.base.UpgradeWebSiteInfo;
import com.zrlog.business.service.UpgradeNoticeService;
import com.zrlog.business.service.WebsiteKvService;
import com.zrlog.common.Updater;
import com.zrlog.common.vo.Version;
import com.zrlog.plugin.IPlugin;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ThreadUtils;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 基于插件的实现，使用定时器，定时检查是否有新的版本发布。
 */
public class UpdateVersionInfoPlugin implements IPlugin {

    private static final Logger LOGGER = LoggerUtil.getLogger(UpdateVersionInfoPlugin.class);

    private final UpgradeNoticeService notices;
    private final WebsiteKvService websiteKv;
    private final Supplier<String> language;
    private final CommandLineUpgradeNotice commandLineNotice;
    private ScheduledExecutorService scheduledExecutorService;
    private UpdateVersionTimerTask updateVersionTimerTask;
    private boolean externallyScheduled;

    public UpdateVersionInfoPlugin(
            UpgradeNoticeService notices,
            Supplier<String> language,
            Updater updater,
            WebsiteKvService websiteKv) {
        this.websiteKv = Objects.requireNonNull(websiteKv);
        this.notices = Objects.requireNonNull(notices);
        this.language = Objects.requireNonNull(language);
        this.commandLineNotice = new CommandLineUpgradeNotice(updater);
    }

    private static boolean isPreviewVersion(Version version) {
        return Objects.nonNull(version) && StringUtils.isNotEmpty(version.getVersion())
                && version.getVersion().contains("-SNAPSHOT");
    }

    public static Version normalizeVersionForDisplay(Version version) {
        if (Objects.isNull(version)) {
            return null;
        }
        Version copyVersion = BeanUtil.convert(version, Version.class);
        copyVersion.setType(I18nUtil.getBackendStringFromRes(
                isPreviewVersion(version) ? "version.type.preview" : "version.type.release"));
        //不在页面展示SNAPSHOT
        copyVersion.setVersion(version.getVersion().replaceAll("-SNAPSHOT", ""));
        return copyVersion;
    }

    public static String getCurrentChangeLog(Map<String, Object> res) {
        return getCurrentChangeLog(res, UpdateVersionInfoPlugin::fetchCurrentChangeLog);
    }

    static String getCurrentChangeLog(Map<String, Object> res, ChangeLogFetcher fetcher) {
        String version = BlogBuildInfoUtil.getVersion();
        String buildId = BlogBuildInfoUtil.getBuildId();
        try {
            String changeLogMd = fetcher.fetch(version, buildId, I18nUtil.getCurrentLocale());
            if (StringUtils.isNotEmpty(changeLogMd) && !UpdateVersionTimerTask.isHtml(changeLogMd)) {
                return changeLogMd;
            }
        } catch (IOException | InterruptedException | URISyntaxException e) {
            LOGGER.log(Level.SEVERE, "", e);
        }
        String uriPath = "94fzb/zrlog/commits/" + buildId;
        String changeUrl = "https://github.com/" + uriPath;
        return (res.get("upgrade.result.noChangeLog") + "\n[" + uriPath + "](" + changeUrl + ")").replace("diff", "commit");
    }

    private static String fetchCurrentChangeLog(String version, String buildId, String locale)
            throws IOException, InterruptedException, URISyntaxException {
        return HttpUtil.getInstance().getSuccessTextByUrl("https://www.zrlog.com/changelog/" +
                version + "-" + buildId + ".md?lang=" + locale + "&v=" + buildId);
    }

    @Override
    public boolean autoStart() {
        return !externallyScheduled && !EnvKit.isFaaSMode();
    }

    private void initExecutorService() {
        scheduledExecutorService = new ScheduledThreadPoolExecutor(1, r -> {
            Thread thread = ThreadUtils.unstarted(r);
            thread.setName("update-version-plugin-thread");
            return thread;
        });
    }

    UpdateVersionTimerTask newUpdateVersionTimerTask(UpgradeWebSiteInfo upgradeWebSiteInfo) {
        return new UpdateVersionTimerTask(upgradeWebSiteInfo.getUpgradePreview(), language.get(),
                notices::sync, commandLineNotice::notifyUpdate);
    }

    @Override
    public boolean start() {
        if (externallyScheduled) return true;
        if (isStarted()) {
            return true;
        }
        UpgradeWebSiteInfo upgradeWebSiteInfo = websiteKv.upgradeWebSiteInfo();
        AutoUpgradeVersionType autoUpgradeVersionType = AutoUpgradeVersionType.cycle(upgradeWebSiteInfo.getAutoUpgradeVersion());
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
        }
        //开启了定时检查，定时器开始工作
        if (autoUpgradeVersionType != AutoUpgradeVersionType.NEVER) {
            initExecutorService();
            updateVersionTimerTask = newUpdateVersionTimerTask(upgradeWebSiteInfo);
            scheduledExecutorService.scheduleAtFixedRate(updateVersionTimerTask, 0, autoUpgradeVersionType.getCycle(), TimeUnit.SECONDS);
        }
        LOGGER.info("UpdateVersionPlugin start autoUpgradeVersionType " + autoUpgradeVersionType);
        return true;
    }

    /** Keep on-demand version checks available when scheduling belongs to the host. */
    public synchronized void setExternallyScheduled(boolean value) {
        if (value) stop();
        externallyScheduled = value;
    }

    public boolean isExternallyScheduled() { return externallyScheduled; }

    @Override
    public boolean isStarted() {
        return Objects.nonNull(scheduledExecutorService);
    }

    @Override
    public boolean stop() {
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
            scheduledExecutorService = null;
        }
        updateVersionTimerTask = null;
        return true;
    }

    /**
     * 查询最近一次版本检查是否失败。
     *
     * @return 最近一次版本检查是否失败
     */
    public boolean isLastCheckFailed() {
        return updateVersionTimerTask != null && updateVersionTimerTask.isLastCheckFailed();
    }

    /**
     * 获取最新的版本信息。
     *
     * @param fetch 是否发起新的请求
     * @return 版本信息
     */
    public Version getLastVersion(boolean fetch) {
        if (updateVersionTimerTask == null) {
            UpgradeWebSiteInfo upgradeWebSiteInfo = websiteKv.upgradeWebSiteInfo();
            updateVersionTimerTask = newUpdateVersionTimerTask(upgradeWebSiteInfo);
        }
        if (fetch) {
            try {
                updateVersionTimerTask.run();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "", e);
            }
        }
        return normalizeVersionForDisplay(updateVersionTimerTask.getVersion());
    }

    @FunctionalInterface
    interface ChangeLogFetcher {
        String fetch(String version, String buildId, String locale)
                throws IOException, InterruptedException, URISyntaxException;
    }
}

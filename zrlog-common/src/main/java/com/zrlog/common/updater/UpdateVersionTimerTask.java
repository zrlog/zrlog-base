package com.zrlog.common.updater;

import com.google.gson.Gson;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.hibegin.common.util.http.HttpUtil;
import com.zrlog.common.Constants;
import com.zrlog.common.vo.Version;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ZrLogUtil;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.function.Consumer;

/**
 * 定时检查是否有新的更新包可用，比对服务器生成最新buildId和包的构建时间（与resources/build.properties对比，注意：开发环境没有这个文件）
 */
public class UpdateVersionTimerTask extends TimerTask {

    private static final Logger LOGGER = LoggerUtil.getLogger(UpdateVersionTimerTask.class);
    private Version version;

    private final boolean previewAble;
    private final String lang;
    private final Consumer<Version> versionConsumer;
    private final Consumer<UpdateVersionCheckResult> checkResultConsumer;

    public UpdateVersionTimerTask(boolean previewAble, String lang) {
        this(previewAble, lang, version -> {
        });
    }

    public UpdateVersionTimerTask(boolean previewAble, String lang, Consumer<Version> versionConsumer) {
        this(previewAble, lang, versionConsumer, result -> {
        });
    }

    public UpdateVersionTimerTask(boolean previewAble, String lang, Consumer<Version> versionConsumer,
                                  Consumer<UpdateVersionCheckResult> checkResultConsumer) {
        this.previewAble = previewAble;
        this.lang = lang;
        this.versionConsumer = versionConsumer;
        this.checkResultConsumer = checkResultConsumer;
    }

    public static boolean isHtml(String str) {
        return str.startsWith("<!DOCTYPE html>") || str.startsWith("<html>");
    }

    private String getChangeLog(String version, Date releaseDate, String buildId, Map<String, Object> res) {
        try {
            String changeLogMd = HttpUtil.getInstance().getSuccessTextByUrl("https://www.zrlog.com/changelog/" +
                    version + "-" + buildId + ".md?lang=" +
                    lang + "&v=" + BlogBuildInfoUtil.getBuildId());
            if (StringUtils.isNotEmpty(changeLogMd) && !isHtml(changeLogMd)) {
                return changeLogMd;
            }
        } catch (IOException | InterruptedException | URISyntaxException e) {
            if (Constants.debugLoggerPrintAble()) {
                LOGGER.log(Level.SEVERE, "", e);
            }
        }
        if (Objects.equals(BlogBuildInfoUtil.getBuildId(), buildId)) {
            return (String) res.get("upgrade.result.noChange");
        }
        String uriPath = "94fzb/zrlog/compare/" + BlogBuildInfoUtil.getBuildId() + "..." + buildId;
        String changeUrl = "https://github.com/" + uriPath;
        return "### " + version + " (" + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(releaseDate) + ")\n" + res.get("upgrade.result.noChangeLog") + "\n[" + uriPath + "](" + changeUrl + ")";
    }

    @Override
    public void run() {
        try {
            UpdateVersionCheckResult checkResult = fetchLastVersion(previewAble);
            Version lastVersion = checkResult.getVersion();
            versionConsumer.accept(lastVersion);
            checkResultConsumer.accept(checkResult);
            //build date ok
            if (lastVersion.getBuildDate().getTime() > 0) {
                this.version = lastVersion;
            }
        } catch (Exception e) {
            LOGGER.warning("fetchLastVersion error " + e.getMessage());
        }
    }

    protected UpdateVersionCheckResult fetchLastVersion(boolean ckPreview) throws IOException, ParseException,
            URISyntaxException, InterruptedException {
        Version lastVersion = getVersion(ckPreview);
        if (!ckPreview) {
            return checkResult(lastVersion, UpdateChannel.RELEASE);
        }
        //存在预览版本
        if (ZrLogUtil.greatThenCurrentVersion(lastVersion.getBuildId(), lastVersion.getBuildDate(), lastVersion.getVersion())) {
            return new UpdateVersionCheckResult(lastVersion, UpdateChannel.PREVIEW, true);
        }
        //如果已是最新预览版，那么尝试检查正式版本
        Version lastReleaseVersion = getVersion(false);
        if (ZrLogUtil.greatThenCurrentVersion(lastReleaseVersion.getBuildId(), lastReleaseVersion.getBuildDate(), lastReleaseVersion.getVersion())) {
            return new UpdateVersionCheckResult(lastReleaseVersion, UpdateChannel.RELEASE, true);
        }
        return lastVersion.getBuildDate().after(lastReleaseVersion.getBuildDate()) ?
                new UpdateVersionCheckResult(lastVersion, UpdateChannel.PREVIEW, false) :
                new UpdateVersionCheckResult(lastReleaseVersion, UpdateChannel.RELEASE, false);
    }

    private static UpdateVersionCheckResult checkResult(Version version, UpdateChannel channel) {
        return new UpdateVersionCheckResult(version, channel,
                ZrLogUtil.greatThenCurrentVersion(version.getBuildId(), version.getBuildDate(), version.getVersion()));
    }

    private static String getJsonFilename() {
        return BlogBuildInfoUtil.getUpdateVersionJsonFilename();
    }

    private Version getVersion(boolean preview) throws IOException, URISyntaxException, InterruptedException, ParseException {
        String versionUrl = BlogBuildInfoUtil.getResourceDownloadUrl() + "/" + (preview ? "preview" : "release") + "/" + getJsonFilename() + "?_" + System.currentTimeMillis() + "&v=" + BlogBuildInfoUtil.getBuildId();
        String txtContent = HttpUtil.getInstance().getSuccessTextByUrl(versionUrl);
        if (StringUtils.isEmpty(txtContent)) {
            LOGGER.warning("Fetch version [" + new URL(versionUrl).getPath() + "] info failed");
            Version errorVersion = new Version();
            errorVersion.setBuildDate(new Date(0));
            errorVersion.setBuildId("000000");
            return errorVersion;
        }
        Version versionInfo = new Gson().fromJson(txtContent.trim(), Version.class);
        Date versionDate = new SimpleDateFormat(Constants.DATE_FORMAT_PATTERN).parse(versionInfo.getReleaseDate());
        versionInfo.setBuildDate(versionDate);
        versionInfo.setReleaseDate(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(versionDate));
        //手动设置对应ChangeLog
        String language;
        if (Objects.nonNull(Constants.zrLogConfig)) {
            language = Constants.getLanguage();
        } else {
            language = "zh_CN";
        }
        if (Objects.isNull(language)) {
            versionInfo.setChangeLog("");
        } else {
            Map<String, Object> langRes = I18nUtil.getBackend();
            if (Objects.isNull(langRes)) {
                versionInfo.setChangeLog("");
            } else {
                versionInfo.setChangeLog(getChangeLog(versionInfo.getVersion(), versionInfo.getBuildDate(), versionInfo.getBuildId(), langRes));
            }
        }
        return versionInfo;
    }

    public Version getVersion() {
        return version;
    }
}

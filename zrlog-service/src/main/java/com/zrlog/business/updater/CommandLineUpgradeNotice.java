package com.zrlog.business.updater;

import com.hibegin.common.util.EnvKit;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.zrlog.common.Constants;
import com.zrlog.common.Updater;
import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.updater.UpdateVersionCheckResult;
import com.zrlog.common.vo.Version;
import com.zrlog.util.BlogBuildInfoUtil;
import com.zrlog.util.ZrLogUtil;

import java.io.File;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

final class CommandLineUpgradeNotice {

    private static final Logger LOGGER = LoggerUtil.getLogger(CommandLineUpgradeNotice.class);
    private static final Set<String> NOTIFIED_UPDATES = ConcurrentHashMap.newKeySet();

    private CommandLineUpgradeNotice() {
    }

    static void notifyUpdate(UpdateVersionCheckResult checkResult) {
        Updater updater = Objects.nonNull(Constants.zrLogConfig) ? Constants.zrLogConfig.getUpdater() : null;
        String notice = nextNotice(checkResult, updater, ZrLogUtil.isDockerMode(),
                ZrLogUtil.isSystemServiceMode(), EnvKit.isFaaSMode(), EnvKit.isDevMode(), NOTIFIED_UPDATES);
        if (Objects.nonNull(notice)) {
            LOGGER.info("\n" + notice);
        }
    }

    static String nextNotice(UpdateVersionCheckResult checkResult, Updater updater, boolean dockerMode,
                             boolean systemServiceMode, boolean faasMode, boolean devMode,
                             Set<String> notifiedUpdates) {
        if (Objects.isNull(checkResult) || !checkResult.isUpgradeAvailable() ||
                Objects.isNull(checkResult.getVersion()) || Objects.isNull(checkResult.getChannel()) ||
                Objects.isNull(updater) || dockerMode || systemServiceMode || faasMode || devMode) {
            return null;
        }
        if (updater.getType() != UpdaterTypeEnum.ZIP && updater.getType() != UpdaterTypeEnum.NATIVE_IMAGE) {
            return null;
        }
        File execFile = updater.execFile();
        if (Objects.isNull(execFile)) {
            return null;
        }
        Version version = checkResult.getVersion();
        String updateKey = Objects.toString(version.getVersion(), "") + ":" +
                Objects.toString(version.getBuildId(), "");
        if (!notifiedUpdates.add(updateKey)) {
            return null;
        }
        String channel = checkResult.getChannel().getValue();
        String command = buildCommand(updater.getType(), execFile, channel);
        return "ZrLog update available\n" +
                "Current: " + displayVersion(BlogBuildInfoUtil.getVersion()) + "\n" +
                "Latest:  " + displayVersion(version.getVersion()) + "\n" +
                "Channel: " + channel + "\n\n" +
                "Upgrade with:\n  " + command;
    }

    private static String buildCommand(UpdaterTypeEnum updaterType, File execFile, String channel) {
        String executable = quote(execFile.getAbsolutePath());
        if (updaterType == UpdaterTypeEnum.ZIP) {
            return "java -jar " + executable + " upgrade --channel=" + channel;
        }
        return executable + " upgrade --channel=" + channel;
    }

    private static String displayVersion(String version) {
        if (StringUtils.isEmpty(version)) {
            return "unknown";
        }
        return version.replace("-SNAPSHOT", "");
    }

    private static String quote(String path) {
        return "\"" + path.replace("\"", "\\\"") + "\"";
    }
}

package com.zrlog.common.updater.handle;

import com.zrlog.common.updater.UpdateVersionHandler;
import com.zrlog.common.updater.UpgradeProgressEvent;
import com.zrlog.common.updater.UpgradeProgressListener;
import com.zrlog.common.Constants;
import com.zrlog.common.Updater;
import com.zrlog.common.vo.Version;

import java.io.File;
import java.io.Serializable;
import java.util.Map;
import java.util.Objects;


public class WarUpdateVersionHandle implements Serializable, UpdateVersionHandler {
    private String message = "";
    private final File file;
    private boolean finish;
    private final Map<String, Object> backendRes;
    private String backup;
    private String upgradeFile;
    private final String upgradeKey;
    private final Version version;
    private final UpgradeProgressListener progressListener;
    private final Updater updater;

    public WarUpdateVersionHandle(File file, Map<String, Object> backendRes, String upgradeKey, Version version) {
        this(file, backendRes, upgradeKey, version, UpgradeProgressListener.NONE);
    }

    public WarUpdateVersionHandle(File file, Map<String, Object> backendRes, String upgradeKey, Version version,
                                  UpgradeProgressListener progressListener) {
        this(file, backendRes, upgradeKey, version, progressListener, null);
    }

    public WarUpdateVersionHandle(File file, Map<String, Object> backendRes, String upgradeKey, Version version,
                                  UpgradeProgressListener progressListener, Updater updater) {
        this.file = file;
        this.backendRes = backendRes;
        this.upgradeKey = upgradeKey;
        this.version = version;
        this.progressListener = progressListener;
        this.updater = updater;
    }

    /**
     * 提示更新进度
     */
    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public boolean isFinish() {
        return finish;
    }

    @Override
    public void doHandle() {
        try {
            Updater currentUpdater = Objects.requireNonNullElseGet(updater, () -> Constants.zrLogConfig.getUpdater());
            if (Objects.isNull(backup)) {
                publishStatus(UpgradeProgressEvent.STAGE_BACKUP, UpgradeProgressEvent.STATUS_RUNNING,
                        "upgrade.status.backingUp", null);
                backup = currentUpdater.backup();
            }
            publishStatus(UpgradeProgressEvent.STAGE_BACKUP, UpgradeProgressEvent.STATUS_COMPLETE,
                    "upgrade.status.backup", backup);
            if (Objects.isNull(upgradeFile)) {
                publishStatus(UpgradeProgressEvent.STAGE_MERGE, UpgradeProgressEvent.STATUS_RUNNING,
                        "upgrade.status.merge", null);
                currentUpdater.restartProcessAsync(version);
                upgradeFile = currentUpdater.buildUpgradeFile(file.toString(), upgradeKey);
            }
            publishStatus(UpgradeProgressEvent.STAGE_MERGE, UpgradeProgressEvent.STATUS_COMPLETE,
                    "upgrade.status.merged", upgradeFile);
            publishStatus(UpgradeProgressEvent.STAGE_RESTART, UpgradeProgressEvent.STATUS_RUNNING,
                    "upgrade.status.restarting", null);
            publishStatus(UpgradeProgressEvent.STAGE_RESTART, UpgradeProgressEvent.STATUS_COMPLETE,
                    "upgrade.status.restartSubmitted", null);
            publishStatus(UpgradeProgressEvent.STAGE_COMPLETE, UpgradeProgressEvent.STATUS_COMPLETE,
                    "upgrade.status.completed", null);
            finish = true;
        } catch (Exception e) {
            publishStatus(UpgradeProgressEvent.STAGE_ERROR, UpgradeProgressEvent.STATUS_ERROR,
                    "upgrade.error.generic", e.getMessage());
        }
    }

    private void publishStatus(String stage, String status, String key, String detail) {
        String progressMessage = Objects.toString(backendRes.get(key), key);
        message = Objects.nonNull(detail) ? progressMessage + " " + detail : progressMessage;
        try {
            progressListener.onProgress(UpgradeProgressEvent.EVENT,
                    UpgradeProgressEvent.data(stage, status, progressMessage, detail));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

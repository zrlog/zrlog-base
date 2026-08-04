package com.zrlog.common.updater.handle;

import com.hibegin.common.util.ZipUtil;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.common.updater.UpdateVersionHandler;
import com.zrlog.common.updater.UpgradeProgressEvent;
import com.zrlog.common.updater.UpgradeProgressListener;
import com.zrlog.common.Constants;
import com.zrlog.common.Updater;
import com.zrlog.common.vo.Version;

import java.io.File;
import java.io.Serializable;
import java.util.*;

/**
 * 更新ZrLog，具体的流程看 run() 里面有详细流程
 */
public class ZipUpdateVersionHandle implements Serializable, UpdateVersionHandler {

    private String message = "";
    private final File file;
    private boolean finish;
    private final Map<String, Object> backendRes;
    private final Version upgradeVersion;
    private final UpgradeProgressListener progressListener;
    private final Updater updater;

    public ZipUpdateVersionHandle(File file, Map<String, Object> backendRes, Version upgradeVersion) {
        this(file, backendRes, upgradeVersion, UpgradeProgressListener.NONE);
    }

    public ZipUpdateVersionHandle(File file, Map<String, Object> backendRes, Version upgradeVersion,
                                  UpgradeProgressListener progressListener) {
        this(file, backendRes, upgradeVersion, progressListener, null);
    }

    public ZipUpdateVersionHandle(File file, Map<String, Object> backendRes, Version upgradeVersion,
                                  UpgradeProgressListener progressListener, Updater updater) {
        this.file = file;
        this.backendRes = backendRes;
        this.upgradeVersion = upgradeVersion;
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

    /**
     * 修改文件的属性，让其可以快速执行
     */
    private void changeExecFile() {
        //非 unix，无法运行 chmod
        if (!Objects.equals(File.separator, "/")) {
            return;
        }
        List<String> execFiles = new ArrayList<>();
        execFiles.add(PathUtil.getRootPath() + "/bin/start.sh");
        execFiles.add(PathUtil.getRootPath() + "/bin/run.sh");
        execFiles.add(PathUtil.getRootPath() + "/bin/start.bat");
        for (String execFile : execFiles) {
            try {
                Process process = Runtime.getRuntime().exec(Arrays.asList("chmod", "a+x", execFile).toArray(new String[0]));
                process.waitFor();
            } catch (Exception e) {
                publishStatus(UpgradeProgressEvent.STAGE_ERROR, UpgradeProgressEvent.STATUS_ERROR, "chmod error",
                        execFile);
            }
        }
    }

    @Override
    public void doHandle() {
        Updater currentUpdater = Objects.requireNonNullElseGet(updater, () -> Constants.zrLogConfig.getUpdater());
        publishStatus(UpgradeProgressEvent.STAGE_UNZIP, UpgradeProgressEvent.STATUS_RUNNING,
                "upgrade.status.unzipping", file.getName());
        try {
            ZipUtil.unZip(file.toString(), currentUpdater.getUnzipPath());
        } catch (Exception e) {
            publishStatus(UpgradeProgressEvent.STAGE_ERROR, UpgradeProgressEvent.STATUS_ERROR, "upgrade.error.unzip",
                    e.getMessage());
            return;
        }
        try {
            changeExecFile();
            publishStatus(UpgradeProgressEvent.STAGE_UNZIP, UpgradeProgressEvent.STATUS_COMPLETE,
                    "upgrade.status.unzipped", null);
            publishStatus(UpgradeProgressEvent.STAGE_RESTART, UpgradeProgressEvent.STATUS_RUNNING,
                    "upgrade.status.restarting", null);
            if (finish) {
                return;
            }
            currentUpdater.restartProcessAsync(upgradeVersion);
            publishStatus(UpgradeProgressEvent.STAGE_RESTART, UpgradeProgressEvent.STATUS_COMPLETE,
                    "upgrade.status.restartSubmitted", null);
            publishStatus(UpgradeProgressEvent.STAGE_COMPLETE, UpgradeProgressEvent.STATUS_COMPLETE,
                    "upgrade.status.completed", null);
            finish = true;
        } catch (Exception e) {
            publishStatus(UpgradeProgressEvent.STAGE_ERROR, UpgradeProgressEvent.STATUS_ERROR,
                    "upgrade.error.generic", e.getMessage());
        } finally {
            file.delete();
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

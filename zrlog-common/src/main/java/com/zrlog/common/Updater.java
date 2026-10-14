package com.zrlog.common;

import com.hibegin.http.server.util.PathUtil;
import com.zrlog.common.vo.Version;

import java.io.File;

public interface Updater extends com.zrlog.updater.spi.ApplicationUpdater {

    void restartProcessAsync(Version upgradeVersion);

    default File getUpdateTempPath() {
        return PathUtil.getConfFile("/update-temp");
    }

    default String buildUpgradeFile(String upgradeFile, String upgradeKey) throws Exception {
        return null;
    }

    default String backup() throws Exception {
        return "";
    }

    String getUnzipPath();

    File execFile();

    UpdaterTypeEnum getType();
}

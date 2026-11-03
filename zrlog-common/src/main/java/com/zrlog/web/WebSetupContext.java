package com.zrlog.web;

import com.zrlog.common.Updater;
import com.zrlog.common.ZrLogConfig;
import java.io.File;

public class WebSetupContext {

    private final ZrLogConfig zrLogConfig;
    private final File dbPropertiesFile;
    private final File installLockFile;
    private final String contextPath;
    private final Updater updater;

    public WebSetupContext(
            ZrLogConfig zrLogConfig,
            File dbPropertiesFile,
            File installLockFile,
            String contextPath,
            Updater updater) {
        this.zrLogConfig = zrLogConfig;
        this.dbPropertiesFile = dbPropertiesFile;
        this.installLockFile = installLockFile;
        this.contextPath = contextPath;
        this.updater = updater;
    }

    public ZrLogConfig getZrLogConfig() {
        return zrLogConfig;
    }

    public File getDbPropertiesFile() {
        return dbPropertiesFile;
    }

    public File getInstallLockFile() {
        return installLockFile;
    }

    public String getContextPath() {
        return contextPath;
    }

    public Updater getUpdater() {
        return updater;
    }
}

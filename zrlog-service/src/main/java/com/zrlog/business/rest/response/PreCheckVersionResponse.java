package com.zrlog.business.rest.response;

public class PreCheckVersionResponse extends CheckVersionResponse {

    private Boolean onlineUpgradable;
    private String disableUpgradeReason;
    private Boolean dockerMode;
    private Boolean systemServiceMode;
    private Boolean faasMode;
    private Boolean warMode;
    private Boolean nativeImageMode;
    private BackupProtectionStatus backupProtection;

    public Boolean getOnlineUpgradable() {
        return onlineUpgradable;
    }

    public void setOnlineUpgradable(Boolean onlineUpgradable) {
        this.onlineUpgradable = onlineUpgradable;
    }

    public String getDisableUpgradeReason() {
        return disableUpgradeReason;
    }

    public void setDisableUpgradeReason(String disableUpgradeReason) {
        this.disableUpgradeReason = disableUpgradeReason;
    }

    public Boolean getDockerMode() {
        return dockerMode;
    }

    @Deprecated
    public void setDockerMode(Boolean dockerMode) {
        this.dockerMode = dockerMode;
    }

    public Boolean getSystemServiceMode() {
        return systemServiceMode;
    }

    @Deprecated
    public void setSystemServiceMode(Boolean systemServiceMode) {
        this.systemServiceMode = systemServiceMode;
    }

    public Boolean getFaasMode() {
        return faasMode;
    }

    public void setFaasMode(Boolean faasMode) {
        this.faasMode = faasMode;
    }

    public Boolean getWarMode() {
        return warMode;
    }

    public void setWarMode(Boolean warMode) {
        this.warMode = warMode;
    }

    public Boolean getNativeImageMode() {
        return nativeImageMode;
    }

    public void setNativeImageMode(Boolean nativeImageMode) {
        this.nativeImageMode = nativeImageMode;
    }

    public BackupProtectionStatus getBackupProtection() {
        return backupProtection;
    }

    public void setBackupProtection(BackupProtectionStatus backupProtection) {
        this.backupProtection = backupProtection;
    }
}

package com.zrlog.business.rest.base;

import com.zrlog.common.Validator;
import com.zrlog.common.exception.ArgsException;

import java.util.Objects;

public class UpgradeWebSiteInfo implements Validator {

    private Integer autoUpgradeVersion;
    private Boolean upgradePreview;
    private String upgradePackageSource;

    public String getUpgradePackageSource() { return upgradePackageSource; }
    public void setUpgradePackageSource(String value) { upgradePackageSource = value; }

    public Integer getAutoUpgradeVersion() {
        return autoUpgradeVersion;
    }

    public void setAutoUpgradeVersion(Integer autoUpgradeVersion) {
        this.autoUpgradeVersion = autoUpgradeVersion;
    }

    public Boolean getUpgradePreview() {
        return upgradePreview;
    }

    public void setUpgradePreview(Boolean upgradePreview) {
        this.upgradePreview = upgradePreview;
    }

    @Override
    public void doValid() {
        if (upgradePackageSource != null && !"dl".equals(upgradePackageSource) && !"github".equals(upgradePackageSource)) {
            throw new ArgsException("upgradePackageSource");
        }
        if (Objects.isNull(autoUpgradeVersion)) {
            throw new ArgsException("autoUpgradeVersion");
        }
    }
}

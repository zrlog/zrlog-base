package com.zrlog.business.dto;

import com.zrlog.common.vo.Version;

public class StoredUpgradeNotice {
    private Version version;
    private Long updatedAt;

    public Version getVersion() {
        return version;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }
}

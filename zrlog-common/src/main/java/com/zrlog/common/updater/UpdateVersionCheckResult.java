package com.zrlog.common.updater;

import com.zrlog.common.vo.Version;

public class UpdateVersionCheckResult {

    private final Version version;
    private final UpdateChannel channel;
    private final boolean upgradeAvailable;

    public UpdateVersionCheckResult(Version version, UpdateChannel channel, boolean upgradeAvailable) {
        this.version = version;
        this.channel = channel;
        this.upgradeAvailable = upgradeAvailable;
    }

    public Version getVersion() {
        return version;
    }

    public UpdateChannel getChannel() {
        return channel;
    }

    public boolean isUpgradeAvailable() {
        return upgradeAvailable;
    }
}

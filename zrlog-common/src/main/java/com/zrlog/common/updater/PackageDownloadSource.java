package com.zrlog.common.updater;

import com.zrlog.common.vo.Version;
import com.zrlog.updater.spi.UpdateCatalogs;

/** Compatibility entry point; package-source rules are maintained by zrlog-updater. */
public final class PackageDownloadSource {
    private PackageDownloadSource() { }

    public static String select(Version version, boolean war, String source) {
        return UpdateCatalogs.getInstance().selectPackageSource(version, war, source);
    }
}

package com.zrlog.common.updater;

import com.zrlog.common.UpdaterTypeEnum;
import java.io.File;

/** Compatibility entry point; execution is supplied by the installed SPI provider. */
public class ZipUpdater extends UpdaterHosts.DelegatingUpdater {
    public ZipUpdater(String[] args, File file) { this(UpdaterHosts.create(UpdaterTypeEnum.ZIP, args, file)); }
    private ZipUpdater(com.zrlog.common.Updater updater) { super(UpdaterHosts.context(updater), UpdaterHosts.unwrap(updater)); }
}

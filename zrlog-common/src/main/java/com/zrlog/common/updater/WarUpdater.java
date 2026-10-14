package com.zrlog.common.updater;

import com.zrlog.common.UpdaterTypeEnum;
import java.io.File;

public class WarUpdater extends UpdaterHosts.DelegatingUpdater {
    public WarUpdater(File file) { this(UpdaterHosts.create(UpdaterTypeEnum.WAR, new String[0], file)); }
    private WarUpdater(com.zrlog.common.Updater updater) { super(UpdaterHosts.context(updater), UpdaterHosts.unwrap(updater)); }
}

package com.zrlog.common.updater;

import com.zrlog.common.UpdaterTypeEnum;
import java.io.File;

public class NativeImageUpdater extends UpdaterHosts.DelegatingUpdater {
    public NativeImageUpdater(String[] args, File file) { this(UpdaterHosts.create(UpdaterTypeEnum.NATIVE_IMAGE, args, file)); }
    private NativeImageUpdater(com.zrlog.common.Updater updater) { super(UpdaterHosts.context(updater), UpdaterHosts.unwrap(updater)); }
}

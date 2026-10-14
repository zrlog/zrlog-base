package com.zrlog.common.updater.handle;
import com.zrlog.common.*;
import com.zrlog.common.updater.*;
import com.zrlog.common.vo.Version;
import java.io.File;
import java.util.Map;
public class ZipUpdateVersionHandle extends DelegatingUpdateHandler {
    public ZipUpdateVersionHandle(File file, Map<String,Object> text, Version version) { this(file, text, version, UpgradeProgressListener.NONE); }
    public ZipUpdateVersionHandle(File file, Map<String,Object> text, Version version, UpgradeProgressListener progress) {
        this(file, text, version, progress, Constants.zrLogConfig == null ? null : Constants.zrLogConfig.getUpdater());
    }
    public ZipUpdateVersionHandle(File file, Map<String,Object> text, Version version, UpgradeProgressListener progress, Updater updater) {
        super(UpdaterHosts.handler(updater == null ? UpdaterTypeEnum.ZIP : updater.getType(), updater, file, version, null, progress, text));
    }
}

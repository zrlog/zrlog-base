package com.zrlog.common.updater.handle;
import com.zrlog.common.*;
import com.zrlog.common.updater.*;
import com.zrlog.common.vo.Version;
import java.io.File;
import java.util.Map;
public class WarUpdateVersionHandle extends DelegatingUpdateHandler {
    public WarUpdateVersionHandle(File file, Map<String,Object> text, String key, Version version) { this(file, text, key, version, UpgradeProgressListener.NONE); }
    public WarUpdateVersionHandle(File file, Map<String,Object> text, String key, Version version, UpgradeProgressListener progress) {
        this(file, text, key, version, progress, Constants.zrLogConfig == null ? null : Constants.zrLogConfig.getUpdater());
    }
    public WarUpdateVersionHandle(File file, Map<String,Object> text, String key, Version version, UpgradeProgressListener progress, Updater updater) {
        super(UpdaterHosts.handler(UpdaterTypeEnum.WAR, updater, file, version, key, progress, text));
    }
}

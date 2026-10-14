package com.zrlog.common.updater.handle;
import com.hibegin.common.util.EnvKit;
import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.updater.*;
import com.zrlog.common.updater.aws.AwsLambdaUpdateConfig;
import com.zrlog.common.vo.Version;
import com.zrlog.updater.spi.*;
import java.io.File;
import java.util.Map;
public class FaasUpdateVersionHandler extends DelegatingUpdateHandler {
    public FaasUpdateVersionHandler(Map<String,Object> text, Version version) { this(text, version, null, UpgradeProgressListener.NONE); }
    public FaasUpdateVersionHandler(Map<String,Object> text, Version version, File file) { this(text, version, file, UpgradeProgressListener.NONE); }
    public FaasUpdateVersionHandler(Map<String,Object> text, Version version, File file, UpgradeProgressListener progress) {
        this(text, version, file, progress, AwsLambdaUpdateConfig.fromEnvironment());
    }
    public FaasUpdateVersionHandler(Map<String,Object> text, Version version, File file, UpgradeProgressListener progress, AwsLambdaUpdateConfig aws) {
        super(handler(text, version, file, progress, aws));
    }
    private static UpdateVersionHandler handler(Map<String,Object> text, Version version, File file, UpgradeProgressListener progress, AwsLambdaUpdateConfig aws) {
        UpdateContext.Builder b = UpdaterHosts.builder(new String[0], null); b.aws = aws;
        return UpdateProviders.get(UpdaterTypeEnum.FAAS).handler(b.build(), new UpdateRequest(file, version, null, null, progress, UpdaterHosts.text(text)));
    }
    public static boolean isOnlineUpgradeSupported() {
        return EnvKit.isLambda() && UpdateProviders.find(UpdaterTypeEnum.FAAS).isPresent() && AwsLambdaUpdateConfig.fromEnvironment().missingRequiredFields().isEmpty();
    }
}

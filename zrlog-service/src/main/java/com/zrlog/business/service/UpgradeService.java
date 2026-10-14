package com.zrlog.business.service;

import com.hibegin.common.util.*;
import com.zrlog.business.exception.DownloadUpgradeFileException;
import com.zrlog.business.exception.CheckUpgradeVersionException;
import com.zrlog.business.rest.response.CheckVersionResponse;
import com.zrlog.business.rest.response.PreCheckVersionResponse;
import com.zrlog.business.rest.response.UpgradeProcessResponse;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.updater.*;
import com.zrlog.common.adapter.UpdaterHosts;
import com.zrlog.common.Constants;
import com.zrlog.common.Updater;
import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.vo.Version;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ZrLogUtil;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import com.zrlog.updater.spi.*;

public class UpgradeService {

    private final UpgradeNoticeService upgradeNoticeService = new UpgradeNoticeService();
    private final BackupProtectionService backupProtectionService;

    public UpgradeService() {
        this(new BackupProtectionService());
    }

    UpgradeService(BackupProtectionService backupProtectionService) {
        this.backupProtectionService = backupProtectionService;
    }

    public CheckVersionResponse getCheckVersionResponse(boolean fetchAble, UpdateVersionInfoPlugin plugin) {
        if (Objects.isNull(plugin)) {
            return upgradeNoticeService.getNotice();
        }
        if (fetchAble) {
            plugin.getLastVersion(true);
            if (plugin.isLastCheckFailed()) {
                throw new CheckUpgradeVersionException();
            }
            CheckVersionResponse storedResponse = upgradeNoticeService.getNotice();
            if (Objects.nonNull(storedResponse.getVersion())) {
                return storedResponse;
            }
        }
        CheckVersionResponse storedResponse = upgradeNoticeService.getNotice();
        if (Objects.nonNull(storedResponse.getVersion())) {
            return storedResponse;
        }
        CheckVersionResponse checkVersionResponse = new CheckVersionResponse();
        Version version = plugin.getLastVersion(false);
        if (Objects.isNull(version)) {
            checkVersionResponse.setUpgrade(false);
            return checkVersionResponse;
        }
        checkVersionResponse.setUpgrade(ZrLogUtil.greatThenCurrentVersion(version.getBuildId(), version.getBuildDate(), version.getVersion()));
        checkVersionResponse.setVersion(version);
        return checkVersionResponse;
    }

    public PreCheckVersionResponse getPreCheckVersionResponse(boolean fetchAble, UpdateVersionInfoPlugin plugin) {
        CheckVersionResponse checkVersionResponse = getCheckVersionResponse(fetchAble, plugin);
        return BeanUtil.convert(checkVersionResponse, PreCheckVersionResponse.class);

    }

    public PreCheckVersionResponse preUpgradeVersion(boolean fetchAble, UpdateVersionInfoPlugin plugin) {
        PreCheckVersionResponse checkVersionResponse = getPreCheckVersionResponse(fetchAble, plugin);
        checkVersionResponse.setDockerMode(isDockerMode());
        checkVersionResponse.setSystemServiceMode(isSystemServiceMode());
        checkVersionResponse.setFaasMode(isFaaSMode());
        checkVersionResponse.setWarMode(isWarMode());
        checkVersionResponse.setNativeImageMode(EnvKit.isNativeImage());
        boolean disable = isOnlineUpgradeDisabled();
        checkVersionResponse.setOnlineUpgradable(!disable);
        Updater currentUpdater = Constants.zrLogConfig == null ? null : Constants.zrLogConfig.getUpdater();
        if (currentUpdater != null && UpdaterHosts.unwrap(currentUpdater) instanceof SystemPackageUpdater) {
            try { checkVersionResponse.setSystemPackageUpgrade(((SystemPackageUpdater) UpdaterHosts.unwrap(currentUpdater)).status()); }
            catch (Exception e) { throw new IllegalStateException("Unable to read system package upgrade status", e); }
        }
        if (isDockerMode() && UpdaterHosts.dockerConfigured()) {
            try {
                UpdaterHosts.dockerControl().capabilities();
                checkVersionResponse.setOnlineUpgradable(true);
                checkVersionResponse.setDockerUpdaterAvailable(true);
            } catch (RuntimeException error) {
                checkVersionResponse.setDisableUpgradeReason(backendString(I18nUtil.getBackend(), "upgrade.docker.updaterUnavailable"));
            }
        }
        checkVersionResponse.setBackupProtection(backupProtectionService.getStatus());
        if (!Boolean.TRUE.equals(checkVersionResponse.getOnlineUpgradable())
                && checkVersionResponse.getDisableUpgradeReason() == null
                && Objects.equals(checkVersionResponse.getUpgrade(), true)) {
            UpgradeProcessResponse upgradeProcessResponse = buildManualUpgradeResponse(checkVersionResponse.getVersion(),
                    I18nUtil.getBackend());
            checkVersionResponse.setDisableUpgradeReason(upgradeProcessResponse.getMessage());
        }
        return checkVersionResponse;
    }

    private File downloadUpgradePackage(Version version, UpgradeProgressListener progressListener, Map<String,Object> backend, Updater updater) {
        try {
            UpdateContext.Builder b = new UpdateContext.Builder(UpdaterHosts.context(updater));
            b.packageSource = new WebsiteKvService().upgradeWebSiteInfo().getUpgradePackageSource();
            UpdaterTypeEnum type = isFaaSMode() ? UpdaterTypeEnum.FAAS : updater == null ? UpdaterTypeEnum.ZIP : updater.getType();
            return UpdateProviders.get(type).download(b.build(), new UpdateRequest(null, version, buildUpgradeKey(version), UpdaterHosts.unwrap(updater), progressListener, UpdaterHosts.text(backend)));
        } catch (Exception e) { throw new DownloadUpgradeFileException(e); }
    }

    public UpgradeProcessResponse doUpgrade(UpdateVersionInfoPlugin plugin) {
        return doUpgrade(plugin, UpgradeProgressListener.NONE);
    }

    public UpgradeProcessResponse doUpgrade(UpdateVersionInfoPlugin plugin, UpgradeProgressListener progressListener) {
        return doUpgrade(plugin, progressListener, I18nUtil.getBackend());
    }

    public UpgradeProcessResponse doUpgrade(UpdateVersionInfoPlugin plugin, UpgradeProgressListener progressListener,
                                            Map<String, Object> backend) {
        return doUpgrade(plugin, progressListener, backend, false);
    }

    public UpgradeProcessResponse doUpgrade(UpdateVersionInfoPlugin plugin, UpgradeProgressListener progressListener,
                                            Map<String, Object> backend, boolean backupRiskAccepted) {
        CheckVersionResponse checkVersionResponse = getCheckVersionResponse(true, plugin);
        Version version = checkVersionResponse.getVersion();
        if (Objects.isNull(version) || !Objects.equals(checkVersionResponse.getUpgrade(), true)) {
            return new UpgradeProcessResponse(true, backendString(backend, "upgrade.result.noChange"));
        }
        Updater updater = Objects.nonNull(Constants.zrLogConfig) ? Constants.zrLogConfig.getUpdater() : null;
        if (isOnlineUpgradeDisabled()) {
            return buildManualUpgradeResponse(version, backend);
        }
        File upgradePackage = downloadUpgradePackage(version, progressListener, backend, updater);
        UpdateVersionHandler updateVersionHandler = newOnlineUpdateVersionHandler(version, upgradePackage,
                progressListener, backend);
        updateVersionHandler.doHandle();
        return new UpgradeProcessResponse(updateVersionHandler.isFinish(), updateVersionHandler.getMessage());
    }

    public UpgradeProcessResponse doUpgrade(Version version, Updater updater,
                                            UpgradeProgressListener progressListener, Map<String, Object> backend) {
        if (Objects.isNull(version) || !ZrLogUtil.greatThenCurrentVersion(version.getBuildId(),
                version.getBuildDate(), version.getVersion())) {
            return new UpgradeProcessResponse(true, backendString(backend, "upgrade.result.noChange"));
        }
        if (isOnlineUpgradeDisabled()) {
            return buildManualUpgradeResponse(version, backend);
        }
        if (Objects.isNull(updater)) {
            return new UpgradeProcessResponse(false, backendString(backend, "upgrade.error.unsupportedRuntime"));
        }
        File upgradePackage = downloadUpgradePackage(version, progressListener, backend, updater);
        UpdateVersionHandler updateVersionHandler = newOnlineUpdateVersionHandler(version, upgradePackage,
                progressListener, backend, updater);
        updateVersionHandler.doHandle();
        return new UpgradeProcessResponse(updateVersionHandler.isFinish(), updateVersionHandler.getMessage());
    }

    UpdateVersionHandler newOnlineUpdateVersionHandler(Version version, File upgradePackage,
                                                       UpgradeProgressListener progressListener,
                                                       Map<String, Object> backend) {
        Updater updater = Objects.nonNull(Constants.zrLogConfig) ? Constants.zrLogConfig.getUpdater() : null;
        return newOnlineUpdateVersionHandler(version, upgradePackage, progressListener, backend, updater);
    }

    UpdateVersionHandler newOnlineUpdateVersionHandler(Version version, File upgradePackage,
                                                       UpgradeProgressListener progressListener,
                                                       Map<String, Object> backend, Updater updater) {
        UpdaterTypeEnum type = updater != null ? updater.getType()
                : isFaaSMode() ? UpdaterTypeEnum.FAAS : isWarMode() ? UpdaterTypeEnum.WAR : UpdaterTypeEnum.ZIP;
        File file = type == UpdaterTypeEnum.DEB ? null : upgradePackage;
        return UpdaterHosts.handler(type, updater, file, version, buildUpgradeKey(version), progressListener, backend);
    }

    private boolean isOnlineUpgradeDisabled() {
        return isDockerMode() || (isSystemServiceMode() && !isDebOnlineUpgradeSupported()) ||
                (isFaaSMode() && !isFaaSOnlineUpgradeSupported());
    }

    private UpgradeProcessResponse buildManualUpgradeResponse(Version version, Map<String, Object> backend) {
        UpdateVersionHandler updateVersionHandler;
        if (isDockerMode()) {
            updateVersionHandler = UpdaterHosts.manualHandler(UpdaterTypeEnum.DOCKER, null, version, backend);
        } else if (isSystemServiceMode()) {
            updateVersionHandler = UpdaterHosts.manualHandler(UpdaterTypeEnum.DEB, null, version, backend);
        } else if (isFaaSMode()) {
            updateVersionHandler = UpdaterHosts.manualHandler(UpdaterTypeEnum.FAAS, null, version, backend);
        } else {
            return new UpgradeProcessResponse(false, "");
        }
        updateVersionHandler.doHandle();
        return new UpgradeProcessResponse(updateVersionHandler.isFinish(), updateVersionHandler.getMessage());
    }

    boolean isDebOnlineUpgradeSupported() {
        Updater updater = Constants.zrLogConfig == null ? null : Constants.zrLogConfig.getUpdater();
        return updater != null && UpdaterHosts.unwrap(updater) instanceof SystemPackageUpdater && ((SystemPackageUpdater) UpdaterHosts.unwrap(updater)).available();
    }

    boolean isDockerMode() {
        return ZrLogUtil.isDockerMode();
    }

    boolean isSystemServiceMode() {
        return ZrLogUtil.isSystemServiceMode();
    }

    boolean isWarMode() {
        return ZrLogUtil.isWarMode();
    }

    boolean isWarMode(Updater updater) {
        return Objects.nonNull(updater) ? updater.getType() == UpdaterTypeEnum.WAR : isWarMode();
    }

    boolean isFaaSMode() {
        return EnvKit.isFaaSMode();
    }

    boolean isFaaSOnlineUpgradeSupported() {
        return UpdaterHosts.onlineUpgradeSupported(UpdaterTypeEnum.FAAS);
    }

    static String buildUpgradeKey(Version version) {
        String versionTag = Objects.nonNull(version) ? version.getBuildId() : "";
        if (StringUtils.isEmpty(versionTag) && Objects.nonNull(version)) {
            versionTag = version.getVersion();
        }
        if (StringUtils.isEmpty(versionTag)) {
            versionTag = String.valueOf(System.currentTimeMillis());
        }
        return ("upgrade-" + versionTag + "-" + System.currentTimeMillis()).replaceAll("[^A-Za-z0-9._-]", "-");
    }

    static String backendString(Map<String, Object> backend, String key) {
        return Objects.toString(backend.get(key), key);
    }

}

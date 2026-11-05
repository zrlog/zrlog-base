package com.zrlog.business.service;

import com.hibegin.common.util.BeanUtil;
import com.hibegin.common.util.EnvKit;
import com.zrlog.business.exception.CheckUpgradeVersionException;
import com.zrlog.business.rest.response.CheckVersionResponse;
import com.zrlog.business.rest.response.PreCheckVersionResponse;
import com.zrlog.business.rest.response.UpgradeProcessResponse;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.Updater;
import com.zrlog.common.UpdaterTypeEnum;
import com.zrlog.common.adapter.UpdaterHosts;
import com.zrlog.common.updater.UpdateVersionHandler;
import com.zrlog.common.updater.UpgradeProgressListener;
import com.zrlog.common.vo.Version;
import com.zrlog.updater.spi.UpdateAvailability;
import com.zrlog.updater.spi.UpdateCommand;
import com.zrlog.updater.spi.UpdateOperations;
import com.zrlog.updater.spi.UpdateTask;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ZrLogUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

@Singleton
public class UpgradeService {

    private final UpgradeNoticeService upgradeNoticeService;
    private final Updater updater;
    private final WebsiteKvService websiteKv;
    private final BackupProtectionService backupProtectionService;

    @Inject
    public UpgradeService(
            Supplier<Updater> updater,
            UpgradeNoticeService upgradeNoticeService,
            BackupProtectionService backupProtectionService,
            WebsiteKvService websiteKv) {
        this.websiteKv = Objects.requireNonNull(websiteKv);
        this.updater = updater.get();
        this.upgradeNoticeService = Objects.requireNonNull(upgradeNoticeService);
        this.backupProtectionService = backupProtectionService;
    }

    static String backendString(Map<String, Object> backend, String key) {
        return Objects.toString(backend.get(key), key);
    }

    public CheckVersionResponse getCheckVersionResponse(
            boolean fetchAble, UpdateVersionInfoPlugin plugin) {
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
        checkVersionResponse.setUpgrade(
                ZrLogUtil.greatThenCurrentVersion(
                        version.getBuildId(), version.getBuildDate(), version.getVersion()));
        checkVersionResponse.setVersion(version);
        return checkVersionResponse;
    }

    public PreCheckVersionResponse getPreCheckVersionResponse(
            boolean fetchAble, UpdateVersionInfoPlugin plugin) {
        CheckVersionResponse checkVersionResponse = getCheckVersionResponse(fetchAble, plugin);
        return BeanUtil.convert(checkVersionResponse, PreCheckVersionResponse.class);
    }

    public PreCheckVersionResponse preUpgradeVersion(
            boolean fetchAble, UpdateVersionInfoPlugin plugin) {
        PreCheckVersionResponse checkVersionResponse =
                getPreCheckVersionResponse(fetchAble, plugin);
        checkVersionResponse.setDockerMode(isDockerMode());
        checkVersionResponse.setSystemServiceMode(isSystemServiceMode());
        checkVersionResponse.setFaasMode(isFaaSMode());
        checkVersionResponse.setWarMode(isWarMode());
        checkVersionResponse.setNativeImageMode(EnvKit.isNativeImage());
        UpdateOperations operations = operations();
        UpdateAvailability availability =
                operations.availability(UpdaterHosts.text(I18nUtil.getBackend()));
        checkVersionResponse.setOnlineUpgradable(availability.online);
        checkVersionResponse.setDisableUpgradeReason(availability.reason);
        checkVersionResponse.setTaskMessages(
                operations.messages(UpdaterHosts.text(I18nUtil.getBackend())));
        checkVersionResponse.setBackupProtection(backupProtectionService.getStatus());
        if (!Boolean.TRUE.equals(checkVersionResponse.getOnlineUpgradable())
                && checkVersionResponse.getDisableUpgradeReason() == null
                && Objects.equals(checkVersionResponse.getUpgrade(), true)) {
            UpgradeProcessResponse upgradeProcessResponse =
                    buildManualUpgradeResponse(
                            checkVersionResponse.getVersion(), I18nUtil.getBackend());
            checkVersionResponse.setDisableUpgradeReason(upgradeProcessResponse.getMessage());
        }
        return checkVersionResponse;
    }

    public UpgradeProcessResponse doUpgrade(UpdateVersionInfoPlugin plugin) {
        return doUpgrade(plugin, UpgradeProgressListener.NONE);
    }

    public UpgradeProcessResponse doUpgrade(
            UpdateVersionInfoPlugin plugin, UpgradeProgressListener progressListener) {
        return doUpgrade(plugin, progressListener, I18nUtil.getBackend());
    }

    public UpgradeProcessResponse doUpgrade(
            UpdateVersionInfoPlugin plugin,
            UpgradeProgressListener progressListener,
            Map<String, Object> backend) {
        return doUpgrade(plugin, progressListener, backend, false);
    }

    public UpgradeProcessResponse doUpgrade(
            UpdateVersionInfoPlugin plugin,
            UpgradeProgressListener progressListener,
            Map<String, Object> backend,
            boolean backupRiskAccepted) {
        CheckVersionResponse checkVersionResponse = getCheckVersionResponse(true, plugin);
        Version version = checkVersionResponse.getVersion();
        if (Objects.isNull(version) || !Objects.equals(checkVersionResponse.getUpgrade(), true)) {
            return new UpgradeProcessResponse(
                    true, backendString(backend, "upgrade.result.noChange"));
        }
        return doUpgradeVersion(version, progressListener, backend);
    }

    /** Execute the exact version already checked and claimed by the caller. */
    public UpgradeProcessResponse doUpgradeVersion(
            Version version,
            UpgradeProgressListener progressListener,
            Map<String, Object> backend) {
        if (Objects.isNull(version)
                || !ZrLogUtil.greatThenCurrentVersion(
                        version.getBuildId(), version.getBuildDate(), version.getVersion())) {
            return new UpgradeProcessResponse(
                    true, backendString(backend, "upgrade.result.noChange"));
        }
        if (isOnlineUpgradeDisabled()) {
            return buildManualUpgradeResponse(version, backend);
        }
        return executeVersion(version, updater, progressListener, backend);
    }

    public UpgradeProcessResponse doUpgrade(
            Version version,
            Updater updater,
            UpgradeProgressListener progressListener,
            Map<String, Object> backend) {
        if (Objects.isNull(version)
                || !ZrLogUtil.greatThenCurrentVersion(
                        version.getBuildId(), version.getBuildDate(), version.getVersion())) {
            return new UpgradeProcessResponse(
                    true, backendString(backend, "upgrade.result.noChange"));
        }
        if (updater == null) {
            return new UpgradeProcessResponse(
                    false, backendString(backend, "upgrade.error.unsupportedRuntime"));
        }
        if (!operations(updater).availability(UpdaterHosts.text(backend)).online) {
            return buildManualUpgradeResponse(version, updater, updater.getType(), backend);
        }
        return executeVersion(version, updater, progressListener, backend);
    }

    private UpgradeProcessResponse executeVersion(
            Version version,
            Updater selected,
            UpgradeProgressListener listener,
            Map<String, Object> backend) {
        UpdateCommand command = new UpdateCommand();
        command.requestId = java.util.UUID.randomUUID().toString();
        command.version =
                version.getArtifactVersion() == null
                        ? version.getVersion()
                        : version.getArtifactVersion();
        command.buildId = version.getBuildId();
        command.channel =
                version.getChannel() == null
                        ? (version.getVersion().contains("SNAPSHOT") ? "preview" : "release")
                        : version.getChannel();
        try {
            UpdateOperations operations = operations(selected);
            UpdateTask task = operations.submit(command, listener, UpdaterHosts.text(backend));
            if (task != null && !task.terminal() && !task.awaitingRestart)
                task = operations.watch(command.requestId, listener, UpdaterHosts.text(backend));
            return new UpgradeProcessResponse(
                    task != null && !task.failed(), task == null ? "" : task.message);
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException(error.getMessage(), error);
        }
    }

    private UpdateOperations operations(Updater selected) {
        return selected == this.updater
                ? operations()
                : UpdaterHosts.operations(
                        selected.getType(),
                        selected,
                        websiteKv.upgradeWebSiteInfo().getUpgradePackageSource());
    }

    public UpdateOperations operations() {
        return UpdaterHosts.operations(
                updateType(), updater, websiteKv.upgradeWebSiteInfo().getUpgradePackageSource());
    }

    private UpdaterTypeEnum updateType() {
        if (updater != null) return updater.getType();
        if (isDockerMode()) return UpdaterTypeEnum.DOCKER;
        if (isSystemServiceMode()) return UpdaterTypeEnum.DEB;
        if (isFaaSMode()) return UpdaterTypeEnum.FAAS;
        return isWarMode() ? UpdaterTypeEnum.WAR : UpdaterTypeEnum.ZIP;
    }

    public UpdateTask submitUpgrade(
            UpdateCommand command, UpgradeProgressListener listener, Map<String, Object> backend)
            throws Exception {
        return operations().submit(command, listener, UpdaterHosts.text(backend));
    }

    public UpdateTask upgradeStatus(String requestId, Map<String, Object> backend)
            throws Exception {
        return operations().status(requestId, UpdaterHosts.text(backend));
    }

    public UpdateTask watchUpgrade(
            String requestId, UpgradeProgressListener listener, Map<String, Object> backend)
            throws Exception {
        return operations().watch(requestId, listener, UpdaterHosts.text(backend));
    }

    private boolean isOnlineUpgradeDisabled() {
        return !operations().availability(UpdaterHosts.text(I18nUtil.getBackend())).online;
    }

    private UpgradeProcessResponse buildManualUpgradeResponse(
            Version version, Map<String, Object> backend) {
        return buildManualUpgradeResponse(version, updater, updateType(), backend);
    }

    private UpgradeProcessResponse buildManualUpgradeResponse(
            Version version, Updater selected, UpdaterTypeEnum type, Map<String, Object> backend) {
        UpdateVersionHandler handler = UpdaterHosts.manualHandler(type, selected, version, backend);
        handler.doHandle();
        return new UpgradeProcessResponse(handler.isFinish(), handler.getMessage());
    }

    boolean isDockerMode() {
        return ZrLogUtil.isDockerMode();
    }

    boolean isSystemServiceMode() {
        return ZrLogUtil.isSystemServiceMode();
    }

    boolean isWarMode() {
        return ZrLogUtil.isWarMode(updater);
    }

    boolean isWarMode(Updater updater) {
        return Objects.nonNull(updater) ? updater.getType() == UpdaterTypeEnum.WAR : isWarMode();
    }

    boolean isFaaSMode() {
        return EnvKit.isFaaSMode();
    }

    boolean isFaaSOnlineUpgradeSupported() {
        return UpdaterHosts.onlineUpgradeSupported(UpdaterTypeEnum.FAAS, updater);
    }
}

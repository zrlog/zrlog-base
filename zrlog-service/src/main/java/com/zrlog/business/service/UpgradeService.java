package com.zrlog.business.service;

import com.hibegin.common.util.*;
import com.hibegin.common.util.http.HttpUtil;
import com.hibegin.common.util.http.handle.HttpFileHandle;
import com.hibegin.http.server.util.PathUtil;
import com.zrlog.business.exception.DownloadUpgradeFileException;
import com.zrlog.business.rest.response.CheckVersionResponse;
import com.zrlog.business.rest.response.PreCheckVersionResponse;
import com.zrlog.business.rest.response.UpgradeProcessResponse;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.updater.*;
import com.zrlog.common.updater.handle.*;
import com.zrlog.common.Constants;
import com.zrlog.common.vo.Version;
import com.zrlog.util.I18nUtil;
import com.zrlog.util.ThreadUtils;
import com.zrlog.util.ZrLogUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

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
        boolean disable = isOnlineUpgradeDisabled();
        checkVersionResponse.setOnlineUpgradable(!disable);
        if (!disable) {
            checkVersionResponse.setBackupProtection(backupProtectionService.getStatus());
        }
        if (disable && Objects.equals(checkVersionResponse.getUpgrade(), true)) {
            UpgradeProcessResponse upgradeProcessResponse = buildManualUpgradeResponse(checkVersionResponse.getVersion(),
                    I18nUtil.getBackend());
            checkVersionResponse.setDisableUpgradeReason(upgradeProcessResponse.getMessage());
        }
        return checkVersionResponse;
    }

    static boolean isErrorFile(File file, long length, String md5sum) {
        return isErrorFile(file, length, null, md5sum);
    }

    static boolean isErrorFile(File file, long length, String sha256, String md5sum) {
        try {
            if (Objects.isNull(file) || !file.exists()) {
                return true;
            }
            //先比较文件大小
            if (length > 0 && length != file.length()) {
                return true;
            }
            if (StringUtils.isNotEmpty(sha256)) {
                return !sha256ByFile(file).equalsIgnoreCase(sha256);
            }
            if (StringUtils.isNotEmpty(md5sum)) {
                return !SecurityUtils.md5ByFile(file).equals(md5sum);
            }
            return file.length() <= 0;
        } catch (Exception e) {
            return true;
        }
    }

    static String sha256ByFile(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (FileInputStream inputStream = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int length;
            while ((length = inputStream.read(buffer)) >= 0) {
                digest.update(buffer, 0, length);
            }
        }
        StringBuilder hex = new StringBuilder();
        for (byte value : digest.digest()) {
            hex.append(String.format("%02x", value & 0xff));
        }
        return hex.toString();
    }

    static int getDownloadProcess(File file, long totalLength) {
        if (totalLength <= 0 || Objects.isNull(file) || !file.exists()) {
            return 0;
        }
        return (int) Math.min(file.length() * 100 / totalLength, 100);
    }

    private HttpFileHandle createFileHandle() {
        File updateTempPath = Objects.nonNull(Constants.zrLogConfig) && Objects.nonNull(Constants.zrLogConfig.getUpdater()) ?
                Constants.zrLogConfig.getUpdater().getUpdateTempPath() : new File(PathUtil.getTempPath());
        File file = new File(updateTempPath, "zrlog-" + System.currentTimeMillis() + "-" +
                Math.abs(System.nanoTime()) + ".zip");
        file.getParentFile().mkdirs();
        HttpFileHandle handle = new HttpFileHandle(file.getParentFile().toString(), file.getName());
        handle.setT(file);
        return handle;
    }

    private String getPackageDownloadUrl(Version version) {
        return isWarMode() ? version.getWarDownloadUrl() : version.getZipDownloadUrl();
    }

    private long getPackageFileSize(Version version) {
        return isWarMode() ? version.getWarFileSize() : version.getZipFileSize();
    }

    private String getPackageMd5sum(Version version) {
        return isWarMode() ? version.getWarMd5sum() : version.getZipMd5sum();
    }

    private String getPackageSha256(Version version) {
        return isWarMode() ? version.getWarSha256() : version.getZipSha256();
    }

    private File downloadUpgradePackage(Version version, UpgradeProgressListener progressListener,
                                        Map<String, Object> backend) {
        HttpFileHandle fileHandle = createFileHandle();
        File file = fileHandle.getT();
        String downloadUrl = getPackageDownloadUrl(version);
        long fileSize = getPackageFileSize(version);
        String sha256 = getPackageSha256(version);
        String md5sum = getPackageMd5sum(version);
        publishProgress(progressListener, UpgradeProgressEvent.STAGE_DOWNLOAD, UpgradeProgressEvent.STATUS_RUNNING,
                getDownloadMessage(backend, null), null);
        AtomicReference<Exception> downloadException = new AtomicReference<>();
        Thread downloadThread = ThreadUtils.start(() -> {
            try {
                HttpUtil.getInstance().sendGetRequest(downloadUrl, fileHandle, new HashMap<>());
            } catch (IOException | InterruptedException | URISyntaxException e) {
                downloadException.set(e);
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                LoggerUtil.getLogger(UpgradeService.class).severe("Download file error " + e.getMessage());
            }
        });
        int lastPercent = 0;
        while (downloadThread.isAlive()) {
            int percent = getDownloadProcess(file, fileSize);
            if (percent > lastPercent) {
                publishProgress(progressListener, UpgradeProgressEvent.STAGE_DOWNLOAD,
                        UpgradeProgressEvent.STATUS_RUNNING,
                        getDownloadMessage(backend, percent), null);
                lastPercent = percent;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                downloadThread.interrupt();
                Thread.currentThread().interrupt();
                throw new DownloadUpgradeFileException(e);
            }
        }
        if (Objects.nonNull(downloadException.get()) || fileHandle.getStatusCode() != 200 ||
                isErrorFile(file, fileSize, sha256, md5sum)) {
            throw new DownloadUpgradeFileException(downloadException.get());
        }
        publishProgress(progressListener, UpgradeProgressEvent.STAGE_DOWNLOAD, UpgradeProgressEvent.STATUS_COMPLETE,
                backendString(backend, "upgrade.status.downloaded"), file.getName());
        return file;
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
        if (isOnlineUpgradeDisabled()) {
            return buildManualUpgradeResponse(version, backend);
        }
        File upgradePackage = downloadUpgradePackage(version, progressListener, backend);
        UpdateVersionHandler updateVersionHandler = newOnlineUpdateVersionHandler(version, upgradePackage,
                progressListener, backend);
        updateVersionHandler.doHandle();
        return new UpgradeProcessResponse(updateVersionHandler.isFinish(), updateVersionHandler.getMessage());
    }

    UpdateVersionHandler newOnlineUpdateVersionHandler(Version version, File upgradePackage,
                                                       UpgradeProgressListener progressListener,
                                                       Map<String, Object> backend) {
        if (isFaaSMode()) {
            return new FaasUpdateVersionHandler(backend, version, upgradePackage,
                    progressListener);
        }
        if (isWarMode()) {
            return new WarUpdateVersionHandle(upgradePackage, backend,
                    buildUpgradeKey(version), version, progressListener);
        }
        return new ZipUpdateVersionHandle(upgradePackage, backend, version,
                progressListener);
    }

    private boolean isOnlineUpgradeDisabled() {
        return isDockerMode() || isSystemServiceMode() ||
                (isFaaSMode() && !isFaaSOnlineUpgradeSupported());
    }

    private UpgradeProcessResponse buildManualUpgradeResponse(Version version, Map<String, Object> backend) {
        UpdateVersionHandler updateVersionHandler;
        if (isDockerMode()) {
            updateVersionHandler = new DockerUpdateVersionHandle(backend);
        } else if (isSystemServiceMode()) {
            updateVersionHandler = new SystemServiceUpdateVersionHandle(backend, version);
        } else if (isFaaSMode()) {
            updateVersionHandler = new FaasUpdateVersionHandler(backend, version);
        } else {
            return new UpgradeProcessResponse(false, "");
        }
        updateVersionHandler.doHandle();
        return new UpgradeProcessResponse(updateVersionHandler.isFinish(), updateVersionHandler.getMessage());
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

    boolean isFaaSMode() {
        return EnvKit.isFaaSMode();
    }

    boolean isFaaSOnlineUpgradeSupported() {
        return FaasUpdateVersionHandler.isOnlineUpgradeSupported();
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

    static String getDownloadMessage(Map<String, Object> backend, Integer percent) {
        String message = backendString(backend, "upgrade.status.downloading");
        if (Objects.nonNull(percent) && percent > 0) {
            return message + " " + percent + "%";
        }
        return message;
    }

    static void publishProgress(UpgradeProgressListener progressListener, String stage, String status,
                                String message, String detail) {
        try {
            progressListener.onProgress(UpgradeProgressEvent.EVENT,
                    UpgradeProgressEvent.data(stage, status, message, detail));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

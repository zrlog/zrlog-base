package com.zrlog.business.service;

import com.google.gson.Gson;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.zrlog.business.dto.StoredUpgradeNotice;
import com.zrlog.business.rest.response.CheckVersionResponse;
import com.zrlog.business.updater.UpdateVersionInfoPlugin;
import com.zrlog.common.vo.Version;
import com.zrlog.util.ZrLogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.logging.Logger;

@Singleton
public class UpgradeNoticeService {

    private static final Logger LOGGER = LoggerUtil.getLogger(UpgradeNoticeService.class);
    private static final String UPGRADE_NOTICE_KEY = "admin_message_center_upgrade_notice";

    private final BooleanSupplier installed;

    public UpgradeNoticeService(BooleanSupplier installed) {
        this(installed, new WebsiteKvService());
    }

    @Inject
    public UpgradeNoticeService(BooleanSupplier installed, WebsiteKvService kvService) {
        this.installed = Objects.requireNonNull(installed);
        this.kvService = Objects.requireNonNull(kvService);
    }

    private final Gson gson = new Gson();
    private final WebsiteKvService kvService;

    public void sync(Version version) {
        if (!installed.getAsBoolean()) {
            return;
        }
        try {
            CheckVersionResponse response = buildResponse(version);
            if (!Boolean.TRUE.equals(response.getUpgrade()) || Objects.isNull(version)) {
                clearStoredUpgradeNotice();
                return;
            }
            StoredUpgradeNotice storedUpgradeNotice = new StoredUpgradeNotice();
            storedUpgradeNotice.setVersion(version);
            storedUpgradeNotice.setUpdatedAt(version.getBuildDate().getTime());
            kvService.putString(UPGRADE_NOTICE_KEY, gson.toJson(storedUpgradeNotice));
        } catch (Exception e) {
            LOGGER.warning("Sync upgrade notice state error " + e.getMessage());
        }
    }

    public CheckVersionResponse getNotice() {
        try {
            StoredUpgradeNotice state = getStoredUpgradeNotice();
            if (Objects.isNull(state) || Objects.isNull(state.getVersion())) {
                return buildResponse(null);
            }
            return buildResponse(state.getVersion());
        } catch (Exception e) {
            LOGGER.warning("Read upgrade notice state error " + e.getMessage());
            return buildResponse(null);
        }
    }

    public CheckVersionResponse buildResponse(Version version) {
        CheckVersionResponse checkVersionResponse = new CheckVersionResponse();
        if (Objects.isNull(version) || Objects.isNull(version.getBuildDate())) {
            checkVersionResponse.setUpgrade(false);
            return checkVersionResponse;
        }
        checkVersionResponse.setUpgrade(ZrLogUtil.greatThenCurrentVersion(
                version.getBuildId(),
                version.getBuildDate(),
                version.getVersion()
        ));
        checkVersionResponse.setVersion(UpdateVersionInfoPlugin.normalizeVersionForDisplay(version));
        return checkVersionResponse;
    }

    public StoredUpgradeNotice getStoredUpgradeNotice() {
        String json = kvService.getString(UPGRADE_NOTICE_KEY);
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        return gson.fromJson(json, StoredUpgradeNotice.class);
    }

    public void clearStoredUpgradeNotice() {
        try {
            kvService.remove(UPGRADE_NOTICE_KEY);
        } catch (Exception e) {
            LOGGER.warning("Clear upgrade notice state error " + e.getMessage());
        }
    }
}

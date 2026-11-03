package com.zrlog.business.service;

import com.hibegin.common.dao.DataSourceWrapper;
import com.hibegin.common.dao.ResultBeanUtils;
import com.hibegin.common.util.LoggerUtil;
import com.zrlog.business.rest.base.UpgradeWebSiteInfo;
import com.zrlog.business.updater.AutoUpgradeVersionType;
import com.zrlog.data.repository.WebSiteRepository;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
public class WebsiteKvService {

    public static final String AUTO_UPGRADE_VERSION_KEY = "autoUpgradeVersion";
    public static final String UPGRADE_PREVIEW_KEY = "upgradePreview";
    private static final Logger LOGGER = LoggerUtil.getLogger(WebsiteKvService.class);
    private final Provider<WebSiteRepository> websites;

    @Inject
    public WebsiteKvService(Provider<WebSiteRepository> websites) {
        this.websites = Objects.requireNonNull(websites, "websites");
    }

    /**
     * Keep all operations on this application's database, including after another app starts.
     */
    public WebsiteKvService(DataSourceWrapper dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        this.websites = () -> new WebSiteRepository(dataSource);
    }

    public String getString(String key) {
        try {
            return websites.get().getStringValueByName(key);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Read website KV failed, key=" + key, e);
            return null;
        }
    }

    public Map<String, Object> getByNames(List<String> keys) {
        try {
            return websites.get().getWebSiteByNameIn(keys);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Read website KV list failed", e);
            return Collections.emptyMap();
        }
    }

    public List<Entry> listByPrefix(String prefix) {
        try {
            return websites.get()
                    .queryList(
                            "SELECT `name`, `value`, length(`value`) AS `size` FROM `website` WHERE `name` LIKE ? ORDER BY `name`",
                            Entry.class, prefix + "%");
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "List website KV by prefix failed, prefix=" + prefix, e);
            return Collections.emptyList();
        }
    }

    public boolean putString(String key, String value) throws SQLException {
        return websites.get().updateByKV(key, value);
    }

    public boolean putStringQuietly(String key, String value) {
        try {
            return putString(key, value);
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Write website KV failed, key=" + key, e);
            return false;
        }
    }

    public boolean remove(String key) throws SQLException {
        return putString(key, null);
    }

    public boolean removeQuietly(String key) {
        try {
            return remove(key);
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Remove website KV failed, key=" + key, e);
            return false;
        }
    }

    public UpgradeWebSiteInfo upgradeWebSiteInfo() {
        UpgradeWebSiteInfo upgrade = ResultBeanUtils.convert(
                getByNames(Arrays.asList(UPGRADE_PREVIEW_KEY, AUTO_UPGRADE_VERSION_KEY, "upgradePackageSource")),
                UpgradeWebSiteInfo.class
        );
        if (Objects.isNull(upgrade.getAutoUpgradeVersion())) {
            upgrade.setAutoUpgradeVersion(AutoUpgradeVersionType.ONE_DAY.getCycle());
        }
        if (upgrade.getUpgradePackageSource() == null || upgrade.getUpgradePackageSource().isBlank()) {
            upgrade.setUpgradePackageSource("dl");
        }
        upgrade.setUpgradePreview(Objects.equals(upgrade.getUpgradePreview(), true));
        return upgrade;
    }

    public static final class Entry {
        public String name;
        public String value;
        public long size;
    }
}

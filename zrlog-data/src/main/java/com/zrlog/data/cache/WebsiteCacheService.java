package com.zrlog.data.cache;

import com.google.gson.Gson;
import com.hibegin.common.util.LoggerUtil;
import com.hibegin.common.util.StringUtils;
import com.zrlog.data.repository.WebSiteRepository;
import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import java.lang.reflect.Type;
import java.sql.SQLException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

@Singleton
public class WebsiteCacheService {
    public static final String KEY_PREFIX = "cache_v1:";
    private static final Logger LOGGER = LoggerUtil.getLogger(WebsiteCacheService.class);
    private static final Gson GSON = new Gson();
    private final Provider<WebSiteRepository> websites;

    @Inject
    public WebsiteCacheService(Provider<WebSiteRepository> websites) {
        this.websites = websites;
    }

    public static String storageKey(String key) {
        return key.startsWith(KEY_PREFIX) ? key : KEY_PREFIX + key;
    }

    public <T> T getJson(String key, Class<T> clazz) {
        return getJson(key, (Type) clazz);
    }

    public <T> T getJson(String key, Type type) {
        return decodeJson(key, getString(key), type);
    }

    <T> T decodeJson(String key, String json, Type type) {
        if (StringUtils.isEmpty(json)) {
            return null;
        }
        try {
            return GSON.fromJson(json, type);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Read website JSON cache failed, key=" + key, e);
            return null;
        }
    }

    public boolean putJson(String key, Object value) {
        try {
            return putString(key, GSON.toJson(value));
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Write website JSON cache failed, key=" + key, e);
            return false;
        }
    }

    public String getString(String key) {
        try {
            return websites.get().getStringValueByName(storageKey(key));
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "Read website cache failed, key=" + key, e);
            return null;
        }
    }

    public boolean putString(String key, String value) {
        try {
            return websites.get().updateByKV(storageKey(key), value);
        } catch (SQLException e) {
            LOGGER.log(Level.FINE, "Write website cache failed, key=" + key, e);
            return false;
        }
    }

    public boolean remove(String key) {
        return putString(key, null);
    }

    public List<Entry> listEntries() {
        try {
            return websites.get().listByPrefix(KEY_PREFIX, Entry.class);
        } catch (Exception e) {
            LOGGER.log(Level.FINE, "List website cache entries failed", e);
            return List.of();
        }
    }

    public static final class Entry {
        public String name;
        public String value;
        public long size;
    }
}
